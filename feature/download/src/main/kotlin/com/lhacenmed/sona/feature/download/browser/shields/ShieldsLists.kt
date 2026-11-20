package com.lhacenmed.sona.feature.download.browser.shields

import android.content.Context
import com.lhacenmed.sona.core.common.di.ApplicationScope
import com.lhacenmed.sona.core.datastore.DownloadSettings
import com.lhacenmed.sona.core.model.AdBlockMode
import com.lhacenmed.sona.core.model.ShieldsPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

/** Brave's catalog of filter lists - which there are, what each is made of, and which are on by default. */
private const val CatalogUrl = "https://raw.githubusercontent.com/brave/adblock-resources/master/filter_lists/list_catalog.json"
private const val DebounceUrl = "https://raw.githubusercontent.com/brave/adblock-lists/master/brave-lists/debounce.json"
private const val QueryFilterUrl = "https://raw.githubusercontent.com/brave/adblock-lists/master/brave-lists/query-filter.json"

/** The catalog id of Brave's default list - the one its default engine holds, every other list going to its additional one. */
private const val DefaultListId = "default"

/** How long the lists stay read once the browser closes. */
private const val ReleaseAfterMs = 10_000L

/** How long a list is kept before it is fetched again - Brave's component updater checks for new ones every few hours. */
private val RefreshAfterMs = TimeUnit.HOURS.toMillis(24)

/**
 * One of the catalog's filter lists: [id], [title] and [description] as it gives them; [isDefault] where it is on unless turned
 * off - by the catalog, or for the device's language; [isHidden] where it is part of the Shields rather than one to choose.
 */
data class FilterList(
    val id: String,
    val title: String,
    val description: String,
    val isDefault: Boolean,
    val isHidden: Boolean,
    internal val sources: List<String>,
) {
    fun isOn(shields: ShieldsPreferences): Boolean = if (isHidden) isDefault else id in shields.listsOn || isDefault && id !in shields.listsOff
}

/** The lists as the Shields run them: Brave's two engines, and debouncing's and query filtering's rules. */
internal class LoadedLists(
    val defaultEngine: FilterEngine?,
    val additionalEngine: FilterEngine?,
    val debounceRules: List<DebounceRule>,
    val debounceSites: Set<String>,
    val queryRules: List<QueryRule>,
)

/**
 * Brave's filter lists, its debounce and query filter rules - fetched from where Brave keeps them, kept on the device and fetched
 * again a day on, and read into engines while the browser is open: they take some memory, and the rest of the app none of it.
 */
@Singleton
class ShieldsLists @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    private val settings: DownloadSettings,
) {
    private val folder = File(context.filesDir, "shields")
    private val client by lazy { OkHttpClient.Builder().callTimeout(2, TimeUnit.MINUTES).build() }
    private val mutex = Mutex()
    @Volatile
    private var openCount = 0
    private var following: Job? = null
    private var releasing: Job? = null

    private val _catalog = MutableStateFlow<List<FilterList>>(emptyList())
    /** The catalog's lists, as last fetched - empty before it ever was. */
    val catalog: StateFlow<List<FilterList>> = _catalog

    private val _loaded = MutableStateFlow<LoadedLists?>(null)
    internal val loaded: StateFlow<LoadedLists?> = _loaded

    /** Readies the lists for a browser opening - read from the device, fetched where they are old - till it [close]s. */
    @Synchronized
    fun open() {
        releasing?.cancel()
        releasing = null
        if (openCount++ > 0 || following != null) return
        following = scope.launch {
            settings.browserShields.flow
                .map { shields -> Triple(shields.adBlock != AdBlockMode.OFF, shields.listsOn, shields.listsOff) }
                .distinctUntilChanged()
                .collect { load(refresh = false) }
        }
    }

    /** Lets the lists go - a little after, so a browser opened again at once, as a turned screen's is, has them still. */
    @Synchronized
    fun close() {
        if (--openCount > 0) return
        releasing = scope.launch {
            delay(ReleaseAfterMs)
            synchronized(this@ShieldsLists) {
                if (openCount > 0) return@launch
                following?.cancel()
                following = null
                _loaded.value = null
            }
        }
    }

    private val _isUpdating = MutableStateFlow(false)
    /** Whether [update] is fetching the lists. */
    val isUpdating: StateFlow<Boolean> = _isUpdating

    /** Fetches every list again now, whatever its age - see [catalog]. */
    fun update() {
        if (_isUpdating.value) return
        _isUpdating.value = true
        scope.launch {
            try {
                load(refresh = true)
            } finally {
                _isUpdating.value = false
            }
        }
    }

    /** The catalog as on the device, for the settings to show before a browser ever opened. */
    fun readCatalog() {
        scope.launch(Dispatchers.IO) { if (_catalog.value.isEmpty()) _catalog.value = parseCatalog(cached(CatalogUrl, refresh = false) ?: return@launch) }
    }

    private suspend fun load(refresh: Boolean) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val shields = settings.browserShields.value
            val catalog = cached(CatalogUrl, refresh)?.let(::parseCatalog).orEmpty()
            _catalog.value = catalog
            val (debounceJson, queryJson) = listOf(DebounceUrl, QueryFilterUrl).map { async { cached(it, refresh) } }.awaitAll()
            val debounceRules = debounceJson?.let(DebounceRule::parse).orEmpty()
            val engines = if (shields.adBlock == AdBlockMode.OFF) null else {
                val lists = catalog.filter { it.isOn(shields) }
                val (default, additional) = lists.partition { it.id == DefaultListId }
                val sources = lists.flatMap { it.sources }.distinct().map { url -> async { url to cached(url, refresh) } }.awaitAll().toMap()
                // Fetched for the settings' update with no browser open - read into engines only for one.
                suspend fun engineOf(of: List<FilterList>): FilterEngine? {
                    if (openCount == 0) return null
                    val texts = of.flatMap { it.sources }.distinct().mapNotNull(sources::get).takeIf { it.isNotEmpty() } ?: return null
                    return withContext(Dispatchers.Default) { FilterEngine.of(texts.asSequence().flatMap(String::lineSequence)) }
                }
                engineOf(default) to engineOf(additional)
            }
            // Read for an open browser only - one that closed meanwhile has no use for them.
            if (openCount > 0) {
                _loaded.value = LoadedLists(
                    defaultEngine = engines?.first,
                    additionalEngine = engines?.second,
                    debounceRules = debounceRules,
                    debounceSites = debounceRules.flatMapTo(HashSet()) { it.sites },
                    queryRules = queryJson?.let(QueryRule::parse).orEmpty(),
                )
            }
        }
    }

    /** [url]'s text: as kept, where it is and is not old - else fetched and kept; the kept one where fetching fails. */
    private fun cached(url: String, refresh: Boolean): String? {
        val file = File(folder, sha1(url))
        val isFresh = file.exists() && System.currentTimeMillis() - file.lastModified() < RefreshAfterMs
        if (isFresh && !refresh) return file.readText()
        val fetched = runCatching {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        }.getOrNull()
        if (fetched != null) {
            folder.mkdirs()
            File(folder, "${file.name}.part").apply { writeText(fetched) }.renameTo(file)
            return fetched
        }
        return file.takeIf(File::exists)?.readText()
    }

    private fun parseCatalog(json: String): List<FilterList> {
        val array = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        val language = Locale.getDefault().language
        return (0 until array.length()).mapNotNull { index ->
            val entry = array.optJSONObject(index) ?: return@mapNotNull null
            val platforms = entry.optJSONArray("platforms")?.let { list -> (0 until list.length()).map(list::optString) }
            // A list for another platform's browser - iOS's, desktop's - is none of this one's.
            if (platforms != null && "ANDROID" !in platforms) return@mapNotNull null
            val langs = entry.optJSONArray("langs")?.let { list -> (0 until list.length()).map(list::optString) }.orEmpty()
            val sources = entry.optJSONArray("sources")?.let { list -> (0 until list.length()).mapNotNull { list.optJSONObject(it)?.optString("url") } }.orEmpty()
            FilterList(
                id = entry.optString("uuid").takeIf(String::isNotEmpty) ?: return@mapNotNull null,
                title = entry.optString("title"),
                description = entry.optString("desc"),
                isDefault = entry.optBoolean("default_enabled") || language in langs,
                isHidden = entry.optBoolean("hidden"),
                sources = sources.filter(String::isNotEmpty),
            )
        }
    }
}

private fun sha1(text: String): String = MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

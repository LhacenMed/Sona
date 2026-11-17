package com.lhacenmed.sona.feature.download.tools

import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import com.lhacenmed.sona.core.common.di.ApplicationScope
import com.lhacenmed.sona.core.downloader.DownloaderRuntime
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A site cookies are saved for - Seal's cookie profile - by its [domain], with how many of them there are. */
internal data class CookieSite(val domain: String, val cookieCount: Int)

/**
 * The cookies saved from signing in on sites, in the one file yt-dlp reads them from - Netscape's format, a site's lines
 * beside every other's - and the [sites] they are for, read from that file: each signing in replaces its site's lines alone.
 */
@Singleton
internal class SavedCookies @Inject constructor(
    @ApplicationContext private val context: Context,
    private val runtime: DownloaderRuntime,
    @ApplicationScope scope: CoroutineScope,
) {
    private val _sites = MutableStateFlow<List<CookieSite>>(emptyList())

    /** Every site cookies are saved for, in the order they were saved. */
    val sites: StateFlow<List<CookieSite>> = _sites.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) { _sites.value = sitesOf(lines()) }
    }

    /**
     * Saves the cookies the web view holds for [url]'s site, in place of what was saved for it before - whether there were
     * any to save.
     */
    suspend fun save(url: String): Boolean = withContext(Dispatchers.IO) {
        val host = Uri.parse(url).host?.removePrefix("www.") ?: return@withContext false
        val cookies = CookieManager.getInstance().getCookie(url)?.split(';')?.map(String::trim)?.filter { '=' in it }.orEmpty()
        if (cookies.isEmpty()) return@withContext false
        CookieManager.getInstance().flush()
        val domain = ".$host"
        val expires = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()) + TimeUnit.DAYS.toSeconds(365)
        val saved = cookies.map { cookie -> "$domain\tTRUE\t/\tTRUE\t$expires\t${cookie.substringBefore('=')}\t${cookie.substringAfter('=')}" }
        write(lines().filterNot { it.startsWith("$domain\t") } + saved)
        true
    }

    /** Forgets the cookies saved for [site], every other site's kept. */
    suspend fun remove(site: CookieSite) = withContext(Dispatchers.IO) {
        write(lines().filterNot { domainOf(it) == site.domain })
    }

    /** Forgets every site's cookies, here and in the web view. */
    suspend fun clear() = withContext(Dispatchers.IO) {
        CookieManager.getInstance().removeAllCookies(null)
        runtime.cookiesFile.delete()
        _sites.value = emptyList()
    }

    /** Copies the cookies file to [uri] - a file the user picked - as it is; whether it could. */
    suspend fun export(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { output -> runtime.cookiesFile.inputStream().use { it.copyTo(output) } } != null
        }.getOrDefault(false)
    }

    /** The cookies file's cookies, a line each - none of its comments or blank lines. */
    private fun lines(): List<String> =
        runtime.cookiesFile.takeIf { it.exists() }?.readLines().orEmpty().filter { it.isNotBlank() && !it.startsWith("#") }

    private fun write(cookies: List<String>) {
        runtime.cookiesFile.writeText((listOf(NETSCAPE_HEADER) + cookies).joinToString("\n", postfix = "\n"))
        _sites.value = sitesOf(cookies)
    }

    private fun sitesOf(cookies: List<String>): List<CookieSite> =
        cookies.groupingBy(::domainOf).eachCount().map { (domain, count) -> CookieSite(domain, count) }
}

/** The first line of every file of cookies yt-dlp reads. */
private const val NETSCAPE_HEADER = "# Netscape HTTP Cookie File"

/** The site a cookie's [line] is for, as a person names it - `youtube.com`. */
private fun domainOf(line: String): String = line.substringBefore('\t').removePrefix(".")

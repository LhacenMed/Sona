package com.lhacenmed.sona.core.downloader

import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import com.lhacenmed.sona.core.common.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** A site the browser holds cookies for, by its [domain] - `youtube.com` - with how many it holds now. */
data class CookieSite(val domain: String, val cookieCount: Int)

/**
 * The cookies of every site signed in on, or visited, in the app's browser - its web view's own, the one place they are kept.
 * Nothing is saved apart from them, nor asked to be: signed in on a site as on any browser, a download from it is signed in
 * too, with the cookies as they stand the moment it starts - see [fileFor] - so a session the site has since renewed is never
 * read stale.
 *
 * The web view cannot list the sites it holds cookies for, so each page the browser finishes with cookies of its own names its
 * site here - see [remember] - for [sites] to list, and to remove or export by.
 */
@Singleton
class BrowserCookies @Inject constructor(
    @ApplicationContext private val context: Context,
    private val runtime: DownloaderRuntime,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val registry = File(context.filesDir, "browser-cookie-sites.txt")
    private val lock = Mutex()
    private val _sites = MutableStateFlow<List<CookieSite>>(emptyList())

    /** Every site the browser holds cookies for, in the order it first held them. */
    val sites: StateFlow<List<CookieSite>> = _sites.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            lock.withLock {
                // The sites saved before the browser's cookies were all there is: theirs are the browser's still.
                runtime.cookiesFile.takeIf(File::exists)?.let { saved ->
                    val domains = saved.readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map { it.substringBefore('\t').removePrefix(".") }
                    write((domains() + domains).distinct())
                    saved.delete()
                }
                refresh()
            }
        }
    }

    /** Names [url]'s site as one the browser holds cookies for, where it does - what each page the browser finishes tells. */
    fun remember(url: String) {
        val domain = domainOf(url) ?: return
        scope.launch(Dispatchers.IO) {
            lock.withLock {
                if (cookiesOf(domain).isEmpty()) return@withLock refresh()
                if (domain !in domains()) write(domains() + domain)
                refresh()
            }
        }
    }

    /**
     * A file of the cookies the browser holds for [url]'s site now - in the Netscape format yt-dlp reads, written for its one
     * run, the run's to delete once it is over. Null where the browser holds none for it.
     */
    fun fileFor(url: String, directory: File): File? {
        val domain = domainOf(url) ?: return null
        val cookies = cookiesOf(domain).ifEmpty { return null }
        directory.mkdirs()
        return File.createTempFile("cookies", ".txt", directory).apply { writeText(netscapeOf(domain, cookies)) }
    }

    /** Forgets [site]'s cookies in the browser - signed out of it there, and in every download after. */
    suspend fun remove(site: CookieSite) = withContext(Dispatchers.IO) {
        lock.withLock {
            val manager = CookieManager.getInstance()
            cookiesOf(site.domain).map { it.substringBefore('=') }.forEach { name ->
                listOf("https://${site.domain}", "https://www.${site.domain}").forEach { origin ->
                    manager.setCookie(origin, "$name=; Max-Age=0; Path=/")
                    manager.setCookie(origin, "$name=; Max-Age=0; Path=/; Domain=.${site.domain}")
                }
            }
            manager.flush()
            write(domains() - site.domain)
            refresh()
        }
    }

    /** Forgets every site's cookies in the browser. */
    suspend fun clear() = withContext(Dispatchers.IO) {
        lock.withLock {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            write(emptyList())
            refresh()
        }
    }

    /** Writes every site's cookies to [output] - a file the user picked - as the one file yt-dlp reads. */
    suspend fun export(output: OutputStream) = withContext(Dispatchers.IO) {
        val text = domains().joinToString("") { domain -> netscapeOf(domain, cookiesOf(domain)).removePrefix(NetscapeHeader) }
        output.write((NetscapeHeader + text).toByteArray())
    }

    /** Lists [sites] as the browser holds them now: one whose cookies are all gone - forgotten as it closed - goes. */
    private fun refresh() {
        _sites.value = domains().map { CookieSite(it, cookiesOf(it).size) }.filter { it.cookieCount > 0 }
    }

    private fun domains(): List<String> = registry.takeIf(File::exists)?.readLines()?.filter(String::isNotBlank).orEmpty()

    private fun write(domains: List<String>) = registry.writeText(domains.joinToString("\n"))

    /** The cookies the browser would send [domain]'s pages, each `name=value`. */
    private fun cookiesOf(domain: String): List<String> =
        runCatching { CookieManager.getInstance().getCookie("https://www.$domain") ?: CookieManager.getInstance().getCookie("https://$domain") }
            .getOrNull()?.split(';')?.map(String::trim)?.filter { '=' in it }.orEmpty()

    private fun netscapeOf(domain: String, cookies: List<String>): String {
        // The web view tells a cookie's name and value alone: each is sent to the whole site, over HTTPS, for a year.
        val expires = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()) + TimeUnit.DAYS.toSeconds(365)
        return NetscapeHeader + cookies.joinToString("") { "\n.$domain\tTRUE\t/\tTRUE\t$expires\t${it.substringBefore('=')}\t${it.substringAfter('=')}" } + "\n"
    }

    /** [url]'s site as a person names it - `youtube.com` for www.youtube.com and m.youtube.com alike - null for no web address. */
    private fun domainOf(url: String): String? =
        Uri.parse(url).takeIf { it.scheme == "https" || it.scheme == "http" }?.host?.lowercase()?.removePrefix("www.")?.removePrefix("m.")

    private companion object {
        /** The first line of every file of cookies yt-dlp reads. */
        const val NetscapeHeader = "# Netscape HTTP Cookie File"
    }
}

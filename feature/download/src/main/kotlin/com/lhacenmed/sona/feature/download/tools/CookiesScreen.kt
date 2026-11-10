package com.lhacenmed.sona.feature.download.tools

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.datastore.DownloadSettings
import com.lhacenmed.sona.core.designsystem.component.SonaActionButtonGroup
import com.lhacenmed.sona.core.designsystem.component.actionButton
import com.lhacenmed.sona.core.designsystem.component.toast
import com.lhacenmed.sona.core.downloader.DownloaderRuntime
import com.lhacenmed.sona.core.model.DownloadFailure
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.download.DownloadQueue
import com.lhacenmed.sona.feature.download.R
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where signing in starts, unless the user goes elsewhere. */
private const val DefaultSite = "https://www.youtube.com"

/**
 * Signing in on a site, for downloads that need an account - Seal's cookies: [site] opens here, the user signs in as on any
 * browser, and its cookies are saved for yt-dlp, which then reads the site as them - every download that failed for want of
 * it trying again by itself. Saved per site; cleared all at once.
 */
data class CookiesScreen(val site: String = DefaultSite) : Screen {
    override val titleRes: Int get() = R.string.download_cookies_title

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    override fun Content() {
        val context = LocalContext.current
        val viewModel: CookiesViewModel = hiltViewModel()
        var address by rememberSaveable { mutableStateOf(site) }
        var webView by remember { mutableStateOf<WebView?>(null) }
        val saveLabel = stringResource(R.string.download_cookies_save)
        val clearLabel = stringResource(R.string.download_cookies_clear)

        Column(modifier = Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text(stringResource(R.string.download_cookies_site)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { webView?.loadUrl(address.withScheme()) }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            )
            AndroidView(
                factory = { viewContext ->
                    WebView(viewContext).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String) {
                                address = url
                            }
                        }
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        loadUrl(address.withScheme())
                        webView = this
                    }
                },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            SonaActionButtonGroup(modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
                actionButton(label = clearLabel, onClick = {
                    viewModel.clear()
                    context.toast(R.string.download_cookies_cleared)
                })
                actionButton(label = saveLabel, onClick = {
                    val url = webView?.url ?: address.withScheme()
                    viewModel.save(url) { saved -> context.toast(if (saved) R.string.download_cookies_saved else R.string.download_cookies_none) }
                })
            }
        }
    }
}

private fun String.withScheme(): String = trim().let { if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it" }

@HiltViewModel
internal class CookiesViewModel @Inject constructor(
    private val runtime: DownloaderRuntime,
    private val settings: DownloadSettings,
    private val queue: DownloadQueue,
) : ViewModel() {

    /**
     * Saves the cookies the web view holds for [url]'s site in the Netscape format yt-dlp reads - in place of what was saved for
     * it before, beside every other site's - and has downloads use them. Says whether there were any to [onSaved].
     */
    fun save(url: String, onSaved: (Boolean) -> Unit) {
        val host = Uri.parse(url).host?.removePrefix("www.") ?: return onSaved(false)
        val cookies = CookieManager.getInstance().getCookie(url)?.split(';')?.map(String::trim)?.filter { '=' in it }.orEmpty()
        if (cookies.isEmpty()) return onSaved(false)
        CookieManager.getInstance().flush()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val domain = ".$host"
                val expires = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()) + TimeUnit.DAYS.toSeconds(365)
                val others = runtime.cookiesFile.takeIf { it.exists() }?.readLines().orEmpty().filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("$domain\t") }
                val lines = cookies.map { cookie -> "$domain\tTRUE\t/\tTRUE\t$expires\t${cookie.substringBefore('=')}\t${cookie.substringAfter('=')}" }
                runtime.cookiesFile.writeText((listOf("# Netscape HTTP Cookie File") + others + lines).joinToString("\n", postfix = "\n"))            }
            settings.updatePreferences { it.copy(usesCookies = true) }
            queue.retryFailed(DownloadFailure.LOGIN_REQUIRED)
            onSaved(true)
        }
    }

    /** Forgets every site's cookies, here and in the web view. */
    fun clear() {
        CookieManager.getInstance().removeAllCookies(null)
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runtime.cookiesFile.delete() }
            settings.updatePreferences { it.copy(usesCookies = false) }
        }
    }
}

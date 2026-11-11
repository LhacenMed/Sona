package com.lhacenmed.sona.feature.download

import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.designsystem.SonaActivity
import com.lhacenmed.sona.core.designsystem.component.toast
import com.lhacenmed.sona.core.designsystem.theme.AppCoverStyle
import com.lhacenmed.sona.core.designsystem.theme.AppFastScrollTouchArea
import com.lhacenmed.sona.core.designsystem.theme.AppTheme
import com.lhacenmed.sona.core.designsystem.theme.SonaTheme
import com.lhacenmed.sona.core.navigation.IntentNavigator
import com.lhacenmed.sona.core.navigation.LocalNavigator
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * "Quick download" in the share sheet - Seal's: the link shared is read in the download sheet, laid over the app it was
 * shared from - Sona itself never opens - downloaded from there as it is chosen, and the sheet gone with it. Whatever the
 * sheet opens - signing in, say - opens in Sona as it would from the download screen.
 */
@AndroidEntryPoint
class QuickDownloadActivity : SonaActivity() {

    /** The same process-wide theme, cover style and fast scroll as every other window, so the sheet is drawn as Sona's are. */
    @Inject lateinit var appTheme: AppTheme
    @Inject lateinit var appCoverStyle: AppCoverStyle
    @Inject lateinit var appFastScrollTouchArea: AppFastScrollTouchArea

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val link = linkIn(intent.getStringExtra(Intent.EXTRA_TEXT))
        if (link == null) {
            toast(R.string.download_no_link)
            finish()
            return
        }
        holdFirstFrameUntil(appTheme::awaitReady)
        setSonaContent {
            val themeConfig by appTheme.config.collectAsStateWithLifecycle()
            val coverStyle by appCoverStyle.style.collectAsStateWithLifecycle()
            val fastScrollTouchArea by appFastScrollTouchArea.touchArea.collectAsStateWithLifecycle()
            SonaTheme(config = themeConfig, coverStyle = coverStyle, fastScrollTouchArea = fastScrollTouchArea) {
                val viewModel: DownloadViewModel = hiltViewModel()
                val linkSheet by viewModel.linkSheet.collectAsStateWithLifecycle()
                val navigator = remember { IntentNavigator(this, currentScreen = null) }
                // Opened once - a window recreated keeps the sheet its view model holds - and gone once the sheet is.
                LaunchedEffect(viewModel) {
                    if (savedInstanceState == null) viewModel.open(link)
                    viewModel.linkSheet.first { it == null }
                    finish()
                }
                CompositionLocalProvider(LocalNavigator provides navigator) {
                    linkSheet?.let { LinkSheet(it, viewModel) }
                }
            }
        }
    }
}

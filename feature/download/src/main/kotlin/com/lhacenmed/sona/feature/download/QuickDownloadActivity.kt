package com.lhacenmed.sona.feature.download

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.lhacenmed.sona.core.common.entry.AppEntry
import com.lhacenmed.sona.core.designsystem.component.toast
import com.lhacenmed.sona.core.downloader.DownloaderRuntime
import com.lhacenmed.sona.core.downloader.RuntimeState
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * "Quick download" in the share sheet - Seal's: the link shared downloads at once, as the download settings stand, and
 * this closes with no more than a toast. Before the downloader is downloaded, Sona opens on its download screen instead,
 * where it is, the link there waiting.
 */
@AndroidEntryPoint
class QuickDownloadActivity : ComponentActivity() {

    @Inject lateinit var queue: DownloadQueue
    @Inject lateinit var downloader: DownloaderRuntime

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val link = linkIn(intent.getStringExtra(Intent.EXTRA_TEXT))
        when {
            link == null -> toast(R.string.download_no_link)
            downloader.state.value == RuntimeState.Installed -> {
                queue.add(link)
                toast(R.string.download_queued)
            }
            else -> startActivity(AppEntry.DOWNLOADS.intent(this).putExtra(Intent.EXTRA_TEXT, link))
        }
        finish()
    }
}

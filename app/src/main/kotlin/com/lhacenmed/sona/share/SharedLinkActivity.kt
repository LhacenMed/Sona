package com.lhacenmed.sona.share

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.lhacenmed.sona.core.common.entry.AppEntry

/**
 * Where a link shared to Sona lands: handed on to the app's own window as [AppEntry.DOWNLOADS], the download screen opened
 * on it as the downloads notification opens it - in the one task Sona has. Shared straight to the app's activity, the link
 * would open a second copy of the whole app beside it: an app sharing asks Android to open whatever it shares to as a new
 * document, a task of its own.
 */
class SharedLinkActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(
            AppEntry.DOWNLOADS.intent(this)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(Intent.EXTRA_TEXT, intent.getStringExtra(Intent.EXTRA_TEXT)),
        )
        finish()
    }
}

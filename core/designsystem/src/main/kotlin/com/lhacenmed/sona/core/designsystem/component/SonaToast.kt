package com.lhacenmed.sona.core.designsystem.component

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.annotation.StringRes
import com.lhacenmed.sona.core.designsystem.effect.performSonaHaptic

/** The toast still showing - cancelled by the next, so a burst of them replaces itself rather than queueing. */
private var shownToast: Toast? = null

private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

/**
 * Shows [message] as the app's toast: felt as well as seen - see [performSonaHaptic], which the haptics
 * setting turns off - and in place of whichever toast is still showing. Safe to call from any thread.
 *
 * The one way the app shows a toast. Shown through the application, so a screen closing under it - a
 * dialog confirming its change, say - does not take it along.
 */
fun Context.toast(message: CharSequence) {
    val show = {
        performSonaHaptic()
        shownToast?.cancel()
        shownToast = Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).apply { show() }
    }
    if (Looper.myLooper() == Looper.getMainLooper()) show() else mainHandler.post(show)
}

/** [toast], with the message read from [messageRes]. */
fun Context.toast(@StringRes messageRes: Int) = toast(getText(messageRes))

/**
 * Copies [text], confirming it with [confirmation]. From Android 13 the system shows what was copied
 * itself, so there the haptic alone confirms it - a toast would only say it twice.
 */
fun Context.copyToClipboard(text: CharSequence, confirmation: CharSequence) {
    getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(confirmation, text))
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) performSonaHaptic() else toast(confirmation)
}

package com.lhacenmed.sona

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

/**
 * The launcher's "Shuffle all" shortcut - Auxio's. It opens the library and shuffles whatever
 * shuffle-all is set to play - every track, or the collection chosen - exactly as the library's button does.
 *
 * Published by the app as it starts rather than declared in the manifest, which is what Auxio does
 * too: a declared shortcut names one package, while a published one opens whichever build published
 * it - the debug build's shortcut opens the debug build.
 */
internal object ShuffleAllShortcut {

    const val ACTION = "${BuildConfig.APPLICATION_ID}.action.SHUFFLE_ALL"

    private const val ID = "shuffle_all"

    fun publish(context: Context) {
        val shortcut = ShortcutInfoCompat.Builder(context, ID)
            .setShortLabel(context.getString(R.string.shuffle_all_shortcut_short))
            .setLongLabel(context.getString(R.string.shuffle_all_shortcut_long))
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_shuffle))
            .setIntent(
                Intent(context, MainActivity::class.java)
                    .setAction(ACTION)
                    // A fresh library to shuffle from, whatever screen the app was left on.
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            )
            .build()
        ShortcutManagerCompat.addDynamicShortcuts(context, listOf(shortcut))
    }
}

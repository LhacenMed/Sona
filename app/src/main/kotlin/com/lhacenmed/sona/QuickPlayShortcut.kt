package com.lhacenmed.sona

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.lhacenmed.sona.core.datastore.QuickPlayMode

/**
 * The launcher's quick play shortcut - Auxio's "Shuffle all". It opens the library and plays whatever
 * quick play is set to play, the way it is set to - exactly as the library's button does - and is named
 * and drawn for that way: Shuffle all, or Play all.
 *
 * Published by the app as it starts rather than declared in the manifest, which is what Auxio does
 * too: a declared shortcut names one package, while a published one opens whichever build published
 * it - the debug build's shortcut opens the debug build.
 */
internal object QuickPlayShortcut {

    // Under the names it was first published with, so a shortcut already pinned is updated in place and
    // keeps working.
    const val ACTION = "${BuildConfig.APPLICATION_ID}.action.SHUFFLE_ALL"

    private const val ID = "shuffle_all"

    fun publish(context: Context, mode: QuickPlayMode) {
        val (shortLabel, longLabel, icon) = when (mode) {
            QuickPlayMode.SHUFFLE -> Triple(
                R.string.quick_play_shortcut_shuffle_short,
                R.string.quick_play_shortcut_shuffle_long,
                R.drawable.ic_shortcut_shuffle,
            )
            QuickPlayMode.PLAY -> Triple(
                R.string.quick_play_shortcut_play_short,
                R.string.quick_play_shortcut_play_long,
                R.drawable.ic_shortcut_play,
            )
        }
        val shortcut = ShortcutInfoCompat.Builder(context, ID)
            .setShortLabel(context.getString(shortLabel))
            .setLongLabel(context.getString(longLabel))
            .setIcon(IconCompat.createWithResource(context, icon))
            .setIntent(
                Intent(context, MainActivity::class.java)
                    .setAction(ACTION)
                    // A fresh library to play from, whatever screen the app was left on.
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            )
            .build()
        // Replaces the shortcut under its id, pinned copies included, as the mode changes.
        ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
    }
}

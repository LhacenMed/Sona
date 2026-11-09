package com.lhacenmed.sona.core.common.entry

import android.app.PendingIntent
import android.content.Context
import android.content.Intent

private const val EXTRA_ENTRY = "com.lhacenmed.sona.extra.APP_ENTRY"

private const val BACK_TO_LIBRARY = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP

/**
 * Where a tap from outside the app - a notification, the home screen widget - opens it. Named here because what
 * is tapped lives in modules that know neither the app's activity nor its screens; the app, which does, opens each.
 *
 * All of them are the launcher's own intent, told apart by an extra: Android starts a second copy of the task's first
 * activity for an intent differing in anything but its extras, so an action of their own would stack a new library
 * over the task each time the launcher's icon is pressed after one of them opened it.
 */
enum class AppEntry(private val flags: Int) {
    /** The app as the user left it. */
    AS_LEFT(flags = 0),

    /** The full player, over the list the queue plays from at its playing row - a task of its own, whatever was left open. */
    NOW_PLAYING(flags = Intent.FLAG_ACTIVITY_CLEAR_TASK),

    UPDATES(flags = BACK_TO_LIBRARY),
    BACKUP(flags = BACK_TO_LIBRARY),
    STORAGE(flags = BACK_TO_LIBRARY),
    AUTO_TAG(flags = BACK_TO_LIBRARY),
    DOWNLOADS(flags = BACK_TO_LIBRARY),
    ;

    /** What opens the app here. One per entry, by its request code: Android tells pending intents apart by no extra. */
    fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            ordinal,
            intent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** What opens the app here, there and then - from outside it, as a share does. */
    fun intent(context: Context): Intent {
        val launcher = checkNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
        return if (this == AS_LEFT) launcher else launcher.addFlags(flags).putExtra(EXTRA_ENTRY, name)
    }

    companion object {
        /** The entry [intent] opens the app at - null for what is no such tap, as the launcher's icon is not. */
        fun of(intent: Intent): AppEntry? = intent.getStringExtra(EXTRA_ENTRY)?.let { name -> entries.firstOrNull { it.name == name } }
    }
}

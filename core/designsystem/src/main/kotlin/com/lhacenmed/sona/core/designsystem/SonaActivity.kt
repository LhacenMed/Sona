package com.lhacenmed.sona.core.designsystem

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge

/**
 * The activity every Sona screen is hosted in.
 *
 * It knows when it starts another activity, which is what a popup menu needs to close the way a native
 * one does: left in place for the new activity to slide in over, rather than animated away on a main
 * thread that is busy starting that activity. Every start funnels through [startActivityForResult] -
 * [startActivity] and activity result launchers alike - so nothing that starts an activity has to say
 * so itself.
 *
 * Every screen is drawn edge to edge, behind the status and navigation bars, from its very first
 * frame: the screens pad themselves by the bars' insets, and nothing of the window theme is left to
 * show beneath them. How the bars' icons are drawn follows the Compose theme - see `SonaTheme`.
 */
abstract class SonaActivity : ComponentActivity() {

    private var hasStartedActivity = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
    }

    /** Runs [action] and returns whether it started another activity. */
    fun startsActivity(action: () -> Unit): Boolean {
        hasStartedActivity = false
        action()
        return hasStartedActivity
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
        hasStartedActivity = true
        super.startActivityForResult(intent, requestCode, options)
    }
}

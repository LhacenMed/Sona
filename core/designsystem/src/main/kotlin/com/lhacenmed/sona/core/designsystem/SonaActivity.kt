package com.lhacenmed.sona.core.designsystem

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * The activity every Sona screen is hosted in.
 *
 * It knows when it starts another activity, which is what a popup menu needs to close the way a native
 * one does: left in place for the new activity to slide in over, rather than animated away on a main
 * thread that is busy starting that activity. Every start funnels through [startActivityForResult] -
 * [startActivity] and activity result launchers alike - so nothing that starts an activity has to say
 * so itself.
 */
abstract class SonaActivity : ComponentActivity() {

    private var hasStartedActivity = false

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

package com.lhacenmed.sona.core.designsystem

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.compositionContext
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.lifecycle.lifecycleScope
import com.lhacenmed.sona.core.designsystem.effect.ApplyRefreshRate
import com.lhacenmed.sona.core.designsystem.effect.ProvideSonaHaptics
import com.lhacenmed.sona.core.designsystem.effect.SonaEffects
import com.lhacenmed.sona.core.designsystem.effect.isHighRefreshRate
import com.lhacenmed.sona.core.designsystem.effect.rememberSupportedHighestFps
import com.lhacenmed.sona.core.designsystem.gesture.holdClaimedFingers
import com.lhacenmed.sona.core.designsystem.motion.ProvideRubberBandOverscroll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * How long a window's first frame may be held - see [SonaActivity.holdFirstFrameUntil].
 *
 * It is a safety valve, not a budget: what is waited for is normally ready in a few milliseconds. If
 * something is genuinely slow (a first run with no cache, a device under memory pressure), showing the
 * app with its loading placeholders beats holding a frozen launch screen indefinitely.
 */
private const val MaxFirstFrameHoldMillis = 1_200L

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
 *
 * Every window moves and feels as [SonaEffects] says, whatever it shows. Its animations run at its pace -
 * the window's compositions are run with it as Android runs them with its own animator scale, so every
 * Compose animation follows it, the dialogs and sheets opened over the window included, with nothing to
 * check it one by one. And the content set with [setSonaContent] asks the display for its fastest mode
 * when that is forced, gives its haptics only while they are on, rubber-bands every list at its ends, and
 * keeps each finger with the drag that claimed it until it lifts - see [holdClaimedFingers].
 */
abstract class SonaActivity : ComponentActivity() {

    private var hasStartedActivity = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.decorView.compositionContext =
            window.decorView.createLifecycleAwareWindowRecomposer(SonaEffects.motionDurationScale, lifecycle)
    }

    /** Sets this activity's content, moving and feeling as [SonaEffects] says - see [SonaActivity]. */
    protected fun setSonaContent(content: @Composable () -> Unit) {
        setContent {
            WindowRefreshRate()
            ProvideSonaHaptics {
                ProvideRubberBandOverscroll {
                    // Passing the window's constraints on as they are, so the content is laid out as if
                    // it were the root.
                    Box(modifier = Modifier.holdClaimedFingers(), propagateMinConstraints = true) { content() }
                }
            }
        }
    }

    /**
     * Holds this window's first frame until [awaitReady] returns, so the first thing the user sees is the
     * app as they set it up - their font, their colours, their library - rather than defaults corrected a
     * moment later. On Android 12+ the system launch screen stays up for as long as the frame is held.
     * What is already ready costs nothing: the frame is released before it was ever due.
     */
    protected fun holdFirstFrameUntil(awaitReady: suspend () -> Unit) {
        var isHeld = true
        val content = findViewById<View>(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (isHeld) return false
                content.viewTreeObserver.removeOnPreDrawListener(this)
                return true
            }
        })
        lifecycleScope.launch {
            withTimeoutOrNull(MaxFirstFrameHoldMillis) { awaitReady() }
            isHeld = false
            // A held frame never draws, so nothing else asks for the next one.
            content.invalidate()
        }
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

/** The display's fastest mode, asked of this window's root view while [SonaEffects] forces it. */
@Composable
private fun WindowRefreshRate() {
    val supportedHighestFps = rememberSupportedHighestFps()
    ApplyRefreshRate(
        isEnabled = SonaEffects.isHighRefreshRateForced && isHighRefreshRate(supportedHighestFps),
        targetFps = supportedHighestFps,
    )
}

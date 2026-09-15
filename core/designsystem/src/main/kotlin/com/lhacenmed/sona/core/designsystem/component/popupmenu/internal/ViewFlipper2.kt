package com.lhacenmed.sona.core.designsystem.component.popupmenu.internal

import android.animation.Animator
import android.animation.Animator.AnimatorListener
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewPropertyAnimator
import android.widget.FrameLayout
import androidx.core.view.isVisible

/**
 * FrameLayout-based view flipper for panel navigation — replaces the previous ViewFlipper
 * subclass that was silently killing ripple animations.
 *
 * Root cause of the ripple bug:
 *   ViewFlipper.showOnly() is called inside both setDisplayedChild() and addView()
 *   (inherited from ViewAnimator). It calls setVisibility(GONE) on outgoing children,
 *   which synchronously triggers RippleDrawable.cancelExistingAnimations() — the ripple
 *   animation is destroyed before our visibility-restore line ever runs.
 *
 * Fix:
 *   Extend FrameLayout directly. FrameLayout never forces child visibility, so we own
 *   the lifecycle entirely and ripple animations complete naturally.
 *
 * displayedIndex tracks the active panel and mirrors two ViewAnimator behaviors that
 * must be replicated manually:
 *
 *   addView  — ViewAnimator increments mWhichChild when a view is inserted at or before
 *              it, so the tracked child doesn't silently shift. We do the same.
 *              Without this, backward navigation (which inserts the cached panel at
 *              index 0) causes displayedIndex to point at the wrong view, inverting the
 *              clip and scrim targets for the entire transition.
 *
 *   removeView — decrements displayedIndex when the removed view was before it,
 *                keeping the index valid after the outgoing panel is discarded.
 *
 * Not marked internal so that [com.lhacenmed.sona.core.designsystem.component.popupmenu.MenuFlipper] can extend
 * it without a visibility mismatch compile error.
 */
open class ViewFlipper2(context: Context) : FrameLayout(context) {

    private var displayedIndex: Int = 0

    /** The currently active panel, or null before the first panel is added. */
    val displayedChildView: View?
        get() = getChildAt(displayedIndex)

    /**
     * Mirror ViewAnimator.addView: inserting a view at or before the currently displayed
     * index shifts that index up by one to keep it pointing at the same child.
     *
     * Without this, backward navigation breaks: inserting the cached panel at index 0
     * shifts the current panel to index 1 while displayedIndex stays 0, causing
     * displayedChildView to return the newly inserted panel instead of the current one.
     * The result is that prevPanel and scrimTarget are set to the wrong views, and the
     * clip dimensions animate against the inverted pair.
     *
     * Negative index means "append at end" — never satisfies the condition.
     * The childCount > 1 guard skips the first-panel insertion (no shift needed).
     */
    override fun addView(view: View, index: Int, params: ViewGroup.LayoutParams) {
        super.addView(view, index, params)
        if (childCount > 1 && index in 0..displayedIndex) {
            displayedIndex++
        }
    }

    /**
     * Transitions to [inView] using caller-supplied [ViewPropertyAnimator] factories.
     *
     * Neither child's visibility is forced — the outgoing panel remains VISIBLE so
     * any in-flight RippleDrawable animations run to completion while it slides out.
     * [outView] is hidden only when its slide-out animation ends via the [onEnd] callback.
     */
    internal fun setDisplayedChild(
        inView: View,
        inAnimator: (View) -> ViewPropertyAnimator,
        outAnimator: (View) -> ViewPropertyAnimator
    ) {
        val outView = displayedChildView
        displayedIndex = indexOfChild(inView)

        if (outView != null) {
            inAnimator(inView)
                .setListener(onStart = { inView.isVisible = true })
                .start()

            outAnimator(outView)
                .setListener(
                    onStart = { outView.isVisible = true },
                    onEnd = { outView.isVisible = false }
                )
                .start()
        }
    }

    /**
     * Mirror ViewAnimator.removeView: removing a view that was before the displayed index
     * shifts that index down by one to keep it valid.
     *
     * In forward navigation, setDisplayedChild sets displayedIndex to the new panel's
     * index (1), then the old panel at index 0 is removed — shifting the new panel to 0
     * and requiring a decrement. In backward navigation, the old panel is at index 1
     * (after the cached panel), so no adjustment is needed.
     */
    override fun removeView(view: View) {
        val removedIndex = indexOfChild(view)
        super.removeView(view)
        if (removedIndex in 0 until displayedIndex) {
            displayedIndex--
        }
    }

    /**
     * Offset touch events by the displayed child's translationX.
     * This ensures touch events are delivered correctly during slide animations.
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        displayedChildView?.let { child ->
            ev.offsetLocation(child.translationX, 0f)
        }
        return super.dispatchTouchEvent(ev)
    }
}

/**
 * Extension to set a listener on a ViewPropertyAnimator.
 */
private fun ViewPropertyAnimator.setListener(
    onEnd: () -> Unit = {},
    onStart: () -> Unit = {}
): ViewPropertyAnimator {
    setListener(object : AnimatorListener {
        override fun onAnimationRepeat(animator: Animator) = Unit
        override fun onAnimationCancel(animator: Animator) = Unit
        override fun onAnimationEnd(animator: Animator) = onEnd()
        override fun onAnimationStart(animator: Animator) = onStart()
    })
    return this
}
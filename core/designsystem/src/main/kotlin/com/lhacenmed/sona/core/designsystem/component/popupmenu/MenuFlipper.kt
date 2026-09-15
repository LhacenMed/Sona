package com.lhacenmed.sona.core.designsystem.component.popupmenu

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.animation.doOnEnd
import androidx.core.graphics.withTranslation
import androidx.core.view.doOnLayout
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.lhacenmed.sona.core.designsystem.component.popupmenu.internal.ForcePaddingsDrawable
import com.lhacenmed.sona.core.designsystem.component.popupmenu.internal.ViewFlipper2

/**
 * A [ViewFlipper2] that wraps its size to the currently displayed child and smoothly
 * animates BOTH height and width changes WITHOUT triggering layout passes.
 *
 * This view IS the [android.widget.PopupWindow.contentView]. Setting a background on it
 * (via [setBackgroundDrawable]) automatically wraps it in the following chain:
 *
 *   [WidthClipDrawable] (outermost)
 *     └─ [HeightClipDrawable]
 *          └─ [ForcePaddingsDrawable]
 *               └─ actual background (GradientDrawable)
 *
 * With [clipToOutline] = true, ALL child rendering is clipped to the background outline,
 * which reflects both the animated clip width and clip height simultaneously.
 *
 * Width animation (new): Right edge is fixed (anchored to the popup anchor). Left edge
 * contracts/expands as the user navigates between menu levels of different natural widths.
 * Each level's panel is positioned with [android.view.Gravity.END] inside a full-width
 * outer frame so the right-fixed clip always aligns with the panel content.
 *
 * Height animation (ported from saket/cascade): Top edge is fixed; bottom edge
 * contracts/expands between levels.
 *
 * Both dimensions animate via a single [sizeAnimator] for perfect synchronisation.
 *
 * Panel navigation uses a flipper that slides panels horizontally — new panels enter from
 * the right while old ones exit left during forward navigation, and the reverse happens
 * when going back. If an animation is already running, subsequent navigation requests
 * queue up until it completes.
 *
 * The navigation scrim is rendered on top of each panel using a multiply blend mode with
 * gray coloring to ensure the darkening effect adapts to the background's luminance,
 * maintaining consistent contrast across light and dark themes.
 *
 * Background sync fix: per-panel backgrounds are drawn as raw rects positioned exactly
 * at [width - panelWidth + translationX, width + translationX], perfectly in sync with
 * the items at all times — including before the size animator starts.
 */
@SuppressLint("ViewConstructor")
class MenuFlipper(
    context: Context,
    private val anim: AnimationConfig
) : ViewFlipper2(context) {

    /**
     * Invoked once the outgoing panel has been removed and the new panel is fully in place.
     * [PopupMenu] uses this to re-enable touch hit-testing for the new level's item views.
     */
    var onPanelSwitchComplete: (() -> Unit)? = null

    // Stateless — a single instance is sufficient for all panel transitions.
    private val navInterpolator = FastOutSlowInInterpolator()

    /**
     * Clip rect for touch dispatch during size animation.
     * Null initially — no restriction until a panel is shown and laid out.
     * Named [clipBounds2] to avoid shadowing [android.view.View.getClipBounds].
     */
    private var clipBounds2: Rect? = null

    // ── Direct clip references ────────────────────────────────────────────────────────────
    // Stored when setBackgroundDrawable wraps the incoming drawable. Accessed per-frame
    // during animation to avoid traversing the drawable chain on every update.

    private var widthClip: WidthClipDrawable? = null
    private var heightClip: HeightClipDrawable? = null

    // Current clipped dimensions — updated by setClippedSize each animation frame.
    private var currentClippedWidth = 0
    private var currentClippedHeight = 0

    /**
     * The single running animator for both width and height transitions.
     * Tracked so [enqueueAnimation] can detect in-flight transitions and
     * [onDetachedFromWindow] can cancel cleanly.
     */
    private var sizeAnimator: ValueAnimator = ObjectAnimator()

    // ── Per-panel background paint ────────────────────────────────────────────────────────
    // Draws the background rect exactly where panel items are, keeping background and
    // items perfectly in sync regardless of clip-width animation timing.

    /**
     * The popup card background color. Must be set from [PopupMenu] after construction.
     * Updating it also updates [panelBgPaint] so subsequent draws use the new color.
     */
    var panelBgColor: Int = Color.BLACK
        set(value) {
            field = value
            panelBgPaint.color = value
        }

    /** Reused per drawChild call — avoids per-frame allocation. */
    private val panelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // ── Transition state ──────────────────────────────────────────────────────────────────
    // Set at the start of each navigation in show(); cleared when the transition ends.
    // Used by drawChild to know each panel's natural content width so the background rect
    // is positioned at [w - panelWidth + tx, w + tx] — identical to the items' position.

    private var transitionInView: View? = null
    private var transitionFromWidth: Int = 0
    private var transitionToWidth: Int = 0

    /**
     * True while a panel-switch animation is in flight.
     * [PopupMenu] reads this to block touch forwarding during transitions.
     * [dispatchTouchEvent] uses it to block item presses during transitions.
     */
    var isTransitioning: Boolean = false
        private set

    // ── Nav scrim ─────────────────────────────────────────────────────────────────────────

    /**
     * Current scrim progress (0 = no effect, [AnimationConfig.navOverlayAlpha] = peak effect).
     * Updated by [scrimAnimator]; triggers [invalidate] via [addUpdateListener].
     */
    private var scrimAlpha: Float = 0f

    /**
     * The panel child the scrim is drawn on top of in [drawChild].
     * Null when no scrim is active. Checked with identity equality (===) so a stale
     * reference after panel removal is harmless — removed views are never passed to [drawChild].
     */
    private var scrimTarget: View? = null

    /** Tracks in-flight scrim animation so rapid nav changes cancel the previous one cleanly. */
    private var scrimAnimator: ValueAnimator? = null

    /**
     * Reused across [drawChild] calls — avoids per-frame allocation.
     * Xfermode is set once at init: [PorterDuff.Mode.MULTIPLY] makes darkening proportional
     * to the background luminance, giving consistent perceived contrast on any theme.
     */
    private val scrimPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
    }

    // ── Panel transitions ─────────────────────────────────────────────────────────────────
    // Ported directly from saket/cascade's HeightAnimatableViewFlipper.show().

    /**
     * Shows [view] as the new panel, animating width and height simultaneously.
     *
     * Slides [view] in from the right when [forward] = true (navigating deeper), or from
     * the left when [forward] = false (navigating back). [toWidth] is the natural width of
     * the new level's panel content.
     *
     * For the first panel (no existing children), the width clip is applied immediately and
     * the height clip is initialised after layout — no size animation runs.
     *
     * For subsequent panels, the incoming view is pre-measured so [animateSize] starts
     * immediately alongside the slide animation, eliminating the doOnLayout delay that
     * previously caused the background clip to lag behind the panel translation.
     *
     * If [sizeAnimator] is already running, the incoming navigation is enqueued and starts
     * immediately after the current animation ends — no overlapping transitions.
     */
    fun show(view: View, forward: Boolean, toWidth: Int) {
        enqueueAnimation {
            // Forward → append at tail so it slides in from the right.
            // Backward → insert at head so it slides in from the left.
            val index = if (forward) childCount else 0
            super.addView(view, index, generateDefaultLayoutParams())

            if (childCount == 1) {
                // First panel: set width clip immediately; defer height until layout resolves
                // the natural panel height, then establish clipBounds2 for touch dispatch.
                currentClippedWidth = toWidth
                widthClip?.clippedWidth = toWidth
                doOnLayout {
                    currentClippedHeight = height + verticalPadding
                    updateClipBounds()
                }
                return@enqueueAnimation
            }

            val prevPanel = displayedChildView!!
            val fromWidth = currentClippedWidth

            // Pre-measure the incoming view so animateSize can start immediately without
            // waiting for a layout pass. Using EXACTLY for width matches the real layout
            // constraint (MATCH_PARENT inside a fixed-width popup window). The measured
            // height is reliable for LinearLayouts with fixed-height item rows.
            val exactW = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
            val unspecH = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            view.measure(exactW, unspecH)
            val toH = view.measuredHeight + view.verticalPadding
            val fromH = prevPanel.height + prevPanel.verticalPadding

            // Record transition state so drawChild positions backgrounds correctly.
            transitionInView = view
            transitionFromWidth = fromWidth
            transitionToWidth = toWidth
            isTransitioning = true

            // Scrim target: the panel that slides left (outgoing on forward, incoming on back).
            animateNavOverlay(target = if (forward) prevPanel else view, forward = forward)

            setDisplayedChild(
                inView = view,
                inAnimator = {
                    it.translationX = if (forward) width.toFloat() else -(width.toFloat() * 0.25f)
                    it.animate()
                        .translationX(0f)
                        .setDuration(anim.navigationDuration)
                        .setInterpolator(navInterpolator)
                },
                outAnimator = {
                    it.translationX = 0f
                    it.animate()
                        .translationX(if (!forward) width.toFloat() else -(width.toFloat() * 0.25f))
                        .setDuration(anim.navigationDuration)
                        .setInterpolator(navInterpolator)
                }
            )

            // Start size animation immediately — no doOnLayout delay — so the clip boundary
            // moves in lock-step with the slide translation from the very first frame.
            animateSize(
                fromW = fromWidth,
                toW = toWidth,
                fromH = fromH,
                toH = toH,
                onEnd = {
                    removeView(prevPanel)
                    transitionInView = null
                    isTransitioning = false
                    onPanelSwitchComplete?.invoke()
                }
            )
        }
    }

    // ── Entry / exit animations ───────────────────────────────────────────────────────────

    /**
     * Animates the popup into view: the flipper (card) scales/fades in while item rows
     * cascade in with staggered slide+fade.
     *
     * Must be called after [android.widget.PopupWindow.showAsDropDown] so the view has
     * correct [measuredWidth] for pivot calculations.
     *
     * The expand interpolator is [AnimationConfig.entryExpandInterpolator] — defaults to
     * [android.view.animation.LinearInterpolator] for a uniform feel. The counter-scale
     * applied to [scrollView] uses the same interpolator since it is the mathematical
     * inverse of the card scale and must track it exactly.
     *
     * @param scrollView The inner ScrollView of the root panel, counter-scaled so items
     *                   do not appear squished while the card's scaleY grows from 0 to 1.
     * @param itemViews  The root panel's item rows for the staggered cascade animation.
     */
    fun startEntryAnimation(scrollView: View, itemViews: List<View>) {
        val expandInterp = anim.entryExpandInterpolator
        val slideInterp = DecelerateInterpolator(anim.itemSlideDeceleration)
        val itemCount = itemViews.size

        // Expand from the top-right corner: right edge fixed (pivotX = width), top edge
        // fixed (pivotY = 0). The card grows downward, toward the content.
        pivotX = measuredWidth.toFloat()
        pivotY = 0f
        alpha = 0f
        scaleY = 0f

        // The scrollView counter-scale shares pivotY = 0f so the 1/scale correction
        // cancels the distortion at the same anchor point.
        scrollView.pivotY = 0f
        scrollView.scaleY = 0f

        // Hide all items at their starting state before the cascade begins.
        itemViews.forEach { v ->
            v.alpha = 0f
            v.translationY = -context.dp(anim.itemSlideDistanceDp)
        }

        // Counter-scale content to prevent squishing during container expand.
        val counterScaleAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = anim.entryExpandDuration
            interpolator = expandInterp
            addUpdateListener {
                val scale = it.animatedValue as Float
                scrollView.scaleY = if (scale > 0f) 1f / scale else 0f
            }
        }

        // Total duration covers the last item's start offset plus its own full duration.
        val totalCascade =
            anim.cascadeStaggerMs * (itemCount - 1).coerceAtLeast(0) + anim.cascadeItemDurationMs
        val cascadeAnim = ValueAnimator.ofFloat(0f, totalCascade.toFloat()).apply {
            duration = totalCascade
            addUpdateListener {
                val elapsed = it.animatedValue as Float
                itemViews.forEachIndexed { i, v ->
                    val progress =
                        ((elapsed - i * anim.cascadeStaggerMs) / anim.cascadeItemDurationMs)
                            .coerceIn(0f, 1f)
                    v.alpha = progress
                    v.translationY =
                        (1f - slideInterp.getInterpolation(progress)) *
                                -context.dp(anim.itemSlideDistanceDp)
                }
            }
        }

        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(this@MenuFlipper, "scaleY", 0f, 1f).apply {
                    duration = anim.entryExpandDuration; interpolator = expandInterp
                },
                ObjectAnimator.ofFloat(this@MenuFlipper, "alpha", 0f, 1f).apply {
                    duration = anim.entryFadeDuration; interpolator = expandInterp
                },
                counterScaleAnim,
                cascadeAnim
            )
            start()
        }
    }

    /**
     * Fades and slides the flipper out, then calls [onEnd] to dismiss the popup window.
     * Any in-flight size animation continues concurrently — since alpha heads to 0 it is
     * invisible and harmless.
     */
    fun startExitAnimation(onEnd: () -> Unit) {
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(this@MenuFlipper, "alpha", 1f, 0f),
                ObjectAnimator.ofFloat(
                    this@MenuFlipper, "translationY",
                    0f, -context.dp(anim.exitSlideDistanceDp)
                )
            )
            duration = anim.exitDuration
            interpolator = DecelerateInterpolator()
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) = onEnd()
            })
            start()
        }
    }

    // ── Size animation ────────────────────────────────────────────────────────────────────

    /**
     * Animates both clip dimensions together from ([fromW], [fromH]) to ([toW], [toH]).
     *
     * A single [sizeAnimator] drives both axes with the same [FastOutSlowInInterpolator]
     * and [AnimationConfig.navigationDuration], so width and height always reach their
     * targets simultaneously — no tearing at the rounded corner.
     *
     * [onEnd] fires when the animation completes (NOT when cancelled), so callers can
     * safely remove the outgoing panel and invoke [onPanelSwitchComplete].
     */
    private fun animateSize(fromW: Int, toW: Int, fromH: Int, toH: Int, onEnd: () -> Unit) {
        sizeAnimator.cancel()
        sizeAnimator = ObjectAnimator.ofFloat(0f, 1f).apply {
            duration = anim.navigationDuration
            interpolator = FastOutSlowInInterpolator()
            addUpdateListener {
                val t = it.animatedValue as Float
                setClippedSize(
                    w = ((toW - fromW) * t + fromW).toInt(),
                    h = ((toH - fromH) * t + fromH).toInt()
                )
            }
            doOnEnd { onEnd() }
            start()
        }
    }

    /**
     * Applies [w] and [h] as the current clip dimensions, propagates them through the
     * drawable chain, refreshes [clipBounds2] for touch dispatch, then invalidates.
     * No measure/layout pass is triggered.
     *
     * Width is applied first so the left boundary of [heightClip]'s delegate bounds is
     * already correct when the height setter re-applies its own bounds in the second step.
     */
    private fun setClippedSize(w: Int, h: Int) {
        currentClippedWidth = w
        currentClippedHeight = h
        widthClip?.clippedWidth = w   // propagates: widthClip → heightClip bounds
        heightClip?.clippedHeight = h // re-applies with correct left from previous step
        updateClipBounds()
        invalidate()
    }

    /**
     * Rebuilds [clipBounds2] from the current clipped dimensions.
     *
     * Coordinate system: [width] = MenuFlipper's own width (= popup maxWidth, left = 0).
     * The visible region is the rightmost [currentClippedWidth] pixels × top [currentClippedHeight] pixels:
     *   left  = width - currentClippedWidth  (left edge of visible panel content)
     *   right = width                         (always fixed — anchored to trigger button)
     *   top   = 0                             (fixed)
     *   bottom= currentClippedHeight          (animated)
     */
    private fun updateClipBounds() {
        val vw = width
        clipBounds2 = (clipBounds2 ?: Rect()).also {
            it.set(vw - currentClippedWidth, 0, vw, currentClippedHeight)
        }
    }

    /**
     * Runs [action] immediately if no size animation is in-flight, otherwise enqueues
     * it to start as soon as the current animation ends.
     */
    private fun enqueueAnimation(action: () -> Unit) {
        if (!sizeAnimator.isRunning) action()
        else sizeAnimator.doOnEnd { action() }
    }

    // ── Nav scrim ─────────────────────────────────────────────────────────────────────────

    /**
     * Starts a [scrimAlpha] animation that darkens [target] during the panel transition.
     *
     *   Forward nav: [target] = outgoing panel. [scrimAlpha] animates 0 → [AnimationConfig.navOverlayAlpha].
     *   Back nav:    [target] = incoming panel. [scrimAlpha] animates [AnimationConfig.navOverlayAlpha] → 0.
     *
     * The scrim is drawn via [PorterDuff.Mode.MULTIPLY] in [drawChild], which darkens each
     * pixel proportionally to its luminance — the perceived effect is identical on light and
     * dark backgrounds. The scrim translates with the panel via [child.translationX] so no
     * separate translation animation is needed here.
     *
     * [doOnEnd] always resets to a clean state (scrimAlpha = 0, target = null) so the scrim
     * never bleeds into idle frames regardless of animation direction.
     */
    private fun animateNavOverlay(target: View, forward: Boolean) {
        scrimTarget = target
        scrimAnimator?.cancel()
        val startAlpha = if (forward) 0f else anim.navOverlayAlpha
        val endAlpha = if (forward) anim.navOverlayAlpha else 0f
        scrimAlpha = startAlpha
        scrimAnimator = ValueAnimator.ofFloat(startAlpha, endAlpha).apply {
            duration = anim.navigationDuration
            interpolator = navInterpolator
            addUpdateListener { scrimAlpha = it.animatedValue as Float; invalidate() }
            doOnEnd { scrimAlpha = 0f; scrimTarget = null; invalidate() }
            start()
        }
    }

    // ── Drawable overrides ────────────────────────────────────────────────────────────────

    /**
     * Wraps [background] in the full clip chain:
     *   [WidthClipDrawable] → [HeightClipDrawable] → [ForcePaddingsDrawable] → [background]
     *
     * [WidthClipDrawable] is outermost so its bounds form the outline consumed by
     * [clipToOutline] = true, giving correct combined width + height clipping when stacked
     * with [HeightClipDrawable] as the inner layer.
     * Direct references to both clip layers are stored for per-frame animation updates
     * without traversing the drawable chain.
     */
    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun setBackgroundDrawable(background: Drawable?) {
        widthClip = null
        heightClip = null
        if (background == null) {
            super.setBackgroundDrawable(null)
        } else {
            val hc = HeightClipDrawable(ForcePaddingsDrawable(background))
            val wc = WidthClipDrawable(hc)
            heightClip = hc
            widthClip = wc
            super.setBackgroundDrawable(wc)
        }
    }

    // ── Drawing overrides ─────────────────────────────────────────────────────────────────

    /**
     * Draws each panel's background as a solid rect positioned exactly where the panel's
     * content items are, then draws the child, then overlays the nav scrim.
     *
     * Background sync fix:
     *   The rect is drawn at [w - panelWidth + tx, w + tx] in MenuFlipper's coordinate
     *   space — the same position as the inner content container (Gravity.END, panelWidth
     *   wide, outer frame translatedX = tx). This keeps the card background and items
     *   perfectly in sync from the very first animation frame, regardless of when the
     *   size animator starts.
     *
     *   Compared to the previous approach (translating the background drawable), this
     *   fixes the desync caused by the drawable bounds being anchored to [clipLeft] rather
     *   than [w - toWidth] when navigating to a narrower level.
     *
     * Background for single panel: handled by the view system drawing MenuFlipper's own
     *   WidthClipDrawable background before dispatching to children — no manual drawing
     *   needed for the single-child case.
     *
     * Scrim: after drawing the child's own content, a filled rect is drawn at
     *   [child.translationX] using [PorterDuff.Mode.MULTIPLY] with a gray tone derived from
     *   [scrimAlpha]. [clipToOutline] clips this rect to the current animated card boundary.
     */
    override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
        if (childCount > 1) {
            // panelWidth = the natural content width of this specific panel.
            // Incoming panel → toWidth; outgoing panel → fromWidth.
            // The rect [w - panelWidth + tx, w + tx] matches the items' screen position exactly.
            val panelWidth = if (child === transitionInView) transitionToWidth else transitionFromWidth
            val tx = child.translationX
            canvas.drawRect(
                width - panelWidth + tx, 0f,
                width + tx, height.toFloat(),
                panelBgPaint
            )
        }
        val result = super.drawChild(canvas, child, drawingTime)
        if (scrimAlpha > 0f && child === scrimTarget) {
            // Map scrimAlpha → gray: 0 = white (identity), navOverlayAlpha = darkened gray.
            val gray = ((1f - scrimAlpha) * 255).toInt()
            scrimPaint.color = Color.rgb(gray, gray, gray)
            canvas.withTranslation(x = child.translationX) {
                drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)
            }
        }
        return result
    }

    // ── Touch overrides ───────────────────────────────────────────────────────────────────

    /**
     * Blocks all touch input while a panel-switch animation is in flight, then rejects
     * events outside the current clip bounds with a synthesized ACTION_CANCEL so any
     * pressed child releases its ripple cleanly.
     *
     * Blocking during [isTransitioning] prevents item presses from interrupting the slide
     * animation and ensures the ripple on the tapped navigation item has time to complete
     * before the next panel appears.
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // Block all input while panels are switching.
        if (isTransitioning) return false

        if (clipBounds2 != null && !clipBounds2!!.contains(ev.x.toInt(), ev.y.toInt())) {
            val maskedAction = ev.actionMasked
            if (maskedAction == MotionEvent.ACTION_MOVE || maskedAction == MotionEvent.ACTION_UP) {
                val cancel = MotionEvent.obtain(ev).apply { action = MotionEvent.ACTION_CANCEL }
                super.dispatchTouchEvent(cancel)
                cancel.recycle()
            }
            return false
        }
        return super.dispatchTouchEvent(ev)
    }

    // ── ViewGroup boilerplate ─────────────────────────────────────────────────────────────

    /**
     * Every panel added to the flipper gets MATCH_PARENT width so it spans the full
     * [maxWidth] popup window. Right-alignment of the actual content is handled inside
     * each panel via [android.view.Gravity.END] on the inner container (see [MenuPanelBuilder]).
     */
    override fun generateDefaultLayoutParams(): LayoutParams =
        LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)

    override fun onDetachedFromWindow() {
        sizeAnimator.cancel()
        scrimAnimator?.cancel()
        super.onDetachedFromWindow()
    }
}

/** Vertical padding (top + bottom) contributed by a view's padding values. */
private val View.verticalPadding: Int get() = paddingTop + paddingBottom

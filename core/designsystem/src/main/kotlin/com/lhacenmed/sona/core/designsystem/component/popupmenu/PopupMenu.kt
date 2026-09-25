package com.lhacenmed.sona.core.designsystem.component.popupmenu

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.animation.DecelerateInterpolator
import android.widget.PopupWindow
import androidx.annotation.RequiresApi
import com.lhacenmed.sona.core.designsystem.effect.SonaEffects

/**
 * A [PopupWindow] that supports unlimited cascading sub-menus by sliding panels in and
 * out of a [MenuFlipper], mirroring the navigation algorithm from saket/cascade.
 *
 * Architecture (mirrors CascadePopupMenu + CascadePopupWindow in a single class):
 *   - [MenuFlipper] is the direct [contentView] — its [WidthClipDrawable] + [HeightClipDrawable]
 *     background chain combined with [android.view.View.clipToOutline] = true is what makes
 *     both width and height transitions smooth without layout passes (see [MenuFlipper]).
 *   - [MenuPanelBuilder] constructs each level's view hierarchy on demand.
 *   - A [StackEntry] backstack (ArrayDeque) tracks navigation depth, caching the panel
 *     [View], its item views, and its [levelWidth] alongside the [MenuLevel] so
 *     back-navigation can restore the exact width and panel without remeasuring.
 *   - Touch forwarding lets a drag-to-open gesture highlight items without a second tap.
 *
 * Width contract:
 *   The popup window is always [maxWidth] = [measureAllWidths] pixels wide. Each level
 *   has its own [currentLevelWidth] = [measureLevelWidth] pixels. Panels are right-aligned
 *   inside the window so the right edge stays fixed at the anchor button while the left
 *   edge animates as the user navigates between levels of different natural widths.
 *
 *   Per-level width overrides ([MenuItem.subMenuMaxWidthDp]) are applied in [navigateTo]
 *   identically to how [MenuPanelBuilder.measureAllWidths] accounts for them during
 *   pre-measurement, so the popup window is always wide enough for every level.
 *
 * Shadow:
 *   Elevation is sourced from [PopupStyle.elevationDp] and controls shadow spread.
 *   On API 28+, shadow color is applied via [View.outlineSpotShadowColor] and
 *   [View.outlineAmbientShadowColor] on the content view when [PopupStyle.shadowColor]
 *   is non-null.
 *
 * Predictive back gesture (API 33+):
 *   After [show], the popup registers a callback on its own window's
 *   [android.window.OnBackInvokedDispatcher] — no external dispatcher or lifecycle owner
 *   is required. On API 34+, [android.window.OnBackAnimationCallback] provides per-frame
 *   [android.window.BackEvent.progress]: the card fades and slides upward proportionally
 *   to the swipe distance, matching [MenuFlipper.startExitAnimation]'s exit style.
 *   Cancelling the gesture animates the card back to rest. On API 33, back simply
 *   triggers dismiss() without a progress animation. The callback is unregistered in
 *   [dismiss] so it never fires on an already-dismissed window.
 */
class PopupMenu(
    context: Context,
    private val rootItems: List<MenuItem>,
    private val style: PopupStyle,
    private val animConfig: AnimationConfig = AnimationConfig(),
    private val onItemClick: (MenuItem) -> Boolean
) : PopupWindow(context) {

    // Captured explicitly — PopupWindow.getContext() resolves as a function reference in
    // Kotlin, breaking extension-function resolution on the Context receiver.
    private val ctx = context

    private val menuFlipper = MenuFlipper(context, animConfig)
    private val panelBuilder = MenuPanelBuilder(context, style)

    // Kept so updateColors can repaint the card, which once set sits deep in the flipper's clip chain.
    private val cardBackground = GradientDrawable().apply {
        setColor(style.backgroundColor)
        cornerRadius = context.dp(style.cornerRadiusDp)
    }

    // Pre-computed once so navigateTo/navigateBack can call measureLevelWidth without
    // repeating the dp→px conversion on every navigation event.
    private val minWidthPx = ctx.dp(style.minWidthDp).toInt()
    private val maxWidthPx = ctx.dp(style.maxWidthDp).toInt()

    // ── Predictive back state ─────────────────────────────────────────────────────────────
    // Stored as Any? to avoid @RequiresApi annotations on fields — cast at use sites inside
    // API-gated helpers. Both are cleared in unregisterPredictiveBack() so repeated calls
    // are no-ops.

    private var backDispatcher: Any? = null  // android.window.OnBackInvokedDispatcher on API 33+
    private var backCallback: Any? = null    // android.window.OnBackInvokedCallback   on API 33+

    // True once predictive back has committed (onBackInvoked fired). Tells dismiss() to
    // skip startExitAnimation — the gesture itself is the exit animation.
    private var predictiveBackDismiss = false

    /**
     * Snapshot of a menu level together with the [View] that renders it.
     *
     * Caching [panel] and [panelItemViews] is the key to correct back-navigation:
     * the panel's overlay [View] (stored as [panel].tag) retains whatever alpha the
     * forward-navigation animation left it at, so [MenuFlipper.animateNavOverlay] can
     * fade it back to 0 when the user navigates back — matching the forward animation.
     *
     * [levelWidth] is stored so navigating back can restore the correct clip width without
     * remeasuring, even after multiple forward navigations to levels of varying widths.
     */
    private data class StackEntry(
        val level: MenuLevel,
        val panel: View,
        val panelItemViews: List<View>,
        val levelWidth: Int
    )

    private val backstack = ArrayDeque<StackEntry>()
    private var currentLevel = MenuLevel(rootItems, null)

    // Item views (header row excluded) for the currently displayed panel.
    private var itemViews = listOf<View>()

    // Natural width of the currently displayed level's panel content.
    // Updated on every navigation event and used by recalculateBoundsImmediate()
    // to compute windowLeft as the actual left screen edge of the visible panel.
    private var currentLevelWidth = 0

    // ── Hit-test state ────────────────────────────────────────────────────────────────────
    // Populated after each panel switch; cleared (boundsReady = false) while a transition
    // is in-flight so stale coordinates from the previous level are never used.
    private var boundsReady = false
    private var windowLeft = 0
    private var windowRight = 0
    private var itemTops = IntArray(0)
    private var itemBottoms = IntArray(0)

    // Index of the item currently visually pressed via touch forwarding. -1 = none.
    private var hoveredIndex = -1

    // Set when a chosen item opens an activity: the popup stays up until hidden, then closes unanimated.
    private var closeOnceHidden = false

    init {
        // Apply the card background directly to the flipper so setBackgroundDrawable wraps
        // it in WidthClipDrawable(HeightClipDrawable(...)). clipToOutline then clips all
        // child rendering to the animated background outline.
        menuFlipper.background = cardBackground
        menuFlipper.clipToOutline = true

        // Provide the background color so MenuFlipper can draw per-panel background rects
        // that are perfectly in sync with the panel items during navigation transitions.
        menuFlipper.panelBgColor = style.backgroundColor

        // Apply shadow color to spot and ambient layers on API 28+.
        // The shadow itself is rendered by the WindowManager using the content view's
        // outline; these properties tint both shadow components independently.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && style.shadowColor != null) {
            menuFlipper.outlineSpotShadowColor = style.shadowColor
            menuFlipper.outlineAmbientShadowColor = style.shadowColor
        }

        // Re-enable hit-testing once the outgoing panel is fully removed.
        menuFlipper.onPanelSwitchComplete = { recalculateBounds() }

        // A popup left up for an activity closes once that activity has covered it.
        menuFlipper.onWindowHidden = { if (closeOnceHidden) dismissWithoutAnimation() }

        contentView = menuFlipper
        height = WRAP_CONTENT
        isFocusable = true
        isOutsideTouchable = true
        // No window animation. Left at its default, a drop-down PopupWindow scales and fades its whole
        // window as it opens and closes - on top of the entry and exit animations the card runs itself.
        animationStyle = 0
        elevation = context.dp(style.elevationDp)
        setBackgroundDrawable(null)
    }

    @SuppressLint("ClickableViewAccessibility")
    fun show(anchor: View) {
        // maxWidth = widest level across the whole tree → fixed popup window width.
        // rootWidth = natural width of the root level only → initial clip target.
        val maxWidth = panelBuilder.measureAllWidths(rootItems, minWidthPx, maxWidthPx)
        val rootWidth = panelBuilder.measureLevelWidth(rootItems, null, minWidthPx, maxWidthPx)
        width = maxWidth
        currentLevelWidth = rootWidth

        // Build the root panel with its exact natural width. The outer FrameLayout is
        // MATCH_PARENT so it fills the window; the inner container is right-aligned at
        // exactly rootWidth pixels via Gravity.END.
        val rootPanel = panelBuilder.createPanel(
            level = currentLevel,
            panelWidth = rootWidth,
            onNavigateBack = ::navigateBack,
            onNavigateTo = ::navigateTo,
            onItemClick = ::chooseItem
        )
        itemViews = rootPanel.itemViews
        menuFlipper.show(rootPanel.panel, forward = true, toWidth = rootWidth)

        // Pre-measure so startEntryAnimation has correct measuredWidth for pivotX before
        // the window layout runs asynchronously after showAsDropDown.
        val exactW = View.MeasureSpec.makeMeasureSpec(maxWidth, View.MeasureSpec.EXACTLY)
        val unspecH = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        menuFlipper.measure(exactW, unspecH)

        // Dismiss the popup when the user taps the invisible left region of the window.
        //
        // Context: the popup window is always maxWidth pixels wide. When a sub-menu is
        // narrower than maxWidth, its panel is right-aligned and the left
        // (maxWidth − currentLevelWidth) pixels are visually clipped by WidthClipDrawable.
        // Those pixels are still owned by the PopupWindow however — the window's decor
        // consumes touches there even though MenuFlipper.dispatchTouchEvent returns false.
        // Without this interceptor the user cannot tap through the invisible region to
        // dismiss the popup or interact with the content behind it.
        //
        // Guard conditions:
        //   boundsReady          — windowLeft is only valid after the first layout pass.
        //   !isTransitioning     — during panel slide the clip is animating; stale
        //                          windowLeft could cause a false dismiss.
        setTouchInterceptor { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN
                && boundsReady
                && !menuFlipper.isTransitioning
                && event.rawX.toInt() < windowLeft
            ) {
                dismiss()
                true
            } else {
                false
            }
        }

        // Right-align popup with the anchor; position directly above it.
        showAsDropDown(anchor, anchor.width - maxWidth, -anchor.height)

        menuFlipper.startEntryAnimation(rootPanel.scrollView, rootPanel.itemViews)

        // Both operations need the view to be attached to a window (next frame after show).
        contentView.post {
            recalculateBoundsImmediate()
            registerPredictiveBack()
        }
    }

    override fun dismiss() {
        cancelHoveredItem()
        unregisterPredictiveBack()
        if (predictiveBackDismiss) {
            // Predictive back already animated the exit — dismiss the window immediately.
            super.dismiss()
        } else {
            menuFlipper.startExitAnimation { super.dismiss() }
        }
    }

    /**
     * Reports [item] chosen, then closes the popup the way a native popup menu does - which depends on
     * what the item did.
     *
     * [onItemClick] answers whether the item opened another activity. If it did, the popup is left
     * exactly as it is, for the activity to slide in over, and is removed without animation once it is
     * out of sight: animating it out would stall part-way, because starting an activity takes the main
     * thread the animation runs on. Any other item closes the popup with its exit animation.
     */
    private fun chooseItem(item: MenuItem) {
        if (onItemClick(item)) closeOnceHidden = true else dismiss()
    }

    /** Removes the popup at once - for when it is already out of sight, with nothing to animate for. */
    private fun dismissWithoutAnimation() {
        cancelHoveredItem()
        unregisterPredictiveBack()
        super.dismiss()
    }

    /**
     * Repaints the popup in [newStyle]'s colours - the card, its shadow and every panel, the ones kept on
     * the backstack included - so an open menu follows a theme change as it happens. Sizes and motion
     * stay as they were when it was shown.
     */
    fun updateColors(newStyle: PopupStyle) {
        cardBackground.setColor(newStyle.backgroundColor)
        menuFlipper.panelBgColor = newStyle.backgroundColor
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && newStyle.shadowColor != null) {
            menuFlipper.outlineSpotShadowColor = newStyle.shadowColor
            menuFlipper.outlineAmbientShadowColor = newStyle.shadowColor
        }
        // Mid-transition, the outgoing panel is both still shown and already on the backstack.
        val panels = (List(menuFlipper.childCount, menuFlipper::getChildAt) + backstack.map { it.panel }).distinct()
        panelBuilder.restyle(newStyle, panels)
        menuFlipper.invalidate()
    }

    // ── Predictive back ───────────────────────────────────────────────────────────────────

    /**
     * Registers the appropriate back callback on the popup window's own dispatcher.
     *
     * Must be called after [showAsDropDown] so [View.findOnBackInvokedDispatcher] has a
     * window to query. We use [contentView.post] in [show] to guarantee this.
     *
     * API 34+: [createAnimationCallback] for per-frame progress animation.
     * API 33:  [createInvokedCallback] for simple dismiss-on-back with no progress.
     * API <33: no-op — back is handled by the legacy KEYCODE_BACK pipeline.
     */
    private fun registerPredictiveBack() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            registerAnimationCallback()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerInvokedCallback()
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun registerAnimationCallback() {
        val dispatcher = menuFlipper.findOnBackInvokedDispatcher() ?: return
        val cb = createAnimationCallback()
        dispatcher.registerOnBackInvokedCallback(
            android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb
        )
        backDispatcher = dispatcher
        backCallback = cb
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun registerInvokedCallback() {
        val dispatcher = menuFlipper.findOnBackInvokedDispatcher() ?: return
        val cb = createInvokedCallback()
        dispatcher.registerOnBackInvokedCallback(
            android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb
        )
        backDispatcher = dispatcher
        backCallback = cb
    }

    /**
     * Unregisters the back callback and clears state. Safe to call multiple times —
     * nulling out the references makes subsequent calls no-ops.
     */
    private fun unregisterPredictiveBack() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            unregisterCallback33()
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun unregisterCallback33() {
        val dispatcher = backDispatcher as? android.window.OnBackInvokedDispatcher
        val cb = backCallback as? android.window.OnBackInvokedCallback
        if (dispatcher != null && cb != null) {
            dispatcher.unregisterOnBackInvokedCallback(cb)
        }
        backDispatcher = null
        backCallback = null
    }

    /**
     * API 33 fallback — dismisses without a progress animation. The system provides its
     * own predictive-back preview for focus windows on API 33, so no manual animation
     * is needed here.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun createInvokedCallback(): android.window.OnBackInvokedCallback =
        android.window.OnBackInvokedCallback { dismiss() }

    /**
     * API 34+ — animates [menuFlipper] alpha and translationY in sync with the back
     * swipe progress, mirroring [MenuFlipper.startExitAnimation]'s exit style.
     *
     * [onBackProgressed]: maps progress (0→1) to alpha (1→0) and translationY (0→-exitSlide).
     * [onBackInvoked]:    committed — sets [predictiveBackDismiss] immediately so concurrent
     *                     dismiss() calls skip startExitAnimation, then completes the exit
     *                     with an 80ms snap-out from whatever state the gesture left behind.
     * [onBackCancelled]:  gesture aborted — animate card back to rest (alpha 1, translationY 0).
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun createAnimationCallback(): android.window.OnBackAnimationCallback =
        object : android.window.OnBackAnimationCallback {
            override fun onBackProgressed(backEvent: android.window.BackEvent) {
                val p = backEvent.progress
                menuFlipper.alpha = 1f - p
                menuFlipper.translationY = -ctx.dp(animConfig.exitSlideDistanceDp) * p
            }

            override fun onBackInvoked() {
                // Set the flag first so any concurrent dismiss() (e.g. outside touch racing
                // against this callback) also skips startExitAnimation.
                predictiveBackDismiss = true
                // Snap-out from current partial state — brief so it feels responsive.
                menuFlipper.animate()
                    .alpha(0f)
                    .translationY(-ctx.dp(animConfig.exitSlideDistanceDp))
                    .setDuration(SonaEffects.animationDuration(80L))
                    .setInterpolator(DecelerateInterpolator())
                    .withEndAction { dismiss() }
                    .start()
            }

            override fun onBackCancelled() {
                // Gesture aborted — restore card to its resting state.
                menuFlipper.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(SonaEffects.animationDuration(150L))
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
        }

    // ── Navigation ────────────────────────────────────────────────────────────────────────

    /**
     * Push current level + panel onto the backstack, slide the new panel in from the right.
     *
     * The effective max width for the new level is resolved from [MenuItem.subMenuMaxWidthDp]
     * when set, falling back to [maxWidthPx]. This mirrors [MenuPanelBuilder.measureAllWidths]
     * exactly, so the popup window is guaranteed to be wide enough for the resolved width.
     */
    private fun navigateTo(item: MenuItem) {
        val subItems = item.subItems ?: return
        boundsReady = false
        // Capture the current panel view so navigateBack() can restore it — along with
        // its overlay alpha — instead of creating a fresh panel with alpha = 0.
        val currentPanel = menuFlipper.displayedChildView ?: return
        backstack.addLast(StackEntry(currentLevel, currentPanel, itemViews, currentLevelWidth))
        currentLevel = MenuLevel(subItems, item.title)
        // Resolve per-item max width override, clamped to the global bounds so the popup
        // window (sized at show-time) is never narrower than this level's panel.
        val effectiveMaxPx = item.subMenuMaxWidthDp
            ?.let { ctx.dp(it).toInt().coerceIn(minWidthPx, maxWidthPx) }
            ?: maxWidthPx
        val newWidth = panelBuilder.measureLevelWidth(subItems, item.title, minWidthPx, effectiveMaxPx)
        currentLevelWidth = newWidth
        showNewLevel(forward = true, panelWidth = newWidth)
    }

    /** Pop the backstack, slide the cached previous panel back in from the left. */
    private fun navigateBack() {
        if (backstack.isEmpty()) return
        boundsReady = false
        val entry = backstack.removeLast()
        currentLevel = entry.level
        // Restore cached item views before the transition ends so recalculateBoundsImmediate()
        // (fired from onPanelSwitchComplete) uses the correct set.
        itemViews = entry.panelItemViews
        currentLevelWidth = entry.levelWidth
        // Reuse the cached panel view — its overlay is already at navOverlayAlpha from the
        // forward navigation, so MenuFlipper.animateNavOverlay can fade it back to 0.
        menuFlipper.show(entry.panel, forward = false, toWidth = entry.levelWidth)
    }

    /** Creates and shows a brand-new panel for the current level (forward navigation only). */
    private fun showNewLevel(forward: Boolean, panelWidth: Int) {
        val result = panelBuilder.createPanel(
            level = currentLevel,
            panelWidth = panelWidth,
            onNavigateBack = ::navigateBack,
            onNavigateTo = ::navigateTo,
            onItemClick = ::chooseItem
        )
        itemViews = result.itemViews
        menuFlipper.show(result.panel, forward, toWidth = panelWidth)
    }

    // ── Bounds management ─────────────────────────────────────────────────────────────────

    /**
     * Posts [recalculateBoundsImmediate] to the next frame so any pending layout from the
     * panel transition is guaranteed to have completed before coordinates are captured.
     */
    private fun recalculateBounds() {
        contentView.post { recalculateBoundsImmediate() }
    }

    /**
     * Snapshots all item-view screen-space extents immediately (no post).
     *
     * Because panels are right-aligned, the visible content starts at:
     *   windowLeft = contentView.screenLeft + (maxWidth - currentLevelWidth)
     *
     * This correctly rejects touches in the invisible left portion of the popup window when
     * the current level is narrower than [maxWidth], preventing phantom item highlights.
     *
     * Each item is placed where it is laid out, not where it is drawn: the bounds are taken while the
     * entry animation still has the card scaled almost flat from its top, and read off the screen then,
     * every row would sit in a sliver at the top - leaving only the first one under a dragging finger,
     * for good, as nothing takes the bounds again once the card has grown.
     */
    private fun recalculateBoundsImmediate() {
        val views = itemViews
        val n = views.size
        itemTops = IntArray(n)
        itemBottoms = IntArray(n)
        val loc = IntArray(2)
        contentView.getLocationOnScreen(loc)
        // Items live in the rightmost currentLevelWidth pixels of the popup window.
        windowLeft = loc[0] + contentView.width - currentLevelWidth
        windowRight = loc[0] + contentView.width
        views.forEachIndexed { i, view ->
            itemTops[i] = loc[1] + laidOutTopIn(contentView, view)
            itemBottoms[i] = itemTops[i] + view.height
        }
        boundsReady = true
    }

    /**
     * How far below [ancestor]'s top [view] is laid out - through each parent's scroll, but none of
     * the translation or scale an animation draws it with.
     */
    private fun laidOutTopIn(ancestor: View, view: View): Int {
        var top = 0
        var current = view
        while (current !== ancestor) {
            val parent = current.parent as View
            top += current.top - parent.scrollY
            current = parent
        }
        return top
    }

    // ── Touch forwarding ──────────────────────────────────────────────────────────────────

    /**
     * Forwards drag events from the parent view (which retains the touch sequence after
     * ACTION_DOWN) to this popup so items highlight as the finger drags over them without
     * requiring a second tap.
     *
     * Ignored entirely while a panel-switch transition is in flight ([MenuFlipper.isTransitioning])
     * so drag-released item taps cannot trigger navigation during the slide animation.
     *
     * @param rawX   Raw screen X from the parent's MotionEvent.
     * @param rawY   Raw screen Y from the parent's MotionEvent.
     * @param action [MotionEvent.ACTION_MOVE], [MotionEvent.ACTION_UP], or [MotionEvent.ACTION_CANCEL].
     */
    fun forwardTouchEvent(rawX: Float, rawY: Float, action: Int) {
        if (!isShowing || menuFlipper.isTransitioning) return
        when (action) {
            MotionEvent.ACTION_MOVE -> {
                val hit = findItemIndexAt(rawX, rawY)
                if (hit != hoveredIndex) {
                    cancelHoveredItem()
                    hoveredIndex = hit
                    if (hit >= 0) pressItem(hit, rawX, rawY)
                }
            }
            MotionEvent.ACTION_UP -> {
                val hit = findItemIndexAt(rawX, rawY)
                if (hit >= 0 && hit == hoveredIndex) {
                    // Release on the same item → ripple completes, OnClickListener fires.
                    releaseItem(hit)
                } else {
                    cancelHoveredItem()
                    // Lifted on a disabled item — nothing happens, as a tap on it does nothing.
                    // Lifted anywhere else outside an item — close without selection.
                    if (!isDisabledItemAt(rawX, rawY)) dismiss()
                }
                hoveredIndex = -1
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelHoveredItem()
                hoveredIndex = -1
            }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────────────────

    /**
     * Returns the index of the clickable item whose cached screen bounds contain
     * ([rawX], [rawY]), or -1 if none match, bounds are not yet ready, or the hit view
     * is non-interactive (spacer or text-block row with [View.isClickable] = false).
     *
     * [windowLeft] is derived from [currentLevelWidth], so the horizontal gate correctly
     * excludes the invisible left portion of the popup window (when the current level
     * is narrower than [maxWidth]).
     */
    private fun findItemIndexAt(rawX: Float, rawY: Float): Int {
        val row = rowIndexAt(rawX, rawY)
        // Spacer, text-block and disabled item rows are non-interactive — skip them.
        return if (row >= 0 && itemViews[row].isClickable) row else -1
    }

    /** Whether ([rawX], [rawY]) lies on an item row that is disabled. */
    private fun isDisabledItemAt(rawX: Float, rawY: Float): Boolean {
        val row = rowIndexAt(rawX, rawY)
        return row >= 0 && (itemViews[row].tag as? MenuItem)?.isEnabled == false
    }

    /** The index of whichever row's cached screen bounds contain ([rawX], [rawY]), interactive or not, or -1. */
    private fun rowIndexAt(rawX: Float, rawY: Float): Int {
        if (!boundsReady) return -1
        val x = rawX.toInt()
        val y = rawY.toInt()
        if (x < windowLeft || x >= windowRight) return -1
        return itemTops.indices.firstOrNull { i -> y >= itemTops[i] && y < itemBottoms[i] } ?: -1
    }

    /**
     * Positions the ripple hotspot at the finger's screen location and marks the item pressed.
     * [rawX] - [windowLeft] converts to an x-offset within the visible panel content.
     */
    private fun pressItem(index: Int, rawX: Float, rawY: Float) {
        val view = itemViews[index]
        view.drawableHotspotChanged(rawX - windowLeft, rawY - itemTops[index])
        view.isPressed = true
    }

    /**
     * Clears the pressed state and fires the OnClickListener directly.
     * The popup's exit animation and the ripple release run concurrently.
     */
    private fun releaseItem(index: Int) {
        itemViews[index].isPressed = false
        itemViews[index].performClick()
    }

    /**
     * Clears the pressed state on [hoveredIndex] without triggering a click,
     * collapsing any in-flight ripple cleanly.
     */
    private fun cancelHoveredItem() {
        if (hoveredIndex < 0) return
        itemViews[hoveredIndex].isPressed = false
        hoveredIndex = -1
    }
}
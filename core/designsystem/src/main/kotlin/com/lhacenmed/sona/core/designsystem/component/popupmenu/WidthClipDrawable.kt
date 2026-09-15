package com.lhacenmed.sona.core.designsystem.component.popupmenu

import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.Drawable
import com.lhacenmed.sona.core.designsystem.component.popupmenu.internal.DrawableWrapperCompat

/**
 * Like [ClipDrawable], but clips in terms of pixels instead of percentage — horizontally.
 *
 * Mirrors [HeightClipDrawable] for the horizontal axis, but with the opposite anchor:
 * the RIGHT edge is fixed while the LEFT edge moves inward as [clippedWidth] shrinks,
 * matching the popup's right-anchored alignment to its trigger button.
 *
 * Used by [MenuFlipper] to animate the popup's visible width without triggering layout
 * passes — only the background's bounds (and therefore its rendering outline) change.
 * Combined with [android.view.View.clipToOutline] = true on the flipper, this clips ALL
 * child rendering to the animated clip region with no view resizing needed.
 *
 * In the background chain, [WidthClipDrawable] is the outermost wrapper so its bounds
 * form the outline consumed by [android.view.View.clipToOutline], giving correct combined
 * width + height clipping when stacked with [HeightClipDrawable] as the inner layer.
 */
internal class WidthClipDrawable(delegate: Drawable) : DrawableWrapperCompat(delegate) {

    /**
     * The width to clip the drawable to, measured from the right edge.
     * Setting this triggers [setBounds] which recalculates the left bound and propagates
     * through the delegate chain, updating the outline used by
     * [android.view.View.clipToOutline]. If null, the full drawable width is used.
     */
    var clippedWidth: Int? = null
        set(value) {
            field = value
            // Re-apply current bounds so setBounds runs the clip logic immediately.
            bounds = bounds
        }

    /**
     * Keeps the right edge fixed: left = right - clippedWidth.
     * Without this fix the drawable would anchor to the wrong (left) edge.
     */
    override fun setBounds(left: Int, top: Int, right: Int, bottom: Int) {
        val clippedLeft = clippedWidth?.let { right - it } ?: left
        super.setBounds(clippedLeft, top, right, bottom)
    }
}

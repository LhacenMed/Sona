package com.lhacenmed.sona.core.designsystem.component.popupmenu

import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.Drawable
import com.lhacenmed.sona.core.designsystem.component.popupmenu.internal.DrawableWrapperCompat

/**
 * Like [ClipDrawable], but clips in terms of pixels instead of percentage.
 *
 * Used by [MenuFlipper] to animate the popup's visible height without triggering layout
 * passes — only the background's bounds (and therefore its rendering outline) change.
 * Combined with [android.view.View.clipToOutline] = true on the flipper, this clips ALL
 * child rendering to the animated clip height with no view resizing needed.
 *
 * Ported directly from saket/cascade's HeightClipDrawable.
 */
internal class HeightClipDrawable(delegate: Drawable) : DrawableWrapperCompat(delegate) {

    /**
     * The height to clip the drawable to. Setting this triggers [setBounds] which updates
     * the delegate's bounds and the outline used by [android.view.View.clipToOutline].
     * If null, the full drawable height is used.
     */
    var clippedHeight: Int? = null
        set(value) {
            field = value
            // Re-apply current bounds so setBounds runs the clip logic immediately.
            bounds = bounds
        }

    override fun setBounds(left: Int, top: Int, right: Int, bottom: Int) {
        super.setBounds(left, top, right, clippedHeight ?: bottom)
    }
}

package com.lhacenmed.sona.core.designsystem.component.popupmenu.internal

import android.graphics.Rect
import android.graphics.drawable.Drawable

/**
 * Workaround for Android's default popup background containing internal paddings
 * that aren't reset even when a custom background is set.
 *
 * This wrapper ensures zero padding is reported for the background drawable,
 * preventing unexpected spacing around the popup content.
 */
internal class ForcePaddingsDrawable(delegate: Drawable) : DrawableWrapperCompat(delegate) {
    override fun getPadding(padding: Rect): Boolean {
        // Always report zero padding
        padding.set(0, 0, 0, 0)
        return true
    }
}
package com.lhacenmed.sona.core.designsystem.component.popupmenu.internal

import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.drawable.Drawable
import android.graphics.drawable.ScaleDrawable
import android.view.Gravity

/**
 * Wrapper for Drawable that works on API 21+.
 * Extends ScaleDrawable as a workaround since DrawableWrapper is API 23+.
 */
internal abstract class DrawableWrapperCompat(
    private val delegate: Drawable
) : ScaleDrawable(delegate, Gravity.CENTER, -1f, -1f) {

    override fun draw(canvas: Canvas) {
        delegate.draw(canvas)
    }

    override fun getOutline(outline: Outline) {
        // ScaleDrawable doesn't delegate getOutline() calls to the wrapped drawable on API 21.
        delegate.getOutline(outline)
    }
}
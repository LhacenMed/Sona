package com.lhacenmed.sona.core.designsystem.component.popupmenu

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableString
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.withSave
import androidx.core.graphics.drawable.toDrawable

/**
 * A [TextView] that renders [RichText]: plain segments use the default text color;
 * [TextSegment.Link] segments render in their own color with no underline, and show a
 * real [RippleDrawable] background clipped to a per-line rounded rectangle on touch —
 * identical to Telegram's self-destruct timer link style.
 *
 * Highlight behavior:
 *   Rest    → link text in [TextSegment.Link.color]; no background visible (null content layer).
 *   Pressed → [RippleDrawable] expands from the hotspot, clipped per visual line to a
 *             rounded rect. Wrapped links get one clipped ripple region per line.
 *   Lift    → ripple exit animation runs naturally via [Drawable.Callback] → [invalidate].
 *
 * Ripple architecture:
 *   Each [LinkSpan] owns one [RippleDrawable]:
 *     color layer  = null (no persistent background at rest)
 *     ripple color = [TextSegment.Link.color] at [RIPPLE_ALPHA]
 *     mask         = solid WHITE (defines the clip boundary; never visible itself)
 *   In [onDraw], for every visual line of a span the canvas is clipped to a rounded
 *   [Path], [RippleDrawable.setBounds] is set to that line's rect, and the drawable is
 *   drawn. The drawable is shared across lines — one expanding circle — but each line
 *   independently clips it to its own rounded rect.
 *
 * Touch architecture:
 *   [onTouchEvent] sets the ripple hotspot from the raw touch point, then drives
 *   [LinkSpan.setPressed]. On [MotionEvent.ACTION_UP], super is called first (which fires
 *   [LinkMovementMethod] → [LinkSpan.onClick]), then pressed state is cleared so the
 *   ripple exit runs while the click callback is already dispatched.
 *   [highlightColor] = TRANSPARENT kills the default blue [LinkMovementMethod] rect.
 *
 * [isClickable] is false so [PopupMenu.findItemIndexAt] skips this row during drag
 * hit-testing. [LinkMovementMethod] still intercepts touch via [TextView.onTouchEvent]
 * independently of [isClickable].
 */
internal class LinkTextView(context: Context) : AppCompatTextView(context) {

    // Reused per onDraw call — avoids per-frame allocation.
    private val bgRect   = RectF()
    private val clipPath = Path()

    // Extra space (px) added around each line's text bounds for the ripple rect.
    private val hPad = context.dp(3f)
    private val vPad = context.dp(0f)

    /** Corner radius (px) for the per-line ripple clip rect. Set by [MenuPanelBuilder]. */
    var cornerRadiusPx: Float = context.dp(4f)

    /** The [LinkSpan] currently under the finger, or null. */
    private var pressedSpan: LinkSpan? = null

    /**
     * Main-thread handler used by [rippleCallback] to schedule/cancel ripple animation
     * frames. A dedicated [Handler] is used instead of [View.postAtTime] because the view
     * method requires the view to be attached to a window; the handler is always ready.
     */
    private val handler = Handler(Looper.getMainLooper())

    /**
     * Shared [Drawable.Callback] wired to every [LinkSpan]'s [RippleDrawable].
     * Drives [invalidate] during the ripple exit animation so the drawable finishes
     * its animation naturally without any manual state polling.
     */
    private val rippleCallback = object : Drawable.Callback {
        override fun invalidateDrawable(who: Drawable) = invalidate()
        override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
            handler.postAtTime(what, who, `when`)
        }
        override fun unscheduleDrawable(who: Drawable, what: Runnable) {
            handler.removeCallbacks(what)
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────────────────

    /**
     * Builds a [SpannableString] from [rich], attaches [LinkSpan]s, and wires up
     * [LinkMovementMethod]. Call after all other style properties are set.
     */
    fun setRichText(rich: RichText) {
        val sb = StringBuilder()
        rich.segments.forEach { seg -> sb.append(seg.rawText) }
        val spannable = SpannableString(sb.toString())
        var offset = 0
        rich.segments.forEach { seg ->
            val len = seg.rawText.length
            if (seg is TextSegment.Link) {
                spannable.setSpan(
                    LinkSpan(seg.color, seg.onClick),
                    offset, offset + len,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            offset += len
        }
        // Suppress the default blue selection rectangle drawn by LinkMovementMethod.
        highlightColor = Color.TRANSPARENT
        movementMethod = LinkMovementMethod.getInstance()
        text = spannable
    }

    // ── Per-line ripple drawing ───────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        val spannable = text as? Spannable
        val lyt = layout
        if (spannable != null && lyt != null) {
            // Draw ripple backgrounds BEFORE super so text renders on top.
            // All spans are drawn — inactive ripples with state=[] are fully transparent.
            spannable.getSpans(0, spannable.length, LinkSpan::class.java).forEach { span ->
                drawSpanRipple(canvas, spannable, lyt, span)
            }
        }
        super.onDraw(canvas)
    }

    /**
     * Draws the span's [RippleDrawable] once per visual line, clipped to a rounded rect.
     *
     * For each line covered by [span]:
     *   1. Compute the line's tight rect (start/end offsets on first/last lines).
     *   2. Save canvas → clip to a rounded [Path] → set drawable bounds → draw → restore.
     *
     * The ripple circle is shared across all lines (same drawable, different bounds each
     * call), so wrapped links produce one expanding ripple visible through N clipped windows.
     * When [span]'s ripple state is empty the drawable is fully transparent — no-op draw.
     */
    private fun drawSpanRipple(
        canvas: Canvas,
        spannable: Spannable,
        lyt: android.text.Layout,
        span: LinkSpan
    ) {
        val start     = spannable.getSpanStart(span)
        val end       = spannable.getSpanEnd(span)
        val startLine = lyt.getLineForOffset(start)
        val endLine   = lyt.getLineForOffset(end)
        val pl = paddingLeft.toFloat()
        val pt = paddingTop.toFloat()

        for (line in startLine..endLine) {
            val left  = if (line == startLine) lyt.getPrimaryHorizontal(start) else lyt.getLineLeft(line)
            val right = if (line == endLine)   lyt.getPrimaryHorizontal(end)   else lyt.getLineRight(line)
            bgRect.set(
                pl + left  - hPad,
                pt + lyt.getLineTop(line)    - vPad,
                pl + right + hPad,
                pt + lyt.getLineBottom(line) + vPad
            )
            canvas.withSave {
                clipPath.reset()
                clipPath.addRoundRect(bgRect, cornerRadiusPx, cornerRadiusPx, Path.Direction.CW)
                clipPath(clipPath)
                span.ripple.setBounds(
                    bgRect.left.toInt(), bgRect.top.toInt(),
                    bgRect.right.toInt(), bgRect.bottom.toInt()
                )
                span.ripple.draw(this)
            }
        }
    }

    // ── Touch tracking ────────────────────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val hit = spanAt(x, y)
                if (hit !== pressedSpan) {
                    pressedSpan?.setPressed(false)
                    pressedSpan = hit
                    // Hotspot in text-content coordinates (inside padding).
                    hit?.setPressed(true, x - paddingLeft, y - paddingTop)
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                // Call super FIRST — LinkMovementMethod fires onClick while ripple is still visible.
                val toRelease = pressedSpan
                pressedSpan = null
                val result = super.onTouchEvent(event)
                // Clearing pressed state triggers the ripple exit animation; callback drives redraws.
                toRelease?.setPressed(false)
                return result
            }
            MotionEvent.ACTION_CANCEL -> {
                pressedSpan?.setPressed(false)
                pressedSpan = null
                invalidate()
            }
        }
        return super.onTouchEvent(event)
    }

    /**
     * Returns the [LinkSpan] whose text bounds contain ([x], [y]) in view coordinates, or null.
     * Mirrors [LinkMovementMethod]'s hit-test logic; guards against offsets outside the span.
     */
    private fun spanAt(x: Float, y: Float): LinkSpan? {
        val lyt       = layout            ?: return null
        val spannable = text as? Spannable ?: return null
        val line      = lyt.getLineForVertical((y - paddingTop).toInt())
        val offset    = lyt.getOffsetForHorizontal(line, x - paddingLeft)
        return spannable.getSpans(offset, offset, LinkSpan::class.java).firstOrNull()
    }

    // ── LinkSpan ──────────────────────────────────────────────────────────────────────────

    /**
     * [ClickableSpan] that renders link text in [color] with no underline and owns one
     * [RippleDrawable] used by [onDraw] for the per-line animated background.
     *
     * Ripple setup:
     *   - color layer = null  → no persistent background; rest state is invisible.
     *   - ripple color = [color] at [RIPPLE_ALPHA] → animated circle on press.
     *   - mask = solid WHITE  → clips the ripple to whatever bounds are set before draw().
     *
     * [callback] is [rippleCallback] so the exit animation drives [invalidate] automatically.
     */
    internal inner class LinkSpan(val color: Int, private val onClick: () -> Unit) : ClickableSpan() {

        val ripple: RippleDrawable = RippleDrawable(
            ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, RIPPLE_ALPHA)),
            null,                       // no content layer — background invisible at rest
            Color.WHITE.toDrawable()  // mask — clips ripple to line rect; never drawn directly
        ).also { it.callback = rippleCallback }

        /**
         * Sets the ripple hotspot and state.
         * [hotX]/[hotY] are in text-content coordinates (subtract padding before passing).
         * On [pressed] = false, state is cleared and the exit animation runs via [rippleCallback].
         */
        fun setPressed(pressed: Boolean, hotX: Float = 0f, hotY: Float = 0f) {
            if (pressed) {
                ripple.setHotspot(hotX, hotY)
                ripple.state = intArrayOf(android.R.attr.state_pressed, android.R.attr.state_enabled)
            } else {
                ripple.state = intArrayOf()
            }
        }

        override fun onClick(widget: View) = onClick()

        /** Renders in [color]; no underline — underlines are a legacy web convention. */
        override fun updateDrawState(ds: TextPaint) {
            ds.color = color
            ds.isUnderlineText = false
        }
    }

    private companion object {
        /** Alpha (0–255) of the ripple color — ~31% opacity, matching Material's default. */
        const val RIPPLE_ALPHA = 80
    }
}

/** Raw string content of any [TextSegment], regardless of type. */
private val TextSegment.rawText: String
    get() = when (this) {
        is TextSegment.Plain -> text
        is TextSegment.Link  -> text
    }

package com.lhacenmed.sona.core.designsystem.component.popupmenu

import android.R.attr.selectableItemBackground
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.children

/**
 * Output of [MenuPanelBuilder.createPanel]. Carries enough view references for
 * [PopupMenu] to wire up entry animations and drag hit-testing.
 *
 * @param panel      Outer [FrameLayout] passed to [MenuFlipper.show]. MATCH_PARENT wide;
 *                   contains the inner [LinearLayout] right-aligned at exactly [panelWidth]
 *                   so item content sits within the [WidthClipDrawable] clip region.
 * @param scrollView The inner ScrollView — counter-scaled during the entry animation.
 * @param itemViews  All item rows (header row excluded) — used for the cascade animation
 *                   and for drag hit-testing. Includes spacer and text-block rows; those
 *                   have [View.isClickable] = false so [PopupMenu.findItemIndexAt] skips them.
 */
internal data class PanelResult(
    val panel: View,
    val scrollView: ScrollView,
    val itemViews: List<View>
)

/**
 * Constructs all view hierarchies for a single menu level.
 *
 * Stateless — safe to call from any thread that holds [ctx]. All created views carry their
 * click listeners, so [PopupMenu] only needs to call [createPanel] and hand the result to
 * [MenuFlipper].
 */
internal class MenuPanelBuilder(
    private val ctx: Context,
    style: PopupStyle
) {

    /** The style rows are built and coloured from; replaced by [restyle]. */
    private var style: PopupStyle = style

    /**
     * Builds a full panel for [level] at exactly [panelWidth] pixels wide.
     *
     * Layout hierarchy:
     * ```
     * outerFrame (FrameLayout, MATCH_PARENT × WRAP_CONTENT)         ← PanelResult.panel
     *   container (LinearLayout, panelWidth × WRAP_CONTENT, END)
     *     [headerRow]  — only for sub-menu levels; excluded from PanelResult.itemViews
     *     scrollView                                                 ← PanelResult.scrollView
     *       itemsLayout
     *         [spacerRow]  — present when MenuItem.spacerAbove = true  ┐ PanelResult.itemViews
     *         itemRow / textRow / richTextRow × N                      ┘
     * ```
     *
     * Spacer rows and text-block rows are included in [PanelResult.itemViews] so they
     * participate in the cascade entry animation alongside regular rows. Both have
     * [View.isClickable] = false, which [PopupMenu.findItemIndexAt] uses to skip them
     * during drag hit-testing — no selection or ripple is triggered on these rows.
     *
     * The outer frame is MATCH_PARENT so [MenuFlipper.generateDefaultLayoutParams] keeps it
     * spanning the full popup window width. The inner container has [Gravity.END] so it sits
     * at the right edge of the frame at [maxWidth - panelWidth, maxWidth], exactly matching
     * [WidthClipDrawable]'s clip region. Item rows use MATCH_PARENT inside the container
     * (which has an EXACT width spec of [panelWidth]) so they fill the level width correctly
     * and the weighted title [TextView] expands as expected.
     *
     * The nav scrim is painted directly onto the panel's canvas region in
     * [MenuFlipper.drawChild] — no overlay child is embedded. [MenuFlipper.clipToOutline]
     * + the clip drawables confine the scrim rect to the current animated card bounds
     * automatically.
     *
     * The header row is intentionally excluded from [PanelResult.itemViews] so drag
     * hit-testing skips it (a drag-open gesture shouldn't trigger back-navigation).
     *
     * @param level         The menu level to render.
     * @param panelWidth    Natural width of this level's content in pixels (from [measureLevelWidth]).
     * @param onNavigateBack Called when the header row is tapped (back-navigation).
     * @param onNavigateTo   Called when a sub-menu trigger row is tapped.
     * @param onItemClick    Called when a leaf item row is tapped.
     */
    fun createPanel(
        level: MenuLevel,
        panelWidth: Int,
        onNavigateBack: () -> Unit,
        onNavigateTo: (MenuItem) -> Unit,
        onItemClick: (MenuItem) -> Unit
    ): PanelResult {
        val hasSubMenuSiblings = level.items.any { it.subItems != null }
        val hasIconSiblings = style.iconAlign && level.items.any { it.hasIcon }
        val rows = mutableListOf<View>()

        // Exact width + Gravity.END: positions container at [maxWidth-panelWidth, maxWidth]
        // inside the MATCH_PARENT outer frame, aligning with WidthClipDrawable's visible region.
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(panelWidth, WRAP_CONTENT, Gravity.END)
        }

        // Header row is only shown for sub-menu levels; null headerTitle = root level.
        if (level.headerTitle != null) {
            container.addView(createHeaderRow(level.headerTitle, onNavigateBack))
        }

        val itemsLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        }

        level.items.forEach { item ->
            // Insert a divider bar above this item when requested.
            if (item.spacerAbove) {
                val spacer = createSpacerRow()
                rows.add(spacer)
                itemsLayout.addView(spacer)
            }

            val row: View = when {
                item.richText != null -> createRichTextRow(item.richText)
                item.infoText != null -> createTextRow(item.infoText)
                else -> createItemRow(item, hasSubMenuSiblings, hasIconSiblings).also { row ->
                    row.setOnClickListener {
                        if (item.subItems != null) onNavigateTo(item) else onItemClick(item)
                    }
                }
            }
            rows.add(row)
            itemsLayout.addView(row)
        }

        val scrollView = ScrollView(ctx).apply {
            isVerticalScrollBarEnabled = false
            addView(itemsLayout)
        }
        container.addView(scrollView)

        // Outer frame: MATCH_PARENT width (overridden anyway by MenuFlipper.generateDefaultLayoutParams).
        // The slide animation translates this frame; the container tracks with it, keeping
        // panel content aligned with the clip region throughout the transition.
        val outerFrame = FrameLayout(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            addView(container)
        }

        return PanelResult(outerFrame, scrollView, rows)
    }

    /**
     * Builds a single item row.
     *
     * Row layout (horizontal LinearLayout):
     *   [leading icon]  drawn when [MenuItem.icon] is non-null; invisible same-size placeholder
     *                   when [hasIconSiblings] is true and [MenuItem.icon] is null (keeps text
     *                   aligned with icon rows — mirrors cascade's hasSubMenuSiblings logic)
     *   title TextView  weight=1, fills available horizontal space; single line with end ellipsis
     *   [trailing]      [PopupStyle.chevronForwardRes] ImageView when [MenuItem.subItems] != null;
     *                   invisible spacer View when [hasSubMenuSiblings] = true to keep
     *                   text alignment consistent with sibling chevron rows.
     *
     * When [MenuItem.isDanger] is true, icon tint, title color, and chevron tint are set to
     * [PopupStyle.dangerColor] instead of [PopupStyle.contentColor], and the row background
     * uses a semi-transparent danger-colored ripple in place of the default theme ripple.
     *
     * Click listeners are attached by [createPanel], not here.
     */
    fun createItemRow(
        item: MenuItem,
        hasSubMenuSiblings: Boolean,
        hasIconSiblings: Boolean
    ): LinearLayout {
        val itemHeightPx = ctx.dp(style.itemHeightDp).toInt()
        val hPad = ctx.dp(style.itemHorizontalPaddingDp).toInt()
        val iconSize = ctx.dp(style.iconSizeDp).toInt()
        val iconSpacing = ctx.dp(style.iconTextSpacingDp).toInt()
        val chevronSize = iconSize

        // Danger rows use dangerColor for all foreground elements.
        val fgColor = if (item.isDanger) style.dangerColor else style.contentColor

        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(hPad, 0, hPad / 2, 0)
            tag = item
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, itemHeightPx)
            background = buildRowBackground(item.isDanger)

            when {
                item.hasIcon -> addView(ImageView(ctx).apply {
                    // A drawable of the row's own, so no two rows share one drawable's tint and callback.
                    if (item.icon != null) setImageResource(item.icon)
                    else setImageDrawable(item.iconDrawable?.constantState?.newDrawable(ctx.resources)?.mutate())
                    imageTintList = ColorStateList.valueOf(fgColor)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                        marginEnd = iconSpacing
                    }
                })
                // Invisible placeholder keeps title text aligned with icon rows.
                hasIconSiblings -> addView(View(ctx).apply {
                    visibility = View.INVISIBLE
                    layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                        marginEnd = iconSpacing
                    }
                })
            }

            addView(TextView(ctx).apply {
                text = item.title
                textSize = style.textSize
                typeface = style.textTypeface
                setTextColor(fgColor)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                // weight=1 so the title fills all space between the leading icon and the
                // trailing chevron/spacer. Works correctly because the row has MATCH_PARENT
                // width inside a container with an EXACT panelWidth spec.
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                    .apply {
                        // If no trailing element exists, add margin to keep text from touching the edge.
                        if (item.subItems == null && !hasSubMenuSiblings) {
                            marginEnd = hPad / 2
                        }
                    }
            })

            when {
                item.subItems != null -> addView(ImageView(ctx).apply {
                    setImageDrawable(
                        ContextCompat.getDrawable(ctx, style.chevronForwardRes)!!.mutate().apply {
                            setTint(fgColor)
                        }
                    )
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    layoutParams = LinearLayout.LayoutParams(chevronSize, chevronSize)
                })
                hasSubMenuSiblings -> addView(View(ctx).apply {
                    visibility = View.INVISIBLE
                    layoutParams = LinearLayout.LayoutParams(chevronSize, chevronSize)
                })
            }
        }
    }

    /**
     * Builds the back-navigation header row shown at the top of each sub-menu level:
     * [PopupStyle.chevronBackRes] followed by [title]. Tapping anywhere on the row
     * calls [onNavigateBack].
     * Title is single-line with end ellipsis so it never overflows the popup width.
     */
    fun createHeaderRow(title: String, onNavigateBack: () -> Unit): LinearLayout {
        val itemHeightPx = ctx.dp(style.itemHeightDp).toInt()
        val hPad = ctx.dp(style.itemHorizontalPaddingDp).toInt()
        val iconSize = ctx.dp(style.iconSizeDp).toInt()
        val iconSpacing = ctx.dp(style.iconTextSpacingDp).toInt()

        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(hPad, 0, hPad, 0)
            tag = RowRole.Header
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, itemHeightPx)
            background = buildRowBackground(danger = false)

            addView(ImageView(ctx).apply {
                setImageDrawable(
                    ContextCompat.getDrawable(ctx, style.chevronBackRes)!!.mutate().apply {
                        setTint(style.contentColor)
                    }
                )
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                    marginEnd = iconSpacing
                }
            })

            addView(TextView(ctx).apply {
                text = "Back"
                textSize = style.textSize
                typeface = style.textTypeface
                setTextColor(style.contentColor)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            })

            setOnClickListener { onNavigateBack() }
        }
    }

    /**
     * Builds a thin divider bar whose color is [PopupStyle.backgroundColor] darkened by
     * [PopupStyle.spacerIntensity] — computed once at construction time as a solid color.
     *
     * This replaces the previous hardware-layer MULTIPLY approach. Pre-baking the color
     * eliminates the entry-animation artifact: the old approach produced a near-black flash
     * at low alpha values because the MULTIPLY xfermode interacts non-linearly with alpha,
     * unlike a plain solid color which fades in correctly under the cascade animation.
     *
     * The resulting color is mathematically equivalent to what `bg * gray / 255` (MULTIPLY)
     * would produce at full opacity, so the visual appearance is unchanged once the popup
     * has finished animating in.
     *
     * The view is non-interactive ([isClickable] = false, [isFocusable] = false), so
     * [PopupMenu.findItemIndexAt] skips it during drag hit-testing even though it lives
     * in [PanelResult.itemViews] for cascade animation purposes.
     */
    private fun createSpacerRow(): View {
        val heightPx = ctx.dp(style.spacerHeightDp).toInt().coerceAtLeast(1)
        return View(ctx).apply {
            tag = RowRole.Spacer
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, heightPx)
            setBackgroundColor(spacerColor())
            isClickable = false
            isFocusable = false
        }
    }

    /**
     * Builds a non-interactive multi-line text block for [MenuItem.infoText] items.
     *
     * [isSingleLine] is explicitly false, so '\n' characters in [text] produce real line
     * breaks — no special processing required at the call site.
     * Line count is capped by [PopupStyle.textItemMaxLines]; excess text is ellipsised.
     * Padding and text size are governed by [PopupStyle.textItemVPaddingDp],
     * [PopupStyle.textItemHPaddingDp], and [PopupStyle.textItemTextSize].
     * Width is bounded by the level's natural [panelWidth], which itself is clamped to
     * [PopupStyle.maxWidthDp] (or [MenuItem.subMenuMaxWidthDp] if set) — no separate
     * width limit is needed.
     *
     * The view is non-interactive ([isClickable] = false, [isFocusable] = false), so
     * [PopupMenu.findItemIndexAt] skips it during drag hit-testing even though it lives
     * in [PanelResult.itemViews] for cascade animation purposes.
     */
    private fun createTextRow(text: String): TextView {
        val hPad = ctx.dp(style.textItemHPaddingDp).toInt()
        val vPad = ctx.dp(style.textItemVPaddingDp).toInt()
        return TextView(ctx).apply {
            tag = RowRole.Text
            this.text = text
            textSize = style.textItemTextSize
            setTextColor(style.contentColor)
            isSingleLine = false                 // explicit: '\n' in infoText produces real line breaks
            maxLines = style.textItemMaxLines
            ellipsize = TextUtils.TruncateAt.END
            setPadding(hPad, vPad, hPad, vPad)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            isClickable = false
            isFocusable = false
        }
    }

    /**
     * Builds a [LinkTextView] for [MenuItem.richText] items — supports tappable inline links
     * with per-line rounded-rectangle press highlights (Telegram self-destruct timer style).
     *
     * Plain segments use [PopupStyle.contentColor]; each [TextSegment.Link] renders in its
     * own color and shows a semi-transparent highlight on press. The highlight corner radius
     * is driven by [PopupStyle.textLinkCornerRadiusDp].
     *
     * [isClickable] = false on the returned view so [PopupMenu.findItemIndexAt] skips it
     * during drag hit-testing — individual link spans handle their own touch events via
     * [LinkMovementMethod] independently of [isClickable].
     */
    private fun createRichTextRow(rich: RichText): LinkTextView {
        val hPad = ctx.dp(style.textItemHPaddingDp).toInt()
        val vPad = ctx.dp(style.textItemVPaddingDp).toInt()
        return LinkTextView(ctx).apply {
            tag = RowRole.Text
            textSize = style.textItemTextSize
            setTextColor(style.contentColor)
            isSingleLine = false
            maxLines = style.textItemMaxLines
            setPadding(hPad, vPad, hPad, vPad)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            isClickable = false
            isFocusable = false
            cornerRadiusPx = ctx.dp(style.textLinkCornerRadiusDp)
            setRichText(rich)
        }
    }

    /**
     * Measures the natural width required for [items] at a single menu level.
     *
     * Measures every item row (with correct sibling flags for icon/chevron spacing) and,
     * when [headerTitle] is non-null, the back-navigation header row too. Returns the
     * maximum measured width clamped to [[minPx]..[maxPx]].
     *
     * Spacer rows (MATCH_PARENT) contribute 0 width in UNSPECIFIED mode and are therefore
     * not measured — they never affect the level's natural width.
     * Text-block rows ARE measured since their content may widen the panel.
     *
     * Used by [PopupMenu] for per-level width targets during navigation transitions, and
     * composed by [measureAllWidths] to find the global maximum for the popup window width.
     *
     * @param items       Items at this level (not including any nested sub-levels).
     * @param headerTitle Back-navigation header title, or null for the root level.
     * @param minPx       Minimum width in pixels (from [PopupStyle.minWidthDp]).
     * @param maxPx       Maximum width in pixels — may be the global [PopupStyle.maxWidthDp]
     *                    or a per-level override from [MenuItem.subMenuMaxWidthDp].
     */
    fun measureLevelWidth(items: List<MenuItem>, headerTitle: String?, minPx: Int, maxPx: Int): Int {
        val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        val hasSubMenu = items.any { it.subItems != null }
        val hasIconSiblings = style.iconAlign && items.any { it.hasIcon }
        var max = minPx

        if (headerTitle != null) {
            val header = createHeaderRow(headerTitle) {}
            header.measure(unspecified, unspecified)
            max = maxOf(max, header.measuredWidth)
        }

        items.forEach { item ->
            // Spacer rows are MATCH_PARENT — measuring them in UNSPECIFIED mode gives 0.
            // Skip creation entirely; they don't affect level width.
            val row: View = when {
                item.richText != null -> createRichTextRow(item.richText)
                item.infoText != null -> createTextRow(item.infoText)
                else -> createItemRow(item, hasSubMenu, hasIconSiblings)
            }
            row.measure(unspecified, unspecified)
            max = maxOf(max, row.measuredWidth)
        }

        return max.coerceIn(minPx, maxPx)
    }

    /**
     * Recursively measures every item at every nesting level and returns the maximum
     * clamped to [[minPx]..[maxPx]] — this single value becomes the popup window [width].
     *
     * Each level's effective max width is derived by inheriting the parent's cap and then
     * applying any [MenuItem.subMenuMaxWidthDp] override declared on the item that opens
     * that level. This mirrors the runtime behaviour in [PopupMenu.navigateTo] exactly,
     * so pre-measurement and navigation always agree on each level's natural width.
     *
     * The override is clamped to [[minPx]..[maxPx]] so a sub-level can never cause the
     * popup window to exceed the global [PopupStyle.maxWidthDp].
     */
    fun measureAllWidths(items: List<MenuItem>, minPx: Int, maxPx: Int): Int {
        var max = minPx

        fun traverse(list: List<MenuItem>, headerTitle: String?, levelMaxPx: Int) {
            max = maxOf(max, measureLevelWidth(list, headerTitle, minPx, levelMaxPx))
            list.forEach { item ->
                item.subItems?.let { children ->
                    // Apply the per-item override if set, otherwise inherit the current cap.
                    val childMaxPx = item.subMenuMaxWidthDp
                        ?.let { ctx.dp(it).toInt().coerceIn(minPx, maxPx) }
                        ?: levelMaxPx
                    traverse(children, item.title, childMaxPx)
                }
            }
        }

        traverse(items, null, maxPx)
        return max.coerceIn(minPx, maxPx)
    }

    /**
     * Swaps [newStyle]'s colours onto [panels] - panels this builder made - without rebuilding them, so
     * an open popup can follow a theme change frame by frame. Only colours are re-applied: sizes were
     * fixed when the panels were measured.
     *
     * Row backgrounds are rebuilt only when their own colours changed, because replacing a background
     * cuts short a ripple that is still running on it.
     */
    fun restyle(newStyle: PopupStyle, panels: List<View>) {
        val rowBackgroundsChanged = newStyle.itemBackgroundColor != style.itemBackgroundColor ||
            newStyle.dangerColor != style.dangerColor
        style = newStyle
        panels.forEach { panel -> panel.forEachRow { row -> recolorRow(row, rowBackgroundsChanged) } }
    }

    /** Recolours [row] as the kind of row its tag says it was built as. */
    private fun recolorRow(row: View, rebuildBackground: Boolean) {
        when (val role = row.tag) {
            is MenuItem -> {
                (row as ViewGroup).tintChildren(if (role.isDanger) style.dangerColor else style.contentColor)
                if (rebuildBackground) row.background = buildRowBackground(role.isDanger)
            }
            RowRole.Header -> {
                (row as ViewGroup).tintChildren(style.contentColor)
                if (rebuildBackground) row.background = buildRowBackground(danger = false)
            }
            RowRole.Spacer -> row.setBackgroundColor(spacerColor())
            RowRole.Text -> (row as TextView).setTextColor(style.contentColor)
        }
    }

    /** [PopupStyle.backgroundColor] darkened by [PopupStyle.spacerIntensity], as one solid colour. */
    private fun spacerColor(): Int {
        val bg = style.backgroundColor
        val factor = 1f - style.spacerIntensity
        return Color.argb(
            Color.alpha(bg),
            (Color.red(bg) * factor).toInt().coerceIn(0, 255),
            (Color.green(bg) * factor).toInt().coerceIn(0, 255),
            (Color.blue(bg) * factor).toInt().coerceIn(0, 255)
        )
    }

    /**
     * Builds a row background with press feedback.
     *
     * Normal rows: [PopupStyle.itemBackgroundColor] base layer + theme [selectableItemBackground]
     * ripple on top, via [android.graphics.drawable.LayerDrawable].
     *
     * Danger rows: [RippleDrawable] with a semi-transparent tint of [PopupStyle.dangerColor]
     * (~24% alpha) as the ripple layer, [PopupStyle.itemBackgroundColor] as the content layer,
     * and a [ColorDrawable] of [Color.WHITE] as the mask.
     *
     * The mask is the critical piece: without it [RippleDrawable] is unbounded — it radiates
     * outward without clipping and is invisible when the content layer is transparent. The
     * white mask is never drawn; it only defines the rectangular clip boundary so the ripple
     * is correctly confined to the row's bounds. This is the standard Android pattern for
     * custom bounded ripples.
     */
    private fun buildRowBackground(danger: Boolean): android.graphics.drawable.Drawable {
        return if (danger) {
            val rippleColor = ColorStateList.valueOf(
                Color.argb(60, Color.red(style.dangerColor), Color.green(style.dangerColor), Color.blue(style.dangerColor))
            )
            RippleDrawable(
                rippleColor,
                style.itemBackgroundColor.toDrawable(),
                ColorDrawable(Color.WHITE) // mask: clips ripple to row bounds; never drawn
            )
        } else {
            val outValue = TypedValue()
            ctx.theme.resolveAttribute(selectableItemBackground, outValue, true)
            val ripple = ContextCompat.getDrawable(ctx, outValue.resourceId)!!.mutate()
            android.graphics.drawable.LayerDrawable(arrayOf(style.itemBackgroundColor.toDrawable(), ripple))
        }
    }
}

/** The rows that are not a [MenuItem] - which is itself the tag of an item row. */
private enum class RowRole { Header, Spacer, Text }

private val MenuItem.hasIcon: Boolean get() = icon != null || iconDrawable != null

/** Calls [action] on every row in this panel: each tagged view, found without looking inside it. */
private fun View.forEachRow(action: (View) -> Unit) {
    if (tag != null) action(this) else if (this is ViewGroup) children.forEach { it.forEachRow(action) }
}

private fun ViewGroup.tintChildren(color: Int) = children.forEach { child ->
    when (child) {
        is ImageView -> child.imageTintList = ColorStateList.valueOf(color)
        is TextView -> child.setTextColor(color)
    }
}

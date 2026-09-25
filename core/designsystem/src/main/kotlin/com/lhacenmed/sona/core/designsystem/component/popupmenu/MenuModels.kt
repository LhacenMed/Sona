package com.lhacenmed.sona.core.designsystem.component.popupmenu

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.TypedValue
//import android.view.animation.LinearInterpolator
import android.animation.TimeInterpolator
import android.view.animation.DecelerateInterpolator
import com.lhacenmed.sona.core.designsystem.R

/**
 * A single menu row. Supports four rendering modes, determined by which fields are set:
 *
 *   Regular item  — [title] non-empty, [infoText] null, [richText] null. Shows optional
 *                   icon, title, optional chevron.
 *   Sub-menu      — [subItems] non-null. Regular item with a trailing chevron that slides
 *                   into a new panel on tap.
 *   Text block    — [infoText] non-null. Renders as a non-interactive multi-line [TextView];
 *                   [title] and [icon] are ignored. Supports '\n' for manual line breaks.
 *   Rich text     — [richText] non-null. Renders as a [LinkTextView] with tappable inline
 *                   links; [infoText], [title], and [icon] are ignored.
 *                   Takes precedence over [infoText] when both are set.
 *
 * Divider:
 *   [spacerAbove] = true on any item inserts a thin divider bar immediately above that item.
 *   The divider uses [android.graphics.PorterDuff.Mode.MULTIPLY] so it darkens the card
 *   background proportionally — identical technique to the nav scrim — and requires no
 *   hard-coded colours.
 *
 * Sub-menu width override:
 *   [subMenuMaxWidthDp] caps the width of the panel that opens when this item is tapped.
 *   Null inherits the current level's cap (ultimately [PopupStyle.maxWidthDp]).
 *   Useful for making a specific sub-menu narrower than the global maximum while keeping
 *   all other levels at their default widths.
 *
 * Danger styling:
 *   [isDanger] = true tints the icon, title, and chevron to [PopupStyle.dangerColor] and
 *   replaces the default theme ripple with a semi-transparent red ripple, signalling a
 *   destructive action to the user.
 *
 * @param title            Label shown for regular and sub-menu items.
 * @param icon             Drawable resource id for the leading icon, or null for icon-less rows.
 * @param iconDrawable     Leading icon as a ready [Drawable], for icons that are not resources.
 *                         Used when [icon] is null.
 * @param subItems         Child items for a cascading sub-menu. Non-null triggers chevron + navigation.
 * @param subMenuMaxWidthDp Optional max width (dp) for the panel opened by this item.
 *                         Overrides [PopupStyle.maxWidthDp] for that one level only.
 *                         Clamped to [[PopupStyle.minWidthDp]..[PopupStyle.maxWidthDp]].
 *                         Only meaningful when [subItems] is non-null.
 * @param spacerAbove      When true, a thin divider bar is rendered immediately above this item.
 * @param infoText         When non-null, the item renders as a non-interactive plain text block.
 *                         Supports '\n' for manual line breaks. Ignored when [richText] is set.
 * @param richText         When non-null, the item renders as a [LinkTextView] with tappable
 *                         inline links. Takes precedence over [infoText].
 * @param isDanger         When true, icon, title, and ripple are rendered in [PopupStyle.dangerColor]
 *                         to signal a destructive action.
 * @param isEnabled        When false, the row is drawn faded and is inert: no ripple, no click,
 *                         and a drag released over it neither chooses it nor closes the menu.
 */
data class MenuItem(
    val title: String = "",
    val icon: Int? = null,
    val iconDrawable: Drawable? = null,
    val subItems: List<MenuItem>? = null,
    val subMenuMaxWidthDp: Float? = null,
    val spacerAbove: Boolean = false,
    val infoText: String? = null,
    val richText: RichText? = null,
    val isDanger: Boolean = false,
    val isEnabled: Boolean = true
)

/**
 * All visual appearance of the popup in one place.
 *
 * @param backgroundColor         Fill color of the popup card.
 * @param contentColor            Applied to item text, leading icons, and navigation chevrons.
 * @param itemBackgroundColor     Fill color drawn behind each item row. The theme ripple is
 *                                layered on top, so press feedback is fully preserved.
 *                                Defaults to [Color.TRANSPARENT] (inherits the card's fill).
 * @param textTypeface            Typeface of item and header titles.
 * @param cornerRadiusDp          Corner radius of the popup card (dp).
 * @param elevationDp             Elevation of the popup window, controlling shadow spread (dp).
 *                                Higher values produce a larger, softer shadow.
 * @param shadowColor             Tint applied to both the spot and ambient shadow on API 28+.
 *                                Null leaves the system default shadow color unchanged.
 *                                Use a semi-transparent dark color for a colored shadow, e.g.
 *                                [Color.argb(180, 0, 0, 0)] for a near-black shadow.
 * @param minWidthDp              Floor for auto-sizing — prevents the card becoming too narrow (dp).
 * @param maxWidthDp              Ceiling for auto-sizing — prevents the card becoming too wide (dp).
 *                                Per-level overrides via [MenuItem.subMenuMaxWidthDp] are clamped
 *                                to this value so the popup window never exceeds it.
 * @param itemHeightDp            Fixed height of each regular row (dp). Content is always
 *                                vertically centred regardless of icon or text size.
 * @param itemHorizontalPaddingDp Horizontal padding applied to both leading and trailing edges
 *                                of each row (dp).
 * @param iconTextSpacingDp       Gap between the trailing edge of the leading icon and the
 *                                leading edge of the label (dp). No effect on icon-less rows.
 * @param iconSizeDp              Width and height of the leading icon square (dp).
 * @param iconAlign               When true (default), icon-less rows in a level that has at
 *                                least one icon receive an invisible placeholder so all text
 *                                starts at the same X. Set false to let icon-less rows use the
 *                                full horizontal space.
 * @param chevronForwardRes       Drawable resource shown on sub-menu trigger rows (trailing).
 *                                Defaults to [R.drawable.ic_chevron_forward].
 * @param chevronBackRes          Drawable resource shown on the header back-navigation row (leading).
 *                                Defaults to [R.drawable.ic_arrow_backward].
 * @param spacerHeightDp          Thickness of the divider bar produced by [MenuItem.spacerAbove]
 *                                (dp). Clamped to a minimum of 1 px after density conversion.
 * @param spacerIntensity         Darkening strength of the divider, using the same
 *                                PorterDuff.MULTIPLY convention as [AnimationConfig.navOverlayAlpha]:
 *                                0 = invisible, 1 = maximum darkening. Recommended: 0.25–0.5.
 * @param textItemMaxLines        Maximum line count for [MenuItem.infoText] and [MenuItem.richText]
 *                                text-block rows.
 * @param textItemTextSize        Text size (sp) for text-block rows.
 * @param textItemHPaddingDp      Horizontal padding for text-block rows (dp).
 * @param textItemVPaddingDp      Vertical padding for text-block rows (dp).
 * @param textLinkCornerRadiusDp  Corner radius (dp) of the per-line pressed highlight rect
 *                                drawn behind [TextSegment.Link] spans in [LinkTextView].
 * @param dangerColor             Color applied to icon, title, and ripple for [MenuItem.isDanger]
 *                                rows. Defaults to a Material-style destructive red.
 */
data class PopupStyle(
    val backgroundColor: Int,
    val contentColor: Int,
    val itemBackgroundColor: Int = Color.TRANSPARENT,
    val textSize: Float = 18f,
    val textTypeface: Typeface = Typeface.DEFAULT,
    val cornerRadiusDp: Float = 14f,
    val elevationDp: Float = 3f,
    val shadowColor: Int? = null,
    val minWidthDp: Float = 112f,
    val maxWidthDp: Float = 280f,
    val itemHeightDp: Float = 46f,
    val itemHorizontalPaddingDp: Float = 20f,
    val iconTextSpacingDp: Float = 20f,
    val iconSizeDp: Float = 22f,
    val iconAlign: Boolean = true,
    val chevronForwardRes: Int = R.drawable.ic_chevron_forward,
    val chevronBackRes: Int = R.drawable.ic_arrow_backward,
    // ── Divider (spacerAbove) ──────────────────────────────────────────────────────────────
    val spacerHeightDp: Float = 8f,
    val spacerIntensity: Float = 0.25f,
    // ── Text-block (infoText / richText) ──────────────────────────────────────────────────
    val textItemMaxLines: Int = 20,
    val textItemTextSize: Float = 15f,
    val textItemHPaddingDp: Float = 10f,
    val textItemVPaddingDp: Float = 8f,
    val textLinkCornerRadiusDp: Float = 4f,
    // ── Danger (isDanger) ──────────────────────────────────────────────────────────────────
    val dangerColor: Int = Color.rgb(220, 53, 69)
)

/**
 * Controls all timing and motion values for the popup animations.
 *
 * Entry animation:
 *   - [entryExpandDuration]      : how long the container scales/fades in (ms)
 *   - [entryExpandInterpolator]  : interpolator for the container expand + alpha + counter-scale.
 *                                  Defaults to [LinearInterpolator] for a uniform expand feel.
 *                                  Swap to [DecelerateInterpolator] for a spring-like ease-out.
 *   - [cascadeStartDelayMs]      : delay before the item cascade begins — set equal to
 *                                  [entryExpandDuration] to start only after the expand finishes,
 *                                  or lower to overlap with the tail of the expand
 *   - [cascadeStaggerMs]         : delay between each consecutive item starting its animation (ms).
 *                                  Should be ~15% of [cascadeItemDurationMs] for a fluid wave effect
 *                                  where each item starts well before the previous one finishes.
 *                                  Increasing this toward [cascadeItemDurationMs] makes the cascade
 *                                  feel more sequential.
 *   - [cascadeItemDurationMs]    : how long each individual item's animation lasts (ms)
 *   - [itemSlideDistanceDp]      : vertical slide distance of each item while fading in (dp)
 *   - [itemSlideDeceleration]    : ease-out strength on each item's slide. Lower values spread the
 *                                  motion more evenly across the duration, making the overlap between
 *                                  consecutive items more perceptually visible.
 *
 * Exit animation:
 *   - [exitDuration]             : how long the container fades/slides out (ms)
 *   - [exitSlideDistanceDp]      : upward slide distance of the container while fading out (dp)
 *
 * Navigation animation (panel slide between menu levels — ported from saket/cascade):
 *   - [navigationDuration]       : how long the incoming/outgoing panel slide and the popup-window
 *                                  height animation run (ms)
 *   - [navOverlayAlpha]          : peak strength of the scrim applied to the outgoing panel during
 *                                  forward navigation, reversed on back-navigation. Uses
 *                                  [PorterDuff.Mode.MULTIPLY] so darkening is proportional to
 *                                  background luminance — the same relative effect on any theme.
 *                                  0 = no overlay, 1 = maximum darkening. Recommended range: 0.25–0.5.
 *
 * Every duration is run through [com.lhacenmed.sona.core.designsystem.effect.SonaEffects.animationDuration]:
 * while animations are disabled the menu appears, moves between levels and goes at once.
 */
data class AnimationConfig(
    val entryExpandDuration: Long = 300,
    val entryFadeDuration: Long = 200,
    val entryExpandInterpolator: TimeInterpolator = DecelerateInterpolator(1f),
    val cascadeStaggerMs: Long = 30,
    val cascadeItemDurationMs: Long = 300,
    val itemSlideDistanceDp: Float = 12f,
    val itemSlideDeceleration: Float = 1.5f,
    val exitDuration: Long = 100,
    val exitSlideDistanceDp: Float = 8f,
    val navigationDuration: Long = 350L,
    val navOverlayAlpha: Float = 0.35f
)

/**
 * A single level in the menu hierarchy tracked on the [PopupMenu] backstack.
 * The root level has a null [headerTitle]; sub-menu levels carry the parent item's title
 * so the header row can display it with a back-arrow.
 */
internal data class MenuLevel(val items: List<MenuItem>, val headerTitle: String?)

/** Converts [value] dp to pixels on this [Context]'s display. */
internal fun Context.dp(value: Float): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

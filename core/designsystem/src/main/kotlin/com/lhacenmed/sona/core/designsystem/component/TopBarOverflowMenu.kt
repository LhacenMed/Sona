package com.lhacenmed.sona.core.designsystem.component

import android.content.res.Resources
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.lhacenmed.sona.core.designsystem.component.popupmenu.MenuItem
import com.lhacenmed.sona.core.designsystem.component.popupmenu.PopupMenu
import com.lhacenmed.sona.core.designsystem.component.popupmenu.PopupStyle
import com.lhacenmed.sona.core.designsystem.theme.SonaComponentStyle
import kotlinx.coroutines.CancellationException

/** How far a press must travel from where it landed on the overflow button before it drags the menu open. */
private val DragToOpenThreshold = 10.dp

/** The weight of Material's menu label, `labelLarge`: medium. */
private val MenuLabelTypeface: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

/** Everything the overflow menu is drawn from, as of the bar's latest composition. */
internal data class OverflowMenuContent(
    val actions: List<TopBarAction>,
    val iconPainters: List<VectorPainter>,
    val style: PopupStyle,
    val density: Density,
    val layoutDirection: LayoutDirection,
)

/**
 * The top bar's overflow menu: a cascading [PopupMenu] opened from the overflow button.
 *
 * It opens the two ways a platform overflow menu does. A tap opens it to pick from; a press that drags
 * off the button opens it under the finger, and the row the finger is lifted from is the one chosen -
 * one gesture from the button to the action.
 *
 * The popup is a window of Views, which the Compose theme cannot reach. Instead, every composition of
 * the bar hands it the theme's current colours, and an open popup repaints itself with them - so it
 * moves through a theme transition on the same frames as the screen behind it.
 */
internal class TopBarOverflowMenu(content: OverflowMenuContent) {

    /** The View the popup hangs from; see [OverflowMenuAnchor]. */
    lateinit var anchor: View

    /** The overflow button's place in the window, to turn a press on it into a position on the screen. */
    lateinit var buttonCoordinates: LayoutCoordinates

    var content = content
        set(value) {
            if (value.style != field.style) openPopup?.updateColors(value.style)
            field = value
        }

    private var openPopup: PopupMenu? = null

    /** Opens the menu over the bar and returns it, or returns null if it is already open. */
    fun show(): PopupMenu? {
        if (openPopup != null) return null
        val (actions, iconPainters, style, density, layoutDirection) = content
        val iconSizePx = with(density) { style.iconSizeDp.dp.roundToPx() }
        val items = actions.mapIndexed { index, action ->
            MenuItem(
                title = action.label,
                iconDrawable = iconPainters[index].toDrawable(anchor.resources, iconSizePx, density, layoutDirection),
            )
        }
        return PopupMenu(anchor.context, items, style) { chosen ->
            actions[items.indexOfFirst { it === chosen }].onClick()
        }.also { popup ->
            popup.setOnDismissListener { openPopup = null }
            openPopup = popup
            popup.show(anchor)
        }
    }

    fun dismiss() {
        openPopup?.dismiss()
    }
}

/**
 * [actions]' overflow menu, repainted from the theme on every composition.
 *
 * The popup is Material's menu in colour: its container and content roles, read from the theme as it
 * is on this frame - mid-transition included. It is Material's menu in size too - the item height,
 * padding, icon and label of a `DropdownMenuItem` - on Sona's own corners, so it reads as one of the
 * app's components rather than something brought in.
 */
@Composable
internal fun rememberTopBarOverflowMenu(actions: List<TopBarAction>): TopBarOverflowMenu {
    val colorScheme = MaterialTheme.colorScheme
    val content = OverflowMenuContent(
        actions = actions,
        iconPainters = actions.map { rememberVectorPainter(it.icon) },
        style = PopupStyle(
            backgroundColor = colorScheme.surfaceContainer.toArgb(),
            contentColor = colorScheme.onSurface.toArgb(),
            textSize = MaterialTheme.typography.labelLarge.fontSize.value,
            textTypeface = MenuLabelTypeface,
            cornerRadiusDp = SonaComponentStyle.CornerRadius.value,
            itemHeightDp = 48f,
            itemHorizontalPaddingDp = 12f,
            iconTextSpacingDp = 12f,
            iconSizeDp = 24f,
        ),
        density = LocalDensity.current,
        layoutDirection = LocalLayoutDirection.current,
    )
    val menu = remember { TopBarOverflowMenu(content) }
    SideEffect { menu.content = content }
    return menu
}

/**
 * The View [menu] opens from. The popup lines its right edge and top up with this View's, so laid over
 * the bar's actions it opens over the overflow button, as a platform overflow menu does. It draws
 * nothing and turns down every touch.
 */
@Composable
internal fun OverflowMenuAnchor(menu: TopBarOverflowMenu, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context ->
            View(context).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                menu.anchor = this
            }
        },
        modifier = modifier,
        // A menu left open once its bar has gone would hang from nothing.
        onRelease = { menu.dismiss() },
    )
}

/**
 * Lets a press on the overflow button drag [menu] open.
 *
 * The press is watched on its way to the button, not taken from it, so a tap stays the button's own:
 * it ripples, morphs and clicks like any other. Once the finger has travelled [DragToOpenThreshold]
 * the menu opens and the press becomes the menu's - consumed from then on, which the button reads as
 * cancelled - and every move and the release go to the popup, which highlights the row under the
 * finger and picks the one it is lifted from.
 */
internal fun Modifier.dragToOpen(menu: TopBarOverflowMenu): Modifier = this
    .onPlaced { menu.buttonCoordinates = it }
    .pointerInput(menu) {
        val thresholdSquared = DragToOpenThreshold.toPx().let { it * it }
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var popup: PopupMenu? = null
            try {
                do {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes
                        .firstOrNull { it.id == down.id } ?: break
                    val dragged = (change.position - down.position).getDistanceSquared() > thresholdSquared
                    if (popup == null && change.pressed && dragged) {
                        popup = menu.show() ?: break
                    }
                    if (popup != null) {
                        change.consume()
                        val onScreen = menu.buttonCoordinates.localToScreen(change.position)
                        val action = if (change.pressed) MotionEvent.ACTION_MOVE else MotionEvent.ACTION_UP
                        popup.forwardTouchEvent(onScreen.x, onScreen.y, action)
                    }
                } while (change.pressed)
            } catch (cancellation: CancellationException) {
                // The press was taken away mid-drag: let go of the row it was holding.
                popup?.forwardTouchEvent(0f, 0f, MotionEvent.ACTION_CANCEL)
                throw cancellation
            }
        }
    }

/** This icon drawn once into a bitmap, at [sizePx] square, for the popup's Views to show. */
private fun VectorPainter.toDrawable(
    resources: Resources,
    sizePx: Int,
    density: Density,
    layoutDirection: LayoutDirection,
): Drawable {
    val bitmap = ImageBitmap(sizePx, sizePx)
    val size = Size(sizePx.toFloat(), sizePx.toFloat())
    CanvasDrawScope().draw(density, layoutDirection, Canvas(bitmap), size) {
        with(this@toDrawable) { draw(size) }
    }
    return BitmapDrawable(resources, bitmap.asAndroidBitmap())
}

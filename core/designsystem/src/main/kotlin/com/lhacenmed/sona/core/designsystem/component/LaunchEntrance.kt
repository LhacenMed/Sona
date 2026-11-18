package com.lhacenmed.sona.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.lhacenmed.sona.core.designsystem.motion.EmphasizedDecelerate
import com.lhacenmed.sona.core.designsystem.motion.FadeInMillis
import com.lhacenmed.sona.core.designsystem.motion.StaggerMillis
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** How far the mark is turned back as the launch screen leaves it, turning upright as it flies - see `ic_splash_anim`. */
private const val SplashMarkRotation = -120f

/** How long the mark takes to fly to its place. */
private const val MarkFlightMillis = 560

/** How long something arriving takes to move into its place. */
private const val ArrivalMillis = 400

/** When the screen's own content starts arriving, as the mark nears its place: the title first. */
const val LaunchContentDelayMillis = 300

/**
 * The order the screen arrives in, [StaggerMillis] apart from top to bottom: the bar's title, its actions, the shortcuts,
 * the tabs, the list's header, then the list's rows one after another - see [RowReveal]. The mini player rises last.
 */
object LaunchOrder {
    const val TITLE = LaunchContentDelayMillis
    const val ACTIONS = TITLE + StaggerMillis
    const val SHORTCUTS = ACTIONS + StaggerMillis
    const val TABS = SHORTCUTS + StaggerMillis
    const val TOOLS = TABS + StaggerMillis
    const val FIRST_ROW = TOOLS + StaggerMillis
    const val MINI_PLAYER = 520

    /** How many of a list's rows arrive one after another; the rest come with the last of them. */
    internal const val STAGGERED_ROWS = 8

    /** When the list's row at [index] arrives, from the moment its list's rows start arriving. */
    internal fun rowDelay(index: Int): Int = index.coerceIn(0, STAGGERED_ROWS - 1) * StaggerMillis
}

/** How far the content under the bar rises into place, and the mini player. */
val LaunchRise = 12.dp
val LaunchMiniPlayerRise = 24.dp

/** How far the bar's title slides in from its start. */
val LaunchTitleShift = 8.dp

/** How long what the mark was taken from takes to turn to the app's colours - done before the title arrives. */
private const val RecolorMillis = 250

/** The whole entrance, every arrival ended. */
private const val EntranceMillis = 900

/** How long [LaunchEntrance.awaitSettled] waits at most: the launch screen's own animation, then the whole entrance. */
private const val MaxSettleWaitMillis = 3_000L

/** The mark's size as it starts in the middle of the window, where there was no launch screen to take it from. */
private val CentredMarkSize = 112.dp

/**
 * The app coming to life: Sona's mark flies from where it was - the launch screen's icon, or the setup's last shape - to its
 * place on the screen opening - the library's bar, or the setup's first shape - and the screen arrives around it, top to
 * bottom.
 *
 * Every part is placed in time against one clock this object holds, not against the composition drawing it, so nothing
 * on screen can restart or drop it: content recomposing, a player showing up, the window laid out again all leave it
 * where it was, and a composition made again picks it up where it stood.
 *
 * It starts in two steps, so the mark never moves where it cannot be seen: [holdAt] draws the launch screen again
 * beneath it - its background, and the mark, still, exactly where its icon is - and [fly] starts it once the launch screen
 * is gone, its first frame the very place the launch screen left it. Taking the launch screen away changes no pixel; then
 * its background and the mark's colours turn to the app's together, over [RecolorMillis]. Only the first of each counts,
 * so a late one can never move a mark already flying. The mark is composed from the first frame, unseen until held, so
 * nothing is built as it starts to move. Touches reach the screen from the first frame.
 */
@Stable
class LaunchEntrance private constructor(private val markRotation: Float) {
    private val clock = Animatable(0f)

    /** The mark as it is held before it flies - null until [holdAt]. */
    internal var held by mutableStateOf<HeldMark?>(null)
        private set

    /** Completed once the held mark is drawn - see [awaitHeldOnScreen]. */
    private val heldDrawn = CompletableDeferred<Unit>()

    /** Where the mark lands, and in what colours - reported by [launchEntranceMark]. */
    internal var markTarget by mutableStateOf<MarkTarget?>(null)

    internal val isMarkFlying: Boolean get() = clock.value < MarkFlightMillis

    /** Whether what the mark lands on draws the flight itself, the mark not drawn - see [launchEntranceFlight]. */
    internal var isFlightDrawnByTarget by mutableStateOf(false)

    /** Whether the mark has been let go to fly - see [fly]. */
    private var isFlying by mutableStateOf(false)

    /** Whether the mark has been placed - see [holdAt]. */
    val isHeld: Boolean get() = held != null

    /**
     * Places the mark, still, at [bounds] in the window, its cookie and note [cookieColor] and [noteColor], over
     * [backdropColor] across the window - what it is taken from, as that shows it; null for the app's own. See [fly].
     */
    fun holdAt(bounds: Rect, cookieColor: Color?, noteColor: Color?, backdropColor: Color? = null) {
        if (isHeld) return
        held = HeldMark(boundsIn = { bounds }, cookieColor = cookieColor, noteColor = noteColor, backdropColor = backdropColor)
    }

    /** Returns once the held mark has been drawn - the frame it is in on its way to the screen. */
    suspend fun awaitHeldOnScreen() {
        heldDrawn.await()
    }

    internal fun onHeldDrawn() {
        heldDrawn.complete(Unit)
    }

    /** Lets the held mark fly, from its next frame. */
    fun fly() {
        isFlying = true
    }

    /** Starts the entrance at once with the mark in the middle of the window, where nothing on screen had it before. */
    fun flyFromCentre() {
        if (!isHeld) {
            val centred: Density.(IntSize) -> Rect = { window ->
                val side = CentredMarkSize.toPx()
                Rect(Offset((window.width - side) / 2f, (window.height - side) / 2f), Size(side, side))
            }
            held = HeldMark(boundsIn = centred, cookieColor = null, noteColor = null, backdropColor = null)
        }
        fly()
    }

    internal suspend fun run() {
        snapshotFlow { isFlying && isHeld }.first { it }
        // Picks up where it stood, so an entrance whose composition is made again carries on rather than restarting.
        clock.animateTo(EntranceMillis.toFloat(), tween((EntranceMillis - clock.value).toInt(), easing = LinearEasing))
    }

    /** How far a part starting at [delayMillis] and lasting [durationMillis] has come, from 0 to 1, eased by [easing]. */
    internal fun progress(delayMillis: Int, durationMillis: Int, easing: Easing): Float =
        easing.transform(((clock.value - delayMillis) / durationMillis).coerceIn(0f, 1f))

    /** How far the mark has flown to its place, from 0 to 1, eased - read as it is drawn. */
    fun flown(): Float = progress(0, MarkFlightMillis, EmphasizedDecelerate)

    /** How far the mark is turned [flown] of the way - from as it was held to upright. */
    fun markRotation(flown: Float): Float = lerp(markRotation, 0f, flown)

    /** How far what the mark was taken from has turned to the app's colours - its backdrop and its own, together. */
    fun recolored(): Float = progress(0, RecolorMillis, FastOutSlowInEasing)

    /** Where the mark is held, in a window [window] across - null until it is. See [holdAt]. */
    fun heldBounds(density: Density, window: IntSize): Rect? = held?.let { with(density) { it.boundsIn(this, window) } }

    /** The colours the mark is held in - null for the mark's own, or until it is held. */
    val heldCookieColor: Color? get() = held?.cookieColor
    val heldNoteColor: Color? get() = held?.noteColor

    /** What the mark was held over, across the window - null for the app's own, or until it is held. */
    val heldBackdropColor: Color? get() = held?.backdropColor

    /**
     * Returns once every part of the entrance is in place - for work that would take its frames, held until the screen has
     * arrived. At most [MaxSettleWaitMillis] - an entrance that never starts holds nothing up for good.
     */
    suspend fun awaitSettled() {
        withTimeoutOrNull(MaxSettleWaitMillis) { snapshotFlow { clock.value >= EntranceMillis }.first { it } }
    }

    /** Returns once the mark has landed - at once where it already has. */
    suspend fun awaitMarkLanded() {
        snapshotFlow { isMarkFlying }.first { !it }
    }

    /** Returns once the entrance has come to its lists' rows - at once where it already has. */
    internal suspend fun awaitRowsTurn() {
        snapshotFlow { clock.value >= LaunchOrder.FIRST_ROW }.first { it }
    }

    companion object {
        /** From Android's launch screen, which hands the mark over turned back - see `ic_splash_anim`. See [holdAt] and [fly]. */
        fun fromSplash(): LaunchEntrance = LaunchEntrance(SplashMarkRotation)

        /**
         * From the setup's last shape, upright, once it is the mark - made as the setup starts to end, so the screen it
         * opens on is built beneath it meanwhile, and held and let fly from where the shape is once it is: see [holdAt].
         */
        fun fromSetup(): LaunchEntrance = LaunchEntrance(0f)
    }
}

/** The mark as [LaunchEntrance.holdAt] places it: where, from the window's size, and in the colours of what it is taken from. */
@Immutable
internal class HeldMark(
    val boundsIn: Density.(IntSize) -> Rect,
    val cookieColor: Color?,
    val noteColor: Color?,
    val backdropColor: Color?,
)

/** Where the mark lands - [bounds] in the window - and the colours it lands in, the mark's own while null. */
@Immutable
internal class MarkTarget(val bounds: Rect, val cookieColor: Color?, val noteColor: Color?)

/** The entrance under way - or null where the app opened without one: a warm start, a notification tapped. */
val LocalLaunchEntrance = compositionLocalOf<LaunchEntrance?> { null }

/** Runs [entrance] over [content], drawing the mark in flight above it. Null shows [content] as it is. */
@Composable
fun LaunchEntranceHost(entrance: LaunchEntrance?, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLaunchEntrance provides entrance) {
        Box(modifier = Modifier.fillMaxSize()) {
            content()
            if (entrance != null) FlyingMark(entrance)
        }
    }
}

@Composable
private fun FlyingMark(entrance: LaunchEntrance) {
    // Keyed on the entrance alone: nothing the content does reaches it.
    LaunchedEffect(entrance) { entrance.run() }
    val isFlying by remember(entrance) { derivedStateOf { entrance.isMarkFlying } }
    if (!isFlying) return

    val density = LocalDensity.current
    val restingCookie = MaterialTheme.colorScheme.primaryContainer
    val restingNote = MaterialTheme.colorScheme.onPrimaryContainer
    // The window's size as composition starts, so a mark held already - the setup's - is drawn on the very first frame.
    val window = LocalWindowInfo.current.containerSize
    val held = entrance.held
    // Composed, unseen, before it is held, so the frame it appears on builds nothing.
    val start = remember(window, held) { held?.takeIf { window != IntSize.Zero }?.let { with(density) { it.boundsIn(this, window) } } }
    val backdrop = held?.backdropColor.takeIf { start != null }
    Box(
        modifier = Modifier
            .fillMaxSize()
            // What the mark was taken from, behind it, turning to the app beneath as the mark turns to its colours.
            .drawBehind {
                // Drawn by what the mark lands on where that draws the flight - beneath itself, not over it.
                if (backdrop != null && !entrance.isFlightDrawnByTarget) drawRect(backdrop, alpha = 1f - entrance.recolored())
                if (start != null) entrance.onHeldDrawn()
            },
    ) {
        // What it lands on draws the flight itself.
        if (entrance.isFlightDrawnByTarget) return@Box
        val flown = { entrance.progress(0, MarkFlightMillis, EmphasizedDecelerate) }
        SonaMark(
            modifier = Modifier
                .size(with(density) { start?.width?.toDp() ?: 1.dp })
                .graphicsLayer {
                    if (start == null) {
                        alpha = 0f
                        return@graphicsLayer
                    }
                    val target = entrance.markTarget
                    val to = target?.bounds ?: start
                    val center = lerp(start.center, to.center, flown())
                    val scale = lerp(1f, to.width / start.width, flown())
                    translationX = center.x - start.width / 2f
                    translationY = center.y - start.height / 2f
                    scaleX = scale
                    scaleY = scale
                    // With nowhere in the bar to land - a screen opened over the library - the mark fades where it is.
                    if (target == null) alpha = 1f - flown()
                },
            cookieRotation = { entrance.markRotation(flown()) },
            // From what it was taken from to what it lands on - one turn, with the backdrop's.
            cookieColor = {
                val landing = entrance.markTarget?.cookieColor ?: restingCookie
                lerp(held?.cookieColor ?: landing, landing, entrance.recolored())
            },
            noteColor = {
                val landing = entrance.markTarget?.noteColor ?: restingNote
                lerp(held?.noteColor ?: landing, landing, entrance.recolored())
            },
        )
    }
}

/** How long a list's rows take to arrive, the last of them in place. */
private const val RowRevealMillis = (LaunchOrder.STAGGERED_ROWS - 1) * StaggerMillis + ArrivalMillis

/**
 * A list's rows arriving one after another, top to bottom, as they first show: with the launch - in their place in its
 * order, after the bar, the shortcuts and the tabs - or, where they arrive after it, from the moment they do: the
 * placeholders of a library still loading, and the rows that take their place. One clock for the whole list, held here
 * rather than by any row, so a row scrolled out and back, or composed again, is never revealed twice.
 */
@Stable
class RowReveal internal constructor() {
    private val clock = Animatable(0f)

    internal suspend fun run(entrance: LaunchEntrance?) {
        entrance?.awaitRowsTurn()
        clock.animateTo(RowRevealMillis.toFloat(), tween((RowRevealMillis - clock.value).toInt(), easing = LinearEasing))
    }

    internal fun progress(delayMillis: Int, durationMillis: Int, easing: Easing): Float =
        easing.transform(((clock.value - delayMillis) / durationMillis).coerceIn(0f, 1f))
}

/** The rows reveal of the list being drawn - see [rowReveal] - or null where its rows show as they are. */
val LocalRowReveal = compositionLocalOf<RowReveal?> { null }

/**
 * A new [RowReveal] each time [key] changes - a list's placeholders, then its rows - running with the launch under way, if
 * any. Null where nothing is to be revealed: [reveals] false.
 */
@Composable
fun rowReveal(key: Any, reveals: Boolean): RowReveal? {
    if (!reveals) return null
    val entrance = LocalLaunchEntrance.current
    val reveal = remember(key) { RowReveal() }
    LaunchedEffect(reveal) { reveal.run(entrance) }
    return reveal
}

/** The row at [index] of a list arriving with its [LocalRowReveal]: fading in and rising into place. Nothing without one. */
@Composable
fun Modifier.revealedRow(index: Int): Modifier {
    val reveal = LocalRowReveal.current ?: return this
    val delay = LaunchOrder.rowDelay(index)
    return graphicsLayer {
        alpha = reveal.progress(delay, FadeInMillis, LinearOutSlowInEasing)
        translationY = (1f - reveal.progress(delay, ArrivalMillis, EmphasizedDecelerate)) * LaunchRise.toPx()
    }
}

/**
 * Where the mark lands, drawn there in [cookieColor] and [noteColor] - the mark's own while null: hidden while the mark flies
 * to it, so the two are never seen at once. Its bounds as drawn, transformations before it included.
 */
@Composable
fun Modifier.launchEntranceMark(cookieColor: Color? = null, noteColor: Color? = null): Modifier {
    val entrance = LocalLaunchEntrance.current ?: return this
    return onGloballyPositioned { entrance.markTarget = MarkTarget(it.boundsInRoot(), cookieColor, noteColor) }
        .graphicsLayer { alpha = if (entrance.isMarkFlying) 0f else 1f }
}

/**
 * Where the mark lands, drawing its flight itself - from where it is held, in [LaunchEntrance.heldBounds], turned as
 * [LaunchEntrance.markRotation] and in [LaunchEntrance.heldCookieColor], to its own place, turn, colours and shape, as far
 * as [LaunchEntrance.flown] - so it can become something else on the way. The mark itself is then not drawn, nor what it
 * was held over: that is for the screen it lands on to draw beneath it, [LaunchEntrance.heldBackdropColor] turning to its
 * own as [LaunchEntrance.recolored] says.
 */
@Composable
fun Modifier.launchEntranceFlight(): Modifier {
    val entrance = LocalLaunchEntrance.current ?: return this
    SideEffect { entrance.isFlightDrawnByTarget = true }
    return this
}

/**
 * Content arriving with the launch: fading in from [delayMillis], rising [rise] and sliding [fromStart] - from the start
 * side - into place as it does. Hidden until the entrance begins; nothing while no entrance is under way.
 */
@Composable
fun Modifier.launchEntrance(delayMillis: Int, rise: Dp = 0.dp, fromStart: Dp = 0.dp): Modifier {
    val entrance = LocalLaunchEntrance.current ?: return this
    val startSign = if (LocalLayoutDirection.current == LayoutDirection.Rtl) 1f else -1f
    return graphicsLayer {
        alpha = entrance.progress(delayMillis, FadeInMillis, LinearOutSlowInEasing)
        val away = 1f - entrance.progress(delayMillis, ArrivalMillis, EmphasizedDecelerate)
        translationY = away * rise.toPx()
        translationX = away * fromStart.toPx() * startSign
    }
}

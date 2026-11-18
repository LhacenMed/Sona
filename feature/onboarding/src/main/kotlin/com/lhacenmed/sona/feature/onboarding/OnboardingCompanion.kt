package com.lhacenmed.sona.feature.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.datastore.OnboardingStep
import com.lhacenmed.sona.core.designsystem.R as DesignR
import com.lhacenmed.sona.core.designsystem.component.LocalLaunchEntrance
import com.lhacenmed.sona.core.designsystem.component.SonaMark
import com.lhacenmed.sona.core.designsystem.component.launchEntranceFlight
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import com.lhacenmed.sona.core.designsystem.motion.EmphasizedDecelerate
import kotlin.math.roundToInt

/** The companion's own size - drawn at this, and scaled to where it stands. */
internal val CompanionSize = 168.dp

/** How long the companion takes to turn into the next step's shape, and to move between its places. */
internal const val CompanionMorphMillis = 560

/** How far the companion turns with each step, like a dial moving forward. */
private const val DegreesPerStep = 50f

/** How long a turn to the next step takes, past it a little and back, as a dial settles. */
private const val TurnMillis = 720
private val TurnEasing = CubicBezierEasing(0.34f, 1.3f, 0.64f, 1f)

/** How a step's icon comes and goes: fading quickly, growing from half its size past whole and back. */
private const val IconFadeMillis = 160
private const val IconScaleMillis = 420
private val IconScaleEasing = CubicBezierEasing(0.34f, 1.5f, 0.64f, 1f)
private const val HiddenIconScale = 0.5f
private val IconSize = 64.dp

/** How long the companion's colours take to change. */
private const val ColorMillis = 300

/** One turn of the companion's slow spin, while it is the hero of a screen. */
private const val IdleTurnMillis = 40_000

/** Each step's icon, inside its shape - none on the first, whose shape is Sona's mark and carries the mark's own note. */
private val OnboardingStep.icon: ImageVector?
    get() = when (this) {
        OnboardingStep.WELCOME -> null
        OnboardingStep.ACCESS -> SonaIcons.LockOpen
        OnboardingStep.RESTORE -> SonaIcons.Restore
        OnboardingStep.FOLDERS -> SonaIcons.FolderOpen
        OnboardingStep.LIBRARY -> SonaIcons.LibraryMusic
        OnboardingStep.DOWNLOADS -> SonaIcons.Download
        OnboardingStep.LOOK -> SonaIcons.Palette
        OnboardingStep.UPDATES -> SonaIcons.SystemUpdate
        OnboardingStep.DONE -> SonaIcons.Check
    }

/** Whether the step is a screen of its own - the first and the last - with the companion as its hero, large and centred. */
internal val OnboardingStep.isHero: Boolean get() = this == OnboardingStep.WELCOME || this == OnboardingStep.DONE

/** Whether the step shows Sona's mark as it is - its cookie, its note, its colours - the one the whole app shows. */
private val OnboardingStep.isMark: Boolean get() = this == OnboardingStep.WELCOME

/**
 * The shape that accompanies the whole setup: each step morphs it into a shape of its own and turns it a little further, like
 * a dial; going back turns it back. On the first step it is Sona's mark exactly - the launch screen's, drawn from the same
 * path, in the same colours.
 *
 * It is where the launch screen's mark lands, and it draws that flight itself - see [launchEntranceFlight]: from the mark as
 * the launch screen leaves it, turned back and in its colours, to this step's own shape, turn and colours, morphing on the
 * way - so it arrives as what it is, whichever step the setup opens on, with nothing changing as it lands. Where it stands
 * on the way is its caller's, as is where it stands at all.
 *
 * As the setup ends - [isLeaving] - it turns back into the mark, whole - upright, in its colours, with its note - for the
 * library's entrance to fly it to the bar as it is. Drawn at [CompanionSize], and scaled to its place.
 */
@Composable
internal fun OnboardingCompanion(step: OnboardingStep, isLeaving: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val entrance = LocalLaunchEntrance.current
    val markPath = stringResource(DesignR.string.sona_mark_cookie_path)
    val mark = remember(markPath) { markShapeOf(markPath) }

    // Whether the mark's flight here is over - at once where the setup opened without one.
    val hasLanded by produceState(initialValue = entrance == null, entrance) {
        entrance?.awaitMarkLanded()
        value = true
    }

    // Null while leaving: the mark.
    val shownStep = step.takeUnless { isLeaving }
    val isMarkColored = shownStep == null || shownStep.isMark
    val fill by animateColorAsState(
        targetValue = if (!isMarkColored && step.isHero) colors.primary else colors.primaryContainer,
        animationSpec = tween(ColorMillis),
        label = "companionFill",
    )
    val iconColor by animateColorAsState(
        targetValue = if (!isMarkColored && step.isHero) colors.onPrimary else colors.onPrimaryContainer,
        animationSpec = tween(ColorMillis),
        label = "companionIcon",
    )

    // The shape it is turning from and to - from wherever it stood, should the last turn be cut short.
    val target = shapeOf(shownStep, mark)
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    val morph = remember { Animatable(1f) }
    LaunchedEffect(target) {
        if (target === to) return@LaunchedEffect
        from = morphed(from, to, morph.value)
        to = target
        morph.snapTo(0f)
        morph.animateTo(1f, tween(CompanionMorphMillis, easing = EmphasizedDecelerate))
    }

    val turnTarget = if (shownStep == null) 0f else step.ordinal * DegreesPerStep
    val turn = remember { Animatable(turnTarget) }
    LaunchedEffect(turnTarget) { turn.animateTo(turnTarget, tween(TurnMillis, easing = TurnEasing)) }

    // The hero's slow spin, once landed; stopped, it settles upright, so the mark it becomes is upright too.
    val spin = remember { Animatable(0f) }
    val spins = step.isHero && !isLeaving && hasLanded
    LaunchedEffect(spins) {
        if (spins) {
            while (true) spin.animateTo(spin.value + 360f, tween(IdleTurnMillis, easing = LinearEasing))
        } else {
            spin.animateTo((spin.value / 360f).roundToInt() * 360f, tween(CompanionMorphMillis, easing = EmphasizedDecelerate))
        }
    }

    // How far the launch screen's mark has flown here - 1 once landed, or where there was none.
    val flown = { if (entrance == null || hasLanded) 1f else entrance.flown() }

    Box(modifier = modifier.size(CompanionSize).launchEntranceFlight(), contentAlignment = Alignment.Center) {
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val path = Path()
                    onDrawBehind {
                        val flight = flown()
                        // From the mark as the launch screen left it - turned back, its shape, its colour - to the step's own.
                        val rotation = (entrance?.markRotation(flight) ?: 0f) + (turn.value + spin.value) * flight
                        val toward = morph.value
                        path.setToShape(size.minDimension) { i ->
                            val stepRadius = from.radii[i] + (to.radii[i] - from.radii[i]) * toward
                            mark.radii[i] + (stepRadius - mark.radii[i]) * flight
                        }
                        val heldFill = entrance?.heldCookieColor?.takeIf { flight < 1f }
                        rotate(rotation) { drawPath(path, if (heldFill == null) fill else lerp(heldFill, fill, entrance.recolored())) }
                    }
                },
        )
        CompanionIcons(
            step = shownStep,
            noteColor = {
                val held = entrance?.heldNoteColor?.takeIf { flown() < 1f }
                if (held == null) iconColor else lerp(held, iconColor, entrance.recolored())
            },
            iconColor = iconColor,
            flown = flown,
        )
    }
}

/**
 * Every step's icon laid over one another, the [step]'s shown - so the one leaving and the one arriving are both drawn as
 * they pass - and the mark's own note, where the mark draws it, on its step. While the launch screen's mark is still flying
 * here - [flown] short of 1 - its note shows whatever the step, leaving as the step's icon arrives.
 */
@Composable
private fun CompanionIcons(step: OnboardingStep?, noteColor: () -> Color, iconColor: Color, flown: () -> Float) {
    // The mark's own note - on its step, and as the setup ends, the companion whole again as Sona's mark.
    val noteShown by animateFloatAsState(if (step == null || step.isMark) 1f else 0f, tween(IconFadeMillis), label = "companionNote")
    SonaMark(
        modifier = Modifier.fillMaxSize(),
        cookieColor = { Color.Transparent },
        noteColor = noteColor,
        noteAlpha = { noteShown + (1f - noteShown) * (1f - flown()) },
    )
    OnboardingStep.entries.forEach { each ->
        val icon = each.icon ?: return@forEach
        val isShown = each == step
        val alpha by animateFloatAsState(if (isShown) 1f else 0f, tween(IconFadeMillis), label = "companionIconAlpha")
        val scale by animateFloatAsState(
            targetValue = if (isShown) 1f else HiddenIconScale,
            animationSpec = tween(IconScaleMillis, easing = IconScaleEasing),
            label = "companionIconScale",
        )
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier
                .size(IconSize)
                .graphicsLayer {
                    this.alpha = alpha * flown()
                    scaleX = scale
                    scaleY = scale
                },
        )
    }
}

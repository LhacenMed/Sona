@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.lhacenmed.sona.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.State
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import com.lhacenmed.sona.core.designsystem.motion.FadeInMillis
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.semantics.Role
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.datastore.OnboardingProgress
import com.lhacenmed.sona.core.datastore.OnboardingStep
import com.lhacenmed.sona.core.designsystem.component.LaunchOrder
import com.lhacenmed.sona.core.designsystem.component.LaunchRise
import com.lhacenmed.sona.core.designsystem.component.LocalBottomContentPadding
import com.lhacenmed.sona.core.designsystem.component.LocalLaunchEntrance
import com.lhacenmed.sona.core.designsystem.component.SonaIconButton
import com.lhacenmed.sona.core.designsystem.component.launchEntrance
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import com.lhacenmed.sona.core.designsystem.motion.EmphasizedDecelerate
import com.lhacenmed.sona.core.designsystem.motion.FadeOutMillis
import com.lhacenmed.sona.core.designsystem.theme.buttonPressShapes
import com.lhacenmed.sona.feature.backup.work.BackupFailure
import kotlinx.coroutines.delay

/** How long the last step takes to settle - its shape turned and morphed, its words arrived - before the library is built. */
private const val EndSettleMillis = 900L

/** How long leaving waits at most for the companion to come to rest as the mark - for a frame clock that never runs. */
private const val MaxBecomeMarkMillis = 2_000L

/** The bar at the top: back, the progress, skip. */
private val BarHeight = 64.dp

/** The companion's size beside a step's content, at its top start. */
private val CompanionSmallSize = 72.dp

/** The gap between the bar and the companion, and the companion and a step's content. */
private val CompanionGap = 8.dp
private val BodyGap = 24.dp

/** A step's sides, and the space the actions take at its foot. */
internal val ScreenPadding = 24.dp
private val ButtonHeight = 56.dp

/** How far below the status bar the hero stands - higher on a short screen, where the text and buttons need the room. */
private val HeroTop = 152.dp
private val CompactHeroTop = 96.dp
private val CompactHeight = 720.dp

/** A progress segment's line, and while it is pressed. */
private val SegmentHeight = 4.dp
private val PressedSegmentHeight = 8.dp

/** How much wider the current step's segment of the progress is than the others. */
private const val CurrentSegmentWeight = 2.4f

/**
 * The first-launch setup: a few short steps, each saved the moment it changes, with a shape accompanying the whole way - see
 * [OnboardingCompanion]. Once its end is in sight - its last step on screen - [onEndInSight] is told, for the library to be
 * built beneath it while there is time; once it ends and its shape is Sona's mark again, [onFinished] is told where it is and
 * in what colours, for the library's entrance to fly it on.
 */
@Composable
fun OnboardingScreen(onEndInSight: () -> Unit, onFinished: (markBounds: Rect, cookieColor: Color, noteColor: Color) -> Unit) {
    val viewModel: OnboardingViewModel = hiltViewModel()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val step = progress.step
    var isLeaving by rememberSaveable { mutableStateOf(false) }
    var isMark by remember { mutableStateOf(false) }
    // The colours the companion ends in: Sona's mark's own.
    val cookieColor = MaterialTheme.colorScheme.primaryContainer
    val noteColor = MaterialTheme.colorScheme.onPrimaryContainer
    val restore by viewModel.restore.collectAsStateWithLifecycle()
    val entrance = LocalLaunchEntrance.current
    val surface = MaterialTheme.colorScheme.surface

    BackHandler(enabled = !isLeaving && step != OnboardingStep.WELCOME) { viewModel.back() }
    // Once the last step has settled - its own motion over - so building the library takes none of its frames.
    LaunchedEffect(step == OnboardingStep.DONE) {
        if (step != OnboardingStep.DONE) return@LaunchedEffect
        delay(EndSettleMillis)
        onEndInSight()
    }

    // Everything but the companion leaves as the setup ends, the shape staying behind to become the mark.
    val contentAlpha by animateFloatAsState(if (isLeaving) 0f else 1f, tween(240), label = "onboardingContent")

    // A surface, so everything on it reads in its colours; and no player under it, so its lists end where they end.
    Surface(color = MaterialTheme.colorScheme.surface) {
        CompositionLocalProvider(LocalBottomContentPadding provides 0.dp) {
            // What the launch screen's mark was held over, turning to the setup's own as it flies - beneath the shape.
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize().drawBehind {
                    val backdrop = entrance?.heldBackdropColor ?: return@drawBehind
                    drawRect(lerp(backdrop, surface, entrance.recolored()))
                },
            ) {
                val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                val navigationBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                val heroTop = statusBar + if (maxHeight < CompactHeight) CompactHeroTop else HeroTop
                val companionTop = statusBar + BarHeight + CompanionGap
                val bodyTop = companionTop + CompanionSmallSize + BodyGap
                val actionsHeight = ButtonHeight + ScreenPadding * 2 + navigationBar +
                    if (step == OnboardingStep.WELCOME) ButtonHeight else 0.dp

                val heroFraction by animateFloatAsState(
                    targetValue = if (step.isHero || isLeaving) 1f else 0f,
                    animationSpec = tween(CompanionMorphMillis, easing = EmphasizedDecelerate),
                    label = "companionPlace",
                )
                val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
                val width = maxWidth
                val window = LocalWindowInfo.current.containerSize
                val density = LocalDensity.current

                // Handed over once the companion is the mark at rest - told by it, not guessed from the clock - and from
                // where it then is for certain: the hero's place, whole.
                LaunchedEffect(isLeaving) {
                    if (!isLeaving) return@LaunchedEffect
                    onEndInSight()
                    viewModel.finish()
                    withTimeoutOrNull(MaxBecomeMarkMillis) { snapshotFlow { isMark }.first { it } }
                    val markBounds = with(density) {
                        val side = CompanionSize.toPx()
                        Rect(Offset((width.toPx() - side) / 2f, heroTop.toPx()), Size(side, side))
                    }
                    onFinished(markBounds, cookieColor, noteColor)
                }

                OnboardingCompanion(
                    step = step,
                    isLeaving = isLeaving,
                    onBecameMark = { isMark = true },
                    modifier = Modifier
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0f, 0f)
                            val smallX = if (isRtl) width - ScreenPadding - CompanionSmallSize else ScreenPadding
                            val heroX = (width - CompanionSize) / 2
                            val x = lerp(smallX.toPx(), heroX.toPx(), heroFraction)
                            val y = lerp(companionTop.toPx(), heroTop.toPx(), heroFraction)
                            val scale = lerp(CompanionSmallSize / CompanionSize, 1f, heroFraction)
                            // On its way from where the launch screen left the mark - unseen until it is held there.
                            val flown = entrance?.flown() ?: 1f
                            val from = if (flown < 1f) entrance?.heldBounds(this, window) else null
                            alpha = if (flown < 1f && from == null) 0f else 1f
                            translationX = if (from == null) x else lerp(from.left, x, flown)
                            translationY = if (from == null) y else lerp(from.top, y, flown)
                            val flightScale = if (from == null) scale else lerp(from.width / CompanionSize.toPx(), scale, flown)
                            scaleX = flightScale
                            scaleY = flightScale
                        },
                )

                Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = contentAlpha }) {
                    OnboardingBar(
                        progress = progress,
                        onBack = { viewModel.back() },
                        onSkip = viewModel::skip,
                        onStep = viewModel::goTo,
                        isHeld = step == OnboardingStep.RESTORE && restore.holdsStep,
                        modifier = Modifier.padding(top = statusBar).launchEntrance(LaunchOrder.TITLE),
                    )

                    var previous by remember { mutableStateOf(progress) }
                    val forward = progress.path.steps.indexOf(step) >= previous.path.steps.indexOf(previous.step)
                    LaunchedEffect(progress) { previous = progress }
                    AnimatedContent(
                        targetState = step,
                        modifier = Modifier.launchEntrance(LaunchOrder.ACTIONS, rise = LaunchRise),
                        transitionSpec = { EnterTransition.None togetherWith fadeOut(tween(FadeOutMillis)) using SizeTransform(clip = false) },
                        label = "onboardingStep",
                    ) { shown ->
                        val arrivals = rememberStepArrivals(forward)
                        if (shown.isHero) {
                            HeroText(
                                step = shown,
                                songCount = viewModel.songCount.collectAsStateWithLifecycle().value,
                                videoCount = viewModel.videoCount.collectAsStateWithLifecycle().value,
                                arrivals = arrivals,
                                modifier = Modifier.padding(top = heroTop + CompanionSize + 36.dp),
                            )
                        } else {
                            StepBody(
                                step = shown,
                                viewModel = viewModel,
                                arrivals = arrivals,
                                modifier = Modifier.fillMaxSize().padding(top = bodyTop, bottom = actionsHeight),
                            )
                        }
                    }

                    OnboardingActions(
                        step = step,
                        viewModel = viewModel,
                        onOpenLibrary = { isLeaving = true },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(start = ScreenPadding, end = ScreenPadding, bottom = ScreenPadding + navigationBar)
                            .launchEntrance(LaunchOrder.TABS, rise = LaunchRise),
                    )
                }
            }
        }
    }
}

/** Back, the progress through the steps between the first and the last, and skip - the steps' own, faded out on the first and last. */
@Composable
private fun OnboardingBar(
    progress: OnboardingProgress,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onStep: (OnboardingStep) -> Unit,
    isHeld: Boolean,
    modifier: Modifier = Modifier,
) {
    val step = progress.step
    val inner = progress.path.steps.drop(1).dropLast(1)
    val current = inner.indexOf(step)
    val canGoBack = !step.isHero && !isHeld
    val canSkip = step != OnboardingStep.DONE && !isHeld
    val barAlpha by animateFloatAsState(if (step.isHero) 0f else 1f, tween(200), label = "onboardingBar")
    val backAlpha by animateFloatAsState(if (canGoBack) 1f else 0f, tween(200), label = "onboardingBack")
    val skipAlpha by animateFloatAsState(if (canSkip) 1f else 0f, tween(200), label = "onboardingSkip")
    val progressLabel = stringResource(R.string.onboarding_progress)

    Row(
        modifier = modifier.fillMaxWidth().height(BarHeight).padding(start = 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SonaIconButton(
            onClick = onBack,
            icon = SonaIcons.ArrowBack,
            contentDescription = stringResource(R.string.onboarding_back),
            enabled = canGoBack,
            modifier = Modifier.graphicsLayer { alpha = backAlpha },
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .graphicsLayer { alpha = barAlpha }
                .semantics {
                    contentDescription = progressLabel
                    progressBarRangeInfo = ProgressBarRangeInfo((current + 1).toFloat(), 0f..inner.size.toFloat(), inner.size)
                },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            inner.forEachIndexed { index, segmentStep ->
                val weight by animateFloatAsState(
                    targetValue = if (index == current) CurrentSegmentWeight else 1f,
                    animationSpec = tween(420, easing = EmphasizedDecelerate),
                    label = "progressSegment",
                )
                val color by animateColorAsState(
                    targetValue = if (index <= current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                    animationSpec = tween(300),
                    label = "progressSegmentColor",
                )
                // Each segment a way to its step: the bar's whole height pressed, the line thickening while held.
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val thickness by animateDpAsState(if (isPressed) PressedSegmentHeight else SegmentHeight, tween(120), label = "progressSegmentPress")
                val stepLabel = stringResource(R.string.onboarding_go_to_step, index + 1, inner.size)
                Box(
                    modifier = Modifier
                        .weight(weight)
                        .height(BarHeight)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            enabled = !step.isHero && !isHeld && index != current,
                            role = Role.Button,
                            onClickLabel = stepLabel,
                            onClick = { onStep(segmentStep) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(thickness)
                            .clip(RoundedCornerShape(50))
                            .drawBehind { drawRect(color) },
                    )
                }
            }
        }
        TextButton(
            onClick = onSkip,
            enabled = canSkip,
            shapes = buttonPressShapes(),
            modifier = Modifier.graphicsLayer { alpha = skipAlpha },
        ) {
            Text(stringResource(if (step == OnboardingStep.WELCOME) R.string.onboarding_skip_setup else R.string.onboarding_skip))
        }
    }
}

/** What is ready as the setup ends - "212 songs are ready", or "212 songs and 64 videos are ready" - then where to change it all. */
@Composable
private fun doneMessage(songCount: Int, videoCount: Int): String {
    val songs = pluralStringResource(R.plurals.onboarding_songs, songCount, songCount)
    val ready = if (videoCount == 0) {
        pluralStringResource(R.plurals.onboarding_done_ready, songCount, songs)
    } else {
        stringResource(R.string.onboarding_done_ready_both, songs, pluralStringResource(R.plurals.onboarding_videos_count, videoCount, videoCount))
    }
    return ready + " " + stringResource(R.string.onboarding_done_change_later)
}

/** The first and the last step's words, centred under the companion. */
@Composable
private fun HeroText(step: OnboardingStep, songCount: Int, videoCount: Int, arrivals: StepArrivals, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val (title, message) = when (step) {
            OnboardingStep.DONE -> stringResource(R.string.onboarding_done_title) to
                doneMessage(songCount, videoCount)
            else -> stringResource(R.string.onboarding_welcome_title) to stringResource(R.string.onboarding_welcome_message)
        }
        with(arrivals) {
            Text(title, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center, modifier = Modifier.arrive(0))
            Text(
                message,
                style = MessageStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.arrive(1),
            )
        }
    }
}

/** One of a step's buttons: what it says, does, and whether it can be pressed yet. */
private class StepAction(val label: String, val onClick: () -> Unit, val isEnabled: Boolean = true)

/**
 * How a step's buttons stand at its foot: the main one alone, the other alone, the two side by side, or - on the first
 * step - the main one above the other.
 */
private enum class ActionsArrangement { PRIMARY, SECONDARY, SIDE_BY_SIDE, STACKED }

/**
 * What a step asks the user to do next, pinned at its foot: one button, or two where there is a real choice. The same two
 * buttons all the way through - never made again - so from one step to the next each moves, grows or shrinks to its new
 * place, takes its new words, and comes or goes where the step has one button fewer or more, rather than being swapped.
 */
@Composable
private fun OnboardingActions(step: OnboardingStep, viewModel: OnboardingViewModel, onOpenLibrary: () -> Unit, modifier: Modifier = Modifier) {
    val restore by viewModel.restore.collectAsStateWithLifecycle()
    val connectDrive = rememberDriveConnection(viewModel)
    var primary: StepAction? = null
    var secondary: StepAction? = null
    when (step) {
        OnboardingStep.WELCOME -> {
            primary = StepAction(stringResource(R.string.onboarding_get_started), viewModel::getStarted)
            secondary = StepAction(stringResource(R.string.onboarding_restore_from_backup), viewModel::startRestore)
        }
        OnboardingStep.UPDATES -> {
            secondary = StepAction(stringResource(R.string.onboarding_not_now), { viewModel.chooseAutoUpdate(false) })
            primary = StepAction(stringResource(R.string.onboarding_turn_on), { viewModel.chooseAutoUpdate(true) })
        }
        OnboardingStep.RESTORE -> when {
            // Nothing to restore yet: the only way on is the setup a new user goes through.
            restore.stage == RestoreStage.SOURCE || restore is RestoreState.NoBackups ->
                secondary = StepAction(stringResource(R.string.onboarding_restore_start_fresh), viewModel::startFresh)
            restore.stage == RestoreStage.RESTORED -> primary = StepAction(stringResource(R.string.onboarding_continue), viewModel::finishRestore)
            // Still looking: Restore waits for a backup to be found.
            restore is RestoreState.Searching -> primary = StepAction(stringResource(R.string.onboarding_restore), {}, isEnabled = false)
            else -> {
                val chosen = restore as? RestoreState.Chosen
                val status = chosen?.status
                primary = if (status == RestoreStatus.FAILED && chosen.failure == BackupFailure.DRIVE_ACCESS_LOST) {
                    // A restore stopped for want of Drive's access carries on once the account is connected again.
                    StepAction(stringResource(R.string.onboarding_restore_connect_drive), { connectDrive(DrivePurpose.CARRY_ON_RESTORE) })
                } else {
                    StepAction(
                        label = stringResource(
                            when (status) {
                                RestoreStatus.RESTORING -> R.string.onboarding_restoring
                                RestoreStatus.FAILED -> R.string.onboarding_try_again
                                else -> R.string.onboarding_restore
                            },
                        ),
                        onClick = viewModel::restore,
                        isEnabled = (status == RestoreStatus.READY && chosen.choosesAnything) || status == RestoreStatus.FAILED,
                    )
                }
            }
        }
        OnboardingStep.DONE -> primary = StepAction(stringResource(R.string.onboarding_open_library), onOpenLibrary)
        else -> primary = StepAction(stringResource(R.string.onboarding_continue), viewModel::next)
    }
    val arrangement = when {
        primary == null -> ActionsArrangement.SECONDARY
        secondary == null -> ActionsArrangement.PRIMARY
        step == OnboardingStep.WELCOME -> ActionsArrangement.STACKED
        else -> ActionsArrangement.SIDE_BY_SIDE
    }
    // A button going keeps what it last said as it fades.
    val last = remember { arrayOfNulls<StepAction>(2) }
    primary?.let { last[0] = it }
    secondary?.let { last[1] = it }

    ActionsLayout(
        arrangement = arrangement,
        primary = { isShown -> last[0]?.let { PrimaryButton(it, isShown) } },
        secondary = { isShown -> last[1]?.let { SecondaryButton(it, isShown, isOutlined = step != OnboardingStep.WELCOME) } },
        modifier = modifier,
    )
}

/** Where one of the buttons stands: its sides, in halves of the row from its start and from its end, and its row from the foot. */
private class ActionPlace(val start: Float, val end: Float, val row: Float, val isShown: Boolean)

private val FullWidth = ActionPlace(start = 0f, end = 0f, row = 0f, isShown = true)

private fun ActionsArrangement.primaryPlace(): ActionPlace = when (this) {
    ActionsArrangement.PRIMARY -> FullWidth
    // Gone into the other's place, so it leaves where the other stays.
    ActionsArrangement.SECONDARY -> ActionPlace(0f, 0f, 0f, isShown = false)
    ActionsArrangement.SIDE_BY_SIDE -> ActionPlace(start = 1f, end = 0f, row = 0f, isShown = true)
    ActionsArrangement.STACKED -> ActionPlace(0f, 0f, row = 1f, isShown = true)
}

private fun ActionsArrangement.secondaryPlace(): ActionPlace = when (this) {
    // Folded to nothing at the start side, so it comes out from there - growing as the main one makes room, never over it.
    ActionsArrangement.PRIMARY -> ActionPlace(start = 0f, end = 2f, row = 0f, isShown = false)
    ActionsArrangement.SECONDARY, ActionsArrangement.STACKED -> FullWidth
    ActionsArrangement.SIDE_BY_SIDE -> ActionPlace(start = 0f, end = 1f, row = 0f, isShown = true)
}

/** How long the buttons take to move to their places, and to come or go. */
private const val ActionsMillis = 420

/** The room between the buttons side by side, and one above the other. */
private val ActionsGap = 8.dp
private val ActionsRowGap = 4.dp

/**
 * The two buttons, each in its place for [arrangement] - moving, growing and shrinking between places as it changes, in
 * the layout's own pass, so nothing is composed again for it. Always two rows tall, the buttons standing at its foot, so
 * its own place never moves; a button not shown is faded out where the other stands and beneath it, out of touch's way.
 */
@Composable
private fun ActionsLayout(
    arrangement: ActionsArrangement,
    primary: @Composable (isShown: Boolean) -> Unit,
    secondary: @Composable (isShown: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primaryPlace = arrangement.primaryPlace()
    val secondaryPlace = arrangement.secondaryPlace()
    val primaryStart = animatePlace(primaryPlace.start, "primaryStart")
    val primaryEnd = animatePlace(primaryPlace.end, "primaryEnd")
    val primaryRow = animatePlace(primaryPlace.row, "primaryRow")
    val secondaryStart = animatePlace(secondaryPlace.start, "secondaryStart")
    val secondaryEnd = animatePlace(secondaryPlace.end, "secondaryEnd")
    val secondaryRow = animatePlace(secondaryPlace.row, "secondaryRow")

    Layout(
        contents = listOf({ primary(primaryPlace.isShown) }, { secondary(secondaryPlace.isShown) }),
        modifier = modifier.fillMaxWidth().height(ButtonHeight * 2 + ActionsRowGap),
    ) { (primaryMeasurables, secondaryMeasurables), constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val half = (width + ActionsGap.toPx()) / 2f
        val rowStep = (ButtonHeight + ActionsRowGap).toPx()
        val buttonHeight = ButtonHeight.roundToPx()

        fun placeOf(measurables: List<Measurable>, start: State<Float>, end: State<Float>, row: State<Float>) = measurables.map {
            val left = start.value * half
            val right = width - end.value * half
            it.measure(Constraints.fixed((right - left).roundToInt().coerceAtLeast(0), buttonHeight)) to
                IntOffset(left.roundToInt(), (height - buttonHeight - row.value * rowStep).roundToInt())
        }
        val primaries = placeOf(primaryMeasurables, primaryStart, primaryEnd, primaryRow)
        val secondaries = placeOf(secondaryMeasurables, secondaryStart, secondaryEnd, secondaryRow)
        layout(width, height) {
            // The one shown above the one going, so the one going never takes a touch.
            primaries.forEach { (placeable, at) -> placeable.placeRelative(at, zIndex = if (primaryPlace.isShown) 1f else 0f) }
            secondaries.forEach { (placeable, at) -> placeable.placeRelative(at, zIndex = if (secondaryPlace.isShown) 1f else 0f) }
        }
    }
}

@Composable
private fun animatePlace(target: Float, label: String): State<Float> =
    animateFloatAsState(target, tween(ActionsMillis, easing = EmphasizedDecelerate), label = label)

/**
 * A button fading in as it comes and out as it goes - out of touch and of what is read aloud meanwhile - its words changing
 * in a short cross-fade.
 */
private fun Modifier.actionShown(isShown: Boolean, alpha: State<Float>): Modifier =
    graphicsLayer { this.alpha = alpha.value }.then(if (isShown) Modifier else Modifier.clearAndSetSemantics {})

@Composable
private fun shownAlpha(isShown: Boolean): State<Float> =
    animateFloatAsState(if (isShown) 1f else 0f, tween(if (isShown) ActionsMillis else FadeOutMillis), label = "actionShown")

@Composable
private fun ActionLabel(label: String) {
    AnimatedContent(
        targetState = label,
        transitionSpec = { fadeIn(tween(FadeInMillis, delayMillis = FadeOutMillis)) togetherWith fadeOut(tween(FadeOutMillis)) using null },
        label = "actionLabel",
    ) { Text(it, style = MaterialTheme.typography.titleMedium, maxLines = 1) }
}

/** The step's main way on, filled. */
@Composable
private fun PrimaryButton(action: StepAction, isShown: Boolean) {
    val alpha = shownAlpha(isShown)
    Button(
        onClick = { if (isShown) action.onClick() },
        enabled = action.isEnabled,
        shapes = buttonPressShapes(),
        modifier = Modifier.actionShown(isShown, alpha),
    ) { ActionLabel(action.label) }
}

/**
 * The way on that is not the step's main one: outlined, in the text's colour - or, beneath the first step's main one, a
 * plain text button in the accent's; its outline and colour turning from one to the other.
 */
@Composable
private fun SecondaryButton(action: StepAction, isShown: Boolean, isOutlined: Boolean) {
    val alpha = shownAlpha(isShown)
    val outline = (ButtonDefaults.outlinedButtonBorder(enabled = true).brush as? SolidColor)?.value ?: MaterialTheme.colorScheme.outlineVariant
    val border by animateColorAsState(if (isOutlined) outline else outline.copy(alpha = 0f), tween(ActionsMillis), label = "actionOutline")
    val content by animateColorAsState(
        if (isOutlined) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
        tween(ActionsMillis),
        label = "actionContent",
    )
    OutlinedButton(
        onClick = { if (isShown) action.onClick() },
        enabled = action.isEnabled,
        shapes = buttonPressShapes(),
        border = BorderStroke(1.dp, border),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = content),
        modifier = Modifier.actionShown(isShown, alpha),
    ) { ActionLabel(action.label) }
}

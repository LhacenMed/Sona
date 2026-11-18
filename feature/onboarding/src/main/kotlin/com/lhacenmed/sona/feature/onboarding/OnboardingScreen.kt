@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.lhacenmed.sona.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
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
    var companionBounds by remember { mutableStateOf(Rect.Zero) }
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

    LaunchedEffect(isLeaving) {
        if (!isLeaving) return@LaunchedEffect
        onEndInSight()
        viewModel.finish()
        delay(CompanionMorphMillis.toLong())
        onFinished(companionBounds, cookieColor, noteColor)
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
                OnboardingCompanion(
                    step = step,
                    isLeaving = isLeaving,
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
                        }
                        .onGloballyPositioned { companionBounds = it.boundsInRoot() },
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
                                trackCount = viewModel.trackCount.collectAsStateWithLifecycle().value,
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

/** The first and the last step's words, centred under the companion. */
@Composable
private fun HeroText(step: OnboardingStep, trackCount: Int, arrivals: StepArrivals, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val (title, message) = when (step) {
            OnboardingStep.DONE -> stringResource(R.string.onboarding_done_title) to
                pluralStringResource(R.plurals.onboarding_done_message, trackCount, trackCount)
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

/** What a step asks the user to do next, pinned at its foot: one button, or two where there is a real choice. */
@Composable
private fun OnboardingActions(step: OnboardingStep, viewModel: OnboardingViewModel, onOpenLibrary: () -> Unit, modifier: Modifier = Modifier) {
    val restore by viewModel.restore.collectAsStateWithLifecycle()
    when (step) {
        OnboardingStep.WELCOME -> Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PrimaryButton(stringResource(R.string.onboarding_get_started), onClick = viewModel::getStarted)
            TextButton(
                onClick = viewModel::startRestore,
                shapes = buttonPressShapes(),
                modifier = Modifier.fillMaxWidth().height(ButtonHeight),
            ) { Text(stringResource(R.string.onboarding_restore_from_backup), style = MaterialTheme.typography.titleMedium) }
        }
        OnboardingStep.UPDATES -> Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(stringResource(R.string.onboarding_not_now), onClick = { viewModel.chooseAutoUpdate(false) }, modifier = Modifier.weight(1f))
            PrimaryButton(stringResource(R.string.onboarding_turn_on), onClick = { viewModel.chooseAutoUpdate(true) }, modifier = Modifier.weight(1f))
        }
        OnboardingStep.RESTORE -> when (restore.stage) {
            // Nothing chosen yet: the only way on is the setup a new user goes through.
            RestoreStage.SOURCE, RestoreStage.NO_BACKUPS ->
                SecondaryButton(stringResource(R.string.onboarding_restore_start_fresh), onClick = viewModel::startFresh, modifier = modifier)
            RestoreStage.RESTORED -> PrimaryButton(stringResource(R.string.onboarding_continue), onClick = viewModel::finishRestore, modifier = modifier)
            else -> {
                val chosen = restore as? RestoreState.Chosen
                val status = chosen?.status
                val connectDrive = rememberDriveConnection(viewModel)
                // A restore stopped for want of Drive's access carries on once the account is connected again.
                if (status == RestoreStatus.FAILED && chosen.failure == BackupFailure.DRIVE_ACCESS_LOST) {
                    PrimaryButton(
                        stringResource(R.string.onboarding_restore_connect_drive),
                        onClick = { connectDrive(DrivePurpose.CARRY_ON_RESTORE) },
                        modifier = modifier,
                    )
                    return
                }
                PrimaryButton(
                    label = stringResource(
                        when (status) {
                            RestoreStatus.RESTORING -> R.string.onboarding_restoring
                            RestoreStatus.FAILED -> R.string.onboarding_try_again
                            else -> R.string.onboarding_restore
                        },
                    ),
                    onClick = viewModel::restore,
                    enabled = status == RestoreStatus.READY || status == RestoreStatus.FAILED,
                    modifier = modifier,
                )
            }
        }
        OnboardingStep.DONE -> PrimaryButton(stringResource(R.string.onboarding_open_library), onClick = onOpenLibrary, modifier = modifier)
        else -> PrimaryButton(stringResource(R.string.onboarding_continue), onClick = viewModel::next, modifier = modifier)
    }
}

/** The way on that is not the step's main one: outlined, in the text's colour. */
@Composable
private fun SecondaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        shapes = buttonPressShapes(),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        modifier = modifier.fillMaxWidth().height(ButtonHeight),
    ) { Text(label, style = MaterialTheme.typography.titleMedium) }
}

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shapes = buttonPressShapes(),
        modifier = modifier.fillMaxWidth().height(ButtonHeight),
    ) { Text(label, style = MaterialTheme.typography.titleMedium) }
}

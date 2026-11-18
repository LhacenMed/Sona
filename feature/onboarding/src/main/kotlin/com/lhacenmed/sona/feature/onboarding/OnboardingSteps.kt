@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.lhacenmed.sona.feature.onboarding

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.runtime.Stable
import com.lhacenmed.sona.core.downloader.RuntimeState
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.activity.result.IntentSenderRequest
import androidx.compose.animation.AnimatedContent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.foundation.clickable
import com.lhacenmed.sona.core.designsystem.theme.iconButtonPressShapes
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.annotation.StringRes
import android.os.Environment
import android.os.StatFs
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.VerticalDivider
import androidx.compose.ui.semantics.role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.withStyle
import com.lhacenmed.sona.core.common.format.formatDuration
import com.lhacenmed.sona.core.designsystem.component.sheet.SonaColumnBottomSheet
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.backup.RestorePart
import androidx.compose.foundation.layout.heightIn
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.lhacenmed.sona.core.designsystem.motion.FadeOutMillis
import com.lhacenmed.sona.feature.backup.BackupFileTypes
import com.lhacenmed.sona.feature.backup.BackupStep
import com.lhacenmed.sona.feature.backup.work.BackupFailure
import com.lhacenmed.sona.feature.backup.RestorePreview
import com.lhacenmed.sona.feature.backup.StoredBackup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.common.permission.AppPermission
import com.lhacenmed.sona.core.datastore.OnboardingStep
import com.lhacenmed.sona.core.datastore.UpdateChannel
import com.lhacenmed.sona.core.designsystem.component.SonaConnectedChoice
import com.lhacenmed.sona.core.designsystem.component.SonaGroupRow
import com.lhacenmed.sona.core.designsystem.component.SonaRowGroup
import com.lhacenmed.sona.core.designsystem.component.SonaSwitch
import com.lhacenmed.sona.core.designsystem.component.permission.rememberPermissionRequest
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import com.lhacenmed.sona.core.designsystem.motion.EmphasizedDecelerate
import com.lhacenmed.sona.core.designsystem.motion.FadeInMillis
import com.lhacenmed.sona.core.designsystem.motion.StaggerMillis
import com.lhacenmed.sona.core.designsystem.theme.buttonPressShapes
import com.lhacenmed.sona.core.designsystem.theme.ContainerEdge
import com.lhacenmed.sona.core.designsystem.theme.palette.ThemePalettes
import com.lhacenmed.sona.core.model.DEFAULT_DOWNLOAD_FOLDER
import com.lhacenmed.sona.core.model.ThemeMode
import com.lhacenmed.sona.feature.download.rememberDownloadFolderPicker
import com.lhacenmed.sona.feature.library.tabs.LibraryTabEditor
import com.lhacenmed.sona.feature.library.tabs.libraryTabRows
import com.lhacenmed.sona.feature.library.tabs.rememberLibraryTabEditor

/** How far a step's content slides in from the side it is going to. */
private val ArrivalShift = 24.dp

/** How long a step's content takes to slide into place. */
private const val ArrivalMillis = 360

/** The latest a step's piece arrives after the first - the rest come with it. */
private const val LastStaggered = 4

/** Where a step's own pieces start arriving: after its title and its line. */
private const val FirstPieceOrder = 2

/** The fade at a step's foot, where its content scrolls under the buttons. */
private val BottomFade = 24.dp

/** A step's line under its title. */
internal val MessageStyle = TextStyle(fontSize = 15.sp, lineHeight = 22.sp)

/** The accents offered where colours do not follow the cover - presets of Settings' palettes, by their ids. */
private val AccentPaletteIds = listOf("default", "sunset_orange", "teal_wave", "ocean_blue", "olive_garden")

/** How what a choice, or a screen's line, shows under it comes and goes: opening into its room as it fades in, closing as it fades out. */
private val OptionEnter = expandVertically(tween(ArrivalMillis, easing = EmphasizedDecelerate), expandFrom = Alignment.Top) +
    fadeIn(tween(FadeInMillis, delayMillis = FadeOutMillis))
private val OptionExit = shrinkVertically(tween(ArrivalMillis, easing = EmphasizedDecelerate), shrinkTowards = Alignment.Top) +
    fadeOut(tween(FadeOutMillis))

/** How long the last of a screen's pieces takes to be in place, from the moment the screen is composed. */
private const val ArrivalsMillis = LastStaggered * StaggerMillis + ArrivalMillis

/**
 * How a screen's pieces arrive: one after another, [StaggerMillis] apart, fading in as they slide from the side the setup is
 * going - [forward] or back. Timed by a clock of its own, started as the screen is composed - see [rememberStepArrivals] - so
 * the pieces arrive whatever transition the screen is in, and are always in place in the end.
 */
@Stable
internal class StepArrivals(private val forward: Boolean) {
    private val clock = Animatable(0f)

    internal suspend fun run() {
        clock.animateTo(ArrivalsMillis.toFloat(), tween((ArrivalsMillis - clock.value).toInt(), easing = LinearEasing))
    }

    @Composable
    fun Modifier.arrive(order: Int): Modifier {
        val delay = order.coerceAtMost(LastStaggered) * StaggerMillis
        val towardEnd = forward == (LocalLayoutDirection.current == LayoutDirection.Ltr)
        return graphicsLayer {
            alpha = LinearOutSlowInEasing.transform(((clock.value - delay) / FadeInMillis).coerceIn(0f, 1f))
            val away = 1f - EmphasizedDecelerate.transform(((clock.value - delay) / ArrivalMillis).coerceIn(0f, 1f))
            translationX = away * ArrivalShift.toPx() * if (towardEnd) 1f else -1f
        }
    }
}

/** A screen's [StepArrivals], arriving from the moment it is first composed. */
@Composable
internal fun rememberStepArrivals(forward: Boolean): StepArrivals {
    val arrivals = remember { StepArrivals(forward) }
    LaunchedEffect(arrivals) { arrivals.run() }
    return arrivals
}

/** A step between the first and the last: its title and line, then its choices - scrolling under the buttons if it must. */
@Composable
internal fun StepBody(step: OnboardingStep, viewModel: OnboardingViewModel, arrivals: StepArrivals, modifier: Modifier = Modifier) {
    if (step == OnboardingStep.RESTORE) return RestoreBody(viewModel, arrivals, modifier)
    val listState = rememberLazyListState()
    val (title, message) = when (step) {
        OnboardingStep.ACCESS -> R.string.onboarding_access_title to R.string.onboarding_access_message
        OnboardingStep.FOLDERS -> R.string.onboarding_folders_title to R.string.onboarding_folders_message
        OnboardingStep.LIBRARY -> R.string.onboarding_library_title to R.string.onboarding_library_message
        OnboardingStep.DOWNLOADS -> R.string.onboarding_downloads_title to R.string.onboarding_downloads_message
        OnboardingStep.LOOK -> R.string.onboarding_look_title to R.string.onboarding_look_message
        else -> R.string.onboarding_updates_title to R.string.onboarding_updates_message
    }
    // Composed only on the library's step, which each step's content keeps to.
    val tabEditor = if (step == OnboardingStep.LIBRARY) rememberLibraryTabEditor(listState) else null
    // Each step's own content, as items under the title - the library's rows being the tab editor's, laid in this list.
    val content: LazyListScope.() -> Unit = when (step) {
        OnboardingStep.ACCESS -> accessStep(arrivals)
        OnboardingStep.FOLDERS -> foldersStep(viewModel, arrivals)
        OnboardingStep.LIBRARY -> libraryStep(viewModel, arrivals, checkNotNull(tabEditor))
        OnboardingStep.DOWNLOADS -> downloadsStep(viewModel, arrivals)
        OnboardingStep.LOOK -> lookStep(viewModel, arrivals)
        else -> updatesStep(viewModel, arrivals)
    }
    StepList(stringResource(title), stringResource(message), arrivals, modifier, listState, content)
}

/** A step's title and line, then [content] - in one list that scrolls under the buttons if it must, fading as it goes. */
@Composable
private fun StepList(
    title: String,
    message: String?,
    arrivals: StepArrivals,
    modifier: Modifier,
    listState: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    // The last line said, so one leaving folds away still saying it.
    val lastMessage = remember { arrayOfNulls<String>(1) }
    message?.let { lastMessage[0] = it }
    LazyColumn(state = listState, modifier = modifier.fadingFoot()) {
        item(key = "title") {
            with(arrivals) {
                Column(modifier = Modifier.padding(horizontal = ScreenPadding).padding(bottom = 16.dp)) {
                    Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.arrive(0))
                    AnimatedVisibility(visible = message != null, enter = OptionEnter, exit = OptionExit) {
                        Text(
                            lastMessage[0].orEmpty(),
                            style = MessageStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp).arrive(1),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        content()
        item(key = "end") { Spacer(Modifier.height(BottomFade)) }
    }
}

/** Fades the list's foot to nothing, so what scrolls under the buttons leaves rather than being cut. */
private fun Modifier.fadingFoot(): Modifier = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fade = BottomFade.toPx()
        drawRect(
            brush = Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = size.height - fade, endY = size.height),
            blendMode = BlendMode.DstIn,
        )
    }

/** A piece of a step, inset from the screen's sides, arriving [order]th. */
private fun LazyListScope.piece(key: String, arrivals: StepArrivals, order: Int, content: @Composable () -> Unit) {
    item(key = key) {
        with(arrivals) {
            // Coming and going as a screen's rows change - the others moving to make room - not snapping in or out.
            Box(modifier = Modifier.animateItem(fadeInSpec = tween(FadeInMillis, delayMillis = FadeOutMillis), placementSpec = tween(ArrivalMillis, easing = EmphasizedDecelerate), fadeOutSpec = tween(FadeOutMillis)).padding(horizontal = ScreenPadding).arrive(order)) { content() }
        }
    }
}

/** What every row of the setup is painted on: the surface container, a tone above the setup's surface. */
@Composable
private fun rowColors(): ListItemColors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)

/** A label over a group of a step's choices. */
@Composable
private fun GroupLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = 20.dp, bottom = 8.dp),
    )
}

/** A row of a group that turns something on or off, the whole row pressed as its switch. */
private fun switchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector? = null,
    summary: String? = null,
    enabled: Boolean = true,
): SonaGroupRow = { shapes ->
    SegmentedListItem(
        onClick = { onCheckedChange(!checked) },
        shapes = shapes,
        colors = rowColors(),
        enabled = enabled,
        leadingContent = icon?.let { { Icon(it, contentDescription = null) } },
        trailingContent = { SonaSwitch(checked = checked, onCheckedChange = null, enabled = enabled) },
        supportingContent = summary?.let { { Text(it) } },
    ) { Text(title) }
}

/** A row's own small button - Allow, Connect - asking right there. */
@Composable
private fun Pill(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shapes = buttonPressShapes(),
        modifier = Modifier.height(36.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) { Text(label) }
}

/** A few plain lines with their icons, on one card - what a step wants understood before anything is chosen. */
@Composable
private fun InfoCard(lines: List<Pair<ImageVector, String>>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        lines.forEach { (icon, text) ->
            Row(modifier = Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

// Restore.

/**
 * The screens restoring goes through on its step: where the backup is, then - one screen for each - the account's backups
 * or the file chosen, then restoring it and what came back. Within a screen nothing is swapped: looking through an account,
 * its backups found, one read, its parts chosen - each comes in, or goes, where it stands, the rest moving to make room.
 */
internal enum class RestoreStage { SOURCE, DRIVE, FILE, RESTORING, RESTORED }

internal val RestoreState.stage: RestoreStage
    get() = when (this) {
        is RestoreState.ChoosingSource -> RestoreStage.SOURCE
        is RestoreState.Searching, is RestoreState.NoBackups -> RestoreStage.DRIVE
        is RestoreState.Chosen -> when {
            status == RestoreStatus.RESTORING || status == RestoreStatus.FAILED || source == BackupSource.Earlier -> RestoreStage.RESTORING
            source is BackupSource.Drive -> RestoreStage.DRIVE
            else -> RestoreStage.FILE
        }
        is RestoreState.Restored -> RestoreStage.RESTORED
    }

/** The steps a restore goes through, as it says it is on them - see [BackupStep]. */
private val RestoreSteps = BackupStep.entries.dropWhile { it != BackupStep.READING_BACKUP }

/** The parts of what comes back the choice sheet shows, one tab each. */
private enum class ChoiceTab { LIBRARY, TRACKS, VIDEOS }

/** How what comes in under a heading - a backup being read, then its parts - replaces what was there: fading through, resizing between. */
private fun AnimatedContentTransitionScope<*>.fadeThrough(): ContentTransform =
    fadeIn(tween(FadeInMillis, delayMillis = FadeOutMillis)) togetherWith fadeOut(tween(FadeOutMillis)) using
        SizeTransform(clip = false) { _, _ -> tween<IntSize>(ArrivalMillis, easing = EmphasizedDecelerate) }

/**
 * Restoring: where the backup is - Google Drive or a file - then which, and what of it, then restoring it and what came back.
 * Each screen arrives as a step does, forward or back; the first with the step itself, by its [arrivals].
 */
@Composable
private fun RestoreBody(viewModel: OnboardingViewModel, arrivals: StepArrivals, modifier: Modifier) {
    val restore by viewModel.restore.collectAsStateWithLifecycle()
    val initialStage = remember { restore.stage }
    var previous by remember { mutableStateOf(restore.stage) }
    val forward = restore.stage >= previous
    LaunchedEffect(restore.stage) { previous = restore.stage }
    val connectDrive = rememberDriveConnection(viewModel)
    val chooseFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::openBackupFile) }
    val onChooseFile = { chooseFile.launch(BackupFileTypes) }
    val onChangeAccount = { connectDrive(DrivePurpose.FIND_BACKUPS) }
    var choiceTab by rememberSaveable { mutableStateOf<ChoiceTab?>(null) }

    AnimatedContent(
        targetState = restore,
        contentKey = { it.stage },
        transitionSpec = { EnterTransition.None togetherWith fadeOut(tween(FadeOutMillis)) },
        label = "restoreStage",
    ) { shown ->
        // The first screen arrives with the step; each after it, on its own.
        val stageArrivals = if (shown.stage == initialStage) arrivals else rememberStepArrivals(forward)
        val (title, message) = restoreText(shown)
        // Each screen's own rows, laid in the list under the title.
        val content: LazyListScope.() -> Unit = when (shown.stage) {
            RestoreStage.SOURCE -> sourceStage(shown as RestoreState.ChoosingSource, stageArrivals, onConnect = onChangeAccount, onChooseFile = onChooseFile)
            RestoreStage.DRIVE -> driveStage(shown, viewModel, stageArrivals, onChangeAccount, onChooseFile, onChoose = { choiceTab = it })
            RestoreStage.FILE -> fileStage(shown as RestoreState.Chosen, viewModel, stageArrivals, onChooseFile, onChoose = { choiceTab = it })
            RestoreStage.RESTORING -> restoringStage(shown as RestoreState.Chosen, stageArrivals)
            RestoreStage.RESTORED -> restoredStage(shown as RestoreState.Restored, viewModel, stageArrivals, onConnect = { connectDrive(DrivePurpose.BACK_UP) })
        }
        StepList(title, message, stageArrivals, modifier, content = content)
    }

    // Choosing exactly what comes back - only while the backup chosen is open and nothing has started.
    val chosen = (restore as? RestoreState.Chosen)?.takeIf { it.status == RestoreStatus.READY && it.preview != null }
    val tab = choiceTab
    if (chosen != null && tab != null) {
        RestoreChoiceSheet(chosen, initialTab = tab, viewModel = viewModel, onDismiss = { choiceTab = null })
    } else if (chosen == null && tab != null) {
        LaunchedEffect(Unit) { choiceTab = null }
    }
}

/** What a restore screen is called, and what it says under that - nothing, where its rows say it all. */
@Composable
private fun restoreText(restore: RestoreState): Pair<String, String?> = when (restore) {
    is RestoreState.Chosen -> when (restore.stage) {
        RestoreStage.RESTORING -> {
            // From the device it was made on, where that is known; else from the file, or Drive - or carrying on.
            val from = restore.device?.label ?: (restore.source as? BackupSource.File)?.name
            val withMedia = restore.chosenMusic.isNotEmpty() || restore.chosenVideos.isNotEmpty()
            stringResource(R.string.onboarding_restoring) to when {
                from != null && withMedia -> stringResource(R.string.onboarding_restoring_from_with_media, from)
                from != null -> stringResource(R.string.onboarding_restoring_from, from)
                restore.source == BackupSource.Earlier -> stringResource(R.string.onboarding_restoring_carrying_on)
                withMedia -> stringResource(R.string.onboarding_restoring_from_drive_with_media)
                else -> stringResource(R.string.onboarding_restoring_from_drive)
            }
        }
        RestoreStage.DRIVE -> stringResource(R.string.onboarding_restore_title) to null
        else -> stringResource(R.string.onboarding_restore_title) to stringResource(R.string.onboarding_restore_check_message)
    }
    is RestoreState.Restored -> stringResource(R.string.onboarding_restored_title) to stringResource(R.string.onboarding_restored_message)
    else -> stringResource(R.string.onboarding_restore_title) to stringResource(R.string.onboarding_restore_message)
}

/**
 * Connecting a Google account: Google's account sheet, then its consent screen the first time - asked for by the setup,
 * heard back from here. Called with what it is for.
 */
@Composable
internal fun rememberDriveConnection(viewModel: OnboardingViewModel): (DrivePurpose) -> Unit {
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        viewModel.finishConnectingDrive(result.data, wasCancelled = result.resultCode != Activity.RESULT_OK)
    }
    return { purpose ->
        viewModel.connectDrive(onConsent = { consent.launch(IntentSenderRequest.Builder(it).build()) }, purpose = purpose)
    }
}

private fun sourceStage(
    source: RestoreState.ChoosingSource,
    arrivals: StepArrivals,
    onConnect: () -> Unit,
    onChooseFile: () -> Unit,
): LazyListScope.() -> Unit = {
    piece("drive", arrivals, 2) { DriveSourceCard(source, onConnect) }
    piece("file", arrivals, 3) {
        JoinedRow(index = 1) { shapes ->
            SegmentedListItem(
                onClick = onChooseFile,
                shapes = shapes,
                colors = rowColors(),
                leadingContent = { Icon(SonaIcons.FolderOpen, contentDescription = null) },
                trailingContent = { Icon(SonaIcons.KeyboardArrowRight, contentDescription = null) },
                supportingContent = { Text(stringResource(R.string.onboarding_restore_file_summary)) },
            ) { Text(stringResource(R.string.onboarding_restore_file)) }
        }
    }
}

/** A row laid as the [index]th of a two-row group, the [DriveSourceCard] being the first. */
@Composable
private fun JoinedRow(index: Int, row: SonaGroupRow) {
    Box(modifier = Modifier.padding(top = ListItemDefaults.SegmentedGap)) { row(ListItemDefaults.segmentedShapes(index = index, count = 2)) }
}

/** Google Drive, first: what it is, the one button that connects an account, and what Sona will see there - nothing else. */
@Composable
private fun DriveSourceCard(source: RestoreState.ChoosingSource, onConnect: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
            .background(colors.surfaceContainer)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(colors.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(SonaIcons.Cloud, contentDescription = null, tint = colors.onPrimaryContainer) }
            Column {
                Text(stringResource(R.string.onboarding_restore_drive), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.onboarding_restore_drive_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        Button(
            onClick = onConnect,
            enabled = !source.isConnecting,
            shapes = buttonPressShapes(),
            modifier = Modifier.padding(top = 14.dp).fillMaxWidth().height(44.dp),
        ) {
            if (source.isConnecting) {
                LoadingIndicator(modifier = Modifier.size(20.dp))
            } else {
                Icon(SonaIcons.AccountCircle, contentDescription = null, modifier = Modifier.size(20.dp))
            }
            Text(stringResource(R.string.onboarding_restore_connect_drive), modifier = Modifier.padding(start = 8.dp))
        }
        Row(modifier = Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val color = if (source.couldNotConnect) colors.error else colors.onSurfaceVariant
            Icon(SonaIcons.Lock, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Text(
                stringResource(if (source.couldNotConnect) R.string.onboarding_restore_drive_not_connected else R.string.onboarding_restore_drive_trust),
                style = MaterialTheme.typography.bodySmall,
                color = color,
            )
        }
    }
}

/** The connected account, and Change - Google's account sheet again. */
@Composable
private fun AccountChip(account: String, onChange: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(colors.surfaceContainer)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape).background(colors.primary),
            contentAlignment = Alignment.Center,
        ) { Text(account.take(1).uppercase(), style = MaterialTheme.typography.titleSmall, color = colors.onPrimary) }
        Text(account, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        TextButton(onClick = onChange, shapes = buttonPressShapes(ContainerEdge.End)) { Text(stringResource(R.string.onboarding_restore_change)) }
    }
}

/**
 * The connected account's screen, all of it under the account: looking for its backups, then those found - or that there
 * are none - and, of the one picked, what comes back. One list, its rows coming and going as the search and the reading go.
 */
private fun driveStage(
    restore: RestoreState,
    viewModel: OnboardingViewModel,
    arrivals: StepArrivals,
    onChangeAccount: () -> Unit,
    onChooseFile: () -> Unit,
    onChoose: (ChoiceTab) -> Unit,
): LazyListScope.() -> Unit = {
    val account = when (restore) {
        is RestoreState.Searching -> restore.account
        is RestoreState.NoBackups -> restore.account
        is RestoreState.Chosen -> (restore.source as? BackupSource.Drive)?.account.orEmpty()
        else -> ""
    }
    piece("account", arrivals, 2) { AccountChip(account, onChangeAccount) }
    when (restore) {
        is RestoreState.Searching -> piece("searching", arrivals, 3) {
            StatusRow(stringResource(R.string.onboarding_restore_searching), modifier = Modifier.padding(top = 16.dp))
        }
        is RestoreState.NoBackups -> piece("empty", arrivals, 3) { NoBackupsCard(restore, onChangeAccount, onChooseFile) }
        is RestoreState.Chosen -> {
            val source = restore.source as BackupSource.Drive
            piece("backups", arrivals, 3) {
                SonaRowGroup(
                    modifier = Modifier.padding(top = 8.dp),
                    rows = source.backups.mapIndexed { index, backup ->
                        driveBackupRow(backup, isNewest = index == 0, isPicked = backup == source.picked, viewModel::pickDriveBackup)
                    },
                )
            }
            comesBack(restore, viewModel, arrivals, onChoose)
        }
        else -> Unit
    }
}

/** The file chosen, and what of it comes back. */
private fun fileStage(
    restore: RestoreState.Chosen,
    viewModel: OnboardingViewModel,
    arrivals: StepArrivals,
    onChooseFile: () -> Unit,
    onChoose: (ChoiceTab) -> Unit,
): LazyListScope.() -> Unit = {
    val name = (restore.source as? BackupSource.File)?.name.orEmpty()
    piece("file", arrivals, 2) { BackupFileChip(name, restore.preview, onChooseFile) }
    comesBack(restore, viewModel, arrivals, onChoose)
}

/** What a [StatusRow] or [comesBack] shows: the backup being read, a passphrase wanted, one that cannot be read, or its parts. */
private enum class OpenPhase { READING, PASSPHRASE, UNREADABLE, READY }

private val RestoreStatus.phase: OpenPhase
    get() = when (this) {
        RestoreStatus.NEEDS_PASSPHRASE, RestoreStatus.WRONG_PASSPHRASE -> OpenPhase.PASSPHRASE
        RestoreStatus.UNREADABLE, RestoreStatus.UNSUPPORTED -> OpenPhase.UNREADABLE
        RestoreStatus.READY -> OpenPhase.READY
        else -> OpenPhase.READING
    }

/**
 * What comes back of the backup chosen, under its heading: while it is read, a line saying so - then, in its place, what is
 * in it to choose from, or what stands in the way. Picking another backup reads it again, in the same place.
 */
private fun LazyListScope.comesBack(restore: RestoreState.Chosen, viewModel: OnboardingViewModel, arrivals: StepArrivals, onChoose: (ChoiceTab) -> Unit) {
    piece("comesBack", arrivals, 4) {
        Column {
            GroupLabel(stringResource(R.string.onboarding_restore_what_comes_back))
            AnimatedContent(
                targetState = restore,
                contentKey = { it.status.phase },
                transitionSpec = { fadeThrough() },
                contentAlignment = Alignment.TopStart,
                label = "comesBack",
            ) { shown ->
                when (shown.status.phase) {
                    OpenPhase.READING -> StatusRow(stringResource(R.string.onboarding_restore_opening))
                    OpenPhase.PASSPHRASE -> PassphraseField(isWrong = shown.status == RestoreStatus.WRONG_PASSPHRASE, onSubmit = viewModel::submitPassphrase)
                    OpenPhase.UNREADABLE -> OpenStatusLine(shown.status)
                    OpenPhase.READY -> ChoiceSummary(shown, viewModel, onChoose)
                }
            }
        }
    }
}

/** A line on its own card saying what is under way - looking for backups, reading one - turning as it goes. */
@Composable
private fun StatusRow(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LoadingIndicator(modifier = Modifier.size(24.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NoBackupsCard(restore: RestoreState.NoBackups, onChangeAccount: () -> Unit, onChooseFile: () -> Unit) {
    Column(
        modifier = Modifier
            .padding(top = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(SonaIcons.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
        Text(stringResource(R.string.onboarding_restore_no_backups), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        Text(
            if (restore.couldNotRead) {
                stringResource(R.string.onboarding_restore_drive_unreadable)
            } else {
                stringResource(R.string.onboarding_restore_no_backups_message, restore.account)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(modifier = Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onChangeAccount, shapes = buttonPressShapes()) { Text(stringResource(R.string.onboarding_restore_other_account)) }
            TextButton(onClick = onChooseFile, shapes = buttonPressShapes()) { Text(stringResource(R.string.onboarding_restore_choose_file)) }
        }
    }
}

/** How many of [all] are chosen, as a checkbox standing for them all shows it. */
private fun toggleStateOf(chosen: Int, all: Int): ToggleableState = when {
    chosen == 0 -> ToggleableState.Off
    chosen >= all -> ToggleableState.On
    else -> ToggleableState.Indeterminate
}

/** The size of [files] together, as a list of what comes back says it. */
@Composable
private fun sizeOf(files: List<Track>): String = Formatter.formatShortFileSize(LocalContext.current, files.sumOf { it.sizeBytes })

/** What of the library comes back, in a word or two: none, all of it, or how many of its parts. */
@Composable
private fun librarySummary(parts: Set<RestorePart>): String = when (parts.size) {
    0 -> stringResource(R.string.onboarding_restore_none)
    RestorePart.entries.size -> stringResource(R.string.onboarding_restore_library_all)
    else -> stringResource(R.string.onboarding_restore_some_of, parts.size, RestorePart.entries.size)
}

/** How many of the [offered] music files or videos come back, and their size: none, all of them, or some. */
@Composable
private fun filesSummary(chosen: List<Track>, offered: List<Track>, isVideo: Boolean): String = when (chosen.size) {
    0 -> stringResource(R.string.onboarding_restore_none)
    offered.size -> pluralStringResource(
        if (isVideo) R.plurals.onboarding_restore_videos_all else R.plurals.onboarding_restore_tracks_all,
        offered.size,
        offered.size,
        sizeOf(chosen),
    )
    else -> pluralStringResource(
        if (isVideo) R.plurals.onboarding_restore_videos_some else R.plurals.onboarding_restore_tracks_some,
        offered.size,
        chosen.size,
        offered.size,
        sizeOf(chosen),
    )
}

/**
 * Of a backup open, what comes back: the library, the music and the videos it can bring - each a row opening the sheet that
 * chooses exactly, with a checkbox beside it for the whole of it - and how much that takes to download, against the room
 * there is. A backup bringing no files - a file's, with Drive not connected - offers its library's parts as chips instead.
 */
@Composable
private fun ChoiceSummary(restore: RestoreState.Chosen, viewModel: OnboardingViewModel, onChoose: (ChoiceTab) -> Unit) {
    val preview = restore.preview ?: return
    val files = preview.files
    val offersFiles = files.music.isNotEmpty() || files.videos.isNotEmpty()
    if (restore.source !is BackupSource.Drive && !offersFiles) {
        Column {
            PartChips(restore, viewModel)
            Text(
                stringResource(R.string.onboarding_restore_file_no_media),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, top = 16.dp),
            )
        }
        return
    }
    Column {
        SonaRowGroup(
            rows = buildList {
                add(
                    categoryRow(
                        icon = SonaIcons.Tune,
                        title = R.string.onboarding_restore_library,
                        summary = { librarySummary(restore.parts) },
                        state = toggleStateOf(restore.parts.size, RestorePart.entries.size),
                        allLabel = R.string.onboarding_restore_all_library,
                        onOpen = { onChoose(ChoiceTab.LIBRARY) },
                        onToggle = { viewModel.setAllRestoreParts(restore.parts.size < RestorePart.entries.size) },
                    ),
                )
                if (files.music.isNotEmpty()) {
                    add(
                        categoryRow(
                            icon = SonaIcons.Song,
                            title = R.string.onboarding_restore_tracks,
                            summary = { filesSummary(restore.chosenMusic, files.music, isVideo = false) },
                            state = toggleStateOf(restore.chosenMusic.size, files.music.size),
                            allLabel = R.string.onboarding_restore_all_tracks,
                            onOpen = { onChoose(ChoiceTab.TRACKS) },
                            onToggle = { viewModel.setAllRestoreFiles(files.music, restore.chosenMusic.size < files.music.size) },
                        ),
                    )
                }
                if (files.videos.isNotEmpty()) {
                    add(
                        categoryRow(
                            icon = SonaIcons.Video,
                            title = R.string.onboarding_restore_videos,
                            summary = { filesSummary(restore.chosenVideos, files.videos, isVideo = true) },
                            state = toggleStateOf(restore.chosenVideos.size, files.videos.size),
                            allLabel = R.string.onboarding_restore_all_videos,
                            onOpen = { onChoose(ChoiceTab.VIDEOS) },
                            onToggle = { viewModel.setAllRestoreFiles(files.videos, restore.chosenVideos.size < files.videos.size) },
                        ),
                    )
                }
            },
        )
        if (offersFiles) StorageLine(restore.chosenMusic + restore.chosenVideos)
    }
}

/** How much the files chosen take to download, and how much room there is on this phone - its share of that room drawn under. */
@Composable
private fun StorageLine(chosen: List<Track>) {
    val context = LocalContext.current
    val total = chosen.sumOf { it.sizeBytes }
    val free = remember { freeBytes() }
    val share by animateFloatAsState(
        targetValue = if (free > 0) (total.toFloat() / free).coerceIn(0.01f, 1f) else 1f,
        animationSpec = tween(ArrivalMillis, easing = EmphasizedDecelerate),
        label = "storageShare",
    )
    val colors = MaterialTheme.colorScheme
    Column(modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                AnnotatedString.fromHtml(stringResource(R.string.onboarding_restore_to_download, Formatter.formatShortFileSize(context, total))),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.onboarding_restore_free, Formatter.formatShortFileSize(context, free)),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(
            progress = { share },
            modifier = Modifier.padding(top = 6.dp).fillMaxWidth(),
            color = if (total > free) colors.error else colors.primary,
            trackColor = colors.surfaceContainerHighest,
            drawStopIndicator = {},
        )
    }
}

/** The room left on this phone's own storage, where restored files go. */
private fun freeBytes(): Long = runCatching { StatFs(Environment.getExternalStorageDirectory().path).availableBytes }.getOrDefault(0L)

/** A backup's library parts as chips, each taken or left - their counts beside them - for a backup that brings no files. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PartChips(restore: RestoreState.Chosen, viewModel: OnboardingViewModel) {
    val summary = restore.preview?.summary ?: return
    FlowRow(modifier = Modifier.padding(start = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RestorePart.entries.forEach { part ->
            val selected = part in restore.parts
            val count = when (part) {
                RestorePart.SETTINGS -> null
                RestorePart.PLAYLISTS -> "%,d".format(summary.playlists)
                RestorePart.FAVORITES -> "%,d".format(summary.favorites)
                RestorePart.HISTORY -> pluralStringResource(R.plurals.onboarding_restore_plays, summary.plays, "%,d".format(summary.plays))
            }
            FilterChip(
                selected = selected,
                onClick = { viewModel.toggleRestorePart(part) },
                label = {
                    Text(stringResource(part.labelRes))
                    if (count != null) Text(" $count", modifier = Modifier.graphicsLayer { alpha = 0.8f })
                },
                leadingIcon = {
                    AnimatedVisibility(visible = selected, enter = expandHorizontally() + fadeIn(), exit = shrinkHorizontally() + fadeOut()) {
                        Icon(SonaIcons.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize))
                    }
                },
            )
        }
    }
}

private val RestorePart.labelRes: Int
    get() = when (this) {
        RestorePart.SETTINGS -> R.string.onboarding_restore_settings
        RestorePart.PLAYLISTS -> R.string.onboarding_restore_playlists
        RestorePart.FAVORITES -> R.string.onboarding_restore_favorites
        RestorePart.HISTORY -> R.string.onboarding_restore_history
    }

private val RestorePart.icon: ImageVector
    get() = when (this) {
        RestorePart.SETTINGS -> SonaIcons.Tune
        RestorePart.PLAYLISTS -> SonaIcons.QueueMusic
        RestorePart.FAVORITES -> SonaIcons.Favorite
        RestorePart.HISTORY -> SonaIcons.History
    }

/**
 * A part of what comes back, two things side by side: the row itself - pressed, it opens the sheet that chooses exactly - and,
 * past a hairline, its checkbox, a button of its own taking or leaving the whole of it.
 */
private fun categoryRow(
    icon: ImageVector,
    @StringRes title: Int,
    summary: @Composable () -> String,
    state: ToggleableState,
    @StringRes allLabel: Int,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
): SonaGroupRow = { shapes ->
    val colors = MaterialTheme.colorScheme
    val label = stringResource(allLabel)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(shapes.shape)
            .background(colors.surfaceContainer)
            .padding(end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 64.dp)
                .clickable(onClick = onOpen)
                .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(icon, contentDescription = null, tint = colors.onSurfaceVariant)
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
                Text(summary(), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            Icon(SonaIcons.KeyboardArrowRight, contentDescription = null, tint = colors.onSurfaceVariant)
        }
        VerticalDivider(modifier = Modifier.height(32.dp), color = colors.outlineVariant)
        ChoiceCheckbox(state = state, label = label, onToggle = onToggle)
    }
}

/** A checkbox that is a button of its own - its shape turning as it is pressed, as the app's icon buttons do. */
@Composable
private fun ChoiceCheckbox(state: ToggleableState, label: String, onToggle: () -> Unit) {
    IconButton(
        onClick = onToggle,
        shapes = iconButtonPressShapes(),
        modifier = Modifier.size(48.dp).semantics {
            role = Role.Checkbox
            toggleableState = state
            contentDescription = label
        },
    ) { TriStateCheckbox(state = state, onClick = null) }
}

/**
 * Choosing exactly what comes back: the library's parts, the music, the videos - a tab each, where there are files to bring -
 * every one a row to take or leave under Select all, and at its foot how much is to download, then Done.
 */
@Composable
private fun RestoreChoiceSheet(restore: RestoreState.Chosen, initialTab: ChoiceTab, viewModel: OnboardingViewModel, onDismiss: () -> Unit) {
    val files = restore.preview?.files ?: return
    val tabs = listOfNotNull(ChoiceTab.LIBRARY, ChoiceTab.TRACKS.takeIf { files.music.isNotEmpty() }, ChoiceTab.VIDEOS.takeIf { files.videos.isNotEmpty() })
    var tab by rememberSaveable { mutableStateOf(initialTab.takeIf { it in tabs } ?: ChoiceTab.LIBRARY) }
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    SonaColumnBottomSheet(title = stringResource(R.string.onboarding_restore_what_comes_back), onDismissRequest = onDismiss) { scope ->
        if (tabs.size > 1) {
            SonaConnectedChoice(
                options = tabs,
                selected = tab,
                onSelect = { tab = it },
                label = {
                    stringResource(
                        when (it) {
                            ChoiceTab.LIBRARY -> R.string.onboarding_restore_library
                            ChoiceTab.TRACKS -> R.string.onboarding_restore_tracks
                            ChoiceTab.VIDEOS -> R.string.onboarding_restore_videos
                        },
                    )
                },
                isCompact = true,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
            )
        }
        AnimatedContent(
            targetState = tab,
            transitionSpec = { fadeThrough() },
            contentAlignment = Alignment.TopStart,
            modifier = Modifier.weight(1f, fill = false).padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
            label = "choiceTab",
        ) { shown ->
            when (shown) {
                ChoiceTab.LIBRARY -> LibraryChoice(restore, viewModel)
                ChoiceTab.TRACKS -> FileChoice(files.music, restore.chosenMusic, here = files.musicHere, isVideo = false, viewModel = viewModel)
                ChoiceTab.VIDEOS -> FileChoice(files.videos, restore.chosenVideos, here = files.videosHere, isVideo = true, viewModel = viewModel)
            }
        }
        HorizontalDivider(color = colors.outlineVariant)
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp)) {
            val total = (restore.chosenMusic + restore.chosenVideos).sumOf { it.sizeBytes }
            Text(
                AnnotatedString.fromHtml(
                    stringResource(
                        R.string.onboarding_restore_to_download_free,
                        Formatter.formatShortFileSize(context, total),
                        Formatter.formatShortFileSize(context, remember { freeBytes() }),
                    ),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 10.dp),
            )
            Button(onClick = { scope.dismiss() }, shapes = buttonPressShapes(), modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(stringResource(R.string.onboarding_restore_done), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** Select all, over what it stands for, on its own pill: how many are chosen, and a checkbox for every one at once. */
@Composable
private fun SelectAllRow(summary: String, state: ToggleableState, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .padding(bottom = 8.dp)
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(colors.secondaryContainer)
            .triStateToggleable(state = state, role = Role.Checkbox, onClick = onToggle)
            .padding(start = 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.onboarding_restore_select_all),
            style = MaterialTheme.typography.titleSmall,
            color = colors.onSecondaryContainer,
            modifier = Modifier.weight(1f),
        )
        Text(summary, style = MaterialTheme.typography.bodySmall, color = colors.onSecondaryContainer.copy(alpha = 0.85f))
        TriStateCheckbox(
            state = state,
            onClick = null,
            colors = CheckboxDefaults.colors(uncheckedColor = colors.onSecondaryContainer),
        )
    }
}

@Composable
private fun LibraryChoice(restore: RestoreState.Chosen, viewModel: OnboardingViewModel) {
    val summary = restore.preview?.summary ?: return
    Column {
        SelectAllRow(
            summary = stringResource(R.string.onboarding_restore_some_of, restore.parts.size, RestorePart.entries.size),
            state = toggleStateOf(restore.parts.size, RestorePart.entries.size),
            onToggle = { viewModel.setAllRestoreParts(restore.parts.size < RestorePart.entries.size) },
        )
        listOf(RestorePart.PLAYLISTS, RestorePart.FAVORITES, RestorePart.HISTORY, RestorePart.SETTINGS).forEach { part ->
            val count = when (part) {
                RestorePart.PLAYLISTS -> "%,d".format(summary.playlists)
                RestorePart.FAVORITES -> "%,d".format(summary.favorites)
                RestorePart.HISTORY -> pluralStringResource(R.plurals.onboarding_restore_plays, summary.plays, "%,d".format(summary.plays))
                RestorePart.SETTINGS -> null
            }
            ChoiceRow(
                title = stringResource(part.labelRes),
                subtitle = count,
                isChosen = part in restore.parts,
                onToggle = { viewModel.toggleRestorePart(part) },
            ) { ChoiceTile(part.icon, width = 36.dp, height = 36.dp, corner = 10.dp) }
        }
    }
}

/** The [offered] music or videos, by title, each taken or left - and how many more of the backup's are here already. */
@Composable
private fun FileChoice(offered: List<Track>, chosen: List<Track>, here: Int, isVideo: Boolean, viewModel: OnboardingViewModel) {
    val context = LocalContext.current
    val chosenPaths = remember(chosen) { chosen.mapTo(HashSet()) { it.path } }
    val sorted = remember(offered) { offered.sortedBy { it.title.lowercase() } }
    val separator = stringResource(R.string.onboarding_detail_separator)
    Column {
        SelectAllRow(
            summary = stringResource(R.string.onboarding_restore_some_of_size, chosen.size, offered.size, sizeOf(chosen)),
            state = toggleStateOf(chosen.size, offered.size),
            onToggle = { viewModel.setAllRestoreFiles(offered, chosen.size < offered.size) },
        )
        LazyColumn {
            items(sorted, key = { it.path }) { file ->
                ChoiceRow(
                    title = file.title,
                    subtitle = listOfNotNull(
                        file.durationMs.takeIf { it > 0 }?.let(::formatDuration),
                        Formatter.formatShortFileSize(context, file.sizeBytes),
                    ).joinToString(separator),
                    isChosen = file.path in chosenPaths,
                    onToggle = { viewModel.toggleRestoreFile(file.path) },
                ) {
                    if (isVideo) {
                        ChoiceTile(SonaIcons.Video, width = 64.dp, height = 36.dp, corner = 8.dp)
                    } else {
                        ChoiceTile(SonaIcons.Song, width = 40.dp, height = 40.dp, corner = 10.dp)
                    }
                }
            }
            if (here > 0) {
                item(key = "here") {
                    Text(
                        pluralStringResource(R.plurals.onboarding_restore_here, here, here),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 12.dp),
                    )
                }
            }
        }
    }
}

/** What a row of the choice sheet stands for, on a tile of its own. */
@Composable
private fun ChoiceTile(icon: ImageVector, width: Dp, height: Dp, corner: Dp) {
    Box(
        modifier = Modifier.size(width, height).clip(RoundedCornerShape(corner)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) }
}

/** One thing to take or leave in the choice sheet, the whole row its checkbox. */
@Composable
private fun ChoiceRow(title: String, subtitle: String?, isChosen: Boolean, onToggle: () -> Unit, tile: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .toggleable(value = isChosen, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        tile()
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Checkbox(checked = isChosen, onCheckedChange = null)
    }
}

/**
 * One of the backups in Drive: the device it was made on, the newest marked so, then when and how large - picked by its radio
 * button. One from before devices were kept says when it was made in the device's place.
 */
private fun driveBackupRow(backup: StoredBackup, isNewest: Boolean, isPicked: Boolean, onPick: (StoredBackup) -> Unit): SonaGroupRow = { shapes ->
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val made = remember(backup.createdAt) { madeAt(context, backup.createdAt) }
    val size = Formatter.formatShortFileSize(context, backup.sizeBytes)
    val device = backup.device
    SegmentedListItem(
        onClick = { onPick(backup) },
        shapes = shapes,
        colors = if (isPicked) {
            ListItemDefaults.segmentedColors(containerColor = colors.secondaryContainer, supportingContentColor = colors.onSecondaryContainer)
        } else {
            rowColors()
        },
        leadingContent = { Icon(if (device != null) SonaIcons.Smartphone else SonaIcons.Backup, contentDescription = null) },
        trailingContent = { RadioButton(selected = isPicked, onClick = null) },
        supportingContent = { Text(if (device != null) detailsOf(made, size) else size) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(device?.label ?: made, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (isNewest) NewestTag()
        }
    }
}

/** When a backup was made, as a list of them says it: "28 Oct, 09:12". */
private fun madeAt(context: Context, createdAt: Long): String =
    DateUtils.formatDateTime(context, createdAt, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH)

/** A backup's details on one line, apart by a middle dot. */
@Composable
private fun detailsOf(vararg details: String): String = details.joinToString(stringResource(R.string.onboarding_detail_separator))

@Composable
private fun NewestTag() {
    Text(
        stringResource(R.string.onboarding_restore_newest),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier
            .padding(start = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** The backup file chosen - and, once open, the device it was made on and when - and Change, the file picker again. */
@Composable
private fun BackupFileChip(name: String, preview: RestorePreview?, onChange: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(colors.surfaceContainer)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(colors.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(SonaIcons.Description, contentDescription = null, tint = colors.onSecondaryContainer, modifier = Modifier.size(20.dp)) }
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (preview != null) {
                val made = madeAt(context, preview.createdAt)
                Text(
                    preview.device?.let { detailsOf(it.label, made) } ?: made,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        TextButton(onClick = onChange, shapes = buttonPressShapes(ContainerEdge.End)) { Text(stringResource(R.string.onboarding_restore_change)) }
    }
}

/** How opening the backup chosen went, where there is anything to say: still reading it, or why it cannot be restored. */
@Composable
private fun OpenStatusLine(status: RestoreStatus) {
    val text = when (status) {
        RestoreStatus.OPENING -> stringResource(R.string.onboarding_restore_opening)
        RestoreStatus.UNREADABLE -> stringResource(R.string.onboarding_restore_unreadable)
        RestoreStatus.UNSUPPORTED -> stringResource(R.string.onboarding_restore_unsupported)
        else -> return
    }
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (status == RestoreStatus.OPENING) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(start = 8.dp, top = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun PassphraseField(isWrong: Boolean, onSubmit: (String) -> Unit) {
    var passphrase by rememberSaveable { mutableStateOf("") }
    Row(modifier = Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = passphrase,
            onValueChange = { passphrase = it },
            label = { Text(stringResource(R.string.onboarding_restore_passphrase)) },
            isError = isWrong,
            supportingText = if (isWrong) {
                { Text(stringResource(R.string.onboarding_restore_wrong_passphrase)) }
            } else {
                null
            },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit(passphrase) }),
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { onSubmit(passphrase) }, enabled = passphrase.isNotEmpty(), shapes = buttonPressShapes()) {
            Text(stringResource(R.string.onboarding_restore_open))
        }
    }
}

/** The restore under way: the step it is on, one bar for the whole, and each step - done, under way, or still to come. */
private fun restoringStage(restore: RestoreState.Chosen, arrivals: StepArrivals): LazyListScope.() -> Unit = {
    val current = restore.progress?.step?.let(RestoreSteps::indexOf)?.coerceAtLeast(0) ?: 0
    val isFailed = restore.status == RestoreStatus.FAILED
    piece("progress", arrivals, 2) {
        val progressLabel = stringResource(R.string.onboarding_restore_progress)
        val whole by animateFloatAsState(
            targetValue = (current + (restore.progress?.fraction ?: 0f)) / RestoreSteps.size,
            animationSpec = tween(400, easing = EmphasizedDecelerate),
            label = "restoreProgress",
        )
        Column {
            Text(
                when {
                    !isFailed -> stringResource(RestoreSteps[current].labelRes)
                    restore.failure == BackupFailure.DRIVE_ACCESS_LOST -> stringResource(R.string.onboarding_restore_drive_access_lost)
                    else -> stringResource(R.string.onboarding_restore_failed)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (isFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            LinearProgressIndicator(
                progress = { whole },
                modifier = Modifier
                    .padding(top = 4.dp, bottom = 18.dp)
                    .fillMaxWidth()
                    .semantics { contentDescription = progressLabel },
                trackColor = MaterialTheme.colorScheme.secondaryContainer,
                drawStopIndicator = {},
            )
        }
    }
    piece("steps", arrivals, 3) {
        Column {
            RestoreSteps.forEachIndexed { index, step ->
                RestoreStepRow(stringResource(step.labelRes), isDone = index < current, isCurrent = index == current && !isFailed)
            }
        }
    }
}

@Composable
private fun RestoreStepRow(label: String, isDone: Boolean, isCurrent: Boolean) {
    val colors = MaterialTheme.colorScheme
    Row(modifier = Modifier.height(44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            when {
                isCurrent -> LoadingIndicator(modifier = Modifier.size(24.dp))
                isDone -> Icon(SonaIcons.CheckCircle, contentDescription = null, tint = colors.primary, modifier = Modifier.size(22.dp))
                else -> Box(modifier = Modifier.size(20.dp).border(2.dp, colors.outlineVariant, CircleShape))
            }
        }
        Text(label, style = MaterialTheme.typography.bodyLarge, color = if (isDone || isCurrent) colors.onSurface else colors.onSurfaceVariant)
    }
}

/**
 * Welcome back: what came back, counted - then, where songs and videos were chosen, those still coming in, the library
 * usable meanwhile - and, restored from Drive, backing up there from now on; from a file, connecting Drive to.
 */
private fun restoredStage(
    restore: RestoreState.Restored,
    viewModel: OnboardingViewModel,
    arrivals: StepArrivals,
    onConnect: () -> Unit,
): LazyListScope.() -> Unit = {
    piece("cameBack", arrivals, 2) { CameBackCard(restore.cameBack) }
    restore.media?.let { media ->
        piece("media", arrivals, 3) { MediaDownloadCard(media, onRetry = viewModel::retryMedia) }
    }
    piece("backingUp", arrivals, 4) {
        val keepsBackingUp by viewModel.keepsBackingUp.collectAsStateWithLifecycle()
        val isDriveConnected by viewModel.isDriveConnected.collectAsStateWithLifecycle()
        SonaRowGroup(
            modifier = Modifier.padding(top = 8.dp),
            rows = listOf(
                if (restore.isFromDrive || isDriveConnected) {
                    switchRow(
                        title = stringResource(R.string.onboarding_restored_keep_backing_up),
                        summary = stringResource(R.string.onboarding_restored_keep_backing_up_summary),
                        icon = SonaIcons.CloudSync,
                        checked = keepsBackingUp,
                        onCheckedChange = viewModel::setKeepsBackingUp,
                    )
                } else {
                    { shapes ->
                        SegmentedListItem(
                            onClick = onConnect,
                            shapes = shapes,
                            colors = rowColors(),
                            leadingContent = { Icon(SonaIcons.CloudUpload, contentDescription = null) },
                            trailingContent = { Pill(stringResource(R.string.onboarding_restored_connect), onConnect) },
                            supportingContent = { Text(stringResource(R.string.onboarding_restored_back_up_to_drive_summary)) },
                        ) { Text(stringResource(R.string.onboarding_restored_back_up_to_drive)) }
                    }
                },
            ),
        )
    }
}

/**
 * What came back, a line for each part restored - counted where the backup said how much: "6 playlists and 48 favorites",
 * "1,204 plays of history". A restore carried on from before, whose backup is not known here any more, says it in words.
 */
@Composable
private fun CameBackCard(cameBack: CameBack?) {
    val lines = buildList {
        if (cameBack == null) {
            add(SonaIcons.QueueMusic to AnnotatedString(stringResource(R.string.onboarding_restored_lists)))
            add(SonaIcons.Tune to AnnotatedString(stringResource(R.string.onboarding_restored_settings)))
            add(SonaIcons.History to AnnotatedString(stringResource(R.string.onboarding_restored_history)))
            return@buildList
        }
        val (parts, summary) = cameBack
        val playlists = pluralStringResource(R.plurals.onboarding_restored_playlists, summary.playlists, summary.playlists)
        val favorites = pluralStringResource(R.plurals.onboarding_restored_favorites, summary.favorites, summary.favorites)
        val lists = when {
            RestorePart.PLAYLISTS in parts && RestorePart.FAVORITES in parts ->
                AnnotatedString.fromHtml(stringResource(R.string.onboarding_restored_lists_and, playlists, favorites))
            RestorePart.PLAYLISTS in parts -> bold(playlists)
            RestorePart.FAVORITES in parts -> bold(favorites)
            else -> null
        }
        lists?.let { add(SonaIcons.QueueMusic to it) }
        if (RestorePart.SETTINGS in parts) add(SonaIcons.Tune to AnnotatedString(stringResource(R.string.onboarding_restored_settings)))
        if (RestorePart.HISTORY in parts) {
            add(SonaIcons.History to AnnotatedString(pluralStringResource(R.plurals.onboarding_restored_plays, summary.plays, "%,d".format(summary.plays))))
        }
    }
    if (lines.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        lines.forEach { (icon, text) ->
            Row(modifier = Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun bold(text: String): AnnotatedString = buildAnnotatedString { withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text) } }

/**
 * The songs and videos still coming in after the library: how many, one bar for them all, and that Sona can be used
 * meanwhile - then that they are all in, or that some did not come, with Try again for the rest.
 */
@Composable
private fun MediaDownloadCard(media: MediaDownload, onRetry: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val progressLabel = stringResource(R.string.onboarding_restored_media_progress)
    val share by animateFloatAsState(
        targetValue = if (media.total > 0) media.done.toFloat() / media.total else 0f,
        animationSpec = tween(ArrivalMillis, easing = EmphasizedDecelerate),
        label = "mediaDownload",
    )
    Column(
        modifier = Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceContainer)
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp)
            .animateContentSize(tween(ArrivalMillis, easing = EmphasizedDecelerate)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            val icon = when (media.state) {
                MediaDownload.State.DOWNLOADING -> SonaIcons.CloudDownload
                MediaDownload.State.DONE -> SonaIcons.CheckCircle
                MediaDownload.State.FAILED -> SonaIcons.CloudOff
            }
            Crossfade(targetState = icon, label = "mediaIcon") {
                Icon(it, contentDescription = null, tint = if (media.state == MediaDownload.State.FAILED) colors.error else colors.primary)
            }
            Column(modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }) {
                Text(
                    stringResource(
                        when (media.state) {
                            MediaDownload.State.DOWNLOADING -> R.string.onboarding_restored_media_downloading
                            MediaDownload.State.DONE -> R.string.onboarding_restored_media_done
                            MediaDownload.State.FAILED -> R.string.onboarding_restored_media_failed
                        },
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (media.total > 0) {
                    Text(
                        pluralStringResource(R.plurals.onboarding_restored_media_count, media.total, media.done, media.total),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            if (media.state == MediaDownload.State.FAILED) Pill(stringResource(R.string.onboarding_try_again), onRetry)
        }
        if (media.state == MediaDownload.State.DOWNLOADING) {
            LinearProgressIndicator(
                progress = { share },
                modifier = Modifier.padding(top = 12.dp, bottom = 10.dp).fillMaxWidth().semantics { contentDescription = progressLabel },
                trackColor = colors.secondaryContainer,
                drawStopIndicator = {},
            )
            Text(
                stringResource(R.string.onboarding_restored_media_use_now),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

// Access.

private fun accessStep(arrivals: StepArrivals): LazyListScope.() -> Unit = {
    piece("permissions", arrivals, 2) {
        SonaRowGroup(rows = AppPermission.needed.map { permission -> permissionRow(permission) })
    }
}

/** How the check of a permission just granted grows in: a little past whole, and back. */
private val GrantedCheckEasing = CubicBezierEasing(0.34f, 1.4f, 0.64f, 1f)

/** A permission's row: why it is wanted, and Allow - asking right there - or a check once it is granted. */
private fun permissionRow(permission: AppPermission): SonaGroupRow = { shapes ->
    val context = LocalContext.current
    var isGranted by remember { mutableStateOf(permission.isGranted(context)) }
    // Read again on coming back, as special access is granted on a page of Android's own.
    LifecycleResumeEffect(permission) {
        isGranted = permission.isGranted(context)
        onPauseOrDispose {}
    }
    val request = rememberPermissionRequest(permission) { isGranted = it }
    val (title, summary, icon) = when (permission) {
        AppPermission.STORAGE -> Triple(R.string.onboarding_access_media, R.string.onboarding_access_media_summary, SonaIcons.LibraryMusic)
        AppPermission.NOTIFICATIONS ->
            Triple(R.string.onboarding_access_notifications, R.string.onboarding_access_notifications_summary, SonaIcons.Notifications)
        AppPermission.APP_INSTALLS -> Triple(R.string.onboarding_access_installs, R.string.onboarding_access_installs_summary, SonaIcons.InstallMobile)
    }
    val allowedLabel = stringResource(R.string.onboarding_allowed)
    SegmentedListItem(
        onClick = { if (!isGranted) request() },
        shapes = shapes,
        colors = rowColors(),
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = {
            // Allow goes and the check comes in one move, the slot narrowing from its end toward the check meanwhile -
            // its height the button's either way, so the row never jumps.
            AnimatedContent(
                targetState = isGranted,
                transitionSpec = {
                    (fadeIn(tween(FadeInMillis, delayMillis = FadeOutMillis)) +
                        scaleIn(tween(ArrivalMillis, delayMillis = FadeOutMillis, easing = GrantedCheckEasing), initialScale = 0.4f)) togetherWith
                        (fadeOut(tween(FadeOutMillis)) + scaleOut(tween(FadeOutMillis), targetScale = 0.9f)) using
                        SizeTransform(clip = false) { _, _ -> tween<IntSize>(ArrivalMillis, easing = EmphasizedDecelerate) }
                },
                contentAlignment = Alignment.CenterEnd,
                modifier = Modifier.heightIn(min = 36.dp),
                label = "permissionGranted",
            ) { granted ->
                if (granted) {
                    Icon(
                        SonaIcons.CheckCircle,
                        contentDescription = allowedLabel,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Pill(stringResource(R.string.onboarding_allow), request)
                }
            }
        },
        supportingContent = { Text(stringResource(summary)) },
    ) { Text(stringResource(title)) }
}

// Media folders.

/**
 * The folders Sona finds songs and videos in, a tab for each: every folder a switch, counted in what the tab shows - and in
 * the other kind too, where it holds both, as a folder counts or not as a whole.
 */
private fun foldersStep(viewModel: OnboardingViewModel, arrivals: StepArrivals): LazyListScope.() -> Unit = {
    piece("folders", arrivals, 2) {
        val folders by viewModel.folders.collectAsStateWithLifecycle()
        val songCount by viewModel.songCount.collectAsStateWithLifecycle()
        val videoCount by viewModel.videoCount.collectAsStateWithLifecycle()
        val isFindingMedia by viewModel.isFindingMedia.collectAsStateWithLifecycle()
        var showsVideos by rememberSaveable { mutableStateOf(false) }
        // No folder yet is not yet none, while the library is still read or scanned: it says so, as reading a backup does.
        val phase = when {
            folders.isNotEmpty() -> FoldersPhase.FOLDERS
            isFindingMedia -> FoldersPhase.FINDING
            else -> FoldersPhase.EMPTY
        }
        AnimatedContent(targetState = phase, transitionSpec = { fadeThrough() }, contentAlignment = Alignment.TopStart, label = "foldersPhase") { shownPhase ->
            when (shownPhase) {
                FoldersPhase.FINDING -> StatusRow(stringResource(R.string.onboarding_folders_finding))
                FoldersPhase.EMPTY -> Text(
                    stringResource(R.string.onboarding_folders_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                FoldersPhase.FOLDERS -> FolderTabs(folders, showsVideos, onShowVideos = { showsVideos = it }, songCount, videoCount, viewModel)
            }
        }
    }
}

/** The folders, a tab for songs and one for videos - see [foldersStep]. */
@Composable
private fun FolderTabs(
    folders: List<MediaFolder>,
    showsVideos: Boolean,
    onShowVideos: (Boolean) -> Unit,
    songCount: Int,
    videoCount: Int,
    viewModel: OnboardingViewModel,
) {
    Column {
        SonaConnectedChoice(
            options = listOf(false, true),
            selected = showsVideos,
            onSelect = onShowVideos,
            label = { stringResource(if (it) R.string.onboarding_videos else R.string.onboarding_music) },
            icon = { if (it) SonaIcons.Video else SonaIcons.Song },
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        )
        AnimatedContent(targetState = showsVideos, transitionSpec = { fadeThrough() }, contentAlignment = Alignment.TopStart, label = "folderKind") { videosShown ->
            val shown = folders.filter { if (videosShown) it.videoCount > 0 else it.songCount > 0 }
            Column {
                if (shown.isEmpty()) {
                    Text(
                        stringResource(if (videosShown) R.string.onboarding_folders_no_videos else R.string.onboarding_folders_no_songs),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                } else {
                    SonaRowGroup(
                        rows = shown.map { folder ->
                            switchRow(
                                title = folder.name,
                                summary = folderSummary(folder, videosFirst = videosShown),
                                icon = SonaIcons.Folder,
                                checked = folder.isIncluded,
                                onCheckedChange = { viewModel.setFolderIncluded(folder.path, it) },
                            )
                        },
                    )
                }
                val total = if (videosShown) videoCount else songCount
                Text(
                    pluralStringResource(if (videosShown) R.plurals.onboarding_library_videos else R.plurals.onboarding_library_songs, total, total),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, top = 12.dp),
                )
            }
        }
    }
}

/** Where the folders step stands: the library still being found, nothing found, or its folders. */
private enum class FoldersPhase { FINDING, EMPTY, FOLDERS }

/** What a folder holds: the tab's kind first - "12 songs" - then the other, where it has any: "12 songs · 3 videos". */
@Composable
private fun folderSummary(folder: MediaFolder, videosFirst: Boolean): String {
    val songs = folder.songCount.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.onboarding_songs, it, it) }
    val videos = folder.videoCount.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.onboarding_videos_count, it, it) }
    val ordered = if (videosFirst) listOf(videos, songs) else listOf(songs, videos)
    return ordered.filterNotNull().joinToString(stringResource(R.string.onboarding_detail_separator))
}

// Library.

private fun libraryStep(viewModel: OnboardingViewModel, arrivals: StepArrivals, tabEditor: LibraryTabEditor): LazyListScope.() -> Unit = {
    // The rows arrive one after another as every other piece of a step does.
    libraryTabRows(
        tabEditor,
        horizontalPadding = ScreenPadding,
        colors = { rowColors() },
        arrival = { index -> with(arrivals) { Modifier.arrive(FirstPieceOrder + index) } },
    )
    // After the tab rows, which come first.
    piece("shuffle", arrivals, LastStaggered) {
        val showsShuffle by viewModel.showsShuffleButton.collectAsStateWithLifecycle()
        SonaRowGroup(
            modifier = Modifier.padding(top = 16.dp),
            rows = listOf(
                switchRow(
                    title = stringResource(R.string.onboarding_shuffle_button),
                    summary = stringResource(R.string.onboarding_shuffle_button_summary),
                    icon = SonaIcons.Shuffle,
                    checked = showsShuffle,
                    onCheckedChange = viewModel::setShowsShuffleButton,
                ),
            ),
        )
    }
}

// Downloads.

private fun downloadsStep(viewModel: OnboardingViewModel, arrivals: StepArrivals): LazyListScope.() -> Unit = {
    piece("downloads", arrivals, 2) {
        val downloads by viewModel.downloads.collectAsStateWithLifecycle()
        val pickFolder = rememberDownloadFolderPicker(onPick = viewModel::setDownloadFolder)
        // Everything under the switch only means something while it is on: faded and inert while off, never taken away.
        val optionsAlpha by animateFloatAsState(if (downloads.isOn) 1f else 0.38f, tween(200), label = "downloadOptions")
        Column {
            SonaRowGroup(
                rows = listOf(
                    switchRow(
                        title = stringResource(R.string.onboarding_downloads_use),
                        summary = stringResource(R.string.onboarding_downloads_use_summary),
                        icon = SonaIcons.Download,
                        checked = downloads.isOn,
                        onCheckedChange = viewModel::setDownloadsOn,
                    ),
                ),
            )
            Column(modifier = Modifier.graphicsLayer { alpha = optionsAlpha }) {
                val downloader by viewModel.downloader.collectAsStateWithLifecycle()
                SonaRowGroup(
                    modifier = Modifier.padding(top = 8.dp),
                    rows = listOf(downloaderRow(downloader, enabled = downloads.isOn, viewModel)),
                )
                GroupLabel(stringResource(R.string.onboarding_downloads_save_to))
                SonaRowGroup(
                    rows = listOf(
                        folderRow(
                            title = DEFAULT_DOWNLOAD_FOLDER,
                            summary = stringResource(R.string.onboarding_downloads_default_folder_summary),
                            selected = downloads.savesToDefaultFolder,
                            enabled = downloads.isOn,
                            onClick = { viewModel.setDownloadFolder(DEFAULT_DOWNLOAD_FOLDER) },
                        ),
                        folderRow(
                            title = stringResource(R.string.onboarding_downloads_other_folder),
                            summary = if (downloads.savesToDefaultFolder) {
                                stringResource(R.string.onboarding_downloads_other_folder_summary)
                            } else {
                                downloads.folder
                            },
                            selected = !downloads.savesToDefaultFolder,
                            enabled = downloads.isOn,
                            onClick = pickFolder,
                        ),
                    ),
                )
            }
        }
    }
}

/**
 * The downloader - what downloading takes beyond the app: fetched from here, once, carrying on on its own - its
 * notification showing it - while the setup moves on. One button does it all: download, then a ring filling round stop as
 * it comes, then a check.
 */
private fun downloaderRow(state: RuntimeState, enabled: Boolean, viewModel: OnboardingViewModel): SonaGroupRow = { shapes ->
    val context = LocalContext.current
    val summary = when (state) {
        RuntimeState.Missing -> stringResource(R.string.onboarding_downloader_missing)
        is RuntimeState.Downloading -> if (state.totalBytes > 0) {
            stringResource(
                R.string.onboarding_downloader_downloading,
                Formatter.formatShortFileSize(context, state.receivedBytes),
                Formatter.formatShortFileSize(context, state.totalBytes),
            )
        } else {
            stringResource(R.string.onboarding_downloader_starting)
        }
        RuntimeState.Installed -> stringResource(R.string.onboarding_downloader_installed)
        RuntimeState.Failed -> stringResource(R.string.onboarding_downloader_failed)
    }
    val act = {
        when (state) {
            RuntimeState.Missing, RuntimeState.Failed -> viewModel.downloadDownloader()
            is RuntimeState.Downloading -> viewModel.stopDownloadingDownloader()
            RuntimeState.Installed -> Unit
        }
    }
    SegmentedListItem(
        onClick = act,
        shapes = shapes,
        colors = rowColors(),
        enabled = enabled,
        leadingContent = { Icon(SonaIcons.Extension, contentDescription = null) },
        trailingContent = { DownloaderButton(state, enabled, onClick = act) },
        supportingContent = { Text(summary, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) },
    ) { Text(stringResource(R.string.onboarding_downloader)) }
}

/** The downloader's one button: its icon cross-fading between download, stop and done, a ring round it filling while it downloads. */
@Composable
private fun DownloaderButton(state: RuntimeState, enabled: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val isDownloading = state is RuntimeState.Downloading
    val ringAlpha by animateFloatAsState(if (isDownloading) 1f else 0f, tween(FadeInMillis), label = "downloaderRing")
    val progress by animateFloatAsState(
        targetValue = (state as? RuntimeState.Downloading)?.progress ?: if (state == RuntimeState.Installed) 1f else 0f,
        animationSpec = tween(400, easing = EmphasizedDecelerate),
        label = "downloaderProgress",
    )
    val label = stringResource(
        when (state) {
            RuntimeState.Missing -> R.string.onboarding_downloader_get
            RuntimeState.Failed -> R.string.onboarding_downloader_retry
            is RuntimeState.Downloading -> R.string.onboarding_downloader_cancel
            RuntimeState.Installed -> R.string.onboarding_downloader_installed
        },
    )
    Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.size(44.dp).graphicsLayer { alpha = ringAlpha },
            strokeWidth = 3.dp,
            trackColor = colors.secondaryContainer,
            gapSize = 0.dp,
        )
        FilledTonalIconButton(
            onClick = onClick,
            enabled = enabled && state != RuntimeState.Installed,
            shapes = iconButtonPressShapes(),
            modifier = Modifier.size(36.dp).semantics { contentDescription = label },
        ) {
            Crossfade(
                targetState = when (state) {
                    RuntimeState.Missing -> SonaIcons.Download
                    RuntimeState.Failed -> SonaIcons.Refresh
                    is RuntimeState.Downloading -> SonaIcons.Close
                    RuntimeState.Installed -> SonaIcons.Check
                },
                animationSpec = tween(FadeInMillis),
                label = "downloaderIcon",
            ) { Icon(it, contentDescription = null, modifier = Modifier.size(20.dp)) }
        }
    }
}

/** One of the folders downloads can be saved to, picked by its radio button. */
private fun folderRow(title: String, summary: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit): SonaGroupRow = { shapes ->
    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        colors = rowColors(),
        enabled = enabled,
        trailingContent = { RadioButton(selected = selected, onClick = null, enabled = enabled) },
        supportingContent = { Text(summary) },
    ) { Text(title) }
}

// Look.

private fun lookStep(viewModel: OnboardingViewModel, arrivals: StepArrivals): LazyListScope.() -> Unit = {
    piece("theme", arrivals, 2) {
        val theme by viewModel.theme.collectAsStateWithLifecycle()
        Column {
            GroupLabel(stringResource(R.string.onboarding_theme))
            SonaConnectedChoice(
                options = ThemeMode.entries,
                selected = theme.mode,
                onSelect = viewModel::setThemeMode,
                label = { stringResource(it.labelRes) },
                icon = { it.icon },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    piece("colors", arrivals, 3) {
        val theme by viewModel.theme.collectAsStateWithLifecycle()
        val usesWallpaperColors = HasWallpaperColors && (theme.dynamicColors || theme.wallpaperColors)
        val usesCoverColors = theme.dynamicColors || theme.coverColors
        Column {
            // Only where the device has wallpaper colours to give is there a choice to make.
            if (HasWallpaperColors) {
                GroupLabel(stringResource(R.string.onboarding_colors))
                SonaConnectedChoice(
                    options = listOf(true, false),
                    selected = usesWallpaperColors,
                    onSelect = viewModel::setWallpaperColors,
                    label = { stringResource(if (it) R.string.onboarding_colors_wallpaper else R.string.onboarding_colors_preset) },
                    icon = { if (it) SonaIcons.Wallpaper else SonaIcons.Palette },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (HasWallpaperColors) {
                // What is under the choice changes in one move: the one there fades, the room settles to the other's size,
                // and the other fades in - never two things animating against each other.
                AnimatedContent(
                    targetState = usesWallpaperColors,
                    transitionSpec = {
                        fadeIn(tween(FadeInMillis, delayMillis = FadeOutMillis)) togetherWith fadeOut(tween(FadeOutMillis)) using
                            SizeTransform(clip = false) { _, _ -> tween<IntSize>(ArrivalMillis, easing = EmphasizedDecelerate) }
                    },
                    contentAlignment = Alignment.TopStart,
                    label = "colorsChoice",
                ) { isWallpaper ->
                    if (isWallpaper) {
                        Text(
                            stringResource(R.string.onboarding_colors_wallpaper_summary),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 8.dp),
                        )
                    } else {
                        AccentSwatches(
                            selectedId = theme.colorPalette ?: AccentPaletteIds.first(),
                            onSelect = viewModel::setAccent,
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        )
                    }
                }
            } else {
                GroupLabel(stringResource(R.string.onboarding_accent_color))
                AccentSwatches(selectedId = theme.colorPalette ?: AccentPaletteIds.first(), onSelect = viewModel::setAccent)
            }
            SonaRowGroup(
                modifier = Modifier.padding(top = 16.dp),
                rows = listOf(
                    switchRow(
                        title = stringResource(R.string.onboarding_cover_colors),
                        summary = stringResource(R.string.onboarding_cover_colors_summary),
                        icon = SonaIcons.CoverColors,
                        checked = usesCoverColors,
                        onCheckedChange = viewModel::setCoverColors,
                    ),
                ),
            )
        }
    }
}

/** Whether the device gives its wallpaper's colours for apps to take on - Android 12 on. */
private val HasWallpaperColors = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

private val ThemeMode.labelRes: Int
    get() = when (this) {
        ThemeMode.SYSTEM -> R.string.onboarding_theme_system
        ThemeMode.LIGHT -> R.string.onboarding_theme_light
        ThemeMode.DARK -> R.string.onboarding_theme_dark
    }

private val ThemeMode.icon: ImageVector
    get() = when (this) {
        ThemeMode.SYSTEM -> SonaIcons.BrightnessAuto
        ThemeMode.LIGHT -> SonaIcons.LightMode
        ThemeMode.DARK -> SonaIcons.DarkMode
    }

/** A few of Settings' palettes as round swatches; the picked one - [selectedId], if any - turns a softer square, ringed. */
@Composable
private fun AccentSwatches(selectedId: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    // Each swatch keeps room for its ring around it, so they stand 12 apart and in line with the label.
    Row(modifier = modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        AccentPaletteIds.mapNotNull(ThemePalettes::findById).forEach { palette ->
            val isSelected = palette.id == selectedId
            val corner by animateDpAsState(if (isSelected) 12.dp else 18.dp, label = "swatchCorner")
            val name = stringResource(palette.nameRes)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .border(2.dp, if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent, RoundedCornerShape(corner + 4.dp))
                    .padding(4.dp)
                    .clip(RoundedCornerShape(corner))
                    .background(palette.seeds.primary)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(palette.id) })
                    .semantics { contentDescription = name },
            )
        }
    }
}

// Updates.

private fun updatesStep(viewModel: OnboardingViewModel, arrivals: StepArrivals): LazyListScope.() -> Unit = {
    piece("howItWorks", arrivals, 2) {
        InfoCard(
            listOf(
                SonaIcons.Wifi to stringResource(R.string.onboarding_updates_download),
                SonaIcons.InstallMobile to stringResource(R.string.onboarding_updates_install),
                SonaIcons.ToggleOff to stringResource(R.string.onboarding_updates_off),
            ),
        )
    }
    piece("channel", arrivals, 3) {
        val channel by viewModel.updateChannel.collectAsStateWithLifecycle()
        Column {
            GroupLabel(stringResource(R.string.onboarding_updates_channel))
            SonaConnectedChoice(
                options = UpdateChannel.entries,
                selected = channel,
                onSelect = viewModel::setUpdateChannel,
                label = { stringResource(if (it == UpdateChannel.STABLE) R.string.onboarding_updates_stable else R.string.onboarding_updates_prerelease) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(
                    if (channel == UpdateChannel.STABLE) R.string.onboarding_updates_stable_summary else R.string.onboarding_updates_prerelease_summary,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, top = 8.dp),
            )
        }
    }
}

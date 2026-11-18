@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.lhacenmed.sona.feature.onboarding

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.runtime.Stable
import com.lhacenmed.sona.core.designsystem.component.SonaIconButton
import com.lhacenmed.sona.core.downloader.RuntimeState
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.activity.result.IntentSenderRequest
import androidx.compose.animation.AnimatedContent
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
    message: String,
    arrivals: StepArrivals,
    modifier: Modifier,
    listState: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(state = listState, modifier = modifier.fadingFoot()) {
        item(key = "title") {
            with(arrivals) {
                Column(modifier = Modifier.padding(horizontal = ScreenPadding).padding(bottom = 24.dp)) {
                    Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.arrive(0))
                    Text(
                        message,
                        style = MessageStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp).arrive(1),
                    )
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
            Box(modifier = Modifier.padding(horizontal = ScreenPadding).arrive(order)) { content() }
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

/** The screens restoring goes through, one after another on its step - each arriving as a step does. */
internal enum class RestoreStage { SOURCE, SEARCHING, NO_BACKUPS, CHOSEN, RESTORING, RESTORED }

internal val RestoreState.stage: RestoreStage
    get() = when (this) {
        is RestoreState.ChoosingSource -> RestoreStage.SOURCE
        is RestoreState.Searching -> RestoreStage.SEARCHING
        is RestoreState.NoBackups -> RestoreStage.NO_BACKUPS
        is RestoreState.Chosen -> if (status == RestoreStatus.RESTORING || status == RestoreStatus.FAILED) RestoreStage.RESTORING else RestoreStage.CHOSEN
        is RestoreState.Restored -> RestoreStage.RESTORED
    }

/** The steps a restore goes through, as it says it is on them - see [BackupStep]. */
private val RestoreSteps = BackupStep.entries.dropWhile { it != BackupStep.READING_BACKUP }

/**
 * Restoring: where the backup is - Google Drive or a file - then which, then restoring it and what came back. Each screen
 * arrives as a step does, forward or back; the first with the step itself, by its [arrivals].
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

    AnimatedContent(
        targetState = restore,
        contentKey = { it.stage },
        transitionSpec = { EnterTransition.None togetherWith fadeOut(tween(FadeOutMillis)) },
        label = "restoreStage",
    ) { shown ->
        // The first screen arrives with the step; each after it, on its own.
        val stageArrivals = if (shown.stage == initialStage) arrivals else rememberStepArrivals(forward)
        val (title, message) = restoreText(shown)
        // Each stage's own rows, laid in the list under the title.
        val content: LazyListScope.() -> Unit = when (shown) {
            is RestoreState.ChoosingSource -> sourceStage(shown, stageArrivals, onConnect = { connectDrive(DrivePurpose.FIND_BACKUPS) }, onChooseFile = onChooseFile)
            is RestoreState.Searching -> searchingStage(shown.account, stageArrivals, onChange = { connectDrive(DrivePurpose.FIND_BACKUPS) })
            is RestoreState.NoBackups -> noBackupsStage(shown, stageArrivals, onChange = { connectDrive(DrivePurpose.FIND_BACKUPS) }, onChooseFile = onChooseFile)
            is RestoreState.Chosen -> if (shown.stage == RestoreStage.RESTORING) {
                restoringStage(shown, stageArrivals)
            } else {
                chosenStage(shown, viewModel, stageArrivals, onChange = { connectDrive(DrivePurpose.FIND_BACKUPS) }, onChooseFile = onChooseFile)
            }
            is RestoreState.Restored -> restoredStage(shown, viewModel, stageArrivals, onConnect = { connectDrive(DrivePurpose.BACK_UP) })
        }
        StepList(title, message, stageArrivals, modifier, content = content)
    }
}

/** What a restore screen is called, and what it says under that. */
@Composable
private fun restoreText(restore: RestoreState): Pair<String, String> = when (restore) {
    is RestoreState.Chosen -> when {
        restore.stage == RestoreStage.RESTORING -> {
            // From the device it was made on, where that is known; else from the file, or Drive - or carrying on.
            val from = restore.device?.label ?: (restore.source as? BackupSource.File)?.name
            stringResource(R.string.onboarding_restoring) to when {
                from != null -> stringResource(R.string.onboarding_restoring_from, from)
                restore.source == BackupSource.Earlier -> stringResource(R.string.onboarding_restoring_carrying_on)
                else -> stringResource(R.string.onboarding_restoring_from_drive)
            }
        }
        restore.source is BackupSource.Drive -> stringResource(R.string.onboarding_restore_title) to stringResource(R.string.onboarding_restore_pick_message)
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
        TextButton(onClick = onChange, shapes = buttonPressShapes()) { Text(stringResource(R.string.onboarding_restore_change)) }
    }
}

private fun searchingStage(account: String, arrivals: StepArrivals, onChange: () -> Unit): LazyListScope.() -> Unit = {
    piece("account", arrivals, 2) { AccountChip(account, onChange) }
    piece("searching", arrivals, 3) {
        Row(
            modifier = Modifier
                .padding(top = 16.dp)
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
            Text(
                stringResource(R.string.onboarding_restore_searching),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun noBackupsStage(
    restore: RestoreState.NoBackups,
    arrivals: StepArrivals,
    onChange: () -> Unit,
    onChooseFile: () -> Unit,
): LazyListScope.() -> Unit = {
    piece("account", arrivals, 2) { AccountChip(restore.account, onChange) }
    piece("empty", arrivals, 3) {
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
            Text(
                stringResource(R.string.onboarding_restore_no_backups),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
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
                TextButton(onClick = onChange, shapes = buttonPressShapes()) { Text(stringResource(R.string.onboarding_restore_other_account)) }
                TextButton(onClick = onChooseFile, shapes = buttonPressShapes()) { Text(stringResource(R.string.onboarding_restore_choose_file)) }
            }
        }
    }
}

private fun chosenStage(
    restore: RestoreState.Chosen,
    viewModel: OnboardingViewModel,
    arrivals: StepArrivals,
    onChange: () -> Unit,
    onChooseFile: () -> Unit,
): LazyListScope.() -> Unit = {
    when (val source = restore.source) {
        is BackupSource.Drive -> {
            piece("account", arrivals, 2) { AccountChip(source.account, onChange) }
            piece("backups", arrivals, 3) {
                Column {
                    GroupLabel(stringResource(R.string.onboarding_restore_backups_in_account))
                    SonaRowGroup(rows = source.backups.mapIndexed { index, backup -> driveBackupRow(backup, isNewest = index == 0, isPicked = backup == source.picked, viewModel::pickDriveBackup) })
                }
            }
        }
        is BackupSource.File -> piece("file", arrivals, 2) { BackupFileChip(source.name, restore.preview, onChooseFile) }
        // Only ever restoring or stopped - shown as a restore under way is, not as one to choose.
        BackupSource.Earlier -> Unit
    }
    piece("status", arrivals, 4) {
        Column {
            OpenStatusLine(restore.status)
            if (restore.status == RestoreStatus.NEEDS_PASSPHRASE || restore.status == RestoreStatus.WRONG_PASSPHRASE) {
                PassphraseField(isWrong = restore.status == RestoreStatus.WRONG_PASSPHRASE, onSubmit = viewModel::submitPassphrase)
            }
        }
    }
    piece("comesBack", arrivals, 5) {
        Column {
            GroupLabel(stringResource(R.string.onboarding_restore_what_comes_back))
            InfoCard(
                listOf(
                    SonaIcons.Settings to stringResource(R.string.onboarding_restore_settings),
                    SonaIcons.Playlist to stringResource(R.string.onboarding_restore_playlists),
                    SonaIcons.Favorite to stringResource(R.string.onboarding_restore_favorites),
                    SonaIcons.History to stringResource(R.string.onboarding_restore_history),
                ),
            )
        }
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
        TextButton(onClick = onChange, shapes = buttonPressShapes()) { Text(stringResource(R.string.onboarding_restore_change)) }
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

/** What came back, and - restored from Drive - backing up there from now on; from a file, connecting Drive to. */
private fun restoredStage(
    restore: RestoreState.Restored,
    viewModel: OnboardingViewModel,
    arrivals: StepArrivals,
    onConnect: () -> Unit,
): LazyListScope.() -> Unit = {
    piece("cameBack", arrivals, 2) {
        InfoCard(
            listOf(
                SonaIcons.QueueMusic to stringResource(R.string.onboarding_restored_lists),
                SonaIcons.Tune to stringResource(R.string.onboarding_restored_settings),
                SonaIcons.History to stringResource(R.string.onboarding_restored_history),
            ),
        )
    }
    piece("backingUp", arrivals, 3) {
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

// Access.

private fun accessStep(arrivals: StepArrivals): LazyListScope.() -> Unit = {
    piece("permissions", arrivals, 2) {
        SonaRowGroup(rows = AppPermission.needed.map { permission -> permissionRow(permission) })
    }
}

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
            if (isGranted) {
                Icon(
                    SonaIcons.CheckCircle,
                    contentDescription = allowedLabel,
                    tint = MaterialTheme.colorScheme.primary,
                )
            } else {
                Pill(stringResource(R.string.onboarding_allow), request)
            }
        },
        supportingContent = { Text(stringResource(summary)) },
    ) { Text(stringResource(title)) }
}

// Music folders.

private fun foldersStep(viewModel: OnboardingViewModel, arrivals: StepArrivals): LazyListScope.() -> Unit = {
    piece("folders", arrivals, 2) {
        val folders by viewModel.folders.collectAsStateWithLifecycle()
        val trackCount by viewModel.trackCount.collectAsStateWithLifecycle()
        Column {
            if (folders.isEmpty()) {
                Text(
                    stringResource(R.string.onboarding_folders_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            } else {
                SonaRowGroup(
                    rows = folders.map { folder ->
                        switchRow(
                            title = folder.name,
                            summary = pluralStringResource(R.plurals.onboarding_folder_tracks, folder.trackCount, folder.trackCount),
                            icon = SonaIcons.Folder,
                            checked = folder.isIncluded,
                            onCheckedChange = { viewModel.setFolderIncluded(folder.path, it) },
                        )
                    },
                )
                Text(
                    pluralStringResource(R.plurals.onboarding_library_total, trackCount, trackCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, top = 12.dp),
                )
            }
        }
    }
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
 * The downloader - what downloading takes beyond the app: fetched from here, once, carrying on on its own while the setup
 * moves on; its progress shown as it goes, and stopped from its end.
 */
private fun downloaderRow(state: RuntimeState, enabled: Boolean, viewModel: OnboardingViewModel): SonaGroupRow = { shapes ->
    val context = LocalContext.current
    val summary = when (state) {
        RuntimeState.Missing -> stringResource(R.string.onboarding_downloader_missing)
        is RuntimeState.Downloading -> stringResource(
            R.string.onboarding_downloader_downloading,
            Formatter.formatShortFileSize(context, state.receivedBytes),
            Formatter.formatShortFileSize(context, state.totalBytes),
        )
        RuntimeState.Installed -> stringResource(R.string.onboarding_downloader_installed)
        RuntimeState.Failed -> stringResource(R.string.onboarding_downloader_failed)
    }
    SegmentedListItem(
        onClick = { if (state == RuntimeState.Missing || state == RuntimeState.Failed) viewModel.downloadDownloader() },
        shapes = shapes,
        colors = rowColors(),
        enabled = enabled,
        leadingContent = { Icon(SonaIcons.Download, contentDescription = null) },
        trailingContent = {
            when (state) {
                RuntimeState.Missing -> Pill(stringResource(R.string.onboarding_downloader_get), viewModel::downloadDownloader, enabled)
                RuntimeState.Failed -> Pill(stringResource(R.string.onboarding_downloader_retry), viewModel::downloadDownloader, enabled)
                RuntimeState.Installed -> Icon(
                    SonaIcons.CheckCircle,
                    contentDescription = stringResource(R.string.onboarding_downloader_installed),
                    tint = MaterialTheme.colorScheme.primary,
                )
                is RuntimeState.Downloading -> SonaIconButton(
                    onClick = viewModel::stopDownloadingDownloader,
                    icon = SonaIcons.Close,
                    contentDescription = stringResource(R.string.onboarding_downloader_cancel),
                )
            }
        },
        supportingContent = {
            Column {
                Text(summary, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                if (state is RuntimeState.Downloading) {
                    val progress by animateFloatAsState(state.progress, tween(400, easing = EmphasizedDecelerate), label = "downloaderProgress")
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                        trackColor = MaterialTheme.colorScheme.secondaryContainer,
                        drawStopIndicator = {},
                    )
                }
            }
        },
    ) { Text(stringResource(R.string.onboarding_downloader)) }
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
            // The one under the choice opens as the other closes, the swatches popping in one after another.
            AnimatedVisibility(visible = usesWallpaperColors, enter = OptionEnter, exit = OptionExit) {
                Text(
                    stringResource(R.string.onboarding_colors_wallpaper_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, top = 8.dp),
                )
            }
            AnimatedVisibility(visible = !usesWallpaperColors, enter = OptionEnter, exit = OptionExit) {
                Column {
                    if (!HasWallpaperColors) GroupLabel(stringResource(R.string.onboarding_accent_color))
                    AccentSwatches(
                        selectedId = theme.colorPalette ?: AccentPaletteIds.first(),
                        onSelect = viewModel::setAccent,
                        modifier = Modifier.padding(top = if (HasWallpaperColors) 12.dp else 0.dp),
                    )
                }
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

/** How what a choice reveals under it comes and goes: opening into its room as it fades in, closing as it fades out. */
private val OptionEnter = expandVertically(tween(ArrivalMillis, easing = EmphasizedDecelerate), expandFrom = Alignment.Top) +
    fadeIn(tween(FadeInMillis, delayMillis = StaggerMillis))
private val OptionExit = shrinkVertically(tween(FadeOutMillis * 2, easing = FastOutLinearInEasing), shrinkTowards = Alignment.Top) +
    fadeOut(tween(FadeOutMillis))

/** How far a swatch grows from as it pops in. */
private const val SwatchStartScale = 0.6f

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

/**
 * A few of Settings' palettes as round swatches; the picked one - [selectedId], if any - turns a softer square, ringed. Shown
 * in an [AnimatedVisibility], they pop in one after another as it opens.
 */
@Composable
private fun AnimatedVisibilityScope.AccentSwatches(selectedId: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    // Each swatch keeps room for its ring around it, so they stand 12 apart and in line with the label.
    Row(modifier = modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        AccentPaletteIds.mapNotNull(ThemePalettes::findById).forEachIndexed { index, palette ->
            val isSelected = palette.id == selectedId
            val corner by animateDpAsState(if (isSelected) 12.dp else 18.dp, label = "swatchCorner")
            val name = stringResource(palette.nameRes)
            val delay = (index + 1) * StaggerMillis
            Box(
                modifier = Modifier
                    .animateEnterExit(
                        enter = fadeIn(tween(FadeInMillis, delay)) +
                            scaleIn(tween(ArrivalMillis, delay, EmphasizedDecelerate), initialScale = SwatchStartScale),
                        exit = ExitTransition.None,
                    )
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

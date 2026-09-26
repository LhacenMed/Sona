@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.lhacenmed.sona.feature.settings.updates

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.lhacenmed.sona.core.datastore.UpdateChannel
import com.lhacenmed.sona.core.designsystem.component.LocalBottomContentPadding
import com.lhacenmed.sona.core.designsystem.component.SonaTopAppBar
import com.lhacenmed.sona.core.designsystem.component.fab.screenList
import com.lhacenmed.sona.core.designsystem.theme.buttonPressShapes
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.settings.R
import com.lhacenmed.sona.feature.update.installedVersionName
import com.lhacenmed.sona.feature.update.github.Commit
import com.lhacenmed.sona.feature.update.github.Release
import com.lhacenmed.sona.feature.update.ui.NewUpdateSheet
import com.lhacenmed.sona.feature.update.ui.UpdateDownloadDialog
import com.lhacenmed.sona.feature.update.ui.startUpdate
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** From this width the status and the preferences stand side by side - Material's medium window. */
private const val WideLayoutMinWidthDp = 600

/**
 * The version running and the newest on the chosen channel, how updates are found, and what is coming
 * next - ArchiveTune's `UpdateScreen`.
 */
data object UpdatesScreen : Screen {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.current
        val uriHandler = LocalUriHandler.current
        val viewModel: UpdatesViewModel = hiltViewModel()
        val channel by viewModel.channel.collectAsStateWithLifecycle()
        val notifications by viewModel.notifications.collectAsStateWithLifecycle()
        val latest by viewModel.latest.collectAsStateWithLifecycle()
        val commits by viewModel.commits.collectAsStateWithLifecycle()
        val check by viewModel.check.collectAsStateWithLifecycle()

        var isCommitHistoryExpanded by rememberSaveable { mutableStateOf(true) }
        var showBetaConfirmation by rememberSaveable { mutableStateOf(false) }
        var showNotificationConfirmation by rememberSaveable { mutableStateOf(false) }
        // The release being downloaded from here; its progress lives in the registry, so nothing is lost with it.
        var updatingTo by remember { mutableStateOf<Release?>(null) }

        val useWideLayout = LocalConfiguration.current.screenWidthDp >= WideLayoutMinWidthDp
        val maxContentWidth = if (useWideLayout) 1_040.dp else 680.dp

        val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) viewModel.setNotifications(true)
        }
        val enableNotifications = {
            val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            if (needsPermission) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.setNotifications(true)
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            SonaTopAppBar(
                title = stringResource(R.string.updates_title),
                subtitle = stringResource(
                    when (channel) {
                        UpdateChannel.STABLE -> R.string.updates_subtitle_stable
                        UpdateChannel.BETA -> R.string.updates_subtitle_beta
                    },
                ),
                onNavigateBack = navigator::back,
            )

            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().screenList(listState),
                contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = LocalBottomContentPadding.current),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item(key = "dashboard", contentType = "dashboard") {
                    val status = @Composable { modifier: Modifier ->
                        UpdateStatusPanel(
                            currentVersion = context.installedVersionName(),
                            latest = latest,
                            isUpdateAvailable = latest?.let(viewModel::isNewer) == true,
                            channel = channel,
                            onCheckForUpdate = viewModel::checkForUpdate,
                            onOpenChangelog = { navigator.go(ChangelogScreen(channel)) },
                            modifier = modifier,
                        )
                    }
                    val preferences = @Composable { modifier: Modifier ->
                        UpdatePreferencesPanel(
                            notifications = notifications,
                            channel = channel,
                            onNotificationsChange = { enabled ->
                                if (enabled) showNotificationConfirmation = true else viewModel.setNotifications(false)
                            },
                            onStableSelected = { viewModel.setChannel(UpdateChannel.STABLE) },
                            onBetaSelected = { if (channel != UpdateChannel.BETA) showBetaConfirmation = true },
                            modifier = modifier,
                        )
                    }
                    val dashboardModifier = Modifier.fillMaxWidth().widthIn(max = maxContentWidth)
                    if (useWideLayout) {
                        Row(modifier = dashboardModifier, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            status(Modifier.weight(1f))
                            preferences(Modifier.weight(1f))
                        }
                    } else {
                        Column(modifier = dashboardModifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            status(Modifier)
                            preferences(Modifier)
                        }
                    }
                }

                item(key = "commits", contentType = "commits") {
                    CommitHistorySection(
                        commits = commits,
                        isExpanded = isCommitHistoryExpanded,
                        onToggleExpanded = { isCommitHistoryExpanded = !isCommitHistoryExpanded },
                        onCommitClick = { commit -> runCatching { uriHandler.openUri(commit.url) } },
                        modifier = Modifier.fillMaxWidth().widthIn(max = maxContentWidth),
                    )
                }
            }
        }

        when (val current = check) {
            UpdateCheck.Idle -> Unit
            UpdateCheck.Checking -> CheckingDialog()
            is UpdateCheck.UpToDate -> UpToDateDialog(current.versionName, onDismiss = viewModel::dismissCheck)
            is UpdateCheck.Failed -> CheckFailedDialog(current.message, onDismiss = viewModel::dismissCheck)
            is UpdateCheck.Available ->
                NewUpdateSheet(
                    release = current.release,
                    onUpdate = {
                        viewModel.dismissCheck()
                        updatingTo = current.release
                        startUpdate(context, current.release)
                    },
                    onDismissRequest = viewModel::dismissCheck,
                )
        }

        updatingTo?.let { release -> UpdateDownloadDialog(release = release, onClose = { updatingTo = null }) }

        if (showNotificationConfirmation) {
            NotificationConfirmationDialog(
                onConfirm = {
                    showNotificationConfirmation = false
                    enableNotifications()
                },
                onDismiss = { showNotificationConfirmation = false },
            )
        }

        if (showBetaConfirmation) {
            AlertDialog(
                onDismissRequest = { showBetaConfirmation = false },
                title = { Text(stringResource(R.string.updates_channel_beta)) },
                text = { Text(stringResource(R.string.updates_beta_confirmation), style = MaterialTheme.typography.bodyMedium) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showBetaConfirmation = false
                            viewModel.setChannel(UpdateChannel.BETA)
                        },
                        shapes = buttonPressShapes(),
                    ) { Text(stringResource(android.R.string.ok)) }
                },
                dismissButton = {
                    TextButton(onClick = { showBetaConfirmation = false }, shapes = buttonPressShapes()) {
                        Text(stringResource(android.R.string.cancel))
                    }
                },
            )
        }
    }
}

@Composable
private fun UpdateStatusPanel(
    currentVersion: String,
    latest: Release?,
    isUpdateAvailable: Boolean,
    channel: UpdateChannel,
    onCheckForUpdate: () -> Unit,
    onOpenChangelog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val isBeta = channel == UpdateChannel.BETA
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerHigh),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = MaterialShapes.SoftBurst.toShape(),
                    color = if (isUpdateAvailable) colorScheme.primaryContainer else colorScheme.secondaryContainer,
                    contentColor = if (isUpdateAvailable) colorScheme.onPrimaryContainer else colorScheme.onSecondaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.SystemUpdate, contentDescription = null, modifier = Modifier.size(24.dp))
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.updates_current_version),
                            style = MaterialTheme.typography.labelLarge,
                            color = colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = if (isBeta) colorScheme.tertiaryContainer else colorScheme.secondaryContainer,
                            contentColor = if (isBeta) colorScheme.onTertiaryContainer else colorScheme.onSecondaryContainer,
                        ) {
                            Text(
                                text = stringResource(channelLabelRes(channel)),
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                    Text(text = currentVersion, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        text = when {
                            latest == null -> stringResource(R.string.updates_status_checking)
                            isUpdateAvailable -> stringResource(R.string.updates_latest_version, latest.versionName)
                            else -> stringResource(R.string.updates_status_current)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(
                    onClick = onCheckForUpdate,
                    shapes = buttonPressShapes(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.updates_check_for_update))
                }
                TextButton(
                    onClick = onOpenChangelog,
                    shapes = buttonPressShapes(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Filled.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.updates_view_changelog))
                }
            }
        }
    }
}

@Composable
private fun UpdatePreferencesPanel(
    notifications: Boolean,
    channel: UpdateChannel,
    onNotificationsChange: (Boolean) -> Unit,
    onStableSelected: () -> Unit,
    onBetaSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SegmentedListItem(
            onClick = { onNotificationsChange(!notifications) },
            shapes = ListItemDefaults.shapes(shape = MaterialTheme.shapes.extraLarge),
            colors = ListItemDefaults.segmentedColors(containerColor = colorScheme.surfaceContainerLow),
            modifier = Modifier.fillMaxWidth(),
            leadingContent = {
                FeatureIcon(Icons.Filled.NewReleases, colorScheme.secondaryContainer, colorScheme.onSecondaryContainer)
            },
            trailingContent = { Switch(checked = notifications, onCheckedChange = null) },
            supportingContent = { Text(stringResource(R.string.updates_notifications_summary)) },
        ) {
            Text(stringResource(R.string.updates_notifications_title))
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerLow),
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FeatureIcon(Icons.Filled.Tune, colorScheme.tertiaryContainer, colorScheme.onTertiaryContainer)
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(R.string.updates_channel_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.updates_channel_summary),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant,
                        )
                    }
                }
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = channel == UpdateChannel.STABLE,
                        onClick = onStableSelected,
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        icon = {},
                    ) { Text(stringResource(R.string.updates_channel_stable)) }
                    SegmentedButton(
                        selected = channel == UpdateChannel.BETA,
                        onClick = onBetaSelected,
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        icon = {},
                    ) { Text(stringResource(R.string.updates_channel_beta)) }
                }
            }
        }
    }
}

/** The development branch's latest commits, under a header that folds them away. Null [commits] are loading. */
@Composable
private fun CommitHistorySection(
    commits: List<Commit>?,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onCommitClick: (Commit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val rotation by animateFloatAsState(targetValue = if (isExpanded) 180f else 0f, label = "commit-history-arrow")
    Column(modifier = modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SegmentedListItem(
            onClick = onToggleExpanded,
            shapes = ListItemDefaults.shapes(shape = MaterialTheme.shapes.extraLarge),
            colors = ListItemDefaults.segmentedColors(containerColor = colorScheme.surfaceContainerLow),
            modifier = Modifier.fillMaxWidth(),
            leadingContent = { FeatureIcon(Icons.Filled.History, colorScheme.secondaryContainer, colorScheme.onSecondaryContainer) },
            trailingContent = { Icon(Icons.Filled.ExpandMore, contentDescription = null, modifier = Modifier.rotate(rotation)) },
            supportingContent = {
                Text(
                    when {
                        commits == null -> stringResource(R.string.updates_loading_commits)
                        commits.isEmpty() -> stringResource(R.string.updates_no_commits)
                        else -> stringResource(R.string.updates_recent_commits_count, commits.size)
                    },
                )
            },
        ) {
            Text(
                text = stringResource(R.string.updates_recent_commits),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }

        AnimatedVisibility(visible = isExpanded) {
            when {
                commits == null ->
                    Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, color = colorScheme.surfaceContainerLow) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            LoadingIndicator(modifier = Modifier.size(32.dp))
                            Text(
                                text = stringResource(R.string.updates_loading_commits),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                commits.isEmpty() ->
                    Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, color = colorScheme.surfaceContainerLow) {
                        Text(
                            text = stringResource(R.string.updates_no_commits),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
                        )
                    }

                else ->
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                        commits.forEachIndexed { index, commit ->
                            key(commit.sha) {
                                CommitItem(commit = commit, index = index, count = commits.size, onClick = { onCommitClick(commit) })
                            }
                        }
                    }
            }
        }
    }
}

@Composable
private fun CommitItem(
    commit: Commit,
    index: Int,
    count: Int,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = ListItemDefaults.segmentedColors(containerColor = colorScheme.surfaceContainerLow),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        leadingContent = { CommitAvatar(commit.authorAvatarUrl) },
        trailingContent = {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = colorScheme.onSurfaceVariant)
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = commit.sha,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = colorScheme.primary,
                )
                Text(
                    text = if (commit.date.isNotEmpty()) "${commit.author} - ${formatCommitDate(commit.date)}" else commit.author,
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
    ) {
        Text(text = commit.message, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CommitAvatar(avatarUrl: String?) {
    Surface(modifier = Modifier.size(40.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Box(contentAlignment = Alignment.Center) {
            if (avatarUrl != null) {
                AsyncImage(model = avatarUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun FeatureIcon(icon: ImageVector, containerColor: Color, contentColor: Color) {
    Surface(shape = MaterialTheme.shapes.large, color = containerColor) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.padding(12.dp).size(22.dp))
    }
}

@Composable
private fun CheckingDialog() {
    AlertDialog(
        onDismissRequest = {},
        icon = { LoadingIndicator(modifier = Modifier.size(24.dp)) },
        title = { Text(stringResource(R.string.updates_status_checking), style = MaterialTheme.typography.headlineSmall) },
        confirmButton = {},
    )
}

@Composable
private fun UpToDateDialog(versionName: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        },
        title = {
            Text(
                text = stringResource(R.string.updates_status_current),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        text = {
            Text(
                text = versionName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss, shapes = buttonPressShapes()) { Text(stringResource(android.R.string.ok)) }
        },
    )
}

@Composable
private fun CheckFailedDialog(message: String?, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(24.dp)) },
        title = { Text(stringResource(R.string.updates_check_failed), style = MaterialTheme.typography.headlineSmall) },
        text = {
            Text(
                text = message ?: stringResource(R.string.updates_error_unknown),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss, shapes = buttonPressShapes()) { Text(stringResource(android.R.string.ok)) }
        },
    )
}

/** ArchiveTune's word on its channels before notifications are turned on. */
@Composable
private fun NotificationConfirmationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.updates_notifications_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.updates_channel_warning_intro), style = MaterialTheme.typography.bodyMedium)
                ChannelWarning(
                    title = stringResource(R.string.updates_channel_warning_stable_title),
                    lines = listOf(
                        stringResource(R.string.updates_channel_warning_stable_source),
                        stringResource(R.string.updates_channel_warning_stable_description),
                    ),
                )
                ChannelWarning(
                    title = stringResource(R.string.updates_channel_warning_beta_title),
                    lines = listOf(
                        stringResource(R.string.updates_channel_warning_beta_source),
                        stringResource(R.string.updates_channel_warning_beta_risk),
                    ),
                )
                Text(stringResource(R.string.updates_channel_warning_beta_unstable), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.updates_channel_warning_acknowledgement), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, shapes = buttonPressShapes()) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = buttonPressShapes()) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

@Composable
private fun ChannelWarning(title: String, lines: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        lines.forEach { Text(text = it, style = MaterialTheme.typography.bodySmall) }
    }
}

internal fun channelLabelRes(channel: UpdateChannel): Int =
    when (channel) {
        UpdateChannel.STABLE -> R.string.updates_channel_stable
        UpdateChannel.BETA -> R.string.updates_channel_beta
    }

/** "Sep 26" - ArchiveTune's commit date. */
private fun formatCommitDate(isoDate: String): String =
    runCatching {
        val input = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        SimpleDateFormat("MMM d", Locale.getDefault()).format(input.parse(isoDate)!!)
    }.getOrElse { isoDate.take(10) }

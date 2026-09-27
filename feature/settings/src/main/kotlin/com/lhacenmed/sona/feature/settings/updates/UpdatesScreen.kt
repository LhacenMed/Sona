package com.lhacenmed.sona.feature.settings.updates

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.datastore.UpdateChannel
import com.lhacenmed.sona.core.designsystem.component.actionButton
import com.lhacenmed.sona.core.designsystem.component.dialog.SonaDialog
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.settings.R
import com.lhacenmed.sona.feature.settings.component.SettingsChoiceItem
import com.lhacenmed.sona.feature.settings.component.SettingsHero
import com.lhacenmed.sona.feature.settings.component.SettingsHeroAction
import com.lhacenmed.sona.feature.settings.component.SettingsHeroBadge
import com.lhacenmed.sona.feature.settings.component.SettingsList
import com.lhacenmed.sona.feature.settings.component.SettingsNavigationItem
import com.lhacenmed.sona.feature.settings.component.SettingsSection
import com.lhacenmed.sona.feature.settings.component.SettingsSectionDivider
import com.lhacenmed.sona.feature.settings.component.SettingsSwitchItem
import com.lhacenmed.sona.feature.update.github.Release
import com.lhacenmed.sona.feature.update.ui.NewUpdateSheet
import com.lhacenmed.sona.feature.update.ui.UpdateDownloadDialog
import com.lhacenmed.sona.feature.update.ui.startUpdate

/**
 * Updates - ArchiveTune's `UpdateScreen`, as a settings screen: its update card - the version installed and
 * the newest there is, a check to ask for it now and the changelog - then how updates are looked for and
 * offered, and what is coming.
 *
 * A newer version is offered from the card, in the update sheet; checking runs in the card's button rather
 * than over the screen, and says so only when there is nothing new.
 */
data object UpdatesScreen : Screen {
    override val titleRes: Int get() = R.string.updates_title

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.current
        val viewModel: UpdatesViewModel = hiltViewModel()
        val channel by viewModel.channel.collectAsStateWithLifecycle()
        val notifications by viewModel.notifications.collectAsStateWithLifecycle()
        val autoPrompt by viewModel.autoPrompt.collectAsStateWithLifecycle()
        val latest by viewModel.latest.collectAsStateWithLifecycle()
        val check by viewModel.check.collectAsStateWithLifecycle()
        val installedBuild = viewModel.installedBuild

        var showBetaConfirmation by rememberSaveable { mutableStateOf(false) }
        var showNotificationConfirmation by rememberSaveable { mutableStateOf(false) }
        // The release being downloaded from here; its progress lives in the registry, so nothing is lost with it.
        var updatingTo by remember { mutableStateOf<Release?>(null) }

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

        SettingsList {
            // ArchiveTune's update card: the version installed, on which channel, and the newest there is -
            // with the one button that moves it forward: update to what was found, or look again.
            val latestRelease = latest
            val newer = latestRelease?.takeIf(viewModel::isUpdate)
            SettingsHero(
                icon = rememberVectorPainter(Icons.Filled.SystemUpdate),
                label = stringResource(R.string.updates_current_version),
                badge = SettingsHeroBadge(
                    text = stringResource(channelLabelRes(channel)),
                    isAccented = channel == UpdateChannel.ARTIFACT,
                ),
                title = installedBuild.versionName,
                supporting = when {
                    !installedBuild.isUpdatable -> stringResource(R.string.updates_status_debug)
                    latestRelease == null -> stringResource(R.string.updates_status_checking)
                    newer != null -> stringResource(R.string.updates_latest_available, newer.versionName)
                    else -> stringResource(R.string.updates_status_current)
                },
                isHighlighted = newer != null,
                actions = listOf(
                    if (newer != null) {
                        SettingsHeroAction(
                            label = stringResource(R.string.updates_update_to, newer.versionName),
                            icon = Icons.Filled.SystemUpdate,
                            onClick = { viewModel.reviewUpdate(newer) },
                        )
                    } else {
                        SettingsHeroAction(
                            label = stringResource(R.string.updates_check_for_update),
                            icon = Icons.Filled.Sync,
                            onClick = viewModel::checkForUpdate,
                            enabled = installedBuild.isUpdatable,
                            isBusy = check == UpdateCheck.Checking,
                        )
                    },
                    SettingsHeroAction(
                        label = stringResource(R.string.updates_view_changelog),
                        icon = Icons.Filled.History,
                        onClick = { navigator.go(ChangelogScreen) },
                    ),
                ),
            )

            SettingsSection(stringResource(R.string.updates_preferences_section)) {
                SettingsChoiceItem(
                    title = stringResource(R.string.updates_channel_title),
                    options = UpdateChannel.entries.map { stringResource(channelLabelRes(it)) },
                    selectedIndex = channel.ordinal,
                    onSelect = { index ->
                        val chosen = UpdateChannel.entries[index]
                        // Beta is agreed to before it is chosen; going back to Stable needs no warning.
                        if (chosen == UpdateChannel.ARTIFACT && channel != UpdateChannel.ARTIFACT) {
                            showBetaConfirmation = true
                        } else {
                            viewModel.setChannel(chosen)
                        }
                    },
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.updates_auto_prompt_title),
                    summary = stringResource(R.string.updates_auto_prompt_summary),
                    checked = autoPrompt,
                    onCheckedChange = viewModel::setAutoPrompt,
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.updates_notifications_title),
                    summary = stringResource(R.string.updates_notifications_summary),
                    checked = notifications,
                    onCheckedChange = { enabled ->
                        if (enabled) showNotificationConfirmation = true else viewModel.setNotifications(false)
                    },
                )
            }

            SettingsSectionDivider()

            SettingsSection(stringResource(R.string.updates_development_section)) {
                SettingsNavigationItem(
                    title = stringResource(R.string.updates_recent_commits),
                    summary = stringResource(R.string.updates_commits_summary),
                    onClick = { navigator.go(CommitsScreen) },
                )
            }
        }

        when (val current = check) {
            UpdateCheck.Idle, UpdateCheck.Checking -> Unit
            UpdateCheck.UpToDate -> {
                val message = stringResource(R.string.updates_status_current)
                LaunchedEffect(current) {
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    viewModel.dismissCheck()
                }
            }
            is UpdateCheck.Failed -> MessageDialog(
                title = stringResource(R.string.updates_check_failed),
                onDismiss = viewModel::dismissCheck,
            ) {
                Text(current.message ?: stringResource(R.string.updates_error_unknown))
            }
            is UpdateCheck.Available ->
                NewUpdateSheet(
                    release = current.release,
                    onUpdate = { apk ->
                        viewModel.dismissCheck()
                        updatingTo = current.release
                        startUpdate(context, current.release, apk)
                    },
                    onDismissRequest = viewModel::dismissCheck,
                )
        }

        updatingTo?.let { release -> UpdateDownloadDialog(release = release, onClose = { updatingTo = null }) }

        if (showNotificationConfirmation) {
            ConfirmationDialog(
                title = stringResource(R.string.updates_notifications_title),
                onConfirm = {
                    showNotificationConfirmation = false
                    enableNotifications()
                },
                onDismiss = { showNotificationConfirmation = false },
            ) {
                ChannelsExplanation()
            }
        }

        if (showBetaConfirmation) {
            ConfirmationDialog(
                title = stringResource(R.string.updates_channel_beta),
                onConfirm = {
                    showBetaConfirmation = false
                    viewModel.setChannel(UpdateChannel.ARTIFACT)
                },
                onDismiss = { showBetaConfirmation = false },
            ) {
                Text(stringResource(R.string.updates_beta_confirmation))
            }
        }
    }
}

/** A dialog that only says something, closed with OK. */
@Composable
private fun MessageDialog(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    // Read here rather than inside the group: a group builds its items outside composition.
    val okLabel = stringResource(android.R.string.ok)
    SonaDialog(
        onDismissRequest = onDismiss,
        title = title,
        buttons = { actionButton(label = okLabel, onClick = onDismiss) },
    ) {
        content()
    }
}

/** A dialog asking before a change: Cancel, or OK to [onConfirm] it. */
@Composable
private fun ConfirmationDialog(
    title: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val cancelLabel = stringResource(R.string.dialog_cancel)
    val okLabel = stringResource(android.R.string.ok)
    SonaDialog(
        onDismissRequest = onDismiss,
        title = title,
        buttons = {
            actionButton(label = cancelLabel, onClick = onDismiss)
            actionButton(label = okLabel, onClick = onConfirm)
        },
    ) {
        content()
    }
}

/** ArchiveTune's word on its channels, before notifications are turned on. */
@Composable
private fun ChannelsExplanation() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.updates_channel_warning_intro), style = MaterialTheme.typography.bodyMedium)
        ChannelExplanation(
            title = stringResource(R.string.updates_channel_warning_stable_title),
            lines = listOf(
                stringResource(R.string.updates_channel_warning_stable_source),
                stringResource(R.string.updates_channel_warning_stable_description),
            ),
        )
        ChannelExplanation(
            title = stringResource(R.string.updates_channel_warning_beta_title),
            lines = listOf(
                stringResource(R.string.updates_channel_warning_beta_source),
                stringResource(R.string.updates_channel_warning_beta_risk),
            ),
        )
        Text(stringResource(R.string.updates_channel_warning_beta_unstable), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.updates_channel_warning_acknowledgement), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ChannelExplanation(title: String, lines: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        lines.forEach { Text(text = it, style = MaterialTheme.typography.bodySmall) }
    }
}

private fun channelLabelRes(channel: UpdateChannel): Int =
    when (channel) {
        UpdateChannel.STABLE -> R.string.updates_channel_stable
        UpdateChannel.ARTIFACT -> R.string.updates_channel_beta
    }

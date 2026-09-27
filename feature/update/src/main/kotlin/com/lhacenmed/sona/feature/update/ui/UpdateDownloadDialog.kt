@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.lhacenmed.sona.feature.update.ui

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.common.network.isNetworkOnline
import com.lhacenmed.sona.core.designsystem.theme.buttonPressShapes
import com.lhacenmed.sona.feature.update.R
import com.lhacenmed.sona.feature.update.UpdateInstaller
import com.lhacenmed.sona.feature.update.UpdateRegistry
import com.lhacenmed.sona.feature.update.UpdateService
import com.lhacenmed.sona.feature.update.UpdateState
import com.lhacenmed.sona.feature.update.installedBuild
import com.lhacenmed.sona.feature.update.StagedApk
import com.lhacenmed.sona.feature.update.github.Release
import com.lhacenmed.sona.feature.update.github.ReleaseApk
import com.lhacenmed.sona.feature.update.sizeText
import com.lhacenmed.sona.feature.update.speedText
import kotlin.math.roundToInt

/**
 * Updates to [release] with [apk] - or, with none chosen, the APK for the running build: installs it when it
 * is the one already downloaded, downloads it otherwise - or, offline, says so. What happens next is
 * [UpdateRegistry.state], which [UpdateDownloadDialog] follows.
 */
fun startUpdate(context: Context, release: Release, apk: ReleaseApk?) {
    val chosen = apk ?: release.apkFor(context.installedBuild())
    val state = UpdateRegistry.stateOf()
    when {
        state is UpdateState.Downloaded && state.staged.isOf(release, chosen) -> install(context, state)
        state == UpdateState.Connecting || state is UpdateState.Downloading -> Unit
        chosen == null -> UpdateRegistry.update(UpdateState.Error(context.getString(R.string.update_no_apk)))
        !context.isNetworkOnline() -> UpdateRegistry.update(UpdateState.Error(context.getString(R.string.update_offline)))
        else -> {
            // Connecting from the press on, so what follows it never reads the moment before the
            // service starts as nothing happening.
            UpdateRegistry.update(UpdateState.Connecting)
            UpdateService.start(context, release, chosen)
        }
    }
}

/** Whether this is [release]'s APK of [apk]'s variant - any of its variants, with none chosen. */
private fun StagedApk.isOf(release: Release, apk: ReleaseApk?): Boolean =
    versionName == release.versionName && (apk == null || variant == apk.variant)

/** Hands the downloaded APK to the installer - or, without the grant to, sends the user to give it. */
private fun install(context: Context, downloaded: UpdateState.Downloaded) {
    context.startActivity(
        if (UpdateInstaller.canInstall(context)) {
            UpdateInstaller.installIntent(context, downloaded.staged.file)
        } else {
            UpdateInstaller.requestPermissionIntent(context)
        },
    )
}

/**
 * The update in progress, after [startUpdate] - ArchiveTune's download dialog: the download's progress as a
 * wavy ring with its percentage, how much of how much has arrived and how fast, and Cancel. A download that finishes launches the installer at once;
 * without the grant to install, or on a failure, it stays to say so, and [onClose] is called once it is
 * done with.
 */
@Composable
fun UpdateDownloadDialog(release: Release, onClose: () -> Unit) {
    val context = LocalContext.current
    val state by UpdateRegistry.state.collectAsStateWithLifecycle()

    // The installer opens by itself only for a download seen through here, never for one merely restored.
    var sawDownload by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        when (val current = state) {
            UpdateState.Connecting, is UpdateState.Downloading -> sawDownload = true
            is UpdateState.Downloaded ->
                if (sawDownload && UpdateInstaller.canInstall(context)) {
                    install(context, current)
                    onClose()
                }
            UpdateState.Idle -> onClose()
            is UpdateState.Error -> Unit
        }
    }

    val title = stringResource(
        if (release.isPreRelease) R.string.update_download_title_beta else R.string.update_download_title,
        release.versionName,
    )

    when (val current = state) {
        UpdateState.Connecting, is UpdateState.Downloading -> {
            val downloading = current as? UpdateState.Downloading
            val progress = downloading?.progress
            val animatedProgress by animateFloatAsState(
                targetValue = progress ?: 0f,
                animationSpec = WavyProgressIndicatorDefaults.ProgressAnimationSpec,
                label = "update-download-progress",
            )
            AlertDialog(
                onDismissRequest = {},
                title = { CenteredTitle(title) },
                text = {
                    // The same shape from connecting to done, so nothing moves as the numbers arrive.
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(modifier = Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                            if (progress != null) {
                                CircularWavyProgressIndicator(progress = { animatedProgress }, modifier = Modifier.fillMaxSize())
                                Text(
                                    text = stringResource(
                                        R.string.update_download_progress,
                                        (animatedProgress * 100f).roundToInt().coerceIn(0, 100),
                                    ),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            } else {
                                CircularWavyProgressIndicator(modifier = Modifier.size(72.dp))
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = downloading?.sizeText(context) ?: stringResource(R.string.update_connecting),
                                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = downloading?.speedText(context).orEmpty(),
                                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { UpdateService.cancel(context) }, shapes = buttonPressShapes()) {
                        Text(stringResource(android.R.string.cancel))
                    }
                },
            )
        }

        is UpdateState.Downloaded ->
            AlertDialog(
                onDismissRequest = onClose,
                title = { CenteredTitle(title) },
                text = { Text(stringResource(R.string.update_downloaded)) },
                confirmButton = {
                    TextButton(onClick = { install(context, current) }, shapes = buttonPressShapes()) {
                        Text(stringResource(R.string.update_install))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onClose, shapes = buttonPressShapes()) {
                        Text(stringResource(android.R.string.cancel))
                    }
                },
            )

        is UpdateState.Error ->
            AlertDialog(
                onDismissRequest = onClose,
                icon = { Icon(Icons.Filled.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { Text(stringResource(R.string.update_failed_title)) },
                text = { Text(current.message, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                confirmButton = {
                    TextButton(onClick = onClose, shapes = buttonPressShapes()) {
                        Text(stringResource(android.R.string.ok))
                    }
                },
            )

        UpdateState.Idle -> Unit
    }
}

@Composable
private fun CenteredTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

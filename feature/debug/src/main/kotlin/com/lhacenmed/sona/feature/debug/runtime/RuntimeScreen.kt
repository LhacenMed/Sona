package com.lhacenmed.sona.feature.debug.runtime

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Debug
import android.text.format.Formatter
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.pm.PackageInfoCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import com.lhacenmed.sona.core.common.permission.AppPermission
import com.lhacenmed.sona.core.designsystem.component.dialog.SonaConfirmationDialog
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.debug.DebugViewModel
import com.lhacenmed.sona.feature.debug.Message
import com.lhacenmed.sona.feature.scanner.scanSignatureOf
import com.lhacenmed.sona.feature.settings.component.SettingsActionItem
import com.lhacenmed.sona.feature.settings.component.SettingsInfoItem
import com.lhacenmed.sona.feature.settings.component.SettingsList
import com.lhacenmed.sona.feature.settings.component.SettingsSection
import com.lhacenmed.sona.feature.settings.component.SettingsSectionDivider
import kotlinx.coroutines.launch

/** What the app is doing and how it stands, followed live, with what can be asked of it from here. */
data object RuntimeScreen : Screen {
    override fun title(context: Context): String = "Runtime"

    @Composable
    override fun Content() {
        val viewModel = hiltViewModel<DebugViewModel>()
        SettingsList {
            AppSection()
            SettingsSectionDivider()
            PlaybackSection(viewModel)
            SettingsSectionDivider()
            ScannerSection(viewModel)
            SettingsSectionDivider()
            BackupSection(viewModel)
            SettingsSectionDivider()
            WorkSection()
            SettingsSectionDivider()
            NotificationsSection()
        }
    }
}

@Composable
private fun ColumnScope.AppSection() {
    val context = LocalContext.current
    val packageInfo = remember { context.packageManager.getPackageInfo(context.packageName, 0) }
    val memory = remember { ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java).getMemoryInfo(it) } }
    val runtime = Runtime.getRuntime()
    SettingsSection("App") {
        SettingsInfoItem("Package", context.packageName)
        SettingsInfoItem("Version", "${packageInfo.versionName} (${PackageInfoCompat.getLongVersionCode(packageInfo)})")
        SettingsInfoItem("Device", "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        SettingsInfoItem("Heap", "${Formatter.formatShortFileSize(context, runtime.totalMemory() - runtime.freeMemory())} of ${Formatter.formatShortFileSize(context, runtime.maxMemory())}")
        SettingsInfoItem("Native heap", Formatter.formatShortFileSize(context, Debug.getNativeHeapAllocatedSize()))
        SettingsInfoItem("Device memory free", "${Formatter.formatShortFileSize(context, memory.availMem)} - low: ${memory.lowMemory}")
        SettingsInfoItem("Permissions", AppPermission.entries.joinToString("\n") { "${it.name}: ${if (it.isGranted(context)) "granted" else "not granted"}" })
    }
}

@Composable
private fun ColumnScope.PlaybackSection(viewModel: DebugViewModel) {
    val playback by viewModel.playback.playbackState.collectAsStateWithLifecycle()
    SettingsSection("Playback") {
        SettingsInfoItem("State", "ready: ${playback.isReady}, playing: ${playback.isPlaying}, buffering: ${playback.isBuffering}, ended: ${playback.hasEnded}, watching: ${playback.isWatching}")
        SettingsInfoItem("Track", "${playback.currentTrackId} in ${playback.space}, from ${playback.parent}")
        SettingsInfoItem("Position", "${playback.positionMs} of ${playback.durationMs} ms, speed ${playback.playbackSpeed}")
        SettingsInfoItem("Modes", "shuffle: ${playback.shuffleEnabled}, repeat: ${playback.repeatMode}, muted: ${playback.isMuted}")
        SettingsInfoItem("Queue", "${playback.queue.size} entries, at ${playback.currentQueueIndex}")
    }
}

@Composable
private fun ColumnScope.ScannerSection(viewModel: DebugViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val progress by viewModel.scanner.progress.collectAsStateWithLifecycle()
    val lastSignature by viewModel.scanSettings.lastScanSignature.collectAsState(null)
    var outcome by remember { mutableStateOf<String?>(null) }
    val current = scanSignatureOf(context, viewModel.librarySettings.excludedFolders.value, viewModel.librarySettings.separateArtists.value)

    fun scan(block: suspend () -> Any) {
        scope.launch { outcome = runCatching { block() }.fold({ it.toString() }, { it.toString() }) }
    }

    SettingsSection("Scanner") {
        SettingsInfoItem("Now", progress?.toString() ?: "Not scanning")
        SettingsInfoItem("Signature", current)
        SettingsInfoItem("Last scan", "${lastSignature ?: "None"} - ${if (lastSignature == current) "up to date" else "out of date"}")
        outcome?.let { Message(it) }
        SettingsActionItem(title = "Refresh", summary = "Read MediaStore again, as any change there does", onClick = { scan { viewModel.scanner.refresh() } })
        SettingsActionItem(title = "Rescan everything", summary = "Read every file and walk storage, even with nothing changed", onClick = { scan { viewModel.scanner.rescan(force = true) } })
    }
}

@Composable
private fun ColumnScope.BackupSection(viewModel: DebugViewModel) {
    val settings = viewModel.backupSettings
    val isConnected by settings.isDriveConnected.collectAsStateWithLifecycle()
    val account by settings.driveAccount.collectAsStateWithLifecycle()
    val isPaused by settings.isBackupPaused.collectAsStateWithLifecycle()
    val usesMobileData by settings.usesMobileData.collectAsStateWithLifecycle()
    val foldersLeftOut by settings.foldersLeftOut.collectAsStateWithLifecycle()
    SettingsSection("Backup") {
        SettingsInfoItem("Drive", if (isConnected) "Connected: $account" else "Not connected")
        SettingsInfoItem("State", "paused: $isPaused, uses mobile data: $usesMobileData")
        SettingsInfoItem("Folders left out", foldersLeftOut.joinToString("\n").ifEmpty { "None" })
    }
}

/** Every job WorkManager holds, whatever state it is in - one still to finish cancelled by opening it. */
@Composable
private fun ColumnScope.WorkSection() {
    val context = LocalContext.current
    val workManager = remember { WorkManager.getInstance(context) }
    val work by remember { workManager.getWorkInfosFlow(WorkQuery.fromStates(WorkInfo.State.entries)) }.collectAsState(emptyList())
    var cancelling by remember { mutableStateOf<WorkInfo?>(null) }
    SettingsSection("Background work") {
        if (work.isEmpty()) SettingsInfoItem("Jobs", "None")
        work.forEach { job ->
            SettingsActionItem(
                title = job.tags.joinToString(", ") { it.substringAfterLast('.') },
                summary = "${job.state}, tried ${job.runAttemptCount} times, next at ${job.nextScheduleTimeMillis} - ${job.id}",
                onClick = { cancelling = job },
                enabled = !job.state.isFinished,
            )
        }
    }
    cancelling?.let { job ->
        SonaConfirmationDialog(
            title = "Cancel this job?",
            message = job.tags.joinToString("\n"),
            confirmLabel = "Cancel job",
            successMessage = "Job cancelled",
            failureMessage = "Could not cancel the job",
            onDismiss = { cancelling = null },
            operation = { onFinished ->
                workManager.cancelWorkById(job.id)
                onFinished(true)
            },
        )
    }
}

@Composable
private fun ColumnScope.NotificationsSection() {
    val context = LocalContext.current
    // Looked at again on every look, as nothing says when one comes or goes.
    var version by remember { mutableIntStateOf(0) }
    val active = remember(version) { context.getSystemService(NotificationManager::class.java).activeNotifications.toList() }
    SettingsSection("Notifications") {
        active.forEach { notification ->
            SettingsInfoItem("${notification.id} on ${notification.notification.channelId}", notification.notification.extras.getCharSequence("android.title")?.toString().orEmpty())
        }
        SettingsActionItem(title = "Look again", summary = "${active.size} showing", onClick = { version++ })
    }
}

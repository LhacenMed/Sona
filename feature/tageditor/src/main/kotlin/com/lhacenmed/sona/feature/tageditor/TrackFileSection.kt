package com.lhacenmed.sona.feature.tageditor

import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.common.storage.storageFolderAt
import com.lhacenmed.sona.core.common.storage.storageFolderPathOf
import com.lhacenmed.sona.core.designsystem.component.actionButton
import com.lhacenmed.sona.core.designsystem.component.dialog.SonaDialog
import com.lhacenmed.sona.core.designsystem.component.section.ColumnSection
import com.lhacenmed.sona.core.designsystem.component.sheet.SonaBottomSheet
import com.lhacenmed.sona.core.designsystem.component.sheet.SonaOptionRow
import com.lhacenmed.sona.core.designsystem.component.toast
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import com.lhacenmed.sona.core.downloader.FileFormat
import com.lhacenmed.sona.core.downloader.RuntimeState
import com.lhacenmed.sona.core.model.Track
import java.io.File

/**
 * The track's file, at the editor's foot: its name, its format and where it is - each changed from its row: renamed, turned into
 * another format, or moved to a folder picked with Android's own picker - see [TrackFileChanges] - with how long it is and its
 * bitrate. Whatever Android asks before a file is changed is asked first.
 */
@Composable
internal fun ColumnScope.TrackFileSection(track: Track, bitrateKbps: Long?, viewModel: TagEditorViewModel) {
    val context = LocalContext.current
    val file = File(track.path)
    var isRenaming by rememberSaveable { mutableStateOf(false) }
    var isChoosingFormat by rememberSaveable { mutableStateOf(false) }
    // What is to be done once Android lets Sona change the file.
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val requestAccess = rememberFileAccessRequest(onGranted = {
        pending?.invoke()
        pending = null
    })
    fun whenAllowed(change: () -> Unit) {
        pending = change
        requestAccess()
    }
    val notOnDevice = stringResource(R.string.tag_editor_folder_not_on_device)
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        tree ?: return@rememberLauncherForActivityResult
        val folder = storageFolderPathOf(tree)?.let(::storageFolderAt) ?: return@rememberLauncherForActivityResult context.toast(notOnDevice)
        whenAllowed { viewModel.moveTo(folder) }
    }

    ColumnSection(title = stringResource(R.string.tag_editor_file)) {
        FileFact(label = stringResource(R.string.tag_editor_name), value = file.nameWithoutExtension, onClick = { isRenaming = true })
        FileFact(label = stringResource(R.string.tag_editor_format), value = file.extension.uppercase(), onClick = { isChoosingFormat = true })
        FileFact(label = stringResource(R.string.tag_editor_duration), value = formatDuration(track.durationMs))
        bitrateKbps?.let { FileFact(label = stringResource(R.string.tag_editor_bitrate), value = stringResource(R.string.tag_editor_kbps, it)) }
        FileFact(
            label = stringResource(R.string.tag_editor_path),
            value = file.parent.orEmpty().removePrefix(Environment.getExternalStorageDirectory().path).trimStart('/').ifEmpty { file.parent.orEmpty() },
            onClick = { folderPicker.launch(null) },
        )
    }

    if (isRenaming) {
        RenameDialog(name = file.nameWithoutExtension, onRename = { name -> whenAllowed { viewModel.rename(name) } }, onDismiss = { isRenaming = false })
    }
    if (isChoosingFormat) {
        FormatSheet(file, track.isVideo, viewModel, onConvert = { format -> whenAllowed { viewModel.convertTo(format) } }, onDismiss = { isChoosingFormat = false })
    }
}

/** The file's name to change - its extension kept - with Cancel, then Rename. */
@Composable
private fun RenameDialog(name: String, onRename: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(name) }
    val cancelLabel = stringResource(R.string.tag_editor_cancel)
    val renameLabel = stringResource(R.string.tag_editor_rename)
    SonaDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.tag_editor_rename_title),
        buttons = {
            actionButton(label = cancelLabel, onClick = onDismiss)
            actionButton(label = renameLabel, enabled = text.isNotBlank() && text.trim() != name, onClick = {
                onRename(text)
                onDismiss()
            })
        },
    ) {
        OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text(stringResource(R.string.tag_editor_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * The formats the file can be turned into - those every part of the app reads, edits and plays: see [FileFormat]. Converting
 * needs the downloader, which brings FFmpeg: where it is not here, the sheet offers it first.
 */
@Composable
private fun FormatSheet(file: File, isVideo: Boolean, viewModel: TagEditorViewModel, onConvert: (FileFormat) -> Unit, onDismiss: () -> Unit) {
    val converterState by viewModel.converterState.collectAsStateWithLifecycle()
    val canConvert = converterState == RuntimeState.Installed
    SonaBottomSheet(title = stringResource(R.string.tag_editor_convert_title), onDismissRequest = onDismiss) {
        if (!canConvert) {
            SonaOptionRow(label = stringResource(R.string.tag_editor_convert_needs_downloader), icon = SonaIcons.Extension, onClick = viewModel::onGetConverter)
        }
        FileFormat.choicesFor(file, isVideo).forEach { format ->
            SonaOptionRow(
                label = format.extension.uppercase(),
                icon = if (format.isVideo) SonaIcons.VideoFile else SonaIcons.AudioFile,
                onClick = {
                    if (canConvert) onConvert(format) else viewModel.onGetConverter()
                    dismiss()
                },
            )
        }
    }
}

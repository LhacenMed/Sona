package com.lhacenmed.sona.feature.download

import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.lhacenmed.sona.core.common.permission.AppPermission
import com.lhacenmed.sona.core.designsystem.component.actionButton
import com.lhacenmed.sona.core.designsystem.component.dialog.SonaDialog
import com.lhacenmed.sona.core.designsystem.component.toast
import java.io.File

/** Where the folder picker's files live: the device's storage, by volume - "primary" its own, a memory card by its id. */
private const val ExternalStorageAuthority = "com.android.externalstorage.documents"

/** Download: the one folder Android lets an app save every kind of file in by itself. */
private const val DownloadFolder = "Download"

/**
 * Picks a folder anywhere on the device's storage - Android's own folder picker, where one can be made too - handed to
 * [onPick] as folders are kept: see `DEFAULT_DOWNLOAD_FOLDER`. Outside Download, Android lets an app save there only once it
 * manages all files: where Sona does not yet, the folder is kept only once the user goes to allow it. What is returned
 * opens the picker.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun rememberDownloadFolderPicker(onPick: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    var waitingOnAccess by remember { mutableStateOf<String?>(null) }
    val notOnDevice = stringResource(R.string.download_folder_not_on_device)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val path = folderPathOf(uri) ?: return@rememberLauncherForActivityResult context.toast(notOnDevice)
        if (isInDownload(path) || AppPermission.FILE_CHANGES.isGranted(context)) onPick(path) else waitingOnAccess = path
    }

    waitingOnAccess?.let { path ->
        val cancelLabel = stringResource(R.string.download_cancel)
        val allowLabel = stringResource(R.string.download_folder_access_allow)
        val close = { waitingOnAccess = null }
        SonaDialog(
            onDismissRequest = close,
            title = stringResource(R.string.download_folder_access_title),
            buttons = {
                actionButton(label = cancelLabel, onClick = close)
                actionButton(label = allowLabel, onClick = {
                    onPick(path)
                    runCatching { context.startActivity(AppPermission.FILE_CHANGES.settingsIntent(context)) }
                    close()
                })
            },
        ) {
            Text(stringResource(R.string.download_folder_access_message, path), style = MaterialTheme.typography.bodyMedium)
        }
    }
    return { picker.launch(null) }
}

/** The folder [tree] - one the picker gave - as folders are kept; null for one not on the device's storage. */
private fun folderPathOf(tree: Uri): String? {
    if (tree.authority != ExternalStorageAuthority) return null
    val (volume, inVolume) = DocumentsContract.getTreeDocumentId(tree).split(':', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
    return if (volume.equals("primary", ignoreCase = true)) inVolume.trim('/') else File("/storage/$volume", inVolume).path
}

/** Whether [path] is Download, or within it. */
private fun isInDownload(path: String): Boolean = path == DownloadFolder || path.startsWith("$DownloadFolder/")

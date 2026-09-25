package com.lhacenmed.sona.feature.library.playlist

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Input
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.common.storage.documentPathOrNull
import com.lhacenmed.sona.core.data.itemsOrEmpty
import com.lhacenmed.sona.core.designsystem.component.SonaBottomSheet
import com.lhacenmed.sona.feature.library.M3U_PICKER_MIME_TYPES
import com.lhacenmed.sona.feature.library.PlaylistNameDialog
import com.lhacenmed.sona.feature.library.PlaylistsViewModel
import com.lhacenmed.sona.feature.library.options.toast

/** Where a new playlist's tracks come from - the rows of the New playlist sheet, in its order. */
private enum class NewPlaylistSource(val icon: ImageVector, val label: String, val description: String) {
    EMPTY(Icons.AutoMirrored.Filled.PlaylistAdd, "Empty playlist", "Add tracks to it afterwards"),
    FOLDER(Icons.Filled.CreateNewFolder, "From a folder", "Every track in a folder you pick"),
    FILE(Icons.AutoMirrored.Filled.Input, "From a playlist file", "The tracks an M3U file lists"),
}

/** What a new playlist starts from, once chosen - and so what it is named after, where anything. */
private sealed interface NewPlaylistDraft {
    val suggestedName: String

    data object Empty : NewPlaylistDraft {
        override val suggestedName = ""
    }

    data class FromFolder(val folderPath: String) : NewPlaylistDraft {
        override val suggestedName get() = folderPath.substringAfterLast('/')
    }

    data class FromFile(val uri: Uri, override val suggestedName: String) : NewPlaylistDraft
}

/**
 * Creating a playlist, whichever way it starts - the one flow the Playlists screen's + button and its
 * empty list both open, so there is a single way in.
 *
 * A sheet asks what the playlist starts from: nothing, a folder or a playlist file. A folder or a file
 * is picked next, then every way ends in the same name dialog - named after the folder or the file
 * where there is one, so often it only needs confirming. Backing out of any step ends the flow, and
 * [onFinished] hears it however it ends.
 *
 * A file is read before its playlist is created, so an import that finds none of its tracks leaves no
 * empty playlist behind; how it went is said either way.
 */
@Composable
internal fun NewPlaylistFlow(onFinished: () -> Unit) {
    val context = LocalContext.current
    val viewModel: PlaylistsViewModel = hiltViewModel()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf<NewPlaylistDraft?>(null) }
    // Whether a folder or file picker is open, which the sheet steps aside for.
    var isPicking by remember { mutableStateOf(false) }

    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        isPicking = false
        val folderPath = uri?.let { documentPathOrNull(it, isTree = true) }
        if (folderPath != null) draft = NewPlaylistDraft.FromFolder(folderPath) else onFinished()
    }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        isPicking = false
        if (uri != null) draft = NewPlaylistDraft.FromFile(uri, context.playlistNameOf(uri)) else onFinished()
    }

    when (val chosen = draft) {
        null -> if (!isPicking) {
            NewPlaylistSheet(
                onDismissRequest = onFinished,
                onSourceChosen = { source ->
                    when (source) {
                        NewPlaylistSource.EMPTY -> draft = NewPlaylistDraft.Empty
                        NewPlaylistSource.FOLDER -> {
                            isPicking = true
                            folderLauncher.launch(null)
                        }
                        NewPlaylistSource.FILE -> {
                            isPicking = true
                            fileLauncher.launch(M3U_PICKER_MIME_TYPES)
                        }
                    }
                },
            )
        }

        else -> PlaylistNameDialog(
            dialogTitle = "New playlist",
            confirmLabel = "Create",
            initialName = chosen.suggestedName,
            takenNames = playlists.itemsOrEmpty.map { it.name },
            onDismiss = onFinished,
            onConfirm = { name ->
                when (chosen) {
                    NewPlaylistDraft.Empty -> viewModel.createPlaylist(name)
                    is NewPlaylistDraft.FromFolder -> viewModel.createPlaylistFromFolder(name, chosen.folderPath)
                    is NewPlaylistDraft.FromFile -> viewModel.importIntoNewPlaylist(
                        name = name,
                        openStream = { context.contentResolver.openInputStream(chosen.uri) },
                    ) { succeeded -> context.toast(if (succeeded) "Playlist imported" else "Could not import playlist") }
                }
                onFinished()
            },
        )
    }
}

/** The sheet asking what a new playlist starts from - a row for each [NewPlaylistSource]. */
@Composable
private fun NewPlaylistSheet(onDismissRequest: () -> Unit, onSourceChosen: (NewPlaylistSource) -> Unit) {
    SonaBottomSheet(title = "New playlist", onDismissRequest = onDismissRequest) {
        NewPlaylistSource.entries.forEach { source ->
            ListItem(
                headlineContent = { Text(source.label) },
                supportingContent = { Text(source.description) },
                leadingContent = { Icon(source.icon, contentDescription = null) },
                // The sheet already paints its own background; a row painting its own would seam
                // against it instead of reading as one surface.
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onSourceChosen(source) },
            )
        }
    }
}

/** The name a playlist file suggests for its playlist: the file's own name, without its extension. */
private fun Context.playlistNameOf(uri: Uri): String =
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        ?.substringBeforeLast('.')
        .orEmpty()

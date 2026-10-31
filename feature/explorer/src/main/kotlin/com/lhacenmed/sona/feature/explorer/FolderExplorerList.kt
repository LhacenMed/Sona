package com.lhacenmed.sona.feature.explorer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.IndeterminateCheckBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.lhacenmed.sona.core.designsystem.component.SonaEmptyState
import com.lhacenmed.sona.core.designsystem.component.SonaFolderCover
import com.lhacenmed.sona.core.designsystem.component.SonaListRow
import com.lhacenmed.sona.core.designsystem.component.SonaTrackRow
import com.lhacenmed.sona.core.designsystem.component.SonaVideoRow
import com.lhacenmed.sona.core.designsystem.component.section.rememberSectionListState
import com.lhacenmed.sona.core.designsystem.component.section.section
import com.lhacenmed.sona.core.model.FolderNode
import com.lhacenmed.sona.core.model.FolderPicks
import com.lhacenmed.sona.core.model.Track

/** Picking in an explorer: the files [picks] keys, [keys] what is picked, [onKeysChange] told of every change. */
internal class ExplorerPicking(val picks: FolderPicks, val keys: Set<String>, val onKeysChange: (Set<String>) -> Unit) {

    fun toggle(files: List<Track>) = onKeysChange(picks.toggled(keys, files))

    fun isPicked(files: List<Track>): Boolean = files.isNotEmpty() && files.all { picks.isPicked(it, keys) }

    /** How many of [files] are picked, as their box shows it: all, some, or none. */
    fun boxOf(files: List<Track>): ImageVector {
        val picked = files.count { picks.isPicked(it, keys) }
        return when {
            files.isNotEmpty() && picked == files.size -> Icons.Filled.CheckBox
            picked > 0 -> Icons.Filled.IndeterminateCheckBox
            else -> Icons.Filled.CheckBoxOutlineBlank
        }
    }
}

/**
 * The folder [navigation] has open, of [explored]: the folders inside it, then its music, then its videos, each
 * under its heading. A folder opens with a press; a file plays with one - see [ExplorerPreviewViewModel] - so
 * looking through files never leaves the explorer. [picking], every row has its box, which picks it - a folder
 * with all inside it - so files are picked and listened to alike.
 *
 * Opening a folder slides it in from the side, and going back slides it out the way it came; each folder keeps
 * where it was scrolled to, so coming back to one finds it as it was left. Rows are painted [rowColor] - a sheet's
 * own background, which a row painting the screen's would seam against.
 */
@Composable
internal fun FolderExplorerList(
    explored: ExploredFiles,
    navigation: ExplorerNavigation,
    preview: ExplorerPreview?,
    onPlay: (Track) -> Unit,
    picking: ExplorerPicking?,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    rowColor: Color = MaterialTheme.colorScheme.surface,
) {
    val listStates = remember { HashMap<String?, LazyListState>() }
    val foldersTitle = stringResource(R.string.explorer_section_folders)
    val musicTitle = stringResource(R.string.explorer_section_music)
    val videosTitle = stringResource(R.string.explorer_section_videos)
    val pickLabel = stringResource(R.string.explorer_pick)
    val unpickLabel = stringResource(R.string.explorer_unpick)

    AnimatedContent(
        targetState = navigation.open,
        transitionSpec = {
            val direction = if (navigation.wentBack) -1 else 1
            slideInHorizontally { width -> direction * width } togetherWith slideOutHorizontally { width -> -direction * width }
        },
        modifier = modifier,
        label = "explorerFolder",
    ) { path ->
        val folders = explored.foldersIn(path)
        val (videos, music) = explored.filesIn(path).partition { it.isVideo }
        if (folders.isEmpty() && music.isEmpty() && videos.isEmpty()) {
            SonaEmptyState(title = stringResource(R.string.explorer_empty_title), message = stringResource(R.string.explorer_empty_message))
            return@AnimatedContent
        }
        val sections = rememberSectionListState()
        val folderRows = folders.map { folder -> FolderRowContent(folder, folderDisplayName(folder), explored.filesInside(folder)) }
        val fileCounts = folderRows.associate { it.folder.path to pluralStringResource(R.plurals.explorer_files, it.files.size, it.files.size) }

        LazyColumn(
            state = listStates.getOrPut(path) { LazyListState() },
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            if (folderRows.isNotEmpty()) {
                section(key = "folders", title = foldersTitle, state = sections) {
                    items(folderRows, key = { "folder:${it.folder.path}" }) { row ->
                        SonaListRow(
                            title = row.name,
                            subtitle = fileCounts.getValue(row.folder.path),
                            selection = null,
                            selectionKey = null,
                            onClick = { navigation.open(row.folder.path) },
                            onOpenOptions = picking?.let { { it.toggle(row.files) } },
                            containerColor = rowColor,
                            optionsIcon = picking?.boxOf(row.files) ?: Icons.Filled.CheckBoxOutlineBlank,
                            optionsLabel = if (picking?.isPicked(row.files) == true) unpickLabel else pickLabel,
                            cover = {
                                SonaFolderCover(
                                    coverArtUris = explored.coversOf(row.folder),
                                    seed = row.folder.path.hashCode(),
                                    isSelected = picking?.isPicked(row.files) == true,
                                )
                            },
                        )
                    }
                }
            }
            var hasSectionAbove = folderRows.isNotEmpty()
            for ((key, title, files) in listOf(Triple("music", musicTitle, music), Triple("videos", videosTitle, videos))) {
                if (files.isEmpty()) continue
                section(key = key, title = title, state = sections, hasDividerAbove = hasSectionAbove) {
                    items(files, key = { "file:${it.id}" }) { file ->
                        val isCurrent = { preview?.trackId == file.id }
                        val isPlaying = { preview?.isPlaying == true }
                        val onPick = picking?.let { { it.toggle(listOf(file)) } }
                        val box = picking?.boxOf(listOf(file)) ?: Icons.Filled.CheckBoxOutlineBlank
                        val boxLabel = if (picking?.isPicked(listOf(file)) == true) unpickLabel else pickLabel
                        if (file.isVideo) {
                            SonaVideoRow(
                                video = file,
                                resumePositionMs = { 0L },
                                isCurrent = isCurrent,
                                isPlaying = isPlaying,
                                selection = null,
                                selectionKey = null,
                                onClick = { onPlay(file) },
                                onOpenOptions = onPick,
                                containerColor = rowColor,
                                optionsIcon = box,
                                optionsLabel = boxLabel,
                            )
                        } else {
                            SonaTrackRow(
                                track = file,
                                isCurrent = isCurrent,
                                isPlaying = isPlaying,
                                selection = null,
                                selectionKey = null,
                                onClick = { onPlay(file) },
                                onOpenOptions = onPick,
                                containerColor = rowColor,
                                optionsIcon = box,
                                optionsLabel = boxLabel,
                            )
                        }
                    }
                }
                hasSectionAbove = true
            }
        }
    }
}

/** A folder's row, as worked out once per folder open: its name, and the files in it and all inside it. */
private class FolderRowContent(val folder: FolderNode, val name: String, val files: List<Track>)

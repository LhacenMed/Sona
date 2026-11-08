package com.lhacenmed.sona.feature.debug.files

import android.content.Context
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.component.LocalBottomContentPadding
import com.lhacenmed.sona.core.designsystem.component.LocalDragSelection
import com.lhacenmed.sona.core.designsystem.component.SelectionState
import com.lhacenmed.sona.core.designsystem.component.SonaEmptyState
import com.lhacenmed.sona.core.designsystem.component.SonaListRow
import com.lhacenmed.sona.core.designsystem.component.SonaPathBar
import com.lhacenmed.sona.core.designsystem.component.dragSelection
import com.lhacenmed.sona.core.designsystem.component.fastscroll.FastScroller
import com.lhacenmed.sona.core.designsystem.component.rememberDragSelection
import com.lhacenmed.sona.core.designsystem.component.rememberSelectionState
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.debug.DebugRowIcon
import com.lhacenmed.sona.feature.debug.DebugSelectionBar
import com.lhacenmed.sona.feature.debug.DeleteConfirmation
import com.lhacenmed.sona.feature.debug.Loaded
import com.lhacenmed.sona.feature.debug.rememberLoaded
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** How much of a file is shown. */
private const val MAX_SHOWN_BYTES = 256 * 1024

private const val HEX_BYTES_PER_LINE = 16

private const val TOP_LEVEL_NAME = "App storage"

private data class FileEntry(val file: File, val sizeBytes: Long)

/** Where the app keeps anything of its own: its private folder, and its folders on shared storage. */
private fun rootsOf(context: Context): List<File> = listOfNotNull(
    File(context.applicationInfo.dataDir),
    context.getExternalFilesDir(null)?.parentFile,
)

private fun entriesIn(folder: File?, context: Context): List<FileEntry> =
    (if (folder == null) rootsOf(context) else folder.listFiles().orEmpty().toList())
        .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        .map { file -> FileEntry(file, file.walkTopDown().filter(File::isFile).sumOf(File::length)) }

/** The folders from one of [roots] down to [path], the open one last - none at the top. */
private fun foldersTo(path: String?, roots: List<File>): List<File> {
    val folder = path?.let(::File) ?: return emptyList()
    val root = roots.firstOrNull { folder == it || folder.startsWith(it) } ?: return listOf(folder)
    return generateSequence(folder) { it.parentFile.takeUnless { _ -> it == root } }.toList().asReversed()
}

/**
 * Everything the app keeps on storage, browsed in place: a folder opens here with the path above showing how deep it
 * is, and back climbs it - a file opens to be read. Rows are selected as any list's are, and deleted from the bar, a
 * folder with all it holds. Each folder keeps where it was scrolled to.
 */
data object FilesScreen : Screen {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val roots = remember { rootsOf(context) }
        val selection = rememberSelectionState()
        var path by rememberSaveable { mutableStateOf<String?>(null) }
        var version by remember { mutableIntStateOf(0) }
        var isConfirmingDelete by remember { mutableStateOf(false) }
        val folders = foldersTo(path, roots)
        var selectableKeys by remember { mutableStateOf(emptyList<String>()) }

        fun open(folder: String?) {
            selection.clear()
            path = folder
        }

        // After the bar's own, which a selection ends with first.
        BackHandler(enabled = path != null) { open(folders.dropLast(1).lastOrNull()?.path) }

        Column(Modifier.fillMaxSize()) {
            DebugSelectionBar(
                title = "Files",
                selection = selection,
                selectableKeys = selectableKeys,
                onDelete = { isConfirmingDelete = true },
            )
            SonaPathBar(
                levels = listOf(TOP_LEVEL_NAME) + folders.map { if (it in roots) it.path else it.name },
                onOpen = { level -> open(folders.getOrNull(level - 1)?.path) },
            )
            FolderContents(path, version, selection, onOpenFolder = ::open, onShown = { selectableKeys = it })
        }

        if (isConfirmingDelete) {
            DeleteConfirmation(
                what = "${selection.count} items",
                onDismiss = { isConfirmingDelete = false },
                delete = {
                    val selected = selection.selectedKeys.filterIsInstance<String>()
                    withContext(Dispatchers.IO) { selected.forEach { File(it).deleteRecursively() } }
                    selection.clear()
                    version++
                },
            )
        }
    }
}

@Composable
private fun FolderContents(
    path: String?,
    version: Int,
    selection: SelectionState,
    onOpenFolder: (String) -> Unit,
    onShown: (paths: List<String>) -> Unit,
) {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val listStates = remember { HashMap<String?, LazyListState>() }
    AnimatedContent(
        targetState = path,
        transitionSpec = {
            val direction = if (targetState.orEmpty().length >= initialState.orEmpty().length) 1 else -1
            slideInHorizontally { width -> direction * width } togetherWith slideOutHorizontally { width -> -direction * width }
        },
        label = "filesFolder",
    ) { shownPath ->
        // Each folder reads its own, so one on its way out keeps its rows as it slides.
        val entries = rememberLoaded(shownPath, version) { withContext(Dispatchers.IO) { entriesIn(shownPath?.let(::File), context) } }
        LaunchedEffect(entries, path) {
            if (shownPath == path) onShown(entries?.getOrNull().orEmpty().map { it.file.path })
        }
        Loaded(entries) { list ->
            if (list.isEmpty()) {
                SonaEmptyState(title = "Nothing here", message = "This folder is empty.")
                return@Loaded
            }
            val listState = listStates.getOrPut(shownPath) { LazyListState() }
            val dragSelection = rememberDragSelection(selection, listState, remember(list) { list.map { it.file.path } })
            CompositionLocalProvider(LocalDragSelection provides dragSelection) {
                FastScroller(listState, Modifier.fillMaxSize()) { overscrollEffect ->
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().dragSelection(dragSelection),
                        contentPadding = PaddingValues(bottom = LocalBottomContentPadding.current),
                        overscrollEffect = overscrollEffect,
                    ) {
                        items(list, key = { it.file.path }) { entry ->
                            val size = Formatter.formatShortFileSize(context, entry.sizeBytes)
                            SonaListRow(
                                title = if (shownPath == null) entry.file.path else entry.file.name,
                                subtitle = "$size - ${DateUtils.getRelativeTimeSpanString(entry.file.lastModified())}",
                                selection = selection,
                                selectionKey = entry.file.path,
                                onClick = {
                                    if (entry.file.isDirectory) onOpenFolder(entry.file.path) else navigator.go(FileContentScreen(entry.file.path))
                                },
                                onOpenOptions = null,
                                cover = { isSelected ->
                                    DebugRowIcon(if (entry.file.isDirectory) Icons.Filled.Folder else Icons.Filled.Description, isSelected)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** What a file holds: as text where it reads as text, as bytes otherwise, up to [MAX_SHOWN_BYTES]. */
internal data class FileContentScreen(val path: String) : Screen {
    override fun title(context: Context): String = path.substringAfterLast('/')

    @Composable
    override fun Content() {
        val content = rememberLoaded(path) { withContext(Dispatchers.IO) { shownContentOf(File(path)) } }
        Loaded(content) { text ->
            val scrollState = rememberScrollState()
            FastScroller(scrollState, Modifier.fillMaxSize()) { overscrollEffect ->
                Text(
                    text = text,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState, overscrollEffect = overscrollEffect)
                        .horizontalScroll(rememberScrollState())
                        .padding(16.dp),
                )
            }
        }
    }
}

private fun shownContentOf(file: File): String {
    val bytes = ByteArray(minOf(file.length(), MAX_SHOWN_BYTES.toLong()).toInt())
    file.inputStream().use { input ->
        var read = 0
        while (read < bytes.size) {
            val count = input.read(bytes, read, bytes.size - read)
            if (count < 0) break
            read += count
        }
    }
    val text = bytes.decodeToString()
    val readsAsText = text.none { it == '\uFFFD' || (it.isISOControl() && it !in "\n\r\t") }
    if (readsAsText) return text
    return bytes.asList().chunked(HEX_BYTES_PER_LINE).joinToString("\n") { line ->
        val hex = line.joinToString(" ") { "%02x".format(it) }
        val shown = line.joinToString("") { byte -> byte.toInt().toChar().takeIf { it.code in 32..126 }?.toString() ?: "." }
        "${hex.padEnd(HEX_BYTES_PER_LINE * 3)} $shown"
    }
}

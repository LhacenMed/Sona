package com.lhacenmed.sona.feature.explorer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.lhacenmed.sona.core.common.cover.rankedCoverArtUris
import com.lhacenmed.sona.core.model.FolderNode
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.core.model.folderTreeOf

/** The device's own storage, under the name Android gives it. */
private val InternalStorage = Regex("""/storage/emulated(/\d+)?""")

/** What [folder] is called where it is shown: its own name - or, for the device's storage itself, that. */
@Composable
internal fun folderDisplayName(folder: FolderNode): String =
    if (InternalStorage.matches(folder.path)) stringResource(R.string.explorer_internal_storage) else folder.name

/** [files] as the folders they lie in: the tree of them, and what each folder holds. */
@Stable
internal class ExploredFiles(files: List<Track>) {

    private val filesByFolder = files.groupBy { it.folderPath }
    val roots: List<FolderNode> = folderTreeOf(filesByFolder.keys)
    private val nodesByPath = roots.asSequence().flatMap { it.withEverythingInside() }.associateBy { it.path }
    private val parents = nodesByPath.values.flatMap { parent -> parent.folders.map { it.path to parent.path } }.toMap()
    private val filesInside = HashMap<String, List<Track>>()

    fun nodeOf(path: String): FolderNode? = nodesByPath[path]

    /** The folders inside [path] - or, for null, at the top. */
    fun foldersIn(path: String?): List<FolderNode> = if (path == null) roots else nodesByPath[path]?.folders.orEmpty()

    /** The files directly in [path] - none at the top. */
    fun filesIn(path: String?): List<Track> = path?.let { filesByFolder[it] }.orEmpty()

    /** The files in [folder] and every folder inside it. */
    fun filesInside(folder: FolderNode): List<Track> = filesInside.getOrPut(folder.path) {
        folder.withEverythingInside().flatMap { filesByFolder[it.path].orEmpty().asSequence() }.toList()
    }

    fun coversOf(folder: FolderNode): List<String> = rankedCoverArtUris(filesInside(folder).map { it.coverArtUri })

    /** The folders from the top down to [path], [path] last - none at the top. */
    fun pathTo(path: String?): List<FolderNode> = generateSequence(path, parents::get).mapNotNull(nodesByPath::get).toList().asReversed()
}

/**
 * Where an explorer is, and where it has been: Back goes to the folder before, as a browser goes back a page,
 * however that folder was reached - opened, or picked on the path.
 */
@Stable
internal class ExplorerNavigation(history: List<String?>) {

    var history by mutableStateOf(history)
        private set

    /** Whether the last move went back - for the folders to slide the way it went. */
    var wentBack by mutableStateOf(false)
        private set

    /** The folder open - null for the top. */
    val open: String? get() = history.last()

    val canGoBack: Boolean get() = history.size > 1

    fun open(path: String?) {
        if (path == open) return
        wentBack = false
        history = history + path
    }

    fun back() {
        wentBack = true
        history = history.dropLast(1)
    }
}

/** An [ExplorerNavigation] opened at [start] - null for the top - kept across recreation. */
@Composable
internal fun rememberExplorerNavigation(start: String?): ExplorerNavigation = rememberSaveable(
    saver = listSaver(save = { it.history }, restore = { ExplorerNavigation(it) }),
) { ExplorerNavigation(listOf(start)) }

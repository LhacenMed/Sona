package com.lhacenmed.sona.core.model

/**
 * What is picked among [files], by key - [keyPrefix] and a path: a folder's key picks every one of [files] directly
 * in it, a file's that file alone. Keys are kept in their shortest form: a folder whose files are all picked is
 * picked by its own key, never by theirs - so it stays picked as files join it later.
 *
 * Keys of other prefixes, or of paths none of [files] has, are left as they are: they are someone else's.
 */
class FolderPicks(files: Collection<Track>, private val keyPrefix: String = "") {

    private val filesByFolder = files.groupBy { it.folderPath }
    private val filePaths = files.mapTo(HashSet()) { it.path }

    fun keyOf(path: String): String = keyPrefix + path

    fun isPicked(file: Track, keys: Set<String>): Boolean = keyOf(file.folderPath) in keys || keyOf(file.path) in keys

    /** [keys] with [files] picked - or unpicked, where every one of them already was. */
    fun toggled(keys: Set<String>, files: Collection<Track>): Set<String> {
        val unpicks = files.all { isPicked(it, keys) }
        val result = keys.toMutableSet()
        val folders = files.mapTo(HashSet()) { it.folderPath }
        // A folder picked by its own key is picked file by file a moment, so some of its files can change alone.
        folders.forEach { folder -> if (result.remove(keyOf(folder))) filesByFolder[folder].orEmpty().mapTo(result) { keyOf(it.path) } }
        files.forEach { if (unpicks) result -= keyOf(it.path) else result += keyOf(it.path) }
        folders.forEach { folder ->
            val own = filesByFolder[folder].orEmpty()
            if (own.isNotEmpty() && own.all { keyOf(it.path) in result }) {
                own.forEach { result -= keyOf(it.path) }
                result += keyOf(folder)
            }
        }
        return result
    }

    /** The keys picking every file [keys] leaves unpicked - what is left out, from what is picked, and back again. */
    fun allBut(keys: Set<String>): Set<String> = filesByFolder.flatMapTo(mutableSetOf()) { (folder, own) ->
        val unpicked = own.filterNot { isPicked(it, keys) }
        when (unpicked.size) {
            0 -> emptyList()
            own.size -> listOf(keyOf(folder))
            else -> unpicked.map { keyOf(it.path) }
        }
    }

    /** Whether [key] is one of these files' or their folders' - none of the keys it leaves alone. */
    fun owns(key: String): Boolean =
        key.startsWith(keyPrefix) && key.removePrefix(keyPrefix).let { path -> path in filesByFolder || path in filePaths }
}

package com.lhacenmed.sona.core.model

/**
 * A folder as the device lays folders out: its [path], its [name], and the [folders] inside it - only those on
 * the way to a folder holding the library's files, so no folder listed is ever empty.
 */
data class FolderNode(val path: String, val name: String, val folders: List<FolderNode>) {

    /** This folder and every folder inside it, at any depth. */
    fun withEverythingInside(): Sequence<FolderNode> = sequenceOf(this) + folders.asSequence().flatMap { it.withEverythingInside() }
}

/** How the Folders tab lists folders that lie inside one another. */
enum class FolderView {
    /** Each folder holding files is listed alone - one inside another included. */
    SEPARATE,

    /** A folder holding files is listed with every one inside it, as one: the top-most of them stands for all. */
    GROUPED,
}

/**
 * The folders holding [paths], as a tree: from where they part - inside the device's storage, typically - down to
 * each of them, every folder on the way listed.
 */
fun folderTreeOf(paths: Collection<String>): List<FolderNode> {
    val listed = paths.toSet()
    val children = HashMap<String, MutableSet<String>>()
    for (path in listed) {
        var child = path
        while (child.isNotEmpty()) {
            val parent = child.substringBeforeLast('/', missingDelimiterValue = "")
            if (!children.getOrPut(parent, ::mutableSetOf).add(child)) break
            child = parent
        }
    }
    fun childrenOf(path: String) = children[path].orEmpty()
    fun nodeOf(path: String): FolderNode = FolderNode(
        path = path,
        name = path.substringAfterLast('/'),
        folders = childrenOf(path).map(::nodeOf).sortedBy { it.name.lowercase() },
    )
    // Where the folders part: below every folder they all lie in, which holds no file of its own.
    var split = ""
    while (split !in listed && childrenOf(split).size == 1) split = childrenOf(split).single()
    if (split in listed) return listOf(nodeOf(split))
    return childrenOf(split).map(::nodeOf).sortedBy { it.name.lowercase() }
}

/**
 * For each of [paths], the top-most of them it lies inside - itself, where it lies inside none: the folder it is
 * listed under when [FolderView.GROUPED].
 */
fun topMostFoldersOf(paths: Collection<String>): Map<String, String> {
    val listed = paths.toSet()
    return listed.associateWith { path ->
        var top = path
        var ancestor = path.substringBeforeLast('/', missingDelimiterValue = "")
        while (ancestor.isNotEmpty()) {
            if (ancestor in listed) top = ancestor
            ancestor = ancestor.substringBeforeLast('/', missingDelimiterValue = "")
        }
        top
    }
}

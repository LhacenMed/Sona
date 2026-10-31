package com.lhacenmed.sona.core.data

import com.lhacenmed.sona.core.model.folderTreeOf
import com.lhacenmed.sona.core.model.topMostFoldersOf
import org.junit.Assert.assertEquals
import org.junit.Test

class FolderTreeTest {

    private val storage = "/storage/emulated/0"

    @Test
    fun theTreeStarts_whereTheFoldersPart() {
        val tree = folderTreeOf(listOf("$storage/Music", "$storage/Music/Live", "$storage/Download"))
        assertEquals(listOf("Download", "Music"), tree.map { it.name })
        assertEquals(listOf("$storage/Music/Live"), tree.single { it.name == "Music" }.folders.map { it.path })
    }

    @Test
    fun foldersOnTheWay_withNoFilesOfTheirOwn_areListed() {
        val tree = folderTreeOf(listOf("$storage/Music/Albums/A", "$storage/Music/Albums/B", "$storage/Podcasts"))
        val albums = tree.single { it.name == "Music" }.folders.single()
        assertEquals("$storage/Music/Albums", albums.path)
        assertEquals(listOf("A", "B"), albums.folders.map { it.name })
    }

    @Test
    fun aSingleFolder_isTheWholeTree() {
        val tree = folderTreeOf(listOf("$storage/Music"))
        assertEquals(listOf("$storage/Music"), tree.map { it.path })
    }

    @Test
    fun everyFolderInside_isReached() {
        val tree = folderTreeOf(listOf("$storage/a", "$storage/a/b", "$storage/a/b/c", "$storage/d"))
        assertEquals(setOf("$storage/a", "$storage/a/b", "$storage/a/b/c"), tree.single { it.name == "a" }.withEverythingInside().map { it.path }.toSet())
    }

    @Test
    fun aFolder_isGroupedUnderTheTopMostHoldingFiles() {
        val tops = topMostFoldersOf(listOf("$storage/Music", "$storage/Music/Live", "$storage/Music/Live/2019", "$storage/Music Videos"))
        assertEquals("$storage/Music", tops["$storage/Music/Live/2019"])
        assertEquals("$storage/Music", tops["$storage/Music/Live"])
        // A name that merely starts the same is another folder.
        assertEquals("$storage/Music Videos", tops["$storage/Music Videos"])
    }

    @Test
    fun foldersWithNoListedFolderAbove_standForThemselves() {
        val tops = topMostFoldersOf(listOf("$storage/Music/A", "$storage/Music/B"))
        assertEquals("$storage/Music/A", tops["$storage/Music/A"])
        assertEquals("$storage/Music/B", tops["$storage/Music/B"])
    }
}

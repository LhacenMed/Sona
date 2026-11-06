package com.lhacenmed.sona.feature.settings.search

import com.lhacenmed.sona.feature.settings.R
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/** Where the settings screens are written, from this module's own directory - where its tests run. */
private val SettingsScreensSources = File("src/main/kotlin/com/lhacenmed/sona/feature/settings")

/** The folders of that holding no screen's rows: the rows themselves, and the search. */
private val NotScreens = setOf("component", "search")

/** A settings row, as a screen writes it: its kind of row, then its title. */
private val SettingsRow = Regex("""Settings(?:Switch|Navigation|Choice|Slider|Action|Download|TextField|Link)Item\(\s*title = stringResource\(R\.string\.(\w+)\)""")

/** Rows no search needs to find: shown only after something failed, they set nothing. */
private val Unsearched = setOf("restore_drive_failed")

class SettingsSearchEntriesTest {

    /** A row added to a settings screen without being added to [SettingsSearchEntries] is one no search would find. */
    @Test
    fun everySettingsRowIsSearchable() {
        val titles = SettingsScreensSources.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.relativeTo(SettingsScreensSources).path.substringBefore(File.separatorChar) !in NotScreens }
            .flatMap { file -> SettingsRow.findAll(file.readText()).map { it.groupValues[1] } }
            .toSet()
        val namesById = R.string::class.java.fields.associate { it.getInt(null) to it.name }
        val searchable = SettingsSearchEntries.mapTo(HashSet()) { namesById.getValue(it.titleRes) }

        assertEquals(emptySet<String>(), titles - searchable - Unsearched)
    }
}

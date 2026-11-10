@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.lhacenmed.sona.feature.debug.mediastore

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.theme.buttonPressShapes
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.debug.DataTable
import com.lhacenmed.sona.feature.debug.Loaded
import com.lhacenmed.sona.feature.debug.PAGE_SIZE
import com.lhacenmed.sona.feature.debug.PageBar
import com.lhacenmed.sona.feature.debug.RowPage
import com.lhacenmed.sona.feature.debug.TableRow
import com.lhacenmed.sona.feature.debug.cellText
import com.lhacenmed.sona.feature.debug.rememberLoaded
import com.lhacenmed.sona.core.designsystem.component.settings.SettingsList
import com.lhacenmed.sona.core.designsystem.component.settings.SettingsNavigationItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The collections of Android's own media database the scanner reads. */
private enum class MediaCollection(val title: String, val uri: Uri) {
    AUDIO("Audio", MediaStore.Audio.Media.EXTERNAL_CONTENT_URI),
    VIDEO("Video", MediaStore.Video.Media.EXTERNAL_CONTENT_URI),
    ALBUMS("Albums", MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI),
    ARTISTS("Artists", MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI),
    GENRES("Genres", MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI),
}

/** What the scanner is given, to set beside what it made of it - so a file's own tags can be told from the library's reading of them. */
data object MediaStoreScreen : Screen {
    override fun title(context: Context): String = "MediaStore"

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        SettingsList {
            MediaCollection.entries.forEach { collection ->
                SettingsNavigationItem(
                    title = collection.title,
                    summary = collection.uri.toString(),
                    onClick = { navigator.go(MediaCollectionScreen(collection)) },
                )
            }
        }
    }
}

/** A collection's rows, read-only: they are Android's, and the scanner would only read a change back. */
private data class MediaCollectionScreen(val collection: MediaCollection) : Screen {
    override fun title(context: Context): String = collection.title

    @Composable
    override fun Content() {
        val context = LocalContext.current
        var filterDraft by rememberSaveable { mutableStateOf("") }
        var filter by rememberSaveable { mutableStateOf("") }
        var offset by rememberSaveable { mutableIntStateOf(0) }
        val result = rememberLoaded(filter, offset) {
            withContext(Dispatchers.IO) { pageOf(context, collection.uri, filter, offset) }
        }
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = filterDraft,
                    onValueChange = { filterDraft = it },
                    label = { Text("WHERE") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(shapes = buttonPressShapes(), onClick = { filter = filterDraft; offset = 0 }) { Text("Apply") }
            }
            Loaded(result) { page ->
                PageBar(offset, page.rows.size, page.hasMore) { offset = it }
                DataTable(page)
            }
        }
    }
}

private fun pageOf(context: Context, uri: Uri, filter: String, offset: Int): RowPage {
    val cursor = context.contentResolver.query(uri, null, filter.ifBlank { null }, null, null) ?: return RowPage(emptyList(), emptyList(), false)
    return cursor.use {
        val rows = buildList {
            if (it.moveToPosition(offset - 1) || offset == 0) {
                while (size < PAGE_SIZE && it.moveToNext()) add(TableRow(null, it.columnNames.indices.map(it::cellText)))
            }
        }
        RowPage(it.columnNames.toList(), rows, hasMore = it.count > offset + rows.size)
    }
}

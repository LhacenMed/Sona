package com.lhacenmed.sona.feature.explorer

import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.theme.buttonPressShapes
import com.lhacenmed.sona.core.model.FolderNode

/**
 * Where the open folder lies: the top, then every folder down to it - [folders], the open one last - each a
 * button that opens it, pressed as every button of the app is. The open folder stands out, filled; the path is
 * scrolled to show it whenever it changes.
 *
 * It scrolls with no overscroll: the app's rubber band, stretched by a drag at the path's end, would pull against
 * the pager a sheet lays its tabs out in - the glitch the tabs once had.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FolderPathBar(folders: List<FolderNode>, onOpen: (path: String?) -> Unit, modifier: Modifier = Modifier) {
    val scrollState = rememberScrollState()
    LaunchedEffect(folders) { scrollState.animateScrollTo(scrollState.maxValue) }
    CompositionLocalProvider(LocalOverscrollFactory provides null) {
        Row(
            modifier = modifier.fillMaxWidth().horizontalScroll(scrollState).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PathButton(stringResource(R.string.explorer_all_folders), isOpen = folders.isEmpty(), onClick = { onOpen(null) })
            folders.forEachIndexed { index, folder ->
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                PathButton(folderDisplayName(folder), isOpen = index == folders.lastIndex, onClick = { onOpen(folder.path) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PathButton(label: String, isOpen: Boolean, onClick: () -> Unit) {
    val text = @Composable { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    if (isOpen) {
        FilledTonalButton(onClick = onClick, shapes = buttonPressShapes()) { text() }
    } else {
        TextButton(onClick = onClick, shapes = buttonPressShapes()) { text() }
    }
}

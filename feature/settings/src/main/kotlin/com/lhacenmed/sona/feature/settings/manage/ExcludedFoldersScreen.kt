package com.lhacenmed.sona.feature.settings.manage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.designsystem.component.LocalBottomContentPadding
import com.lhacenmed.sona.core.designsystem.component.SonaBottomSheet
import com.lhacenmed.sona.core.designsystem.component.screen.screenList
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import com.lhacenmed.sona.core.designsystem.theme.buttonPressShapes
import com.lhacenmed.sona.core.designsystem.theme.iconButtonPressShapes
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.settings.R

/**
 * "Excluded Folders" screen: view/add/remove folders that the media scanner should skip - their music and
 * videos alike. A folder is added from the ones the library found media in, never picked out of the device's
 * whole storage.
 */
data object ExcludedFoldersScreen : Screen {
    override val titleRes: Int get() = R.string.excluded_folders_title

    @Composable
    override fun Content() {
        val viewModel: ExcludedFoldersViewModel = hiltViewModel()
        val excludedFolders by viewModel.excludedFolders.collectAsStateWithLifecycle()
        var isPickingFolder by remember { mutableStateOf(false) }

        // Box keeps the screen's root layout shape identical whether showing the empty state or
        // the list, so switching between the two never reflows surrounding chrome.
        Box(modifier = Modifier.fillMaxSize()) {
            if (excludedFolders.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.excluded_folders_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.excluded_folders_empty_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = { isPickingFolder = true },
                        shapes = buttonPressShapes(),
                        modifier = Modifier.padding(top = 16.dp),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text(text = stringResource(R.string.excluded_folders_add), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            } else {
                // The add button stays put at the bottom, so the whole column ends above the mini player.
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = LocalBottomContentPadding.current),
                ) {
                    val listState = rememberLazyListState()
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .screenList(listState),
                    ) {
                        items(excludedFolders) { path ->
                            ListItem(
                                headlineContent = { Text(path) },
                                trailingContent = {
                                    IconButton(onClick = { viewModel.removeFolder(path) }, shapes = iconButtonPressShapes()) {
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = stringResource(R.string.excluded_folders_remove),
                                        )
                                    }
                                },
                            )
                            HorizontalDivider()
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Button(onClick = { isPickingFolder = true }, shapes = buttonPressShapes()) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Text(text = stringResource(R.string.excluded_folders_add), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }

        if (isPickingFolder) {
            val excludableFolders by viewModel.excludableFolders.collectAsStateWithLifecycle()
            ExcludableFoldersSheet(
                folders = excludableFolders,
                onFolderChosen = viewModel::addFolder,
                onDismissRequest = { isPickingFolder = false },
            )
        }
    }
}

/** The folders holding music or videos, one row each - a press excludes it and closes the sheet. */
@Composable
private fun ExcludableFoldersSheet(folders: List<String>, onFolderChosen: (String) -> Unit, onDismissRequest: () -> Unit) {
    SonaBottomSheet(title = stringResource(R.string.excluded_folders_add), onDismissRequest = onDismissRequest) {
        if (folders.isEmpty()) {
            Text(
                text = stringResource(R.string.excluded_folders_none_left),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            )
        }
        folders.forEach { path ->
            ListItem(
                headlineContent = { Text(path.substringAfterLast('/')) },
                supportingContent = { Text(path) },
                leadingContent = { Icon(SonaIcons.Folder, contentDescription = null, modifier = Modifier.size(24.dp)) },
                // The sheet already paints its own background; a row painting its own would seam against it.
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable {
                    onFolderChosen(path)
                    dismiss()
                },
            )
        }
    }
}

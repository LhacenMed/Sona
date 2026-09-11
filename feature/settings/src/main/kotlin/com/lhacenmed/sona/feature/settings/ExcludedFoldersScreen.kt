package com.lhacenmed.sona.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.common.storage.documentPathOrNull
import com.lhacenmed.sona.core.navigation.Screen

/** "Excluded Folders" screen: view/add/remove folders that the media scanner should skip. */
object ExcludedFoldersScreen : Screen {
    override val titleRes: Int get() = R.string.excluded_folders_title

    @Composable
    override fun Content() {
        val viewModel: ExcludedFoldersViewModel = hiltViewModel()
        val excludedFolders by viewModel.excludedFolders.collectAsStateWithLifecycle()

        val pickFolderLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
        ) { uri: Uri? ->
            if (uri != null) {
                documentPathOrNull(uri, isTree = true)?.let(viewModel::addFolder)
            }
        }

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
                        onClick = { pickFolderLauncher.launch(null) },
                        modifier = Modifier.padding(top = 16.dp),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text(text = stringResource(R.string.excluded_folders_add), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(excludedFolders) { path ->
                            ListItem(
                                headlineContent = { Text(path) },
                                trailingContent = {
                                    IconButton(onClick = { viewModel.removeFolder(path) }) {
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
                        Button(onClick = { pickFolderLauncher.launch(null) }) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Text(text = stringResource(R.string.excluded_folders_add), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

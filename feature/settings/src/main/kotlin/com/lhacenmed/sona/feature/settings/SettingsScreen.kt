package com.lhacenmed.sona.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen

/** Settings home: a simple menu of settings sub-screens. */
object SettingsScreen : Screen {
    override val titleRes: Int get() = R.string.settings_title

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current

        Column(modifier = Modifier.fillMaxSize()) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_excluded_folders)) },
                leadingContent = { Icon(Icons.Filled.Folder, contentDescription = null) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null) },
                modifier = Modifier.clickable { navigator.go(ExcludedFoldersScreen) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_manage_tabs)) },
                leadingContent = { Icon(Icons.Filled.Tab, contentDescription = null) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null) },
                modifier = Modifier.clickable { navigator.go(TabVisibilityScreen) },
            )
        }
    }
}

package com.lhacenmed.sona.feature.settings.screen

import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.designsystem.component.dialog.SonaConfirmationDialog
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.settings.R
import com.lhacenmed.sona.feature.settings.component.SettingsActionItem
import com.lhacenmed.sona.feature.settings.component.SettingsInfoItem
import com.lhacenmed.sona.feature.settings.component.SettingsList
import com.lhacenmed.sona.feature.settings.component.SettingsNote
import com.lhacenmed.sona.feature.settings.component.SettingsSection
import com.lhacenmed.sona.feature.settings.component.SettingsSectionDivider

/**
 * What the app keeps on disk beyond the music itself: the library it built from the device's files, and
 * the images it downloaded - avatars, on About and Updates. Covers are read from the music as they are
 * shown, so there is no cover cache to manage.
 */
data object StorageScreen : Screen {
    override val titleRes: Int get() = R.string.storage_title

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val viewModel: StorageViewModel = hiltViewModel()
        val databaseBytes by viewModel.databaseBytes.collectAsStateWithLifecycle()
        val imageCacheBytes by viewModel.imageCacheBytes.collectAsStateWithLifecycle()
        val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
        var showClearImageCacheDialog by rememberSaveable { mutableStateOf(false) }
        val sizeOf = { bytes: Long? -> bytes?.let { Formatter.formatShortFileSize(context, it) }.orEmpty() }

        SettingsList {
            SettingsSection(stringResource(R.string.storage_library_section)) {
                SettingsInfoItem(
                    title = stringResource(R.string.storage_database_used_title),
                    value = sizeOf(databaseBytes),
                )
                SettingsActionItem(
                    title = stringResource(R.string.storage_rescan_title),
                    summary = stringResource(if (isScanning) R.string.storage_rescanning else R.string.storage_rescan_summary),
                    onClick = viewModel::rescan,
                    enabled = !isScanning,
                )
            }

            SettingsSectionDivider()

            SettingsSection(stringResource(R.string.storage_image_cache_section)) {
                SettingsInfoItem(
                    title = stringResource(R.string.storage_image_cache_used_title),
                    value = sizeOf(imageCacheBytes),
                )
                SettingsActionItem(
                    title = stringResource(R.string.storage_clear_image_cache_title),
                    summary = stringResource(R.string.storage_clear_image_cache_summary),
                    onClick = { showClearImageCacheDialog = true },
                )
                SettingsNote(stringResource(R.string.storage_note))
            }
        }

        if (showClearImageCacheDialog) {
            SonaConfirmationDialog(
                title = stringResource(R.string.storage_clear_image_cache_title),
                message = stringResource(R.string.storage_clear_image_cache_confirm),
                confirmLabel = stringResource(R.string.storage_clear_action),
                successMessage = stringResource(R.string.storage_clear_image_cache_done),
                failureMessage = stringResource(R.string.storage_clear_image_cache_failed),
                onDismiss = { showClearImageCacheDialog = false },
                operation = viewModel::clearImageCache,
            )
        }
    }
}

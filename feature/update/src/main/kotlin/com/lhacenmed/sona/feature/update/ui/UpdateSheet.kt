package com.lhacenmed.sona.feature.update.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.component.SonaBottomSheet
import com.lhacenmed.sona.core.designsystem.theme.buttonPressShapes
import com.lhacenmed.sona.feature.update.R
import com.lhacenmed.sona.feature.update.UpdateRegistry
import com.lhacenmed.sona.feature.update.UpdateState
import com.lhacenmed.sona.feature.update.github.Release
import com.lhacenmed.sona.feature.update.github.ReleaseApk
import com.lhacenmed.sona.feature.update.installedBuild

/**
 * A newer version, and what it brings - ArchiveTune's update sheet: its version, its notes, which of its APKs
 * to update with, and the button that updates to it. [onUpdate] is called with the sheet already on its way
 * out, and the APK chosen - null for a release with none to choose from.
 */
@Composable
fun NewUpdateSheet(
    release: Release,
    onUpdate: (ReleaseApk?) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    var selectedApk by remember(release) { mutableStateOf(release.initialApk(context)) }

    SonaBottomSheet(
        onDismissRequest = onDismissRequest,
        header = {
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                Text(
                    text = stringResource(R.string.update_new_available),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {},
                    contentPadding = PaddingValues(horizontal = 5.dp, vertical = 5.dp),
                    shapes = buttonPressShapes(),
                ) {
                    Text(text = release.versionName, style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(12.dp))
            }
        },
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            val notes = release.notes
            if (notes.isNullOrBlank()) {
                Text(text = stringResource(R.string.update_notes_unavailable), style = MaterialTheme.typography.bodyMedium)
            } else {
                ReleaseNotes(markdown = notes, modifier = Modifier.fillMaxWidth().padding(end = 8.dp))
            }
            Spacer(Modifier.height(12.dp))
            selectedApk?.let { apk ->
                ApkVariantPicker(
                    apks = release.apks,
                    selected = apk,
                    onSelect = { selectedApk = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
            }
            Button(
                onClick = {
                    dismiss()
                    onUpdate(selectedApk)
                },
                shapes = buttonPressShapes(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.update_action))
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** The APK the sheet opens on: the one already downloaded for this release, else the one for the running build. */
private fun Release.initialApk(context: Context): ReleaseApk? {
    val staged = (UpdateRegistry.stateOf() as? UpdateState.Downloaded)?.staged?.takeIf { it.versionName == versionName }
    return staged?.variant?.let(::apkOf) ?: apkFor(context.installedBuild())
}

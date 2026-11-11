package com.lhacenmed.sona.feature.debug

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.lhacenmed.sona.core.navigation.DebugTools
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.debug.database.DatabasesScreen
import com.lhacenmed.sona.feature.debug.files.FilesScreen
import com.lhacenmed.sona.feature.debug.logs.LogsScreen
import com.lhacenmed.sona.feature.debug.mediastore.MediaStoreScreen
import com.lhacenmed.sona.feature.debug.preferences.PreferencesScreen
import com.lhacenmed.sona.feature.debug.runtime.RuntimeScreen
import com.lhacenmed.sona.core.designsystem.component.settings.SettingsList
import com.lhacenmed.sona.core.designsystem.component.settings.SettingsNavigationItem
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons

/** Every part of the debug tools: a screen added here is one the tools offer. */
private enum class DebugSection(val title: String, val summary: String, val icon: ImageVector, val screen: Screen) {
    RUNTIME("Runtime", "App, permissions, playback, scanner, backup and background work, live", SonaIcons.Monitor, RuntimeScreen),
    DATABASES("Databases", "Every database: tables, rows and SQL - to read and to change", SonaIcons.Storage, DatabasesScreen),
    PREFERENCES("Preferences", "Every settings file and shared preferences file - to read and to change", SonaIcons.Settings, PreferencesScreen),
    FILES("Files", "Everything the app keeps on storage, caches included - to read and to delete", SonaIcons.Folder, FilesScreen),
    MEDIA_STORE("MediaStore", "What Android itself says of the device's files, before the scanner reads it", SonaIcons.LibraryMusic, MediaStoreScreen),
    LOGS("Logs", "What this process has logged", SonaIcons.Subject, LogsScreen),
}

/** The debug tools' home: everything behind how the app behaves, to look into and to change. */
data object DebugToolsScreen : Screen {
    override fun title(context: Context): String = "Debug tools"

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        SettingsList {
            DebugSection.entries.forEach { section ->
                SettingsNavigationItem(
                    title = section.title,
                    summary = section.summary,
                    icon = section.icon,
                    onClick = { navigator.go(section.screen) },
                )
            }
        }
    }
}

internal class SonaDebugTools @Inject constructor() : DebugTools {
    override val screen: Screen = DebugToolsScreen
    override val summary = "Databases, stored data, files, logs and live state"
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DebugToolsModule {
    @Binds
    abstract fun bindDebugTools(tools: SonaDebugTools): DebugTools
}

package com.lhacenmed.sona.feature.debug.preferences

import android.content.Context
import androidx.core.content.edit
import com.lhacenmed.sona.core.common.di.IoDispatcher
import com.lhacenmed.sona.core.datastore.SettingsInspector
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal enum class PreferenceKind(val label: String) {
    SETTINGS("Settings"),
    SHARED("Shared preferences"),
}

internal data class PreferenceFile(val kind: PreferenceKind, val name: String, val values: Map<String, Any>)

/**
 * Every key-value file the app keeps, as one list: the settings files, and every shared preferences file found on
 * storage, whichever part of the app made it. Values are changed as the kind they already are.
 *
 * Backup's own settings file is not among them: it is private to the backup, whose state is on the runtime screen, and
 * its key is sealed. It shows under Files.
 */
@Singleton
internal class PreferencesInspector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsInspector,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun files(): List<PreferenceFile> = withContext(ioDispatcher) {
        settings.read().map { (name, values) -> PreferenceFile(PreferenceKind.SETTINGS, name, values) } +
            sharedPreferenceNames().map { name ->
                PreferenceFile(PreferenceKind.SHARED, name, shared(name).all.filterValues { it != null }.mapValues { it.value!! })
            }
    }

    /** Stores [text] under [key], as the kind [current] is - throwing where it cannot be read as that. */
    suspend fun set(file: PreferenceFile, key: String, current: Any, text: String) = withContext(ioDispatcher) {
        val value = parsed(text, current)
        when (file.kind) {
            PreferenceKind.SETTINGS -> settings.set(file.name, key, value)
            PreferenceKind.SHARED -> shared(file.name).edit(commit = true) {
                @Suppress("UNCHECKED_CAST")
                when (value) {
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Float -> putFloat(key, value)
                    is String -> putString(key, value)
                    is Set<*> -> putStringSet(key, value as Set<String>)
                }
            }
        }
    }

    suspend fun remove(file: PreferenceFile, key: String) = withContext(ioDispatcher) {
        when (file.kind) {
            PreferenceKind.SETTINGS -> settings.remove(file.name, key)
            PreferenceKind.SHARED -> shared(file.name).edit(commit = true) { remove(key) }
        }
    }

    private fun shared(name: String) = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    private fun sharedPreferenceNames(): List<String> =
        File(context.applicationInfo.dataDir, "shared_prefs").listFiles().orEmpty()
            .filter { it.extension == "xml" }
            .map { it.nameWithoutExtension }
            .sorted()
}

/** A set is written a value to a line. */
internal fun preferenceText(value: Any): String = if (value is Set<*>) value.joinToString("\n") else value.toString()

private fun parsed(text: String, current: Any): Any = when (current) {
    is Boolean -> text.trim().toBooleanStrict()
    is Int -> text.trim().toInt()
    is Long -> text.trim().toLong()
    is Float -> text.trim().toFloat()
    is Set<*> -> text.lines().filter(String::isNotEmpty).toSet()
    else -> text
}

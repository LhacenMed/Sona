package com.lhacenmed.sona.core.datastore

import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * One stored setting, as the rest of the app sees it.
 *
 * [value] is read from memory, so a screen, the player or the notification starts from what the user
 * chose - or from the setting's default if they never changed it - the same way it would start from
 * a hard-coded default: nothing to wait for, and nothing to correct a frame later. [flow] follows the
 * setting from then on, and only emits when this setting changes, not when a neighbour in the same
 * file does.
 *
 * Both rely on [SettingsLoader] having loaded every settings file as the process starts.
 */
class Setting<T> internal constructor(
    private val preferences: StateFlow<Preferences?>,
    private val read: (Preferences) -> T,
) {
    val value: T
        get() = read(checkNotNull(preferences.value) { "A setting was read before SettingsLoader loaded it" })

    val flow: Flow<T> = preferences.filterNotNull().map(read).distinctUntilChanged()
}

/**
 * The setting as a screen holds it: starting from [Setting.value], so the first frame already shows
 * what is stored, and followed for as long as anything is watching - and a few seconds past that, so a
 * configuration change does not start it over.
 */
fun <T> Setting<T>.stateIn(scope: CoroutineScope): StateFlow<T> =
    flow.stateIn(scope, SharingStarted.WhileSubscribed(5_000), value)

/** The enum stored under [key] by its name, or [default] when none is - or one no longer offered is. */
internal inline fun <reified E : Enum<E>> Preferences.enum(key: Preferences.Key<String>, default: E): E =
    this[key]?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default

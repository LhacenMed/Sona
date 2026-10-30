package com.lhacenmed.sona.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

// Every settings file the app keeps, declared in one place: a file added here is one a backup carries,
// rather than one a list somewhere else has to be remembered for.

internal val Context.libraryDataStore by preferencesDataStore(name = "library_settings")
/** Shared with [ShuffleSettings] and [QuickPlaySettings], so whether shuffle is on stays stored where it always was. */
internal val Context.playbackDataStore by preferencesDataStore(name = "playback_settings")
internal val Context.themeDataStore by preferencesDataStore(name = "theme_settings")
internal val Context.equalizerDataStore by preferencesDataStore(name = "equalizer_settings")
internal val Context.sortDataStore by preferencesDataStore(name = "sort_settings")
internal val Context.imageDataStore by preferencesDataStore(name = "image_settings")
internal val Context.playerStyleDataStore by preferencesDataStore(name = "player_style_settings")
internal val Context.lyricsDataStore by preferencesDataStore(name = "lyrics_settings")
internal val Context.updateDataStore by preferencesDataStore(name = "update_settings")
internal val Context.effectDataStore by preferencesDataStore(name = "effect_settings")
internal val Context.videoDataStore by preferencesDataStore(name = "video_settings")
/** The scanner's bookkeeping rather than anything the user chose, so the one file a backup leaves out. */
internal val Context.scanDataStore by preferencesDataStore(name = "scan_state")

/** Every settings file a backup carries, by the name it is stored under. */
internal fun Context.backedUpSettingsFiles(): Map<String, DataStore<Preferences>> = mapOf(
    "library_settings" to libraryDataStore,
    "playback_settings" to playbackDataStore,
    "theme_settings" to themeDataStore,
    "equalizer_settings" to equalizerDataStore,
    "sort_settings" to sortDataStore,
    "image_settings" to imageDataStore,
    "player_style_settings" to playerStyleDataStore,
    "lyrics_settings" to lyricsDataStore,
    "update_settings" to updateDataStore,
    "effect_settings" to effectDataStore,
    "video_settings" to videoDataStore,
)

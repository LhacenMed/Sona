package com.lhacenmed.sona.core.datastore

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Puts every user setting in memory before anything can read one.
 *
 * The application runs [load] first, ahead of any activity, service or receiver. That is what lets
 * every [Setting.value] be read synchronously: the app starts already configured, the way it would
 * start with defaults, rather than starting with defaults and then applying the user's choices on
 * top - which is what used to show every library tab for a moment before hiding the excluded ones.
 *
 * Each file began loading when its class was created, so they load concurrently and this waits for
 * the slowest one, not for their sum. [ScanSettings] is not here: it is scanner bookkeeping that
 * nothing draws, and the scanner already waits for it.
 */
@Singleton
class SettingsLoader @Inject constructor(
    private val librarySettings: LibrarySettings,
    private val playbackSettings: PlaybackSettings,
    private val shuffleSettings: ShuffleSettings,
    private val themeSettings: ThemeSettings,
    private val equalizerSettings: EqualizerSettings,
    private val sortSettings: SortSettings,
    private val imageSettings: ImageSettings,
    private val playerStyleSettings: PlayerStyleSettings,
    private val lyricsSettings: LyricsSettings,
    private val updateSettings: UpdateSettings,
) {
    suspend fun load() {
        librarySettings.awaitLoaded()
        playbackSettings.awaitLoaded()
        shuffleSettings.awaitLoaded()
        themeSettings.awaitLoaded()
        equalizerSettings.awaitLoaded()
        sortSettings.awaitLoaded()
        imageSettings.awaitLoaded()
        playerStyleSettings.awaitLoaded()
        lyricsSettings.awaitLoaded()
        updateSettings.awaitLoaded()
    }
}

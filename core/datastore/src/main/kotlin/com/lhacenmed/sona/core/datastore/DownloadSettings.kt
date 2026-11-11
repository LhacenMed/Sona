package com.lhacenmed.sona.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.lhacenmed.sona.core.common.di.ApplicationScope
import com.lhacenmed.sona.core.model.DownloadChoices
import com.lhacenmed.sona.core.model.DownloaderPreferences
import com.lhacenmed.sona.core.model.SponsorSegment
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

private val KIND = stringPreferencesKey("download_kind")
private val VIDEO_QUALITY = stringPreferencesKey("download_video_quality")
private val VIDEO_FORMAT = stringPreferencesKey("download_video_format")
private val AUDIO_FORMAT = stringPreferencesKey("download_audio_format")
private val CAPTIONS = booleanPreferencesKey("download_captions")
private val SKIP_SPONSORS = booleanPreferencesKey("download_skip_sponsors")

private val DOWNLOADS_AT_ONCE = intPreferencesKey("download_at_once")
private val WIFI_ONLY = booleanPreferencesKey("download_wifi_only")
private val FOLDER_NAME = stringPreferencesKey("download_folder_name")
private val FILE_NAME_TEMPLATE = stringPreferencesKey("download_file_name_template")
private val SPONSOR_SEGMENTS = stringSetPreferencesKey("download_sponsor_segments")
private val PROXY = stringPreferencesKey("download_proxy")
private val RATE_LIMIT = stringPreferencesKey("download_rate_limit")
private val USES_COOKIES = booleanPreferencesKey("download_uses_cookies")
private val USES_ARCHIVE = booleanPreferencesKey("download_uses_archive")

/** How the user downloads - what a Quick download takes, and the download screen starts from - and how downloading works. */
@Singleton
class DownloadSettings @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope,
) {
    private val dataStore = context.downloadDataStore
    private val cache = PreferencesCache(dataStore, scope)

    internal suspend fun awaitLoaded() = cache.awaitLoaded()

    val choices: Setting<DownloadChoices> = cache.setting { preferences ->
        val defaults = DownloadChoices()
        DownloadChoices(
            kind = preferences.enum(KIND, defaults.kind),
            videoQuality = preferences.enum(VIDEO_QUALITY, defaults.videoQuality),
            videoFormat = preferences.enum(VIDEO_FORMAT, defaults.videoFormat),
            audioFormat = preferences.enum(AUDIO_FORMAT, defaults.audioFormat),
            captions = preferences[CAPTIONS] ?: defaults.captions,
            skipSponsors = preferences[SKIP_SPONSORS] ?: defaults.skipSponsors,
        )
    }

    val preferences: Setting<DownloaderPreferences> = cache.setting { preferences ->
        val defaults = DownloaderPreferences()
        DownloaderPreferences(
            downloadsAtOnce = preferences[DOWNLOADS_AT_ONCE] ?: defaults.downloadsAtOnce,
            wifiOnly = preferences[WIFI_ONLY] ?: defaults.wifiOnly,
            folderName = preferences[FOLDER_NAME] ?: defaults.folderName,
            fileNameTemplate = preferences[FILE_NAME_TEMPLATE] ?: defaults.fileNameTemplate,
            sponsorSegments = preferences[SPONSOR_SEGMENTS]
                ?.mapNotNull { id -> SponsorSegment.entries.firstOrNull { it.id == id } }?.toSet()
                ?: defaults.sponsorSegments,
            proxy = preferences[PROXY] ?: defaults.proxy,
            rateLimit = preferences[RATE_LIMIT] ?: defaults.rateLimit,
            usesCookies = preferences[USES_COOKIES] ?: defaults.usesCookies,
            usesArchive = preferences[USES_ARCHIVE] ?: defaults.usesArchive,
        )
    }

    suspend fun setChoices(choices: DownloadChoices) {
        dataStore.edit {
            it[KIND] = choices.kind.name
            it[VIDEO_QUALITY] = choices.videoQuality.name
            it[VIDEO_FORMAT] = choices.videoFormat.name
            it[AUDIO_FORMAT] = choices.audioFormat.name
            it[CAPTIONS] = choices.captions
            it[SKIP_SPONSORS] = choices.skipSponsors
        }
    }

    /** Changes the preferences as [change] makes them from what they are. */
    suspend fun updatePreferences(change: (DownloaderPreferences) -> DownloaderPreferences) {
        dataStore.edit { it.write(change(preferences.value)) }
    }

    private fun MutablePreferences.write(preferences: DownloaderPreferences) {
        this[DOWNLOADS_AT_ONCE] = preferences.downloadsAtOnce
        this[WIFI_ONLY] = preferences.wifiOnly
        this[FOLDER_NAME] = preferences.folderName
        this[FILE_NAME_TEMPLATE] = preferences.fileNameTemplate
        this[SPONSOR_SEGMENTS] = preferences.sponsorSegments.map { it.id }.toSet()
        this[PROXY] = preferences.proxy
        this[RATE_LIMIT] = preferences.rateLimit
        this[USES_COOKIES] = preferences.usesCookies
        this[USES_ARCHIVE] = preferences.usesArchive
    }
}

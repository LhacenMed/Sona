package com.lhacenmed.sona.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lhacenmed.sona.core.common.di.ApplicationScope
import com.lhacenmed.sona.core.model.DownloadChoices
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

private val KIND = stringPreferencesKey("download_kind")
private val VIDEO_QUALITY = stringPreferencesKey("download_video_quality")
private val AUDIO_FORMAT = stringPreferencesKey("download_audio_format")
private val CAPTIONS = booleanPreferencesKey("download_captions")
private val SKIP_SPONSORS = booleanPreferencesKey("download_skip_sponsors")

/** How the user downloads - what a Quick download takes, and the download screen starts from. */
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
            audioFormat = preferences.enum(AUDIO_FORMAT, defaults.audioFormat),
            captions = preferences[CAPTIONS] ?: defaults.captions,
            skipSponsors = preferences[SKIP_SPONSORS] ?: defaults.skipSponsors,
        )
    }

    suspend fun setChoices(choices: DownloadChoices) {
        dataStore.edit {
            it[KIND] = choices.kind.name
            it[VIDEO_QUALITY] = choices.videoQuality.name
            it[AUDIO_FORMAT] = choices.audioFormat.name
            it[CAPTIONS] = choices.captions
            it[SKIP_SPONSORS] = choices.skipSponsors
        }
    }
}

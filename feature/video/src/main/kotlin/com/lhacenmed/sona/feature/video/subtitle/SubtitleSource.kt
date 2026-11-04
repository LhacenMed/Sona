package com.lhacenmed.sona.feature.video.subtitle

import com.lhacenmed.sona.feature.playback.MediaTrack
import java.io.File
import java.util.Locale

/** A subtitle file a video can show: the one attached to it, or one lying beside it. */
internal data class SubtitleFile(
    val file: File,
    /** What it is called to the user. */
    val name: String,
    val language: String?,
    val isAttached: Boolean,
    /** When it last changed, so a file replaced under the same name is read again. */
    val modifiedAt: Long,
)

/** Where the lines a video shows come from. */
internal sealed interface SubtitleSource {
    data object Off : SubtitleSource

    /** Nothing chosen: the player shows what the file itself asks to be shown, as a forced track. */
    data object PlayerDefault : SubtitleSource

    data class FromFile(val subtitle: SubtitleFile) : SubtitleSource

    data class Embedded(val track: MediaTrack) : SubtitleSource
}

private const val CHOICE_OFF = "off"
private const val CHOICE_ATTACHED = "attached"
private const val CHOICE_FILE_PREFIX = "file:"
private const val CHOICE_TRACK_PREFIX = "track:"

/** How this choice is kept with its video - see [com.lhacenmed.sona.core.model.VideoSubtitle.choice]. */
internal val SubtitleSource.storedChoice: String?
    get() = when (this) {
        SubtitleSource.Off -> CHOICE_OFF
        SubtitleSource.PlayerDefault -> null
        is SubtitleSource.FromFile -> if (subtitle.isAttached) CHOICE_ATTACHED else CHOICE_FILE_PREFIX + subtitle.file.name
        is SubtitleSource.Embedded -> "$CHOICE_TRACK_PREFIX${track.groupIndex}:${track.trackIndex}"
    }

/**
 * What a video shows: what the user picked for it, while that is still there - a file can be gone since. Otherwise
 * decided the way a series should carry on, nothing while the user last turned them off, and else its attached file,
 * a file beside it in [language] or any, or its own track in [language] - the last two as they were last picked.
 */
internal fun chooseSubtitleSource(
    choice: String?,
    attached: SubtitleFile?,
    sideFiles: List<SubtitleFile>,
    tracks: List<MediaTrack>,
    isOn: Boolean,
    language: String?,
): SubtitleSource {
    val picked = when {
        choice == CHOICE_OFF -> SubtitleSource.Off
        choice == CHOICE_ATTACHED -> attached?.let(SubtitleSource::FromFile)
        choice == null -> null
        choice.startsWith(CHOICE_FILE_PREFIX) -> {
            val name = choice.removePrefix(CHOICE_FILE_PREFIX)
            sideFiles.firstOrNull { it.file.name == name }?.let(SubtitleSource::FromFile)
        }
        choice.startsWith(CHOICE_TRACK_PREFIX) -> {
            val key = choice.removePrefix(CHOICE_TRACK_PREFIX)
            tracks.firstOrNull { "${it.groupIndex}:${it.trackIndex}" == key }?.let(SubtitleSource::Embedded)
        }
        else -> null
    }
    if (picked != null) return picked
    if (!isOn) return SubtitleSource.Off
    val file = attached
        ?: sideFiles.firstOrNull { isSameLanguage(it.language, language) }
        ?: sideFiles.firstOrNull()
    if (file != null) return SubtitleSource.FromFile(file)
    val track = tracks.firstOrNull { !it.isForced && isSameLanguage(it.language, language) }
    return track?.let(SubtitleSource::Embedded) ?: SubtitleSource.PlayerDefault
}

/** Whether two language codes name one language, whichever form each is in - "ar", "ara", "ar-EG". */
internal fun isSameLanguage(first: String?, second: String?): Boolean {
    if (first == null || second == null) return false
    return first.iso3Language() == second.iso3Language()
}

private fun String.iso3Language(): String {
    val primary = substringBefore('-').substringBefore('_').lowercase(Locale.ROOT)
    return runCatching { Locale(primary).isO3Language }.getOrDefault(primary)
}

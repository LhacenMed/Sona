package com.lhacenmed.sona.feature.download

import android.content.Context
import android.text.format.Formatter
import androidx.compose.ui.graphics.vector.ImageVector
import com.lhacenmed.sona.core.downloader.AudioTrackKind
import com.lhacenmed.sona.core.downloader.MediaFormat
import com.lhacenmed.sona.core.model.AudioFormat
import com.lhacenmed.sona.core.model.DownloadFailure
import com.lhacenmed.sona.core.model.DownloadKind
import com.lhacenmed.sona.core.model.SponsorSegment
import com.lhacenmed.sona.core.model.VideoFormat
import com.lhacenmed.sona.core.model.VideoQuality
import java.util.Locale
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons

/** The one place each of the downloader's choices is named - the download screen's and the Downloads settings' alike. */
internal val DownloadKind.labelRes: Int
    get() = when (this) {
        DownloadKind.VIDEO -> R.string.download_kind_video
        DownloadKind.AUDIO -> R.string.download_kind_audio
    }

internal val VideoQuality.labelRes: Int
    get() = when (this) {
        VideoQuality.BEST -> R.string.download_quality_best
        VideoQuality.P2160 -> R.string.download_quality_2160
        VideoQuality.P1440 -> R.string.download_quality_1440
        VideoQuality.P1080 -> R.string.download_quality_1080
        VideoQuality.P720 -> R.string.download_quality_720
        VideoQuality.P480 -> R.string.download_quality_480
        VideoQuality.P360 -> R.string.download_quality_360
    }

internal val VideoFormat.labelRes: Int
    get() = when (this) {
        VideoFormat.LEGACY -> R.string.download_video_format_legacy
        VideoFormat.QUALITY -> R.string.download_video_format_quality
    }

internal val VideoFormat.summaryRes: Int
    get() = when (this) {
        VideoFormat.LEGACY -> R.string.download_video_format_legacy_summary
        VideoFormat.QUALITY -> R.string.download_video_format_quality_summary
    }

internal val AudioFormat.labelRes: Int
    get() = when (this) {
        AudioFormat.ORIGINAL -> R.string.download_format_original
        AudioFormat.M4A -> R.string.download_format_m4a
        AudioFormat.MP3 -> R.string.download_format_mp3
        AudioFormat.OPUS -> R.string.download_format_opus
    }

val SponsorSegment.labelRes: Int
    get() = when (this) {
        SponsorSegment.SPONSOR -> R.string.download_segment_sponsor
        SponsorSegment.SELF_PROMOTION -> R.string.download_segment_self_promotion
        SponsorSegment.INTERACTION -> R.string.download_segment_interaction
        SponsorSegment.INTRO -> R.string.download_segment_intro
        SponsorSegment.OUTRO -> R.string.download_segment_outro
        SponsorSegment.PREVIEW -> R.string.download_segment_preview
        SponsorSegment.FILLER -> R.string.download_segment_filler
        SponsorSegment.MUSIC_OFFTOPIC -> R.string.download_segment_music_offtopic
    }

/** What the user can do about a failure - the one button its sheet offers. */
internal enum class FailureFix(val labelRes: Int, val icon: ImageVector) {
    TRY_AGAIN(R.string.download_fix_try_again, SonaIcons.Refresh),
    TRY_BEST_FORMAT(R.string.download_fix_try_again, SonaIcons.Refresh),
    OPEN_LINK(R.string.download_open_link, SonaIcons.OpenInNew),
    SIGN_IN(R.string.download_fix_sign_in, SonaIcons.Login),
    FREE_UP_SPACE(R.string.download_fix_free_up_space, SonaIcons.Storage),
    GET_DOWNLOADER(R.string.download_fix_get_downloader, SonaIcons.Extension),
}

/** How a failure is told: [rowRes] on its row; [titleRes] and [messageRes] in its sheet, with its [fix]. */
internal class FailurePresentation(val rowRes: Int, val titleRes: Int, val messageRes: Int, val icon: ImageVector, val fix: FailureFix)

/** The one place each failure is put in words, and given its fix. */
internal val DownloadFailure.presentation: FailurePresentation
    get() = when (this) {
        DownloadFailure.NO_NETWORK -> FailurePresentation(
            R.string.download_failure_no_network, R.string.download_failure_no_network, R.string.download_failure_no_network_message, SonaIcons.WifiOff, FailureFix.TRY_AGAIN,
        )
        DownloadFailure.UNAVAILABLE -> FailurePresentation(
            R.string.download_failure_unavailable_row, R.string.download_failure_unavailable, R.string.download_failure_unavailable_message, SonaIcons.Block, FailureFix.OPEN_LINK,
        )
        DownloadFailure.LOGIN_REQUIRED -> FailurePresentation(
            R.string.download_failure_login_required, R.string.download_failure_login_required, R.string.download_failure_login_required_message, SonaIcons.Lock, FailureFix.SIGN_IN,
        )
        DownloadFailure.FORMAT_GONE -> FailurePresentation(
            R.string.download_failure_format_gone_row, R.string.download_failure_format_gone, R.string.download_failure_format_gone_message, SonaIcons.HighQuality, FailureFix.TRY_BEST_FORMAT,
        )
        DownloadFailure.NO_SPACE -> FailurePresentation(
            R.string.download_failure_no_space, R.string.download_failure_no_space, R.string.download_failure_no_space_message, SonaIcons.Storage, FailureFix.FREE_UP_SPACE,
        )
        DownloadFailure.NO_DOWNLOADER -> FailurePresentation(
            R.string.download_failure_no_downloader, R.string.download_failure_no_downloader, R.string.download_failure_no_downloader_message, SonaIcons.Extension, FailureFix.GET_DOWNLOADER,
        )
        DownloadFailure.FAILED -> FailurePresentation(
            R.string.download_failed, R.string.download_failure_other, R.string.download_failure_other_message, SonaIcons.ErrorOutline, FailureFix.TRY_AGAIN,
        )
    }

internal val AudioTrackKind.labelRes: Int
    get() = when (this) {
        AudioTrackKind.ORIGINAL -> R.string.download_track_original
        AudioTrackKind.DUBBED -> R.string.download_track_dubbed
        AudioTrackKind.DESCRIPTIVE -> R.string.download_track_descriptive
    }

/** What a sound is, told under its language in the audio track picker. */
internal val AudioTrackKind.summaryRes: Int
    get() = if (this == AudioTrackKind.ORIGINAL) R.string.download_track_original_summary else labelRes

/** A language as the user reads it, in their own - "Spanish (Mexico)" - from a site's code, "es-MX"; a described sound's, "en-desc", as its language's. */
internal fun languageName(code: String): String {
    val locale = Locale.forLanguageTag(code.removeSuffix("-desc"))
    return locale.getDisplayName(Locale.getDefault()).takeIf { it.isNotBlank() && it != code }
        ?.replaceFirstChar { it.titlecase(Locale.getDefault()) } ?: code
}

/** The languages captions can be translated into, as their codes: the device's and English first, then the rest by name. */
internal fun translatableLanguages(): List<String> {
    val first = listOf(Locale.getDefault().language, "en").distinct()
    val rest = Locale.getISOLanguages().filter { it !in first && languageName(it) != it }.sortedBy(::languageName)
    return first + rest
}

/** "1080p60 · avc1 · 120 MB", "M4A · 128 kbps · 4 MB" - what tells one format from another. */
internal fun MediaFormat.facts(context: Context): String = listOfNotNull(
    height?.let { "${it}p${fps?.takeIf { fps -> fps > 30 } ?: ""}" } ?: extension.uppercase(),
    (videoCodec ?: audioCodec)?.substringBefore('.'),
    bitrateKbps?.takeIf { height == null }?.let { context.getString(R.string.download_bitrate, it) },
    sizeBytes?.let { (if (isSizeApproximate) "~" else "") + Formatter.formatShortFileSize(context, it) },
).joinToString(" · ")

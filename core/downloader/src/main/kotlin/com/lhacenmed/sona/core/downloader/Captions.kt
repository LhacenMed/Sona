package com.lhacenmed.sona.core.downloader

import com.lhacenmed.sona.core.model.DownloadChoices
import com.lhacenmed.sona.core.model.DownloadKind
import com.lhacenmed.sona.core.model.DownloadOptions
import org.json.JSONObject

/** How yt-dlp names YouTube's captions made from the speech, in the language spoken - listed again without it, the same file. */
private const val SpokenSuffix = "-orig"

/** A language's code: two or three letters, perhaps with more after a hyphen - "en", "pt-BR", "zh-Hans", "fr-en". */
private val LanguageCode = Regex("[a-z]{2,3}(-[a-z0-9]{2,8})*", RegexOption.IGNORE_CASE)

/** The codes Android still gives some languages, and the ones sites give them now. */
private val CurrentCodes = mapOf("iw" to "he", "in" to "id", "ji" to "yi")

/**
 * What yt-dlp is told to embed of a video's captions, as its `--sub-langs` - the one place which captions a video is
 * downloaded with is decided: one track for each language they are wanted in, the device's [deviceLanguage] and English,
 * the best of that language the video has; and with neither, the one of the language spoken in it. Null for none.
 *
 * Chosen from [read], yt-dlp's read of the video, where it is at hand. A playlist's videos are not read before they are
 * downloaded, and no code tells people's captions from those YouTube translated, which it refuses: each takes those made
 * from its speech, in the language spoken.
 */
internal fun captionLanguagesOf(read: JSONObject?, deviceLanguage: String): String? {
    if (read == null) return ".*$SpokenSuffix"
    return exactCaptionLanguages(captionsToEmbed(read.keysOf("subtitles"), read.keysOf("automatic_captions"), wantedLanguages(deviceLanguage)))
}

/** [codes], picked - each matched whole - as yt-dlp's `--sub-langs`; null for none. */
internal fun exactCaptionLanguages(codes: List<String>): String? =
    codes.takeIf { it.isNotEmpty() }?.joinToString(",") { it.replace(Regex("""[\\.^$*+?{}\[\]|()]"""), """\\$0""") }

/** The captions this video is downloaded with unless others are picked - see [captionLanguagesOf] - what a picker starts from. */
fun MediaInfo.suggestedCaptions(deviceLanguage: String): List<String> =
    captionsToEmbed(madeCaptions, automaticCaptions, wantedLanguages(deviceLanguage))

/**
 * The captions a download of this video takes with [choices] and [options]: those picked, else those suggested - none for a
 * video downloaded without captions. Downloading the captions alone, they are always taken - and where it has none, its
 * speech is transcribed, where the user [transcribes] - see [CaptionTranscription].
 */
fun MediaInfo.pickedCaptions(choices: DownloadChoices, options: DownloadOptions, deviceLanguage: String, transcribes: Boolean): List<String> =
    options.subtitleLanguages ?: when {
        choices.kind == DownloadKind.SUBTITLES -> suggestedCaptions(deviceLanguage).ifEmpty { listOfNotNull(TRANSCRIBED_CAPTIONS.takeIf { transcribes }) }
        choices.captions -> suggestedCaptions(deviceLanguage)
        else -> emptyList()
    }

/**
 * A video's captions as a picker lists them, each by the code that picks it - see [captionGroups] - and the one a
 * translation into another language is made from: see [translatedCaptionCode]. Null where it has none.
 */
class CaptionGroups(val made: List<String>, val spoken: List<String>, val translationSource: String?)

/**
 * This video's own captions: those made by people, and those its site made from the speech, in the language spoken - not
 * those YouTube translated, which it refuses to every reader. A translation is made from people's in the language spoken,
 * else the speech's, else any of people's.
 */
val MediaInfo.captionGroups: CaptionGroups
    get() {
        val spoken = spokenCaptionsOf(automaticCaptions)
        val source = spoken.firstOrNull()?.let { language -> madeCaptions.firstOrNull { primaryOf(it) == primaryOf(language) } ?: language }
            ?: madeCaptions.firstOrNull()
        return CaptionGroups(madeCaptions, spoken, source)
    }

/**
 * The captions of [automatic] its site made from the speech: YouTube marks those in the language spoken - the rest it lists
 * are translations - and any other site's are the speech's, each a language of its own.
 */
private fun spokenCaptionsOf(automatic: List<String>): List<String> {
    val marked = automatic.filter { it.endsWith(SpokenSuffix, ignoreCase = true) }.map { it.dropLast(SpokenSuffix.length) }
    return marked.ifEmpty { automatic.filter { LanguageCode.matches(it) } }
}

/** The languages captions are wanted in: the device's, and English. */
private fun wantedLanguages(deviceLanguage: String) = listOf(deviceLanguage, "en").map(::comparable).distinct()

/**
 * The codes of the captions to embed, of the [made] by people and the [automatic], each named as yt-dlp names them - one
 * for each of the [wanted] languages: one made by people in it - its own code before a regional one - else the one its site
 * made from the speech, where that is the language spoken. Never one YouTube translated: it refuses them to every reader.
 *
 * With none, the language spoken: its captions made from the speech, or else the first made by people.
 */
internal fun captionsToEmbed(made: List<String>, automatic: List<String>, wanted: List<String>): List<String> {
    val spoken = spokenCaptionsOf(automatic)
    val chosen = wanted.mapNotNull { language ->
        made.firstOrNull { comparable(it) == language }
            ?: made.firstOrNull { primaryOf(it) == language }
            ?: spoken.firstOrNull { comparable(it) == language }
    }
    if (chosen.isNotEmpty()) return chosen.distinct()
    return listOfNotNull(spoken.firstOrNull() ?: made.firstOrNull { LanguageCode.matches(it) })
}

/** [code] as it is compared: in lower case, its language by the code sites give it now. */
private fun comparable(code: String): String {
    val lower = code.lowercase()
    val language = lower.substringBefore('-')
    return CurrentCodes[language]?.let { it + lower.removePrefix(language) } ?: lower
}

/** The language [code] is of - "pt" of "pt-BR", "fr" of "fr-en". */
private fun primaryOf(code: String): String = comparable(code).substringBefore('-')

internal fun JSONObject.keysOf(name: String): List<String> = optJSONObject(name)?.keys()?.asSequence()?.toList().orEmpty()

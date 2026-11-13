package com.lhacenmed.sona.core.downloader

import com.lhacenmed.sona.core.downloader.sites.languageTagOf
import com.lhacenmed.sona.core.model.DownloaderPreferences
import com.lhacenmed.sona.core.model.TranscriptionService
import java.io.File
import okhttp3.Response
import okhttp3.Callback
import okhttp3.Call
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.resume
import java.net.UnknownHostException
import java.net.SocketTimeoutException
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/** How the captions transcribed from a video's speech are picked - see [CaptionTranscription]. */
const val TRANSCRIBED_CAPTIONS = "transcribed"

private const val SupadataApi = "https://api.supadata.ai/v1/transcript"
private const val GroqApi = "https://api.groq.com/openai/v1/audio/transcriptions"
private const val GroqModel = "whisper-large-v3-turbo"

/** The most sound Groq's free tier takes in one request. */
private const val GroqMaxBytes = 25L * 1024 * 1024

/** How long a transcript Supadata makes as a job of its own - a long video's, or one made from its speech - is waited for. */
private const val SupadataWaitMs = 10 * 60_000L

/** How often that job is asked after, as Supadata asks. */
private const val SupadataPollMs = 1_000L

/** The sites Supadata reads a video's transcript from by its link alone. */
private val SupadataSites = Regex("""(?:^|\.)(?:youtube\.com|youtu\.be|tiktok\.com|instagram\.com|x\.com|twitter\.com|facebook\.com|fb\.watch)$""")

/** What Groq reads sound from, by its name's extension. */
private val GroqExtensions = setOf("flac", "mp3", "mp4", "mpeg", "mpga", "m4a", "ogg", "wav", "webm")

/** A video's speech, written down: its [lines], in [language] - a language tag, "und" where unknown. */
private class Transcript(val lines: List<CaptionLine>, val language: String)

/**
 * Captions transcribed from a video's speech, where it has none of its own - by the user's own free keys to two services, the one
 * they chose first, then the other - see [DownloaderPreferences.transcribers]: Supadata by the video's link, which takes the
 * site's own transcript where it has one, else makes one - by its sound's address, for a site it does not read; Groq's Whisper
 * from its sound, fetched here and uploaded. Picked as [TRANSCRIBED_CAPTIONS]; made
 * before yt-dlp downloads, from the read it is handed, as [CaptionTranslation]'s are - see [listMadeCaptions].
 */
internal object CaptionTranscription {
    // A transcript made as it is asked for is answered only once made, and a sound uploaded takes as long as it takes: neither
    // is cut off by OkHttp's ten seconds of silence, only by the whole call's limit.
    private val client by lazy {
        OkHttpClient.Builder().callTimeout(5, TimeUnit.MINUTES).readTimeout(5, TimeUnit.MINUTES).writeTimeout(5, TimeUnit.MINUTES).build()
    }

    /**
     * [picked], with [TRANSCRIBED_CAPTIONS] among them made into [folder] and listed in [read] under the language spoken - left
     * out where no service makes it, each one's failure said to [log].
     */
    suspend fun make(read: JSONObject?, picked: List<String>, folder: File, preferences: DownloaderPreferences, log: (String) -> Unit): List<String> {
        if (TRANSCRIBED_CAPTIONS !in picked) return picked
        val code = read?.let { transcribe(it, preferences, log) }?.let { read.listMadeCaptions(it.language, it.lines, folder) }
        return picked.mapNotNull { if (it == TRANSCRIBED_CAPTIONS) code else it }
    }

    private suspend fun transcribe(read: JSONObject, preferences: DownloaderPreferences, log: (String) -> Unit): Transcript? = withContext(Dispatchers.IO) {
        val page = read.stringOrNull("webpage_url")
        val sound = soundOf(read)
        val attempts = preferences.transcribers.mapNotNull { service ->
            val key = preferences.keyFor(service)
            val attempt: (suspend () -> Transcript?)? = when (service) {
                TranscriptionService.SUPADATA -> (page?.takeIf(::isSupadataSite) ?: sound?.getString("url"))?.let { url -> { supadata(url, key) } }
                TranscriptionService.GROQ -> sound?.let { { groq(it, key) } }
            }
            attempt?.let { service.title to it }
        }
        if (attempts.isEmpty()) log("Nothing to transcribe: no link or sound a service takes")
        for ((service, attempt) in attempts) {
            log("Transcribing the speech with $service")
            val transcript = try {
                attempt()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("$service could not transcribe it: ${reasonOf(e)}")
                null
            }
            if (transcript != null) return@withContext transcript
        }
        null
    }

    private fun isSupadataSite(url: String): Boolean = url.toHttpUrlOrNull()?.host?.let(SupadataSites::containsMatchIn) == true

    /**
     * The transcript Supadata gives of [url] - a video's link, or its file's - the site's own where it has one, else one made
     * from its speech: answered at once, within ~100 seconds while it is made, or as a job asked after until it is done. Null
     * where it heard no speech; failing, in Supadata's words, where it has none to give.
     */
    private suspend fun supadata(url: String, key: String): Transcript? {
        val first = call(Request.Builder().url(SupadataApi.toHttpUrl().newBuilder().addQueryParameter("url", url).build()).header("x-api-key", key).build())
        // 206: answered, but with Supadata's error - it has no transcript of it, and makes none.
        if (first.code == 206) throw IOException(errorOf(first.json.toString()))
        val answer = first.json.stringOrNull("jobId")?.let { awaitJob(it, key) } ?: first.json
        val language = languageTagOf(answer.stringOrNull("lang"))
        val lines = answer.optJSONArray("content")?.objects().orEmpty().mapNotNull { segment ->
            val start = segment.optLong("offset")
            val text = segment.optString("text").trim().takeIf(String::isNotEmpty) ?: return@mapNotNull null
            CaptionLine(start, start + segment.optLong("duration"), text)
        }
        return lines.takeIf { it.isNotEmpty() }?.let { Transcript(it, language) }
    }

    /**
     * Supadata's job [id], once done - its transcript, at the top or under `result` as its versions give it - failing where it
     * failed, or is not done in time.
     */
    private suspend fun awaitJob(id: String, key: String): JSONObject = withTimeoutOrNull(SupadataWaitMs) {
        var job: JSONObject
        do {
            delay(SupadataPollMs)
            job = call(Request.Builder().url("$SupadataApi/$id").header("x-api-key", key).build()).json
            if (job.optString("status") == "failed") throw IOException(job.opt("error")?.let { errorOf(it.toString()) } ?: "the job failed")
        } while (job.optString("status") != "completed")
        job.optJSONObject("result") ?: job
    } ?: throw IOException("no transcript within ${SupadataWaitMs / 60_000} minutes")

    /** The transcript Groq's Whisper hears in [sound], one of the read's formats - fetched here, as yt-dlp would fetch it. */
    private suspend fun groq(sound: JSONObject, key: String): Transcript? {
        val bytes = fetch(sound) ?: throw IOException("its sound is larger than the ${GroqMaxBytes / (1024 * 1024)} MB Groq takes")
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", "sound.${sound.getString("ext")}", bytes.toRequestBody("application/octet-stream".toMediaType()))
            .addFormDataPart("model", GroqModel)
            .addFormDataPart("response_format", "verbose_json")
            .addFormDataPart("temperature", "0")
            .build()
        val answer = call(Request.Builder().url(GroqApi).header("Authorization", "Bearer $key").post(body).build()).json
        val lines = answer.optJSONArray("segments")?.objects().orEmpty().mapNotNull { segment ->
            val text = segment.optString("text").trim().takeIf(String::isNotEmpty) ?: return@mapNotNull null
            CaptionLine((segment.optDouble("start") * 1000).toLong(), (segment.optDouble("end") * 1000).toLong(), text)
        }
        return lines.takeIf { it.isNotEmpty() }?.let { Transcript(it, languageNamed(answer.optString("language"))) }
    }

    /**
     * The read's format Groq is handed: one file over HTTP(S) with sound in a container Groq reads - the sound alone where
     * there is one, the smallest first. Null where there is none.
     */
    private fun soundOf(read: JSONObject): JSONObject? = read.optJSONArray("formats")?.objects().orEmpty()
        .filter { format ->
            format.stringOrNull("url")?.startsWith("http") == true && format.optString("protocol", "https") in setOf("http", "https") &&
                format.optString("acodec") != "none" && format.optString("ext") in GroqExtensions
        }
        .minWithOrNull(compareBy<JSONObject> { it.optString("vcodec") != "none" }.thenBy { it.optLong("filesize", Long.MAX_VALUE) }.thenBy { it.optDouble("tbr", Double.MAX_VALUE) })

    /** [format]'s file, with the headers its read names - null past what Groq takes. */
    private suspend fun fetch(format: JSONObject): ByteArray? {
        val headers = format.optJSONObject("http_headers")
        val request = Request.Builder().url(format.getString("url")).apply { headers?.keys()?.forEach { header(it, headers.getString(it)) } }.build()
        return client.newCall(request).await { response ->
            if (!response.isSuccessful) throw IOException("its sound answered HTTP ${response.code}")
            val body = response.body ?: throw IOException("its sound came empty")
            if (body.contentLength() > GroqMaxBytes) return@await null
            val source = body.source()
            if (source.request(GroqMaxBytes + 1)) null else source.buffer.readByteArray()
        }
    }

    /** A service's answer: its status, and what it said. */
    private class Answer(val code: Int, val json: JSONObject)

    /**
     * [request]'s answer - asked again, a little later each time, while the service is busy or briefly failing: past its rate,
     * or erring on its side. Failing with the service's own words where it refuses.
     */
    private suspend fun call(request: Request): Answer {
        var attempt = 0
        while (true) {
            val (code, text, retryAfter) = client.newCall(request).await { Triple(it.code, it.body?.string().orEmpty(), it.header("retry-after")?.toLongOrNull()) }
            // As long as the service asks - Groq says so on a 429 - else a little longer each time; never a wait past a minute.
            if (code in RetriedCodes && ++attempt < MaxAttempts && (retryAfter == null || retryAfter <= MaxRetryAfterSeconds)) {
                delay(retryAfter?.coerceAtLeast(1)?.times(1000) ?: (RetryDelayMs * attempt))
                continue
            }
            if (code !in 200..299) throw IOException("HTTP $code: ${errorOf(text)}")
            return Answer(code, runCatching { JSONObject(text) }.getOrElse { throw IOException("HTTP $code, but no transcript in what it answered") })
        }
    }

    /**
     * What a refusal says of why: Supadata's `message` and `details`, OpenAI's `error.message` - or its first words. A plain
     * error code is said as it is.
     */
    private fun errorOf(text: String): String = runCatching {
        val json = JSONObject(text)
        json.optJSONObject("error")?.stringOrNull("message")
            ?: listOfNotNull(json.stringOrNull("message"), json.stringOrNull("details")).distinct().joinToString(": ").ifEmpty { null }
            ?: json.stringOrNull("error")
    }.getOrNull() ?: text.take(200).ifBlank { "no reason given" }
}

/** How many times a busy or briefly failing service is asked, in all. */
private const val MaxAttempts = 3

/** How long before it is asked again - this, times how many times it was. */
private const val RetryDelayMs = 2_000L

/** The longest a service's own `retry-after` is waited - one asking longer, its quota spent, is let go for the next service. */
private const val MaxRetryAfterSeconds = 60L

/** The answers worth asking again for: past its rate, out of room (Groq's 498), or erring on its side. */
private val RetriedCodes = setOf(429, 498, 500, 502, 503, 504)

/** This call's answer, as [read] makes it - cancelled with the coroutine awaiting it, however long it is waited on. */
private suspend fun <T> Call.await(read: (Response) -> T): T = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) = continuation.resumeWithException(e)

        override fun onResponse(call: Call, response: Response) {
            runCatching { response.use(read) }.fold(continuation::resume, continuation::resumeWithException)
        }
    })
}

/** Why a service could not be asked, in a few words - its own where it gave some. */
private fun reasonOf(e: Exception): String = when (e) {
    is SocketTimeoutException -> "it did not answer in time"
    is UnknownHostException -> "no connection"
    else -> e.message ?: e::class.simpleName.orEmpty()
}

/** [name], a language as Whisper names it - "english" - as its tag, "en"; "und" where it is none Android names. */
private fun languageNamed(name: String): String = Locale.getISOLanguages().firstOrNull { code ->
    Locale.forLanguageTag(code).getDisplayLanguage(Locale.ENGLISH).equals(name, ignoreCase = true)
} ?: name.lowercase().takeIf { it.length == 2 } ?: "und"

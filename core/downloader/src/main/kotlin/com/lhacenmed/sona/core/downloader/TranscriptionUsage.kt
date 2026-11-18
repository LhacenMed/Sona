package com.lhacenmed.sona.core.downloader

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import com.lhacenmed.sona.core.model.TranscriptionService
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/** How much of a transcription service's allowance is spent, as the service itself counts it. */
sealed interface ServiceUsage {
    /** Supadata's: [used] of [max] credits this billing period, on [plan]. */
    data class Credits(val used: Long, val max: Long, val plan: String) : ServiceUsage

    /** Groq's: [left] of [limit] requests today, counted again from [resetsAt]. */
    data class DailyRequests(val left: Long, val limit: Long, val resetsAt: Long) : ServiceUsage
}

/** What a service says of a key, asked as it is given - see [TranscriptionUsage.check]. */
sealed interface KeyCheck {
    /** Still being asked. */
    data object Checking : KeyCheck

    /** The key works: what is left of its allowance. */
    data class Works(val usage: ServiceUsage) : KeyCheck

    /** The service refused it - mistyped, or revoked. */
    data object Refused : KeyCheck

    /** The service could not be asked: offline, or answering with an error of its own. */
    data object Unreachable : KeyCheck
}

private const val SupadataMeApi = "https://api.supadata.ai/v1/me"
private const val Prefs = "transcription_usage"
private const val GroqLeft = "groq_left"
private const val GroqLimit = "groq_limit"
private const val GroqResetsAt = "groq_resets_at"

/**
 * Each transcription service's allowance, as live as the service lets it be known: Supadata's asked of its account endpoint,
 * Groq's - which has none - taken from the rate headers on each of its answers to a transcription, and kept, so it is there
 * after a restart too. A day Groq has started counting again since is shown whole.
 */
@Singleton
class TranscriptionUsage @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences(Prefs, Context.MODE_PRIVATE)
    private val client by lazy { OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build() }

    private val groq = MutableStateFlow(
        prefs.takeIf { it.contains(GroqLimit) }?.let {
            ServiceUsage.DailyRequests(it.getLong(GroqLeft, 0), it.getLong(GroqLimit, 0), it.getLong(GroqResetsAt, 0))
        },
    )

    /** Groq's requests left today, as its last answer said - null before it has answered once. */
    val groqRequests: StateFlow<ServiceUsage.DailyRequests?> = groq.asStateFlow()

    /** Whether [service] takes [key], and where it does, what is left of its allowance - asked of the service itself. */
    suspend fun check(service: TranscriptionService, key: String): KeyCheck = when (service) {
        TranscriptionService.SUPADATA -> checkSupadata(key)
        TranscriptionService.GROQ -> checkGroq(key)
    }

    /** Supadata's word on [key], from its account endpoint - which costs no credit - with its credits this billing period. */
    private suspend fun checkSupadata(key: String): KeyCheck = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url(SupadataMeApi).header("x-api-key", key).build()).execute().use { response ->
                when {
                    response.code == 401 || response.code == 403 -> KeyCheck.Refused
                    !response.isSuccessful -> KeyCheck.Unreachable
                    else -> {
                        val json = JSONObject(response.body?.string().orEmpty())
                        KeyCheck.Works(ServiceUsage.Credits(json.getLong("usedCredits"), json.getLong("maxCredits"), json.optString("plan")))
                    }
                }
            }
        }.getOrDefault(KeyCheck.Unreachable)
    }

    /**
     * Groq's word on [key]: it has no account endpoint, so a second of silence is transcribed - one of the day's requests -
     * for the rate headers its answer carries, kept as its requests left. A key past today's requests still works.
     */
    private suspend fun checkGroq(key: String): KeyCheck = withContext(Dispatchers.IO) {
        runCatching {
            val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("file", "silence.wav", SilentWav.toRequestBody("audio/wav".toMediaType()))
                .addFormDataPart("model", GroqModel)
                .build()
            client.newCall(Request.Builder().url(GroqApi).header("Authorization", "Bearer $key").post(body).build()).execute().use { response ->
                recordGroq(response.headers)
                when {
                    response.code == 401 || response.code == 403 -> KeyCheck.Refused
                    response.isSuccessful || response.code == 429 -> groq.value?.let { KeyCheck.Works(it) } ?: KeyCheck.Unreachable
                    else -> KeyCheck.Unreachable
                }
            }
        }.getOrDefault(KeyCheck.Unreachable)
    }

    /** Keeps what Groq's rate headers say of today's requests - nothing, where they say nothing of them. */
    internal fun recordGroq(headers: Headers, now: Long = System.currentTimeMillis()) {
        val left = headers["x-ratelimit-remaining-requests"]?.toLongOrNull() ?: return
        val limit = headers["x-ratelimit-limit-requests"]?.toLongOrNull() ?: return
        val resetsAt = now + (headers["x-ratelimit-reset-requests"]?.let(::durationMsOf) ?: 0L)
        prefs.edit().putLong(GroqLeft, left).putLong(GroqLimit, limit).putLong(GroqResetsAt, resetsAt).apply()
        groq.value = ServiceUsage.DailyRequests(left, limit, resetsAt)
    }
}

/** A second of silence, as a WAV file - 16 kHz, mono, 16-bit - what checking a Groq key sends. */
private val SilentWav: ByteArray by lazy {
    val rate = 16_000
    val dataSize = rate * 2
    java.nio.ByteBuffer.allocate(44 + dataSize).order(java.nio.ByteOrder.LITTLE_ENDIAN).apply {
        put("RIFF".toByteArray()); putInt(36 + dataSize); put("WAVE".toByteArray())
        put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(rate); putInt(rate * 2); putShort(2); putShort(16)
        put("data".toByteArray()); putInt(dataSize)
    }.array()
}

/** [usage] as it stands at [now]: a day Groq has counted again since is whole. */
fun ServiceUsage.DailyRequests.at(now: Long = System.currentTimeMillis()): ServiceUsage.DailyRequests =
    if (now >= resetsAt) copy(left = limit) else this

/** A Go-style duration, as Groq's headers write one - "2m59.56s", "1h2m", "7.66s", "120ms" - in milliseconds; null where it is none. */
internal fun durationMsOf(text: String): Long? {
    val parts = Regex("""(\d+(?:\.\d+)?)(h|ms|m|s)""").findAll(text).toList().ifEmpty { return null }
    return parts.sumOf { part ->
        val value = part.groupValues[1].toDouble()
        when (part.groupValues[2]) {
            "h" -> value * 3_600_000
            "m" -> value * 60_000
            "s" -> value * 1_000
            else -> value
        }
    }.toLong()
}

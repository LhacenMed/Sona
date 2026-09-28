package com.lhacenmed.sona.feature.tageditor.net

import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

/**
 * The one way the tag editor asks anything of the web - YTDLnis's `MusicHttp`: every catalogue and lyrics
 * source shares its client, its timeouts and its failure handling.
 *
 * Every failure - unreachable, refused, unreadable - is a null: a source that cannot answer is a normal
 * outcome here, and the others answer without it. A request is cancelled with the coroutine that made it,
 * so leaving the editor stops every lookup at once.
 */
internal object Http {
    private const val UserAgent = "Sona"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    /** The response body of a GET to [url], or null when there is none to read. */
    suspend fun text(url: String, headers: Map<String, String> = emptyMap()): String? =
        attempt {
            val request = Request.Builder().url(url)
                .header("User-Agent", UserAgent)
                .apply { headers.forEach { (name, value) -> header(name, value) } }
                .build()
            client.newCall(request).await().use { response ->
                if (response.isSuccessful) withContext(Dispatchers.IO) { response.body?.string() } else null
            }
        }

    suspend fun json(url: String, headers: Map<String, String> = emptyMap()): JSONObject? =
        text(url, headers)?.let { runCatching { JSONObject(it) }.getOrNull() }

    suspend fun jsonArray(url: String): JSONArray? =
        text(url)?.let { runCatching { JSONArray(it) }.getOrNull() }

    suspend fun bytes(url: String): ByteArray? =
        attempt {
            client.newCall(Request.Builder().url(url).header("User-Agent", UserAgent).build()).await().use { response ->
                if (response.isSuccessful) withContext(Dispatchers.IO) { response.body?.bytes() } else null
            }
        }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { response.close() }
            }

            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }
        })
        continuation.invokeOnCancellation { cancel() }
    }
}

/** [block]'s result, or null for any failure but a cancellation, which is left to cancel. */
internal inline fun <T> attempt(block: () -> T?): T? =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

// JSON as the sources send it: loosely typed, and now and then missing or null where a value was expected. Every
// read comes out blank rather than throwing - a tag that is missing is one that stays blank.

internal fun JSONObject.string(key: String): String =
    if (isNull(key)) "" else optString(key).trim()

internal fun JSONObject.objects(key: String): List<JSONObject> = optJSONArray(key)?.objects().orEmpty()

internal fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull(::optJSONObject)

/** The first value a source actually filled in, falling back between equivalent fields. */
internal fun firstNonBlank(vararg values: String?): String = values.firstOrNull { !it.isNullOrBlank() }.orEmpty()

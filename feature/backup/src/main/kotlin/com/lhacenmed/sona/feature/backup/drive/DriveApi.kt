package com.lhacenmed.sona.feature.backup.drive

import android.content.Context
import com.lhacenmed.sona.core.common.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSink
import org.json.JSONArray
import org.json.JSONObject

private const val FILES_URL = "https://www.googleapis.com/drive/v3/files"
private const val UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files"
private const val ABOUT_URL = "https://www.googleapis.com/drive/v3/about"
private const val FILE_FIELDS = "id,name,size,createdTime,appProperties"
private const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"

/** What an upload sends at a time - a multiple of 256 KiB, as resumable uploads require. */
private const val CHUNK_BYTES = 16L * 1024 * 1024

/** Up to this size a file goes in one request, its details with it - two for each song is what slowed a library. */
private const val ONE_REQUEST_MAX_BYTES = 5L * 1024 * 1024

/** How many times a request Drive asked to slow down for, or one cut off, is sent before it fails. */
private const val CALL_ATTEMPTS = 6
private const val FIRST_BACKOFF_MS = 1_000L
private const val MAX_JITTER_MS = 500L

/** How many times a chunk is sent again after a dropped connection before the upload gives up. */
private const val CHUNK_ATTEMPTS = 5
private const val RETRY_DELAY_MS = 2_000L

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val HTTP_TOO_MANY_REQUESTS = 429
private const val HTTP_SERVER_ERROR = 500
private const val RATE_LIMIT_PEEK_BYTES = 4_096L
internal const val HTTP_NOT_FOUND = 404
private const val HTTP_GONE = 410
private const val HTTP_RESUME_INCOMPLETE = 308

private val JSON = "application/json; charset=utf-8".toMediaType()
private val BINARY = "application/octet-stream".toMediaType()

/** A file in the user's Drive, as Sona sees it: one it made, with the [properties] it gave it. */
internal data class DriveFile(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val createdAt: Long,
    val properties: Map<String, String>,
)

/** Drive answered [code] - an error. */
internal class DriveHttpException(val code: Int, message: String) : IOException(message)

/** The Drive API, as far as Sona uses it: its own files and folders, by query, up and down. */
@Singleton
internal class DriveApi @Inject constructor(
    @ApplicationContext context: Context,
    private val access: DriveAccess,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val http = OkHttpClient.Builder().readTimeout(60, TimeUnit.SECONDS).writeTimeout(60, TimeUnit.SECONDS).build()

    /**
     * Where each upload under way can carry on - by the file's name, size and last change, so a file that changed
     * since is never sent on the end of what its old bytes began, and by account, so it never ends in another's Drive. Kept on disk, so an upload a run did not finish -
     * stopped, cut off, or the app closed - carries on where Drive stopped, in whichever run comes next.
     */
    private val uploads = context.getSharedPreferences("drive_uploads", Context.MODE_PRIVATE)

    /** Every file - not in the trash - that has every one of [properties], in [parentId] where given. */
    suspend fun find(properties: Map<String, String>, parentId: String? = null): List<DriveFile> {
        val query = buildList {
            add("trashed = false")
            parentId?.let { add("'$it' in parents") }
            properties.forEach { (key, value) -> add("appProperties has { key='$key' and value='$value' }") }
        }.joinToString(" and ")
        val files = mutableListOf<DriveFile>()
        var pageToken: String? = null
        do {
            val url = FILES_URL.toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("fields", "nextPageToken,files($FILE_FIELDS)")
                .addQueryParameter("pageSize", "1000")
                .apply { pageToken?.let { addQueryParameter("pageToken", it) } }
                .build()
            val page = json(Request.Builder().url(url))
            files += page.getJSONArray("files").toDriveFiles()
            pageToken = page.optString("nextPageToken").takeIf { it.isNotEmpty() }
        } while (pageToken != null)
        return files
    }

    suspend fun createFolder(name: String, properties: Map<String, String>, parentId: String? = null): String {
        val metadata = metadata(name, properties, parentId).put("mimeType", FOLDER_MIME_TYPE)
        return json(Request.Builder().url("$FILES_URL?fields=id").post(metadata.toString().toRequestBody(JSON))).getString("id")
    }

    /**
     * Uploads [file] as [name] into [parentId]: a small one in a single request, a large one in chunks - each sent
     * again where the connection dropped, and carried on from where an earlier run left it, so it is never started
     * over.
     */
    suspend fun upload(file: File, name: String, parentId: String, properties: Map<String, String>): DriveFile {
        val size = file.length()
        if (size <= ONE_REQUEST_MAX_BYTES) return uploadAtOnce(file, name, parentId, properties)
        val key = "$name:$size:${file.lastModified()}:${access.account.value}"
        var session = uploads.getString(key, null)
        var isResuming = session != null
        var offset = 0L
        var failures = 0
        while (true) {
            try {
                if (session == null) {
                    session = startUpload(name, parentId, properties, size)
                    // Only one way on for a name: one begun for bytes it no longer has could only send them wrong.
                    uploads.edit().apply { uploads.all.keys.filter { it.startsWith("$name:") }.forEach { remove(it) } }.putString(key, session).apply()
                    offset = 0L
                }
                if (isResuming) {
                    // Drive says how far it got; null means the last chunk did arrive, only its answer did not.
                    val uploaded = try {
                        uploadedBytes(session, size)
                    } catch (e: DriveHttpException) {
                        if (e.code != HTTP_NOT_FOUND && e.code != HTTP_GONE) throw e
                        // Drive lets go of an upload left for a week; this one starts again.
                        session = null
                        isResuming = false
                        continue
                    }
                    if (uploaded == null) return finished(key) { find(emptyMap(), parentId).first { it.name == name } }
                    offset = uploaded
                    isResuming = false
                }
                val length = minOf(CHUNK_BYTES, size - offset)
                val range = if (size == 0L) "bytes */0" else "bytes $offset-${offset + length - 1}/$size"
                call(Request.Builder().url(session).header("Content-Range", range).put(FileSlice(file, offset, length))).use {
                    if (it.code != HTTP_RESUME_INCOMPLETE) return finished(key) { JSONObject(it.body!!.string()).toDriveFile() }
                    offset = it.rangeEnd()
                }
                failures = 0
            } catch (e: DriveAccessLostException) {
                throw e
            } catch (e: IOException) {
                if (++failures >= CHUNK_ATTEMPTS) throw e
                isResuming = session != null
                delay(failures * RETRY_DELAY_MS)
            }
        }
    }

    /** [file] uploaded as [name] in one request, its details and its bytes together. */
    private suspend fun uploadAtOnce(file: File, name: String, parentId: String, properties: Map<String, String>): DriveFile {
        val body = MultipartBody.Builder()
            .setType("multipart/related".toMediaType())
            .addPart(metadata(name, properties, parentId).toString().toRequestBody(JSON))
            .addPart(file.asRequestBody(BINARY))
            .build()
        return json(Request.Builder().url("$UPLOAD_URL?uploadType=multipart&fields=$FILE_FIELDS").post(body)).toDriveFile()
    }

    /** Begins an upload of [size] bytes as [name], and returns where its bytes go. */
    private suspend fun startUpload(name: String, parentId: String, properties: Map<String, String>, size: Long): String = call(
        Request.Builder()
            .url("$UPLOAD_URL?uploadType=resumable&fields=$FILE_FIELDS")
            .header("X-Upload-Content-Length", size.toString())
            .post(metadata(name, properties, parentId).toString().toRequestBody(JSON)),
    ).use { response -> response.header("Location") ?: throw IOException("Drive started no upload for $name") }

    /** The file [uploaded] reads back, the upload under [key] being over. */
    private inline fun finished(key: String, uploaded: () -> DriveFile): DriveFile =
        uploaded().also { uploads.edit().remove(key).apply() }

    suspend fun download(id: String, file: File) {
        open(id).use { input -> file.outputStream().use { input.copyTo(it) } }
    }

    suspend fun open(id: String): InputStream = call(Request.Builder().url("$FILES_URL/$id?alt=media")).body!!.byteStream()

    suspend fun delete(id: String) {
        call(Request.Builder().url("$FILES_URL/$id").delete()).close()
    }

    /** The email address of the account [token] was given for - asked of Drive with that token, before it is kept. */
    suspend fun accountEmail(token: String): String = withContext(ioDispatcher) {
        val request = Request.Builder().url("$ABOUT_URL?fields=user(emailAddress)").header("Authorization", "Bearer $token").build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw DriveHttpException(response.code, "Drive answered ${response.code}")
            JSONObject(response.body!!.string()).getJSONObject("user").getString("emailAddress")
        }
    }

    /** How much more the user's Drive holds, or null where it has no limit. */
    suspend fun freeBytes(): Long? {
        val quota = json(Request.Builder().url("$ABOUT_URL?fields=storageQuota")).getJSONObject("storageQuota")
        val limit = quota.optString("limit").toLongOrNull() ?: return null
        return limit - quota.optString("usage").toLong()
    }

    /** How much of an interrupted upload Drive has, or null where it already has it all. */
    private suspend fun uploadedBytes(session: String, size: Long): Long? =
        call(Request.Builder().url(session).header("Content-Range", "bytes */$size").put(ByteArray(0).toRequestBody())).use {
            if (it.code == HTTP_RESUME_INCOMPLETE) it.rangeEnd() else null
        }

    private suspend fun json(request: Request.Builder): JSONObject = call(request).use { JSONObject(it.body!!.string()) }

    /**
     * Sends [request], and fails on any answer other than success or "resume incomplete" - after sending it again,
     * waiting longer each time, while Drive asks to slow down or briefly fails, as Drive asks of every app. One cut
     * off is sent again too, unless it makes a file: sent twice, that would make two.
     */
    private suspend fun call(request: Request.Builder): Response {
        var attempt = 0
        while (true) {
            val response = try {
                send(request)
            } catch (e: DriveAccessLostException) {
                throw e
            } catch (e: IOException) {
                if (request.build().method == "POST" || ++attempt >= CALL_ATTEMPTS) throw e
                delay(backoff(attempt))
                continue
            }
            if (response.isTransient() && ++attempt < CALL_ATTEMPTS) {
                response.close()
                delay(backoff(attempt))
                continue
            }
            if (!response.isSuccessful && response.code != HTTP_RESUME_INCOMPLETE) {
                val message = response.body?.string()
                response.close()
                throw DriveHttpException(response.code, "Drive answered ${response.code}: $message")
            }
            return response
        }
    }

    /** Sends [request] with a current token - once more with a fresh one if Drive refused it. */
    private suspend fun send(request: Request.Builder): Response = withContext(ioDispatcher) {
        val token = access.token()
        var response = http.newCall(request.header("Authorization", "Bearer $token").build()).execute()
        if (response.code == HTTP_UNAUTHORIZED) {
            response.close()
            access.forget(token)
            response = http.newCall(request.header("Authorization", "Bearer ${access.token()}").build()).execute()
            if (response.code == HTTP_UNAUTHORIZED) {
                // Refused with a fresh token too: the access is gone - taken back - whatever Google still hands out.
                response.close()
                access.lose()
            }
        }
        response
    }
}

/** Whether Drive asked to slow down, or failed for the moment - what it asks to be sent again, a little later. */
private fun Response.isTransient(): Boolean =
    code == HTTP_TOO_MANY_REQUESTS || code >= HTTP_SERVER_ERROR ||
        (code == HTTP_FORBIDDEN && peekBody(RATE_LIMIT_PEEK_BYTES).string().contains("ratelimitexceeded", ignoreCase = true))

/** How long to wait before the [attempt]th try again: doubling from a second, with a little chance in it. */
private fun backoff(attempt: Int): Long = (FIRST_BACKOFF_MS shl (attempt - 1)) + Random.nextLong(MAX_JITTER_MS)

private fun metadata(name: String, properties: Map<String, String>, parentId: String?) = JSONObject()
    .put("name", name)
    .put("appProperties", JSONObject(properties))
    .apply { parentId?.let { put("parents", JSONArray().put(it)) } }

/** Where the next chunk starts, from the `Range: bytes=0-N` a "resume incomplete" answer carries - none yet if absent. */
private fun Response.rangeEnd(): Long = header("Range")?.substringAfterLast('-')?.toLong()?.plus(1) ?: 0L

private fun JSONArray.toDriveFiles() = (0 until length()).map { getJSONObject(it).toDriveFile() }

private fun JSONObject.toDriveFile(): DriveFile {
    val properties = optJSONObject("appProperties")
    return DriveFile(
        id = getString("id"),
        name = getString("name"),
        sizeBytes = optString("size").toLongOrNull() ?: 0L,
        createdAt = optString("createdTime").takeIf { it.isNotEmpty() }?.let { Instant.parse(it).toEpochMilli() } ?: 0L,
        properties = properties?.keys()?.asSequence()?.associateWith { properties.getString(it) }.orEmpty(),
    )
}

/** [length] bytes of [file] from [offset], read only as they are sent. */
private class FileSlice(private val file: File, private val offset: Long, private val length: Long) : RequestBody() {
    override fun contentType() = BINARY

    override fun contentLength() = length

    override fun writeTo(sink: BufferedSink) {
        RandomAccessFile(file, "r").use { source ->
            source.seek(offset)
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
            var remaining = length
            while (remaining > 0) {
                val read = source.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (read < 0) throw IOException("${file.name} ended early")
                sink.write(buffer, 0, read)
                remaining -= read
            }
        }
    }
}

package com.lhacenmed.sona.core.downloader

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

/** How many links' reads are kept - in memory, and on the device. */
private const val KeptReads = 8

/** How long a read whose streams do not say when they stop working is kept: long enough to reopen a link, short of what its site changes in. */
private const val KeptForMs = 15 * 60 * 1000L

/**
 * The links lately read, kept - in memory, and on the device, so a link read before shows, and downloads, at once, even
 * after the app restarts: each for as long as its streams work - until the expiry they state, or [KeptForMs] where they state
 * none. Kept by [linkKeyOf], so the same video shared twice is the same link.
 */
@Singleton
internal class ReadCache @Inject constructor(@ApplicationContext context: Context) {
    private class Kept(val extraction: Extraction, val readAt: Long) {
        fun holds(now: Long = System.currentTimeMillis()) = extraction.expiresAt?.let { now < it } ?: (now - readAt < KeptForMs)
    }

    private val memory = object : LinkedHashMap<String, Kept>(KeptReads, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Kept>?) = size > KeptReads
    }

    private val folder = File(context.cacheDir, "reads")

    /** [key]'s read, from memory - null where it is not there, or no longer holds. */
    fun inMemory(key: String): Extraction? = synchronized(memory) { memory[key]?.takeIf { it.holds() }?.extraction }

    /** [key]'s read, from memory or the device - null where neither holds one still good. Blocks its thread. */
    fun get(key: String): Extraction? = inMemory(key) ?: fromDevice(key)?.also { synchronized(memory) { memory[key] = it } }?.extraction

    /** Keeps [extraction] as [key]'s read. Blocks its thread. */
    fun put(key: String, extraction: Extraction) {
        val kept = Kept(extraction, System.currentTimeMillis())
        synchronized(memory) { memory[key] = kept }
        runCatching {
            folder.mkdirs()
            val saved = JSONObject().put("key", key).put("readAt", kept.readAt).put("isComplete", extraction.isComplete).put("json", extraction.json)
            // Written whole, then put in place: a save cut short never leaves half a file to read back.
            val partial = File(folder, "${fileOf(key).name}.part").apply { writeText(saved.toString()) }
            partial.renameTo(fileOf(key))
            folder.listFiles { file -> file.extension == "json" }?.sortedByDescending(File::lastModified)?.drop(KeptReads)?.forEach(File::delete)
        }
    }

    /** Lets [key]'s read go. */
    fun remove(key: String) {
        synchronized(memory) { memory.remove(key) }
        fileOf(key).delete()
    }

    private fun fromDevice(key: String): Kept? = runCatching {
        val saved = JSONObject(fileOf(key).readText()).takeIf { it.getString("key") == key } ?: return null
        Kept(extractionOf(saved.getString("json"), saved.getBoolean("isComplete")), saved.getLong("readAt")).takeIf { it.holds() }
    }.getOrNull()

    private fun fileOf(key: String): File {
        val hash = MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(folder, "$hash.json")
    }
}

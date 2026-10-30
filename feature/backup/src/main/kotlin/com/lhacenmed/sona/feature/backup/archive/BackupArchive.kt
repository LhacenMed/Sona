package com.lhacenmed.sona.feature.backup.archive

import com.lhacenmed.sona.feature.backup.BACKUP_FORMAT_VERSION
import com.lhacenmed.sona.feature.backup.BackedUpSetting
import com.lhacenmed.sona.feature.backup.BackupLibrary
import com.lhacenmed.sona.feature.backup.BackupManifest
import com.lhacenmed.sona.feature.backup.backedUpSettingOf
import com.lhacenmed.sona.feature.backup.media.fingerprintOf
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.json.Json

private const val MANIFEST = "manifest.json"
private const val LIBRARY = "library.json"
private const val SETTINGS = "settings.json"
private const val FONTS = "files/fonts"

private val json = Json { ignoreUnknownKeys = true }

/** Where a backup keeps the files of the user's it carries. */
object BackupEntries {
    const val BACKGROUND = "files/background"
    fun cover(name: String) = "files/covers/$name"
    fun font(name: String) = "$FONTS/$name"
}

/** A backup made in a later format than this version reads. */
class UnsupportedBackupException : Exception()

/** A backup's contents, unpacked into [directory] as the archive held them. */
class BackupContents(
    val manifest: BackupManifest,
    val library: BackupLibrary,
    val settings: Map<String, Map<String, Any>>,
    private val directory: File,
) {
    /** The backup's copy of [entry], or null where it has none. */
    fun file(entry: String): File? = File(directory, entry).takeIf { it.isFile }

    /** The custom fonts the backup carries. */
    val fonts: List<File> get() = File(directory, FONTS).listFiles()?.filter { it.isFile }.orEmpty()
}

/**
 * A backup as a file: a zip of what it says of itself, the library, the settings, and the user's own files -
 * playlist covers, the player's background, fonts - each opened only as it is written.
 */
object BackupArchive {

    fun write(
        output: OutputStream,
        manifest: BackupManifest,
        library: BackupLibrary,
        settings: Map<String, Map<String, Any>>,
        files: Map<String, () -> InputStream?>,
    ) {
        ZipOutputStream(output).use { zip ->
            zip.put(MANIFEST, json.encodeToString(manifest))
            zip.put(LIBRARY, json.encodeToString(library))
            zip.put(SETTINGS, json.encodeToString(settings.mapValues { (_, values) -> values.mapValues { backedUpSettingOf(it.value) } }))
            files.forEach { (entry, open) ->
                open()?.use { input ->
                    zip.putNextEntry(ZipEntry(entry))
                    input.copyTo(zip)
                    zip.closeEntry()
                }
            }
        }
    }

    /**
     * What a backup of [library], [settings] and [files] holds, as one fingerprint: the same for two backups only
     * when they would hold the same - whenever and however each was written.
     */
    fun contentFingerprint(
        library: BackupLibrary,
        settings: Map<String, Map<String, Any>>,
        files: Map<String, () -> InputStream?>,
    ): String {
        val sortedSettings = settings.toSortedMap().mapValues { (_, values) -> values.toSortedMap().mapValues { backedUpSettingOf(it.value) } }
        val fileFingerprints = files.toSortedMap().map { (entry, open) -> "$entry=${open()?.use(::fingerprintOf)}" }
        val content = json.encodeToString(library) + json.encodeToString(sortedSettings) + fileFingerprints.joinToString()
        return fingerprintOf(content.byteInputStream())
    }

    /**
     * Unpacks the backup read from [input] into [directory], emptied first, and reads it. An entry that would
     * land outside [directory] is refused rather than written.
     */
    fun extract(input: InputStream, directory: File): BackupContents {
        directory.deleteRecursively()
        directory.mkdirs()
        val root = directory.canonicalPath + File.separator
        ZipInputStream(input).use { zip ->
            generateSequence { zip.nextEntry }.filterNot { it.isDirectory }.forEach { entry ->
                val file = File(directory, entry.name)
                check(file.canonicalPath.startsWith(root)) { "${entry.name} lies outside the backup" }
                file.parentFile?.mkdirs()
                file.outputStream().use { zip.copyTo(it) }
            }
        }
        return read(directory)
    }

    /** The backup [extract] unpacked into [directory]. */
    fun read(directory: File): BackupContents {
        val manifest = json.decodeFromString<BackupManifest>(File(directory, MANIFEST).readText())
        if (manifest.formatVersion > BACKUP_FORMAT_VERSION) throw UnsupportedBackupException()
        return BackupContents(
            manifest = manifest,
            library = json.decodeFromString(File(directory, LIBRARY).readText()),
            settings = json.decodeFromString<Map<String, Map<String, BackedUpSetting>>>(File(directory, SETTINGS).readText())
                .mapValues { (_, values) -> values.mapValues { it.value.value } },
            directory = directory,
        )
    }
}

private fun ZipOutputStream.put(entry: String, text: String) {
    putNextEntry(ZipEntry(entry))
    write(text.toByteArray())
    closeEntry()
}

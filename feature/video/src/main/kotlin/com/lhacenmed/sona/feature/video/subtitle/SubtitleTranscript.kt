package com.lhacenmed.sona.feature.video.subtitle

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.DataReader
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.text.Cue
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.DefaultExtractorInput
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.DiscardingTrackOutput
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.text.CueDecoder
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer

/**
 * Every line of [stretches] as text, in the order they are said: one to a line, and a line said again straight after
 * itself only once - as rolling captions repeat each line under the next. Empty where none has any text.
 */
internal fun transcriptOf(stretches: List<TimedCues<Cue>>): String {
    val lines = ArrayList<String>()
    for (stretch in stretches.sortedBy { it.startUs }) {
        for (cue in stretch.cues) {
            cue.text?.lineSequence()?.map(String::trim)?.filter(String::isNotEmpty)?.forEach { line ->
                if (line != lines.lastOrNull()) lines += line
            }
        }
    }
    return lines.joinToString("\n")
}

/**
 * The lines of the video at [uri]'s own subtitle track at [indexInType] - see [com.lhacenmed.sona.feature.playback.MediaTrack.indexInType] - read
 * from the whole file by the same extractors the player reads it with, so the track is the one the player has there. Null
 * where the file cannot be read. Blocks, and is called off the main thread.
 */
@OptIn(UnstableApi::class)
internal fun embeddedSubtitleOf(context: Context, uri: Uri, indexInType: Int): List<TimedCues<Cue>>? = try {
    context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
        FileInputStream(descriptor.fileDescriptor).channel.use { channel ->
            val reader = DataReader { buffer, offset, length ->
                channel.read(ByteBuffer.wrap(buffer, offset, length)).let { if (it < 0) C.RESULT_END_OF_INPUT else it }
            }
            val size = channel.size()
            var input: ExtractorInput = DefaultExtractorInput(reader, 0, size)
            val extractor = DefaultExtractorsFactory().setTextTrackTranscodingEnabled(true)
                .createExtractors(uri, emptyMap())
                .firstOrNull { it.sniffs(input) }
                ?: return null
            val collector = CueCollector()
            extractor.init(TrackPicker(indexInType, collector))
            val position = PositionHolder()
            try {
                while (true) {
                    when (extractor.read(input, position)) {
                        Extractor.RESULT_END_OF_INPUT -> break
                        Extractor.RESULT_SEEK -> {
                            channel.position(position.position)
                            input = DefaultExtractorInput(reader, position.position, size)
                        }
                    }
                }
            } finally {
                extractor.release()
            }
            collector.stretches
        }
    }
} catch (_: IOException) {
    null
} catch (_: SecurityException) {
    null
}

@OptIn(UnstableApi::class)
private fun Extractor.sniffs(input: ExtractorInput): Boolean = try {
    sniff(input)
} catch (_: IOException) {
    false
} finally {
    input.resetPeekPosition()
}

/** Hands the text track at [indexInType] to [collector], and every other track to nothing. */
@OptIn(UnstableApi::class)
private class TrackPicker(private val indexInType: Int, private val collector: TrackOutput) : ExtractorOutput {
    private val textTrackIds = ArrayList<Int>()
    private val discarding = DiscardingTrackOutput()

    override fun track(id: Int, type: Int): TrackOutput {
        if (type != C.TRACK_TYPE_TEXT) return discarding
        if (id !in textTrackIds) textTrackIds += id
        return if (textTrackIds.indexOf(id) == indexInType) collector else discarding
    }

    override fun endTracks() = Unit

    override fun seekMap(seekMap: SeekMap) = Unit
}

/**
 * The lines of one text track, as the extractor hands them on: each sample already read into cues, the extractors being
 * made to - a track of pictures rather than text gives none.
 */
@OptIn(UnstableApi::class)
private class CueCollector : TrackOutput {
    val stretches = ArrayList<TimedCues<Cue>>()

    private val decoder = CueDecoder()
    private val pending = ByteArrayOutputStream()
    private var isCues = false

    override fun format(format: Format) {
        isCues = format.sampleMimeType == MimeTypes.APPLICATION_MEDIA3_CUES
    }

    override fun sampleData(input: DataReader, length: Int, allowEndOfInput: Boolean, sampleDataPart: Int): Int {
        val bytes = ByteArray(length)
        val read = input.read(bytes, 0, length)
        if (read == C.RESULT_END_OF_INPUT) {
            if (allowEndOfInput) return C.RESULT_END_OF_INPUT
            throw EOFException()
        }
        pending.write(bytes, 0, read)
        return read
    }

    override fun sampleData(data: ParsableByteArray, length: Int, sampleDataPart: Int) {
        pending.write(data.data, data.position, length)
        data.skipBytes(length)
    }

    override fun sampleMetadata(timeUs: Long, flags: Int, size: Int, offset: Int, cryptoData: TrackOutput.CryptoData?) {
        val bytes = pending.toByteArray()
        val start = bytes.size - offset - size
        if (isCues) decoder.decode(timeUs, bytes, start, size).let { stretches += TimedCues(it.startTimeUs, null, it.cues) }
        // What follows the sample is the start of the next.
        pending.reset()
        pending.write(bytes, start + size, offset)
    }
}

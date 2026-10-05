package com.lhacenmed.sona.feature.equalizer

import com.lhacenmed.sona.feature.playback.EqualizerCapabilities
import kotlin.math.ceil

/** The three broad tones the basic controls turn - each a group of the device's bands. ArchiveTune's `EqualizerTone`. */
enum class EqualizerTone {
    BASS,
    MIDRANGE,
    TREBLE,
}

/**
 * The bands that make up this tone: by frequency - up to 250 Hz, up to 4 kHz, above - or, on a device that
 * does not say where its bands sit, by thirds of them. ArchiveTune's `equalizerToneIndices`.
 */
internal fun EqualizerTone.bandIndices(capabilities: EqualizerCapabilities): List<Int> {
    val frequencies = capabilities.centerFrequenciesHz
    val bandCount = capabilities.bandCount
    if (bandCount <= 0) return emptyList()
    val byFrequency = frequencies.indices.filter { index ->
        when (this) {
            EqualizerTone.BASS -> frequencies[index] <= 250
            EqualizerTone.MIDRANGE -> frequencies[index] in 251..4_000
            EqualizerTone.TREBLE -> frequencies[index] > 4_000
        }
    }
    if (frequencies.any { it > 0 } && byFrequency.isNotEmpty()) return byFrequency

    val firstBoundary = ceil(bandCount / 3.0).toInt()
    val secondBoundary = ceil(bandCount * 2 / 3.0).toInt()
    val byThirds = when (this) {
        EqualizerTone.BASS -> 0 until firstBoundary
        EqualizerTone.MIDRANGE -> firstBoundary until secondBoundary
        EqualizerTone.TREBLE -> secondBoundary until bandCount
    }.toList()
    if (byThirds.isNotEmpty()) return byThirds
    return listOf(
        when (this) {
            EqualizerTone.BASS -> 0
            EqualizerTone.MIDRANGE -> (bandCount - 1) / 2
            EqualizerTone.TREBLE -> bandCount - 1
        },
    )
}

/** Where this tone sits in [levelsMb]: the average of its bands. */
internal fun EqualizerTone.levelIn(levelsMb: List<Int>, capabilities: EqualizerCapabilities): Int {
    val indices = bandIndices(capabilities)
    return indices.sumOf { levelsMb[it] } / indices.size
}

/**
 * [levelsMb] with this tone's bands moved together until they average [targetMb], each kept in the device's
 * range - so the shape within the tone is kept. ArchiveTune's `adjustToneBands`.
 */
internal fun EqualizerTone.adjust(levelsMb: List<Int>, targetMb: Int, capabilities: EqualizerCapabilities): List<Int> {
    val delta = targetMb - levelIn(levelsMb, capabilities)
    val indices = bandIndices(capabilities).toSet()
    return levelsMb.mapIndexed { index, level ->
        if (index in indices) (level + delta).coerceIn(capabilities.bandLevelRangeMb) else level
    }
}

package com.lhacenmed.sona.feature.tageditor.tags

import androidx.compose.runtime.Immutable

/** The cover a track is saved with: the one its file has, one a catalogue has, or a picture from the device. */
@Immutable
sealed interface CoverChoice {
    data object Own : CoverChoice

    /** A catalogue's cover, at the size it is embedded at. */
    data class Web(val url: String) : CoverChoice

    /** A picture from the device's gallery - scaled down to be embedded. */
    data class Device(val uri: String) : CoverChoice
}

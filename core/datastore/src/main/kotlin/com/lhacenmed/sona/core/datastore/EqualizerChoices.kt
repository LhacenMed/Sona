package com.lhacenmed.sona.core.datastore

import org.json.JSONArray
import org.json.JSONObject

/** How much of the equalizer the screen shows: ArchiveTune's `EqualizerControlMode`. */
enum class EqualizerControlMode {
    /** Presets and three broad tones. */
    BASIC,

    /** Every band, the output gain, the enhancements and the profiles. */
    ADVANCED,
}

/** Where the sound in use came from - ArchiveTune's selected profile id, typed. */
sealed interface EqualizerSelection {
    /** Every band at zero. */
    data object Flat : EqualizerSelection

    /** Tuned by hand since the last preset or profile. */
    data object Manual : EqualizerSelection

    /** The device's own preset at [index]. */
    data class SystemPreset(val index: Int) : EqualizerSelection

    /** The saved profile with this [id]. */
    data class Profile(val id: String) : EqualizerSelection
}

/**
 * What the equalizer does to the sound - everything a profile keeps. Levels and gains are in millibels,
 * strengths in thousandths, as the platform's effects take them.
 */
data class EqualizerSound(
    /** One level per band, in band order. Empty until the bands are first set. */
    val bandLevelsMb: List<Int> = emptyList(),
    val outputGainEnabled: Boolean = false,
    val outputGainMb: Int = 0,
    val bassBoostEnabled: Boolean = false,
    val bassBoostStrength: Int = 0,
    val virtualizerEnabled: Boolean = false,
    val virtualizerStrength: Int = 0,
    /** Lowers the output by the highest band's boost, so boosting does not clip. */
    val autoHeadroomEnabled: Boolean = false,
) {
    companion object {
        val OUTPUT_GAIN_RANGE_MB = -1500..1500
        val STRENGTH_RANGE = 0..1000
    }
}

/** A sound the user saved under a [name], with the bands it was tuned on - ArchiveTune's `EqProfile`. */
data class EqualizerProfile(
    val id: String,
    val name: String,
    val centerFrequenciesHz: List<Int>,
    val sound: EqualizerSound,
)

/** Everything stored about the equalizer, read as one: the screen draws all of it, and the player applies [sound]. */
data class EqualizerChoices(
    /** ArchiveTune's "sound shaping": off, the player plays untouched whatever else is set. */
    val enabled: Boolean,
    val controlMode: EqualizerControlMode,
    val selection: EqualizerSelection,
    val sound: EqualizerSound,
    /** By name, whatever its case. */
    val profiles: List<EqualizerProfile>,
)

internal fun EqualizerSelection.toStorageKey(): String =
    when (this) {
        EqualizerSelection.Flat -> "flat"
        EqualizerSelection.Manual -> "manual"
        is EqualizerSelection.SystemPreset -> "system:$index"
        is EqualizerSelection.Profile -> "profile:$id"
    }

internal fun equalizerSelectionOf(key: String?): EqualizerSelection =
    when {
        key == "manual" -> EqualizerSelection.Manual
        key?.startsWith("system:") == true ->
            key.removePrefix("system:").toIntOrNull()?.let(EqualizerSelection::SystemPreset) ?: EqualizerSelection.Manual
        key?.startsWith("profile:") == true -> EqualizerSelection.Profile(key.removePrefix("profile:"))
        else -> EqualizerSelection.Flat
    }

// Profiles are kept, and exported, in ArchiveTune's JSON - `{"profiles": [...]}` with its field names - so a
// profile moves between the two apps as a file.

internal fun encodeEqualizerProfiles(profiles: List<EqualizerProfile>): String =
    JSONObject()
        .put("profiles", JSONArray(profiles.distinctBy { it.id }.sortedBy { it.name.lowercase() }.map { it.toJson() }))
        .toString()

/** The profiles in [raw], or none when it holds none it can read. */
internal fun decodeEqualizerProfiles(raw: String?): List<EqualizerProfile> =
    raw?.takeIf(String::isNotBlank)
        ?.let { runCatching { JSONObject(it).getJSONArray("profiles").profiles() }.getOrNull() }
        .orEmpty()

/** What an imported file holds: ArchiveTune's object of profiles, or a bare list of them. */
internal fun decodeImportedEqualizerProfiles(raw: String): List<EqualizerProfile> =
    runCatching { JSONObject(raw).getJSONArray("profiles").profiles() }.getOrNull()
        ?: runCatching { JSONArray(raw).profiles() }.getOrDefault(emptyList())

private fun EqualizerProfile.toJson(): JSONObject =
    JSONObject()
        .put("id", id)
        .put("name", name)
        .put("bandCenterFreqHz", JSONArray(centerFrequenciesHz))
        .put("bandLevelsMb", JSONArray(sound.bandLevelsMb))
        .put("outputGainMb", sound.outputGainMb)
        .put("outputGainEnabled", sound.outputGainEnabled)
        .put("bassBoostStrength", sound.bassBoostStrength)
        .put("bassBoostEnabled", sound.bassBoostEnabled)
        .put("virtualizerStrength", sound.virtualizerStrength)
        .put("virtualizerEnabled", sound.virtualizerEnabled)
        .put("autoHeadroomEnabled", sound.autoHeadroomEnabled)

private fun JSONArray.profiles(): List<EqualizerProfile> =
    (0 until length()).map { getJSONObject(it).toProfile() }

private fun JSONObject.toProfile(): EqualizerProfile {
    val outputGainMb = optInt("outputGainMb").coerceIn(EqualizerSound.OUTPUT_GAIN_RANGE_MB)
    val bassBoostStrength = optInt("bassBoostStrength").coerceIn(EqualizerSound.STRENGTH_RANGE)
    val virtualizerStrength = optInt("virtualizerStrength").coerceIn(EqualizerSound.STRENGTH_RANGE)
    return EqualizerProfile(
        id = optString("id"),
        name = optString("name").trim(),
        centerFrequenciesHz = optJSONArray("bandCenterFreqHz").ints(),
        sound = EqualizerSound(
            bandLevelsMb = optJSONArray("bandLevelsMb").ints(),
            // A profile from before an effect had its own switch has it on wherever it was set.
            outputGainEnabled = optionalBoolean("outputGainEnabled") ?: (outputGainMb != 0),
            outputGainMb = outputGainMb,
            bassBoostEnabled = optionalBoolean("bassBoostEnabled") ?: (bassBoostStrength != 0),
            bassBoostStrength = bassBoostStrength,
            virtualizerEnabled = optionalBoolean("virtualizerEnabled") ?: (virtualizerStrength != 0),
            virtualizerStrength = virtualizerStrength,
            autoHeadroomEnabled = optBoolean("autoHeadroomEnabled"),
        ),
    )
}

private fun JSONObject.optionalBoolean(name: String): Boolean? = if (isNull(name)) null else optBoolean(name)

private fun JSONArray?.ints(): List<Int> = if (this == null) emptyList() else (0 until length()).map(::getInt)

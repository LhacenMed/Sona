package com.lhacenmed.sona.core.designsystem.theme.palette

import android.util.Base64
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.json.JSONObject

/**
 * The four colours a palette's scheme is grown from - one for each of Material's tonal roles.
 * ArchiveTune's `ThemeSeedPalette`.
 */
@Immutable
data class ThemeSeedPalette(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val neutral: Color,
)

/**
 * A palette written down, in ArchiveTune's two forms, so themes move between the two apps as they are.
 *
 * Stored as `seedPalette:` and the theme's JSON in URL-safe Base64 - so a custom theme and a preset's id
 * share one setting. Exported and imported as that JSON itself: a version, an optional name, and the
 * four seeds as `#AARRGGBB`.
 */
object ThemeSeedPaletteCodec {
    private const val PreferencePrefix = "seedPalette:"
    private const val Version = 1
    private const val JsonIndent = 4

    fun encodeForPreference(palette: ThemeSeedPalette, name: String? = null): String =
        PreferencePrefix + Base64.encodeToString(
            encodeAsJson(palette, name).toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP,
        )

    fun decodeFromPreference(value: String): ThemeSeedPalette? = preferenceJson(value)?.let(::decodeFromJson)

    fun extractNameFromPreference(value: String): String? = preferenceJson(value)?.let(::extractNameFromJson)

    fun encodeAsJson(palette: ThemeSeedPalette, name: String? = null): String =
        JSONObject()
            .put("version", Version)
            .put("name", name ?: JSONObject.NULL)
            .put("primary", palette.primary.toHexArgb())
            .put("secondary", palette.secondary.toHexArgb())
            .put("tertiary", palette.tertiary.toHexArgb())
            .put("neutral", palette.neutral.toHexArgb())
            .toString(JsonIndent)

    /** Only the primary seed is required; a missing one of the others is the primary again. */
    fun decodeFromJson(text: String): ThemeSeedPalette? {
        val json = parse(text) ?: return null
        val primary = json.color("primary") ?: return null
        return ThemeSeedPalette(
            primary = primary,
            secondary = json.color("secondary") ?: primary,
            tertiary = json.color("tertiary") ?: primary,
            neutral = json.color("neutral") ?: primary,
        )
    }

    fun extractNameFromJson(text: String): String? =
        parse(text)?.optString("name")?.takeIf { it.isNotBlank() && it != "null" }

    private fun preferenceJson(value: String): String? {
        if (!value.startsWith(PreferencePrefix)) return null
        return runCatching {
            Base64.decode(value.removePrefix(PreferencePrefix), Base64.URL_SAFE or Base64.NO_WRAP)
                .toString(Charsets.UTF_8)
        }.getOrNull()
    }

    private fun parse(text: String): JSONObject? =
        text.trim().takeIf { it.isNotEmpty() }?.let { runCatching { JSONObject(it) }.getOrNull() }

    private fun JSONObject.color(key: String): Color? {
        val value = optString(key).trim().takeIf { it.isNotEmpty() } ?: return null
        return runCatching {
            Color(android.graphics.Color.parseColor(if (value.startsWith("#")) value else "#$value"))
        }.getOrNull()
    }

    private fun Color.toHexArgb(): String = String.format("#%08X", toArgb())
}

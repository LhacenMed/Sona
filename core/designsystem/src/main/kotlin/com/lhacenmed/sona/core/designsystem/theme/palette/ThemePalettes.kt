package com.lhacenmed.sona.core.designsystem.theme.palette

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.lhacenmed.sona.core.designsystem.R

/** A ready-made palette: ArchiveTune's `ThemePalette`, its seeds under the id the choice is stored as. */
@Immutable
data class ThemePalette(
    val id: String,
    @StringRes val nameRes: Int,
    val seeds: ThemeSeedPalette,
) {
    constructor(id: String, @StringRes nameRes: Int, primary: Color, secondary: Color, tertiary: Color, neutral: Color) :
        this(id, nameRes, ThemeSeedPalette(primary, secondary, tertiary, neutral))
}

/** ArchiveTune's preset palettes, in its order. */
object ThemePalettes {
    val all: List<ThemePalette> = listOf(
        ThemePalette("default", R.string.palette_default, Color(0xFFED5564), Color(0xFFED5564), Color(0xFFED5564), Color(0xFFED5564)),
        ThemePalette("ocean_blue", R.string.palette_ocean_blue, Color(0xFF4A90D9), Color(0xFF4A90D9), Color(0xFF4A90D9), Color(0xFF4A90D9)),
        ThemePalette("arctic_blue", R.string.palette_arctic_blue, Color(0xFF00BFFF), Color(0xFF00BFFF), Color(0xFF00BFFF), Color(0xFF00BFFF)),
        ThemePalette("midnight_navy", R.string.palette_midnight_navy, Color(0xFF2C3E50), Color(0xFF2C3E50), Color(0xFF2C3E50), Color(0xFF2C3E50)),
        ThemePalette("sky_blue", R.string.palette_sky_blue, Color(0xFF87CEEB), Color(0xFF87CEEB), Color(0xFF87CEEB), Color(0xFF87CEEB)),
        ThemePalette("cobalt_blue", R.string.palette_cobalt_blue, Color(0xFF0047AB), Color(0xFF0047AB), Color(0xFF0047AB), Color(0xFF0047AB)),
        ThemePalette("electric_blue", R.string.palette_electric_blue, Color(0xFF7DF9FF), Color(0xFF7DF9FF), Color(0xFF7DF9FF), Color(0xFF7DF9FF)),
        ThemePalette("emerald_green", R.string.palette_emerald_green, Color(0xFF2ECC71), Color(0xFF2ECC71), Color(0xFF2ECC71), Color(0xFF2ECC71)),
        ThemePalette("teal_wave", R.string.palette_teal_wave, Color(0xFF1ABC9C), Color(0xFF1ABC9C), Color(0xFF1ABC9C), Color(0xFF1ABC9C)),
        ThemePalette("forest_green", R.string.palette_forest_green, Color(0xFF228B22), Color(0xFF228B22), Color(0xFF228B22), Color(0xFF228B22)),
        ThemePalette("spotify_green", R.string.palette_spotify_green, Color(0xFF1DB954), Color(0xFF1DB954), Color(0xFF1DB954), Color(0xFF1DB954)),
        ThemePalette("mint_fresh", R.string.palette_mint_fresh, Color(0xFF98FF98), Color(0xFF98FF98), Color(0xFF98FF98), Color(0xFF98FF98)),
        ThemePalette("olive_garden", R.string.palette_olive_garden, Color(0xFF808000), Color(0xFF808000), Color(0xFF808000), Color(0xFF808000)),
        ThemePalette("sage_green", R.string.palette_sage_green, Color(0xFF9CAF88), Color(0xFF9CAF88), Color(0xFF9CAF88), Color(0xFF9CAF88)),
        ThemePalette("sunset_orange", R.string.palette_sunset_orange, Color(0xFFE67E22), Color(0xFFE67E22), Color(0xFFE67E22), Color(0xFFE67E22)),
        ThemePalette("golden_hour", R.string.palette_golden_hour, Color(0xFFF39C12), Color(0xFFF39C12), Color(0xFFF39C12), Color(0xFFF39C12)),
        ThemePalette("warm_amber", R.string.palette_warm_amber, Color(0xFFFFBF00), Color(0xFFFFBF00), Color(0xFFFFBF00), Color(0xFFFFBF00)),
        ThemePalette("tangerine_blast", R.string.palette_tangerine_blast, Color(0xFFFF9800), Color(0xFFFF9800), Color(0xFFFF9800), Color(0xFFFF9800)),
        ThemePalette("peach", R.string.palette_peach, Color(0xFFFFDAB9), Color(0xFFFFDAB9), Color(0xFFFFDAB9), Color(0xFFFFDAB9)),
        ThemePalette("mango", R.string.palette_mango, Color(0xFFFF8243), Color(0xFFFF8243), Color(0xFFFF8243), Color(0xFFFF8243)),
        ThemePalette("royal_purple", R.string.palette_royal_purple, Color(0xFF9B59B6), Color(0xFF9B59B6), Color(0xFF9B59B6), Color(0xFF9B59B6)),
        ThemePalette("lavender_dream", R.string.palette_lavender_dream, Color(0xFFB39DDB), Color(0xFFB39DDB), Color(0xFFB39DDB), Color(0xFFB39DDB)),
        ThemePalette("grape_purple", R.string.palette_grape_purple, Color(0xFF6B5B95), Color(0xFF6B5B95), Color(0xFF6B5B95), Color(0xFF6B5B95)),
        ThemePalette("violet", R.string.palette_violet, Color(0xFFEE82EE), Color(0xFFEE82EE), Color(0xFFEE82EE), Color(0xFFEE82EE)),
        ThemePalette("amethyst", R.string.palette_amethyst, Color(0xFF9966CC), Color(0xFF9966CC), Color(0xFF9966CC), Color(0xFF9966CC)),
        ThemePalette("ultra_violet", R.string.palette_ultra_violet, Color(0xFF645394), Color(0xFF645394), Color(0xFF645394), Color(0xFF645394)),
        ThemePalette("cherry_blossom", R.string.palette_cherry_blossom, Color(0xFFFFB7C5), Color(0xFFFFB7C5), Color(0xFFFFB7C5), Color(0xFFFFB7C5)),
        ThemePalette("rose_quartz", R.string.palette_rose_quartz, Color(0xFFF7CAC9), Color(0xFFF7CAC9), Color(0xFFF7CAC9), Color(0xFFF7CAC9)),
        ThemePalette("magenta_pop", R.string.palette_magenta_pop, Color(0xFFFF00FF), Color(0xFFFF00FF), Color(0xFFFF00FF), Color(0xFFFF00FF)),
        ThemePalette("hot_pink", R.string.palette_hot_pink, Color(0xFFFF69B4), Color(0xFFFF69B4), Color(0xFFFF69B4), Color(0xFFFF69B4)),
        ThemePalette("blush", R.string.palette_blush, Color(0xFFDE5D83), Color(0xFFDE5D83), Color(0xFFDE5D83), Color(0xFFDE5D83)),
        ThemePalette("coral", R.string.palette_coral, Color(0xFFFF7F50), Color(0xFFFF7F50), Color(0xFFFF7F50), Color(0xFFFF7F50)),
        ThemePalette("bubblegum", R.string.palette_bubblegum, Color(0xFFFFC1CC), Color(0xFFFFC1CC), Color(0xFFFFC1CC), Color(0xFFFFC1CC)),
        ThemePalette("crimson_red", R.string.palette_crimson_red, Color(0xFFDC143C), Color(0xFFDC143C), Color(0xFFDC143C), Color(0xFFDC143C)),
        ThemePalette("youtube_red", R.string.palette_youtube_red, Color(0xFFFF0000), Color(0xFFFF0000), Color(0xFFFF0000), Color(0xFFFF0000)),
        ThemePalette("wine_red", R.string.palette_wine_red, Color(0xFF722F37), Color(0xFF722F37), Color(0xFF722F37), Color(0xFF722F37)),
        ThemePalette("ruby_red", R.string.palette_ruby_red, Color(0xFFE0115F), Color(0xFFE0115F), Color(0xFFE0115F), Color(0xFFE0115F)),
        ThemePalette("scarlet", R.string.palette_scarlet, Color(0xFFFF2400), Color(0xFFFF2400), Color(0xFFFF2400), Color(0xFFFF2400)),
        ThemePalette("charcoal", R.string.palette_charcoal, Color(0xFF36454F), Color(0xFF36454F), Color(0xFF36454F), Color(0xFF36454F)),
        ThemePalette("silver", R.string.palette_silver, Color(0xFFC0C0C0), Color(0xFFC0C0C0), Color(0xFFC0C0C0), Color(0xFFC0C0C0)),
        ThemePalette("slate", R.string.palette_slate, Color(0xFF708090), Color(0xFF708090), Color(0xFF708090), Color(0xFF708090)),
        ThemePalette("graphite", R.string.palette_graphite, Color(0xFF474747), Color(0xFF474747), Color(0xFF474747), Color(0xFF474747)),
        ThemePalette("terracotta", R.string.palette_terracotta, Color(0xFFE2725B), Color(0xFFE2725B), Color(0xFFE2725B), Color(0xFFE2725B)),
        ThemePalette("coffee", R.string.palette_coffee, Color(0xFF6F4E37), Color(0xFF6F4E37), Color(0xFF6F4E37), Color(0xFF6F4E37)),
        ThemePalette("mocha", R.string.palette_mocha, Color(0xFF967969), Color(0xFF967969), Color(0xFF967969), Color(0xFF967969)),
        ThemePalette("sand", R.string.palette_sand, Color(0xFFC2B280), Color(0xFFC2B280), Color(0xFFC2B280), Color(0xFFC2B280)),
        ThemePalette("clay", R.string.palette_clay, Color(0xFFB66A50), Color(0xFFB66A50), Color(0xFFB66A50), Color(0xFFB66A50)),
        ThemePalette("pastel_pink", R.string.palette_pastel_pink, Color(0xFFFFD1DC), Color(0xFFFFD1DC), Color(0xFFFFD1DC), Color(0xFFFFD1DC)),
        ThemePalette("pastel_blue", R.string.palette_pastel_blue, Color(0xFFAEC6CF), Color(0xFFAEC6CF), Color(0xFFAEC6CF), Color(0xFFAEC6CF)),
        ThemePalette("pastel_green", R.string.palette_pastel_green, Color(0xFF77DD77), Color(0xFF77DD77), Color(0xFF77DD77), Color(0xFF77DD77)),
        ThemePalette("pastel_yellow", R.string.palette_pastel_yellow, Color(0xFFFDFD96), Color(0xFFFDFD96), Color(0xFFFDFD96), Color(0xFFFDFD96)),
        ThemePalette("pastel_purple", R.string.palette_pastel_purple, Color(0xFFB19CD9), Color(0xFFB19CD9), Color(0xFFB19CD9), Color(0xFFB19CD9)),
        ThemePalette("neon_green", R.string.palette_neon_green, Color(0xFF39FF14), Color(0xFF39FF14), Color(0xFF39FF14), Color(0xFF39FF14)),
        ThemePalette("neon_pink", R.string.palette_neon_pink, Color(0xFFFF10F0), Color(0xFFFF10F0), Color(0xFFFF10F0), Color(0xFFFF10F0)),
        ThemePalette("neon_blue", R.string.palette_neon_blue, Color(0xFF00F5FF), Color(0xFF00F5FF), Color(0xFF00F5FF), Color(0xFF00F5FF)),
        ThemePalette("neon_orange", R.string.palette_neon_orange, Color(0xFFFF5F1F), Color(0xFFFF5F1F), Color(0xFFFF5F1F), Color(0xFFFF5F1F)),
        ThemePalette("cyberpunk", R.string.palette_cyberpunk, Color(0xFFFF00FF), Color(0xFFFF00FF), Color(0xFFFF00FF), Color(0xFFFF00FF)),
        ThemePalette("synthwave", R.string.palette_synthwave, Color(0xFFFF6EC7), Color(0xFFFF6EC7), Color(0xFFFF6EC7), Color(0xFFFF6EC7)),
        ThemePalette("ocean", R.string.palette_ocean, Color(0xFF006994), Color(0xFF006994), Color(0xFF006994), Color(0xFF006994)),
        ThemePalette("forest", R.string.palette_forest, Color(0xFF0B3D0B), Color(0xFF0B3D0B), Color(0xFF0B3D0B), Color(0xFF0B3D0B)),
        ThemePalette("autumn", R.string.palette_autumn, Color(0xFFD2691E), Color(0xFFD2691E), Color(0xFFD2691E), Color(0xFFD2691E)),
        ThemePalette("winter", R.string.palette_winter, Color(0xFFADD8E6), Color(0xFFADD8E6), Color(0xFFADD8E6), Color(0xFFADD8E6)),
        ThemePalette("spring", R.string.palette_spring, Color(0xFF98FB98), Color(0xFF98FB98), Color(0xFF98FB98), Color(0xFF98FB98)),
        ThemePalette("summer", R.string.palette_summer, Color(0xFFFFD700), Color(0xFFFFD700), Color(0xFFFFD700), Color(0xFFFFD700)),
        ThemePalette("twilight", R.string.palette_twilight, Color(0xFF4B0082), Color(0xFF4B0082), Color(0xFF4B0082), Color(0xFF4B0082)),
        ThemePalette("aurora", R.string.palette_aurora, Color(0xFF00FF7F), Color(0xFF00FF7F), Color(0xFF00FF7F), Color(0xFF00FF7F)),
        ThemePalette("candy", R.string.palette_candy, Color(0xFFFF69B4), Color(0xFFFF69B4), Color(0xFFFF69B4), Color(0xFFFF69B4)),
        ThemePalette("rainbow", R.string.palette_rainbow, Color(0xFFFF0000), Color(0xFFFF0000), Color(0xFFFF0000), Color(0xFFFF0000)),
    )

    val Default: ThemePalette = all.first()

    fun findById(id: String): ThemePalette? = all.find { it.id == id }

    /**
     * The seeds a stored palette choice stands for: a custom theme's own, a preset's, or - with nothing
     * chosen, or a choice no longer offered - the default palette's.
     */
    fun seedsOf(storedPalette: String?): ThemeSeedPalette =
        storedPalette?.let { ThemeSeedPaletteCodec.decodeFromPreference(it) ?: findById(it)?.seeds }
            ?: Default.seeds
}

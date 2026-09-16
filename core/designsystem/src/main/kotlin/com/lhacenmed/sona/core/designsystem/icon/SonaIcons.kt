package com.lhacenmed.sona.core.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.common.cover.DefaultCover

/**
 * The icons the library is drawn with - Auxio's own, which are Material Symbols outlined glyphs - so a
 * section, its loading state and a missing cover all speak one visual language.
 *
 * Filled white, as Auxio's drawables are, so whatever draws one tints it.
 */
object SonaIcons {

    /** Auxio's `ic_song_48`. */
    val Song: ImageVector by lazy {
        materialSymbol(
            name = "Song",
            pathData = "M393,840Q330,840 286.5,796.5Q243,753 243,690Q243,627 286.5,583.5Q330,540 393,540Q421,540 443.5,548Q466,556 483,570L483,120L717,120L717,255L543,255L543,690Q543,753 499.5,796.5Q456,840 393,840Z",
        )
    }

    /** Auxio's `ic_album_48`. */
    val Album: ImageVector by lazy {
        materialSymbol(
            name = "Album",
            pathData = "M480,644Q550,644 600,596.5Q650,549 650,480Q650,409 600.5,359.5Q551,310 480,310Q411,310 363.5,360Q316,410 316,480Q316,549 363.5,596.5Q411,644 480,644ZM480,520Q463,520 451.5,508.5Q440,497 440,480Q440,463 451.5,451.5Q463,440 480,440Q497,440 508.5,451.5Q520,463 520,480Q520,497 508.5,508.5Q497,520 480,520ZM480,880Q398,880 325,848.5Q252,817 197.5,762.5Q143,708 111.5,635Q80,562 80,480Q80,397 111.5,324Q143,251 197.5,197Q252,143 325,111.5Q398,80 480,80Q563,80 636,111.5Q709,143 763,197Q817,251 848.5,324Q880,397 880,480Q880,562 848.5,635Q817,708 763,762.5Q709,817 636,848.5Q563,880 480,880ZM480,820Q622,820 721,720.5Q820,621 820,480Q820,338 721,239Q622,140 480,140Q339,140 239.5,239Q140,338 140,480Q140,621 239.5,720.5Q339,820 480,820ZM480,480Q480,480 480,480Q480,480 480,480Q480,480 480,480Q480,480 480,480Q480,480 480,480Q480,480 480,480Q480,480 480,480Q480,480 480,480Z",
        )
    }

    /** Auxio's `ic_artist_48`. */
    val Artist: ImageVector by lazy {
        materialSymbol(
            name = "Artist",
            pathData = "M0,720L0,667Q0,628.43 41.5,604.22Q83,580 150.38,580Q162.54,580 173.77,580.5Q185,581 196,582.65Q188,600 184,617.82Q180,635.63 180,655L180,720L0,720ZM240,720L240,655Q240,623 257.5,596.5Q275,570 307,550Q339,530 383.5,520Q428,510 480,510Q533,510 577.5,520Q622,530 654,550Q686,570 703,596.5Q720,623 720,655L720,720L240,720ZM780,720L780,655Q780,635.14 776.5,617.57Q773,600 765,582.73Q776,581 787.17,580.5Q798.34,580 810,580Q877.5,580 918.75,603.77Q960,627.54 960,667L960,720L780,720ZM300,660L660,660L660,654Q660,617 609.5,593.5Q559,570 480,570Q401,570 350.5,593.5Q300,617 300,655L300,660ZM149.57,550Q121,550 100.5,529.44Q80,508.87 80,480Q80,451 100.56,430.5Q121.13,410 150,410Q179,410 199.5,430.5Q220,451 220,480.43Q220,509 199.5,529.5Q179,550 149.57,550ZM809.57,550Q781,550 760.5,529.44Q740,508.87 740,480Q740,451 760.56,430.5Q781.13,410 810,410Q839,410 859.5,430.5Q880,451 880,480.43Q880,509 859.5,529.5Q839,550 809.57,550ZM480,480Q430,480 395,445Q360,410 360,360Q360,309 395,274.5Q430,240 480,240Q531,240 565.5,274.5Q600,309 600,360Q600,410 565.5,445Q531,480 480,480ZM480.35,420Q506,420 523,402.65Q540,385.3 540,359.65Q540,334 522.85,317Q505.7,300 480.35,300Q455,300 437.5,317.15Q420,334.3 420,359.65Q420,385 437.35,402.5Q454.7,420 480.35,420ZM480,660L480,660Q480,660 480,660Q480,660 480,660Q480,660 480,660Q480,660 480,660L480,660ZM480,360Q480,360 480,360Q480,360 480,360Q480,360 480,360Q480,360 480,360Q480,360 480,360Q480,360 480,360Q480,360 480,360Q480,360 480,360Z",
        )
    }

    /** Auxio's `ic_genre_48`. */
    val Genre: ImageVector by lazy {
        materialSymbol(
            name = "Genre",
            pathData = "M215,843Q181.17,843 148.08,831.5Q115,820 90,794Q125,782 140,759Q155,736 155,697Q155,653.25 185.68,622.62Q216.35,592 260.18,592Q304,592 334.5,622.62Q365,653.25 365,697Q365,761 321.5,802Q278,843 215,843ZM215,783Q250,783 277.5,758Q305,733 305,697Q305,677 292.5,664.5Q280,652 260,652Q240,652 227.5,664.5Q215,677 215,697Q215,736 206.5,754.5Q198,773 175,777Q181,778 195,780.5Q209,783 215,783ZM445,606L355,511L731,135Q745,121 762,120.5Q779,120 794,135L823,164Q838,179 837.5,196.5Q837,214 823,228L445,606ZM260,697Q260,697 260,697Q260,697 260,697Q260,697 260,697Q260,697 260,697Q260,697 260,697Q260,697 260,697Q260,697 260,697Q260,697 260,697Q260,697 260,697Q260,697 260,697Z",
        )
    }

    /** Auxio's `ic_playlist_48`. */
    val Playlist: ImageVector by lazy {
        materialSymbol(
            name = "Playlist",
            pathData = "M120,630L120,570L426,570L426,630L120,630ZM120,465L120,405L593,405L593,465L120,465ZM120,300L120,240L593,240L593,300L120,300ZM662,840L662,518L880,679L662,840Z",
        )
    }

    /** Material Symbols outlined `folder_48px` - Auxio has no folders section, so no folder icon of its own. */
    val Folder: ImageVector by lazy {
        materialSymbol(
            name = "Folder",
            pathData = "M140,800Q116,800 98,781.5Q80,763 80,740L80,220Q80,197 98,178.5Q116,160 140,160L421,160L481,220L820,220Q843,220 861.5,238.5Q880,257 880,280L880,740Q880,763 861.5,781.5Q843,800 820,800L140,800ZM140,740L820,740Q820,740 820,740Q820,740 820,740L820,280Q820,280 820,280Q820,280 820,280L456,280L396,220L140,220Q140,220 140,220Q140,220 140,220L140,740Q140,740 140,740Q140,740 140,740ZM140,740Q140,740 140,740Q140,740 140,740L140,220Q140,220 140,220Q140,220 140,220L140,220L140,280L140,280Q140,280 140,280Q140,280 140,280L140,740Q140,740 140,740Q140,740 140,740L140,740Z",
        )
    }

    /** What stands in for a missing cover: [DefaultCover]'s glyph, the one every default cover shares. */
    val CoverPlaceholder: ImageVector by lazy {
        icon(
            name = "CoverPlaceholder",
            size = 24.dp,
            viewportSize = DefaultCover.GLYPH_VIEWPORT_SIZE,
            pathData = DefaultCover.GLYPH_PATH_DATA,
        )
    }

    /**
     * Auxio's `ic_check_20`: the badge on a selected cover.
     *
     * The tick is drawn small within its 20dp square, which is the whole of the badge - that built-in
     * room around it is what sets the tick's size, so the drawable fills the badge rather than being
     * inset inside it.
     */
    val Check: ImageVector by lazy {
        icon(
            name = "Check",
            size = 20.dp,
            viewportSize = 20f,
            pathData = "M8.229,14.062 L4.708,10.521 5.75,9.479 8.229,11.938 14.25,5.938 15.292,7Z",
        )
    }
}

/** A 48dp Material Symbols glyph, drawn on its 960-unit grid. */
private fun materialSymbol(name: String, pathData: String): ImageVector =
    icon(name = name, size = 48.dp, viewportSize = 960f, pathData = pathData)

private fun icon(name: String, size: Dp, viewportSize: Float, pathData: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = size,
        defaultHeight = size,
        viewportWidth = viewportSize,
        viewportHeight = viewportSize,
    )
        .addPath(pathData = addPathNodes(pathData), fill = SolidColor(Color.White))
        .build()

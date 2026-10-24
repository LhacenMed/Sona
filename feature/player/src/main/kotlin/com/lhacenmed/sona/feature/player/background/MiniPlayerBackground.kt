package com.lhacenmed.sona.feature.player.background

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.lhacenmed.sona.core.datastore.MiniPlayerBackgroundStyle

/**
 * What the mini player is drawn over: ArchiveTune's `MiniPlayerBackground`. A cover style with no cover
 * colours to draw from - nothing playing, or a cover that cannot be read - is drawn as the theme's. Every style
 * is drawn in the theme's tones, as the expanded player's are - see [PlayerBackground] - veiled in the mini
 * player's own surface, so the theme's text reads over each.
 */
@Composable
internal fun MiniPlayerBackground(
    style: MiniPlayerBackgroundStyle,
    gradientColors: List<Color>,
    modifier: Modifier = Modifier,
) {
    val surface = MaterialTheme.colorScheme.surfaceContainerHigh
    val first = gradientColors.firstOrNull()
    if (style == MiniPlayerBackgroundStyle.FOLLOW_THEME || first == null) {
        Box(modifier = modifier.background(surface))
        return
    }
    val second = gradientColors.getOrElse(1) { first }
    val third = gradientColors.getOrElse(2) { second }
    val fourth = gradientColors.getOrElse(3) { first }

    when (style) {
        MiniPlayerBackgroundStyle.GRADIENT ->
            Box(modifier = modifier) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0f to first.copy(alpha = 0.95f),
                                    0.52f to second.copy(alpha = 0.82f),
                                    1f to third.copy(alpha = 0.72f),
                                ),
                            ),
                        ),
                )
                Box(modifier = Modifier.fillMaxSize().background(surface.copy(alpha = 0.32f)))
            }

        MiniPlayerBackgroundStyle.GLOW ->
            Box(
                modifier = modifier.drawWithCache {
                    val width = size.width
                    val height = size.height
                    val startGlow = Brush.radialGradient(
                        colors = listOf(first.copy(alpha = 0.82f), first.copy(alpha = 0.38f), Color.Transparent),
                        center = Offset(width * 0.12f, height * 0.42f),
                        radius = width * 0.72f,
                    )
                    val endGlow = Brush.radialGradient(
                        colors = listOf(second.copy(alpha = 0.78f), second.copy(alpha = 0.34f), Color.Transparent),
                        center = Offset(width * 0.88f, height * 0.58f),
                        radius = width * 0.72f,
                    )
                    val topGlow = Brush.radialGradient(
                        colors = listOf(third.copy(alpha = 0.58f), Color.Transparent),
                        center = Offset(width * 0.52f, height * 0.05f),
                        radius = width * 0.54f,
                    )
                    val bottomGlow = Brush.radialGradient(
                        colors = listOf(fourth.copy(alpha = 0.46f), Color.Transparent),
                        center = Offset(width * 0.46f, height * 1.05f),
                        radius = width * 0.54f,
                    )
                    onDrawBehind {
                        drawRect(surface)
                        drawRect(startGlow)
                        drawRect(endGlow)
                        drawRect(topGlow)
                        drawRect(bottomGlow)
                        drawRect(surface.copy(alpha = 0.24f))
                    }
                },
            )

        MiniPlayerBackgroundStyle.FOLLOW_THEME -> Unit
    }
}

package com.lhacenmed.sona.feature.video.audio

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.component.SonaCoverBackdrop
import com.lhacenmed.sona.feature.video.R
import com.lhacenmed.sona.feature.video.VideoPlayerTokens

/** How long the disc takes to turn once. */
private const val DiscTurnMs = 8_000

private val DiscSize = 240.dp

/**
 * What shows in place of the picture while a video plays for its sound alone: a disc labelled with the video's
 * own cover, turning while it plays, and the way back to the picture.
 */
@Composable
internal fun AudioOnlyContent(
    coverArtUri: String?,
    isPlaying: Boolean,
    onBackToVideo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        modifier = modifier.fillMaxSize().padding(horizontal = 72.dp),
    ) {
        Disc(coverArtUri, isPlaying)
        Text(
            text = stringResource(R.string.video_audio_message),
            style = MaterialTheme.typography.bodyLarge,
            color = VideoPlayerTokens.ContentColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 360.dp),
        )
        Button(
            onClick = onBackToVideo,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Text(stringResource(R.string.video_back_to_video), modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
        }
    }
}

/** The record, with its grooves drawn once and the label turning with it - stopped where it was while paused. */
@Composable
private fun Disc(coverArtUri: String?, isPlaying: Boolean) {
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        while (true) {
            rotation.animateTo(rotation.value + 360f, tween(DiscTurnMs, easing = LinearEasing))
            rotation.snapTo(rotation.value % 360f)
        }
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(DiscSize)
            // Read as the disc is drawn, so turning it draws again without composing anything.
            .graphicsLayer { rotationZ = rotation.value }
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFF2A2A2A), Color(0xFF0E0E0E))))
            .drawBehind {
                val groove = Color.White.copy(alpha = 0.06f)
                for (step in 1..6) {
                    drawCircle(groove, radius = size.minDimension / 2f * (0.45f + step * 0.08f), style = Stroke(1.dp.toPx()))
                }
            },
    ) {
        // Filled and cut round, whatever the shape of the video's frame.
        SonaCoverBackdrop(
            coverArtUri = coverArtUri,
            modifier = Modifier.fillMaxWidth(0.4f).aspectRatio(1f).clip(CircleShape),
        )
        // The spindle hole.
        Box(Modifier.size(10.dp).clip(CircleShape).background(Color.Black))
    }
}

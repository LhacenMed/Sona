package com.lhacenmed.sona.feature.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

/** A slider track drawn as one thick rounded line. Ported from ArchiveTune's `PlayerSliderTrack`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerSliderTrack(
    sliderState: SliderState,
    colors: SliderColors,
    trackHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val valueRange = sliderState.valueRange
    Canvas(
        modifier
            .fillMaxWidth()
            .height(trackHeight),
    ) {
        drawTrack(
            activeRangeEnd =
                calcFraction(
                    valueRange.start,
                    valueRange.endInclusive,
                    sliderState.value.coerceIn(valueRange.start, valueRange.endInclusive),
                ),
            inactiveTrackColor = colors.inactiveTrackColor,
            activeTrackColor = colors.activeTrackColor,
            trackHeight = trackHeight,
        )
    }
}

private fun DrawScope.drawTrack(
    activeRangeEnd: Float,
    inactiveTrackColor: Color,
    activeTrackColor: Color,
    trackHeight: Dp,
) {
    val isRtl = layoutDirection == LayoutDirection.Rtl
    val sliderLeft = Offset(0f, center.y)
    val sliderRight = Offset(size.width, center.y)
    val sliderStart = if (isRtl) sliderRight else sliderLeft
    val sliderEnd = if (isRtl) sliderLeft else sliderRight
    val trackStrokeWidth = trackHeight.toPx()
    drawLine(
        inactiveTrackColor,
        sliderStart,
        sliderEnd,
        trackStrokeWidth,
        StrokeCap.Round,
    )
    val sliderValueEnd =
        Offset(
            sliderStart.x +
                (sliderEnd.x - sliderStart.x) * activeRangeEnd,
            center.y,
        )
    drawLine(
        activeTrackColor,
        sliderStart,
        sliderValueEnd,
        trackStrokeWidth,
        StrokeCap.Round,
    )
}

private fun calcFraction(
    a: Float,
    b: Float,
    pos: Float,
) = (if (b - a == 0f) 0f else (pos - a) / (b - a)).coerceIn(0f, 1f)

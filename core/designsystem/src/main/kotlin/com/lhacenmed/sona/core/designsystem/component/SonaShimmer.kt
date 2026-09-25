package com.lhacenmed.sona.core.designsystem.component

import android.graphics.Matrix
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import com.lhacenmed.sona.core.designsystem.effect.SonaEffects
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.tan

/** One sweep of the highlight, start to finish: Shimmer's default `animationDuration`. */
private const val SHIMMER_DURATION_MILLIS = 1_000L

/** How far the highlight leans from vertical: Shimmer's default `tilt`. */
private const val SHIMMER_TILT_DEGREES = 20f

/**
 * The gradient's alpha from edge to edge. Only alpha matters, since it masks what is drawn beneath:
 * Shimmer's default base colour `0x4cffffff`, rising to its opaque highlight in the middle.
 */
private val ShimmerColors = Color.White.copy(alpha = 0x4C / 255f).let { base ->
    listOf(base, Color.White, Color.White, base)
}

/** Where those colours sit: Shimmer's `updatePositions()` for its default dropoff of 0.5 and no intensity. */
private val ShimmerStops = listOf(0.25f, 0.4995f, 0.5005f, 0.75f)

/**
 * Sweeps a shimmer across whatever this modifies - Facebook Shimmer's default alpha highlight, the one
 * YTDLnis draws its loading cards with, reproduced exactly.
 *
 * Nothing is painted on top: what is drawn beneath is masked down to a third of its opacity, and a
 * tilted band of full opacity travels across it from one edge to the other, then starts over. Being a
 * mask, it takes on the shape and colours of the content itself, so a shimmering shape needs no
 * colours of its own. The sweep runs in the draw phase alone and never recomposes anything.
 */
fun Modifier.shimmer(): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen } then ShimmerElement

private data object ShimmerElement : ModifierNodeElement<ShimmerNode>() {
    override fun create() = ShimmerNode()

    override fun update(node: ShimmerNode) = Unit
}

private class ShimmerNode : Modifier.Node(), DrawModifierNode {

    /** How far through the current sweep the highlight is, from 0 to 1. */
    private var progress = 0f

    private val gradientMatrix = Matrix()
    private lateinit var gradient: Shader
    private lateinit var gradientBrush: Brush
    private var gradientWidth = -1f

    override fun onAttach() {
        coroutineScope.launch {
            // It sweeps on frame time, which no animation pace reaches, so it asks itself: with nothing
            // moving on its own, the highlight rests off the edge and the content stays dimmed, still.
            snapshotFlow { SonaEffects.shouldAnimate }.collectLatest { shouldAnimate ->
                if (!shouldAnimate) {
                    progress = 0f
                    invalidateDraw()
                    return@collectLatest
                }
                val startMillis = withInfiniteAnimationFrameMillis { it }
                while (true) {
                    withInfiniteAnimationFrameMillis { frameMillis ->
                        progress = (frameMillis - startMillis) % SHIMMER_DURATION_MILLIS / SHIMMER_DURATION_MILLIS.toFloat()
                    }
                    invalidateDraw()
                }
            }
        }
    }

    // Shimmer's `ShimmerDrawable.draw`: a horizontal gradient as wide as the content, rotated about the
    // centre, travelling from fully off one edge to fully off the other.
    override fun ContentDrawScope.draw() {
        drawContent()
        if (gradientWidth != size.width) {
            gradient = LinearGradientShader(
                from = Offset.Zero,
                to = Offset(size.width, 0f),
                colors = ShimmerColors,
                colorStops = ShimmerStops,
            )
            gradientBrush = ShaderBrush(gradient)
            gradientWidth = size.width
        }
        val tiltTan = tan(Math.toRadians(SHIMMER_TILT_DEGREES.toDouble())).toFloat()
        val travel = size.width + tiltTan * size.height
        gradientMatrix.setRotate(SHIMMER_TILT_DEGREES, size.width / 2f, size.height / 2f)
        gradientMatrix.preTranslate(-travel + 2f * travel * progress, 0f)
        gradient.setLocalMatrix(gradientMatrix)
        drawRect(brush = gradientBrush, blendMode = BlendMode.DstIn)
    }
}

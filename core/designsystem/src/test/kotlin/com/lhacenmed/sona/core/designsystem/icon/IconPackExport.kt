package com.lhacenmed.sona.core.designsystem.icon

import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Writes every icon of [SonaIcons] out as the icon pack takes it - see [SonaIconPack]: a vector drawable named as the pack names
 * it, `xml/icon_delete.xml`, and the same drawing as an SVG, `svg/icon_delete.svg`, for a designer's tools. A pack redrawn from
 * these replaces the icons by dropping the drawables into a module's `res/drawable`. [PlayerIcons] are such drawables already -
 * core/common's `icon_player_*.xml`.
 *
 * Run only when given a folder to write into:
 * `ICON_PACK_DIR=/path/to/pack ./gradlew :core:designsystem:testDebugUnitTest --tests "*IconPackExport*"`.
 */
class IconPackExport {
    @Test
    fun export() {
        val folder = System.getenv("ICON_PACK_DIR")?.let(::File)
        assumeTrue("ICON_PACK_DIR names the folder the pack is written into", folder != null)
        val xml = File(folder, "xml").apply { mkdirs() }
        val svg = File(folder, "svg").apply { mkdirs() }
        val icons = iconsOf(SonaIcons, "icon_")
        icons.forEach { (name, icon) ->
            File(xml, "$name.xml").writeText(vectorDrawableOf(icon))
            File(svg, "$name.svg").writeText(svgOf(icon))
        }
        File(folder, "icons.txt").writeText(icons.keys.joinToString("\n", postfix = "\n"))
    }
}

/** Every icon of [registry], by the name the pack gives it: [prefix], then its name in snake case - as [PackIcon] names it. */
private fun iconsOf(registry: Any, prefix: String): Map<String, ImageVector> =
    registry.javaClass.declaredMethods
        .filter { it.returnType == ImageVector::class.java && it.parameterCount == 0 && it.name.startsWith("get") }
        .sortedBy { it.name }
        .associate { getter ->
            val name = getter.name.removePrefix("get").replace(Regex("(?<=[a-z0-9])(?=[A-Z])"), "_").lowercase()
            (prefix + name) to getter.invoke(registry) as ImageVector
        }

private fun vectorDrawableOf(icon: ImageVector): String = buildString {
    appendLine("""<vector xmlns:android="http://schemas.android.com/apk/res/android"""")
    appendLine("""    android:width="${icon.defaultWidth.value.figure()}dp"""")
    appendLine("""    android:height="${icon.defaultHeight.value.figure()}dp"""")
    appendLine("""    android:viewportWidth="${icon.viewportWidth.figure()}"""")
    append("""    android:viewportHeight="${icon.viewportHeight.figure()}"""")
    if (icon.autoMirror) append("""${"\n"}    android:autoMirrored="true"""")
    appendLine(">")
    icon.root.forEach { appendDrawableNode(it, "    ") }
    appendLine("</vector>")
}

private fun StringBuilder.appendDrawableNode(node: VectorNode, indent: String) {
    when (node) {
        is VectorGroup -> {
            appendLine("$indent<group${groupAttributes(node, " android:")}>")
            node.forEach { appendDrawableNode(it, "$indent    ") }
            appendLine("$indent</group>")
        }

        is VectorPath -> {
            append("$indent<path android:pathData=\"${pathDataOf(node.pathData)}\"")
            // Drawn white, as every icon is: whatever shows it tints it.
            if (node.fill != null) append(" android:fillColor=\"#FFFFFFFF\"")
            if (node.fill != null && node.fillAlpha != 1f) append(" android:fillAlpha=\"${node.fillAlpha.figure()}\"")
            if (node.pathFillType == PathFillType.EvenOdd) append(" android:fillType=\"evenOdd\"")
            if (node.stroke != null && node.strokeLineWidth > 0f) {
                append(" android:strokeColor=\"#FFFFFFFF\" android:strokeWidth=\"${node.strokeLineWidth.figure()}\"")
                append(" android:strokeLineCap=\"${node.strokeLineCap.attribute()}\" android:strokeLineJoin=\"${node.strokeLineJoin.attribute()}\"")
                if (node.strokeAlpha != 1f) append(" android:strokeAlpha=\"${node.strokeAlpha.figure()}\"")
            }
            appendLine(" />")
        }
    }
}

private fun svgOf(icon: ImageVector): String = buildString {
    val width = icon.viewportWidth.figure()
    val height = icon.viewportHeight.figure()
    appendLine("""<svg xmlns="http://www.w3.org/2000/svg" width="${icon.defaultWidth.value.figure()}" height="${icon.defaultHeight.value.figure()}" viewBox="0 0 $width $height">""")
    icon.root.forEach { appendSvgNode(it, "  ") }
    appendLine("</svg>")
}

private fun StringBuilder.appendSvgNode(node: VectorNode, indent: String) {
    when (node) {
        is VectorGroup -> {
            val transform = listOfNotNull(
                "translate(${node.translationX.figure()} ${node.translationY.figure()})".takeIf { node.translationX != 0f || node.translationY != 0f },
                "rotate(${node.rotation.figure()} ${node.pivotX.figure()} ${node.pivotY.figure()})".takeIf { node.rotation != 0f },
                "scale(${node.scaleX.figure()} ${node.scaleY.figure()})".takeIf { node.scaleX != 1f || node.scaleY != 1f },
            ).joinToString(" ")
            appendLine("$indent<g${if (transform.isEmpty()) "" else " transform=\"$transform\""}>")
            node.forEach { appendSvgNode(it, "$indent  ") }
            appendLine("$indent</g>")
        }

        is VectorPath -> {
            append("$indent<path d=\"${pathDataOf(node.pathData)}\"")
            append(" fill=\"${if (node.fill != null) "currentColor" else "none"}\"")
            if (node.pathFillType == PathFillType.EvenOdd) append(" fill-rule=\"evenodd\"")
            if (node.stroke != null && node.strokeLineWidth > 0f) {
                append(" stroke=\"currentColor\" stroke-width=\"${node.strokeLineWidth.figure()}\"")
                append(" stroke-linecap=\"${node.strokeLineCap.attribute()}\" stroke-linejoin=\"${node.strokeLineJoin.attribute()}\"")
            }
            appendLine(" />")
        }
    }
}

/** A group's transform, as a drawable's attributes - each written where it moves anything. */
private fun groupAttributes(group: VectorGroup, prefix: String): String = listOfNotNull(
    "${prefix}rotation=\"${group.rotation.figure()}\"".takeIf { group.rotation != 0f },
    "${prefix}pivotX=\"${group.pivotX.figure()}\"".takeIf { group.pivotX != 0f },
    "${prefix}pivotY=\"${group.pivotY.figure()}\"".takeIf { group.pivotY != 0f },
    "${prefix}scaleX=\"${group.scaleX.figure()}\"".takeIf { group.scaleX != 1f },
    "${prefix}scaleY=\"${group.scaleY.figure()}\"".takeIf { group.scaleY != 1f },
    "${prefix}translateX=\"${group.translationX.figure()}\"".takeIf { group.translationX != 0f },
    "${prefix}translateY=\"${group.translationY.figure()}\"".takeIf { group.translationY != 0f },
).joinToString("")

/** [nodes] as SVG path data - what a drawable's `pathData` holds too. */
private fun pathDataOf(nodes: List<PathNode>): String = nodes.joinToString(" ") { node ->
    when (node) {
        is PathNode.Close -> "Z"
        is PathNode.MoveTo -> "M${f(node.x, node.y)}"
        is PathNode.RelativeMoveTo -> "m${f(node.dx, node.dy)}"
        is PathNode.LineTo -> "L${f(node.x, node.y)}"
        is PathNode.RelativeLineTo -> "l${f(node.dx, node.dy)}"
        is PathNode.HorizontalTo -> "H${node.x.figure()}"
        is PathNode.RelativeHorizontalTo -> "h${node.dx.figure()}"
        is PathNode.VerticalTo -> "V${node.y.figure()}"
        is PathNode.RelativeVerticalTo -> "v${node.dy.figure()}"
        is PathNode.CurveTo -> "C${f(node.x1, node.y1, node.x2, node.y2, node.x3, node.y3)}"
        is PathNode.RelativeCurveTo -> "c${f(node.dx1, node.dy1, node.dx2, node.dy2, node.dx3, node.dy3)}"
        is PathNode.ReflectiveCurveTo -> "S${f(node.x1, node.y1, node.x2, node.y2)}"
        is PathNode.RelativeReflectiveCurveTo -> "s${f(node.dx1, node.dy1, node.dx2, node.dy2)}"
        is PathNode.QuadTo -> "Q${f(node.x1, node.y1, node.x2, node.y2)}"
        is PathNode.RelativeQuadTo -> "q${f(node.dx1, node.dy1, node.dx2, node.dy2)}"
        is PathNode.ReflectiveQuadTo -> "T${f(node.x, node.y)}"
        is PathNode.RelativeReflectiveQuadTo -> "t${f(node.dx, node.dy)}"
        is PathNode.ArcTo -> "A${arc(node.horizontalEllipseRadius, node.verticalEllipseRadius, node.theta, node.isMoreThanHalf, node.isPositiveArc, node.arcStartX, node.arcStartY)}"
        is PathNode.RelativeArcTo -> "a${arc(node.horizontalEllipseRadius, node.verticalEllipseRadius, node.theta, node.isMoreThanHalf, node.isPositiveArc, node.arcStartDx, node.arcStartDy)}"
    }
}

private fun arc(rx: Float, ry: Float, theta: Float, large: Boolean, sweep: Boolean, x: Float, y: Float) =
    "${f(rx, ry, theta)} ${if (large) 1 else 0} ${if (sweep) 1 else 0} ${f(x, y)}"

private fun f(vararg values: Float): String = values.joinToString(" ") { it.figure() }

/** [this] as it is written: no more figures than it has, and no trailing zeros. */
private fun Float.figure(): String = toBigDecimal().stripTrailingZeros().toPlainString().let { if (it == "-0") "0" else it }

private fun StrokeCap.attribute(): String = when (this) {
    StrokeCap.Round -> "round"
    StrokeCap.Square -> "square"
    else -> "butt"
}

private fun StrokeJoin.attribute(): String = when (this) {
    StrokeJoin.Round -> "round"
    StrokeJoin.Bevel -> "bevel"
    else -> "miter"
}


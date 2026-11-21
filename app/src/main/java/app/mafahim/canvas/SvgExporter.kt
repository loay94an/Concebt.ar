package app.mafahim.canvas

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import kotlin.math.ceil
import kotlin.math.max

object SvgExporter {
    fun export(context: Context, document: DrawingDocument): Uri {
        val directory = File(context.cacheDir, "exports").apply { mkdirs() }
        val output = File(directory, "mafahim-${System.currentTimeMillis()}.svg")
        output.writeText(buildSvg(document), Charsets.UTF_8)
        return FileProvider.getUriForFile(context, "${context.packageName}.files", output)
    }

    internal fun buildSvg(document: DrawingDocument): String {
        val visibleLayers = document.layers.filter { it.visible }.associateBy { it.id }
        val drawable = document.strokes.mapNotNull { stroke ->
            val layer = visibleLayers[stroke.layerId] ?: return@mapNotNull null
            if (stroke.tool == BrushTool.MOVE) return@mapNotNull null
            val points = stroke.points.filter { it.x.isFinite() && it.y.isFinite() }
            if (points.isEmpty()) return@mapNotNull null
            val width = stroke.width.takeIf { it.isFinite() }?.coerceIn(0.5f, 100f) ?: 5f
            val opacity = ((stroke.opacity.takeIf { it.isFinite() } ?: 1f) *
                (layer.opacity.takeIf { it.isFinite() } ?: 1f)).coerceIn(0f, 1f)
            Triple(stroke, points, width to opacity)
        }

        val allPoints = drawable.flatMap { it.second }
        val padding = max(24f, (drawable.maxOfOrNull { it.third.first } ?: 5f) / 2f + 8f)
        val minX = (allPoints.minOfOrNull { it.x } ?: 0f) - padding
        val minY = (allPoints.minOfOrNull { it.y } ?: 0f) - padding
        val maxX = (allPoints.maxOfOrNull { it.x } ?: 976f) + padding
        val maxY = (allPoints.maxOfOrNull { it.y } ?: 676f) + padding
        val viewWidth = (maxX - minX).coerceAtLeast(1f)
        val viewHeight = (maxY - minY).coerceAtLeast(1f)

        return buildString {
            appendLine("""<svg xmlns="http://www.w3.org/2000/svg" width="${ceil(viewWidth).toInt()}" height="${ceil(viewHeight).toInt()}" viewBox="${fmt(minX)} ${fmt(minY)} ${fmt(viewWidth)} ${fmt(viewHeight)}">""")
            appendLine("""<rect x="${fmt(minX)}" y="${fmt(minY)}" width="${fmt(viewWidth)}" height="${fmt(viewHeight)}" fill="#ffffff"/>""")
            drawable.forEach { (stroke, points, widthAndOpacity) ->
                val (width, opacity) = widthAndOpacity
                val hex = Integer.toHexString(stroke.color).takeLast(6).padStart(6, '0')
                if (points.size == 1) {
                    val p = points.first()
                    val pressure = p.pressure.takeIf { it.isFinite() }?.coerceIn(0.05f, 1f) ?: 1f
                    appendLine("""<circle cx="${fmt(p.x)}" cy="${fmt(p.y)}" r="${fmt(width * pressure / 2f)}" fill="#$hex" opacity="${fmt(opacity)}"/>""")
                } else if (points.any { (it.pressure.takeIf { value -> value.isFinite() } ?: 1f) < 0.999f }) {
                    points.zipWithNext().forEach { (start, end) ->
                        val pressure = (((start.pressure + end.pressure) / 2f).takeIf { value -> value.isFinite() } ?: 1f).coerceIn(0.05f, 1f)
                        appendLine("""<line x1="${fmt(start.x)}" y1="${fmt(start.y)}" x2="${fmt(end.x)}" y2="${fmt(end.y)}" stroke="#$hex" stroke-width="${fmt(width * pressure)}" opacity="${fmt(opacity)}" stroke-linecap="round"/>""")
                    }
                } else {
                    val serializedPoints = points.joinToString(" ") { "${fmt(it.x)},${fmt(it.y)}" }
                    appendLine("""<polyline points="$serializedPoints" fill="none" stroke="#$hex" stroke-width="${fmt(width)}" opacity="${fmt(opacity)}" stroke-linecap="round" stroke-linejoin="round"/>""")
                }
            }
            appendLine("</svg>")
        }
    }

    private fun fmt(value: Float): String = if (value == 0f) "0" else value.toString()
}

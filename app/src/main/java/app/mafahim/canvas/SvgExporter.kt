package app.mafahim.canvas

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.Base64
import kotlin.math.ceil
import kotlin.math.max

object SvgExporter {
    fun export(context: Context, document: DrawingDocument): Uri {
        val directory = File(context.cacheDir, "exports").apply { mkdirs() }
        val output = File(directory, "mafahim-${System.currentTimeMillis()}.svg")
        val imageData = document.images.mapNotNull { image ->
            val file = File(context.filesDir, "images/${File(image.fileName).name}")
            if (file.isFile) image.fileName to "data:image/png;base64," + Base64.getEncoder().encodeToString(file.readBytes()) else null
        }.toMap()
        output.writeText(buildSvg(document, imageData), Charsets.UTF_8)
        return FileProvider.getUriForFile(context, "${context.packageName}.files", output)
    }

    internal fun buildSvg(document: DrawingDocument, imageData: Map<String, String> = emptyMap()): String {
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

        val allPoints = drawable.flatMap { it.second } + document.images.flatMap { image ->
            listOf(CanvasPoint(image.x, image.y), CanvasPoint(image.x + image.width, image.y + image.height))
        }
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
            document.images.forEach { image ->
                val data = imageData[image.fileName] ?: return@forEach
                appendLine("""<g transform="rotate(${fmt(image.rotationDegrees)} ${fmt(image.x + image.width / 2f)} ${fmt(image.y + image.height / 2f)})"><image x="${fmt(image.x)}" y="${fmt(image.y)}" width="${fmt(image.width)}" height="${fmt(image.height)}" href="$data"/></g>""")
            }
            drawable.forEach { (stroke, points, widthAndOpacity) ->
                val (width, opacity) = widthAndOpacity
                val hex = Integer.toHexString(stroke.color).takeLast(6).padStart(6, '0')
                if (points.size == 1) {
                    val p = points.first()
                    val pressureWidth = CanvasGeometry.pressureTiltWidth(p, width)
                    appendLine("""<circle cx="${fmt(p.x)}" cy="${fmt(p.y)}" r="${fmt(pressureWidth / 2f)}" fill="#$hex" opacity="${fmt(opacity)}"/>""")
                } else if (points.any { (it.pressure.takeIf { value -> value.isFinite() } ?: 1f) < 0.999f || it.tilt > 0f }) {
                    points.zipWithNext().forEach { (start, end) ->
                        val segmentWidth = (CanvasGeometry.pressureTiltWidth(start, width) + CanvasGeometry.pressureTiltWidth(end, width)) / 2f
                        appendLine("""<line x1="${fmt(start.x)}" y1="${fmt(start.y)}" x2="${fmt(end.x)}" y2="${fmt(end.y)}" stroke="#$hex" stroke-width="${fmt(segmentWidth)}" opacity="${fmt(opacity)}" stroke-linecap="round"/>""")
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

package app.mafahim.canvas

import android.content.Context
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

object PdfExporter {
    fun export(context: Context, document: DrawingDocument): Uri {
        val pdf = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(1200, 1600, 1).create()
        val page = pdf.startPage(pageInfo)
        val canvas = page.canvas
        canvas.drawColor(android.graphics.Color.WHITE)

        val visibleLayers = document.layers.filter { it.visible }.associateBy { it.id }
        val strokes = document.strokes.filter { it.layerId in visibleLayers && it.points.isNotEmpty() }
        val points = strokes.flatMap { it.points }
        val minX = points.minOfOrNull { it.x } ?: 0f
        val minY = points.minOfOrNull { it.y } ?: 0f
        val maxX = points.maxOfOrNull { it.x } ?: 1000f
        val maxY = points.maxOfOrNull { it.y } ?: 1400f
        val padding = 48f
        val scale = minOf(
            (pageInfo.pageWidth - padding * 2) / max(1f, maxX - minX),
            (pageInfo.pageHeight - padding * 2) / max(1f, maxY - minY)
        ).coerceAtMost(8f)
        canvas.save()
        canvas.translate(padding - minX * scale, padding - minY * scale)
        canvas.scale(scale, scale)

        strokes.forEach { stroke ->
            val layer = visibleLayers[stroke.layerId] ?: return@forEach
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = stroke.color
                alpha = (stroke.opacity * layer.opacity * 255f).toInt().coerceIn(0, 255)
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            if (stroke.points.size == 1) {
                val point = stroke.points.first()
                paint.style = Paint.Style.FILL
                canvas.drawCircle(point.x, point.y,
                    CanvasGeometry.pressureTiltWidth(point, stroke.width) / 2f, paint)
            } else {
                stroke.points.zipWithNext().forEach { (a, b) ->
                    paint.strokeWidth = (
                        CanvasGeometry.pressureTiltWidth(a, stroke.width) +
                            CanvasGeometry.pressureTiltWidth(b, stroke.width)
                        ) / 2f
                    val path = Path().apply {
                        moveTo(a.x, a.y)
                        lineTo(b.x, b.y)
                    }
                    canvas.drawPath(path, paint)
                }
            }
        }
        canvas.restore()
        pdf.finishPage(page)

        val directory = File(context.cacheDir, "exports").apply { mkdirs() }
        val output = File(directory, "mafahim-${System.currentTimeMillis()}.pdf")
        FileOutputStream(output).use(pdf::writeTo)
        pdf.close()
        return FileProvider.getUriForFile(context, "${context.packageName}.files", output)
    }
}

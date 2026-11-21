package app.mafahim.canvas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import kotlin.math.ceil
import kotlin.math.max

object PsdExporter {
    private const val MAX_DIMENSION = 1024
    private const val MAX_LAYERS = 16

    fun export(context: Context, document: DrawingDocument): Uri {
        require(document.layers.size <= MAX_LAYERS) { "يدعم تصدير PSD حالياً حتى 16 طبقة" }
        val allPoints = document.strokes.flatMap { it.points } + document.images.flatMap {
            listOf(CanvasPoint(it.x, it.y), CanvasPoint(it.x + it.width, it.y + it.height))
        }
        val minX = allPoints.minOfOrNull { it.x } ?: 0f
        val minY = allPoints.minOfOrNull { it.y } ?: 0f
        val maxX = allPoints.maxOfOrNull { it.x } ?: 512f
        val maxY = allPoints.maxOfOrNull { it.y } ?: 512f
        val padding = 24f
        val sourceWidth = (maxX - minX + padding * 2).coerceAtLeast(1f)
        val sourceHeight = (maxY - minY + padding * 2).coerceAtLeast(1f)
        val scale = minOf(1f, MAX_DIMENSION / sourceWidth, MAX_DIMENSION / sourceHeight)
        val width = ceil(sourceWidth * scale).toInt().coerceIn(1, MAX_DIMENSION)
        val height = ceil(sourceHeight * scale).toInt().coerceIn(1, MAX_DIMENSION)
        val offsetX = padding - minX
        val offsetY = padding - minY

        val layers = document.layers.map { layer ->
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.translate(offsetX * scale, offsetY * scale)
            canvas.scale(scale, scale)
            document.strokes.filter { it.layerId == layer.id }.forEach { stroke ->
                drawStroke(canvas, stroke, layer.opacity)
            }
            bitmap
        }.toMutableList()

        if (layers.isEmpty()) layers += Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val imageCanvas = Canvas(layers.last())
        imageCanvas.translate(offsetX * scale, offsetY * scale)
        imageCanvas.scale(scale, scale)
        document.images.forEach { image ->
            val file = File(context.filesDir, "images/${File(image.fileName).name}")
            val bitmap = Bitmap.decodeFileCompat(file)
            if (bitmap != null) {
                val rect = RectF(image.x, image.y, image.x + image.width, image.y + image.height)
                imageCanvas.save()
                imageCanvas.rotate(image.rotationDegrees, rect.centerX(), rect.centerY())
                imageCanvas.drawBitmap(bitmap, null, rect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                imageCanvas.restore()
                bitmap.recycle()
            }
        }

        val composite = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val compositeCanvas = Canvas(composite)
        document.layers.forEachIndexed { index, layer ->
            if (layer.visible) {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { alpha = (layer.opacity.coerceIn(0f, 1f) * 255f).toInt() }
                compositeCanvas.drawBitmap(layers[index], 0f, 0f, paint)
            }
        }
        val rasterLayers = layers.mapIndexed { index, bitmap ->
            PsdRasterLayer(document.layers.getOrNull(index)?.name ?: "Images", document.layers.getOrNull(index)?.visible ?: true, rgba(bitmap))
        }
        val bytes = PsdWriter.write(width, height, rasterLayers, rgba(composite))
        layers.forEach { it.recycle() }
        composite.recycle()

        val directory = File(context.cacheDir, "exports").apply { mkdirs() }
        val output = File(directory, "mafahim-${System.currentTimeMillis()}.psd")
        FileOutputStream(output).use { it.write(bytes) }
        return FileProvider.getUriForFile(context, "${context.packageName}.files", output)
    }

    private fun drawStroke(canvas: Canvas, stroke: DrawStroke, layerOpacity: Float) {
        if (stroke.points.isEmpty()) return
        val base = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = stroke.color
            alpha = (stroke.opacity.coerceIn(0f, 1f) * layerOpacity.coerceIn(0f, 1f) * 255f).toInt()
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        if (stroke.points.size == 1) {
            val p = stroke.points.first()
            base.style = Paint.Style.FILL
            canvas.drawCircle(p.x, p.y, CanvasGeometry.pressureTiltWidth(p, stroke.width) / 2f, base)
            return
        }
        stroke.points.zipWithNext().forEach { (a, b) ->
            base.strokeWidth = (CanvasGeometry.pressureTiltWidth(a, stroke.width) +
                CanvasGeometry.pressureTiltWidth(b, stroke.width)) / 2f
            val path = Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y) }
            canvas.drawPath(path, base)
        }
    }

    private fun rgba(bitmap: Bitmap): ByteArray {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val bytes = ByteArray(pixels.size * 4)
        pixels.forEachIndexed { index, color ->
            val offset = index * 4
            bytes[offset] = (color shr 16).toByte()
            bytes[offset + 1] = (color shr 8).toByte()
            bytes[offset + 2] = color.toByte()
            bytes[offset + 3] = (color ushr 24).toByte()
        }
        return bytes
    }

    private fun File.decodeBitmap(): Bitmap? = android.graphics.BitmapFactory.decodeFile(absolutePath)
    private fun Bitmap.Companion.decodeFileCompat(file: File): Bitmap? = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
}

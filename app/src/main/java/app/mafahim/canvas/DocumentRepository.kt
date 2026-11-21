package app.mafahim.canvas

import android.content.Context
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.io.FileOutputStream

data class DocumentSummary(val id: String, val title: String, val modifiedAt: Long)

class DocumentRepository(context: Context) {
    private val appContext = context.applicationContext
    private val imagesDir = File(context.filesDir, "images")
    private val documentsDir = File(context.filesDir, "documents")
    private val legacyTarget = File(context.filesDir, "mafahim-document.mfd")

    private fun targetFor(id: String) = File(documentsDir, safeId(id) + ".mfd")
    private fun temporaryFor(id: String) = File(documentsDir, safeId(id) + ".mfd.tmp")
    private fun safeId(id: String) = id.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(80).ifBlank { UUID.randomUUID().toString() }

    fun importImage(uri: Uri): DrawingImage? = runCatching {
        imagesDir.mkdirs()
        val bitmap = appContext.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            ?: return null
        val ratio = minOf(1f, 2400f / maxOf(bitmap.width, bitmap.height).toFloat())
        val scaled = if (ratio < 1f) Bitmap.createScaledBitmap(bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1), true) else bitmap
        val id = UUID.randomUUID().toString()
        val name = "$id.png"
        FileOutputStream(File(imagesDir, name)).use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (scaled !== bitmap) { scaled.recycle(); bitmap.recycle() }
        DrawingImage(id, name, 60f, 60f, scaled.width.toFloat(), scaled.height.toFloat())
    }.getOrNull()

    fun loadImage(fileName: String): Bitmap? {
        val safeName = File(fileName).name
        return BitmapFactory.decodeFile(File(imagesDir, safeName).absolutePath)
    }

    @Synchronized
    fun save(document: DrawingDocument) {
        val target = targetFor(document.id)
        val temporary = temporaryFor(document.id)
        target.parentFile?.mkdirs()
        val layers = document.layers.take(MAX_LAYERS).ifEmpty {
            listOf(DrawingLayer("layer-base", "الطبقة الأولى"))
        }
        val validLayerIds = layers.mapTo(hashSetOf()) { it.id }
        val strokes = document.strokes.filter { it.layerId in validLayerIds }.take(MAX_STROKES)

        DataOutputStream(BufferedOutputStream(FileOutputStream(temporary))).use { output ->
            output.writeInt(MAGIC)
            output.writeInt(FORMAT_VERSION)
            output.writeUTF(document.id.take(MAX_TEXT))
            output.writeUTF(document.title.take(MAX_TEXT))
            output.writeInt(layers.size)
            layers.forEach { layer ->
                output.writeUTF(layer.id.take(MAX_TEXT))
                output.writeUTF(layer.name.take(MAX_TEXT))
                output.writeBoolean(layer.visible)
                output.writeFloat(layer.opacity.finiteOr(1f).coerceIn(0f, 1f))
            }
            output.writeInt(strokes.size)
            strokes.forEach { stroke ->
                output.writeUTF(stroke.id.take(MAX_TEXT))
                output.writeUTF(stroke.layerId.take(MAX_TEXT))
                output.writeInt(stroke.tool.ordinal)
                output.writeInt(stroke.color)
                output.writeFloat(stroke.width.finiteOr(5f).coerceIn(0.5f, 100f))
                output.writeFloat(stroke.opacity.finiteOr(1f).coerceIn(0f, 1f))
                val points = stroke.points.take(MAX_POINTS)
                output.writeInt(points.size)
                points.forEach { point ->
                    output.writeFloat(point.x.finiteOr(0f))
                    output.writeFloat(point.y.finiteOr(0f))
                    output.writeFloat(point.pressure.finiteOr(1f).coerceIn(0.05f, 1f))
                    output.writeFloat(point.tilt.finiteOr(0f).coerceIn(0f, 1.5707964f))
                    output.writeFloat(point.orientation.finiteOr(0f).coerceIn(-3.1415927f, 3.1415927f))
                }
            }
            val images = document.images.take(MAX_IMAGES)
            output.writeInt(images.size)
            images.forEach { image ->
                output.writeUTF(File(image.fileName).name.take(MAX_TEXT))
                output.writeFloat(image.x.finiteOr(0f))
                output.writeFloat(image.y.finiteOr(0f))
                output.writeFloat(image.width.finiteOr(1f).coerceIn(1f, 10000f))
                output.writeFloat(image.height.finiteOr(1f).coerceIn(1f, 10000f))
            }
            output.flush()
        }

        try {
            Files.move(temporary.toPath(), target.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    @Synchronized
    fun load(): DrawingDocument? {
        val newest = documentsDir.listFiles { file -> file.isFile && file.extension == "mfd" }
            ?.maxByOrNull { it.lastModified() }
        return if (newest != null) loadFrom(newest) else loadFrom(legacyTarget)
    }

    @Synchronized
    fun load(id: String): DrawingDocument? = loadFrom(targetFor(id))

    @Synchronized
    fun listDocuments(): List<DocumentSummary> {
        val files = documentsDir.listFiles { file -> file.isFile && file.extension == "mfd" } ?: return emptyList()
        return files.mapNotNull { file ->
            loadFrom(file)?.let { DocumentSummary(it.id, it.title, file.lastModified()) }
        }.sortedByDescending { it.modifiedAt }
    }

    @Synchronized
    fun delete(id: String): Boolean = targetFor(id).delete()

    private fun loadFrom(file: File): DrawingDocument? {
        if (!file.isFile) return null
        return runCatching {
            DataInputStream(BufferedInputStream(FileInputStream(file))).use { input ->
                require(input.readInt() == MAGIC) { "صيغة ملف غير معروفة" }
                val formatVersion = input.readInt()
                require(formatVersion in 1..FORMAT_VERSION) { "إصدار ملف غير مدعوم" }
                val documentId = if (formatVersion >= 4) input.readUTF() else UUID.randomUUID().toString()
                val title = input.readUTF()
                val layerCount = input.readInt()
                require(layerCount in 1..MAX_LAYERS) { "عدد الطبقات غير صالح" }
                val layers = List(layerCount) {
                    val id = input.readUTF()
                    val name = input.readUTF()
                    val visible = input.readBoolean()
                    val opacity = input.readFloat().finiteOr(1f).coerceIn(0f, 1f)
                    DrawingLayer(id, name, visible, opacity)
                }
                val strokeCount = input.readInt()
                require(strokeCount in 0..MAX_STROKES) { "عدد الخطوط غير صالح" }
                val strokes = List(strokeCount) {
                    val id = input.readUTF()
                    val layerId = input.readUTF()
                    val toolIndex = input.readInt()
                    val tool = BrushTool.entries.getOrNull(toolIndex)
                        ?: throw IllegalArgumentException("أداة رسم غير صالحة")
                    val color = input.readInt()
                    val width = input.readFloat().finiteOr(5f).coerceIn(0.5f, 100f)
                    val opacity = input.readFloat().finiteOr(1f).coerceIn(0f, 1f)
                    val pointCount = input.readInt()
                    require(pointCount in 0..MAX_POINTS) { "عدد نقاط غير صالح" }
                    val points = List(pointCount) {
                        val x = input.readFloat()
                        val y = input.readFloat()
                        require(x.isFinite() && y.isFinite()) { "إحداثيات غير صالحة" }
                        CanvasPoint(
                            x, y,
                            if (formatVersion >= 2) input.readFloat().finiteOr(1f).coerceIn(0.05f, 1f) else 1f,
                            if (formatVersion >= 3) input.readFloat().finiteOr(0f).coerceIn(0f, 1.5707964f) else 0f,
                            if (formatVersion >= 3) input.readFloat().finiteOr(0f).coerceIn(-3.1415927f, 3.1415927f) else 0f
                        )
                    }
                    DrawStroke(id, layerId, tool, color, width, opacity, points)
                }
                val images = if (formatVersion >= 5) {
                    val count = input.readInt()
                    require(count in 0..MAX_IMAGES) { "عدد الصور غير صالح" }
                    List(count) {
                        val fileName = File(input.readUTF()).name
                        val x = input.readFloat().finiteOr(0f)
                        val y = input.readFloat().finiteOr(0f)
                        val width = input.readFloat().finiteOr(1f).coerceIn(1f, 10000f)
                        val height = input.readFloat().finiteOr(1f).coerceIn(1f, 10000f)
                        DrawingImage(fileName.removeSuffix(".png"), fileName, x, y, width, height)
                    }
                } else emptyList()
                DrawingDocument(title, layers, strokes, documentId, images)
            }
        }.getOrNull()
    }

    private fun Float.finiteOr(fallback: Float): Float = if (isFinite()) this else fallback

    companion object {
        private const val MAGIC = 0x4D464D31
        private const val FORMAT_VERSION = 5
        private const val MAX_TEXT = 256
        private const val MAX_LAYERS = 128
        private const val MAX_STROKES = 100_000
        private const val MAX_POINTS = 50_000
        private const val MAX_IMAGES = 256
    }
}

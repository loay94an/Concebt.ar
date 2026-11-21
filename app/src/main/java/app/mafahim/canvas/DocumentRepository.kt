package app.mafahim.canvas

import android.content.Context
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

class DocumentRepository(context: Context) {
    private val target = File(context.filesDir, "mafahim-document.mfd")
    private val temporary = File(context.filesDir, "mafahim-document.mfd.tmp")

    @Synchronized
    fun save(document: DrawingDocument) {
        target.parentFile?.mkdirs()
        val layers = document.layers.take(MAX_LAYERS).ifEmpty {
            listOf(DrawingLayer("layer-base", "الطبقة الأولى"))
        }
        val validLayerIds = layers.mapTo(hashSetOf()) { it.id }
        val strokes = document.strokes.filter { it.layerId in validLayerIds }.take(MAX_STROKES)

        DataOutputStream(BufferedOutputStream(FileOutputStream(temporary))).use { output ->
            output.writeInt(MAGIC)
            output.writeInt(FORMAT_VERSION)
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
                }
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
        if (!target.isFile) return null
        return runCatching {
            DataInputStream(BufferedInputStream(FileInputStream(target))).use { input ->
                require(input.readInt() == MAGIC) { "صيغة ملف غير معروفة" }
                require(input.readInt() == FORMAT_VERSION) { "إصدار ملف غير مدعوم" }
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
                        CanvasPoint(x, y)
                    }
                    DrawStroke(id, layerId, tool, color, width, opacity, points)
                }
                DrawingDocument(title, layers, strokes)
            }
        }.getOrNull()
    }

    private fun Float.finiteOr(fallback: Float): Float = if (isFinite()) this else fallback

    companion object {
        private const val MAGIC = 0x4D464D31
        private const val FORMAT_VERSION = 1
        private const val MAX_TEXT = 256
        private const val MAX_LAYERS = 128
        private const val MAX_STROKES = 100_000
        private const val MAX_POINTS = 50_000
    }
}

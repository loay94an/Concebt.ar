package app.mafahim.canvas

import android.content.Context
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
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
        val imageWidth = scaled.width
        val imageHeight = scaled.height
        val id = UUID.randomUUID().toString()
        val name = "$id.png"
        FileOutputStream(File(imagesDir, name)).use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
        scaled.recycle()
        if (scaled !== bitmap) bitmap.recycle()
        DrawingImage(id, name, 60f, 60f, imageWidth.toFloat(), imageHeight.toFloat())
    }.getOrNull()

    fun importPdf(uri: Uri): List<DrawingImage> = runCatching {
        imagesDir.mkdirs()
        val descriptor = appContext.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalArgumentException("تعذر فتح ملف PDF")
        descriptor.use { parcel ->
            PdfRenderer(parcel).use { renderer ->
                require(renderer.pageCount in 1..40) { "يدعم الاستيراد حتى 40 صفحة في المرة الواحدة" }
                buildList {
                    var top = 60f
                    for (index in 0 until renderer.pageCount) {
                        renderer.openPage(index).use { page ->
                            val scale = minOf(1.8f, 2000f / maxOf(page.width, page.height).toFloat())
                            val width = (page.width * scale).toInt().coerceAtLeast(1)
                            val height = (page.height * scale).toInt().coerceAtLeast(1)
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            bitmap.eraseColor(android.graphics.Color.WHITE)
                            page.render(bitmap, Rect(0, 0, width, height), null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val id = UUID.randomUUID().toString()
                            val name = "$id.png"
                            FileOutputStream(File(imagesDir, name)).use { output ->
                                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "تعذر حفظ صفحة PDF" }
                            }
                            bitmap.recycle()
                            add(DrawingImage(id, name, 60f, top, width.toFloat(), height.toFloat()))
                            top += height + 32f
                        }
                    }
                }
            }
        }
    }.getOrElse { throw IllegalArgumentException(it.message ?: "تعذر استيراد ملف PDF", it) }

    fun cropImage(image: DrawingImage, frame: ImageCropFrame): DrawingImage? = runCatching {
        val source = loadImage(image.fileName) ?: throw IllegalArgumentException("الصورة الأصلية غير موجودة")
        val left = (source.width * frame.left).toInt().coerceIn(0, source.width - 1)
        val top = (source.height * frame.top).toInt().coerceIn(0, source.height - 1)
        val right = (source.width * frame.right).toInt().coerceIn(left + 1, source.width)
        val bottom = (source.height * frame.bottom).toInt().coerceIn(top + 1, source.height)
        val cropped = Bitmap.createBitmap(source, left, top, right - left, bottom - top)
        imagesDir.mkdirs()
        val fileName = "${UUID.randomUUID()}.png"
        FileOutputStream(File(imagesDir, fileName)).use { output ->
            check(cropped.compress(Bitmap.CompressFormat.PNG, 100, output)) { "تعذر حفظ الصورة المقصوصة" }
        }
        val scaleX = (right - left).toFloat() / source.width
        val scaleY = (bottom - top).toFloat() / source.height
        val result = image.copy(
            fileName = fileName,
            x = image.x + image.width * frame.left,
            y = image.y + image.height * frame.top,
            width = image.width * scaleX,
            height = image.height * scaleY
        )
        if (cropped !== source) cropped.recycle()
        source.recycle()
        result
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
                output.writeBoolean(layer.locked)
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
                output.writeFloat(image.rotationDegrees.finiteOr(0f).coerceIn(-360f, 360f))
            }
            val guides = document.guides.take(MAX_GUIDES)
            output.writeInt(guides.size)
            guides.forEach { guide ->
                output.writeUTF(guide.id.take(MAX_TEXT))
                output.writeInt(guide.type.ordinal)
                output.writeFloat(guide.origin.x.finiteOr(0f))
                output.writeFloat(guide.origin.y.finiteOr(0f))
                output.writeFloat(guide.angleDegrees.finiteOr(0f).coerceIn(-360f, 360f))
                output.writeFloat(guide.spacing.finiteOr(24f).coerceIn(1f, 10000f))
                output.writeBoolean(guide.enabled)
            }
            val artboards = document.artboards.take(MAX_ARTBOARDS)
            output.writeInt(artboards.size)
            artboards.forEach { artboard ->
                output.writeUTF(artboard.id.take(MAX_TEXT))
                output.writeUTF(artboard.name.take(MAX_TEXT))
                output.writeFloat(artboard.x.finiteOr(0f))
                output.writeFloat(artboard.y.finiteOr(0f))
                output.writeFloat(artboard.width.finiteOr(1000f).coerceIn(1f, 100000f))
                output.writeFloat(artboard.height.finiteOr(1400f).coerceIn(1f, 100000f))
                output.writeInt(artboard.backgroundColor)
                output.writeBoolean(artboard.visible)
            }
            output.writeUTF(document.activeArtboardId.orEmpty().take(MAX_TEXT))
            val pageDocument = CanvasPageEngine.saveActive(CanvasPageEngine.normalized(document))
            val pages = pageDocument.pages.take(MAX_PAGES)
            output.writeInt(pages.size)
            pages.forEach { page -> writePage(output, page) }
            output.writeUTF(pageDocument.activePageId.orEmpty().take(MAX_TEXT))
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

    private fun writePage(output: DataOutputStream, page: DrawingPage) {
        output.writeUTF(page.id.take(MAX_TEXT))
        output.writeUTF(page.name.take(MAX_TEXT))
        output.writeInt(page.backgroundColor)
        val layers = page.layers.take(MAX_LAYERS).ifEmpty { listOf(DrawingLayer("layer-base", "الطبقة الأولى")) }
        output.writeInt(layers.size)
        layers.forEach { layer ->
            output.writeUTF(layer.id.take(MAX_TEXT)); output.writeUTF(layer.name.take(MAX_TEXT))
            output.writeBoolean(layer.visible)
            output.writeFloat(layer.opacity.finiteOr(1f).coerceIn(0f, 1f))
            output.writeBoolean(layer.locked)
        }
        val validLayerIds = layers.mapTo(hashSetOf()) { it.id }
        val strokes = page.strokes.filter { it.layerId in validLayerIds }.take(MAX_STROKES)
        output.writeInt(strokes.size)
        strokes.forEach { stroke ->
            output.writeUTF(stroke.id.take(MAX_TEXT)); output.writeUTF(stroke.layerId.take(MAX_TEXT))
            output.writeInt(stroke.tool.ordinal); output.writeInt(stroke.color)
            output.writeFloat(stroke.width.finiteOr(5f).coerceIn(0.5f, 100f))
            output.writeFloat(stroke.opacity.finiteOr(1f).coerceIn(0f, 1f))
            val points = stroke.points.take(MAX_POINTS)
            output.writeInt(points.size)
            points.forEach { point ->
                output.writeFloat(point.x.finiteOr(0f)); output.writeFloat(point.y.finiteOr(0f))
                output.writeFloat(point.pressure.finiteOr(1f).coerceIn(0.05f, 1f))
                output.writeFloat(point.tilt.finiteOr(0f).coerceIn(0f, 1.5707964f))
                output.writeFloat(point.orientation.finiteOr(0f).coerceIn(-3.1415927f, 3.1415927f))
            }
        }
        val images = page.images.take(MAX_IMAGES)
        output.writeInt(images.size)
        images.forEach { image ->
            output.writeUTF(image.id.take(MAX_TEXT)); output.writeUTF(File(image.fileName).name.take(MAX_TEXT))
            output.writeFloat(image.x.finiteOr(0f)); output.writeFloat(image.y.finiteOr(0f))
            output.writeFloat(image.width.finiteOr(1f).coerceIn(1f, 10000f))
            output.writeFloat(image.height.finiteOr(1f).coerceIn(1f, 10000f))
            output.writeFloat(image.rotationDegrees.finiteOr(0f).coerceIn(-360f, 360f))
        }
        val guides = page.guides.take(MAX_GUIDES)
        output.writeInt(guides.size)
        guides.forEach { guide ->
            output.writeUTF(guide.id.take(MAX_TEXT)); output.writeInt(guide.type.ordinal)
            output.writeFloat(guide.origin.x.finiteOr(0f)); output.writeFloat(guide.origin.y.finiteOr(0f))
            output.writeFloat(guide.angleDegrees.finiteOr(0f).coerceIn(-360f, 360f))
            output.writeFloat(guide.spacing.finiteOr(24f).coerceIn(1f, 10000f))
            output.writeBoolean(guide.enabled)
        }
    }

    private fun readPage(input: DataInputStream): DrawingPage {
        val id = input.readUTF()
        val name = input.readUTF()
        val backgroundColor = input.readInt()
        val layerCount = input.readInt()
        require(layerCount in 1..MAX_LAYERS) { "عدد طبقات الصفحة غير صالح" }
        val layers = List(layerCount) {
            DrawingLayer(input.readUTF(), input.readUTF(), input.readBoolean(),
                input.readFloat().finiteOr(1f).coerceIn(0f, 1f), input.readBoolean())
        }
        val strokeCount = input.readInt()
        require(strokeCount in 0..MAX_STROKES) { "عدد خطوط الصفحة غير صالح" }
        val strokes = List(strokeCount) {
            val strokeId = input.readUTF()
            val layerId = input.readUTF()
            val tool = BrushTool.entries.getOrNull(input.readInt())
                ?: throw IllegalArgumentException("أداة رسم الصفحة غير صالحة")
            val color = input.readInt()
            val width = input.readFloat().finiteOr(5f).coerceIn(0.5f, 100f)
            val opacity = input.readFloat().finiteOr(1f).coerceIn(0f, 1f)
            val pointCount = input.readInt()
            require(pointCount in 0..MAX_POINTS) { "عدد نقاط الصفحة غير صالح" }
            val points = List(pointCount) {
                val x = input.readFloat(); val y = input.readFloat()
                require(x.isFinite() && y.isFinite()) { "إحداثيات الصفحة غير صالحة" }
                CanvasPoint(x, y, input.readFloat().finiteOr(1f).coerceIn(0.05f, 1f),
                    input.readFloat().finiteOr(0f).coerceIn(0f, 1.5707964f),
                    input.readFloat().finiteOr(0f).coerceIn(-3.1415927f, 3.1415927f))
            }
            DrawStroke(strokeId, layerId, tool, color, width, opacity, points)
        }
        val imageCount = input.readInt()
        require(imageCount in 0..MAX_IMAGES) { "عدد صور الصفحة غير صالح" }
        val images = List(imageCount) {
            val imageId = input.readUTF()
            val fileName = File(input.readUTF()).name
            DrawingImage(imageId, fileName, input.readFloat().finiteOr(0f), input.readFloat().finiteOr(0f),
                input.readFloat().finiteOr(1f).coerceIn(1f, 10000f),
                input.readFloat().finiteOr(1f).coerceIn(1f, 10000f),
                input.readFloat().finiteOr(0f).coerceIn(-360f, 360f))
        }
        val guideCount = input.readInt()
        require(guideCount in 0..MAX_GUIDES) { "عدد أدلة الصفحة غير صالح" }
        val guides = List(guideCount) {
            val guideId = input.readUTF()
            val type = ShapeGuideType.entries.getOrNull(input.readInt())
                ?: throw IllegalArgumentException("نوع دليل الصفحة غير صالح")
            val x = input.readFloat().finiteOr(0f); val y = input.readFloat().finiteOr(0f)
            val angle = input.readFloat().finiteOr(0f).coerceIn(-360f, 360f)
            val spacing = input.readFloat().finiteOr(24f).coerceIn(1f, 10000f)
            ShapeGuide(guideId, type, CanvasPoint(x, y), angle, spacing, input.readBoolean())
        }
        return DrawingPage(id, name, backgroundColor, layers, strokes, images, guides)
    }

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
                    val locked = if (formatVersion >= 8) input.readBoolean() else false
                    DrawingLayer(id, name, visible, opacity, locked)
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
                        val rotation = if (formatVersion >= 6) input.readFloat().finiteOr(0f).coerceIn(-360f, 360f) else 0f
                        DrawingImage(fileName.removeSuffix(".png"), fileName, x, y, width, height, rotation)
                    }
                } else emptyList()
                val guides = if (formatVersion >= 7) {
                    val count = input.readInt()
                    require(count in 0..MAX_GUIDES) { "عدد الأدلة غير صالح" }
                    List(count) {
                        val id = input.readUTF()
                        val typeIndex = input.readInt()
                        val type = ShapeGuideType.entries.getOrNull(typeIndex)
                            ?: throw IllegalArgumentException("نوع دليل غير صالح")
                        val x = input.readFloat().finiteOr(0f)
                        val y = input.readFloat().finiteOr(0f)
                        val angle = input.readFloat().finiteOr(0f).coerceIn(-360f, 360f)
                        val spacing = input.readFloat().finiteOr(24f).coerceIn(1f, 10000f)
                        val enabled = input.readBoolean()
                        ShapeGuide(id, type, CanvasPoint(x, y), angle, spacing, enabled)
                    }
                } else emptyList()
                val artboards = if (formatVersion >= 9) {
                    val count = input.readInt()
                    require(count in 0..MAX_ARTBOARDS) { "عدد اللوحات الفنية غير صالح" }
                    List(count) {
                        val id = input.readUTF()
                        val name = input.readUTF()
                        val x = input.readFloat().finiteOr(0f)
                        val y = input.readFloat().finiteOr(0f)
                        val width = input.readFloat().finiteOr(1000f).coerceIn(1f, 100000f)
                        val height = input.readFloat().finiteOr(1400f).coerceIn(1f, 100000f)
                        val backgroundColor = input.readInt()
                        val visible = input.readBoolean()
                        DrawingArtboard(id, name, x, y, width, height, backgroundColor, visible)
                    }
                } else emptyList()
                val activeArtboardId = if (formatVersion >= 9) input.readUTF().takeIf { it.isNotBlank() } else null
                val pages: List<DrawingPage>
                val activePageId: String?
                if (formatVersion >= 10) {
                    val pageCount = input.readInt()
                    require(pageCount in 1..MAX_PAGES) { "عدد الصفحات غير صالح" }
                    pages = List(pageCount) { readPage(input) }
                    activePageId = input.readUTF().takeIf { it.isNotBlank() }
                    require(activePageId == null || pages.any { it.id == activePageId }) { "الصفحة النشطة غير موجودة" }
                } else {
                    pages = emptyList()
                    activePageId = null
                }
                val document = DrawingDocument(title, layers, strokes, documentId, images, guides, artboards,
                    activeArtboardId, pages, activePageId)
                if (activePageId != null) CanvasPageEngine.activate(document, activePageId) else document
            }
        }.getOrNull()
    }

    private fun Float.finiteOr(fallback: Float): Float = if (isFinite()) this else fallback

    companion object {
        private const val MAGIC = 0x4D464D31
        private const val FORMAT_VERSION = 10
        private const val MAX_TEXT = 256
        private const val MAX_LAYERS = 128
        private const val MAX_STROKES = 100_000
        private const val MAX_POINTS = 50_000
        private const val MAX_IMAGES = 256
        private const val MAX_GUIDES = 256
        private const val MAX_ARTBOARDS = 64
        private const val MAX_PAGES = 64
    }
}

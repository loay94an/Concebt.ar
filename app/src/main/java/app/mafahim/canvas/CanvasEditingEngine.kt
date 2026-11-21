package app.mafahim.canvas

import java.util.UUID

data class CanvasClipboard(
    val strokes: List<DrawStroke> = emptyList(),
    val images: List<DrawingImage> = emptyList()
) {
    val isEmpty: Boolean get() = strokes.isEmpty() && images.isEmpty()
}

/** Pure editing operations; callers apply the returned immutable document as one undoable edit. */
object CanvasEditingEngine {
    fun toggleSelection(current: CanvasSelection, hit: CanvasSelection): CanvasSelection {
        val strokes = current.strokeIds.toMutableSet()
        val images = current.imageIds.toMutableSet()
        hit.strokeIds.forEach { if (!strokes.add(it)) strokes.remove(it) }
        hit.imageIds.forEach { if (!images.add(it)) images.remove(it) }
        return CanvasSelection(strokes, images)
    }

    fun selectAll(document: DrawingDocument): CanvasSelection {
        val visible = document.layers.filter { it.visible && !it.locked }.mapTo(hashSetOf()) { it.id }
        return CanvasSelection(
            strokeIds = document.strokes.filter { it.layerId in visible }.mapTo(linkedSetOf()) { it.id },
            imageIds = document.images.mapTo(linkedSetOf()) { it.id }
        )
    }

    fun copy(document: DrawingDocument, selection: CanvasSelection): CanvasClipboard {
        val unlocked = document.layers.filter { it.visible && !it.locked }.mapTo(hashSetOf()) { it.id }
        return CanvasClipboard(
            strokes = document.strokes.filter { it.id in selection.strokeIds && it.layerId in unlocked },
            images = document.images.filter { it.id in selection.imageIds }
        )
    }

    fun paste(document: DrawingDocument, clipboard: CanvasClipboard, offset: Float = 24f): Pair<DrawingDocument, CanvasSelection> {
        if (clipboard.isEmpty) return document to CanvasSelection()
        val targetLayer = document.layers.firstOrNull { it.visible && !it.locked } ?: return document to CanvasSelection()
        val strokes = clipboard.strokes.map { stroke ->
            stroke.copy(
                id = UUID.randomUUID().toString(),
                layerId = targetLayer.id,
                points = stroke.points.map { it.copy(x = it.x + offset, y = it.y + offset) }
            )
        }
        val images = clipboard.images.map { image ->
            image.copy(id = UUID.randomUUID().toString(), x = image.x + offset, y = image.y + offset)
        }
        val next = document.copy(strokes = document.strokes + strokes, images = document.images + images)
        return next to CanvasSelection(strokes.mapTo(linkedSetOf()) { it.id }, images.mapTo(linkedSetOf()) { it.id })
    }

    fun delete(document: DrawingDocument, selection: CanvasSelection): DrawingDocument {
        if (selection.isEmpty) return document
        val locked = document.layers.filter { it.locked }.mapTo(hashSetOf()) { it.id }
        return document.copy(
            strokes = document.strokes.filterNot { it.id in selection.strokeIds && it.layerId !in locked },
            images = document.images.filterNot { it.id in selection.imageIds }
        )
    }

    fun cut(document: DrawingDocument, selection: CanvasSelection): Pair<DrawingDocument, CanvasClipboard> {
        val clipboard = copy(document, selection)
        return delete(document, selection) to clipboard
    }
}

enum class CropHandle { LEFT, TOP, RIGHT, BOTTOM, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

data class ImageCropFrame(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f
) {
    init {
        require(left in 0f..1f && top in 0f..1f && right in 0f..1f && bottom in 0f..1f)
        require(right > left && bottom > top)
    }

    fun drag(handle: CropHandle, dx: Float, dy: Float, minFraction: Float = 0.02f): ImageCropFrame {
        val minSize = minFraction.coerceIn(0.001f, 0.25f)
        var l = left
        var t = top
        var r = right
        var b = bottom
        if (handle in setOf(CropHandle.LEFT, CropHandle.TOP_LEFT, CropHandle.BOTTOM_LEFT)) l = (l + dx).coerceIn(0f, r - minSize)
        if (handle in setOf(CropHandle.RIGHT, CropHandle.TOP_RIGHT, CropHandle.BOTTOM_RIGHT)) r = (r + dx).coerceIn(l + minSize, 1f)
        if (handle in setOf(CropHandle.TOP, CropHandle.TOP_LEFT, CropHandle.TOP_RIGHT)) t = (t + dy).coerceIn(0f, b - minSize)
        if (handle in setOf(CropHandle.BOTTOM, CropHandle.BOTTOM_LEFT, CropHandle.BOTTOM_RIGHT)) b = (b + dy).coerceIn(t + minSize, 1f)
        return ImageCropFrame(l, t, r, b)
    }
}

object CanvasColorEngine {
    fun parseHex(value: String): Int? {
        val normalized = value.trim().removePrefix("#")
        if (normalized.length != 6 && normalized.length != 8) return null
        return normalized.toLongOrNull(16)?.let { raw ->
            if (normalized.length == 6) (0xFF000000L or raw).toInt() else raw.toInt()
        }
    }

    fun hex(color: Int): String = "#%08X".format(java.util.Locale.ROOT, color)

    fun applyBackground(document: DrawingDocument, color: Int): DrawingDocument {
        val activeBoard = document.activeArtboardId
        val boards = document.artboards.map { board ->
            if (activeBoard == null || board.id == activeBoard) board.copy(backgroundColor = color) else board
        }
        val activePage = document.activePageId
        val pages = if (activePage == null) document.pages else document.pages.map { page ->
            if (page.id == activePage) page.copy(backgroundColor = color) else page
        }
        return document.copy(artboards = boards, pages = pages)
    }
}

object CanvasGuideFactory {
    fun line(id: String = UUID.randomUUID().toString(), origin: CanvasPoint, angleDegrees: Float) =
        ShapeGuide(id, ShapeGuideType.LINE, origin, angleDegrees)

    fun parallel(id: String = UUID.randomUUID().toString(), origin: CanvasPoint, angleDegrees: Float, spacing: Float) =
        ShapeGuide(id, ShapeGuideType.PARALLEL, origin, angleDegrees, spacing.coerceAtLeast(1f))

    fun perpendicular(id: String = UUID.randomUUID().toString(), origin: CanvasPoint, angleDegrees: Float) =
        ShapeGuide(id, ShapeGuideType.PERPENDICULAR, origin, angleDegrees + 90f)

    fun grid(id: String = UUID.randomUUID().toString(), origin: CanvasPoint, spacing: Float) =
        ShapeGuide(id, ShapeGuideType.GRID, origin, spacing = spacing.coerceAtLeast(1f))

    fun constrainedAngle(angleDegrees: Float, incrementDegrees: Float = 15f): Float {
        val step = incrementDegrees.takeIf { it.isFinite() && it > 0f } ?: 15f
        return (angleDegrees / step).let { kotlin.math.round(it) * step }
    }
}

package app.mafahim.canvas

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class CanvasSelection(
    val strokeIds: Set<String> = emptySet(),
    val imageIds: Set<String> = emptySet()
) {
    val isEmpty: Boolean get() = strokeIds.isEmpty() && imageIds.isEmpty()
}

data class SelectionBounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = (right - left).coerceAtLeast(0f)
    val height get() = (bottom - top).coerceAtLeast(0f)
    val centerX get() = (left + right) / 2f
    val centerY get() = (top + bottom) / 2f
    fun contains(x: Float, y: Float, padding: Float = 0f) =
        x >= left - padding && x <= right + padding && y >= top - padding && y <= bottom + padding
}

data class SelectionTransform(
    val translateX: Float = 0f,
    val translateY: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotationDegrees: Float = 0f
)

object CanvasSelectionEngine {
    fun bounds(document: DrawingDocument, selection: CanvasSelection): SelectionBounds? {
        val points = buildList {
            document.strokes.filter { it.id in selection.strokeIds }.forEach { addAll(it.points) }
            document.images.filter { it.id in selection.imageIds }.forEach { image ->
                val cx = image.x + image.width / 2f
                val cy = image.y + image.height / 2f
                val angle = Math.toRadians(image.rotationDegrees.toDouble())
                val c = kotlin.math.cos(angle).toFloat()
                val si = kotlin.math.sin(angle).toFloat()
                listOf(
                    CanvasPoint(image.x, image.y),
                    CanvasPoint(image.x + image.width, image.y),
                    CanvasPoint(image.x + image.width, image.y + image.height),
                    CanvasPoint(image.x, image.y + image.height)
                ).forEach { p ->
                    val dx = p.x - cx
                    val dy = p.y - cy
                    add(CanvasPoint(cx + dx * c - dy * si, cy + dx * si + dy * c))
                }
            }
        }
        if (points.isEmpty()) return null
        return SelectionBounds(
            points.minOf { it.x }, points.minOf { it.y },
            points.maxOf { it.x }, points.maxOf { it.y }
        )
    }

    /** Select the nearest visible object under a world-space point. Images take priority. */
    fun hitTest(document: DrawingDocument, x: Float, y: Float, tolerance: Float): CanvasSelection {
        val visible = document.layers.filter { it.visible && !it.locked }.mapTo(hashSetOf()) { it.id }
        val image = document.images.lastOrNull { image ->
            val cx = image.x + image.width / 2f
            val cy = image.y + image.height / 2f
            val angle = Math.toRadians(-image.rotationDegrees.toDouble())
            val dx = x - cx
            val dy = y - cy
            val localX = cx + dx * kotlin.math.cos(angle).toFloat() - dy * kotlin.math.sin(angle).toFloat()
            val localY = cy + dx * kotlin.math.sin(angle).toFloat() + dy * kotlin.math.cos(angle).toFloat()
            localX >= image.x - tolerance && localY >= image.y - tolerance &&
                localX <= image.x + image.width + tolerance && localY <= image.y + image.height + tolerance
        }
        if (image != null) return CanvasSelection(imageIds = setOf(image.id))
        val stroke = document.strokes.asReversed().firstOrNull { stroke ->
            if (stroke.layerId !in visible || stroke.points.isEmpty()) false
            else if (stroke.points.size == 1) CanvasGeometry.distance(stroke.points.first(), CanvasPoint(x, y)) <= tolerance + stroke.width / 2f
            else stroke.points.zipWithNext().any { (a, b) ->
                distanceToSegment(x, y, a.x, a.y, b.x, b.y) <= tolerance + stroke.width / 2f
            }
        }
        return if (stroke == null) CanvasSelection() else CanvasSelection(strokeIds = setOf(stroke.id))
    }

    fun transform(document: DrawingDocument, selection: CanvasSelection, transform: SelectionTransform): DrawingDocument {
        if (selection.isEmpty) return document
        val bounds = bounds(document, selection) ?: return document
        val sx = transform.scaleX.takeIf { it.isFinite() }?.coerceIn(0.05f, 20f) ?: 1f
        val sy = transform.scaleY.takeIf { it.isFinite() }?.coerceIn(0.05f, 20f) ?: 1f
        val angle = Math.toRadians(transform.rotationDegrees.takeIf { it.isFinite() }?.toDouble() ?: 0.0)
        val c = kotlin.math.cos(angle).toFloat()
        val s = kotlin.math.sin(angle).toFloat()
        fun map(point: CanvasPoint): CanvasPoint {
            val dx = (point.x - bounds.centerX) * sx
            val dy = (point.y - bounds.centerY) * sy
            return point.copy(
                x = bounds.centerX + dx * c - dy * s + transform.translateX,
                y = bounds.centerY + dx * s + dy * c + transform.translateY
            )
        }
        val strokes = document.strokes.map { stroke ->
            if (stroke.id !in selection.strokeIds) stroke
            else stroke.copy(points = stroke.points.map(::map))
        }
        val images = document.images.map { image ->
            if (image.id !in selection.imageIds) image
            else {
                val center = map(CanvasPoint(image.x + image.width / 2f, image.y + image.height / 2f))
                val topLeft = map(CanvasPoint(image.x, image.y))
                val topRight = map(CanvasPoint(image.x + image.width, image.y))
                val bottomLeft = map(CanvasPoint(image.x, image.y + image.height))
                val newWidth = hypot(topRight.x - topLeft.x, topRight.y - topLeft.y).coerceAtLeast(1f)
                val newHeight = hypot(bottomLeft.x - topLeft.x, bottomLeft.y - topLeft.y).coerceAtLeast(1f)
                image.copy(
                    x = center.x - newWidth / 2f, y = center.y - newHeight / 2f,
                    width = newWidth, height = newHeight,
                    rotationDegrees = image.rotationDegrees + transform.rotationDegrees
                )
            }
        }
        return document.copy(strokes = strokes, images = images)
    }

    private fun distanceToSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        val length2 = dx * dx + dy * dy
        if (length2 <= 0.000001f) return hypot(px - ax, py - ay)
        val t = (((px - ax) * dx + (py - ay) * dy) / length2).coerceIn(0f, 1f)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }
}

enum class ShapeGuideType { LINE, RECTANGLE, ELLIPSE, PARALLEL, PERPENDICULAR, GRID }

data class ShapeGuide(
    val id: String,
    val type: ShapeGuideType,
    val origin: CanvasPoint,
    val angleDegrees: Float = 0f,
    val spacing: Float = 24f,
    val enabled: Boolean = true
)

data class SnapResult(val point: CanvasPoint, val snapped: Boolean, val target: String? = null)

object CanvasSnapEngine {
    fun snap(point: CanvasPoint, guides: List<ShapeGuide>, gridSpacing: Float?, candidates: List<CanvasPoint>, tolerance: Float): SnapResult {
        val safeTolerance = tolerance.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
        val options = mutableListOf<Pair<CanvasPoint, String>>()
        candidates.forEachIndexed { index, candidate -> options += candidate to "point:$index" }
        guides.filter { it.enabled }.forEach { guide ->
            val angle = Math.toRadians(guide.angleDegrees.toDouble())
            val dx = point.x - guide.origin.x
            val dy = point.y - guide.origin.y
            when (guide.type) {
                ShapeGuideType.LINE, ShapeGuideType.PARALLEL -> {
                    val ux = kotlin.math.cos(angle).toFloat()
                    val uy = kotlin.math.sin(angle).toFloat()
                    val along = dx * ux + dy * uy
                    options += CanvasPoint(guide.origin.x + along * ux, guide.origin.y + along * uy) to "guide:${guide.id}"
                }
                ShapeGuideType.PERPENDICULAR -> {
                    val ux = -kotlin.math.sin(angle).toFloat()
                    val uy = kotlin.math.cos(angle).toFloat()
                    val along = dx * ux + dy * uy
                    options += CanvasPoint(guide.origin.x + along * ux, guide.origin.y + along * uy) to "guide:${guide.id}"
                }
                ShapeGuideType.GRID -> {
                    val step = guide.spacing.takeIf { it.isFinite() && it > 0f } ?: return@forEach
                    options += CanvasPoint(
                        guide.origin.x + ((point.x - guide.origin.x) / step).roundToInt() * step,
                        guide.origin.y + ((point.y - guide.origin.y) / step).roundToInt() * step
                    ) to "grid:${guide.id}"
                }
                else -> Unit
            }
        }
        if (gridSpacing != null && gridSpacing.isFinite() && gridSpacing > 0f) {
            options += CanvasPoint((point.x / gridSpacing).roundToInt() * gridSpacing, (point.y / gridSpacing).roundToInt() * gridSpacing) to "grid"
        }
        val nearest = options.minByOrNull { hypot(it.first.x - point.x, it.first.y - point.y) }
        if (nearest != null && hypot(nearest.first.x - point.x, nearest.first.y - point.y) <= safeTolerance) {
            return SnapResult(nearest.first.copy(pressure = point.pressure, tilt = point.tilt, orientation = point.orientation), true, nearest.second)
        }
        return SnapResult(point, false)
    }
}

enum class MeasurementUnit(val millimetersPerUnit: Float, val symbol: String) {
    PIXEL(25.4f / 96f, "px"),
    MILLIMETER(1f, "mm"),
    CENTIMETER(10f, "cm"),
    INCH(25.4f, "in")
}

object CanvasMeasurement {
    fun distance(a: CanvasPoint, b: CanvasPoint, unit: MeasurementUnit, pixelsPerMillimeter: Float = 1f): Float {
        val calibration = pixelsPerMillimeter.takeIf { it.isFinite() && it > 0f } ?: 1f
        val millimeters = hypot(b.x - a.x, b.y - a.y) / calibration
        return millimeters / unit.millimetersPerUnit
    }

    fun label(a: CanvasPoint, b: CanvasPoint, unit: MeasurementUnit, pixelsPerMillimeter: Float = 1f): String {
        val value = distance(a, b, unit, pixelsPerMillimeter)
        return String.format(java.util.Locale.ROOT, "%.2f %s", value, unit.symbol)
    }
}

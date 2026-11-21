package app.mafahim.canvas

import kotlin.math.hypot
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import java.util.UUID

enum class BrushTool { PEN, PENCIL, MARKER, ERASER, MOVE, LINE, RECTANGLE, ELLIPSE, MEASURE, SELECT, GUIDE }

data class CanvasPoint(val x: Float, val y: Float, val pressure: Float = 1f, val tilt: Float = 0f, val orientation: Float = 0f)

data class DrawingLayer(
    val id: String,
    val name: String,
    val visible: Boolean = true,
    val opacity: Float = 1f
)

data class DrawStroke(
    val id: String,
    val layerId: String,
    val tool: BrushTool,
    val color: Int,
    val width: Float,
    val opacity: Float,
    val points: List<CanvasPoint>
)

data class DrawingImage(
    val id: String,
    val fileName: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val rotationDegrees: Float = 0f
)

data class DrawingDocument(
    val title: String,
    val layers: List<DrawingLayer>,
    val strokes: List<DrawStroke>,
    val id: String = UUID.randomUUID().toString(),
    val images: List<DrawingImage> = emptyList(),
    val guides: List<ShapeGuide> = emptyList()
) {
    companion object {
        fun empty() = DrawingDocument(
            title = "لوحة جديدة",
            layers = listOf(DrawingLayer("layer-base", "الطبقة الأولى")),
            strokes = emptyList()
        )
    }
}

object CanvasGeometry {
    fun toWorld(screen: CanvasPoint, zoom: Float, panX: Float, panY: Float): CanvasPoint {
        val safeZoom = zoom.coerceIn(0.2f, 8f)
        return screen.copy(x = (screen.x - panX) / safeZoom, y = (screen.y - panY) / safeZoom)
    }

    fun distance(a: CanvasPoint, b: CanvasPoint): Float = hypot(a.x - b.x, a.y - b.y)

    fun normalizedPressure(value: Float): Float =
        (if (value.isFinite()) value else 1f).coerceIn(0.05f, 1f)

    fun normalizedTilt(value: Float): Float =
        (if (value.isFinite()) value else 0f).coerceIn(0f, (PI / 2.0).toFloat())

    fun normalizedOrientation(value: Float): Float =
        (if (value.isFinite()) value else 0f).coerceIn(-PI.toFloat(), PI.toFloat())

    fun pressureTiltWidth(point: CanvasPoint, baseWidth: Float): Float {
        val pressure = normalizedPressure(point.pressure)
        val tilt = normalizedTilt(point.tilt) / (PI / 2.0).toFloat()
        return baseWidth * pressure * (1f + tilt * 0.35f)
    }

    fun shapePoints(tool: BrushTool, start: CanvasPoint, end: CanvasPoint): List<CanvasPoint> {
        return when (tool) {
            BrushTool.LINE, BrushTool.MEASURE -> listOf(start, end)
            BrushTool.RECTANGLE -> listOf(start, CanvasPoint(end.x, start.y), end, CanvasPoint(start.x, end.y), start)
            BrushTool.ELLIPSE -> {
                val centerX = (start.x + end.x) / 2f
                val centerY = (start.y + end.y) / 2f
                val radiusX = kotlin.math.abs(end.x - start.x) / 2f
                val radiusY = kotlin.math.abs(end.y - start.y) / 2f
                (0..64).map { step ->
                    val angle = (step / 64f) * (2f * PI.toFloat())
                    CanvasPoint(centerX + cos(angle) * radiusX, centerY + sin(angle) * radiusY)
                }.toMutableList().apply { this[lastIndex] = this.first() }
            }
            else -> listOf(start, end)
        }
    }

    fun smooth(points: List<CanvasPoint>, subdivisions: Int = 3): List<CanvasPoint> {
        if (points.size < 3 || subdivisions < 1) return points
        val result = ArrayList<CanvasPoint>((points.size - 1) * subdivisions + 1)
        result += points.first()
        for (index in 0 until points.lastIndex) {
            val p0 = points[(index - 1).coerceAtLeast(0)]
            val p1 = points[index]
            val p2 = points[index + 1]
            val p3 = points[(index + 2).coerceAtMost(points.lastIndex)]
            for (step in 1..subdivisions) {
                val t = step.toFloat() / subdivisions
                val t2 = t * t
                val t3 = t2 * t
                fun interpolate(a: Float, b: Float, c: Float, d: Float): Float =
                    0.5f * ((2f * b) + (-a + c) * t +
                        (2f * a - 5f * b + 4f * c - d) * t2 +
                        (-a + 3f * b - 3f * c + d) * t3)
                result += CanvasPoint(
                    interpolate(p0.x, p1.x, p2.x, p3.x),
                    interpolate(p0.y, p1.y, p2.y, p3.y),
                    normalizedPressure(interpolate(p0.pressure, p1.pressure, p2.pressure, p3.pressure)),
                    normalizedTilt(interpolate(p0.tilt, p1.tilt, p2.tilt, p3.tilt)),
                    normalizedOrientation(interpolate(p0.orientation, p1.orientation, p2.orientation, p3.orientation))
                )
            }
        }
        result[result.lastIndex] = points.last()
        return result
    }
}

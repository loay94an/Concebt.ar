package app.mafahim.canvas

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Independent, deterministic brush geometry. It does not depend on Concepts binaries. */
enum class BrushPreset { FINELINER, PENCIL, MARKER, ROUND_BRUSH, CHISEL, ERASER }

data class BrushDab(
    val center: CanvasPoint,
    val radiusX: Float,
    val radiusY: Float,
    /** Radians clockwise from the x axis in canvas coordinates. */
    val rotation: Float,
    val opacity: Float
) {
    fun outline(vertices: Int = 24): List<CanvasPoint> {
        val count = vertices.coerceIn(8, 96)
        val c = cos(rotation)
        val s = sin(rotation)
        return (0 until count).map { index ->
            val angle = (2.0 * PI * index / count).toFloat()
            val x = cos(angle) * radiusX
            val y = sin(angle) * radiusY
            CanvasPoint(center.x + x * c - y * s, center.y + x * s + y * c)
        }
    }
}

object IndependentBrushEngine {
    /**
     * Converts raw stylus samples into pressure/tilt/orientation-aware dabs.
     * orientation is retained as the nib azimuth; tilt controls eccentricity.
     */
    fun createDabs(
        points: List<CanvasPoint>,
        preset: BrushPreset,
        baseWidth: Float,
        baseOpacity: Float = 1f,
        spacing: Float = 0.18f
    ): List<BrushDab> {
        if (points.isEmpty()) return emptyList()
        val width = baseWidth.takeIf { it.isFinite() }?.coerceIn(0.5f, 256f) ?: 5f
        val opacity = baseOpacity.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 1f
        val safeSpacing = spacing.takeIf { it.isFinite() }?.coerceIn(0.04f, 1f) ?: 0.18f
        val samples = resample(points, max(0.5f, width * safeSpacing))
        return samples.map { point ->
            val pressure = CanvasGeometry.normalizedPressure(point.pressure)
            val tilt = (CanvasGeometry.normalizedTilt(point.tilt) / (PI / 2.0).toFloat()).coerceIn(0f, 1f)
            val pressureScale = when (preset) {
                BrushPreset.FINELINER -> 0.72f + pressure * 0.28f
                BrushPreset.PENCIL -> 0.35f + pressure * 0.85f
                BrushPreset.MARKER -> 0.88f + pressure * 0.12f
                BrushPreset.ROUND_BRUSH -> 0.2f + pressure * 0.8f
                BrushPreset.CHISEL -> 0.75f + pressure * 0.25f
                BrushPreset.ERASER -> 1f
            }
            val elongation = when (preset) {
                BrushPreset.CHISEL -> 2.8f
                BrushPreset.PENCIL -> 1f + tilt * 1.6f
                BrushPreset.ROUND_BRUSH -> 1f + tilt * 2.2f
                else -> 1f + tilt * 1.35f
            }
            val radius = width * pressureScale / 2f
            val rotation = CanvasGeometry.normalizedOrientation(point.orientation)
            val alpha = when (preset) {
                BrushPreset.PENCIL -> opacity * (0.25f + pressure * 0.65f)
                BrushPreset.MARKER -> opacity * 0.42f
                else -> opacity
            }
            val rx = radius * sqrt(elongation)
            val ry = radius / sqrt(elongation)
            BrushDab(point, rx, ry, rotation, alpha.coerceIn(0f, 1f))
        }
    }

    private fun resample(points: List<CanvasPoint>, step: Float): List<CanvasPoint> {
        if (points.size == 1) return points
        val result = ArrayList<CanvasPoint>()
        result += points.first()
        var carry = 0f
        for (index in 0 until points.lastIndex) {
            val a = points[index]
            val b = points[index + 1]
            val dx = b.x - a.x
            val dy = b.y - a.y
            val distance = kotlin.math.hypot(dx, dy)
            if (distance <= 0.0001f) continue
            var travelled = step - carry
            while (travelled <= distance) {
                val t = travelled / distance
                result += CanvasPoint(
                    a.x + dx * t, a.y + dy * t,
                    lerp(a.pressure, b.pressure, t),
                    lerp(a.tilt, b.tilt, t),
                    lerpAngle(a.orientation, b.orientation, t)
                )
                travelled += step
            }
            carry = (carry + distance) % step
        }
        if (result.last() != points.last()) result += points.last()
        return result
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    private fun lerpAngle(a: Float, b: Float, t: Float): Float {
        val delta = ((b - a + PI.toFloat()) % (2f * PI.toFloat()) + 2f * PI.toFloat()) % (2f * PI.toFloat()) - PI.toFloat()
        return a + delta * t
    }
}

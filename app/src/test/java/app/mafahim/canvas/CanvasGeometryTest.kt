package app.mafahim.canvas

import org.junit.Assert.assertEquals
import kotlin.math.PI
import org.junit.Test

class CanvasGeometryTest {
    @Test
    fun screenPointConvertsToWorldCoordinates() {
        val result = CanvasGeometry.toWorld(CanvasPoint(210f, 130f), 2f, 10f, 30f)
        assertEquals(100f, result.x, 0.001f)
        assertEquals(50f, result.y, 0.001f)
    }

    @Test
    fun zoomIsClampedBeforeCoordinateConversion() {
        val result = CanvasGeometry.toWorld(CanvasPoint(8f, 8f), 0f, 0f, 0f)
        assertEquals(40f, result.x, 0.001f)
        assertEquals(40f, result.y, 0.001f)
    }

    @Test
    fun pressureIsClampedToPhysicalRange() {
        assertEquals(0.05f, CanvasGeometry.normalizedPressure(-2f), 0.001f)
        assertEquals(1f, CanvasGeometry.normalizedPressure(4f), 0.001f)
        assertEquals(1f, CanvasGeometry.normalizedPressure(Float.NaN), 0.001f)
    }

    @Test
    fun smoothKeepsEndpointsAndAddsIntermediateSamples() {
        val points = listOf(CanvasPoint(0f, 0f), CanvasPoint(10f, 10f), CanvasPoint(20f, 0f))
        val smoothed = CanvasGeometry.smooth(points, subdivisions = 4)
        assertEquals(9, smoothed.size)
        assertEquals(points.first(), smoothed.first())
        assertEquals(points.last(), smoothed.last())
    }

    @Test
    fun tiltAndOrientationAreNormalized() {
        assertEquals(0f, CanvasGeometry.normalizedTilt(-1f), 0.001f)
        assertEquals((PI / 2.0).toFloat(), CanvasGeometry.normalizedTilt(9f), 0.001f)
        assertEquals(0f, CanvasGeometry.normalizedOrientation(Float.NaN), 0.001f)
        assertEquals(1.35f, CanvasGeometry.pressureTiltWidth(CanvasPoint(0f, 0f, 1f, (PI / 2.0).toFloat()), 1f), 0.001f)
    }

    @Test
    fun distanceUsesEuclideanMetric() {
        assertEquals(5f, CanvasGeometry.distance(CanvasPoint(0f, 0f), CanvasPoint(3f, 4f)), 0.001f)
    }
}

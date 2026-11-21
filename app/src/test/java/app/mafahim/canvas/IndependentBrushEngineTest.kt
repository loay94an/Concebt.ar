package app.mafahim.canvas

import org.junit.Assert.*
import org.junit.Test

class IndependentBrushEngineTest {
    @Test fun pressureChangesNibSize() {
        val light = IndependentBrushEngine.createDabs(listOf(CanvasPoint(0f, 0f, pressure = 0.2f)), BrushPreset.ROUND_BRUSH, 10f).single()
        val firm = IndependentBrushEngine.createDabs(listOf(CanvasPoint(0f, 0f, pressure = 1f)), BrushPreset.ROUND_BRUSH, 10f).single()
        assertTrue(firm.radiusX > light.radiusX)
    }
    @Test fun tiltAndAzimuthChangeChiselGeometry() {
        val dab = IndependentBrushEngine.createDabs(listOf(CanvasPoint(10f, 20f, pressure = 1f, tilt = 1.2f, orientation = 0.7f)), BrushPreset.CHISEL, 12f).single()
        assertTrue(dab.radiusX > dab.radiusY)
        assertEquals(0.7f, dab.rotation, 0.001f)
        assertEquals(24, dab.outline().size)
    }
    @Test fun resamplingFollowsPathAndIncludesEndPoint() {
        val dabs = IndependentBrushEngine.createDabs(listOf(CanvasPoint(0f, 0f), CanvasPoint(100f, 0f)), BrushPreset.FINELINER, 5f)
        assertTrue(dabs.size > 10)
        assertEquals(100f, dabs.last().center.x, 0.001f)
    }
    @Test fun largeStylusStrokeResamplesWithoutDroppingEndpoint() {
        val points = (0..10_000).map { index ->
            CanvasPoint(index.toFloat(), (index % 7).toFloat(), pressure = 0.2f + (index % 8) * 0.1f)
        }
        val dabs = IndependentBrushEngine.createDabs(points, BrushPreset.ROUND_BRUSH, 5f)
        assertTrue(dabs.size > 10_000)
        assertEquals(points.last().x, dabs.last().center.x, 0.001f)
        assertTrue(dabs.all { it.radiusX.isFinite() && it.radiusY.isFinite() && it.opacity.isFinite() })
    }

    @Test fun emptyAndInvalidInputAreSafe() {
        assertTrue(IndependentBrushEngine.createDabs(emptyList(), BrushPreset.PENCIL, Float.NaN).isEmpty())
        val dab = IndependentBrushEngine.createDabs(listOf(CanvasPoint(0f, 0f, pressure = Float.NaN, tilt = Float.NaN, orientation = Float.NaN)), BrushPreset.PENCIL, Float.NaN).single()
        assertTrue(dab.radiusX.isFinite() && dab.opacity.isFinite())
    }
}

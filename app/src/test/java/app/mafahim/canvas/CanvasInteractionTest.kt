package app.mafahim.canvas

import org.junit.Assert.*
import org.junit.Test

class CanvasInteractionTest {
    private fun sampleDocument() = DrawingDocument(
        title = "test",
        layers = listOf(DrawingLayer("l1", "Layer")),
        strokes = listOf(DrawStroke("s1", "l1", BrushTool.PEN, -1, 4f, 1f,
            listOf(CanvasPoint(0f, 0f), CanvasPoint(100f, 0f)))),
        images = listOf(DrawingImage("i1", "image.png", 20f, 20f, 40f, 30f))
    )

    @Test fun hitTestFindsImageAndStroke() {
        val doc = sampleDocument()
        assertEquals(setOf("i1"), CanvasSelectionEngine.hitTest(doc, 30f, 30f, 2f).imageIds)
        assertEquals(setOf("s1"), CanvasSelectionEngine.hitTest(doc, 50f, 1f, 2f).strokeIds)
        assertTrue(CanvasSelectionEngine.hitTest(doc, 500f, 500f, 2f).isEmpty)
    }

    @Test fun transformsOnlySelectedObjects() {
        val doc = sampleDocument()
        val selection = CanvasSelection(strokeIds = setOf("s1"))
        val moved = CanvasSelectionEngine.transform(doc, selection, SelectionTransform(translateX = 10f, translateY = 5f))
        assertEquals(10f, moved.strokes.first().points.first().x, 0.001f)
        assertEquals(5f, moved.strokes.first().points.first().y, 0.001f)
        assertEquals(doc.images, moved.images)
    }

    @Test fun scaleAndRotatePreserveFiniteCoordinates() {
        val doc = sampleDocument()
        val selected = CanvasSelection(imageIds = setOf("i1"))
        val result = CanvasSelectionEngine.transform(doc, selected, SelectionTransform(scaleX = 2f, scaleY = 2f, rotationDegrees = 90f))
        val image = result.images.first()
        assertTrue(image.width > 1f && image.height > 1f)
        assertEquals(90f, image.rotationDegrees, 0.001f)
        assertTrue(image.x.isFinite() && image.y.isFinite())
    }

    @Test fun snapUsesNearestCandidateWithinTolerance() {
        val point = CanvasPoint(9f, 9f, pressure = 0.4f)
        val result = CanvasSnapEngine.snap(point, emptyList(), 10f, emptyList(), 3f)
        assertTrue(result.snapped)
        assertEquals(10f, result.point.x, 0.001f)
        assertEquals(0.4f, result.point.pressure, 0.001f)
    }

    @Test fun lineGuideProjectsOntoGuide() {
        val guide = ShapeGuide("g1", ShapeGuideType.LINE, CanvasPoint(0f, 0f), 0f)
        val result = CanvasSnapEngine.snap(CanvasPoint(10f, 2f), listOf(guide), null, emptyList(), 3f)
        assertTrue(result.snapped)
        assertEquals(10f, result.point.x, 0.001f)
        assertEquals(0f, result.point.y, 0.001f)
    }

    @Test fun rotatedImageHitTestUsesInverseRotation() {
        val doc = sampleDocument().copy(images = listOf(DrawingImage("rot", "r.png", 0f, 0f, 100f, 20f, 90f)))
        val selection = CanvasSelectionEngine.hitTest(doc, 50f, -35f, 1f)
        assertEquals(setOf("rot"), selection.imageIds)
    }

    @Test fun lockedLayerCannotBeSelected() {
        val doc = sampleDocument().copy(layers = listOf(DrawingLayer("l1", "Layer", locked = true)))
        assertTrue(CanvasSelectionEngine.hitTest(doc, 50f, 1f, 2f).isEmpty)
    }

    @Test fun measurementSupportsCalibrationAndUnits() {
        val a = CanvasPoint(0f, 0f)
        val b = CanvasPoint(100f, 0f)
        assertEquals(50f, CanvasMeasurement.distance(a, b, MeasurementUnit.MILLIMETER, 2f), 0.001f)
        assertEquals("5.00 cm", CanvasMeasurement.label(a, b, MeasurementUnit.CENTIMETER, 2f))
    }
}

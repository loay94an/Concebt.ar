package app.mafahim.canvas

import org.junit.Assert.*
import org.junit.Test

class CanvasEditingEngineTest {
    private fun document() = DrawingDocument(
        title = "اختبار",
        layers = listOf(DrawingLayer("l1", "طبقة 1")),
        strokes = listOf(
            DrawStroke("s1", "l1", BrushTool.PEN, 0xFF000000.toInt(), 4f, 1f,
                listOf(CanvasPoint(10f, 10f), CanvasPoint(20f, 20f)))
        ),
        images = listOf(DrawingImage("i1", "i1.png", 30f, 30f, 40f, 40f))
    )

    @Test fun multiSelectionToggleAddsAndRemovesObjects() {
        val first = CanvasSelection(strokeIds = setOf("s1"))
        val both = CanvasEditingEngine.toggleSelection(first, CanvasSelection(imageIds = setOf("i1")))
        assertEquals(setOf("s1"), both.strokeIds)
        assertEquals(setOf("i1"), both.imageIds)
        assertEquals(first, CanvasEditingEngine.toggleSelection(both, CanvasSelection(imageIds = setOf("i1"))))
    }

    @Test fun copyPasteCreatesNewIdsAndOffsetsGeometry() {
        val original = document()
        val clip = CanvasEditingEngine.copy(original, CanvasSelection(setOf("s1"), setOf("i1")))
        val (pasted, selection) = CanvasEditingEngine.paste(original, clip, 12f)
        assertEquals(2, pasted.strokes.size)
        assertEquals(2, pasted.images.size)
        assertNotEquals("s1", pasted.strokes.last().id)
        assertEquals(22f, pasted.strokes.last().points.first().x, 0.001f)
        assertEquals(42f, pasted.images.last().x, 0.001f)
        assertEquals(1, selection.strokeIds.size)
        assertEquals(1, selection.imageIds.size)
    }

    @Test fun cutReturnsClipboardAndRemovesSelectedObjects() {
        val (cut, clipboard) = CanvasEditingEngine.cut(document(), CanvasSelection(setOf("s1"), setOf("i1")))
        assertTrue(cut.strokes.isEmpty())
        assertTrue(cut.images.isEmpty())
        assertFalse(clipboard.isEmpty)
    }

    @Test fun cropHandlesStayInsideSourceAndKeepMinimumSize() {
        val frame = ImageCropFrame().drag(CropHandle.TOP_LEFT, 0.4f, 0.3f)
        assertEquals(0.4f, frame.left, 0.001f)
        assertEquals(0.3f, frame.top, 0.001f)
        val limited = frame.drag(CropHandle.RIGHT, -2f, 0f)
        assertTrue(limited.right - limited.left >= 0.019f)
    }

    @Test fun colorHexRoundTripsAndRejectsInvalidInput() {
        val color = CanvasColorEngine.parseHex("#123456")!!
        assertEquals(0xFF123456.toInt(), color)
        assertEquals("#FF123456", CanvasColorEngine.hex(color))
        assertNull(CanvasColorEngine.parseHex("xyz"))
    }

    @Test fun geometricGuidesSupportAngleConstraints() {
        assertEquals(45f, CanvasGuideFactory.constrainedAngle(43f, 15f), 0.001f)
        val guide = CanvasGuideFactory.perpendicular("p", CanvasPoint(0f, 0f), 30f)
        assertEquals(120f, guide.angleDegrees, 0.001f)
    }
}

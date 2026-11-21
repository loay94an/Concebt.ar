package app.mafahim.canvas

import org.junit.Assert.*
import org.junit.Test

class CanvasPageEngineTest {
    private fun base() = DrawingDocument(
        title = "مستند",
        layers = listOf(DrawingLayer("layer-a", "الرسم")),
        strokes = listOf(DrawStroke("stroke-a", "layer-a", BrushTool.PEN, -1, 4f, 1f,
            listOf(CanvasPoint(1f, 2f), CanvasPoint(3f, 4f))))
    )

    @Test fun creatingPageKeepsPreviousContentAndStartsEmpty() {
        val first = CanvasPageEngine.normalized(base())
        val firstId = first.activePageId!!
        val second = CanvasPageEngine.create(first, "الصفحة الثانية")
        assertEquals(2, second.pages.size)
        assertEquals(firstId, second.pages.first().id)
        assertEquals(1, second.pages.first().strokes.size)
        assertTrue(second.strokes.isEmpty())
        assertEquals("الصفحة الثانية", second.pages.last().name)
    }

    @Test fun switchingPagesRestoresIndependentStrokes() {
        val first = CanvasPageEngine.normalized(base())
        val firstId = first.activePageId!!
        val second = CanvasPageEngine.create(first, "2")
        val secondId = second.activePageId!!
        val back = CanvasPageEngine.activate(second, firstId)
        assertEquals(listOf("stroke-a"), back.strokes.map { it.id })
        val forward = CanvasPageEngine.activate(back, secondId)
        assertTrue(forward.strokes.isEmpty())
    }

    @Test fun duplicatePageCopiesContentWithFreshIdentifiers() {
        val first = CanvasPageEngine.normalized(base())
        val id = first.activePageId!!
        val duplicate = CanvasPageEngine.duplicate(first, id)
        assertEquals(2, duplicate.pages.size)
        assertNotEquals(first.pages.first().id, duplicate.pages.last().id)
        assertNotEquals(first.pages.first().strokes.first().id, duplicate.pages.last().strokes.first().id)
        assertEquals(1, duplicate.strokes.size)
    }
}

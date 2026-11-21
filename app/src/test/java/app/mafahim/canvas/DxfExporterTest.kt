package app.mafahim.canvas

import org.junit.Assert.assertTrue
import org.junit.Test

class DxfExporterTest {
    @Test
    fun exportsVisibleStrokesAsLayeredPolylines() {
        val document = DrawingDocument(
            "DXF",
            listOf(DrawingLayer("sketch", "الرسم")),
            listOf(
                DrawStroke("s1", "sketch", BrushTool.PEN, 0xFF000000.toInt(), 4f, 1f,
                    listOf(CanvasPoint(1f, 2f), CanvasPoint(10f, 20f)))
            )
        )

        val dxf = DxfExporter.buildDxf(document)

        assertTrue(dxf.contains("AC1015"))
        assertTrue(dxf.contains("LWPOLYLINE"))
        assertTrue(dxf.contains("sketch"))
        assertTrue(dxf.contains("10\n1.0\n20\n2.0"))
        assertTrue(dxf.endsWith("0\nEOF\n"))
    }
}

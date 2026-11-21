package app.mafahim.canvas

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SvgExporterTest {
    @Test
    fun exportsActualDrawingBoundsIncludingNegativeCoordinates() {
        val document = DrawingDocument("اختبار", listOf(DrawingLayer("layer-1", "الطبقة الأولى")),
            listOf(DrawStroke("stroke-1", "layer-1", BrushTool.PEN, 0xFF123456.toInt(),
                4f, 1f, listOf(CanvasPoint(-20f, 10f), CanvasPoint(80f, 110f)))))

        val svg = SvgExporter.buildSvg(document)

        assertTrue(svg.contains("viewBox=\"-44.0 -14.0 148.0 148.0\""))
        assertTrue(svg.contains("#123456"))
        assertTrue(svg.contains("<polyline"))
    }

    @Test
    fun exportsSinglePointAsCircle() {
        val document = DrawingDocument("نقطة", listOf(DrawingLayer("layer-1", "الطبقة")),
            listOf(DrawStroke("dot", "layer-1", BrushTool.PEN, 0xFF000000.toInt(),
                8f, 1f, listOf(CanvasPoint(15f, 25f)))))

        assertTrue(SvgExporter.buildSvg(document).contains("<circle"))
    }

    @Test
    fun hiddenLayerIsNotExported() {
        val document = DrawingDocument("طبقات", listOf(DrawingLayer("layer-1", "مخفية", visible = false)),
            listOf(DrawStroke("hidden", "layer-1", BrushTool.PEN, -1, 4f, 1f,
                listOf(CanvasPoint(1f, 1f), CanvasPoint(2f, 2f)))))

        val svg = SvgExporter.buildSvg(document)

        assertFalse(svg.contains("<polyline"))
        assertTrue(svg.contains("<svg"))
    }
}

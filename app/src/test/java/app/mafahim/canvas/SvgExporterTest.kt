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
    fun exportsPressureSensitiveSegmentsWithVariableWidths() {
        val document = DrawingDocument("ضغط", listOf(DrawingLayer("layer-1", "الطبقة")),
            listOf(DrawStroke("pressure", "layer-1", BrushTool.PEN, 0xFF000000.toInt(),
                10f, 1f, listOf(CanvasPoint(0f, 0f, 0.2f), CanvasPoint(20f, 0f, 1f)))))

        val svg = SvgExporter.buildSvg(document)

        assertTrue(svg.contains("<line"))
        assertTrue(svg.contains("stroke-width=\"6.0\""))
    }

    @Test
    fun embedsImportedImageAsDataUriWhenProvided() {
        val image = DrawingImage("image-1", "image-1.png", 12f, 18f, 120f, 80f)
        val document = DrawingDocument("صورة", listOf(DrawingLayer("layer-1", "الطبقة")), emptyList(),
            images = listOf(image))

        val svg = SvgExporter.buildSvg(document, mapOf("image-1.png" to "data:image/png;base64,AAAA"))

        assertTrue(svg.contains("<image"))
        assertTrue(svg.contains("data:image/png;base64,AAAA"))
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

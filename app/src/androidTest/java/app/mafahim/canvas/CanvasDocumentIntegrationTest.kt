package app.mafahim.canvas

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CanvasDocumentIntegrationTest {
    @Test fun layeredPsdExportCreatesPhotoshopFile() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val document = DrawingDocument.empty().copy(
            strokes = listOf(DrawStroke("psd-stroke", "layer-base", BrushTool.PEN, -1, 4f, 1f,
                listOf(CanvasPoint(10f, 10f), CanvasPoint(90f, 90f))))
        )
        val uri = PsdExporter.export(context, document)
        val header = context.contentResolver.openInputStream(uri)!!.use { input ->
            ByteArray(4).also { buffer -> input.read(buffer) }
        }
        assertEquals("8BPS", String(header, Charsets.US_ASCII))
    }

    @Test fun saveRestorePreservesLayersImagesGuidesAndStylusSamples() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = DocumentRepository(context)
        val id = "integration-${System.currentTimeMillis()}"
        val document = DrawingDocument(
            title = "اختبار تكامل",
            id = id,
            layers = listOf(DrawingLayer("layer-1", "حبر", visible = true, opacity = 0.65f, locked = true)),
            strokes = listOf(DrawStroke("stroke-1", "layer-1", BrushTool.CHISEL, -1, 12f, 0.8f,
                listOf(CanvasPoint(1f, 2f, 0.4f, 0.8f, 0.3f), CanvasPoint(8f, 9f, 0.9f, 0.2f, 1.1f)))),
            images = listOf(DrawingImage("image-1", "image.png", 12f, 13f, 90f, 45f, 27f)),
            guides = listOf(ShapeGuide("guide-1", ShapeGuideType.PERPENDICULAR, CanvasPoint(5f, 6f), 30f, 24f, true)),
            artboards = listOf(DrawingArtboard("board-1", "صفحة A4", -10f, -20f, 210f, 297f)),
            activeArtboardId = "board-1"
        )
        repository.save(document)
        val restored = repository.load(id)
        assertNotNull(restored)
        val actual = requireNotNull(restored)
        assertEquals(document.title, actual.title)
        assertEquals(document.layers, actual.layers)
        assertEquals(document.strokes, actual.strokes)
        assertEquals(document.images, actual.images)
        assertEquals(document.guides, actual.guides)
        assertEquals(document.artboards, actual.artboards)
        assertEquals(1, actual.pages.size)
        assertEquals(actual.pages.first().strokes, actual.strokes)
        repository.delete(id)
    }
    @Test fun pdfExportWritesCompletePdfDocument() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val document = DrawingDocument.empty().copy(
            strokes = listOf(DrawStroke("pdf-stroke", "layer-base", BrushTool.PEN, -1, 6f, 1f,
                listOf(CanvasPoint(10f, 20f), CanvasPoint(90f, 120f))))
        )
        val uri = PdfExporter.export(context, document)
        val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        assertTrue(bytes.size > 500)
        assertTrue(String(bytes.copyOfRange(0, 5), Charsets.US_ASCII).startsWith("%PDF"))
        assertTrue(String(bytes.takeLast(20).toByteArray(), Charsets.US_ASCII).contains("%%EOF"))
    }

}

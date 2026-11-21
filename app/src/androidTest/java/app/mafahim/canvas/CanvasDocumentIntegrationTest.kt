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
            guides = listOf(ShapeGuide("guide-1", ShapeGuideType.PERPENDICULAR, CanvasPoint(5f, 6f), 30f, 24f, true))
        )
        repository.save(document)
        val restored = repository.load(id)
        assertNotNull(restored)
        assertEquals(document, restored)
        repository.delete(id)
    }
}

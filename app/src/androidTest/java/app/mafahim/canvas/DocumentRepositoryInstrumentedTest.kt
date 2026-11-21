package app.mafahim.canvas

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DocumentRepositoryInstrumentedTest {
    @Test
    fun roundTripsStylusDataAndImportedImageMetadata() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = DocumentRepository(context)
        val id = "test-" + UUID.randomUUID().toString()
        val document = DrawingDocument(
            title = "اختبار حفظ",
            layers = listOf(DrawingLayer("layer-1", "طبقة")),
            strokes = listOf(
                DrawStroke(
                    "stroke-1", "layer-1", BrushTool.PEN, 0xFF123456.toInt(), 8f, 1f,
                    listOf(CanvasPoint(1f, 2f, 0.4f, 0.7f, 0.3f), CanvasPoint(8f, 9f, 0.9f, 0.2f, -0.4f))
                )
            ),
            id = id,
            images = listOf(DrawingImage("image-1", "image-1.png", 12f, 14f, 200f, 100f))
        )

        repository.save(document)
        val restored = repository.load(id)

        assertNotNull(restored)
        assertEquals(id, restored!!.id)
        assertEquals(0.4f, restored.strokes.first().points.first().pressure, 0.001f)
        assertEquals(0.7f, restored.strokes.first().points.first().tilt, 0.001f)
        assertEquals(0.3f, restored.strokes.first().points.first().orientation, 0.001f)
        assertEquals("image-1.png", restored.images.first().fileName)
        assertEquals(200f, restored.images.first().width, 0.001f)
        repository.delete(id)
    }
}

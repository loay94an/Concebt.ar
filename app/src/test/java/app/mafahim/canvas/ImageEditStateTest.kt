package app.mafahim.canvas

import org.junit.Assert.*
import org.junit.Test

class ImageEditStateTest {
    private val initial = ImageEditState("img-1", ImageRect(10f, 20f, 210f, 120f))
    @Test fun moveTranslatesBoundsAndCrop() {
        val moved = initial.setCrop(ImageRect(20f, 30f, 100f, 90f)).move(5f, -10f)
        assertEquals(ImageRect(15f, 10f, 215f, 110f), moved.bounds)
        assertEquals(ImageRect(25f, 20f, 105f, 80f), moved.crop)
    }
    @Test fun resizeCanPreserveAspectRatio() {
        val resized = initial.resize(110f, 220f, keepAspect = true)
        assertEquals(2f, resized.bounds.width / resized.bounds.height, 0.001f)
        assertNull(resized.crop)
    }
    @Test fun cropIsClippedToImageBounds() {
        val cropped = initial.setCrop(ImageRect(-20f, 40f, 90f, 200f))
        assertEquals(ImageRect(10f, 40f, 90f, 120f), cropped.crop)
        assertFalse(cropped.hitTest(150f, 60f))
        assertTrue(cropped.hitTest(50f, 60f))
    }
    @Test fun disjointCropBecomesEmpty() {
        val emptyCrop = initial.setCrop(ImageRect(300f, 300f, 400f, 400f))\n        assertEquals(0f, emptyCrop.visibleBounds.width, 0.001f)\n        assertFalse(emptyCrop.hitTest(50f, 50f))
    }
}

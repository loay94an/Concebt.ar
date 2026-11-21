package app.mafahim.canvas

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PsdWriterTest {
    @Test fun writesPhotoshopHeaderAndRgbDocument() {
        val rgba = byteArrayOf(
            0xFF.toByte(), 0, 0, 0xFF.toByte(),
            0, 0xFF.toByte(), 0, 0xFF.toByte()
        )
        val bytes = PsdWriter.write(2, 1, listOf(PsdRasterLayer("Ink", true, rgba)), rgba)
        assertEquals("8BPS", String(bytes.copyOfRange(0, 4), Charsets.US_ASCII))
        val header = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        header.position(4)
        assertEquals(1, header.short.toInt())
        header.position(12)
        assertEquals(3, header.short.toInt())
        assertEquals(1, header.int)
        assertEquals(2, header.int)
        assertEquals(8, header.short.toInt())
        assertEquals(3, header.short.toInt())
        assertTrue(bytes.size > 100)
    }

    @Test fun rejectsInvalidLayerPixelBuffer() {
        try {
            PsdWriter.write(1, 1, listOf(PsdRasterLayer("bad", true, byteArrayOf(1, 2))), byteArrayOf(1, 2, 3, 4))
            fail("Expected invalid raster rejection")
        } catch (_: IllegalArgumentException) { }
    }
}

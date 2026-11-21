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
    @Test fun layerAndCompositeSectionsHaveConsistentRawChannelLengths() {
        val width = 2
        val height = 1
        val pixels = width * height
        val rgba = ByteArray(pixels * 4) { (it * 17).toByte() }
        val bytes = PsdWriter.write(width, height, listOf(
            PsdRasterLayer("حبر", true, rgba),
            PsdRasterLayer("خلفية", false, rgba)
        ), rgba)
        val input = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        input.position(26)
        val colorModeLength = input.int
        input.position(input.position() + colorModeLength)
        val resourcesLength = input.int
        input.position(input.position() + resourcesLength)
        val layerMaskLength = input.int
        val layerMaskStart = input.position()
        val layerInfoLength = input.int
        val layerInfoStart = input.position()
        val layerInfoEnd = layerInfoStart + layerInfoLength
        val layerCount = input.short.toInt()
        assertEquals(2, layerCount)
        repeat(layerCount) {
            input.position(input.position() + 16)
            val channelCount = input.short.toInt()
            assertEquals(4, channelCount)
            repeat(channelCount) {
                val channelId = input.short.toInt()
                assertTrue(channelId in listOf(0, 1, 2, -1))
                assertEquals(2 + pixels, input.int)
            }
            input.position(input.position() + 8) // blend signature and mode
            input.position(input.position() + 4) // opacity, clipping, flags, filler
            val extraLength = input.int
            assertTrue(extraLength >= 0)
            input.position(input.position() + extraLength)
        }
        repeat(layerCount * 4) {
            assertEquals(0, input.short.toInt()) // raw compression
            input.position(input.position() + pixels)
        }
        assertEquals(layerInfoEnd, input.position())
        input.position(layerInfoEnd)
        assertEquals(0, input.int) // global layer mask length
        assertEquals(layerMaskStart + layerMaskLength, input.position())
        assertEquals(0, input.short.toInt()) // composite raw compression
        assertEquals(3 * pixels, input.remaining())
    }

}

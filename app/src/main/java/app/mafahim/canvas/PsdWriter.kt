package app.mafahim.canvas

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

/** One full-canvas layer in row-major RGBA byte order. */
data class PsdRasterLayer(val name: String, val visible: Boolean, val rgba: ByteArray, val opacity: Float = 1f)

object PsdWriter {
    fun write(width: Int, height: Int, layers: List<PsdRasterLayer>, compositeRgba: ByteArray): ByteArray {
        require(width in 1..30000 && height in 1..30000)
        val pixels = Math.multiplyExact(width, height)
        require(compositeRgba.size == pixels * 4)
        require(layers.size <= 100)
        layers.forEach { require(it.rgba.size == pixels * 4) }

        val layerInfoBuffer = ByteArrayOutputStream()
        DataOutputStream(layerInfoBuffer).use { out ->
            out.writeShort(layers.size)
            layers.forEach { layer ->
                out.writeInt(0); out.writeInt(0); out.writeInt(height); out.writeInt(width)
                out.writeShort(4)
                listOf(0, 1, 2, -1).forEach { channel ->
                    out.writeShort(channel)
                    out.writeInt(2 + pixels)
                }
                out.writeBytes("8BIM")
                out.writeBytes("norm")
                out.writeByte((layer.opacity.takeIf { it.isFinite() } ?: 1f).coerceIn(0f, 1f).times(255f).toInt())
                out.writeByte(0)
                out.writeByte(if (layer.visible) 0 else 2)
                out.writeByte(0)
                val pascalName = layer.name.map { if (it.code in 32..126) it.code.toByte() else '?'.code.toByte() }.take(255).toByteArray()
                val paddedNameLength = ((pascalName.size + 1 + 3) / 4) * 4
                val unicodeName = layer.name.toByteArray(Charsets.UTF_16BE)
                val unicodeBlockLength = 12 + unicodeName.size
                val extraLength = 8 + paddedNameLength + unicodeBlockLength
                out.writeInt(extraLength)
                out.writeInt(0) // layer mask data
                out.writeInt(0) // blending ranges
                out.writeByte(pascalName.size)
                out.write(pascalName)
                repeat(paddedNameLength - pascalName.size - 1) { out.writeByte(0) }
                out.writeBytes("8BIM")
                out.writeBytes("luni")
                out.writeInt(4 + unicodeName.size)
                out.writeInt(layer.name.length)
                out.write(unicodeName)
            }
            layers.forEach { layer ->
                for (channel in intArrayOf(0, 1, 2, 3)) {
                    out.writeShort(0) // raw channel compression
                    var index = channel
                    while (index < layer.rgba.size) {
                        out.writeByte(layer.rgba[index].toInt())
                        index += 4
                    }
                }
            }
            if (layerInfoBuffer.size() % 2 != 0) layerInfoBuffer.write(0)
        }
        val layerInfo = layerInfoBuffer.toByteArray()
        val layerMaskBuffer = ByteArrayOutputStream()
        DataOutputStream(layerMaskBuffer).use { out ->
            out.writeInt(layerInfo.size)
            out.write(layerInfo)
            if (layerInfo.size % 2 != 0) out.writeByte(0)
            out.writeInt(0) // global layer mask info
        }
        val layerMask = layerMaskBuffer.toByteArray()

        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { out ->
            out.writeBytes("8BPS")
            out.writeShort(1)
            repeat(6) { out.writeByte(0) }
            out.writeShort(3)
            out.writeInt(height)
            out.writeInt(width)
            out.writeShort(8)
            out.writeShort(3) // RGB
            out.writeInt(0) // color mode data
            out.writeInt(0) // image resources
            out.writeInt(layerMask.size)
            out.write(layerMask)
            out.writeShort(0) // merged image raw compression
            for (channel in 0..2) {
                var index = channel
                while (index < compositeRgba.size) {
                    out.writeByte(compositeRgba[index].toInt())
                    index += 4
                }
            }
        }
        return output.toByteArray()
    }
}

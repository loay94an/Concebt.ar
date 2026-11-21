package app.mafahim.canvas

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object DxfExporter {
    fun export(context: Context, document: DrawingDocument): Uri {
        val directory = File(context.cacheDir, "exports").apply { mkdirs() }
        val output = File(directory, "mafahim-${System.currentTimeMillis()}.dxf")
        output.writeText(buildDxf(document), Charsets.UTF_8)
        return FileProvider.getUriForFile(context, "${context.packageName}.files", output)
    }

    internal fun buildDxf(document: DrawingDocument): String = buildString {
        appendLine("0"); appendLine("SECTION"); appendLine("2"); appendLine("HEADER")
        appendLine("9"); appendLine("$ACADVER"); appendLine("1"); appendLine("AC1015")
        appendLine("0"); appendLine("ENDSEC")
        val layers = document.layers.filter { it.visible }
        appendLine("0"); appendLine("SECTION"); appendLine("2"); appendLine("TABLES")
        appendLine("0"); appendLine("TABLE"); appendLine("2"); appendLine("LAYER")
        appendLine("70"); appendLine(layers.size.toString())
        layers.forEach { layer ->
            appendLine("0"); appendLine("LAYER")
            appendLine("2"); appendLine(layer.id.filter { it.isLetterOrDigit() || it == '_' }.take(31).ifBlank { "Layer" })
            appendLine("70"); appendLine("0")
            appendLine("62"); appendLine("7")
            appendLine("6"); appendLine("CONTINUOUS")
        }
        appendLine("0"); appendLine("ENDTAB"); appendLine("0"); appendLine("ENDSEC")
        appendLine("0"); appendLine("SECTION"); appendLine("2"); appendLine("ENTITIES")
        document.strokes.filter { stroke -> layers.any { it.id == stroke.layerId } }.forEach { stroke ->
            val layer = stroke.layerId.filter { it.isLetterOrDigit() || it == '_' }.take(31).ifBlank { "Layer" }
            val points = stroke.points
            if (points.size == 1) {
                appendLine("0"); appendLine("POINT")
                appendLine("8"); appendLine(layer)
                appendLine("10"); appendLine(points.first().x.toString())
                appendLine("20"); appendLine(points.first().y.toString())
                appendLine("30"); appendLine("0")
            } else if (points.size > 1) {
                appendLine("0"); appendLine("LWPOLYLINE")
                appendLine("8"); appendLine(layer)
                appendLine("90"); appendLine(points.size.toString())
                appendLine("70"); appendLine(if (points.first() == points.last()) "1" else "0")
                appendLine("43"); appendLine(stroke.width.toString())
                points.forEach { point ->
                    appendLine("10"); appendLine(point.x.toString())
                    appendLine("20"); appendLine(point.y.toString())
                }
            }
        }
        appendLine("0"); appendLine("ENDSEC"); appendLine("0"); appendLine("EOF")
    }
}

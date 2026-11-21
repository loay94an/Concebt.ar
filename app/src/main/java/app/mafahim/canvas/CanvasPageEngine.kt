package app.mafahim.canvas

import java.util.UUID

/** Page snapshots own independent drawing content; switching pages swaps the active document payload. */
object CanvasPageEngine {
    fun normalized(document: DrawingDocument): DrawingDocument {
        if (document.pages.isNotEmpty()) return document
        val page = snapshot(document, UUID.randomUUID().toString(), "الصفحة 1")
        return document.copy(pages = listOf(page), activePageId = page.id)
    }
    fun create(document: DrawingDocument, name: String): DrawingDocument {
        val saved = saveActive(normalized(document))
        val page = DrawingPage(UUID.randomUUID().toString(), name.ifBlank { "صفحة جديدة" }, 0xFFFFFFFF.toInt(),
            saved.layers.map { it.copy(id = UUID.randomUUID().toString()) }.ifEmpty {
                listOf(DrawingLayer(UUID.randomUUID().toString(), "الطبقة الأولى"))
            }, emptyList(), emptyList(), emptyList())
        return activate(saved.copy(pages = saved.pages + page), page.id)
    }
    fun saveActive(document: DrawingDocument): DrawingDocument {
        val id = document.activePageId ?: return document
        val name = document.pages.firstOrNull { it.id == id }?.name ?: "صفحة"
        val page = snapshot(document, id, name)
        return document.copy(pages = document.pages.map { if (it.id == id) page else it })
    }
    fun activate(document: DrawingDocument, pageId: String): DrawingDocument {
        val saved = saveActive(document)
        val page = saved.pages.firstOrNull { it.id == pageId } ?: return document
        return saved.copy(activePageId = page.id, layers = page.layers, strokes = page.strokes,
            images = page.images, guides = page.guides)
    }
    fun rename(document: DrawingDocument, pageId: String, name: String): DrawingDocument =
        document.copy(pages = document.pages.map {
            if (it.id == pageId) it.copy(name = name.trim().ifBlank { it.name }) else it
        })
    fun duplicate(document: DrawingDocument, pageId: String, name: String? = null): DrawingDocument {
        val saved = saveActive(normalized(document))
        val source = saved.pages.firstOrNull { it.id == pageId } ?: return document
        val layers = source.layers.map { it.copy(id = UUID.randomUUID().toString()) }
        val strokes = source.strokes.map { stroke ->
            val index = source.layers.indexOfFirst { it.id == stroke.layerId }
            stroke.copy(id = UUID.randomUUID().toString(),
                layerId = layers.getOrNull(index)?.id ?: layers.first().id,
                points = stroke.points.map { it.copy() })
        }
        val copy = source.copy(id = UUID.randomUUID().toString(),
            name = name ?: "\${source.name} - نسخة", layers = layers, strokes = strokes,
            images = source.images.map { it.copy(id = UUID.randomUUID().toString()) },
            guides = source.guides.map { it.copy(id = UUID.randomUUID().toString()) })
        return activate(saved.copy(pages = saved.pages + copy), copy.id)
    }
    private fun snapshot(document: DrawingDocument, id: String, name: String) = DrawingPage(
        id, name,
        document.artboards.firstOrNull { it.id == document.activeArtboardId }?.backgroundColor ?: 0xFFFFFFFF.toInt(),
        document.layers.toList(), document.strokes.toList(), document.images.toList(), document.guides.toList())
}

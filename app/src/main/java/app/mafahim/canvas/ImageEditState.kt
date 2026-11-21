package app.mafahim.canvas

import kotlin.math.max
import kotlin.math.min

/** Geometry-only image editing model; coordinates are in document/world units. */
data class ImageRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = max(0f, right - left)
    val height get() = max(0f, bottom - top)
    fun contains(x: Float, y: Float) = x >= left && x <= right && y >= top && y <= bottom
    fun intersect(other: ImageRect): ImageRect? {
        val l = max(left, other.left)
        val t = max(top, other.top)
        val r = min(right, other.right)
        val b = min(bottom, other.bottom)
        return if (r > l && b > t) ImageRect(l, t, r, b) else null
    }
}

data class ImageEditState(
    val imageId: String,
    val bounds: ImageRect,
    val crop: ImageRect? = null,
    val rotationDegrees: Float = 0f
) {
    val visibleBounds: ImageRect get() = crop?.let { bounds.intersect(it) } ?: bounds

    fun move(dx: Float, dy: Float): ImageEditState {
        if (!dx.isFinite() || !dy.isFinite()) return this
        return copy(bounds = bounds.translate(dx, dy), crop = crop?.translate(dx, dy))
    }

    /** Resize from one corner. keepAspect=true preserves the current aspect ratio. */
    fun resize(right: Float, bottom: Float, keepAspect: Boolean = false): ImageEditState {
        if (!right.isFinite() || !bottom.isFinite()) return this
        var w = (right - bounds.left).coerceAtLeast(1f)
        var h = (bottom - bounds.top).coerceAtLeast(1f)
        if (keepAspect && bounds.height > 0f) {
            val ratio = bounds.width / bounds.height
            if (w / h > ratio) w = h * ratio else h = w / ratio
        }
        return copy(bounds = ImageRect(bounds.left, bounds.top, bounds.left + w, bounds.top + h), crop = null)
    }

    /** Crop is expressed in document coordinates and clipped to image bounds. */
    fun setCrop(requested: ImageRect?): ImageEditState =
        copy(crop = requested?.let(bounds::intersect))

    fun hitTest(x: Float, y: Float): Boolean = visibleBounds.contains(x, y)
}

private fun ImageRect.translate(dx: Float, dy: Float) =
    copy(left = left + dx, top = top + dy, right = right + dx, bottom = bottom + dy)

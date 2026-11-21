package app.mafahim.canvas

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
@androidx.compose.ui.ExperimentalComposeUiApi
fun CropPreview(
    bitmap: ImageBitmap,
    frame: ImageCropFrame,
    onFrameChange: (ImageCropFrame) -> Unit
) {
    var activeHandle by remember { mutableStateOf<CropHandle?>(null) }
    var lastPointer by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    Canvas(
        modifier = Modifier.fillMaxWidth().height(240.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFE8EBEF))
            .onSizeChanged { canvasSize = it }
            .pointerInteropFilter { event ->
                val width = canvasSize.width.toFloat().coerceAtLeast(1f)
                val height = canvasSize.height.toFloat().coerceAtLeast(1f)
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        val points = listOf(
                            CropHandle.TOP_LEFT to Offset(frame.left, frame.top),
                            CropHandle.TOP to Offset((frame.left + frame.right) / 2f, frame.top),
                            CropHandle.TOP_RIGHT to Offset(frame.right, frame.top),
                            CropHandle.RIGHT to Offset(frame.right, (frame.top + frame.bottom) / 2f),
                            CropHandle.BOTTOM_RIGHT to Offset(frame.right, frame.bottom),
                            CropHandle.BOTTOM to Offset((frame.left + frame.right) / 2f, frame.bottom),
                            CropHandle.BOTTOM_LEFT to Offset(frame.left, frame.bottom),
                            CropHandle.LEFT to Offset(frame.left, (frame.top + frame.bottom) / 2f)
                        )
                        val pointer = Offset(event.x / width, event.y / height)
                        activeHandle = points.minByOrNull { (_, position) ->
                            val dx = position.x - pointer.x
                            val dy = position.y - pointer.y
                            dx * dx + dy * dy
                        }?.first
                        lastPointer = Offset(event.x, event.y)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        activeHandle?.let { handle ->
                            onFrameChange(frame.drag(
                                handle,
                                (event.x - lastPointer.x) / width,
                                (event.y - lastPointer.y) / height
                            ))
                            lastPointer = Offset(event.x, event.y)
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        activeHandle = null
                        true
                    }
                    else -> true
                }
            }
    ) {
        drawImage(bitmap, dstOffset = IntOffset.Zero,
            dstSize = IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1)))
        val left = size.width * frame.left
        val top = size.height * frame.top
        val right = size.width * frame.right
        val bottom = size.height * frame.bottom
        drawRect(Color.Black.copy(alpha = 0.48f), Offset.Zero, Size(size.width, top))
        drawRect(Color.Black.copy(alpha = 0.48f), Offset(0f, bottom), Size(size.width, size.height - bottom))
        drawRect(Color.Black.copy(alpha = 0.48f), Offset(0f, top), Size(left, bottom - top))
        drawRect(Color.Black.copy(alpha = 0.48f), Offset(right, top), Size(size.width - right, bottom - top))
        drawRect(Color(0xFF1976D2), Offset(left, top), Size(right - left, bottom - top), style = Stroke(width = 3f))
        val handles = listOf(
            Offset(left, top), Offset((left + right) / 2f, top), Offset(right, top),
            Offset(right, (top + bottom) / 2f), Offset(right, bottom),
            Offset((left + right) / 2f, bottom), Offset(left, bottom),
            Offset(left, (top + bottom) / 2f)
        )
        handles.forEach { center ->
            drawCircle(Color.White, 8f, center)
            drawCircle(Color(0xFF1976D2), 8f, center, style = Stroke(width = 2f))
        }
    }
}

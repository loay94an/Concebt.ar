package app.mafahim.canvas

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MafahimApp() }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun MafahimApp() {
    val context = LocalContext.current
    val repository = remember { DocumentRepository(context) }
    var document by remember { mutableStateOf(DrawingDocument.empty()) }
    var loaded by remember { mutableStateOf(false) }
    var darkMode by remember { mutableStateOf(false) }
    var activeLayerId by remember { mutableStateOf("layer-base") }
    var selectedTool by remember { mutableStateOf(BrushTool.PEN) }
    var selectedColor by remember { mutableIntStateOf(Color(0xFF20242D).toArgb()) }
    var brushWidth by remember { mutableStateOf(5f) }
    var zoom by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var layersDialog by remember { mutableStateOf(false) }
    var renameLayerId by remember { mutableStateOf<String?>(null) }
    var renameLayerName by remember { mutableStateOf("") }
    var galleryDialog by remember { mutableStateOf(false) }
    var artboardsDialog by remember { mutableStateOf(false) }
    var pagesDialog by remember { mutableStateOf(false) }
    var colorDialog by remember { mutableStateOf(false) }
    var brushLibraryDialog by remember { mutableStateOf(false) }
    var customColorHex by remember { mutableStateOf("#FF20242D") }
    var clipboard by remember { mutableStateOf(CanvasClipboard()) }
    var cropDialogImageId by remember { mutableStateOf<String?>(null) }
    var cropFrame by remember { mutableStateOf(ImageCropFrame()) }
    var documentList by remember { mutableStateOf(emptyList<DocumentSummary>()) }
    val scope = rememberCoroutineScope()
    var imageBitmaps by remember { mutableStateOf<Map<String, ImageBitmap>>(emptyMap()) }
    val undoStack = remember { mutableStateListOf<DrawingDocument>() }
    val redoStack = remember { mutableStateListOf<DrawingDocument>() }
    var canvasSelection by remember { mutableStateOf(CanvasSelection()) }
    var snapEnabled by remember { mutableStateOf(false) }
    var guideAngleDegrees by remember { mutableStateOf(0f) }
    var measurementUnit by remember { mutableStateOf(MeasurementUnit.MILLIMETER) }

    LaunchedEffect(Unit) {
        val saved = withContext(Dispatchers.IO) { repository.load() }
        val initial = CanvasPageEngine.normalized(saved ?: DrawingDocument.empty())
        document = initial
        activeLayerId = initial.layers.firstOrNull()?.id ?: "layer-base"
        documentList = withContext(Dispatchers.IO) { repository.listDocuments() }
        loaded = true
    }

    LaunchedEffect(document, loaded) {
        if (loaded) {
            delay(350)
            withContext(Dispatchers.IO) { repository.save(document) }
            documentList = withContext(Dispatchers.IO) { repository.listDocuments() }
        }
    }

    fun applyDocument(next: DrawingDocument) {
        if (next == document) return
        undoStack.add(document)
        if (undoStack.size > 40) undoStack.removeAt(0)
        redoStack.clear()
        document = next
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val imported = withContext(Dispatchers.IO) { repository.importImage(uri) }
                if (imported != null) {
                    applyDocument(document.copy(images = document.images + imported))
                } else {
                    Toast.makeText(context, "تعذر استيراد الصورة", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(document.images) {
        imageBitmaps = withContext(Dispatchers.IO) {
            document.images.mapNotNull { image ->
                repository.loadImage(image.fileName)?.asImageBitmap()?.let { image.fileName to it }
            }.toMap()
        }
    }

    val snapCandidates = remember(document) {
        val visibleIds = document.layers.filter { it.visible && !it.locked }.mapTo(hashSetOf()) { it.id }
        document.strokes.filter { it.layerId in visibleIds }.flatMap { stroke ->
            listOfNotNull(stroke.points.firstOrNull(), stroke.points.lastOrNull())
        } + document.images.flatMap { image ->
            listOf(CanvasPoint(image.x, image.y), CanvasPoint(image.x + image.width, image.y + image.height))
        }
    }

    fun snapped(point: CanvasPoint): CanvasPoint = if (!snapEnabled) point else
        CanvasSnapEngine.snap(point, document.guides, 34f, snapCandidates, 8f / zoom.coerceAtLeast(0.2f)).point

    fun addStroke(stroke: DrawStroke) {
        val targetLayer = document.layers.firstOrNull { it.id == stroke.layerId } ?: return
        if (!targetLayer.visible || targetLayer.locked) return
        applyDocument(document.copy(strokes = document.strokes + stroke))
    }

    val currentPan by rememberUpdatedState(pan)
    val currentZoom by rememberUpdatedState(zoom)
    val currentColor by rememberUpdatedState(selectedColor)
    val currentWidth by rememberUpdatedState(brushWidth)
    val currentLayer by rememberUpdatedState(activeLayerId)
    val addStrokeNow by rememberUpdatedState<(DrawStroke) -> Unit>({ stroke -> addStroke(stroke) })
    val setPanNow by rememberUpdatedState<(Offset) -> Unit>({ value -> pan = value })
    val setViewNow by rememberUpdatedState<(Float, Offset) -> Unit>({ nextZoom, nextPan ->
        zoom = nextZoom
        pan = nextPan
    })
    val currentTool by rememberUpdatedState(selectedTool)
    val gesturePoints = remember { mutableListOf<CanvasPoint>() }
    var activeTool by remember { mutableStateOf(BrushTool.PEN) }
    var drawingGesture by remember { mutableStateOf(false) }
    var panningGesture by remember { mutableStateOf(false) }
    var previousCentroid by remember { mutableStateOf(Offset.Zero) }
    var previousSpan by remember { mutableStateOf(0f) }

    val palette = listOf(
        0xFF20242D.toInt(), 0xFF8B5CF6.toInt(), 0xFFEC4899.toInt(), 0xFF06B6D4.toInt(),
        0xFF10B981.toInt(), 0xFFF59E0B.toInt(), 0xFFEF4444.toInt(), 0xFF3B82F6.toInt(),
        0xFF111111.toInt(), 0xFF777777.toInt(), 0xFFB8A7D9.toInt(), 0xFFFFFFFF.toInt()
    )

    // Cache deterministic brush geometry per document revision; the Canvas draw pass
    // only maps the prepared nib outlines into screen coordinates.
    val preparedBrushDabs = remember(document) {
        document.strokes.associate { stroke ->
            val preset = when (stroke.tool) {
                BrushTool.PENCIL -> BrushPreset.PENCIL
                BrushTool.MARKER -> BrushPreset.MARKER
                BrushTool.ERASER -> BrushPreset.ERASER
                BrushTool.ROUND_BRUSH -> BrushPreset.ROUND_BRUSH
                BrushTool.CHISEL -> BrushPreset.CHISEL
                else -> BrushPreset.FINELINER
            }
            stroke.id to IndependentBrushEngine.createDabs(
                stroke.points, preset, stroke.width, stroke.opacity
            )
        }
    }

    val colorScheme = if (darkMode) {
        darkColorScheme(
            primary = Color(0xFFC4B5FD),
            secondary = Color(0xFF67E8F9),
            tertiary = Color(0xFFF9A8D4),
            surface = Color(0xFF191722),
            background = Color(0xFF101018)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF8B5CF6),
            secondary = Color(0xFF06B6D4),
            tertiary = Color(0xFFEC4899),
            surface = Color.White,
            background = Color(0xFFF4F2F8)
        )
    }

    MaterialTheme(colorScheme = colorScheme) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Image(
                                painter = androidx.compose.ui.res.painterResource(app.mafahim.canvas.R.drawable.ic_launcher_foreground),
                                contentDescription = "شعار مفاهيم",
                                modifier = Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(Color(0xFF8B5CF6))
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text("مفاهيم", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text("استوديو الرسم الإبداعي", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)) {
                                Text("لوحة العمل", modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            }
                            TextButton(onClick = {
                                if (undoStack.isNotEmpty()) { redoStack.add(document); document = undoStack.removeAt(undoStack.lastIndex) }
                            }, enabled = undoStack.isNotEmpty()) { Text("↶", fontSize = 22.sp) }
                            TextButton(onClick = {
                                if (redoStack.isNotEmpty()) { undoStack.add(document); document = redoStack.removeAt(redoStack.lastIndex) }
                            }, enabled = redoStack.isNotEmpty()) { Text("↷", fontSize = 22.sp) }
                            TextButton(onClick = { darkMode = !darkMode }) { Text(if (darkMode) "☀" else "☾", fontSize = 18.sp) }
                            TextButton(onClick = { documentList = repository.listDocuments(); galleryDialog = true }) { Text("ملفاتي", fontSize = 11.sp) }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { brushLibraryDialog = true }) { Text("✦ مكتبة الفرش") }
                        TextButton(onClick = { imagePicker.launch(arrayOf("image/*")) }) { Text("＋ صورة") }
                        TextButton(onClick = { layersDialog = true }) { Text("▤ الطبقات") }
                        TextButton(onClick = { pagesDialog = true }) { Text("▧ الصفحات") }
                        TextButton(onClick = { artboardsDialog = true }) { Text("▦ اللوحات") }
                        TextButton(onClick = {
                            runCatching {
                                val uri = SvgExporter.export(context, document)
                                val shareIntent = Intent(Intent.ACTION_SEND).apply { type = "image/svg+xml"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                                context.startActivity(Intent.createChooser(shareIntent, "تصدير SVG"))
                            }.onFailure { Toast.makeText(context, "تعذر تصدير SVG", Toast.LENGTH_SHORT).show() }
                        }) { Text("⇧ SVG") }
                        TextButton(onClick = {
                            runCatching {
                                val uri = PdfExporter.export(context, document)
                                val shareIntent = Intent(Intent.ACTION_SEND).apply { type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                                context.startActivity(Intent.createChooser(shareIntent, "تصدير PDF"))
                            }.onFailure { Toast.makeText(context, "تعذر إنشاء PDF", Toast.LENGTH_SHORT).show() }
                        }) { Text("⇧ PDF") }
                        TextButton(onClick = {
                            runCatching {
                                val uri = PsdExporter.export(context, document)
                                val shareIntent = Intent(Intent.ACTION_SEND).apply { type = "image/vnd.adobe.photoshop"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                                context.startActivity(Intent.createChooser(shareIntent, "تصدير PSD"))
                            }.onFailure { Toast.makeText(context, it.message ?: "تعذر إنشاء PSD", Toast.LENGTH_LONG).show() }
                        }) { Text("⇧ PSD") }
                        TextButton(onClick = {
                            runCatching {
                                val uri = DxfExporter.export(context, document)
                                val shareIntent = Intent(Intent.ACTION_SEND).apply { type = "application/dxf"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                                context.startActivity(Intent.createChooser(shareIntent, "تصدير DXF"))
                            }.onFailure { Toast.makeText(context, "تعذر إنشاء DXF", Toast.LENGTH_SHORT).show() }
                        }) { Text("⇧ DXF") }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 3.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                            Canvas(
                                modifier = Modifier.fillMaxSize()
                                    .clip(RoundedCornerShape(18.dp))
                                    .pointerInteropFilter { event ->
                                        fun pointAt(index: Int): CanvasPoint {
                                            val raw = CanvasPoint(
                                                event.getX(index),
                                                event.getY(index),
                                                CanvasGeometry.normalizedPressure(event.getPressure(index)),
                                                CanvasGeometry.normalizedTilt(event.getAxisValue(MotionEvent.AXIS_TILT, index)),
                                                CanvasGeometry.normalizedOrientation(event.getAxisValue(MotionEvent.AXIS_ORIENTATION, index))
                                            )
                                            return snapped(CanvasGeometry.toWorld(raw, currentZoom, currentPan.x, currentPan.y))
                                        }
                                        fun centroid(): Offset {
                                            var x = 0f
                                            var y = 0f
                                            for (i in 0 until event.pointerCount) {
                                                x += event.getX(i)
                                                y += event.getY(i)
                                            }
                                            return Offset(x / event.pointerCount, y / event.pointerCount)
                                        }
                                        fun span(center: Offset): Float {
                                            if (event.pointerCount < 2) return 0f
                                            var sum = 0f
                                            for (i in 0 until event.pointerCount) {
                                                val dx = event.getX(i) - center.x
                                                val dy = event.getY(i) - center.y
                                                sum += dx * dx + dy * dy
                                            }
                                            return kotlin.math.sqrt(sum / event.pointerCount)
                                        }
                                        fun appendSamples() {
                                            val index = 0
                                            for (h in 0 until event.historySize) {
                                                val sample = CanvasPoint(
                                                    event.getHistoricalX(index, h),
                                                    event.getHistoricalY(index, h),
                                                    CanvasGeometry.normalizedPressure(event.getHistoricalPressure(index, h)),
                                                    CanvasGeometry.normalizedTilt(event.getHistoricalAxisValue(MotionEvent.AXIS_TILT, index, h)),
                                                    CanvasGeometry.normalizedOrientation(event.getHistoricalAxisValue(MotionEvent.AXIS_ORIENTATION, index, h))
                                                )
                                                gesturePoints.add(snapped(CanvasGeometry.toWorld(sample, currentZoom, currentPan.x, currentPan.y)))
                                            }
                                            gesturePoints.add(pointAt(index))
                                        }
                                        when (event.actionMasked) {
                                            MotionEvent.ACTION_DOWN -> {
                                                gesturePoints.clear()
                                                activeTool = currentTool
                                                drawingGesture = activeTool != BrushTool.MOVE && activeTool != BrushTool.SELECT && activeTool != BrushTool.GUIDE
                                                panningGesture = activeTool == BrushTool.MOVE
                                                previousCentroid = Offset(event.x, event.y)
                                                previousSpan = 0f
                                                if (activeTool == BrushTool.SELECT) {
                                                    val hit = pointAt(0)
                                                    canvasSelection = CanvasEditingEngine.toggleSelection(canvasSelection, CanvasSelectionEngine.hitTest(document, hit.x, hit.y, 12f / currentZoom.coerceAtLeast(0.2f)))
                                                } else if (activeTool == BrushTool.GUIDE) {
                                                    val anchor = pointAt(0)
                                                    applyDocument(document.copy(guides = document.guides + ShapeGuide(UUID.randomUUID().toString(), ShapeGuideType.LINE, anchor, guideAngleDegrees)))
                                                } else if (drawingGesture) appendSamples()
                                            }
                                            MotionEvent.ACTION_POINTER_DOWN -> {
                                                drawingGesture = false
                                                panningGesture = false
                                                gesturePoints.clear()
                                                previousCentroid = centroid()
                                                previousSpan = span(previousCentroid)
                                            }
                                            MotionEvent.ACTION_MOVE -> {
                                                if (event.pointerCount >= 2) {
                                                    val center = centroid()
                                                    val nextSpan = span(center)
                                                    if (previousSpan > 0f && nextSpan > 0f) {
                                                        val oldZoom = currentZoom
                                                        val nextZoom = (oldZoom * (nextSpan / previousSpan)).coerceIn(0.2f, 8f)
                                                        val nextPan = Offset(
                                                            center.x - (previousCentroid.x - currentPan.x) * (nextZoom / oldZoom),
                                                            center.y - (previousCentroid.y - currentPan.y) * (nextZoom / oldZoom)
                                                        )
                                                        setViewNow(nextZoom, nextPan)
                                                    }
                                                    previousCentroid = center
                                                    previousSpan = nextSpan
                                                } else if (panningGesture) {
                                                    val position = Offset(event.x, event.y)
                                                    setPanNow(currentPan + (position - previousCentroid))
                                                    previousCentroid = position
                                                } else if (drawingGesture) {
                                                    appendSamples()
                                                }
                                            }
                                            MotionEvent.ACTION_POINTER_UP -> {
                                                previousSpan = 0f
                                                previousCentroid = Offset(event.x, event.y)
                                            }
                                            MotionEvent.ACTION_UP -> {
                                                if (drawingGesture && gesturePoints.isNotEmpty()) {
                                                    val tool = activeTool
                                                    val color = if (tool == BrushTool.ERASER) Color.White.toArgb() else currentColor
                                                    val opacity = when (tool) {
                                                        BrushTool.MARKER -> 0.28f
                                                        BrushTool.PENCIL -> 0.72f
                                                        else -> 1f
                                                    }
                                                    if (tool == BrushTool.MEASURE) {
                                                        val distance = CanvasGeometry.distance(gesturePoints.first(), gesturePoints.last())
                                                        Toast.makeText(context, "القياس: " + CanvasMeasurement.label(gesturePoints.first(), gesturePoints.last(), measurementUnit), Toast.LENGTH_SHORT).show()
                                                    }
                                                    addStrokeNow(
                                                        DrawStroke(
                                                            id = UUID.randomUUID().toString(),
                                                            layerId = currentLayer,
                                                            tool = tool,
                                                            color = color,
                                                            width = if (tool == BrushTool.ERASER) currentWidth * 3f else currentWidth,
                                                            opacity = opacity,
                                                            points = if (tool == BrushTool.LINE || tool == BrushTool.RECTANGLE || tool == BrushTool.ELLIPSE || tool == BrushTool.MEASURE) {
                                                                CanvasGeometry.shapePoints(tool, gesturePoints.first(), gesturePoints.last())
                                                            } else CanvasGeometry.smooth(gesturePoints.toList())
                                                        )
                                                    )
                                                }
                                                gesturePoints.clear()
                                                drawingGesture = false
                                                panningGesture = false
                                                previousSpan = 0f
                                            }
                                            MotionEvent.ACTION_CANCEL -> {
                                                gesturePoints.clear()
                                                drawingGesture = false
                                                panningGesture = false
                                                previousSpan = 0f
                                            }
                                        }
                                        true
                                    }
                            ) {
                                val pageBackground = document.pages.firstOrNull { it.id == document.activePageId }?.backgroundColor ?: 0xFFFCFCFA.toInt()
                                drawRect(Color(if (document.artboards.none { it.visible }) pageBackground else 0xFFFCFCFA.toInt()))
                                document.artboards.filter { it.visible }.forEach { artboard ->
                                    val topLeft = Offset(artboard.x * zoom + pan.x, artboard.y * zoom + pan.y)
                                    val boardSize = Size(artboard.width * zoom, artboard.height * zoom)
                                    drawRect(Color(artboard.backgroundColor), topLeft, boardSize)
                                    drawRect(
                                        color = if (document.activeArtboardId == artboard.id) Color(0xFF1976D2) else Color(0xFF9AA7B5),
                                        topLeft = topLeft,
                                        size = boardSize,
                                        style = Stroke(width = if (document.activeArtboardId == artboard.id) 2.5f else 1f)
                                    )
                                }
                                val spacing = 34f * zoom.coerceIn(0.2f, 8f)
                                if (spacing >= 8f) {
                                    var gx = ((pan.x % spacing) + spacing) % spacing
                                    var gy = ((pan.y % spacing) + spacing) % spacing
                                    while (gx < size.width) {
                                        drawLine(Color(0xFFE8E9E8), Offset(gx, 0f), Offset(gx, size.height), 0.7f)
                                        gx += spacing
                                    }
                                    while (gy < size.height) {
                                        drawLine(Color(0xFFE8E9E8), Offset(0f, gy), Offset(size.width, gy), 0.7f)
                                        gy += spacing
                                    }
                                }

                                document.images.forEach { image ->
                                    val bitmap = imageBitmaps[image.fileName] ?: return@forEach
                                    rotate(
                                        degrees = image.rotationDegrees,
                                        pivot = Offset((image.x + image.width / 2f) * zoom + pan.x, (image.y + image.height / 2f) * zoom + pan.y)
                                    ) {
                                        drawImage(
                                            image = bitmap,
                                            dstOffset = IntOffset((image.x * zoom + pan.x).toInt(), (image.y * zoom + pan.y).toInt()),
                                            dstSize = IntSize((image.width * zoom).toInt().coerceAtLeast(1), (image.height * zoom).toInt().coerceAtLeast(1))
                                        )
                                    }
                                }

                                document.guides.filter { it.enabled }.forEach { guide ->
                                    val angle = Math.toRadians(guide.angleDegrees.toDouble())
                                    val dx = kotlin.math.cos(angle).toFloat()
                                    val dy = kotlin.math.sin(angle).toFloat()
                                    val origin = Offset(guide.origin.x * zoom + pan.x, guide.origin.y * zoom + pan.y)
                                    if (guide.type == ShapeGuideType.LINE || guide.type == ShapeGuideType.PARALLEL || guide.type == ShapeGuideType.PERPENDICULAR) {
                                        val ux = if (guide.type == ShapeGuideType.PERPENDICULAR) -dy else dx
                                        val uy = if (guide.type == ShapeGuideType.PERPENDICULAR) dx else dy
                                        drawLine(Color(0xFF3C8DAD).copy(alpha = 0.65f), origin - Offset(ux, uy) * 5000f, origin + Offset(ux, uy) * 5000f, strokeWidth = 1.2f)
                                    }
                                }
                                val visibleLayers = document.layers.filter { it.visible }.associateBy { it.id }
                                document.strokes.forEach { stroke ->
                                    val layer = visibleLayers[stroke.layerId] ?: return@forEach
                                    if (stroke.points.isEmpty()) return@forEach
                                    val color = Color(stroke.color).copy(
                                        alpha = (stroke.opacity * layer.opacity).coerceIn(0f, 1f)
                                    )
                                    val isShape = stroke.tool == BrushTool.LINE ||
                                        stroke.tool == BrushTool.RECTANGLE ||
                                        stroke.tool == BrushTool.ELLIPSE ||
                                        stroke.tool == BrushTool.MEASURE
                                    if (isShape) {
                                        val mapped = stroke.points.map { Offset(it.x * zoom + pan.x, it.y * zoom + pan.y) }
                                        if (mapped.size == 1) {
                                            drawCircle(color, radius = (stroke.width * zoom / 2f).coerceAtLeast(0.5f), center = mapped.first())
                                        } else {
                                            stroke.points.zipWithNext().forEachIndexed { index, pair ->
                                                val pressure = (CanvasGeometry.pressureTiltWidth(pair.first, 1f) + CanvasGeometry.pressureTiltWidth(pair.second, 1f)) / 2f
                                                drawLine(color, mapped[index], mapped[index + 1],
                                                    strokeWidth = (stroke.width * pressure * zoom).coerceAtLeast(0.5f),
                                                    cap = StrokeCap.Round)
                                            }
                                        }
                                    } else {
                                        preparedBrushDabs[stroke.id].orEmpty().forEach { dab ->
                                            val outline = dab.outline(20)
                                            val nibPath = Path().apply {
                                                if (outline.isNotEmpty()) {
                                                    moveTo(outline.first().x * zoom + pan.x, outline.first().y * zoom + pan.y)
                                                    outline.drop(1).forEach { point ->
                                                        lineTo(point.x * zoom + pan.x, point.y * zoom + pan.y)
                                                    }
                                                    close()
                                                }
                                            }
                                            drawPath(
                                                path = nibPath,
                                                color = Color(stroke.color).copy(alpha = (dab.opacity * layer.opacity).coerceIn(0f, 1f))
                                            )
                                        }
                                    }
                                }
                                CanvasSelectionEngine.bounds(document, canvasSelection)?.let { bounds ->
                                    drawRect(
                                        color = Color(0xFF1976D2),
                                        topLeft = Offset(bounds.left * zoom + pan.x, bounds.top * zoom + pan.y),
                                        size = Size(bounds.width * zoom, bounds.height * zoom),
                                        style = Stroke(width = 2f)
                                    )
                                }
                            }
                            Surface(
                                modifier = Modifier.align(Alignment.TopStart).padding(9.dp),
                                color = Color.White.copy(alpha = 0.86f),
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    " ${((zoom * 100).toInt())}% ",
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                    fontSize = 11.sp,
                                    color = Color.DarkGray
                                )
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(
                                BrushTool.PEN to "قلم",
                                BrushTool.PENCIL to "رصاص",
                                BrushTool.MARKER to "ماركر",
                                BrushTool.ERASER to "ممحاة",
                                BrushTool.MOVE to "تحريك",
                                BrushTool.LINE to "خط",
                                BrushTool.RECTANGLE to "مستطيل",
                                BrushTool.ELLIPSE to "بيضاوي",
                                BrushTool.MEASURE to "قياس",
                                BrushTool.SELECT to "تحديد",
                                BrushTool.GUIDE to "دليل",
                                BrushTool.ROUND_BRUSH to "فرشاة دائرية",
                                BrushTool.CHISEL to "قلم عريض"
                            ).forEach { (tool, label) ->
                                FilterChip(
                                    selected = selectedTool == tool,
                                    onClick = { selectedTool = tool },
                                    label = { Text(label, fontSize = 12.sp) }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(selected = snapEnabled, onClick = { snapEnabled = !snapEnabled }, label = { Text("التقاط ذكي", fontSize = 11.sp) })
                            TextButton(onClick = { guideAngleDegrees = if (guideAngleDegrees == 0f) 90f else 0f }) { Text(if (guideAngleDegrees == 0f) "دليل أفقي" else "دليل رأسي") }
                            TextButton(onClick = { measurementUnit = MeasurementUnit.entries[(measurementUnit.ordinal + 1) % MeasurementUnit.entries.size] }) { Text("وحدة: ${measurementUnit.symbol}") }
                            TextButton(onClick = { applyDocument(document.copy(guides = emptyList())) }) { Text("مسح الأدلة") }
                            TextButton(onClick = { canvasSelection = CanvasEditingEngine.selectAll(document) }) { Text("تحديد الكل") }
                            if (!clipboard.isEmpty) {
                                TextButton(onClick = {
                                    val (next, selection) = CanvasEditingEngine.paste(document, clipboard)
                                    applyDocument(next)
                                    canvasSelection = selection
                                }) { Text("لصق") }
                            }
                            if (canvasSelection.imageIds.size == 1) {
                                TextButton(onClick = {
                                    cropDialogImageId = canvasSelection.imageIds.first()
                                    cropFrame = ImageCropFrame()
                                }) { Text("قص الصورة") }
                            }
                            if (!canvasSelection.isEmpty) {
                                TextButton(onClick = { clipboard = CanvasEditingEngine.copy(document, canvasSelection) }) { Text("نسخ") }
                                TextButton(onClick = {
                                    val (next, copied) = CanvasEditingEngine.cut(document, canvasSelection)
                                    clipboard = copied
                                    applyDocument(next)
                                    canvasSelection = CanvasSelection()
                                }) { Text("قص") }
                                TextButton(onClick = { applyDocument(CanvasSelectionEngine.transform(document, canvasSelection, SelectionTransform(translateX = -10f))) }) { Text("←") }
                                TextButton(onClick = { applyDocument(CanvasSelectionEngine.transform(document, canvasSelection, SelectionTransform(translateX = 10f))) }) { Text("→") }
                                TextButton(onClick = { applyDocument(CanvasSelectionEngine.transform(document, canvasSelection, SelectionTransform(translateY = -10f))) }) { Text("↑") }
                                TextButton(onClick = { applyDocument(CanvasSelectionEngine.transform(document, canvasSelection, SelectionTransform(translateY = 10f))) }) { Text("↓") }
                                TextButton(onClick = { applyDocument(CanvasSelectionEngine.transform(document, canvasSelection, SelectionTransform(scaleX = 1.1f, scaleY = 1.1f))) }) { Text("تكبير") }
                                TextButton(onClick = { applyDocument(CanvasSelectionEngine.transform(document, canvasSelection, SelectionTransform(scaleX = 0.9f, scaleY = 0.9f))) }) { Text("تصغير") }
                                TextButton(onClick = { applyDocument(CanvasSelectionEngine.transform(document, canvasSelection, SelectionTransform(rotationDegrees = 15f))) }) { Text("↻") }
                                TextButton(onClick = {
                                    applyDocument(CanvasEditingEngine.delete(document, canvasSelection))
                                    canvasSelection = CanvasSelection()
                                }) { Text("حذف") }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("اللون", fontSize = 12.sp)
                            Row(
                                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                palette.forEach { argb ->
                                    Box(
                                        modifier = Modifier.size(26.dp)
                                            .clip(CircleShape)
                                            .background(Color(argb))
                                            .then(
                                                if (selectedColor == argb) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                                else Modifier.border(1.dp, Color.LightGray, CircleShape)
                                            )
                                            .clickable { selectedColor = argb }
                                    )
                                }
                            }
                            TextButton(onClick = { customColorHex = CanvasColorEngine.hex(selectedColor); colorDialog = true }) { Text("مخصص") }
                            Text("${brushWidth.toInt()} px", fontSize = 11.sp)
                        }
                        Slider(
                            value = brushWidth,
                            onValueChange = { brushWidth = it },
                            valueRange = 1f..28f,
                            modifier = Modifier.fillMaxWidth().height(25.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { layersDialog = true }) { Text("الطبقات (${document.layers.size})") }
                            TextButton(onClick = { artboardsDialog = true }) { Text("اللوحات (${document.artboards.size})") }
                            TextButton(onClick = { pagesDialog = true }) { Text("الصفحات (${document.pages.size})") }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { zoom = (zoom / 1.2f).coerceIn(0.25f, 5f) }) { Text("−") }
                            Text("${(zoom * 100).toInt()}%", fontSize = 11.sp)
                            TextButton(onClick = { zoom = (zoom * 1.2f).coerceIn(0.25f, 5f) }) { Text("+") }
                            TextButton(onClick = { zoom = 1f; pan = Offset.Zero }) { Text("ملاءمة") }
                        }
                    }
                }
            }
        }
    }

    if (brushLibraryDialog) {
        AlertDialog(
            onDismissRequest = { brushLibraryDialog = false },
            title = {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("مكتبة الفرش", fontWeight = FontWeight.Bold)
                    Text("اختر أداة الرسم المناسبة لأسلوبك", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("الفرش الأساسية", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    listOf(
                        BrushTool.PEN to "قلم حبر",
                        BrushTool.PENCIL to "قلم رصاص",
                        BrushTool.MARKER to "قلم تحديد",
                        BrushTool.ROUND_BRUSH to "فرشاة ناعمة",
                        BrushTool.CHISEL to "قلم عريض",
                        BrushTool.ERASER to "ممحاة"
                    ).chunked(2).forEach { rowTools ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowTools.forEach { (tool, label) ->
                                FilterChip(
                                    modifier = Modifier.weight(1f),
                                    selected = selectedTool == tool,
                                    onClick = { selectedTool = tool; brushLibraryDialog = false },
                                    label = { Text(label, fontSize = 12.sp) }
                                )
                            }
                        }
                    }
                    androidx.compose.material3.HorizontalDivider()
                    Text("أدوات الرسم والتحرير", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    listOf(
                        BrushTool.LINE to "خط مستقيم",
                        BrushTool.RECTANGLE to "مستطيل",
                        BrushTool.ELLIPSE to "دائرة وبيضاوي",
                        BrushTool.SELECT to "تحديد العناصر",
                        BrushTool.MOVE to "تحريك اللوحة",
                        BrushTool.GUIDE to "أدلة هندسية"
                    ).chunked(2).forEach { rowTools ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowTools.forEach { (tool, label) ->
                                FilterChip(
                                    modifier = Modifier.weight(1f),
                                    selected = selectedTool == tool,
                                    onClick = { selectedTool = tool; brushLibraryDialog = false },
                                    label = { Text(label, fontSize = 12.sp) }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { brushLibraryDialog = false }) { Text("تم") } }
        )
    }

    if (layersDialog) {
        AlertDialog(
            onDismissRequest = { layersDialog = false },
            title = { Text("إدارة الطبقات") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    document.layers.forEach { layer ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = activeLayerId == layer.id,
                                onClick = { activeLayerId = layer.id },
                                enabled = layer.visible
                            )
                            Text(layer.name, modifier = Modifier.weight(1f), fontSize = 13.sp, maxLines = 1)
                            Checkbox(
                                checked = layer.locked,
                                onCheckedChange = { locked ->
                                    applyDocument(document.copy(layers = document.layers.map { if (it.id == layer.id) it.copy(locked = locked) else it }))
                                }
                            )
                            TextButton(onClick = { renameLayerId = layer.id; renameLayerName = layer.name }) { Text("اسم") }
                            val layerIndex = document.layers.indexOfFirst { it.id == layer.id }
                            TextButton(onClick = {
                                if (layerIndex > 0) {
                                    val reordered = document.layers.toMutableList()
                                    val item = reordered.removeAt(layerIndex)
                                    reordered.add(layerIndex - 1, item)
                                    applyDocument(document.copy(layers = reordered))
                                }
                            }, enabled = layerIndex > 0) { Text("↑") }
                            TextButton(onClick = {
                                if (layerIndex in 0 until document.layers.lastIndex) {
                                    val reordered = document.layers.toMutableList()
                                    val item = reordered.removeAt(layerIndex)
                                    reordered.add(layerIndex + 1, item)
                                    applyDocument(document.copy(layers = reordered))
                                }
                            }, enabled = layerIndex in 0 until document.layers.lastIndex) { Text("↓") }
                            TextButton(onClick = { applyDocument(document.copy(layers = document.layers.map { if (it.id == layer.id) it.copy(opacity = (it.opacity - 0.1f).coerceAtLeast(0f)) else it })) }) { Text("−") }
                            TextButton(onClick = { applyDocument(document.copy(layers = document.layers.map { if (it.id == layer.id) it.copy(opacity = (it.opacity + 0.1f).coerceAtMost(1f)) else it })) }) { Text("+") }
                            Checkbox(
                                checked = layer.visible,
                                onCheckedChange = { visible ->
                                    val otherVisibleLayer = document.layers.firstOrNull {
                                        it.id != layer.id && it.visible
                                    }
                                    if (!visible && layer.id == activeLayerId && otherVisibleLayer == null) {
                                        Toast.makeText(context, "يجب إبقاء طبقة واحدة ظاهرة على الأقل", Toast.LENGTH_SHORT).show()
                                    } else {
                                        applyDocument(document.copy(layers = document.layers.map {
                                            if (it.id == layer.id) it.copy(visible = visible) else it
                                        }))
                                        if (!visible && layer.id == activeLayerId) {
                                            activeLayerId = otherVisibleLayer!!.id
                                        }
                                    }
                                }
                            )
                            TextButton(
                                onClick = {
                                    if (document.layers.size > 1) {
                                        val nextLayers = document.layers.filterNot { it.id == layer.id }
                                        applyDocument(document.copy(
                                            layers = nextLayers,
                                            strokes = document.strokes.filterNot { it.layerId == layer.id }
                                        ))
                                        if (activeLayerId == layer.id) {
                                            activeLayerId = nextLayers.firstOrNull { it.visible }?.id ?: nextLayers.first().id
                                        }
                                    }
                                },
                                enabled = document.layers.size > 1 &&
                                    (layer.id != activeLayerId || document.layers.any { it.id != layer.id && it.visible })
                            ) { Text("حذف") }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val newLayer = DrawingLayer(UUID.randomUUID().toString(), "طبقة ${document.layers.size + 1}")
                    applyDocument(document.copy(layers = document.layers + newLayer))
                    activeLayerId = newLayer.id
                }) { Text("إضافة طبقة") }
            },
            dismissButton = { TextButton(onClick = { layersDialog = false }) { Text("إغلاق") } }
        )
    }

    if (cropDialogImageId != null) {
        val sourceImage = document.images.firstOrNull { it.id == cropDialogImageId }
        val sourceBitmap = sourceImage?.let { imageBitmaps[it.fileName] }
        AlertDialog(
            onDismissRequest = { cropDialogImageId = null },
            title = { Text("قص الصورة") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (sourceBitmap != null) {
                        CropPreview(bitmap = sourceBitmap, frame = cropFrame, onFrameChange = { cropFrame = it })
                        Text("اسحب المقابض الزرقاء لتحديد الجزء الذي تريد الاحتفاظ به.")
                    } else {
                        Text("جارٍ تجهيز الصورة أو تعذر تحميلها.")
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = sourceImage != null && sourceBitmap != null, onClick = {
                    val original = sourceImage
                    if (original != null) {
                        scope.launch {
                            val current = document
                            val cropped = withContext(Dispatchers.IO) {
                                repository.cropImage(original, cropFrame)
                            }
                            if (cropped != null) {
                                applyDocument(current.copy(images = current.images.map {
                                    if (it.id == original.id) cropped else it
                                }))
                                canvasSelection = CanvasSelection(imageIds = setOf(original.id))
                            } else {
                                Toast.makeText(context, "تعذر قص الصورة", Toast.LENGTH_SHORT).show()
                            }
                            cropDialogImageId = null
                        }
                    }
                }) { Text("تطبيق القص") }
            },
            dismissButton = { TextButton(onClick = { cropDialogImageId = null }) { Text("إلغاء") } }
        )
    }

    if (pagesDialog) {
        AlertDialog(
            onDismissRequest = { pagesDialog = false },
            title = { Text("إدارة الصفحات") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    document.pages.forEach { page ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = document.activePageId == page.id,
                                onClick = {
                                    val next = CanvasPageEngine.activate(document, page.id)
                                    applyDocument(next)
                                    activeLayerId = next.layers.firstOrNull()?.id ?: "layer-base"
                                    canvasSelection = CanvasSelection()
                                }
                            )
                            Text(page.name, modifier = Modifier.weight(1f), maxLines = 1)
                            TextButton(onClick = {
                                val next = CanvasPageEngine.duplicate(document, page.id)
                                applyDocument(next)
                                activeLayerId = next.layers.firstOrNull()?.id ?: "layer-base"
                            }) { Text("نسخ") }
                        }
                    }
                    Text("كل صفحة تحتفظ بطبقاتها ورسوماتها وصورها وأدلتها.")
                }
            },
            confirmButton = {
                Button(onClick = {
                    val next = CanvasPageEngine.create(document, "الصفحة ${document.pages.size + 1}")
                    applyDocument(next)
                    activeLayerId = next.layers.firstOrNull()?.id ?: "layer-base"
                    canvasSelection = CanvasSelection()
                }) { Text("إضافة صفحة") }
            },
            dismissButton = { TextButton(onClick = { pagesDialog = false }) { Text("إغلاق") } }
        )
    }

    if (colorDialog) {
        AlertDialog(
            onDismissRequest = { colorDialog = false },
            title = { Text("لون مخصص وخلفية الصفحة") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = customColorHex,
                    onValueChange = { customColorHex = it.take(9) },
                    singleLine = true,
                    label = { Text("HEX مثل #FF336699") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val parsed = CanvasColorEngine.parseHex(customColorHex)
                    if (parsed != null) {
                        selectedColor = parsed
                        applyDocument(CanvasColorEngine.applyBackground(document, parsed))
                        colorDialog = false
                    } else Toast.makeText(context, "صيغة اللون غير صحيحة", Toast.LENGTH_SHORT).show()
                }) { Text("تطبيق اللون والخلفية") }
            },
            dismissButton = { TextButton(onClick = { colorDialog = false }) { Text("إلغاء") } }
        )
    }

    if (artboardsDialog) {
        AlertDialog(
            onDismissRequest = { artboardsDialog = false },
            title = { Text("إدارة اللوحات الفنية") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (document.artboards.isEmpty()) Text("لا توجد لوحات فنية بعد")
                    document.artboards.forEach { artboard ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = document.activeArtboardId == artboard.id,
                                onClick = { applyDocument(document.copy(activeArtboardId = artboard.id)) }
                            )
                            Text(artboard.name, modifier = Modifier.weight(1f), maxLines = 1)
                            Checkbox(
                                checked = artboard.visible,
                                onCheckedChange = { visible -> applyDocument(document.copy(artboards = document.artboards.map { if (it.id == artboard.id) it.copy(visible = visible) else it })) }
                            )
                            TextButton(onClick = {
                                val remaining = document.artboards.filterNot { it.id == artboard.id }
                                val active = if (document.activeArtboardId == artboard.id) remaining.firstOrNull()?.id else document.activeArtboardId
                                applyDocument(document.copy(artboards = remaining, activeArtboardId = active))
                            }) { Text("حذف") }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val artboard = DrawingArtboard(UUID.randomUUID().toString(), "لوحة فنية ${document.artboards.size + 1}", 0f, 0f, 1000f, 1400f)
                    applyDocument(document.copy(artboards = document.artboards + artboard, activeArtboardId = artboard.id))
                }) { Text("إضافة لوحة") }
            },
            dismissButton = { TextButton(onClick = { artboardsDialog = false }) { Text("إغلاق") } }
        )
    }

    if (renameLayerId != null) {
        AlertDialog(
            onDismissRequest = { renameLayerId = null },
            title = { Text("إعادة تسمية الطبقة") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = renameLayerName,
                    onValueChange = { renameLayerName = it.take(80) },
                    singleLine = true,
                    label = { Text("اسم الطبقة") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = renameLayerId
                    if (id != null && renameLayerName.isNotBlank()) {
                        applyDocument(document.copy(layers = document.layers.map { if (it.id == id) it.copy(name = renameLayerName.trim()) else it }))
                    }
                    renameLayerId = null
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { renameLayerId = null }) { Text("إلغاء") } }
        )
    }

    if (galleryDialog) {
        AlertDialog(
            onDismissRequest = { galleryDialog = false },
            title = { Text("معرض المستندات") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(onClick = {
                        document = CanvasPageEngine.normalized(DrawingDocument.empty())
                        activeLayerId = document.layers.first().id
                        undoStack.clear()
                        redoStack.clear()
                        galleryDialog = false
                    }, modifier = Modifier.fillMaxWidth()) { Text("إنشاء لوحة جديدة") }
                    if (documentList.isEmpty()) {
                        Text("لا توجد لوحات محفوظة بعد")
                    } else {
                        documentList.forEach { item ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            val opened = withContext(Dispatchers.IO) { repository.load(item.id) }
                                            if (opened != null) {
                                                document = opened
                                                activeLayerId = opened.layers.firstOrNull()?.id ?: "layer-base"
                                                undoStack.clear()
                                                redoStack.clear()
                                            }
                                            galleryDialog = false
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Text(item.title, maxLines = 1) }
                                TextButton(onClick = {
                                    scope.launch {
                                        withContext(Dispatchers.IO) { repository.delete(item.id) }
                                        documentList = withContext(Dispatchers.IO) { repository.listDocuments() }
                                        if (item.id == document.id) {
                                            document = DrawingDocument.empty()
                                            activeLayerId = document.layers.first().id
                                            undoStack.clear()
                                            redoStack.clear()
                                        }
                                    }
                                }) { Text("حذف") }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { galleryDialog = false }) { Text("إغلاق") } }
        )
    }
}

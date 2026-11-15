package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.model.AnnotationStroke
import com.example.model.InteractionTool
import com.example.model.LaserPointerState
import com.example.model.NormalizedPoint
import com.example.model.PdfSlideLink
import com.example.ui.theme.SkyCyan
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun StageSlideCanvas(
    slideBitmap: Bitmap?,
    strokes: List<AnnotationStroke>,
    activeStroke: AnnotationStroke?,
    laserState: LaserPointerState,
    activeTool: InteractionTool,
    links: List<PdfSlideLink> = emptyList(),
    showLinkHints: Boolean = true,
    isEraserActive: Boolean = false,
    eraserPosition: NormalizedPoint? = null,
    isBlackoutOverlay: Boolean = false,
    interactive: Boolean = true,
    onNextSlide: () -> Unit = {},
    onPreviousSlide: () -> Unit = {},
    onLinkClick: (PdfSlideLink) -> Unit = {},
    onLaserMove: (NormalizedPoint) -> Unit = {},
    onLaserEnd: () -> Unit = {},
    onStrokeStart: (NormalizedPoint) -> Unit = {},
    onStrokeMove: (NormalizedPoint) -> Unit = {},
    onStrokeEnd: () -> Unit = {},
    onEraseStart: (NormalizedPoint) -> Unit = {},
    onEraseMove: (NormalizedPoint) -> Unit = {},
    onEraseEnd: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val latestTool = rememberUpdatedState(activeTool)
    val latestEraser = rememberUpdatedState(isEraserActive)
    val latestBitmap = rememberUpdatedState(slideBitmap)
    val latestLinks = rememberUpdatedState(links)
    var tappedLinkId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(tappedLinkId) {
        if (tappedLinkId != null) {
            delay(260L)
            tappedLinkId = null
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .background(Color.Black)
            .testTag("stage_slide_canvas"),
        contentAlignment = Alignment.Center
    ) {
        val boxW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val boxH = constraints.maxHeight.toFloat().coerceAtLeast(1f)

        val pointerModifier = if (interactive) {
            Modifier.pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val tool = latestTool.value
                    val bmp = latestBitmap.value
                    val slideRect = computeSlideRect(boxW, boxH, bmp)

                    when (tool) {
                        InteractionTool.LASER -> {
                            down.consume()
                            toNormalizedPoint(down.position, slideRect)?.let(onLaserMove)
                            var pointerId = down.id
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == pointerId }
                                    ?: event.changes.firstOrNull()
                                if (change == null || !change.pressed) {
                                    onLaserEnd()
                                    break
                                }
                                change.consume()
                                pointerId = change.id
                                toNormalizedPoint(change.position, slideRect)?.let(onLaserMove)
                            }
                        }

                        InteractionTool.PEN, InteractionTool.HIGHLIGHTER -> {
                            down.consume()
                            val erasing = latestEraser.value
                            val startPt = toNormalizedPoint(down.position, slideRect)
                            var started = false
                            if (startPt != null) {
                                if (erasing) onEraseStart(startPt) else onStrokeStart(startPt)
                                started = true
                            }
                            var pointerId = down.id
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == pointerId }
                                    ?: event.changes.firstOrNull()
                                if (change == null || !change.pressed) {
                                    if (started) {
                                        if (erasing) onEraseEnd() else onStrokeEnd()
                                    }
                                    break
                                }
                                change.consume()
                                pointerId = change.id
                                toNormalizedPoint(change.position, slideRect)?.let { pt ->
                                    if (!started) {
                                        if (erasing) onEraseStart(pt) else onStrokeStart(pt)
                                        started = true
                                    } else {
                                        if (erasing) onEraseMove(pt) else onStrokeMove(pt)
                                    }
                                }
                            }
                        }

                        InteractionTool.NAVIGATE -> {
                            val startPos = down.position
                            var lastPos = startPos
                            var pointerId = down.id
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == pointerId }
                                    ?: event.changes.firstOrNull()
                                if (change == null || !change.pressed) {
                                    val dx = lastPos.x - startPos.x
                                    val dy = lastPos.y - startPos.y
                                    if (abs(dx) > 64f && abs(dx) > abs(dy) * 1.2f) {
                                        if (dx < 0f) onNextSlide() else onPreviousSlide()
                                    } else if (abs(dx) < 20f && abs(dy) < 20f) {
                                        val tapNorm = toNormalizedPoint(startPos, slideRect)
                                        val hitLink = if (tapNorm != null) {
                                            latestLinks.value.firstOrNull { link ->
                                                link.bounds.any { it.contains(tapNorm.x, tapNorm.y, slop = 0.018f) }
                                            }
                                        } else null

                                        if (hitLink != null) {
                                            tappedLinkId = hitLink.id
                                            onLinkClick(hitLink)
                                        } else if (startPos.x < boxW * 0.22f) {
                                            onPreviousSlide()
                                        } else if (startPos.x > boxW * 0.78f) {
                                            onNextSlide()
                                        }
                                    }
                                    break
                                }
                                pointerId = change.id
                                lastPos = change.position
                            }
                        }
                    }
                }
            }
        } else {
            Modifier
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(pointerModifier)
        ) {
            val allStrokes = if (activeStroke != null) strokes + activeStroke else strokes

            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(Color.Black)
                val bmp = slideBitmap
                if (bmp == null || bmp.isRecycled) return@Canvas

                val slideRect = computeSlideRect(size.width, size.height, bmp)
                if (!isBlackoutOverlay) {
                    drawImage(
                        image = bmp.asImageBitmap(),
                        dstOffset = IntOffset(slideRect.left.roundToInt(), slideRect.top.roundToInt()),
                        dstSize = IntSize(slideRect.width.roundToInt().coerceAtLeast(1), slideRect.height.roundToInt().coerceAtLeast(1))
                    )

                    // Render subtle device-only link hints when Navigate mode is active
                    if (interactive && activeTool == InteractionTool.NAVIGATE && (showLinkHints || tappedLinkId != null)) {
                        for (link in links) {
                            val isFlashing = link.id == tappedLinkId
                            if (!showLinkHints && !isFlashing) continue
                            val fillColor = if (isFlashing) SkyCyan.copy(alpha = 0.34f) else SkyCyan.copy(alpha = 0.09f)
                            val borderColor = if (isFlashing) SkyCyan else SkyCyan.copy(alpha = 0.42f)
                            for (b in link.bounds) {
                                val rectLeft = slideRect.left + b.left * slideRect.width
                                val rectTop = slideRect.top + b.top * slideRect.height
                                val rectW = ((b.right - b.left) * slideRect.width).coerceAtLeast(4f)
                                val rectH = ((b.bottom - b.top) * slideRect.height).coerceAtLeast(4f)
                                drawRoundRect(
                                    color = fillColor,
                                    topLeft = Offset(rectLeft, rectTop),
                                    size = Size(rectW, rectH),
                                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                                )
                                drawRoundRect(
                                    color = borderColor,
                                    topLeft = Offset(rectLeft, rectTop),
                                    size = Size(rectW, rectH),
                                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                                    style = Stroke(width = if (isFlashing) 2.dp.toPx() else 1.dp.toPx())
                                )
                            }
                        }
                    }

                    // Render synchronized annotations
                    for (stroke in allStrokes) {
                        val pts = stroke.points
                        if (pts.isEmpty()) continue
                        val rawColor = Color(stroke.colorHex)
                        val strokeColor = if (stroke.isHighlighter) rawColor.copy(alpha = 0.38f) else rawColor
                        val strokeWidth = (stroke.strokeWidthRatio * slideRect.width).coerceAtLeast(3f)

                        if (pts.size == 1) {
                            drawCircle(
                                color = strokeColor,
                                radius = strokeWidth / 2f,
                                center = Offset(
                                    slideRect.left + pts[0].x * slideRect.width,
                                    slideRect.top + pts[0].y * slideRect.height
                                )
                            )
                        } else {
                            val path = Path().apply {
                                val p0x = slideRect.left + pts[0].x * slideRect.width
                                val p0y = slideRect.top + pts[0].y * slideRect.height
                                moveTo(p0x, p0y)
                                for (i in 1 until pts.size) {
                                    val prevX = slideRect.left + pts[i - 1].x * slideRect.width
                                    val prevY = slideRect.top + pts[i - 1].y * slideRect.height
                                    val curX = slideRect.left + pts[i].x * slideRect.width
                                    val curY = slideRect.top + pts[i].y * slideRect.height
                                    quadraticTo(prevX, prevY, (prevX + curX) / 2f, (prevY + curY) / 2f)
                                }
                                lineTo(
                                    slideRect.left + pts.last().x * slideRect.width,
                                    slideRect.top + pts.last().y * slideRect.height
                                )
                            }
                            drawPath(
                                path = path,
                                color = strokeColor,
                                style = Stroke(
                                    width = strokeWidth,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }

                    // Render laser pointer + smooth trail
                    if (laserState.isActive && laserState.position != null) {
                        val laserColor = Color(laserState.color.colorHex)
                        val trail = laserState.trail
                        if (trail.size >= 2) {
                            for (i in 1 until trail.size) {
                                val frac = i.toFloat() / trail.size.toFloat()
                                val p1 = Offset(
                                    slideRect.left + trail[i - 1].x * slideRect.width,
                                    slideRect.top + trail[i - 1].y * slideRect.height
                                )
                                val p2 = Offset(
                                    slideRect.left + trail[i].x * slideRect.width,
                                    slideRect.top + trail[i].y * slideRect.height
                                )
                                drawLine(
                                    color = laserColor.copy(alpha = frac * 0.55f),
                                    start = p1,
                                    end = p2,
                                    strokeWidth = (slideRect.width * 0.008f) * frac + 2f,
                                    cap = StrokeCap.Round
                                )
                            }
                        }

                        val center = Offset(
                            slideRect.left + laserState.position.x * slideRect.width,
                            slideRect.top + laserState.position.y * slideRect.height
                        )
                        val glowR = (slideRect.width * 0.026f).coerceIn(14f, 44f)
                        val coreR = (slideRect.width * 0.008f).coerceIn(5f, 14f)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    laserColor.copy(alpha = 0.85f),
                                    laserColor.copy(alpha = 0.35f),
                                    Color.Transparent
                                ),
                                center = center,
                                radius = glowR
                            ),
                            radius = glowR,
                            center = center
                        )
                        drawCircle(
                            color = laserColor,
                            radius = coreR,
                            center = center
                        )
                        drawCircle(
                            color = Color.White,
                            radius = coreR * 0.45f,
                            center = center
                        )
                    }

                    // Render subtle device-only eraser reticle while dragging eraser
                    if (interactive && isEraserActive && eraserPosition != null) {
                        val center = Offset(
                            slideRect.left + eraserPosition.x * slideRect.width,
                            slideRect.top + eraserPosition.y * slideRect.height
                        )
                        val r = (slideRect.width * 0.032f).coerceIn(14f, 44f)
                        drawCircle(
                            color = Color.White.copy(alpha = 0.20f),
                            radius = r,
                            center = center
                        )
                        drawCircle(
                            color = SkyCyan,
                            radius = r,
                            center = center,
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }
            }

            if (isBlackoutOverlay) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "BLACKOUT ACTIVE",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}

private fun computeSlideRect(containerW: Float, containerH: Float, bmp: Bitmap?): Rect {
    if (bmp == null || bmp.width <= 0 || bmp.height <= 0) {
        return Rect(0f, 0f, containerW, containerH)
    }
    val bmpW = bmp.width.toFloat()
    val bmpH = bmp.height.toFloat()
    val scale = min(containerW / bmpW, containerH / bmpH)
    val drawnW = bmpW * scale
    val drawnH = bmpH * scale
    val left = (containerW - drawnW) / 2f
    val top = (containerH - drawnH) / 2f
    return Rect(left, top, left + drawnW, top + drawnH)
}

private fun toNormalizedPoint(offset: Offset, slideRect: Rect): NormalizedPoint? {
    if (slideRect.width <= 1f || slideRect.height <= 1f) return null
    val nx = ((offset.x - slideRect.left) / slideRect.width).coerceIn(0f, 1f)
    val ny = ((offset.y - slideRect.top) / slideRect.height).coerceIn(0f, 1f)
    return NormalizedPoint(nx, ny)
}

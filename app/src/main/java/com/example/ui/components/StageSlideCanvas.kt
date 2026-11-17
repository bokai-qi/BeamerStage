package com.example.ui.components

import android.graphics.Bitmap
import android.os.SystemClock
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.model.AnnotationStroke
import com.example.model.ChalkboardState
import com.example.model.InteractionTool
import com.example.model.LaserPointerState
import com.example.model.NormalizedPoint
import com.example.model.PdfSlideLink
import com.example.model.SlideViewportState
import com.example.model.SpotlightState
import com.example.ui.theme.SkyCyan
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

@Composable
fun StageSlideCanvas(
    slideBitmap: Bitmap?,
    strokes: List<AnnotationStroke>,
    activeStroke: AnnotationStroke?,
    laserState: LaserPointerState,
    activeTool: InteractionTool,
    spotlightState: SpotlightState = SpotlightState(),
    viewportState: SlideViewportState = SlideViewportState(),
    chalkboardState: ChalkboardState = ChalkboardState(),
    links: List<PdfSlideLink> = emptyList(),
    showLinkHints: Boolean = true,
    isEraserActive: Boolean = false,
    eraserPosition: NormalizedPoint? = null,
    isBlackoutOverlay: Boolean = false,
    backdropColor: Color = Color.Black,
    interactive: Boolean = true,
    onNextSlide: () -> Unit = {},
    onPreviousSlide: () -> Unit = {},
    onLinkClick: (PdfSlideLink) -> Unit = {},
    onLaserMove: (NormalizedPoint) -> Unit = {},
    onLaserEnd: () -> Unit = {},
    onSpotlightMove: (NormalizedPoint) -> Unit = {},
    onSpotlightEnd: () -> Unit = {},
    onStrokeStart: (NormalizedPoint) -> Unit = {},
    onStrokeMove: (NormalizedPoint) -> Unit = {},
    onSnapStrokeToLine: () -> Unit = {},
    onCancelActiveStroke: () -> Unit = {},
    onStrokeEnd: () -> Unit = {},
    onEraseStart: (NormalizedPoint) -> Unit = {},
    onEraseMove: (NormalizedPoint) -> Unit = {},
    onEraseEnd: () -> Unit = {},
    onViewportChange: (scale: Float, panX: Float, panY: Float) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val latestTool = rememberUpdatedState(activeTool)
    val latestEraser = rememberUpdatedState(isEraserActive)
    val latestBitmap = rememberUpdatedState(slideBitmap)
    val latestLinks = rememberUpdatedState(links)
    val latestViewport = rememberUpdatedState(viewportState)
    val latestChalkboard = rememberUpdatedState(chalkboardState)
    var tappedLinkId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(tappedLinkId) {
        if (tappedLinkId != null) {
            delay(260L)
            tappedLinkId = null
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .background(backdropColor)
            .clipToBounds()
            .testTag("stage_slide_canvas"),
        contentAlignment = Alignment.Center
    ) {
        val boxW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val boxH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val latestBoxW = rememberUpdatedState(boxW)
        val latestBoxH = rememberUpdatedState(boxH)

        val pointerModifier = if (interactive) {
            Modifier.pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val tool = latestTool.value
                    val curBoxW = latestBoxW.value
                    val curBoxH = latestBoxH.value

                    fun currentRects(): Pair<Rect, Rect> {
                        val base = computeBaseRect(
                            containerW = curBoxW,
                            containerH = curBoxH,
                            bmp = latestBitmap.value,
                            isChalkboard = latestChalkboard.value.isActive
                        )
                        val content = computeZoomedRect(
                            containerW = curBoxW,
                            containerH = curBoxH,
                            baseRect = base,
                            viewport = latestViewport.value,
                            isChalkboard = latestChalkboard.value.isActive
                        )
                        return base to content
                    }

                    suspend fun AwaitPointerEventScope.runTwoFingerPinchLoop(
                        initialPressed: List<PointerInputChange> = emptyList()
                    ) {
                        var prevCentroid: Offset? = if (initialPressed.size >= 2) {
                            computeCentroid(initialPressed)
                        } else null
                        var prevSpan: Float? = if (initialPressed.size >= 2 && prevCentroid != null) {
                            computeAverageSpan(initialPressed, prevCentroid!!)
                        } else null

                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            event.changes.forEach { it.consume() }

                            if (pressed.size >= 2) {
                                val centroid = computeCentroid(pressed)
                                val span = computeAverageSpan(pressed, centroid)
                                val prevC = prevCentroid
                                val prevS = prevSpan
                                if (prevC != null && prevS != null && prevS > 10f && span > 10f) {
                                    val zoomDelta = (span / prevS).coerceIn(0.70f, 1.45f)
                                    val base = computeBaseRect(
                                        containerW = curBoxW,
                                        containerH = curBoxH,
                                        bmp = latestBitmap.value,
                                        isChalkboard = latestChalkboard.value.isActive
                                    )
                                    val vp = latestViewport.value
                                    val oldScale = vp.scale.coerceIn(1f, 5f)
                                    val newScale = (oldScale * zoomDelta).coerceIn(1f, 5f)

                                    if (newScale <= 1.01f) {
                                        onViewportChange(1f, 0f, 0f)
                                    } else {
                                        val oldDrawnW = (base.width * oldScale).coerceAtLeast(1f)
                                        val oldDrawnH = (base.height * oldScale).coerceAtLeast(1f)
                                        val newDrawnW = (base.width * newScale).coerceAtLeast(1f)
                                        val newDrawnH = (base.height * newScale).coerceAtLeast(1f)

                                        // Anchor zoom around the exact pinch focal point under the user's fingers
                                        val rawPanX = vp.panX +
                                            (centroid.x - curBoxW / 2f) / newDrawnW -
                                            (prevC.x - curBoxW / 2f) / oldDrawnW
                                        val rawPanY = vp.panY +
                                            (centroid.y - curBoxH / 2f) / newDrawnH -
                                            (prevC.y - curBoxH / 2f) / oldDrawnH

                                        val clampedPanX = clampViewportPan(rawPanX, newScale, newDrawnW, curBoxW)
                                        val clampedPanY = clampViewportPan(rawPanY, newScale, newDrawnH, curBoxH)
                                        onViewportChange(newScale, clampedPanX, clampedPanY)
                                    }
                                }
                                prevCentroid = centroid
                                prevSpan = span
                            } else if (pressed.size == 1 && latestViewport.value.isZoomed) {
                                // Smooth continuation pan if 1 finger lifts first at the end of a pinch
                                val singlePos = pressed[0].position
                                val prevC = prevCentroid
                                if (prevC != null && prevSpan == null) {
                                    val base = computeBaseRect(
                                        containerW = curBoxW,
                                        containerH = curBoxH,
                                        bmp = latestBitmap.value,
                                        isChalkboard = latestChalkboard.value.isActive
                                    )
                                    val vp = latestViewport.value
                                    val drawnW = (base.width * vp.scale).coerceAtLeast(1f)
                                    val drawnH = (base.height * vp.scale).coerceAtLeast(1f)
                                    val rawPanX = vp.panX + (singlePos.x - prevC.x) / drawnW
                                    val rawPanY = vp.panY + (singlePos.y - prevC.y) / drawnH
                                    onViewportChange(
                                        vp.scale,
                                        clampViewportPan(rawPanX, vp.scale, drawnW, curBoxW),
                                        clampViewportPan(rawPanY, vp.scale, drawnH, curBoxH)
                                    )
                                }
                                prevCentroid = singlePos
                                prevSpan = null
                            } else {
                                prevCentroid = null
                                prevSpan = null
                            }
                        }
                    }

                    // If a second finger is already down on the initial event, go straight into pinch-zoom
                    val initialPressed = currentEvent.changes.filter { it.pressed }
                    if (initialPressed.size >= 2 && !latestChalkboard.value.isActive) {
                        initialPressed.forEach { it.consume() }
                        runTwoFingerPinchLoop(initialPressed)
                        return@awaitEachGesture
                    }

                    when (tool) {
                        InteractionTool.LASER -> {
                            down.consume()
                            val (_, initRect) = currentRects()
                            toNormalizedPoint(down.position, initRect)?.let(onLaserMove)
                            var pointerId = down.id
                            while (true) {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.size >= 2 && !latestChalkboard.value.isActive) {
                                    onLaserEnd()
                                    runTwoFingerPinchLoop(pressed)
                                    break
                                }
                                val change = event.changes.firstOrNull { it.id == pointerId }
                                    ?: event.changes.firstOrNull()
                                if (change == null || !change.pressed) {
                                    onLaserEnd()
                                    break
                                }
                                change.consume()
                                pointerId = change.id
                                val (_, rect) = currentRects()
                                toNormalizedPoint(change.position, rect)?.let(onLaserMove)
                            }
                        }

                        InteractionTool.SPOTLIGHT -> {
                            down.consume()
                            val (_, initRect) = currentRects()
                            toNormalizedPoint(down.position, initRect)?.let(onSpotlightMove)
                            var pointerId = down.id
                            while (true) {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.size >= 2 && !latestChalkboard.value.isActive) {
                                    onSpotlightEnd()
                                    runTwoFingerPinchLoop(pressed)
                                    break
                                }
                                val change = event.changes.firstOrNull { it.id == pointerId }
                                    ?: event.changes.firstOrNull()
                                if (change == null || !change.pressed) {
                                    onSpotlightEnd()
                                    break
                                }
                                change.consume()
                                pointerId = change.id
                                val (_, rect) = currentRects()
                                toNormalizedPoint(change.position, rect)?.let(onSpotlightMove)
                            }
                        }

                        InteractionTool.PEN, InteractionTool.HIGHLIGHTER -> {
                            down.consume()
                            val erasing = latestEraser.value
                            val (_, initRect) = currentRects()
                            val startPt = toNormalizedPoint(down.position, initRect)
                            var started = false
                            var sampleCount = 0
                            if (startPt != null) {
                                if (erasing) onEraseStart(startPt) else onStrokeStart(startPt)
                                started = true
                                sampleCount = 1
                            }

                            var pointerId = down.id
                            val originPos = down.position
                            var anchorPos = down.position
                            var anchorTimeMs = SystemClock.uptimeMillis()
                            var hasMovedForLineSnap = false
                            var snappedToLine = false
                            val moveThresholdPx = 28f
                            val jitterRadiusPx = 9f

                            while (true) {
                                val shouldWatchHoldSnap = !erasing && started && hasMovedForLineSnap && !snappedToLine
                                val waitTimeoutMs = if (shouldWatchHoldSnap) {
                                    (380L - (SystemClock.uptimeMillis() - anchorTimeMs)).coerceAtLeast(16L)
                                } else {
                                    null
                                }

                                val event = if (waitTimeoutMs != null) {
                                    withTimeoutOrNull(waitTimeoutMs) { awaitPointerEvent() }
                                } else {
                                    awaitPointerEvent()
                                }

                                if (event == null) {
                                    if (!erasing && started && hasMovedForLineSnap && !snappedToLine) {
                                        snappedToLine = true
                                        onSnapStrokeToLine()
                                    }
                                    continue
                                }

                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.size >= 2 && sampleCount <= 6 && !latestChalkboard.value.isActive) {
                                    if (started) {
                                        if (erasing) onEraseEnd() else onCancelActiveStroke()
                                    }
                                    runTwoFingerPinchLoop(pressed)
                                    break
                                }

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
                                sampleCount++

                                val nowMs = SystemClock.uptimeMillis()
                                if (hypot(change.position.x - originPos.x, change.position.y - originPos.y) >= moveThresholdPx) {
                                    hasMovedForLineSnap = true
                                }
                                if (hypot(change.position.x - anchorPos.x, change.position.y - anchorPos.y) > jitterRadiusPx) {
                                    anchorPos = change.position
                                    anchorTimeMs = nowMs
                                } else if (!erasing && started && hasMovedForLineSnap && !snappedToLine && (nowMs - anchorTimeMs >= 380L)) {
                                    snappedToLine = true
                                    onSnapStrokeToLine()
                                }

                                val (_, rect) = currentRects()
                                toNormalizedPoint(change.position, rect)?.let { pt ->
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
                            var pannedWhileZoomed = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.size >= 2 && !latestChalkboard.value.isActive) {
                                    runTwoFingerPinchLoop(pressed)
                                    break
                                }

                                val change = event.changes.firstOrNull { it.id == pointerId }
                                    ?: event.changes.firstOrNull()
                                if (change == null || !change.pressed) {
                                    val dx = lastPos.x - startPos.x
                                    val dy = lastPos.y - startPos.y
                                    val isZoomed = latestViewport.value.isZoomed && !latestChalkboard.value.isActive

                                    if (!isZoomed && !latestChalkboard.value.isActive && abs(dx) > 64f && abs(dx) > abs(dy) * 1.2f) {
                                        if (dx < 0f) onNextSlide() else onPreviousSlide()
                                    } else if (!pannedWhileZoomed && abs(dx) < 20f && abs(dy) < 20f) {
                                        val (_, contentRect) = currentRects()
                                        val tapNorm = toNormalizedPoint(startPos, contentRect)
                                        val hitLink = if (!latestChalkboard.value.isActive && tapNorm != null) {
                                            latestLinks.value.firstOrNull { link ->
                                                link.bounds.any { it.contains(tapNorm.x, tapNorm.y, slop = 0.018f) }
                                            }
                                        } else null

                                        if (hitLink != null) {
                                            tappedLinkId = hitLink.id
                                            onLinkClick(hitLink)
                                        } else if (!latestChalkboard.value.isActive && startPos.x < curBoxW * 0.22f) {
                                            onPreviousSlide()
                                        } else if (!latestChalkboard.value.isActive && startPos.x > curBoxW * 0.78f) {
                                            onNextSlide()
                                        }
                                    }
                                    break
                                }

                                val stepDx = change.position.x - lastPos.x
                                val stepDy = change.position.y - lastPos.y
                                if (latestViewport.value.isZoomed && !latestChalkboard.value.isActive) {
                                    if (hypot(change.position.x - startPos.x, change.position.y - startPos.y) > 10f) {
                                        pannedWhileZoomed = true
                                    }
                                    if (pannedWhileZoomed) {
                                        change.consume()
                                        val (baseRect, _) = currentRects()
                                        val vp = latestViewport.value
                                        val drawnW = (baseRect.width * vp.scale).coerceAtLeast(1f)
                                        val drawnH = (baseRect.height * vp.scale).coerceAtLeast(1f)
                                        val rawPanX = vp.panX + stepDx / drawnW
                                        val rawPanY = vp.panY + stepDy / drawnH
                                        onViewportChange(
                                            vp.scale,
                                            clampViewportPan(rawPanX, vp.scale, drawnW, curBoxW),
                                            clampViewportPan(rawPanY, vp.scale, drawnH, curBoxH)
                                        )
                                    }
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
                drawRect(backdropColor)
                val bmp = slideBitmap
                val isChalkboard = chalkboardState.isActive
                if (!isChalkboard && (bmp == null || bmp.isRecycled)) return@Canvas

                val baseRect = computeBaseRect(size.width, size.height, bmp, isChalkboard)
                val slideRect = computeZoomedRect(
                    containerW = size.width,
                    containerH = size.height,
                    baseRect = baseRect,
                    viewport = viewportState,
                    isChalkboard = isChalkboard
                )

                // Clip to the FULL viewer zone (not baseRect!) so zoomed slides fill the entire viewer zone
                val visibleBounds = Rect(
                    left = slideRect.left.coerceAtLeast(0f),
                    top = slideRect.top.coerceAtLeast(0f),
                    right = slideRect.right.coerceAtMost(size.width),
                    bottom = slideRect.bottom.coerceAtMost(size.height)
                )

                if (!isBlackoutOverlay && visibleBounds.width > 0f && visibleBounds.height > 0f) {
                    clipRect(
                        left = visibleBounds.left,
                        top = visibleBounds.top,
                        right = visibleBounds.right,
                        bottom = visibleBounds.bottom
                    ) {
                        if (isChalkboard) {
                            val style = chalkboardState.style
                            drawRect(
                                color = Color(style.bgHex),
                                topLeft = Offset(slideRect.left, slideRect.top),
                                size = Size(slideRect.width, slideRect.height)
                            )
                            if (style.showDotGrid) {
                                val cols = 24
                                val spacing = slideRect.width / cols.toFloat()
                                val rows = (slideRect.height / spacing).toInt()
                                val dotRadius = (slideRect.width * 0.0022f).coerceIn(1.2f, 4f)
                                val dotColor = Color(0xFF334155)
                                for (r in 1 until rows) {
                                    for (c in 1 until cols) {
                                        drawCircle(
                                            color = dotColor,
                                            radius = dotRadius,
                                            center = Offset(
                                                slideRect.left + c * spacing,
                                                slideRect.top + r * spacing
                                            )
                                        )
                                    }
                                }
                            }
                        } else if (bmp != null && !bmp.isRecycled) {
                            drawImage(
                                image = bmp.asImageBitmap(),
                                dstOffset = IntOffset(slideRect.left.roundToInt(), slideRect.top.roundToInt()),
                                dstSize = IntSize(
                                    slideRect.width.roundToInt().coerceAtLeast(1),
                                    slideRect.height.roundToInt().coerceAtLeast(1)
                                )
                            )
                        }

                        // Render subtle device-only link hints when Navigate mode is active on PDF slides
                        if (!isChalkboard && interactive && activeTool == InteractionTool.NAVIGATE && (showLinkHints || tappedLinkId != null)) {
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

                        // Render synchronized Spotlight overlay across the visible slide area
                        if (spotlightState.isActive && spotlightState.position != null) {
                            val center = Offset(
                                slideRect.left + spotlightState.position.x * slideRect.width,
                                slideRect.top + spotlightState.position.y * slideRect.height
                            )
                            val radius = (spotlightState.radiusRatio * slideRect.width).coerceAtLeast(24f)
                            val maskPath = Path().apply {
                                fillType = PathFillType.EvenOdd
                                addRect(visibleBounds)
                                addOval(
                                    Rect(
                                        center.x - radius,
                                        center.y - radius,
                                        center.x + radius,
                                        center.y + radius
                                    )
                                )
                            }
                            drawPath(
                                path = maskPath,
                                color = Color.Black.copy(alpha = 0.72f)
                            )
                            drawCircle(
                                color = Color.White.copy(alpha = 0.7f),
                                radius = radius,
                                center = center,
                                style = Stroke(width = 1.8.dp.toPx())
                            )
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
                            val glowR = (baseRect.width * 0.026f).coerceIn(14f, 44f)
                            val coreR = (baseRect.width * 0.008f).coerceIn(5f, 14f)
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

                    // Crisp hairline border around the visible slide bounds
                    drawRect(
                        color = Color(0xFF94A3B8).copy(alpha = 0.26f),
                        topLeft = Offset(visibleBounds.left, visibleBounds.top),
                        size = Size(visibleBounds.width, visibleBounds.height),
                        style = Stroke(width = 1.dp.toPx())
                    )
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

private fun computeBaseRect(
    containerW: Float,
    containerH: Float,
    bmp: Bitmap?,
    isChalkboard: Boolean
): Rect {
    val aspectW = if (bmp != null && !bmp.isRecycled && bmp.width > 0) bmp.width.toFloat() else if (isChalkboard) 16f else containerW
    val aspectH = if (bmp != null && !bmp.isRecycled && bmp.height > 0) bmp.height.toFloat() else if (isChalkboard) 9f else containerH
    if (aspectW <= 0f || aspectH <= 0f) return Rect(0f, 0f, containerW, containerH)
    val scale = min(containerW / aspectW, containerH / aspectH)
    val drawnW = aspectW * scale
    val drawnH = aspectH * scale
    val left = (containerW - drawnW) / 2f
    val top = (containerH - drawnH) / 2f
    return Rect(left, top, left + drawnW, top + drawnH)
}

internal fun clampViewportPan(
    rawPan: Float,
    scale: Float,
    drawnSize: Float,
    containerSize: Float
): Float {
    if (scale <= 1.01f || drawnSize <= containerSize || drawnSize <= 1f) return 0f
    val maxPan = (drawnSize - containerSize) / (2f * drawnSize)
    return rawPan.coerceIn(-maxPan, maxPan)
}

private fun computeZoomedRect(
    containerW: Float,
    containerH: Float,
    baseRect: Rect,
    viewport: SlideViewportState,
    isChalkboard: Boolean
): Rect {
    if (isChalkboard || !viewport.isZoomed) return baseRect
    val zoom = viewport.scale.coerceIn(1f, 5f)
    val zoomedW = baseRect.width * zoom
    val zoomedH = baseRect.height * zoom
    val panX = clampViewportPan(viewport.panX, zoom, zoomedW, containerW)
    val panY = clampViewportPan(viewport.panY, zoom, zoomedH, containerH)
    val centerX = containerW / 2f + panX * zoomedW
    val centerY = containerH / 2f + panY * zoomedH
    val left = centerX - zoomedW / 2f
    val top = centerY - zoomedH / 2f
    return Rect(left, top, left + zoomedW, top + zoomedH)
}

private fun computeCentroid(changes: List<PointerInputChange>): Offset {
    var sumX = 0f
    var sumY = 0f
    changes.forEach {
        sumX += it.position.x
        sumY += it.position.y
    }
    val n = changes.size.coerceAtLeast(1).toFloat()
    return Offset(sumX / n, sumY / n)
}

private fun computeAverageSpan(changes: List<PointerInputChange>, centroid: Offset): Float {
    var sumDist = 0f
    changes.forEach {
        sumDist += hypot(it.position.x - centroid.x, it.position.y - centroid.y)
    }
    return (sumDist / changes.size.coerceAtLeast(1).toFloat()) * 2f
}

private fun toNormalizedPoint(offset: Offset, slideRect: Rect): NormalizedPoint? {
    if (slideRect.width <= 1f || slideRect.height <= 1f) return null
    if (offset.x < slideRect.left - 12f || offset.x > slideRect.right + 12f ||
        offset.y < slideRect.top - 12f || offset.y > slideRect.bottom + 12f
    ) {
        return null
    }
    val nx = ((offset.x - slideRect.left) / slideRect.width).coerceIn(0f, 1f)
    val ny = ((offset.y - slideRect.top) / slideRect.height).coerceIn(0f, 1f)
    return NormalizedPoint(nx, ny)
}

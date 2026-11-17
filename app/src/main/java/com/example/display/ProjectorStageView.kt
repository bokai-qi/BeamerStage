package com.example.display

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import com.example.model.AnnotationStroke
import com.example.model.ChalkboardState
import com.example.model.LaserPointerState
import com.example.model.SlideViewportState
import com.example.model.SpotlightState
import com.example.ui.components.clampViewportPan
import kotlin.math.min

data class ProjectorRenderState(
    val bitmap: Bitmap? = null,
    val strokes: List<AnnotationStroke> = emptyList(),
    val laserState: LaserPointerState = LaserPointerState(),
    val spotlightState: SpotlightState = SpotlightState(),
    val viewportState: SlideViewportState = SlideViewportState(),
    val chalkboardState: ChalkboardState = ChalkboardState(),
    val isBlackout: Boolean = false
)

class ProjectorStageView(context: Context) : View(context) {

    private var state: ProjectorRenderState = ProjectorRenderState()

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val boardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val dotGridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#334155")
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val laserCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val laserGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val spotlightMaskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(190, 0, 0, 0)
    }
    private val spotlightRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(175, 255, 255, 255)
    }

    private val baseRect = RectF()
    private val contentRect = RectF()
    private val reusablePath = Path()
    private val spotlightPath = Path()

    fun updateStage(renderState: ProjectorRenderState) {
        this.state = renderState
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.BLACK)

        val current = state
        if (current.isBlackout || width <= 0 || height <= 0) return

        val viewW = width.toFloat()
        val viewH = height.toFloat()
        val bmp = current.bitmap
        val isChalkboard = current.chalkboardState.isActive

        if (!isChalkboard && (bmp == null || bmp.isRecycled)) return

        val aspectW = if (bmp != null && !bmp.isRecycled && bmp.width > 0) bmp.width.toFloat() else 16f
        val aspectH = if (bmp != null && !bmp.isRecycled && bmp.height > 0) bmp.height.toFloat() else 9f

        val fitScale = min(viewW / aspectW, viewH / aspectH)
        val baseW = aspectW * fitScale
        val baseH = aspectH * fitScale
        val baseLeft = (viewW - baseW) / 2f
        val baseTop = (viewH - baseH) / 2f
        baseRect.set(baseLeft, baseTop, baseLeft + baseW, baseTop + baseH)

        val vp = current.viewportState
        val effectiveZoom = if (!isChalkboard && vp.mirrorToProjector && vp.isZoomed) {
            vp.scale.coerceIn(1f, 5f)
        } else {
            1f
        }
        val drawnW = baseW * effectiveZoom
        val drawnH = baseH * effectiveZoom
        val clampedPanX = clampViewportPan(vp.panX, effectiveZoom, drawnW, viewW)
        val clampedPanY = clampViewportPan(vp.panY, effectiveZoom, drawnH, viewH)

        val centerX = viewW / 2f + clampedPanX * drawnW
        val centerY = viewH / 2f + clampedPanY * drawnH
        val drawnLeft = centerX - drawnW / 2f
        val drawnTop = centerY - drawnH / 2f
        contentRect.set(drawnLeft, drawnTop, drawnLeft + drawnW, drawnTop + drawnH)

        val visibleLeft = contentRect.left.coerceAtLeast(0f)
        val visibleTop = contentRect.top.coerceAtLeast(0f)
        val visibleRight = contentRect.right.coerceAtMost(viewW)
        val visibleBottom = contentRect.bottom.coerceAtMost(viewH)
        if (visibleRight <= visibleLeft || visibleBottom <= visibleTop) return

        val saveCount = canvas.save()
        canvas.clipRect(visibleLeft, visibleTop, visibleRight, visibleBottom)

        if (isChalkboard) {
            val style = current.chalkboardState.style
            boardBgPaint.color = style.bgHex.toInt()
            canvas.drawRect(contentRect, boardBgPaint)
            if (style.showDotGrid) {
                val cols = 24
                val spacing = drawnW / cols.toFloat()
                val rows = (drawnH / spacing).toInt()
                val dotRadius = (drawnW * 0.0022f).coerceIn(1.5f, 4.5f)
                for (r in 1 until rows) {
                    for (c in 1 until cols) {
                        canvas.drawCircle(
                            contentRect.left + c * spacing,
                            contentRect.top + r * spacing,
                            dotRadius,
                            dotGridPaint
                        )
                    }
                }
            }
        } else if (bmp != null && !bmp.isRecycled) {
            canvas.drawBitmap(bmp, null, contentRect, bitmapPaint)
        }

        // Draw synchronized vector ink annotations mapped into contentRect
        for (stroke in current.strokes) {
            val pts = stroke.points
            if (pts.isEmpty()) continue
            val baseColor = stroke.colorHex.toInt()
            val argb = if (stroke.isHighlighter) {
                Color.argb(
                    96,
                    Color.red(baseColor),
                    Color.green(baseColor),
                    Color.blue(baseColor)
                )
            } else {
                baseColor
            }
            strokePaint.color = argb
            strokePaint.strokeWidth = (stroke.strokeWidthRatio * drawnW).coerceAtLeast(3f)

            if (pts.size == 1) {
                val px = contentRect.left + pts[0].x * drawnW
                val py = contentRect.top + pts[0].y * drawnH
                canvas.drawPoint(px, py, strokePaint)
            } else {
                reusablePath.reset()
                val p0x = contentRect.left + pts[0].x * drawnW
                val p0y = contentRect.top + pts[0].y * drawnH
                reusablePath.moveTo(p0x, p0y)
                for (i in 1 until pts.size) {
                    val prevX = contentRect.left + pts[i - 1].x * drawnW
                    val prevY = contentRect.top + pts[i - 1].y * drawnH
                    val curX = contentRect.left + pts[i].x * drawnW
                    val curY = contentRect.top + pts[i].y * drawnH
                    reusablePath.quadTo(prevX, prevY, (prevX + curX) / 2f, (prevY + curY) / 2f)
                }
                val lastX = contentRect.left + pts.last().x * drawnW
                val lastY = contentRect.top + pts.last().y * drawnH
                reusablePath.lineTo(lastX, lastY)
                canvas.drawPath(reusablePath, strokePaint)
            }
        }

        // Draw synchronized Spotlight dimming beam
        val spot = current.spotlightState
        if (spot.isActive && spot.position != null) {
            val sx = contentRect.left + spot.position.x * drawnW
            val sy = contentRect.top + spot.position.y * drawnH
            val sr = (spot.radiusRatio * drawnW).coerceAtLeast(32f)

            spotlightPath.reset()
            spotlightPath.fillType = Path.FillType.EVEN_ODD
            spotlightPath.addRect(visibleLeft, visibleTop, visibleRight, visibleBottom, Path.Direction.CW)
            spotlightPath.addCircle(sx, sy, sr, Path.Direction.CW)
            canvas.drawPath(spotlightPath, spotlightMaskPaint)

            spotlightRingPaint.strokeWidth = (baseW * 0.0025f).coerceIn(2f, 5f)
            canvas.drawCircle(sx, sy, sr, spotlightRingPaint)
        }

        // Draw synchronized laser pointer + smooth trail
        val laserState = current.laserState
        val laserPos = laserState.position
        if (laserState.isActive && laserPos != null) {
            val laserArgb = laserState.color.colorHex.toInt()
            val r = Color.red(laserArgb)
            val g = Color.green(laserArgb)
            val b = Color.blue(laserArgb)

            val trail = laserState.trail
            if (trail.size >= 2) {
                for (i in 1 until trail.size) {
                    val fraction = i.toFloat() / trail.size.toFloat()
                    trailPaint.color = Color.argb((fraction * 140).toInt(), r, g, b)
                    trailPaint.strokeWidth = (drawnW * 0.008f) * fraction + 2f
                    val x1 = contentRect.left + trail[i - 1].x * drawnW
                    val y1 = contentRect.top + trail[i - 1].y * drawnH
                    val x2 = contentRect.left + trail[i].x * drawnW
                    val y2 = contentRect.top + trail[i].y * drawnH
                    canvas.drawLine(x1, y1, x2, y2, trailPaint)
                }
            }

            val cx = contentRect.left + laserPos.x * drawnW
            val cy = contentRect.top + laserPos.y * drawnH
            val glowRadius = (baseW * 0.024f).coerceIn(18f, 48f)
            val coreRadius = (baseW * 0.007f).coerceIn(6f, 16f)

            laserGlowPaint.shader = RadialGradient(
                cx,
                cy,
                glowRadius,
                intArrayOf(
                    Color.argb(210, r, g, b),
                    Color.argb(90, r, g, b),
                    Color.argb(0, r, g, b)
                ),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, glowRadius, laserGlowPaint)

            laserCorePaint.color = Color.WHITE
            canvas.drawCircle(cx, cy, coreRadius * 0.65f, laserCorePaint)

            laserCorePaint.color = Color.argb(240, r, g, b)
            canvas.drawCircle(cx, cy, coreRadius, laserCorePaint)
            laserCorePaint.color = Color.WHITE
            canvas.drawCircle(cx, cy, coreRadius * 0.48f, laserCorePaint)
        }

        canvas.restoreToCount(saveCount)
    }
}

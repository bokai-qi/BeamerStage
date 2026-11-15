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
import com.example.model.LaserPointerState
import kotlin.math.min

class ProjectorStageView(context: Context) : View(context) {

    private var slideBitmap: Bitmap? = null
    private var strokes: List<AnnotationStroke> = emptyList()
    private var laserState: LaserPointerState = LaserPointerState()
    private var isBlackout: Boolean = false

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
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

    private val slideDestRect = RectF()
    private val reusablePath = Path()

    fun updateStage(
        bitmap: Bitmap?,
        strokes: List<AnnotationStroke>,
        laserState: LaserPointerState,
        isBlackout: Boolean
    ) {
        this.slideBitmap = bitmap
        this.strokes = strokes
        this.laserState = laserState
        this.isBlackout = isBlackout
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.BLACK)

        if (isBlackout) return
        val bmp = slideBitmap ?: return
        if (bmp.isRecycled || width <= 0 || height <= 0) return

        val viewW = width.toFloat()
        val viewH = height.toFloat()
        val bmpW = bmp.width.toFloat().coerceAtLeast(1f)
        val bmpH = bmp.height.toFloat().coerceAtLeast(1f)

        val scale = min(viewW / bmpW, viewH / bmpH)
        val drawnW = bmpW * scale
        val drawnH = bmpH * scale
        val left = (viewW - drawnW) / 2f
        val top = (viewH - drawnH) / 2f
        slideDestRect.set(left, top, left + drawnW, top + drawnH)

        canvas.drawBitmap(bmp, null, slideDestRect, bitmapPaint)

        // Draw synchronized vector ink annotations mapped into slideDestRect
        for (stroke in strokes) {
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
                val px = slideDestRect.left + pts[0].x * drawnW
                val py = slideDestRect.top + pts[0].y * drawnH
                canvas.drawPoint(px, py, strokePaint)
            } else {
                reusablePath.reset()
                val p0x = slideDestRect.left + pts[0].x * drawnW
                val p0y = slideDestRect.top + pts[0].y * drawnH
                reusablePath.moveTo(p0x, p0y)
                for (i in 1 until pts.size) {
                    val prevX = slideDestRect.left + pts[i - 1].x * drawnW
                    val prevY = slideDestRect.top + pts[i - 1].y * drawnH
                    val curX = slideDestRect.left + pts[i].x * drawnW
                    val curY = slideDestRect.top + pts[i].y * drawnH
                    reusablePath.quadTo(prevX, prevY, (prevX + curX) / 2f, (prevY + curY) / 2f)
                }
                val lastX = slideDestRect.left + pts.last().x * drawnW
                val lastY = slideDestRect.top + pts.last().y * drawnH
                reusablePath.lineTo(lastX, lastY)
                canvas.drawPath(reusablePath, strokePaint)
            }
        }

        // Draw synchronized laser pointer + smooth trail
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
                    val x1 = slideDestRect.left + trail[i - 1].x * drawnW
                    val y1 = slideDestRect.top + trail[i - 1].y * drawnH
                    val x2 = slideDestRect.left + trail[i].x * drawnW
                    val y2 = slideDestRect.top + trail[i].y * drawnH
                    canvas.drawLine(x1, y1, x2, y2, trailPaint)
                }
            }

            val cx = slideDestRect.left + laserPos.x * drawnW
            val cy = slideDestRect.top + laserPos.y * drawnH
            val glowRadius = (drawnW * 0.024f).coerceIn(18f, 48f)
            val coreRadius = (drawnW * 0.007f).coerceIn(6f, 16f)

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
    }
}

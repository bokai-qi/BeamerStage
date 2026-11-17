package com.example.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.local.DeckEntity
import com.example.model.AnnotationStroke
import com.example.model.BeamerLayoutMode
import com.example.model.ChalkboardStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object AnnotatedPdfExporter {

    suspend fun exportDeckWithAnnotations(
        context: Context,
        pdfEngine: PdfSlideEngine,
        deck: DeckEntity,
        requestedMode: BeamerLayoutMode,
        annotationsByPage: Map<Int, List<AnnotationStroke>>,
        chalkboardStyle: ChalkboardStyle = ChalkboardStyle.DARK_SLATE
    ): Uri = withContext(Dispatchers.IO) {
        val totalPages = pdfEngine.openDocument(deck.uriString).coerceAtLeast(1)
        val document = PdfDocument()

        val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#334155")
        }
        val path = Path()

        try {
            var defaultWidth = 960
            var defaultHeight = 540

            for (pageIndex in 0 until totalPages) {
                val rendered = pdfEngine.renderSlidePair(
                    uriString = deck.uriString,
                    pageIndex = pageIndex,
                    requestedMode = requestedMode,
                    targetSlideWidthPx = 1280,
                    includeNotesBitmap = false
                )
                val bmp = rendered.slideBitmap
                val pageW = (bmp?.width ?: defaultWidth).coerceAtLeast(320)
                val pageH = (bmp?.height ?: defaultHeight).coerceAtLeast(240)
                defaultWidth = pageW
                defaultHeight = pageH

                val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, pageIndex + 1).create()
                val page = document.startPage(pageInfo)
                val canvas = page.canvas
                canvas.drawColor(Color.WHITE)

                if (bmp != null && !bmp.isRecycled) {
                    canvas.drawBitmap(bmp, null, RectF(0f, 0f, pageW.toFloat(), pageH.toFloat()), bitmapPaint)
                }

                val strokes = annotationsByPage[pageIndex].orEmpty()
                drawStrokesOnCanvas(
                    canvas = canvas,
                    strokes = strokes,
                    width = pageW.toFloat(),
                    height = pageH.toFloat(),
                    strokePaint = strokePaint,
                    reusablePath = path
                )

                document.finishPage(page)
            }

            // Append any non-empty Chalkboard scratchpad pages (stored at negative indices <= -1000)
            val boardEntries = annotationsByPage.entries
                .filter { it.key <= -1000 && it.value.isNotEmpty() }
                .sortedByDescending { it.key } // -1000 (Board 1), -1001 (Board 2), ...

            boardEntries.forEachIndexed { idx, entry ->
                val pageNumber = totalPages + idx + 1
                val pageInfo = PdfDocument.PageInfo.Builder(defaultWidth, defaultHeight, pageNumber).create()
                val page = document.startPage(pageInfo)
                val canvas = page.canvas
                canvas.drawColor(chalkboardStyle.bgHex.toInt())

                if (chalkboardStyle.showDotGrid) {
                    val cols = 24
                    val spacing = defaultWidth.toFloat() / cols.toFloat()
                    val rows = (defaultHeight / spacing).toInt()
                    val radius = (defaultWidth * 0.0022f).coerceAtLeast(1.5f)
                    for (r in 1 until rows) {
                        for (c in 1 until cols) {
                            canvas.drawCircle(c * spacing, r * spacing, radius, dotPaint)
                        }
                    }
                }

                drawStrokesOnCanvas(
                    canvas = canvas,
                    strokes = entry.value,
                    width = defaultWidth.toFloat(),
                    height = defaultHeight.toFloat(),
                    strokePaint = strokePaint,
                    reusablePath = path
                )

                document.finishPage(page)
            }

            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            exportDir.listFiles()?.forEach { old ->
                if (System.currentTimeMillis() - old.lastModified() > 24L * 3600L * 1000L) {
                    old.delete()
                }
            }

            val sanitizedTitle = deck.title
                .replace(Regex("[^A-Za-z0-9._-]+"), "_")
                .trim('_')
                .ifBlank { "BeamerStage_Slides" }
                .take(48)
            val outFile = File(exportDir, "${sanitizedTitle}_annotated.pdf")
            FileOutputStream(outFile).use { out ->
                document.writeTo(out)
            }

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outFile
            )
        } finally {
            document.close()
        }
    }

    private fun drawStrokesOnCanvas(
        canvas: Canvas,
        strokes: List<AnnotationStroke>,
        width: Float,
        height: Float,
        strokePaint: Paint,
        reusablePath: Path
    ) {
        for (stroke in strokes) {
            val pts = stroke.points
            if (pts.isEmpty()) continue
            val baseColor = stroke.colorHex.toInt()
            strokePaint.color = if (stroke.isHighlighter) {
                Color.argb(96, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
            } else {
                baseColor
            }
            strokePaint.strokeWidth = (stroke.strokeWidthRatio * width).coerceAtLeast(2.5f)

            if (pts.size == 1) {
                canvas.drawPoint(pts[0].x * width, pts[0].y * height, strokePaint)
            } else {
                reusablePath.reset()
                reusablePath.moveTo(pts[0].x * width, pts[0].y * height)
                for (i in 1 until pts.size) {
                    val prevX = pts[i - 1].x * width
                    val prevY = pts[i - 1].y * height
                    val curX = pts[i].x * width
                    val curY = pts[i].y * height
                    reusablePath.quadTo(prevX, prevY, (prevX + curX) / 2f, (prevY + curY) / 2f)
                }
                reusablePath.lineTo(pts.last().x * width, pts.last().y * height)
                canvas.drawPath(reusablePath, strokePaint)
            }
        }
    }
}

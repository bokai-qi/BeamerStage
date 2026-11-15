package com.example.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.data.local.DeckEntity
import com.example.data.local.PresentationRepository
import com.example.model.BeamerLayoutMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.cos
import kotlin.math.sin

class SampleBeamerGenerator(
    private val context: Context,
    private val repository: PresentationRepository
) {
    companion object {
        const val SAMPLE_STANDARD_ID = "sample_spectral_graph_beamer"
        const val SAMPLE_SPLIT_ID = "sample_split_notes_beamer"
    }

    suspend fun ensureSamplesSeeded() = withContext(Dispatchers.IO) {
        val samplesDir = File(context.filesDir, "samples").apply { mkdirs() }

        val standardFile = File(samplesDir, "spectral_graph_theory.pdf")
        if (!standardFile.exists()) {
            createStandardBeamerPdf(standardFile)
        }
        if (repository.getDeckById(SAMPLE_STANDARD_ID) == null) {
            repository.upsertDeck(
                DeckEntity(
                    id = SAMPLE_STANDARD_ID,
                    uriString = standardFile.toURI().toString(),
                    title = "Spectral Graph Theory (Beamer)",
                    pageCount = 6,
                    lastPageIndex = 0,
                    layoutModeName = BeamerLayoutMode.SINGLE.name,
                    targetDurationMinutes = 15,
                    isBundledSample = true,
                    lastOpenedAt = System.currentTimeMillis()
                )
            )
            val notes = listOf(
                "Welcome everyone! Today we explore how eigenvalues of the graph Laplacian reveal global connectivity, bottlenecks, and clustering structure.",
                "Highlight the normalization factor D^{-1/2} on the slide. Point with the crimson laser to the degree diagonal entries before moving to Cheeger's inequality.",
                "Walk slowly through the Cheeger inequality: λ₂ bounds the expansion ratio h(G) from both sides. Emphasize why spectral clustering works on sparse graphs.",
                "Use the Pen tool to circle the Fiedler vector sign-cut across the bridge edge between cluster A and cluster B.",
                "Compare runtime complexity: power iteration converges in O(m / gap) steps, making it practical for million-node networks.",
                "Thank the audience and open for Q&A. Keep Freeze active if someone asks to revisit Slide 3's theorem while you check your backup slides."
            )
            notes.forEachIndexed { idx, text ->
                repository.saveSlideNote(SAMPLE_STANDARD_ID, idx, text)
            }
        }

        val splitFile = File(samplesDir, "beamer_split_notes_demo.pdf")
        if (!splitFile.exists()) {
            createSplitNotesBeamerPdf(splitFile)
        }
        if (repository.getDeckById(SAMPLE_SPLIT_ID) == null) {
            repository.upsertDeck(
                DeckEntity(
                    id = SAMPLE_SPLIT_ID,
                    uriString = splitFile.toURI().toString(),
                    title = "Dual-Screen Beamer Notes Demo",
                    pageCount = 4,
                    lastPageIndex = 0,
                    layoutModeName = BeamerLayoutMode.AUTO.name,
                    targetDurationMinutes = 10,
                    isBundledSample = true,
                    lastOpenedAt = System.currentTimeMillis() - 1000L
                )
            )
        }
    }

    private fun createStandardBeamerPdf(outputFile: File) {
        val pdf = PdfDocument()
        val width = 960
        val height = 540

        drawStandardSlide(pdf, 1, width, height) { canvas ->
            drawBeamerHeader(canvas, width, "Colloquium · Applied Mathematics", 1, 6)
            val titleBg = Paint().apply { color = Color.rgb(22, 33, 62) }
            canvas.drawRoundRect(RectF(64f, 110f, width - 64f, 270f), 14f, 14f, titleBg)

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 38f
                typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            }
            canvas.drawText("Spectral Graph Theory", 96f, 178f, titlePaint)

            val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(147, 197, 253)
                textSize = 21f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }
            canvas.drawText("Laplacian Eigenvalues, Cheeger Bounds & Partitioning", 96f, 222f, subtitlePaint)

            val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(51, 65, 85)
                textSize = 18f
                typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            }
            canvas.drawText("Prof. E. Noether · Department of Mathematics", 96f, 340f, metaPaint)
            canvas.drawText("Compiled with LaTeX Beamer · 16:9 Widescreen", 96f, 376f, metaPaint)
            drawBeamerFooter(canvas, width, height, "Spectral Graph Theory", "1 / 6")
        }

        drawStandardSlide(pdf, 2, width, height) { canvas ->
            drawBeamerHeader(canvas, width, "1. The Normalized Graph Laplacian", 2, 6)
            drawBullet(canvas, 70f, 128f, "Let G = (V, E) be an undirected weighted graph with adjacency matrix W.")
            drawBullet(canvas, 70f, 170f, "Degree matrix D = diag(d₁, …, dₙ), where dᵢ = ∑ⱼ wᵢⱼ.")
            drawTheoremBlock(
                canvas,
                title = "Definition (Normalized Laplacian)",
                lines = listOf(
                    "L_sym = I - D^(-1/2) W D^(-1/2)",
                    "For any f ∈ ℝⁿ:   fᵀ L_sym f = ½ ∑_{(i,j)∈E} wᵢⱼ (fᵢ/√dᵢ - fⱼ/√dⱼ)² ≥ 0"
                ),
                top = 215f,
                width = width
            )
            drawBullet(canvas, 70f, 395f, "All eigenvalues are real and bounded:  0 = λ₁ ≤ λ₂ ≤ … ≤ λₙ ≤ 2.")
            drawBeamerFooter(canvas, width, height, "Spectral Graph Theory", "2 / 6")
        }

        drawStandardSlide(pdf, 3, width, height) { canvas ->
            drawBeamerHeader(canvas, width, "2. Cheeger's Inequality & Bottlenecks", 3, 6)
            drawTheoremBlock(
                canvas,
                title = "Theorem (Alon–Milman / Cheeger)",
                lines = listOf(
                    "Define conductance φ(S) = cut(S, V\\S) / min(vol(S), vol(V\\S)).",
                    "Let h(G) = min_S φ(S). Then the second eigenvalue λ₂ satisfies:",
                    "        λ₂ / 2  ≤  h(G)  ≤  √(2 λ₂)"
                ),
                top = 115f,
                width = width
            )
            drawBullet(canvas, 70f, 320f, "λ₂ = 0  if and only if G has at least two connected components.")
            drawBullet(canvas, 70f, 365f, "Large spectral gap λ₂ guarantees rapid mixing of random walks.")
            drawBullet(canvas, 70f, 410f, "Provides a provable O(√λ₂) approximation to the NP-hard Sparsest Cut.")
            drawBeamerFooter(canvas, width, height, "Spectral Graph Theory", "3 / 6")
        }

        drawStandardSlide(pdf, 4, width, height) { canvas ->
            drawBeamerHeader(canvas, width, "3. Fiedler Vector Partitioning", 4, 6)
            drawGraphClusterDiagram(canvas, 80f, 125f, 410f, 330f)
            drawBullet(canvas, 530f, 155f, "Eigenvector v₂ (Fiedler vector)", maxWidth = 370f)
            drawBullet(canvas, 530f, 205f, "Threshold cut S = { i : v₂(i) ≥ τ }", maxWidth = 370f)
            drawBullet(canvas, 530f, 255f, "Sweep over n−1 sorted entries", maxWidth = 370f)
            drawBullet(canvas, 530f, 305f, "Minimizes conductance across", maxWidth = 370f)
            val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(30, 64, 175)
                textSize = 18f
                typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
            }
            canvas.drawText("   sparse inter-cluster bridges.", 530f, 335f, notePaint)
            drawBeamerFooter(canvas, width, height, "Spectral Graph Theory", "4 / 6")
        }

        drawStandardSlide(pdf, 5, width, height) { canvas ->
            drawBeamerHeader(canvas, width, "4. Eigenvalue Spectrum Plot", 5, 6)
            drawSpectrumPlot(canvas, 80f, 125f, 520f, 320f)
            drawTheoremBlock(
                canvas,
                title = "k-Way Clustering Heuristic",
                lines = listOf(
                    "A gap between λₖ and λₖ₊₁",
                    "indicates k cohesive clusters",
                    "in the underlying manifold."
                ),
                top = 155f,
                width = width,
                left = 635f,
                right = width - 55f
            )
            drawBeamerFooter(canvas, width, height, "Spectral Graph Theory", "5 / 6")
        }

        drawStandardSlide(pdf, 6, width, height) { canvas ->
            drawBeamerHeader(canvas, width, "Summary & Open Questions", 6, 6)
            drawBullet(canvas, 70f, 135f, "Laplacian spectra bridge linear algebra, geometry, and combinatorics.")
            drawBullet(canvas, 70f, 185f, "Cheeger's inequality certifies partition quality without brute force.")
            drawBullet(canvas, 70f, 235f, "Extensions: Hypergraph Laplacians, Magnetic Laplacians, GNNs.")
            val thanksPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(22, 33, 62)
                textSize = 30f
                typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            }
            canvas.drawText("Thank you — Questions?", 70f, 355f, thanksPaint)
            drawBeamerFooter(canvas, width, height, "Spectral Graph Theory", "6 / 6")
        }

        FileOutputStream(outputFile).use { fos ->
            pdf.writeTo(fos)
        }
        pdf.close()
    }

    private fun createSplitNotesBeamerPdf(outputFile: File) {
        val pdf = PdfDocument()
        val width = 1920
        val height = 540
        val half = 960

        val slideTitles = listOf(
            "Dual-Screen Beamer Workflow",
            "Why Split-Page PDFs?",
            "Live Math & Diagram Focus",
            "Stage Execution Checklist"
        )
        val slideBodies = listOf(
            listOf(
                "Compiled with \\setbeameroption{show notes on second screen=right}",
                "Left 960×540 canvas projects cleanly to USB-C DisplayPort.",
                "Right 960×540 canvas stays private on your phone screen."
            ),
            listOf(
                "LaTeX \\note{...} embeds equations and TikZ diagrams inside notes.",
                "Zero extra sidecar files needed — everything travels in one PDF.",
                "BeamerStage auto-detects the 32:9 double-wide geometry."
            ),
            listOf(
                "Use the Laser pointer to highlight terms on the audience display.",
                "Use the Pen tool to annotate proofs live during Q&A.",
                "Tap Freeze to inspect upcoming slides without moving the projector."
            ),
            listOf(
                "1. Connect Pixel USB-C DisplayPort adapter to lecture hall projector.",
                "2. Verify 1920×1080 signal pill in the top stage bar.",
                "3. Use Volume Up/Down as a discreet pocket slide clicker."
            )
        )
        val beamerNotes = listOf(
            listOf(
                "[TIMING: 0:00 – 1:30]",
                "• Introduce how LaTeX Beamer outputs 32:9 split PDFs.",
                "• Audience sees ONLY the crisp slide on the left half.",
                "• Mention that DisplayPort on Pixel requires zero Wi-Fi."
            ),
            listOf(
                "[TIMING: 1:30 – 4:00]",
                "• Recall reminder: P(A|B) = P(B|A)P(A) / P(B).",
                "• Emphasize that compiled Beamer notes preserve math typesetting.",
                "• Switch to Single Mode anytime from the Display chip."
            ),
            listOf(
                "[TIMING: 4:00 – 7:30]",
                "• Demo the Crimson laser pointer on bullet #1.",
                "• Draw a quick sky-blue box around 'Freeze' to explain Q&A peeking.",
                "• Keep pace steady — target 2.5 minutes per section."
            ),
            listOf(
                "[TIMING: 7:30 – 10:00]",
                "• Final recap before taking audience questions.",
                "• Hit Blackout if switching back to chalkboard discussion.",
                "• Thank the session chair and audience."
            )
        )

        for (i in 0 until 4) {
            val pageInfo = PdfDocument.PageInfo.Builder(width, height, i + 1).create()
            val page = pdf.startPage(pageInfo)
            val canvas = page.canvas
            canvas.drawColor(Color.WHITE)

            // Left Half: Audience Beamer Slide
            canvas.save()
            canvas.clipRect(0, 0, half, height)
            drawBeamerHeader(canvas, half, slideTitles[i], i + 1, 4)
            slideBodies[i].forEachIndexed { idx, line ->
                drawBullet(canvas, 60f, 150f + idx * 72f, line, maxWidth = half - 110f)
            }
            drawBeamerFooter(canvas, half, height, "Dual-Screen Beamer", "${i + 1} / 4")
            canvas.restore()

            // Divider line at 960 (invisible when cropped, helpful if viewed in Single mode)
            val dividerPaint = Paint().apply {
                color = Color.rgb(203, 213, 225)
                strokeWidth = 2f
            }
            canvas.drawLine(half.toFloat(), 0f, half.toFloat(), height.toFloat(), dividerPaint)

            // Right Half: Rendered Beamer Notes Page
            canvas.save()
            canvas.translate(half.toFloat(), 0f)
            val noteBg = Paint().apply { color = Color.rgb(248, 250, 252) }
            canvas.drawRect(0f, 0f, half.toFloat(), height.toFloat(), noteBg)

            val noteHeader = Paint().apply { color = Color.rgb(30, 41, 59) }
            canvas.drawRect(0f, 0f, half.toFloat(), 66f, noteHeader)

            val noteHeaderText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 22f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            }
            canvas.drawText("BEAMER \\note{} — SLIDE ${i + 1} OF 4", 36f, 42f, noteHeaderText)

            val noteLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(15, 23, 42)
                textSize = 23f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }
            val timingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(2, 132, 199)
                textSize = 22f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            }
            beamerNotes[i].forEachIndexed { idx, line ->
                val y = 128f + idx * 68f
                canvas.drawText(line, 40f, y, if (idx == 0) timingPaint else noteLinePaint)
            }
            canvas.restore()

            pdf.finishPage(page)
        }

        FileOutputStream(outputFile).use { fos ->
            pdf.writeTo(fos)
        }
        pdf.close()
    }

    private inline fun drawStandardSlide(
        pdf: PdfDocument,
        pageNumber: Int,
        width: Int,
        height: Int,
        block: (Canvas) -> Unit
    ) {
        val pageInfo = PdfDocument.PageInfo.Builder(width, height, pageNumber).create()
        val page = pdf.startPage(pageInfo)
        page.canvas.drawColor(Color.WHITE)
        block(page.canvas)
        pdf.finishPage(page)
    }

    private fun drawBeamerHeader(canvas: Canvas, width: Int, title: String, page: Int, total: Int) {
        val barPaint = Paint().apply { color = Color.rgb(22, 33, 62) }
        canvas.drawRect(0f, 0f, width.toFloat(), 74f, barPaint)

        val accentPaint = Paint().apply { color = Color.rgb(56, 189, 248) }
        canvas.drawRect(0f, 74f, width.toFloat(), 78f, accentPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 25f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        canvas.drawText(title, 44f, 48f, textPaint)

        // Beamer navigation circles
        val dotFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(56, 189, 248) }
        val dotStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(148, 163, 184)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val startX = width - 44f - (total - 1) * 20f
        for (i in 1..total) {
            val cx = startX + (i - 1) * 20f
            canvas.drawCircle(cx, 38f, 5.5f, if (i <= page) dotFill else dotStroke)
        }
    }

    private fun drawBeamerFooter(canvas: Canvas, width: Int, height: Int, leftText: String, rightText: String) {
        val footerPaint = Paint().apply { color = Color.rgb(241, 245, 249) }
        canvas.drawRect(0f, height - 38f, width.toFloat(), height.toFloat(), footerPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(71, 85, 105)
            textSize = 14f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        canvas.drawText(leftText, 44f, height - 14f, textPaint)
        val rw = textPaint.measureText(rightText)
        canvas.drawText(rightText, width - 44f - rw, height - 14f, textPaint)
    }

    private fun drawBullet(canvas: Canvas, x: Float, y: Float, text: String, maxWidth: Float = 820f) {
        val bulletPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(2, 132, 199) }
        canvas.drawCircle(x, y - 6f, 5f, bulletPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 20f
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        }
        val clipped = if (textPaint.measureText(text) > maxWidth) {
            val end = textPaint.breakText(text, true, maxWidth, null)
            text.substring(0, end)
        } else {
            text
        }
        canvas.drawText(clipped, x + 20f, y, textPaint)
    }

    private fun drawTheoremBlock(
        canvas: Canvas,
        title: String,
        lines: List<String>,
        top: Float,
        width: Int,
        left: Float = 64f,
        right: Float = width - 64f
    ) {
        val bodyHeight = 50f + lines.size * 36f
        val headerBg = Paint().apply { color = Color.rgb(30, 58, 138) }
        val bodyBg = Paint().apply { color = Color.rgb(239, 246, 255) }
        val border = Paint().apply {
            color = Color.rgb(191, 219, 254)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawRoundRect(RectF(left, top, right, top + bodyHeight), 10f, 10f, bodyBg)
        canvas.drawRoundRect(RectF(left, top, right, top + 38f), 10f, 10f, headerBg)
        canvas.drawRect(left, top + 24f, right, top + 38f, headerBg)
        canvas.drawRoundRect(RectF(left, top, right, top + bodyHeight), 10f, 10f, border)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 18f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText(title, left + 18f, top + 25f, titlePaint)

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 18f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }
        lines.forEachIndexed { idx, line ->
            canvas.drawText(line, left + 18f, top + 72f + idx * 34f, linePaint)
        }
    }

    private fun drawGraphClusterDiagram(canvas: Canvas, x: Float, y: Float, w: Float, h: Float) {
        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(248, 250, 252) }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(203, 213, 225)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawRoundRect(RectF(x, y, x + w, y + h), 12f, 12f, boxPaint)
        canvas.drawRoundRect(RectF(x, y, x + w, y + h), 12f, 12f, borderPaint)

        val clusterAList = List(5) { idx ->
            val angle = idx * (2.0 * Math.PI / 5.0)
            Pair(x + w * 0.28f + 56f * cos(angle).toFloat(), y + h * 0.52f + 56f * sin(angle).toFloat())
        }
        val clusterBList = List(5) { idx ->
            val angle = idx * (2.0 * Math.PI / 5.0) + 0.3
            Pair(x + w * 0.72f + 56f * cos(angle).toFloat(), y + h * 0.52f + 56f * sin(angle).toFloat())
        }

        val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(100, 116, 139)
            strokeWidth = 2.5f
        }
        for (i in clusterAList.indices) {
            for (j in i + 1 until clusterAList.size) {
                canvas.drawLine(clusterAList[i].first, clusterAList[i].second, clusterAList[j].first, clusterAList[j].second, edgePaint)
                canvas.drawLine(clusterBList[i].first, clusterBList[i].second, clusterBList[j].first, clusterBList[j].second, edgePaint)
            }
        }
        // Single bridge edge (the cut)
        val bridgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(244, 63, 94)
            strokeWidth = 4f
        }
        canvas.drawLine(clusterAList[0].first, clusterAList[0].second, clusterBList[2].first, clusterBList[2].second, bridgePaint)

        val nodeA = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(2, 132, 199) }
        val nodeB = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(16, 185, 129) }
        clusterAList.forEach { canvas.drawCircle(it.first, it.second, 11f, nodeA) }
        clusterBList.forEach { canvas.drawCircle(it.first, it.second, 11f, nodeB) }

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(51, 65, 85)
            textSize = 16f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText("S  (v₂ > 0)", x + w * 0.16f, y + h - 22f, labelPaint)
        canvas.drawText("V\\S  (v₂ < 0)", x + w * 0.62f, y + h - 22f, labelPaint)
    }

    private fun drawSpectrumPlot(canvas: Canvas, x: Float, y: Float, w: Float, h: Float) {
        val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 41, 59)
            strokeWidth = 2.5f
        }
        val originX = x + 46f
        val originY = y + h - 42f
        val plotW = w - 72f
        val plotH = h - 72f
        canvas.drawLine(originX, originY, originX + plotW, originY, axisPaint)
        canvas.drawLine(originX, originY, originX, originY - plotH, axisPaint)

        val values = listOf(0.0f, 0.08f, 0.14f, 0.72f, 0.94f, 1.18f, 1.45f, 1.78f)
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(2, 132, 199) }
        val gapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(244, 63, 94)
            strokeWidth = 3f
        }
        val points = values.mapIndexed { idx, v ->
            val px = originX + 26f + idx * (plotW - 52f) / (values.size - 1)
            val py = originY - (v / 2.0f) * (plotH - 20f)
            Pair(px, py)
        }
        // Highlight spectral gap between λ₃ and λ₄
        canvas.drawLine(points[2].first, points[2].second, points[3].first, points[3].second, gapPaint)
        points.forEach { (px, py) ->
            canvas.drawCircle(px, py, 7f, dotPaint)
        }
        val caption = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(225, 29, 72)
            textSize = 15f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        canvas.drawText("← eigengap Δ = λ₄ - λ₃", points[2].first + 18f, (points[2].second + points[3].second) / 2f, caption)
    }
}

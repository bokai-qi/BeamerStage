package com.example

import com.example.model.AnnotationStroke
import com.example.model.BeamerLayoutMode
import com.example.model.NormalizedPoint
import com.example.model.NormalizedRect
import com.example.model.PdfLinkAction
import com.example.pdf.PdfLinkAnnotator
import com.example.pdf.PdfLinkExtractor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets

class PresenterLogicTest {

    @Test
    fun annotationStrokesRoundTripSerialization() {
        val strokes = listOf(
            AnnotationStroke(
                id = "s1",
                points = listOf(NormalizedPoint(0.1f, 0.2f), NormalizedPoint(0.8f, 0.7f)),
                colorHex = 0xFFF43F5E,
                strokeWidthRatio = 0.0055f,
                isHighlighter = false
            )
        )
        val encoded = Json.encodeToString(strokes)
        val decoded = Json.decodeFromString<List<AnnotationStroke>>(encoded)
        assertEquals(strokes, decoded)
    }

    @Test
    fun beamerLayoutModesHaveDistinctBadges() {
        val badges = BeamerLayoutMode.entries.map { it.badgeLabel }.toSet()
        assertEquals(BeamerLayoutMode.entries.size, badges.size)
        assertTrue(badges.contains("SLIDE | NOTE"))
    }

    @Test
    fun normalizedRectHitTestingRespectsSlop() {
        val rect = NormalizedRect(0.20f, 0.40f, 0.35f, 0.48f)
        assertTrue(rect.contains(0.25f, 0.44f))
        assertTrue(rect.contains(0.19f, 0.40f, slop = 0.015f))
        assertFalse(rect.contains(0.10f, 0.44f, slop = 0.015f))
    }

    @Test
    fun pdfLinkExtractorParsesGotoUriAndNamedDestinations() {
        val samplePdf = """
            %PDF-1.4
            1 0 obj
            << /Type /Catalog /Pages 2 0 R /Names << /Dests 6 0 R >> >>
            endobj
            2 0 obj
            << /Type /Pages /Kids [3 0 R 4 0 R 5 0 R] /Count 3 /MediaBox [0 0 960 540] >>
            endobj
            3 0 obj
            << /Type /Page /Parent 2 0 R /Annots [7 0 R 8 0 R 9 0 R] >>
            endobj
            4 0 obj
            << /Type /Page /Parent 2 0 R >>
            endobj
            5 0 obj
            << /Type /Page /Parent 2 0 R >>
            endobj
            6 0 obj
            << /Names [(sec:end) [5 0 R /Fit]] >>
            endobj
            7 0 obj
            << /Type /Annot /Subtype /Link /Rect [96 400 288 454] /A << /S /GoTo /D (sec:end) >> >>
            endobj
            8 0 obj
            << /Type /Annot /Subtype /Link /Rect [480 54 672 108] /A << /S /URI /URI (https://ctan.org/pkg/beamer) >> >>
            endobj
            9 0 obj
            << /Type /Annot /Subtype /Link /Rect [860 10 900 35] /A << /S /Named /N /NextPage >> >>
            endobj
            xref
            0 10
            0000000000 65535 f 
            trailer
            << /Size 10 /Root 1 0 R >>
            startxref
            0
            %%EOF
        """.trimIndent().toByteArray(StandardCharsets.ISO_8859_1)

        val rawMap = PdfLinkExtractor.extractLinksFromPdfBytes(samplePdf)
        val page0Links = rawMap[0].orEmpty()
        assertEquals(3, page0Links.size)
        assertEquals(PdfLinkAction.GoToPage(2), page0Links[0].action)
        assertEquals(PdfLinkAction.OpenUri("https://ctan.org/pkg/beamer"), page0Links[1].action)
        assertEquals(PdfLinkAction.GoToPage(1), page0Links[2].action)

        // Check top-left coordinate conversion (960x540 page, Rect [96 400 288 454])
        assertEquals(0.10f, page0Links[0].leftFraction, 0.001f)
        assertEquals(0.30f, page0Links[0].rightFraction, 0.001f)
        assertEquals((540f - 454f) / 540f, page0Links[0].topFraction, 0.001f)
        assertEquals((540f - 400f) / 540f, page0Links[0].bottomFraction, 0.001f)

        // Check Beamer split-screen cropping keeps left-half link & excludes right-half link
        val splitSlideLinks = PdfLinkExtractor.mapRawLinksToSlide(
            pageIndex = 0,
            rawLinks = page0Links,
            effectiveLayout = BeamerLayoutMode.SLIDE_LEFT_NOTES_RIGHT
        )
        assertEquals(1, splitSlideLinks.size)
        assertEquals(0.20f, splitSlideLinks[0].bounds[0].left, 0.001f)
        assertEquals(0.60f, splitSlideLinks[0].bounds[0].right, 0.001f)
    }

    @Test
    fun pdfLinkAnnotatorAppendsValidIncrementalUpdate() {
        val basePdf = """
            %PDF-1.4
            1 0 obj
            << /Type /Catalog /Pages 2 0 R >>
            endobj
            2 0 obj
            << /Type /Pages /Kids [3 0 R 4 0 R] /Count 2 /MediaBox [0 0 960 540] >>
            endobj
            3 0 obj
            << /Type /Page /Parent 2 0 R >>
            endobj
            4 0 obj
            << /Type /Page /Parent 2 0 R >>
            endobj
            xref
            0 5
            0000000000 65535 f 
            trailer
            << /Size 5 /Root 1 0 R >>
            startxref
            123
            %%EOF
        """.trimIndent()

        val tempFile = File.createTempFile("annot_test", ".pdf")
        try {
            tempFile.writeText(basePdf, StandardCharsets.ISO_8859_1)
            PdfLinkAnnotator.appendLinksToPdf(
                pdfFile = tempFile,
                pageHeightPt = 540f,
                linksByPage = mapOf(
                    0 to listOf(
                        PdfLinkAnnotator.SampleLinkSpec(
                            left = 96f,
                            top = 108f,
                            right = 288f,
                            bottom = 162f,
                            target = PdfLinkAnnotator.SampleLinkTarget.GoToPage(1)
                        )
                    )
                )
            )
            val parsed = PdfLinkExtractor.extractLinksFromPdfBytes(tempFile.readBytes())
            val p0 = parsed[0].orEmpty()
            assertEquals(1, p0.size)
            assertEquals(PdfLinkAction.GoToPage(1), p0[0].action)
            assertEquals(0.10f, p0[0].leftFraction, 0.001f)
            assertEquals(0.20f, p0[0].topFraction, 0.001f)
        } finally {
            tempFile.delete()
        }
    }
}

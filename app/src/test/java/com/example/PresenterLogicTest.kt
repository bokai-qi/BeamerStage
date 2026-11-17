package com.example

import com.example.model.AnnotationStroke
import com.example.model.AppThemeMode
import com.example.model.BeamerLayoutMode
import com.example.model.DarkThemeStyle
import com.example.model.NormalizedPoint
import com.example.model.NormalizedRect
import com.example.model.PdfLinkAction
import com.example.model.PdfSlideLink
import com.example.model.UserPreferences
import com.example.pdf.PdfLinkAnnotator
import com.example.pdf.PdfLinkExtractor
import com.example.ui.presenter.StrokeHitTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.zip.Deflater

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

    @Test
    fun eraserHitTestRemovesTouchedPenAndHighlighterStrokes() {
        val penStroke = AnnotationStroke(
            id = "pen_1",
            points = listOf(NormalizedPoint(0.20f, 0.30f), NormalizedPoint(0.50f, 0.30f)),
            colorHex = 0xFFF43F5E,
            strokeWidthRatio = 0.0055f,
            isHighlighter = false
        )
        val markStroke = AnnotationStroke(
            id = "mark_1",
            points = listOf(NormalizedPoint(0.60f, 0.70f), NormalizedPoint(0.85f, 0.70f)),
            colorHex = 0xFFF59E0B,
            strokeWidthRatio = 0.024f,
            isHighlighter = true
        )
        val strokes = listOf(penStroke, markStroke)

        // Touching near midpoint of pen stroke erases only penStroke
        val afterPenErase = StrokeHitTest.removeStrokesNear(strokes, NormalizedPoint(0.35f, 0.315f))
        assertEquals(listOf(markStroke), afterPenErase)

        // Touching far from both erases nothing
        val afterMiss = StrokeHitTest.removeStrokesNear(strokes, NormalizedPoint(0.10f, 0.85f))
        assertEquals(strokes, afterMiss)

        // Touching near highlighter stroke removes it
        val afterMarkErase = StrokeHitTest.removeStrokesNear(afterPenErase, NormalizedPoint(0.72f, 0.725f))
        assertTrue(afterMarkErase.isEmpty())
    }

    @Test
    fun userPreferencesIncludeThemeAndSimpleModeDefaults() {
        val prefs = UserPreferences()
        assertEquals(AppThemeMode.DARK, prefs.themeMode)
        assertEquals(DarkThemeStyle.STAGE, prefs.darkThemeStyle)
        assertEquals(2, DarkThemeStyle.entries.size)
        assertFalse(prefs.simplePresentingMode)
    }

    @Test
    fun streamingLinkExtractorSkipsLargeBinaryStreamsAndHandlesInlineObjectsAndCycles() {
        val imagePayloadSize = 4 * 1024 * 1024 // 4 MB embedded image stream
        val sb = StringBuilder()
        sb.append("%PDF-1.5\n")
        sb.append("1 0 obj<< /Type /Catalog /Pages 2 0 R >>endobj\n")
        sb.append("2 0 obj << /Type /Pages /Kids [2 0 R 3 0 R 4 0 R] /Count 2 /MediaBox [0 0 960 540] >> endobj\n")
        sb.append("3 0 obj << /Type /Page /Parent 2 0 R /Resources << /XObject << /Im1 5 0 R >> >> /Annots [6 0 R] >> endobj\n")
        sb.append("4 0 obj << /Type /Page /Parent 2 0 R >> endobj\n")
        sb.append("5 0 obj << /Type /XObject /Subtype /Image /Length $imagePayloadSize >>\nstream\n")
        val prefixBytes = sb.toString().toByteArray(StandardCharsets.ISO_8859_1)

        val suffix = """
            
            endstream
            endobj
            6 0 obj << /Type /Annot /Subtype /Link /Rect [100 100 300 200] /A << /S /GoTo /D 7 0 R >> >> endobj
            7 0 obj [4 0 R /Fit] endobj
            trailer
            << /Size 8 /Root 1 0 R >>
            %%EOF
        """.trimIndent().toByteArray(StandardCharsets.ISO_8859_1)

        val combined = ByteArray(prefixBytes.size + imagePayloadSize + suffix.size)
        System.arraycopy(prefixBytes, 0, combined, 0, prefixBytes.size)
        // Fill image payload with arbitrary bytes
        for (i in 0 until imagePayloadSize step 4) {
            combined[prefixBytes.size + i] = 0x55
        }
        System.arraycopy(suffix, 0, combined, prefixBytes.size + imagePayloadSize, suffix.size)

        val parsed = PdfLinkExtractor.extractLinksFromStream(combined.inputStream())
        val p0Links = parsed[0].orEmpty()
        assertEquals(1, p0Links.size)
        assertEquals(PdfLinkAction.GoToPage(1), p0Links[0].action)
    }

    @Test
    fun objStmWithIndirectLengthExtractsBeamerNamedButtonsAndEscapedDests() {
        val obj3 = "<< /Type /Page /Parent 2 0 R /Annots [7 0 R 8 0 R] >>"
        val obj4 = "<< /Type /Page /Parent 2 0 R >>"
        val obj6 = "<< /Names [(frame\\(end\\)) << /D [4 0 R /Fit] >>] >>"
        val obj7 = "<< /Type /Annot /Subtype /Link /Rect [800 10 840 30] /A << /S /Named /N /NextPage >> >>"
        val obj8 = "<< /Type /Annot /Subtype /Link /Rect [100 100 300 160] /A << /S /GoTo /D (frame\\(end\\)) >> >>"

        val entries = listOf(3 to obj3, 4 to obj4, 6 to obj6, 7 to obj7, 8 to obj8)
        val bodies = StringBuilder()
        val header = StringBuilder()
        var offset = 0
        for ((id, body) in entries) {
            header.append("$id $offset ")
            bodies.append(body).append('\n')
            offset += body.length + 1
        }
        val headerStr = header.toString()
        val rawObjStm = (headerStr + bodies.toString()).toByteArray(StandardCharsets.ISO_8859_1)

        val deflater = Deflater()
        deflater.setInput(rawObjStm)
        deflater.finish()
        val compressedOut = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        while (!deflater.finished()) {
            val n = deflater.deflate(buf)
            if (n > 0) compressedOut.write(buf, 0, n)
        }
        deflater.end()
        val compressedBytes = compressedOut.toByteArray()

        val pdfOut = ByteArrayOutputStream()
        pdfOut.write(
            """
            %PDF-1.5
            1 0 obj << /Type /Catalog /Pages 2 0 R /Names << /Dests 6 0 R >> >> endobj
            2 0 obj << /Kids [3 0 R 4 0 R] /Count 2 /MediaBox [0 0 960 540] >> endobj
            10 0 obj << /Type /ObjStm /N ${entries.size} /First ${headerStr.length} /Filter /FlateDecode /Length 20 0 R >>
            stream
            """.trimIndent().toByteArray(StandardCharsets.ISO_8859_1)
        )
        pdfOut.write('\n'.code)
        pdfOut.write(compressedBytes)
        pdfOut.write(
            """
            
            endstream
            endobj
            20 0 obj ${compressedBytes.size} endobj
            trailer << /Size 21 /Root 1 0 R >>
            %%EOF
            """.trimIndent().toByteArray(StandardCharsets.ISO_8859_1)
        )

        val parsed = PdfLinkExtractor.extractLinksFromStream(pdfOut.toByteArray().inputStream())
        val p0 = parsed[0].orEmpty()
        assertEquals(2, p0.size)
        assertEquals(PdfLinkAction.GoToPage(1), p0[0].action)
        assertEquals(PdfLinkAction.GoToPage(1), p0[1].action)
    }

    @Test
    fun mergeSlideLinksPreservesBeamerButtonsAndDeduplicatesOverlappingPlatformLinks() {
        val platformLinks = listOf(
            PdfSlideLink(
                id = "goto_0_0",
                bounds = listOf(NormalizedRect(0.10f, 0.20f, 0.30f, 0.28f)),
                action = PdfLinkAction.GoToPage(3)
            )
        )
        val parsedLinks = listOf(
            // Duplicate of native link
            PdfSlideLink(
                id = "raw_0_0",
                bounds = listOf(NormalizedRect(0.101f, 0.201f, 0.299f, 0.279f)),
                action = PdfLinkAction.GoToPage(3)
            ),
            // Beamer navigation button (/Named /NextPage) missed by Android PdfRenderer
            PdfSlideLink(
                id = "raw_0_1",
                bounds = listOf(NormalizedRect(0.85f, 0.90f, 0.92f, 0.96f)),
                action = PdfLinkAction.GoToPage(1)
            )
        )

        val merged = PdfLinkExtractor.mergeSlideLinks(platformLinks, parsedLinks)
        assertEquals(2, merged.size)
        assertEquals("goto_0_0", merged[0].id)
        assertEquals("raw_0_1", merged[1].id)
    }

    @Test
    fun pdfMetadataExtractorParsesHierarchicalOutlinesAndUtf16Titles() {
        // UTF-16BE with BOM (FEFF) for "Appendix α"
        val utf16Hex = "FEFF0041007000700065006E006400690078002003B1"
        val samplePdf = """
            %PDF-1.4
            1 0 obj
            << /Type /Catalog /Pages 2 0 R /Outlines 10 0 R >>
            endobj
            2 0 obj
            << /Type /Pages /Kids [3 0 R 4 0 R 5 0 R] /Count 3 /MediaBox [0 0 960 540] >>
            endobj
            3 0 obj
            << /Type /Page /Parent 2 0 R >>
            endobj
            4 0 obj
            << /Type /Page /Parent 2 0 R >>
            endobj
            5 0 obj
            << /Type /Page /Parent 2 0 R >>
            endobj
            10 0 obj
            << /Type /Outlines /First 11 0 R /Last 13 0 R /Count 3 >>
            endobj
            11 0 obj
            << /Title (1. Introduction) /Parent 10 0 R /Next 13 0 R /First 12 0 R /Last 12 0 R /Dest [3 0 R /Fit] >>
            endobj
            12 0 obj
            << /Title (1.1 Setup & Notation) /Parent 11 0 R /A << /S /GoTo /D [4 0 R /Fit] >> >>
            endobj
            13 0 obj
            << /Title <$utf16Hex> /Parent 10 0 R /Prev 11 0 R /Dest [5 0 R /Fit] >>
            endobj
            trailer
            << /Size 14 /Root 1 0 R >>
            %%EOF
        """.trimIndent().toByteArray(StandardCharsets.ISO_8859_1)

        val metadata = PdfLinkExtractor.extractMetadataFromPdfBytes(samplePdf)
        assertEquals(3, metadata.outlines.size)

        assertEquals("1. Introduction", metadata.outlines[0].title)
        assertEquals(0, metadata.outlines[0].pageIndex)
        assertEquals(0, metadata.outlines[0].level)

        assertEquals("1.1 Setup & Notation", metadata.outlines[1].title)
        assertEquals(1, metadata.outlines[1].pageIndex)
        assertEquals(1, metadata.outlines[1].level)

        assertEquals("Appendix α", metadata.outlines[2].title)
        assertEquals(2, metadata.outlines[2].pageIndex)
        assertEquals(0, metadata.outlines[2].level)
    }

    @Test
    fun pdfLinkAnnotatorWritesOutlinesAndRoundTripsMetadata() {
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

        val tempFile = File.createTempFile("annot_outlines_test", ".pdf")
        try {
            tempFile.writeText(basePdf, StandardCharsets.ISO_8859_1)
            PdfLinkAnnotator.appendLinksToPdf(
                pdfFile = tempFile,
                pageHeightPt = 540f,
                linksByPage = emptyMap(),
                outlines = listOf(
                    com.example.model.PdfOutlineEntry("Overview", 0),
                    com.example.model.PdfOutlineEntry("Main Theorem", 1)
                )
            )
            val meta = PdfLinkExtractor.extractMetadataFromPdfBytes(tempFile.readBytes())
            assertEquals(2, meta.outlines.size)
            assertEquals("Overview", meta.outlines[0].title)
            assertEquals(0, meta.outlines[0].pageIndex)
            assertEquals("Main Theorem", meta.outlines[1].title)
            assertEquals(1, meta.outlines[1].pageIndex)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun stageModelsIncludeSpotlightCharcoalInkAndZoomDetection() {
        assertTrue(com.example.model.PenColor.entries.any { it == com.example.model.PenColor.CHARCOAL })
        assertEquals(3, com.example.model.PenStrokeWidth.entries.size)
        assertTrue(com.example.model.InteractionTool.entries.contains(com.example.model.InteractionTool.SPOTLIGHT))
        assertFalse(com.example.model.SlideViewportState(scale = 1f).isZoomed)
        assertTrue(com.example.model.SlideViewportState(scale = 1.8f).isZoomed)
    }

    @Test
    fun clampViewportPanKeepsSlideCenteredWhenZoomedOutAndEdgeToEdgeWhenZoomedIn() {
        // At 1.0x scale, pan always snaps to 0f
        assertEquals(0f, com.example.ui.components.clampViewportPan(0.3f, 1.0f, 1000f, 1000f), 0.0001f)

        // When zoomed slide along an axis is still smaller than container (e.g. tall Focus container), keep centered (0f)
        assertEquals(0f, com.example.ui.components.clampViewportPan(0.25f, 1.4f, 840f, 1600f), 0.0001f)

        // When zoomed slide exceeds container (e.g. 2000px drawn inside 1000px container), maxPan = (2000 - 1000)/(2*2000) = 0.25f
        val clampedPos = com.example.ui.components.clampViewportPan(0.45f, 2.0f, 2000f, 1000f)
        assertEquals(0.25f, clampedPos, 0.0001f)
        val leftAtMax = (1000f / 2f + clampedPos * 2000f) - 2000f / 2f
        assertEquals(0f, leftAtMax, 0.01f) // Left edge touches 0px with zero background gap

        val clampedNeg = com.example.ui.components.clampViewportPan(-0.45f, 2.0f, 2000f, 1000f)
        assertEquals(-0.25f, clampedNeg, 0.0001f)
        val rightAtMin = (1000f / 2f + clampedNeg * 2000f) + 2000f / 2f
        assertEquals(1000f, rightAtMin, 0.01f) // Right edge touches containerW with zero background gap
    }
}

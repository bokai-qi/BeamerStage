package com.example.pdf

import com.example.model.PdfOutlineEntry
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Appends standard ISO-32000 PDF Link annotations (/Subtype /Link) and Beamer section
 * bookmarks (/Outlines) to an existing PDF using a clean incremental update.
 */
object PdfLinkAnnotator {

    sealed interface SampleLinkTarget {
        data class GoToPage(val targetPageIndex: Int) : SampleLinkTarget
        data class OpenUri(val uri: String) : SampleLinkTarget
    }

    /**
     * Bounds are in top-left canvas pixel coordinates (matching PdfDocument's page width/height).
     */
    data class SampleLinkSpec(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val target: SampleLinkTarget
    )

    fun appendLinksToPdf(
        pdfFile: File,
        pageHeightPt: Float,
        linksByPage: Map<Int, List<SampleLinkSpec>>,
        outlines: List<PdfOutlineEntry> = emptyList()
    ) {
        if ((linksByPage.isEmpty() && outlines.isEmpty()) || !pdfFile.exists()) return
        val originalBytes = pdfFile.readBytes()
        val text = String(originalBytes, StandardCharsets.ISO_8859_1)

        val rootRef = Regex("""/Root\s+(\d+)\s+\d+\s+R""").findAll(text).lastOrNull()
            ?.groupValues?.get(1)?.toIntOrNull() ?: return
        val sizeMatch = Regex("""/Size\s+(\d+)""").findAll(text).lastOrNull()
            ?.groupValues?.get(1)?.toIntOrNull() ?: return
        val prevStartXref = Regex("""startxref\s+(\d+)""").findAll(text).lastOrNull()
            ?.groupValues?.get(1)?.toIntOrNull() ?: return

        var catalogBody: String? = null
        val pageEntries = mutableListOf<Pair<Int, String>>()
        val objRegex = Regex("""(?:^|[\r\n])\s*(\d+)\s+0\s+obj\b(.*?)endobj""", RegexOption.DOT_MATCHES_ALL)
        objRegex.findAll(text).forEach { m ->
            val objNum = m.groupValues[1].toIntOrNull() ?: return@forEach
            val body = m.groupValues[2].trim()
            if (objNum == rootRef) {
                catalogBody = body
            }
            if (Regex("""/Type\s*/Page\b""").containsMatchIn(body) &&
                !Regex("""/Type\s*/Pages\b""").containsMatchIn(body)
            ) {
                pageEntries.add(objNum to body)
            }
        }
        if (pageEntries.isEmpty()) return

        var nextObjNum = sizeMatch
        val updatedObjects = linkedMapOf<Int, String>()

        linksByPage.forEach { (pageIdx, specs) ->
            if (pageIdx !in pageEntries.indices || specs.isEmpty()) return@forEach
            val (pageObjNum, originalPageBody) = pageEntries[pageIdx]
            val annotRefs = mutableListOf<Int>()

            for (spec in specs) {
                val annotObjNum = nextObjNum++
                val lly = pageHeightPt - spec.bottom
                val ury = pageHeightPt - spec.top
                val rectStr = "[${spec.left} $lly ${spec.right} $ury]"
                val actionStr = when (val t = spec.target) {
                    is SampleLinkTarget.GoToPage -> {
                        val targetPageObj = pageEntries.getOrNull(t.targetPageIndex)?.first ?: continue
                        "<< /S /GoTo /D [$targetPageObj 0 R /Fit] >>"
                    }
                    is SampleLinkTarget.OpenUri -> {
                        "<< /S /URI /URI (${escapePdfLiteral(t.uri)}) >>"
                    }
                }
                updatedObjects[annotObjNum] =
                    "<< /Type /Annot /Subtype /Link /Rect $rectStr /Border [0 0 0] /A $actionStr >>"
                annotRefs.add(annotObjNum)
            }

            if (annotRefs.isNotEmpty()) {
                val refsList = annotRefs.joinToString(" ") { "$it 0 R" }
                val updatedBody = insertKeyIntoDict(originalPageBody, "/Annots [$refsList]")
                updatedObjects[pageObjNum] = updatedBody
            }
        }

        // Optional flat/section PDF /Outlines tree
        val validOutlines = outlines.filter { it.pageIndex in pageEntries.indices }
        val currentCatalog = catalogBody
        if (validOutlines.isNotEmpty() && currentCatalog != null) {
            val outlinesRootObj = nextObjNum++
            val itemObjNums = validOutlines.map { nextObjNum++ }

            validOutlines.forEachIndexed { idx, entry ->
                val objNum = itemObjNums[idx]
                val targetPageObj = pageEntries[entry.pageIndex].first
                val prevPart = if (idx > 0) " /Prev ${itemObjNums[idx - 1]} 0 R" else ""
                val nextPart = if (idx + 1 < itemObjNums.size) " /Next ${itemObjNums[idx + 1]} 0 R" else ""
                updatedObjects[objNum] =
                    "<< /Title (${escapePdfLiteral(entry.title)}) /Parent $outlinesRootObj 0 R$prevPart$nextPart /Dest [$targetPageObj 0 R /Fit] >>"
            }

            updatedObjects[outlinesRootObj] =
                "<< /Type /Outlines /First ${itemObjNums.first()} 0 R /Last ${itemObjNums.last()} 0 R /Count ${itemObjNums.size} >>"
            updatedObjects[rootRef] = insertKeyIntoDict(currentCatalog, "/Outlines $outlinesRootObj 0 R")
        }

        if (updatedObjects.isEmpty()) return

        val out = ByteArrayOutputStream()
        out.write(originalBytes)
        if (originalBytes.lastOrNull() != '\n'.code.toByte()) {
            out.write('\n'.code)
        }

        val offsets = linkedMapOf<Int, Int>()
        for ((objNum, body) in updatedObjects) {
            offsets[objNum] = out.size()
            val entry = "$objNum 0 obj\n$body\nendobj\n"
            out.write(entry.toByteArray(StandardCharsets.ISO_8859_1))
        }

        val newStartXref = out.size()
        val xrefSb = StringBuilder()
        xrefSb.append("xref\n")
        for ((objNum, offset) in offsets.entries.sortedBy { it.key }) {
            xrefSb.append("$objNum 1\n")
            xrefSb.append(String.format(java.util.Locale.US, "%010d 00000 n \n", offset))
        }
        xrefSb.append("trailer\n")
        xrefSb.append("<< /Size $nextObjNum /Root $rootRef 0 R /Prev $prevStartXref >>\n")
        xrefSb.append("startxref\n")
        xrefSb.append("$newStartXref\n")
        xrefSb.append("%%EOF\n")
        out.write(xrefSb.toString().toByteArray(StandardCharsets.ISO_8859_1))

        pdfFile.writeBytes(out.toByteArray())
    }

    private fun escapePdfLiteral(raw: String): String {
        return raw
            .replace("\\", "\\\\")
            .replace("(", "\\(")
            .replace(")", "\\)")
    }

    private fun insertKeyIntoDict(dictBody: String, keyVal: String): String {
        val lastClose = dictBody.lastIndexOf(">>")
        if (lastClose < 0) return dictBody
        val prefix = dictBody.substring(0, lastClose).trimEnd()
        val suffix = dictBody.substring(lastClose)
        return "$prefix $keyVal $suffix"
    }
}

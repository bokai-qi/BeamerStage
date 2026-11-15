package com.example.pdf

import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.os.Build
import com.example.model.BeamerLayoutMode
import com.example.model.NormalizedRect
import com.example.model.PdfLinkAction
import com.example.model.PdfSlideLink
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.Inflater

/**
 * Extracts clickable internal GoTo and external URI links from PDF pages.
 * Uses Android V (API 35+) native PdfRenderer link APIs when available, and
 * falls back to a self-contained PDF object/annotation parser for API 24-34.
 */
object PdfLinkExtractor {

    data class RawPageLink(
        val leftFraction: Float,
        val topFraction: Float,
        val rightFraction: Float,
        val bottomFraction: Float,
        val action: PdfLinkAction
    )

    fun extractFromPlatformPage(
        page: PdfRenderer.Page,
        effectiveLayout: BeamerLayoutMode
    ): List<PdfSlideLink> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            return emptyList()
        }
        val pageWidth = page.width.coerceAtLeast(1).toFloat()
        val pageHeight = page.height.coerceAtLeast(1).toFloat()
        val result = mutableListOf<PdfSlideLink>()

        val gotoLinks = runCatching { page.gotoLinks }.getOrDefault(emptyList())
        gotoLinks.forEachIndexed { idx, gotoLink ->
            val targetPage = gotoLink.destination.pageNumber
            val rects = gotoLink.bounds.mapNotNull { rect ->
                mapPageRectToSlideNormalized(rect, pageWidth, pageHeight, effectiveLayout)
            }
            if (rects.isNotEmpty() && targetPage >= 0) {
                result.add(
                    PdfSlideLink(
                        id = "goto_${page.index}_$idx",
                        bounds = rects,
                        action = PdfLinkAction.GoToPage(targetPage)
                    )
                )
            }
        }

        val uriLinks = runCatching { page.linkContents }.getOrDefault(emptyList())
        uriLinks.forEachIndexed { idx, linkContent ->
            val uriStr = linkContent.uri.toString()
            if (uriStr.isNotBlank()) {
                val rects = linkContent.bounds.mapNotNull { rect ->
                    mapPageRectToSlideNormalized(rect, pageWidth, pageHeight, effectiveLayout)
                }
                if (rects.isNotEmpty()) {
                    result.add(
                        PdfSlideLink(
                            id = "uri_${page.index}_$idx",
                            bounds = rects,
                            action = PdfLinkAction.OpenUri(uriStr)
                        )
                    )
                }
            }
        }
        return result
    }

    fun mapRawLinksToSlide(
        pageIndex: Int,
        rawLinks: List<RawPageLink>,
        effectiveLayout: BeamerLayoutMode
    ): List<PdfSlideLink> {
        return rawLinks.mapIndexedNotNull { idx, raw ->
            val normalized = mapFractionRectToSlide(
                leftFrac = raw.leftFraction,
                topFrac = raw.topFraction,
                rightFrac = raw.rightFraction,
                bottomFrac = raw.bottomFraction,
                effectiveLayout = effectiveLayout
            ) ?: return@mapIndexedNotNull null
            PdfSlideLink(
                id = "raw_${pageIndex}_$idx",
                bounds = listOf(normalized),
                action = raw.action
            )
        }
    }

    private fun mapPageRectToSlideNormalized(
        rect: RectF,
        pageWidth: Float,
        pageHeight: Float,
        effectiveLayout: BeamerLayoutMode
    ): NormalizedRect? {
        val leftFrac = rect.left / pageWidth
        val topFrac = rect.top / pageHeight
        val rightFrac = rect.right / pageWidth
        val bottomFrac = rect.bottom / pageHeight
        return mapFractionRectToSlide(leftFrac, topFrac, rightFrac, bottomFrac, effectiveLayout)
    }

    private fun mapFractionRectToSlide(
        leftFrac: Float,
        topFrac: Float,
        rightFrac: Float,
        bottomFrac: Float,
        effectiveLayout: BeamerLayoutMode
    ): NormalizedRect? {
        val mappedLeft: Float
        val mappedRight: Float
        when (effectiveLayout) {
            BeamerLayoutMode.SLIDE_LEFT_NOTES_RIGHT -> {
                if (leftFrac >= 0.5f) return null
                mappedLeft = (leftFrac * 2f).coerceIn(0f, 1f)
                mappedRight = (rightFrac * 2f).coerceIn(0f, 1f)
            }
            BeamerLayoutMode.NOTES_LEFT_SLIDE_RIGHT -> {
                if (rightFrac <= 0.5f) return null
                mappedLeft = ((leftFrac - 0.5f) * 2f).coerceIn(0f, 1f)
                mappedRight = ((rightFrac - 0.5f) * 2f).coerceIn(0f, 1f)
            }
            else -> {
                mappedLeft = leftFrac.coerceIn(0f, 1f)
                mappedRight = rightFrac.coerceIn(0f, 1f)
            }
        }
        val mappedTop = topFrac.coerceIn(0f, 1f)
        val mappedBottom = bottomFrac.coerceIn(0f, 1f)
        if (mappedRight <= mappedLeft || mappedBottom <= mappedTop) return null
        return NormalizedRect(mappedLeft, mappedTop, mappedRight, mappedBottom)
    }

    /**
     * Parses raw PDF bytes to extract page-level link annotations (/Subtype /Link).
     * Supports incremental updates, /FlateDecode /ObjStm object streams, named destinations,
     * /GoTo actions, /URI actions, and Beamer /Named actions (/NextPage, /PrevPage, etc.).
     */
    fun extractLinksFromPdfBytes(pdfBytes: ByteArray): Map<Int, List<RawPageLink>> {
        val text = String(pdfBytes, StandardCharsets.ISO_8859_1)
        val objects = parseAllObjects(pdfBytes, text)
        if (objects.isEmpty()) return emptyMap()

        val rootRef = findRootObjectNumber(text) ?: findCatalogObjectNumber(objects) ?: return emptyMap()
        val catalogBody = objects[rootRef] ?: return emptyMap()

        val pagesRootRef = extractIndirectRef(catalogBody, "/Pages") ?: return emptyMap()
        val orderedPages = mutableListOf<PageNode>()
        collectPages(pagesRootRef, objects, floatArrayOf(0f, 0f, 612f, 792f), orderedPages)
        if (orderedPages.isEmpty()) return emptyMap()

        val pageNumByObj = mutableMapOf<Int, Int>()
        orderedPages.forEachIndexed { idx, node ->
            pageNumByObj[node.objNum] = idx
        }

        val namedDests = collectNamedDestinations(catalogBody, objects, pageNumByObj)
        val linksByPage = mutableMapOf<Int, List<RawPageLink>>()

        orderedPages.forEachIndexed { pageIndex, pageNode ->
            val pageBody = objects[pageNode.objNum] ?: return@forEachIndexed
            val annotRefs = extractAnnotsRefs(pageBody, objects)
            if (annotRefs.isEmpty()) return@forEachIndexed

            val media = pageNode.mediaBox
            val mediaW = (media[2] - media[0]).let { if (it <= 0f) 1f else it }
            val mediaH = (media[3] - media[1]).let { if (it <= 0f) 1f else it }

            val pageLinks = mutableListOf<RawPageLink>()
            for (annotRef in annotRefs) {
                val annotBody = objects[annotRef] ?: continue
                if (!Regex("""/Subtype\s*/Link\b""").containsMatchIn(annotBody)) continue

                val rect = extractFloatArray(annotBody, "/Rect", 4) ?: continue
                val llx = minOf(rect[0], rect[2])
                val lly = minOf(rect[1], rect[3])
                val urx = maxOf(rect[0], rect[2])
                val ury = maxOf(rect[1], rect[3])

                val leftFrac = (llx - media[0]) / mediaW
                val rightFrac = (urx - media[0]) / mediaW
                val topFrac = (media[3] - ury) / mediaH
                val bottomFrac = (media[3] - lly) / mediaH

                val action = resolveAnnotAction(
                    annotBody = annotBody,
                    objects = objects,
                    pageNumByObj = pageNumByObj,
                    namedDests = namedDests,
                    currentPageIndex = pageIndex,
                    totalPages = orderedPages.size
                ) ?: continue

                pageLinks.add(
                    RawPageLink(
                        leftFraction = leftFrac,
                        topFraction = topFrac,
                        rightFraction = rightFrac,
                        bottomFraction = bottomFrac,
                        action = action
                    )
                )
            }
            if (pageLinks.isNotEmpty()) {
                linksByPage[pageIndex] = pageLinks
            }
        }
        return linksByPage
    }

    private data class PageNode(
        val objNum: Int,
        val mediaBox: FloatArray
    )

    private fun parseAllObjects(pdfBytes: ByteArray, text: String): Map<Int, String> {
        val objects = HashMap<Int, String>()
        val objHeaderRegex = Regex("""(?:^|[\r\n])\s*(\d+)\s+\d+\s+obj\b""")
        val matches = objHeaderRegex.findAll(text).toList()

        for (i in matches.indices) {
            val match = matches[i]
            val objNum = match.groupValues[1].toIntOrNull() ?: continue
            val bodyStart = match.range.last + 1
            val searchLimit = if (i + 1 < matches.size) matches[i + 1].range.first else text.length
            val endObjIdx = text.indexOf("endobj", bodyStart)
            val bodyEnd = if (endObjIdx in bodyStart until searchLimit) endObjIdx else searchLimit
            val rawBody = text.substring(bodyStart, bodyEnd).trim()
            // In PDF incremental updates, later object definitions overwrite earlier ones
            objects[objNum] = rawBody

            // Expand compressed PDF 1.5+ /ObjStm streams if present
            if (rawBody.contains("/Type") && rawBody.contains("/ObjStm")) {
                parseObjStm(pdfBytes, text, bodyStart, bodyEnd, rawBody, objects)
            }
        }
        return objects
    }

    private fun parseObjStm(
        pdfBytes: ByteArray,
        text: String,
        bodyStart: Int,
        bodyEnd: Int,
        headerBody: String,
        objects: MutableMap<Int, String>
    ) {
        val nMatch = Regex("""/N\s+(\d+)""").find(headerBody) ?: return
        val firstMatch = Regex("""/First\s+(\d+)""").find(headerBody) ?: return
        val count = nMatch.groupValues[1].toIntOrNull() ?: return
        val firstOffset = firstMatch.groupValues[1].toIntOrNull() ?: return

        val streamKeywordIdx = text.indexOf("stream", bodyStart)
        if (streamKeywordIdx < 0 || streamKeywordIdx >= bodyEnd) return
        var dataStart = streamKeywordIdx + "stream".length
        if (dataStart < pdfBytes.size && pdfBytes[dataStart] == '\r'.code.toByte()) dataStart++
        if (dataStart < pdfBytes.size && pdfBytes[dataStart] == '\n'.code.toByte()) dataStart++

        val endStreamIdx = text.indexOf("endstream", dataStart)
        if (endStreamIdx < 0 || endStreamIdx > bodyEnd) return
        var dataEnd = endStreamIdx
        while (dataEnd > dataStart && (pdfBytes[dataEnd - 1] == '\n'.code.toByte() || pdfBytes[dataEnd - 1] == '\r'.code.toByte())) {
            dataEnd--
        }
        if (dataEnd <= dataStart) return

        val compressedSlice = pdfBytes.copyOfRange(dataStart, dataEnd)
        val decodedBytes = if (headerBody.contains("/FlateDecode")) {
            inflateBytes(compressedSlice) ?: return
        } else {
            compressedSlice
        }
        val decoded = String(decodedBytes, StandardCharsets.ISO_8859_1)
        if (firstOffset > decoded.length) return

        val headerNums = Regex("""\d+""").findAll(decoded.substring(0, firstOffset))
            .mapNotNull { it.value.toIntOrNull() }
            .toList()
        val pairs = mutableListOf<Pair<Int, Int>>()
        for (i in 0 until minOf(count, headerNums.size / 2)) {
            pairs.add(headerNums[i * 2] to headerNums[i * 2 + 1])
        }
        for (i in pairs.indices) {
            val objId = pairs[i].first
            val start = firstOffset + pairs[i].second
            val end = if (i + 1 < pairs.size) firstOffset + pairs[i + 1].second else decoded.length
            if (start in 0..end && end <= decoded.length) {
                objects.putIfAbsent(objId, decoded.substring(start, end).trim())
            }
        }
    }

    private fun inflateBytes(input: ByteArray): ByteArray? {
        return runCatching {
            val inflater = Inflater()
            inflater.setInput(input)
            val baos = ByteArrayOutputStream()
            val buf = ByteArray(4096)
            while (!inflater.finished()) {
                val read = inflater.inflate(buf)
                if (read == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) break
                } else {
                    baos.write(buf, 0, read)
                }
            }
            inflater.end()
            baos.toByteArray()
        }.getOrNull()
    }

    private fun findRootObjectNumber(text: String): Int? {
        val matches = Regex("""/Root\s+(\d+)\s+\d+\s+R""").findAll(text).toList()
        return matches.lastOrNull()?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun findCatalogObjectNumber(objects: Map<Int, String>): Int? {
        return objects.entries.firstOrNull {
            Regex("""/Type\s*/Catalog\b""").containsMatchIn(it.value)
        }?.key
    }

    private fun collectPages(
        nodeObjNum: Int,
        objects: Map<Int, String>,
        inheritedMediaBox: FloatArray,
        out: MutableList<PageNode>
    ) {
        val body = objects[nodeObjNum] ?: return
        val mediaBox = extractFloatArray(body, "/MediaBox", 4) ?: inheritedMediaBox
        val cropBox = extractFloatArray(body, "/CropBox", 4) ?: mediaBox

        val kidsMatch = Regex("""/Kids\s*\[(.*?)]""", RegexOption.DOT_MATCHES_ALL).find(body)
        if (kidsMatch != null && Regex("""/Type\s*/Pages\b""").containsMatchIn(body)) {
            val kidRefs = Regex("""(\d+)\s+\d+\s+R""").findAll(kidsMatch.groupValues[1])
                .mapNotNull { it.groupValues[1].toIntOrNull() }
            for (kid in kidRefs) {
                collectPages(kid, objects, cropBox, out)
            }
        } else if (Regex("""/Type\s*/Page\b""").containsMatchIn(body)) {
            out.add(PageNode(nodeObjNum, cropBox))
        }
    }

    private fun extractAnnotsRefs(pageBody: String, objects: Map<Int, String>): List<Int> {
        val directArray = Regex("""/Annots\s*\[(.*?)]""", RegexOption.DOT_MATCHES_ALL).find(pageBody)
        if (directArray != null) {
            return Regex("""(\d+)\s+\d+\s+R""").findAll(directArray.groupValues[1])
                .mapNotNull { it.groupValues[1].toIntOrNull() }
                .toList()
        }
        val annotsRef = extractIndirectRef(pageBody, "/Annots") ?: return emptyList()
        val arrayBody = objects[annotsRef] ?: return emptyList()
        return Regex("""(\d+)\s+\d+\s+R""").findAll(arrayBody)
            .mapNotNull { it.groupValues[1].toIntOrNull() }
            .toList()
    }

    private fun collectNamedDestinations(
        catalogBody: String,
        objects: Map<Int, String>,
        pageNumByObj: Map<Int, Int>
    ): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        // 1. /Dests dictionary on Catalog
        val destsRef = extractIndirectRef(catalogBody, "/Dests")
        if (destsRef != null) {
            objects[destsRef]?.let { parseDestsDictEntries(it, objects, pageNumByObj, result) }
        }
        // 2. /Names -> /Dests name tree
        val namesRef = extractIndirectRef(catalogBody, "/Names")
        val namesBody = if (namesRef != null) objects[namesRef] else catalogBody
        if (namesBody != null) {
            val nameTreeRef = extractIndirectRef(namesBody, "/Dests")
            if (nameTreeRef != null) {
                traverseNameTree(nameTreeRef, objects, pageNumByObj, result)
            }
        }
        return result
    }

    private fun parseDestsDictEntries(
        body: String,
        objects: Map<Int, String>,
        pageNumByObj: Map<Int, Int>,
        out: MutableMap<String, Int>
    ) {
        val directMatch = Regex("""/([A-Za-z0-9_.:+-]+)\s*\[(\d+)\s+\d+\s+R""").findAll(body)
        for (m in directMatch) {
            val obj = m.groupValues[2].toIntOrNull() ?: continue
            pageNumByObj[obj]?.let { out[m.groupValues[1]] = it }
        }
        val refMatch = Regex("""/([A-Za-z0-9_.:+-]+)\s+(\d+)\s+\d+\s+R""").findAll(body)
        for (m in refMatch) {
            val destObj = m.groupValues[2].toIntOrNull() ?: continue
            val destBody = objects[destObj] ?: continue
            resolveDestBodyToPage(destBody, pageNumByObj)?.let { out[m.groupValues[1]] = it }
        }
    }

    private fun traverseNameTree(
        nodeObj: Int,
        objects: Map<Int, String>,
        pageNumByObj: Map<Int, Int>,
        out: MutableMap<String, Int>
    ) {
        val body = objects[nodeObj] ?: return
        val kidsMatch = Regex("""/Kids\s*\[(.*?)]""", RegexOption.DOT_MATCHES_ALL).find(body)
        if (kidsMatch != null) {
            Regex("""(\d+)\s+\d+\s+R""").findAll(kidsMatch.groupValues[1]).forEach { m ->
                m.groupValues[1].toIntOrNull()?.let { traverseNameTree(it, objects, pageNumByObj, out) }
            }
        }
        val namesMatch = Regex("""/Names\s*\[(.*)]""", RegexOption.DOT_MATCHES_ALL).find(body)
        if (namesMatch != null) {
            val content = namesMatch.groupValues[1]
            val entryRegex = Regex("""\(([^)]*)\)\s*(?:\[(\d+)\s+\d+\s+R[^\]]*]|(\d+)\s+\d+\s+R)""")
            entryRegex.findAll(content).forEach { m ->
                val name = unescapePdfString(m.groupValues[1])
                val directPageObj = m.groupValues[2].toIntOrNull()
                if (directPageObj != null) {
                    pageNumByObj[directPageObj]?.let { out[name] = it }
                } else {
                    val destObj = m.groupValues[3].toIntOrNull()
                    val destBody = destObj?.let { objects[it] }
                    if (destBody != null) {
                        resolveDestBodyToPage(destBody, pageNumByObj)?.let { out[name] = it }
                    }
                }
            }
        }
    }

    private fun resolveAnnotAction(
        annotBody: String,
        objects: Map<Int, String>,
        pageNumByObj: Map<Int, Int>,
        namedDests: Map<String, Int>,
        currentPageIndex: Int,
        totalPages: Int
    ): PdfLinkAction? {
        // Direct /Dest on annotation
        extractDestTarget(annotBody, "/Dest", objects, pageNumByObj, namedDests)?.let {
            return PdfLinkAction.GoToPage(it)
        }

        // /A action (either inline dictionary or indirect reference)
        val actionRef = extractIndirectRef(annotBody, "/A")
        val actionBody = if (actionRef != null) {
            objects[actionRef]
        } else {
            extractInlineActionDict(annotBody)
        } ?: return null

        val actionType = Regex("""/S\s*/([A-Za-z]+)""").find(actionBody)?.groupValues?.get(1)
        return when (actionType) {
            "GoTo" -> {
                extractDestTarget(actionBody, "/D", objects, pageNumByObj, namedDests)
                    ?.let { PdfLinkAction.GoToPage(it) }
            }
            "URI" -> {
                extractPdfString(actionBody, "/URI")?.let { PdfLinkAction.OpenUri(it) }
            }
            "Named" -> {
                val named = Regex("""/N\s*/([A-Za-z]+)""").find(actionBody)?.groupValues?.get(1)
                when (named) {
                    "NextPage" -> PdfLinkAction.GoToPage((currentPageIndex + 1).coerceAtMost(totalPages - 1))
                    "PrevPage" -> PdfLinkAction.GoToPage((currentPageIndex - 1).coerceAtLeast(0))
                    "FirstPage" -> PdfLinkAction.GoToPage(0)
                    "LastPage" -> PdfLinkAction.GoToPage((totalPages - 1).coerceAtLeast(0))
                    else -> null
                }
            }
            else -> null
        }
    }

    private fun extractInlineActionDict(annotBody: String): String? {
        val idx = Regex("""/A\s*<<""").find(annotBody)?.range?.last ?: return null
        var depth = 0
        var i = idx - 1
        while (i < annotBody.length - 1) {
            if (annotBody[i] == '<' && annotBody[i + 1] == '<') {
                depth++
                i += 2
            } else if (annotBody[i] == '>' && annotBody[i + 1] == '>') {
                depth--
                i += 2
                if (depth <= 0) {
                    return annotBody.substring(idx - 1, i)
                }
            } else {
                i++
            }
        }
        return annotBody.substring(idx - 1)
    }

    private fun extractDestTarget(
        body: String,
        key: String,
        objects: Map<Int, String>,
        pageNumByObj: Map<Int, Int>,
        namedDests: Map<String, Int>
    ): Int? {
        // 1. Array: /Dest [12 0 R /Fit]
        val arrMatch = Regex("""$key\s*\[\s*(\d+)\s+\d+\s+R""").find(body)
        if (arrMatch != null) {
            val obj = arrMatch.groupValues[1].toIntOrNull()
            if (obj != null) return pageNumByObj[obj]
        }
        // 2. Literal string named dest: /D (section.1)
        val strMatch = Regex("""$key\s*\(([^)]*)\)""").find(body)
        if (strMatch != null) {
            val name = unescapePdfString(strMatch.groupValues[1])
            namedDests[name]?.let { return it }
        }
        // 3. Name dest: /D /section1
        val nameMatch = Regex("""$key\s*/([A-Za-z0-9_.:+-]+)""").find(body)
        if (nameMatch != null) {
            namedDests[nameMatch.groupValues[1]]?.let { return it }
        }
        // 4. Indirect ref to destination array/dict
        val ref = extractIndirectRef(body, key)
        if (ref != null) {
            val refBody = objects[ref]
            if (refBody != null) {
                resolveDestBodyToPage(refBody, pageNumByObj)?.let { return it }
            }
        }
        return null
    }

    private fun resolveDestBodyToPage(destBody: String, pageNumByObj: Map<Int, Int>): Int? {
        val m = Regex("""\[\s*(\d+)\s+\d+\s+R""").find(destBody) ?: return null
        val obj = m.groupValues[1].toIntOrNull() ?: return null
        return pageNumByObj[obj]
    }

    private fun extractIndirectRef(body: String, key: String): Int? {
        return Regex("""$key\s+(\d+)\s+\d+\s+R""").find(body)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun extractFloatArray(body: String, key: String, expectedCount: Int): FloatArray? {
        val match = Regex("""$key\s*\[\s*([^\]]+)]""").find(body) ?: return null
        val nums = Regex("""-?\d+(?:\.\d+)?""").findAll(match.groupValues[1])
            .mapNotNull { it.value.toFloatOrNull() }
            .toList()
        if (nums.size < expectedCount) return null
        return FloatArray(expectedCount) { nums[it] }
    }

    private fun extractPdfString(body: String, key: String): String? {
        val litMatch = Regex("""$key\s*\(((?:\\.|[^\\)])*)\)""").find(body)
        if (litMatch != null) {
            return unescapePdfString(litMatch.groupValues[1])
        }
        val hexMatch = Regex("""$key\s*<([0-9A-Fa-f\s]+)>""").find(body)
        if (hexMatch != null) {
            val clean = hexMatch.groupValues[1].replace(Regex("\\s+"), "")
            val bytes = ByteArray(clean.length / 2) { idx ->
                clean.substring(idx * 2, idx * 2 + 2).toInt(16).toByte()
            }
            return String(bytes, StandardCharsets.UTF_8)
        }
        return null
    }

    private fun unescapePdfString(raw: String): String {
        val sb = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c == '\\' && i + 1 < raw.length) {
                when (val next = raw[i + 1]) {
                    'n' -> { sb.append('\n'); i += 2 }
                    'r' -> { sb.append('\r'); i += 2 }
                    't' -> { sb.append('\t'); i += 2 }
                    '\\', '(', ')' -> { sb.append(next); i += 2 }
                    else -> { sb.append(next); i += 2 }
                }
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }
}

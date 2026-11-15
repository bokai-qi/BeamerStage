package com.example.pdf

import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.os.Build
import com.example.model.BeamerLayoutMode
import com.example.model.NormalizedRect
import com.example.model.PdfLinkAction
import com.example.model.PdfSlideLink
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.zip.Inflater

/**
 * Extracts clickable internal GoTo and external URI links from PDF pages.
 * Uses Android V (API 35+) native PdfRenderer link APIs when available, and
 * falls back to a streaming low-memory PDF object/annotation parser for API 24-34.
 */
object PdfLinkExtractor {

    private const val MAX_DICT_BYTES = 1024 * 1024
    private const val MAX_OBJSTM_INPUT_BYTES = 4 * 1024 * 1024
    private const val MAX_OBJSTM_OUTPUT_BYTES = 8 * 1024 * 1024
    private const val MAX_PAGE_TREE_DEPTH = 64

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

    fun mergeSlideLinks(
        platformLinks: List<PdfSlideLink>,
        parsedLinks: List<PdfSlideLink>
    ): List<PdfSlideLink> {
        if (platformLinks.isEmpty()) return parsedLinks
        if (parsedLinks.isEmpty()) return platformLinks
        val merged = platformLinks.toMutableList()
        for (candidate in parsedLinks) {
            val isDuplicate = merged.any { existing ->
                existing.action == candidate.action &&
                    boundsOverlapOrMatch(existing.bounds, candidate.bounds)
            }
            if (!isDuplicate) {
                merged.add(candidate)
            }
        }
        return merged
    }

    private fun boundsOverlapOrMatch(
        first: List<NormalizedRect>,
        second: List<NormalizedRect>
    ): Boolean {
        for (a in first) {
            for (b in second) {
                val interLeft = maxOf(a.left, b.left)
                val interTop = maxOf(a.top, b.top)
                val interRight = minOf(a.right, b.right)
                val interBottom = minOf(a.bottom, b.bottom)
                if (interRight > interLeft && interBottom > interTop) {
                    val interArea = (interRight - interLeft) * (interBottom - interTop)
                    val areaA = (a.right - a.left) * (a.bottom - a.top)
                    val areaB = (b.right - b.left) * (b.bottom - b.top)
                    val minArea = minOf(areaA, areaB).coerceAtLeast(1e-6f)
                    if (interArea / minArea >= 0.25f) {
                        return true
                    }
                }
                val centerAX = (a.left + a.right) * 0.5f
                val centerAY = (a.top + a.bottom) * 0.5f
                if (b.contains(centerAX, centerAY, slop = 0.015f)) {
                    return true
                }
            }
        }
        return false
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

    fun extractLinksFromPdfBytes(pdfBytes: ByteArray): Map<Int, List<RawPageLink>> {
        return extractLinksFromStream(ByteArrayInputStream(pdfBytes))
    }

    /**
     * Streaming low-memory PDF parser for link annotations (/Subtype /Link).
     * Skips binary image/font/content streams in-place without loading the PDF into RAM,
     * while expanding /ObjStm object streams and resolving /GoTo, /URI, and /Named actions.
     */
    fun extractLinksFromStream(inputStream: InputStream): Map<Int, List<RawPageLink>> {
        val scan = scanPdfObjects(inputStream)
        val objects = scan.objects
        if (objects.isEmpty()) return emptyMap()

        val rootRef = scan.rootObjNum ?: findCatalogObjectNumber(objects) ?: return emptyMap()
        val catalogBody = objects[rootRef] ?: return emptyMap()

        val pagesRootRef = extractIndirectRef(catalogBody, "/Pages") ?: return emptyMap()
        val orderedPages = mutableListOf<PageNode>()
        collectPages(
            nodeObjNum = pagesRootRef,
            objects = objects,
            inheritedMediaBox = floatArrayOf(0f, 0f, 612f, 792f),
            out = orderedPages,
            visited = HashSet(),
            depth = 0
        )
        if (orderedPages.isEmpty()) return emptyMap()

        val pageNumByObj = HashMap<Int, Int>(orderedPages.size * 2)
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

    private data class ScanResult(
        val objects: Map<Int, String>,
        val rootObjNum: Int?
    )

    private fun scanPdfObjects(rawStream: InputStream): ScanResult {
        val stream = if (rawStream is BufferedInputStream) rawStream else BufferedInputStream(rawStream, 65536)
        val objects = HashMap<Int, String>()
        var lastRootRef: Int? = null

        val objHeaderRegex = Regex("""^\s*(\d+)\s+\d+\s+obj(?=\s|<<|\[|/|\(|$)([\s\S]*)$""")
        val rootRefRegex = Regex("""/Root\s+(\d+)\s+\d+\s+R""")
        val directLengthRegex = Regex("""/Length\s+(\d+)(?![0-9])(?!\s+\d+\s+R)""")

        val lineBuf = ByteArrayOutputStream(256)
        val objBuf = ByteArrayOutputStream(2048)

        // Fixed 16-byte circular buffer to detect `endobj` and `stream\n` / `stream\r` with 0 heap allocations
        val tailRing = ByteArray(16)
        var tailCount = 0

        var inObj = false
        var currentObjNum = -1
        var objTruncated = false

        fun resetObjectState() {
            inObj = false
            currentObjNum = -1
            objTruncated = false
            tailCount = 0
            lineBuf.reset()
            objBuf.reset()
        }

        fun pushTailByte(b: Int) {
            tailRing[tailCount and 15] = b.toByte()
            tailCount++
        }

        fun tailByteFromEnd(offsetFromEnd: Int): Int {
            // offsetFromEnd = 0 is most recent byte
            val idx = (tailCount - 1 - offsetFromEnd) and 15
            return tailRing[idx].toInt() and 0xFF
        }

        fun matchesEndObjAtDelimiter(): Boolean {
            if (tailCount < 7) return false
            // tailByteFromEnd(0) is delimiter (' ', '\t', '\r', '\n', '>', ']', '<', '/')
            // preceding 6 bytes must be "endobj" and byte before that (if any) must not be alphanumeric
            val match = tailByteFromEnd(1) == 'j'.code &&
                tailByteFromEnd(2) == 'b'.code &&
                tailByteFromEnd(3) == 'o'.code &&
                tailByteFromEnd(4) == 'd'.code &&
                tailByteFromEnd(5) == 'n'.code &&
                tailByteFromEnd(6) == 'e'.code
            if (!match) return false
            if (tailCount == 7) return true
            val before = tailByteFromEnd(7)
            return isKeywordBoundary(before)
        }

        fun matchesStreamEol(eolByte: Int): Boolean {
            if (tailCount < 7) return false
            if (tailByteFromEnd(0) != eolByte) return false
            val match = tailByteFromEnd(1) == 'm'.code &&
                tailByteFromEnd(2) == 'a'.code &&
                tailByteFromEnd(3) == 'e'.code &&
                tailByteFromEnd(4) == 'r'.code &&
                tailByteFromEnd(5) == 't'.code &&
                tailByteFromEnd(6) == 's'.code
            if (!match) return false
            return tailCount == 7 || isStreamPrefixDelimiter(tailByteFromEnd(7).toByte())
        }

        fun finishObject() {
            if (!objTruncated && objBuf.size() > 0) {
                val full = String(objBuf.toByteArray(), StandardCharsets.ISO_8859_1)
                val endIdx = full.lastIndexOf("endobj")
                val body = (if (endIdx >= 0) full.substring(0, endIdx) else full).trim()
                rootRefRegex.findAll(body).lastOrNull()?.groupValues?.get(1)?.toIntOrNull()?.let {
                    lastRootRef = it
                }
                if (isRelevantMetadataObject(body)) {
                    objects[currentObjNum] = body
                }
            }
            resetObjectState()
        }

        fun feedObjectByte(byteInt: Int): Boolean {
            if (objBuf.size() < MAX_DICT_BYTES) {
                objBuf.write(byteInt)
            } else {
                objTruncated = true
            }
            pushTailByte(byteInt)

            if (byteInt == '\n'.code || byteInt == '\r'.code || byteInt == ' '.code ||
                byteInt == '\t'.code || byteInt == '>'.code || byteInt == ']'.code || byteInt == '<'.code
            ) {
                if (matchesEndObjAtDelimiter()) {
                    finishObject()
                    return true
                }
            }

            if (byteInt == '\n'.code || byteInt == '\r'.code) {
                val endsWithStreamLf = byteInt == '\n'.code && matchesStreamEol('\n'.code)
                val endsWithStreamCr = !endsWithStreamLf && byteInt == '\r'.code && matchesStreamEol('\r'.code)
                if (endsWithStreamLf || endsWithStreamCr) {
                    if (endsWithStreamCr) {
                        stream.mark(1)
                        val next = stream.read()
                        if (next != '\n'.code && next >= 0) {
                            stream.reset()
                        }
                    }
                    val curBytes = objBuf.toByteArray()
                    val headerBytesLen = (curBytes.size - 7).coerceAtLeast(0)
                    val headerBody = String(curBytes, 0, headerBytesLen, StandardCharsets.ISO_8859_1).trim()
                    rootRefRegex.findAll(headerBody).lastOrNull()?.groupValues?.get(1)?.toIntOrNull()?.let {
                        lastRootRef = it
                    }
                    if (!objTruncated && isRelevantMetadataObject(headerBody)) {
                        objects[currentObjNum] = headerBody
                    }

                    val isObjStm = headerBody.contains("/ObjStm")
                    val directLength = directLengthRegex.find(headerBody)?.groupValues?.get(1)?.toLongOrNull()

                    if (isObjStm) {
                        val streamBytes = readStreamUpToEndstream(
                            stream = stream,
                            directLength = directLength,
                            maxBytes = MAX_OBJSTM_INPUT_BYTES
                        )
                        if (streamBytes != null) {
                            parseObjStmBytes(headerBody, streamBytes, objects)
                        }
                    } else {
                        skipStreamUpToEndstream(stream, directLength)
                    }

                    skipUntilEndObj(stream)
                    resetObjectState()
                    return true
                }
            }
            return false
        }

        fun processNonObjByte(b: Int) {
            if (b == '\n'.code || b == '\r'.code) {
                if (lineBuf.size() > 0) {
                    val line = lineBuf.toString(StandardCharsets.ISO_8859_1.name())
                    lineBuf.reset()
                    rootRefRegex.findAll(line).lastOrNull()?.groupValues?.get(1)?.toIntOrNull()?.let {
                        lastRootRef = it
                    }
                    val match = objHeaderRegex.find(line)
                    if (match != null) {
                        currentObjNum = match.groupValues[1].toIntOrNull() ?: -1
                        if (currentObjNum >= 0) {
                            inObj = true
                            objTruncated = false
                            tailCount = 0
                            objBuf.reset()
                            val remainderBytes = match.groupValues[2].toByteArray(StandardCharsets.ISO_8859_1)
                            var idx = 0
                            while (idx < remainderBytes.size) {
                                val rb = remainderBytes[idx].toInt() and 0xFF
                                idx++
                                if (inObj) {
                                    feedObjectByte(rb)
                                } else {
                                    processNonObjByte(rb)
                                }
                            }
                            if (inObj) {
                                feedObjectByte(b)
                            }
                        }
                    }
                }
            } else {
                if (lineBuf.size() < 4096) {
                    lineBuf.write(b)
                } else {
                    val tail = lineBuf.toByteArray()
                    lineBuf.reset()
                    lineBuf.write(tail, tail.size - 512, 512)
                    lineBuf.write(b)
                }
            }
        }

        while (true) {
            val b = stream.read()
            if (b < 0) break
            if (!inObj) {
                processNonObjByte(b)
            } else {
                feedObjectByte(b)
            }
        }

        if (inObj && objBuf.size() > 0) {
            finishObject()
        } else if (lineBuf.size() > 0) {
            val tailLine = lineBuf.toString(StandardCharsets.ISO_8859_1.name())
            rootRefRegex.findAll(tailLine).lastOrNull()?.groupValues?.get(1)?.toIntOrNull()?.let {
                lastRootRef = it
            }
        }

        return ScanResult(objects, lastRootRef)
    }

    private fun isKeywordBoundary(c: Int): Boolean {
        return c == ' '.code || c == '\t'.code || c == '\r'.code || c == '\n'.code ||
            c == '>'.code || c == ']'.code || c == ')'.code || c == '/'.code
    }

    private fun isStreamPrefixDelimiter(byte: Byte): Boolean {
        val c = byte.toInt() and 0xFF
        return c == ' '.code || c == '\t'.code || c == '\r'.code || c == '\n'.code || c == '>'.code
    }

    private val destKeyRegex = Regex("""/D(?:\s|\[|\(|/)""")

    private fun isRelevantMetadataObject(body: String): Boolean {
        val trimmed = body.trimStart()
        if (trimmed.startsWith("[")) {
            return trimmed.contains(" R")
        }
        return body.contains("/Catalog") ||
            body.contains("/Pages") ||
            body.contains("/Page") ||
            body.contains("/Link") ||
            body.contains("/Annots") ||
            body.contains("/Names") ||
            body.contains("/Dests") ||
            body.contains("/GoTo") ||
            body.contains("/URI") ||
            body.contains("/Named") ||
            body.contains("/Dest") ||
            body.contains("/Kids") ||
            (body.contains(" R") && destKeyRegex.containsMatchIn(body))
    }

    private fun skipStreamUpToEndstream(stream: BufferedInputStream, directLength: Long?) {
        if (directLength != null && directLength > 0L) {
            skipFully(stream, directLength)
        }
        // Scan until `endstream` (only reads remaining padding or scans binary stream with 0 heap allocations)
        val target = byteArrayOf(
            'e'.code.toByte(), 'n'.code.toByte(), 'd'.code.toByte(),
            's'.code.toByte(), 't'.code.toByte(), 'r'.code.toByte(),
            'e'.code.toByte(), 'a'.code.toByte(), 'm'.code.toByte()
        )
        var matched = 0
        while (true) {
            val b = stream.read()
            if (b < 0) return
            val byteVal = b.toByte()
            if (byteVal == target[matched]) {
                matched++
                if (matched == target.size) return
            } else {
                matched = if (byteVal == target[0]) 1 else 0
            }
        }
    }

    private fun skipFully(stream: InputStream, bytesToSkip: Long) {
        var remaining = bytesToSkip
        val discard = ByteArray(8192)
        while (remaining > 0L) {
            val skipped = stream.skip(remaining)
            if (skipped > 0L) {
                remaining -= skipped
            } else {
                val read = stream.read(discard, 0, minOf(remaining, discard.size.toLong()).toInt())
                if (read < 0) return
                remaining -= read
            }
        }
    }

    private fun readStreamUpToEndstream(
        stream: BufferedInputStream,
        directLength: Long?,
        maxBytes: Int
    ): ByteArray? {
        if (directLength != null && directLength in 1..maxBytes) {
            val len = directLength.toInt()
            val out = ByteArray(len)
            var offset = 0
            while (offset < len) {
                val read = stream.read(out, offset, len - offset)
                if (read < 0) break
                offset += read
            }
            skipStreamUpToEndstream(stream, null)
            return if (offset == len) out else out.copyOf(offset)
        }
        if (directLength != null && directLength > maxBytes) {
            skipStreamUpToEndstream(stream, directLength)
            return null
        }

        val baos = ByteArrayOutputStream(4096)
        val target = byteArrayOf(
            'e'.code.toByte(), 'n'.code.toByte(), 'd'.code.toByte(),
            's'.code.toByte(), 't'.code.toByte(), 'r'.code.toByte(),
            'e'.code.toByte(), 'a'.code.toByte(), 'm'.code.toByte()
        )
        var matched = 0
        while (true) {
            val b = stream.read()
            if (b < 0) return null
            if (baos.size() >= maxBytes + target.size) {
                skipStreamUpToEndstream(stream, null)
                return null
            }
            baos.write(b)
            val byteVal = b.toByte()
            if (byteVal == target[matched]) {
                matched++
                if (matched == target.size) {
                    val raw = baos.toByteArray()
                    var dataEnd = raw.size - target.size
                    // Strip only the single EOL separator preceding `endstream`
                    if (dataEnd > 0 && raw[dataEnd - 1] == '\n'.code.toByte()) {
                        dataEnd--
                        if (dataEnd > 0 && raw[dataEnd - 1] == '\r'.code.toByte()) {
                            dataEnd--
                        }
                    } else if (dataEnd > 0 && raw[dataEnd - 1] == '\r'.code.toByte()) {
                        dataEnd--
                    }
                    return raw.copyOf(dataEnd)
                }
            } else {
                matched = if (byteVal == target[0]) 1 else 0
            }
        }
    }

    private fun skipUntilEndObj(stream: BufferedInputStream) {
        val target = byteArrayOf(
            'e'.code.toByte(), 'n'.code.toByte(), 'd'.code.toByte(),
            'o'.code.toByte(), 'b'.code.toByte(), 'j'.code.toByte()
        )
        var matched = 0
        var count = 0
        while (count < 256) {
            val b = stream.read()
            if (b < 0) return
            count++
            val byteVal = b.toByte()
            if (byteVal == target[matched]) {
                matched++
                if (matched == target.size) return
            } else {
                matched = if (byteVal == target[0]) 1 else 0
            }
        }
    }

    private fun parseObjStmBytes(
        headerBody: String,
        streamBytes: ByteArray,
        objects: MutableMap<Int, String>
    ) {
        val nMatch = Regex("""/N\s+(\d+)""").find(headerBody) ?: return
        val firstMatch = Regex("""/First\s+(\d+)""").find(headerBody) ?: return
        val count = nMatch.groupValues[1].toIntOrNull() ?: return
        val firstOffset = firstMatch.groupValues[1].toIntOrNull() ?: return

        val decodedBytes = if (headerBody.contains("/FlateDecode")) {
            inflateBytes(streamBytes, MAX_OBJSTM_OUTPUT_BYTES) ?: return
        } else {
            streamBytes
        }
        val decoded = String(decodedBytes, StandardCharsets.ISO_8859_1)
        if (firstOffset <= 0 || firstOffset > decoded.length) return

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
                val objBody = decoded.substring(start, end).trim()
                if (isRelevantMetadataObject(objBody)) {
                    objects[objId] = objBody
                }
            }
        }
    }

    private fun inflateBytes(input: ByteArray, maxOutputBytes: Int = MAX_OBJSTM_OUTPUT_BYTES): ByteArray? {
        val inflater = Inflater()
        return try {
            inflater.setInput(input)
            val baos = ByteArrayOutputStream()
            val buf = ByteArray(4096)
            var zeroReads = 0
            while (!inflater.finished() && baos.size() < maxOutputBytes) {
                val read = inflater.inflate(buf)
                if (read == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) break
                    zeroReads++
                    if (zeroReads > 4) break
                } else {
                    zeroReads = 0
                    val allowed = minOf(read, maxOutputBytes - baos.size())
                    baos.write(buf, 0, allowed)
                }
            }
            baos.toByteArray()
        } catch (_: Throwable) {
            null
        } finally {
            inflater.end()
        }
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
        out: MutableList<PageNode>,
        visited: MutableSet<Int>,
        depth: Int
    ) {
        if (depth > MAX_PAGE_TREE_DEPTH || !visited.add(nodeObjNum)) return
        val body = objects[nodeObjNum] ?: return
        val mediaBox = extractFloatArray(body, "/MediaBox", 4) ?: inheritedMediaBox
        val cropBox = extractFloatArray(body, "/CropBox", 4) ?: mediaBox

        val kidsContent = extractBracketedContent(body, "/Kids")
        val isLeafPage = Regex("""/Type\s*/Page\b""").containsMatchIn(body)
        if (kidsContent != null && !isLeafPage) {
            val kidRefs = Regex("""(\d+)\s+\d+\s+R""").findAll(kidsContent)
                .mapNotNull { it.groupValues[1].toIntOrNull() }
            for (kid in kidRefs) {
                collectPages(kid, objects, cropBox, out, visited, depth + 1)
            }
        } else if (isLeafPage) {
            out.add(PageNode(nodeObjNum, cropBox))
        }
    }

    private fun extractAnnotsRefs(pageBody: String, objects: Map<Int, String>): List<Int> {
        val directArray = extractBracketedContent(pageBody, "/Annots")
        if (directArray != null) {
            return Regex("""(\d+)\s+\d+\s+R""").findAll(directArray)
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
        val destsRef = extractIndirectRef(catalogBody, "/Dests")
        if (destsRef != null) {
            objects[destsRef]?.let { parseDestsDictEntries(it, objects, pageNumByObj, result) }
        } else {
            extractInlineDict(catalogBody, "/Dests")?.let {
                parseDestsDictEntries(it, objects, pageNumByObj, result)
            }
        }
        val namesRef = extractIndirectRef(catalogBody, "/Names")
        val namesBody = if (namesRef != null) objects[namesRef] else extractInlineDict(catalogBody, "/Names")
        if (namesBody != null) {
            val nameTreeRef = extractIndirectRef(namesBody, "/Dests")
            if (nameTreeRef != null) {
                traverseNameTree(nameTreeRef, objects, pageNumByObj, result, HashSet(), 0)
            } else {
                extractInlineDict(namesBody, "/Dests")?.let { inlineTree ->
                    traverseNameTreeBody(inlineTree, objects, pageNumByObj, result, HashSet(), 0)
                }
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
        out: MutableMap<String, Int>,
        visited: MutableSet<Int>,
        depth: Int
    ) {
        if (depth > MAX_PAGE_TREE_DEPTH || !visited.add(nodeObj)) return
        val body = objects[nodeObj] ?: return
        traverseNameTreeBody(body, objects, pageNumByObj, out, visited, depth)
    }

    private fun traverseNameTreeBody(
        body: String,
        objects: Map<Int, String>,
        pageNumByObj: Map<Int, Int>,
        out: MutableMap<String, Int>,
        visited: MutableSet<Int>,
        depth: Int
    ) {
        val kidsContent = extractBracketedContent(body, "/Kids")
        if (kidsContent != null) {
            Regex("""(\d+)\s+\d+\s+R""").findAll(kidsContent).forEach { m ->
                m.groupValues[1].toIntOrNull()?.let {
                    traverseNameTree(it, objects, pageNumByObj, out, visited, depth + 1)
                }
            }
        }
        val namesContent = extractBracketedContent(body, "/Names")
        if (namesContent != null) {
            val entryRegex = Regex(
                """\(((?:\\.|[^\\)])*)\)\s*(?:\[\s*(\d+)\s+\d+\s+R[^\]]*]|<<[^>]*?/D\s*\[\s*(\d+)\s+\d+\s+R|(\d+)\s+\d+\s+R)"""
            )
            entryRegex.findAll(namesContent).forEach { m ->
                val name = unescapePdfString(m.groupValues[1])
                val directPageObj = m.groupValues[2].toIntOrNull() ?: m.groupValues[3].toIntOrNull()
                if (directPageObj != null) {
                    pageNumByObj[directPageObj]?.let { out[name] = it }
                } else {
                    val destObj = m.groupValues[4].toIntOrNull()
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
        extractDestTarget(annotBody, "/Dest", objects, pageNumByObj, namedDests)?.let {
            return PdfLinkAction.GoToPage(it)
        }

        val actionRef = extractIndirectRef(annotBody, "/A")
        val actionBody = if (actionRef != null) {
            objects[actionRef]
        } else {
            extractInlineDict(annotBody, "/A")
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
                    "NextPage" -> PdfLinkAction.GoToPage((currentPageIndex + 1).coerceAtMost((totalPages - 1).coerceAtLeast(0)))
                    "PrevPage" -> PdfLinkAction.GoToPage((currentPageIndex - 1).coerceAtLeast(0))
                    "FirstPage" -> PdfLinkAction.GoToPage(0)
                    "LastPage" -> PdfLinkAction.GoToPage((totalPages - 1).coerceAtLeast(0))
                    else -> null
                }
            }
            else -> null
        }
    }

    private fun extractBracketedContent(body: String, key: String): String? {
        val m = Regex("""$key\s*\[""").find(body) ?: return null
        val start = m.range.last + 1
        var depth = 1
        var inString = false
        var escaped = false
        var i = start
        while (i < body.length) {
            val c = body[i]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (c == '\\') {
                    escaped = true
                } else if (c == ')') {
                    inString = false
                }
            } else {
                when (c) {
                    '(' -> inString = true
                    '[' -> depth++
                    ']' -> {
                        depth--
                        if (depth == 0) {
                            return body.substring(start, i)
                        }
                    }
                }
            }
            i++
        }
        return null
    }

    private fun extractInlineDict(body: String, key: String): String? {
        val idx = Regex("""$key\s*<<""").find(body)?.range?.last ?: return null
        var depth = 0
        var i = idx - 1
        while (i < body.length - 1) {
            if (body[i] == '<' && body[i + 1] == '<') {
                depth++
                i += 2
            } else if (body[i] == '>' && body[i + 1] == '>') {
                depth--
                i += 2
                if (depth <= 0) {
                    return body.substring(idx - 1, i)
                }
            } else {
                i++
            }
        }
        return body.substring(idx - 1)
    }

    private fun extractDestTarget(
        body: String,
        key: String,
        objects: Map<Int, String>,
        pageNumByObj: Map<Int, Int>,
        namedDests: Map<String, Int>
    ): Int? {
        val arrMatch = Regex("""$key\s*\[\s*(\d+)\s+\d+\s+R""").find(body)
        if (arrMatch != null) {
            val obj = arrMatch.groupValues[1].toIntOrNull()
            if (obj != null) return pageNumByObj[obj]
        }
        val strMatch = Regex("""$key\s*\(((?:\\.|[^\\)])*)\)""").find(body)
        if (strMatch != null) {
            val name = unescapePdfString(strMatch.groupValues[1])
            namedDests[name]?.let { return it }
        }
        val nameMatch = Regex("""$key\s*/([A-Za-z0-9_.:+-]+)""").find(body)
        if (nameMatch != null) {
            namedDests[nameMatch.groupValues[1]]?.let { return it }
        }
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
            if (clean.length < 2) return null
            val bytes = ByteArray(clean.length / 2) { idx ->
                clean.substring(idx * 2, idx * 2 + 2).toIntOrNull(16)?.toByte() ?: 0
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

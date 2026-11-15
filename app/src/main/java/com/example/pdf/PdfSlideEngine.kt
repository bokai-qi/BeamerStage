package com.example.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import com.example.model.BeamerLayoutMode
import com.example.model.PdfSlideLink
import com.example.model.SlideBitmapPair
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import kotlin.math.roundToInt

class PdfSlideEngine(private val context: Context) {

    private val renderMutex = Mutex()
    private var openGeneration: Long = 0L
    private var currentUriString: String? = null
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var pdfRenderer: PdfRenderer? = null
    private var detectedDefaultLayout: BeamerLayoutMode = BeamerLayoutMode.SINGLE
    private var cachedRawLinksUri: String? = null
    private var cachedRawLinksByPage: Map<Int, List<PdfLinkExtractor.RawPageLink>> = emptyMap()

    // Adaptive LRU Bitmap Cache (up to 24 MB, scaled to 1/8th of max JVM heap)
    private val cacheBytesLimit = minOf(
        24 * 1024 * 1024,
        (Runtime.getRuntime().maxMemory() / 8L).toInt().coerceAtLeast(8 * 1024 * 1024)
    )
    private val bitmapCache = object : LruCache<String, Bitmap>(cacheBytesLimit) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private val linksCache = LruCache<String, List<PdfSlideLink>>(256)

    suspend fun openDocument(uriString: String): Int = withContext(Dispatchers.IO) {
        renderMutex.withLock {
            openGeneration++
            if (currentUriString == uriString && pdfRenderer != null) {
                return@withLock pdfRenderer!!.pageCount
            }
            closeLocked()
            val (pfd, renderer) = openRendererWithFallback(uriString)
            fileDescriptor = pfd
            pdfRenderer = renderer
            currentUriString = uriString

            detectedDefaultLayout = detectLayoutLocked(renderer)
            renderer.pageCount
        }
    }

    suspend fun inspectPageCountAndTitle(uriString: String): Pair<Int, BeamerLayoutMode> =
        withContext(Dispatchers.IO) {
            renderMutex.withLock {
                val (pfd, renderer) = openRendererWithFallback(uriString)
                try {
                    renderer.use { r ->
                        val count = r.pageCount
                        val autoMode = detectLayoutLocked(r)
                        count to autoMode
                    }
                } finally {
                    runCatching { pfd.close() }
                }
            }
        }

    suspend fun renderSlidePair(
        uriString: String,
        pageIndex: Int,
        requestedMode: BeamerLayoutMode,
        targetSlideWidthPx: Int = 1440,
        includeNotesBitmap: Boolean = true
    ): SlideBitmapPair = withContext(Dispatchers.IO) {
        renderMutex.withLock {
            ensureOpenLocked(uriString)
            val renderer = pdfRenderer ?: return@withLock SlideBitmapPair(null, null, BeamerLayoutMode.SINGLE)
            if (pageIndex < 0 || pageIndex >= renderer.pageCount) {
                return@withLock SlideBitmapPair(null, null, BeamerLayoutMode.SINGLE)
            }

            val effectiveMode = if (requestedMode == BeamerLayoutMode.AUTO) {
                detectedDefaultLayout
            } else {
                requestedMode
            }

            val slideKey = "$uriString:$pageIndex:${effectiveMode.name}:slide:$targetSlideWidthPx"
            val notesWidthPx = (targetSlideWidthPx * 0.65f).roundToInt().coerceAtLeast(560)
            val notesKey = "$uriString:$pageIndex:${effectiveMode.name}:notes:$notesWidthPx"
            val linksKey = "$uriString:$pageIndex:${effectiveMode.name}:links"

            val cachedSlide = bitmapCache.get(slideKey)
            val cachedNotes = if (includeNotesBitmap && effectiveMode != BeamerLayoutMode.SINGLE) {
                bitmapCache.get(notesKey)
            } else null
            val cachedLinks = linksCache.get(linksKey)

            if (cachedSlide != null &&
                cachedLinks != null &&
                (!includeNotesBitmap || effectiveMode == BeamerLayoutMode.SINGLE || cachedNotes != null)
            ) {
                return@withLock SlideBitmapPair(cachedSlide, cachedNotes, effectiveMode, cachedLinks)
            }

            renderer.openPage(pageIndex).use { page ->
                val slideBmp = cachedSlide ?: renderHalfOrFullSafe(
                    page = page,
                    cropHalf = when (effectiveMode) {
                        BeamerLayoutMode.SLIDE_LEFT_NOTES_RIGHT -> CropHalf.LEFT
                        BeamerLayoutMode.NOTES_LEFT_SLIDE_RIGHT -> CropHalf.RIGHT
                        else -> CropHalf.FULL
                    },
                    targetWidthPx = targetSlideWidthPx
                )?.also { bitmapCache.put(slideKey, it) }

                val notesBmp = if (includeNotesBitmap && effectiveMode != BeamerLayoutMode.SINGLE) {
                    cachedNotes ?: renderHalfOrFullSafe(
                        page = page,
                        cropHalf = when (effectiveMode) {
                            BeamerLayoutMode.SLIDE_LEFT_NOTES_RIGHT -> CropHalf.RIGHT
                            BeamerLayoutMode.NOTES_LEFT_SLIDE_RIGHT -> CropHalf.LEFT
                            else -> CropHalf.FULL
                        },
                        targetWidthPx = notesWidthPx
                    )?.also { bitmapCache.put(notesKey, it) }
                } else {
                    null
                }

                val links = cachedLinks ?: extractLinksLocked(uriString, page, pageIndex, effectiveMode)
                    .also { linksCache.put(linksKey, it) }

                SlideBitmapPair(slideBmp, notesBmp, effectiveMode, links)
            }
        }
    }

    suspend fun renderThumbnail(
        uriString: String,
        pageIndex: Int,
        requestedMode: BeamerLayoutMode,
        targetWidthPx: Int = 340
    ): Bitmap? = withContext(Dispatchers.IO) {
        renderMutex.withLock {
            ensureOpenLocked(uriString)
            val renderer = pdfRenderer ?: return@withLock null
            if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@withLock null

            val effectiveMode = if (requestedMode == BeamerLayoutMode.AUTO) {
                detectedDefaultLayout
            } else {
                requestedMode
            }

            val key = "$uriString:$pageIndex:${effectiveMode.name}:thumb:$targetWidthPx"
            bitmapCache.get(key)?.let { return@withLock it }

            renderer.openPage(pageIndex).use { page ->
                val cropHalf = when (effectiveMode) {
                    BeamerLayoutMode.SLIDE_LEFT_NOTES_RIGHT -> CropHalf.LEFT
                    BeamerLayoutMode.NOTES_LEFT_SLIDE_RIGHT -> CropHalf.RIGHT
                    else -> CropHalf.FULL
                }
                renderHalfOrFullSafe(page, cropHalf, targetWidthPx)?.also {
                    bitmapCache.put(key, it)
                }
            }
        }
    }

    suspend fun close() = withContext(Dispatchers.IO) {
        renderMutex.withLock {
            closeLocked()
        }
    }

    fun releaseSessionNonBlocking() {
        val targetGen = openGeneration
        bitmapCache.evictAll()
        linksCache.evictAll()
        CoroutineScope(Dispatchers.IO).launch {
            renderMutex.withLock {
                if (openGeneration == targetGen) {
                    closeLocked()
                }
            }
        }
    }

    private fun ensureOpenLocked(uriString: String) {
        if (currentUriString != uriString || pdfRenderer == null) {
            openGeneration++
            closeLocked()
            val (pfd, renderer) = openRendererWithFallback(uriString)
            fileDescriptor = pfd
            pdfRenderer = renderer
            currentUriString = uriString
            detectedDefaultLayout = detectLayoutLocked(renderer)
        }
    }

    private fun detectLayoutLocked(renderer: PdfRenderer): BeamerLayoutMode {
        return if (renderer.pageCount > 0) {
            runCatching {
                renderer.openPage(0).use { page ->
                    val ratio = page.width.toFloat() / page.height.coerceAtLeast(1).toFloat()
                    if (ratio >= 2.2f) BeamerLayoutMode.SLIDE_LEFT_NOTES_RIGHT else BeamerLayoutMode.SINGLE
                }
            }.getOrDefault(BeamerLayoutMode.SINGLE)
        } else {
            BeamerLayoutMode.SINGLE
        }
    }

    private fun openRendererWithFallback(uriString: String): Pair<ParcelFileDescriptor, PdfRenderer> {
        val pfd = openParcelFileDescriptor(uriString)
        if (pfd != null) {
            try {
                return pfd to PdfRenderer(pfd)
            } catch (err: Throwable) {
                runCatching { pfd.close() }
                // Fall back to caching stream locally if provider FD was non-seekable
                val cachedFile = copyUriToLocalCache(uriString)
                if (cachedFile != null) {
                    val localPfd = ParcelFileDescriptor.open(cachedFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    try {
                        return localPfd to PdfRenderer(localPfd)
                    } catch (innerErr: Throwable) {
                        runCatching { localPfd.close() }
                        throw innerErr
                    }
                }
                throw err
            }
        }

        val cachedFile = copyUriToLocalCache(uriString)
            ?: throw IllegalStateException("Unable to open PDF: $uriString")
        val localPfd = ParcelFileDescriptor.open(cachedFile, ParcelFileDescriptor.MODE_READ_ONLY)
        try {
            return localPfd to PdfRenderer(localPfd)
        } catch (err: Throwable) {
            runCatching { localPfd.close() }
            throw err
        }
    }

    private fun localCacheFileForUri(uriString: String): File {
        val cacheDir = File(context.cacheDir, "pdf_cache").apply { mkdirs() }
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(uriString.toByteArray())
            .take(12)
            .joinToString("") { "%02x".format(it) }
        return File(cacheDir, "$hash.pdf")
    }

    private fun copyUriToLocalCache(uriString: String): File? {
        return runCatching {
            val outFile = localCacheFileForUri(uriString)
            if (outFile.exists() && outFile.length() > 0L) {
                outFile.setLastModified(System.currentTimeMillis())
                prunePdfCache(outFile.parentFile, keepFile = outFile)
                return@runCatching outFile
            }
            openPdfInputStream(uriString)?.use { input ->
                FileOutputStream(outFile).use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buf)
                        if (read < 0) break
                        output.write(buf, 0, read)
                    }
                }
            } ?: return@runCatching null
            if (outFile.length() > 0L) {
                prunePdfCache(outFile.parentFile, keepFile = outFile)
                outFile
            } else {
                outFile.delete()
                null
            }
        }.getOrNull()
    }

    private fun prunePdfCache(cacheDir: File?, keepFile: File) {
        if (cacheDir == null || !cacheDir.exists()) return
        val files = cacheDir.listFiles()
            ?.filter { it.isFile && it.absolutePath != keepFile.absolutePath }
            ?.sortedByDescending { it.lastModified() }
            ?: return
        var runningBytes = keepFile.length()
        files.forEachIndexed { index, file ->
            runningBytes += file.length()
            if (index >= 2 || runningBytes > 80L * 1024L * 1024L) {
                file.delete()
            }
        }
    }

    private fun extractLinksLocked(
        uriString: String,
        page: PdfRenderer.Page,
        pageIndex: Int,
        effectiveLayout: BeamerLayoutMode
    ): List<PdfSlideLink> {
        val platformLinks = PdfLinkExtractor.extractFromPlatformPage(page, effectiveLayout)
        if (cachedRawLinksUri != uriString) {
            cachedRawLinksByPage = runCatching {
                val cachedFile = localCacheFileForUri(uriString)
                val stream = if (cachedFile.exists() && cachedFile.length() > 0L) {
                    FileInputStream(cachedFile)
                } else {
                    openPdfInputStream(uriString)
                }
                stream?.use { PdfLinkExtractor.extractLinksFromStream(it) } ?: emptyMap()
            }.getOrDefault(emptyMap())
            cachedRawLinksUri = uriString
        }
        val rawForPage = cachedRawLinksByPage[pageIndex].orEmpty()
        val parsedLinks = PdfLinkExtractor.mapRawLinksToSlide(pageIndex, rawForPage, effectiveLayout)
        return PdfLinkExtractor.mergeSlideLinks(platformLinks, parsedLinks)
    }

    private fun openPdfInputStream(uriString: String): InputStream? {
        return runCatching {
            val uri = Uri.parse(uriString)
            if (uri.scheme == "file") {
                val path = uri.path ?: return null
                FileInputStream(File(path))
            } else {
                context.contentResolver.openInputStream(uri)
            }
        }.getOrNull()
    }

    private fun closeLocked() {
        runCatching { pdfRenderer?.close() }
        runCatching { fileDescriptor?.close() }
        pdfRenderer = null
        fileDescriptor = null
        currentUriString = null
        cachedRawLinksUri = null
        cachedRawLinksByPage = emptyMap()
    }

    private fun openParcelFileDescriptor(uriString: String): ParcelFileDescriptor? {
        val uri = Uri.parse(uriString)
        return if (uri.scheme == "file") {
            val file = File(uri.path ?: return null)
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        } else {
            runCatching { context.contentResolver.openFileDescriptor(uri, "r") }.getOrNull()
        }
    }

    private enum class CropHalf { FULL, LEFT, RIGHT }

    private fun renderHalfOrFullSafe(
        page: PdfRenderer.Page,
        cropHalf: CropHalf,
        targetWidthPx: Int
    ): Bitmap? {
        return try {
            renderHalfOrFull(page, cropHalf, targetWidthPx)
        } catch (_: OutOfMemoryError) {
            bitmapCache.evictAll()
            System.gc()
            runCatching {
                renderHalfOrFull(page, cropHalf, (targetWidthPx / 2).coerceAtLeast(360))
            }.getOrNull()
        } catch (_: Throwable) {
            null
        }
    }

    private fun renderHalfOrFull(
        page: PdfRenderer.Page,
        cropHalf: CropHalf,
        targetWidthPx: Int
    ): Bitmap {
        val pageWidth = page.width.coerceAtLeast(1).toFloat()
        val pageHeight = page.height.coerceAtLeast(1).toFloat()
        val sourceWidth = if (cropHalf == CropHalf.FULL) pageWidth else pageWidth / 2f
        val maxW = targetWidthPx.coerceAtLeast(1)
        val maxH = 1920f
        val scale = minOf(maxW.toFloat() / sourceWidth, maxH / pageHeight).coerceAtLeast(0.05f)
        val outWidth = (sourceWidth * scale).roundToInt().coerceIn(1, maxW)
        val outHeight = (pageHeight * scale).roundToInt().coerceIn(1, 1920)

        val bitmap = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)

        val matrix = Matrix().apply {
            setScale(scale, scale)
            if (cropHalf == CropHalf.RIGHT) {
                postTranslate(-outWidth.toFloat(), 0f)
            }
        }

        page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        return bitmap
    }
}

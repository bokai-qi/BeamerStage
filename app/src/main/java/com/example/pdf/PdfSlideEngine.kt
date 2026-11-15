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
import com.example.model.SlideBitmapPair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

class PdfSlideEngine(private val context: Context) {

    private val renderMutex = Mutex()
    private var currentUriString: String? = null
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var pdfRenderer: PdfRenderer? = null
    private var detectedDefaultLayout: BeamerLayoutMode = BeamerLayoutMode.SINGLE

    // 32 MB LRU Bitmap Cache for crisp instant slide changes & thumbnails
    private val bitmapCache = object : LruCache<String, Bitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    suspend fun openDocument(uriString: String): Int = withContext(Dispatchers.IO) {
        renderMutex.withLock {
            if (currentUriString == uriString && pdfRenderer != null) {
                return@withLock pdfRenderer!!.pageCount
            }
            closeLocked()
            val pfd = openParcelFileDescriptor(uriString)
                ?: throw IllegalStateException("Unable to open PDF file")
            val renderer = PdfRenderer(pfd)
            fileDescriptor = pfd
            pdfRenderer = renderer
            currentUriString = uriString

            detectedDefaultLayout = if (renderer.pageCount > 0) {
                renderer.openPage(0).use { page ->
                    val ratio = page.width.toFloat() / page.height.coerceAtLeast(1).toFloat()
                    if (ratio >= 2.2f) BeamerLayoutMode.SLIDE_LEFT_NOTES_RIGHT else BeamerLayoutMode.SINGLE
                }
            } else {
                BeamerLayoutMode.SINGLE
            }
            renderer.pageCount
        }
    }

    suspend fun inspectPageCountAndTitle(uriString: String): Pair<Int, BeamerLayoutMode> =
        withContext(Dispatchers.IO) {
            renderMutex.withLock {
                val pfd = openParcelFileDescriptor(uriString)
                    ?: throw IllegalStateException("Cannot access selected PDF")
                PdfRenderer(pfd).use { renderer ->
                    val count = renderer.pageCount
                    val autoMode = if (count > 0) {
                        renderer.openPage(0).use { page ->
                            val ratio = page.width.toFloat() / page.height.coerceAtLeast(1).toFloat()
                            if (ratio >= 2.2f) BeamerLayoutMode.SLIDE_LEFT_NOTES_RIGHT else BeamerLayoutMode.SINGLE
                        }
                    } else {
                        BeamerLayoutMode.SINGLE
                    }
                    pfd.close()
                    count to autoMode
                }
            }
        }

    suspend fun renderSlidePair(
        uriString: String,
        pageIndex: Int,
        requestedMode: BeamerLayoutMode,
        targetSlideWidthPx: Int = 1600,
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
            val notesKey = "$uriString:$pageIndex:${effectiveMode.name}:notes:${targetSlideWidthPx / 2}"

            val cachedSlide = bitmapCache.get(slideKey)
            val cachedNotes = if (includeNotesBitmap && effectiveMode != BeamerLayoutMode.SINGLE) {
                bitmapCache.get(notesKey)
            } else null

            if (cachedSlide != null && (!includeNotesBitmap || effectiveMode == BeamerLayoutMode.SINGLE || cachedNotes != null)) {
                return@withLock SlideBitmapPair(cachedSlide, cachedNotes, effectiveMode)
            }

            renderer.openPage(pageIndex).use { page ->
                val slideBmp = cachedSlide ?: renderHalfOrFull(
                    page = page,
                    cropHalf = when (effectiveMode) {
                        BeamerLayoutMode.SLIDE_LEFT_NOTES_RIGHT -> CropHalf.LEFT
                        BeamerLayoutMode.NOTES_LEFT_SLIDE_RIGHT -> CropHalf.RIGHT
                        else -> CropHalf.FULL
                    },
                    targetWidthPx = targetSlideWidthPx
                ).also { bitmapCache.put(slideKey, it) }

                val notesBmp = if (includeNotesBitmap && effectiveMode != BeamerLayoutMode.SINGLE) {
                    cachedNotes ?: renderHalfOrFull(
                        page = page,
                        cropHalf = when (effectiveMode) {
                            BeamerLayoutMode.SLIDE_LEFT_NOTES_RIGHT -> CropHalf.RIGHT
                            BeamerLayoutMode.NOTES_LEFT_SLIDE_RIGHT -> CropHalf.LEFT
                            else -> CropHalf.FULL
                        },
                        targetWidthPx = (targetSlideWidthPx * 0.65f).roundToInt().coerceAtLeast(640)
                    ).also { bitmapCache.put(notesKey, it) }
                } else {
                    null
                }

                SlideBitmapPair(slideBmp, notesBmp, effectiveMode)
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
                renderHalfOrFull(page, cropHalf, targetWidthPx).also {
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

    private fun ensureOpenLocked(uriString: String) {
        if (currentUriString != uriString || pdfRenderer == null) {
            closeLocked()
            val pfd = openParcelFileDescriptor(uriString)
                ?: throw IllegalStateException("Unable to open PDF: $uriString")
            val renderer = PdfRenderer(pfd)
            fileDescriptor = pfd
            pdfRenderer = renderer
            currentUriString = uriString
            detectedDefaultLayout = if (renderer.pageCount > 0) {
                renderer.openPage(0).use { page ->
                    val ratio = page.width.toFloat() / page.height.coerceAtLeast(1).toFloat()
                    if (ratio >= 2.2f) BeamerLayoutMode.SLIDE_LEFT_NOTES_RIGHT else BeamerLayoutMode.SINGLE
                }
            } else {
                BeamerLayoutMode.SINGLE
            }
        }
    }

    private fun closeLocked() {
        runCatching { pdfRenderer?.close() }
        runCatching { fileDescriptor?.close() }
        pdfRenderer = null
        fileDescriptor = null
        currentUriString = null
    }

    private fun openParcelFileDescriptor(uriString: String): ParcelFileDescriptor? {
        val uri = Uri.parse(uriString)
        return if (uri.scheme == "file") {
            val file = File(uri.path ?: return null)
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        } else {
            context.contentResolver.openFileDescriptor(uri, "r")
        }
    }

    private enum class CropHalf { FULL, LEFT, RIGHT }

    private fun renderHalfOrFull(
        page: PdfRenderer.Page,
        cropHalf: CropHalf,
        targetWidthPx: Int
    ): Bitmap {
        val pageWidth = page.width.coerceAtLeast(1).toFloat()
        val pageHeight = page.height.coerceAtLeast(1).toFloat()
        val sourceWidth = if (cropHalf == CropHalf.FULL) pageWidth else pageWidth / 2f
        val scale = targetWidthPx.toFloat() / sourceWidth
        val outWidth = targetWidthPx.coerceAtLeast(1)
        val outHeight = (pageHeight * scale).roundToInt().coerceIn(1, 2400)

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

package com.example.display

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Display
import com.example.model.AnnotationStroke
import com.example.model.ExternalDisplayInfo
import com.example.model.LaserPointerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ExternalDisplayController(context: Context) {

    private val appContext = context.applicationContext
    private val displayManager = appContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _connectedDisplay = MutableStateFlow<ExternalDisplayInfo?>(null)
    val connectedDisplay: StateFlow<ExternalDisplayInfo?> = _connectedDisplay.asStateFlow()

    private var activeActivity: Activity? = null
    private var presentation: BeamerPresentation? = null
    private var activeDisplayId: Int? = null
    private var isPresenterSessionActive: Boolean = false

    private var lastBitmap: Bitmap? = null
    private var lastStrokes: List<AnnotationStroke> = emptyList()
    private var lastLaser: LaserPointerState = LaserPointerState()
    private var lastBlackout: Boolean = false

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = refreshDisplays()
        override fun onDisplayRemoved(displayId: Int) = refreshDisplays()
        override fun onDisplayChanged(displayId: Int) = refreshDisplays()
    }

    init {
        displayManager.registerDisplayListener(displayListener, mainHandler)
        refreshDisplays()
    }

    fun attachActivity(activity: Activity) {
        activeActivity = activity
        syncPresentationWindow()
    }

    fun detachActivity(activity: Activity) {
        if (activeActivity === activity) {
            dismissPresentation()
            activeActivity = null
        }
    }

    fun setPresenterSessionActive(active: Boolean) {
        isPresenterSessionActive = active
        syncPresentationWindow()
    }

    fun updateOutput(
        bitmap: Bitmap?,
        strokes: List<AnnotationStroke>,
        laserState: LaserPointerState,
        isBlackout: Boolean
    ) {
        lastBitmap = bitmap
        lastStrokes = strokes
        lastLaser = laserState
        lastBlackout = isBlackout
        presentation?.render(bitmap, strokes, laserState, isBlackout)
    }

    fun refreshDisplays() {
        val presentationDisplays = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        val primaryExternal = presentationDisplays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }

        _connectedDisplay.value = primaryExternal?.let { display ->
            val mode = display.mode
            val width = mode?.physicalWidth ?: run {
                val metrics = DisplayMetrics()
                @Suppress("DEPRECATION")
                display.getRealMetrics(metrics)
                metrics.widthPixels
            }
            val height = mode?.physicalHeight ?: run {
                val metrics = DisplayMetrics()
                @Suppress("DEPRECATION")
                display.getRealMetrics(metrics)
                metrics.heightPixels
            }
            val refresh = mode?.refreshRate ?: display.refreshRate
            ExternalDisplayInfo(
                displayId = display.displayId,
                name = display.name.ifBlank { "External DisplayPort" },
                width = width,
                height = height,
                refreshRate = refresh
            )
        }
        syncPresentationWindow()
    }

    private fun syncPresentationWindow() {
        val activity = activeActivity
        if (activity == null || activity.isFinishing || activity.isDestroyed || !isPresenterSessionActive) {
            dismissPresentation()
            return
        }

        val presentationDisplays = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        val targetDisplay = presentationDisplays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }

        if (targetDisplay == null) {
            dismissPresentation()
            return
        }

        if (presentation != null && activeDisplayId == targetDisplay.displayId && presentation?.isShowing == true) {
            presentation?.render(lastBitmap, lastStrokes, lastLaser, lastBlackout)
            return
        }

        dismissPresentation()
        runCatching {
            val pres = BeamerPresentation(activity, targetDisplay)
            pres.render(lastBitmap, lastStrokes, lastLaser, lastBlackout)
            pres.show()
            presentation = pres
            activeDisplayId = targetDisplay.displayId
        }
    }

    private fun dismissPresentation() {
        runCatching {
            if (presentation?.isShowing == true) {
                presentation?.dismiss()
            }
        }
        presentation = null
        activeDisplayId = null
    }

    fun release() {
        displayManager.unregisterDisplayListener(displayListener)
        dismissPresentation()
        activeActivity = null
    }
}

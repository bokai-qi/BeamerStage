package com.example.display

import android.app.Presentation
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Display
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.model.AnnotationStroke
import com.example.model.LaserPointerState

class BeamerPresentation(
    outerContext: Context,
    display: Display
) : Presentation(outerContext, display) {

    private var stageView: ProjectorStageView? = null
    private var pendingBitmap: Bitmap? = null
    private var pendingStrokes: List<AnnotationStroke> = emptyList()
    private var pendingLaser: LaserPointerState = LaserPointerState()
    private var pendingBlackout: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.BLACK))
            addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            WindowCompat.setDecorFitsSystemWindows(this, false)
            WindowInsetsControllerCompat(this, decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }

        val view = ProjectorStageView(context)
        stageView = view
        setContentView(view)
        view.updateStage(pendingBitmap, pendingStrokes, pendingLaser, pendingBlackout)
    }

    fun render(
        bitmap: Bitmap?,
        strokes: List<AnnotationStroke>,
        laserState: LaserPointerState,
        isBlackout: Boolean
    ) {
        pendingBitmap = bitmap
        pendingStrokes = strokes
        pendingLaser = laserState
        pendingBlackout = isBlackout
        stageView?.updateStage(bitmap, strokes, laserState, isBlackout)
    }
}

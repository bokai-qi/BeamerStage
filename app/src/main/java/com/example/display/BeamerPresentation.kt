package com.example.display

import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Display
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class BeamerPresentation(
    outerContext: Context,
    display: Display
) : Presentation(outerContext, display) {

    private var stageView: ProjectorStageView? = null
    private var pendingState: ProjectorRenderState = ProjectorRenderState()

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
        view.updateStage(pendingState)
    }

    fun render(state: ProjectorRenderState) {
        pendingState = state
        stageView?.updateStage(state)
    }
}

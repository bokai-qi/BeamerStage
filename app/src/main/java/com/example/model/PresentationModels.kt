package com.example.model

import android.graphics.Bitmap
import kotlinx.serialization.Serializable

enum class BeamerLayoutMode(
    val displayName: String,
    val badgeLabel: String,
    val description: String
) {
    AUTO(
        displayName = "Auto-Detect Beamer Layout",
        badgeLabel = "AUTO",
        description = "Detects 32:9 double-wide Beamer split-notes pages automatically; treats standard pages as full slides."
    ),
    SINGLE(
        displayName = "Single Full Slide",
        badgeLabel = "1:1 SLIDE",
        description = "Projects the entire PDF page as the presentation slide."
    ),
    SLIDE_LEFT_NOTES_RIGHT(
        displayName = "Slide Left · Notes Right",
        badgeLabel = "SLIDE | NOTE",
        description = "Standard LaTeX Beamer 'show notes on second screen=right'. Projects left half; displays right half in notes."
    ),
    NOTES_LEFT_SLIDE_RIGHT(
        displayName = "Notes Left · Slide Right",
        badgeLabel = "NOTE | SLIDE",
        description = "LaTeX Beamer 'show notes on second screen=left'. Projects right half; displays left half in notes."
    )
}

enum class InteractionTool(val label: String) {
    NAVIGATE("Navigate"),
    LASER("Laser"),
    PEN("Pen"),
    HIGHLIGHTER("Highlight")
}

enum class LaserColor(val colorHex: Long, val label: String) {
    CRIMSON(0xFFFF2D55, "Crimson"),
    EMERALD(0xFF10B981, "Green"),
    CYAN(0xFF38BDF8, "Cyan")
}

enum class PenColor(val colorHex: Long, val label: String) {
    CRIMSON(0xFFF43F5E, "Crimson"),
    SKY(0xFF38BDF8, "Sky"),
    AMBER(0xFFF59E0B, "Amber"),
    EMERALD(0xFF10B981, "Emerald"),
    WHITE(0xFFF8FAFC, "White")
}

@Serializable
data class NormalizedPoint(
    val x: Float,
    val y: Float
)

@Serializable
data class AnnotationStroke(
    val id: String,
    val points: List<NormalizedPoint>,
    val colorHex: Long,
    val strokeWidthRatio: Float,
    val isHighlighter: Boolean
)

data class LaserPointerState(
    val position: NormalizedPoint? = null,
    val trail: List<NormalizedPoint> = emptyList(),
    val color: LaserColor = LaserColor.CRIMSON,
    val isActive: Boolean = false
)

data class ExternalDisplayInfo(
    val displayId: Int,
    val name: String,
    val width: Int,
    val height: Int,
    val refreshRate: Float
) {
    val resolutionLabel: String get() = "${width}×${height}"
}

enum class AppThemeMode(val label: String) {
    SYSTEM("System"),
    DARK("Dark"),
    LIGHT("Light")
}

enum class DarkThemeStyle(val label: String) {
    STAGE("Stage Dark"),
    OLED("OLED Black")
}

data class UserPreferences(
    val volumeKeysNavigate: Boolean = true,
    val keepScreenOn: Boolean = true,
    val noteFontSizeSp: Float = 16f,
    val defaultLaserColor: LaserColor = LaserColor.CRIMSON,
    val showTimerBar: Boolean = true,
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val darkThemeStyle: DarkThemeStyle = DarkThemeStyle.STAGE,
    val simplePresentingMode: Boolean = false
)

@Serializable
data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    fun contains(x: Float, y: Float, slop: Float = 0.018f): Boolean {
        return x >= (left - slop) && x <= (right + slop) &&
            y >= (top - slop) && y <= (bottom + slop)
    }
}

sealed interface PdfLinkAction {
    data class GoToPage(val pageIndex: Int) : PdfLinkAction
    data class OpenUri(val uri: String) : PdfLinkAction
}

data class PdfSlideLink(
    val id: String,
    val bounds: List<NormalizedRect>,
    val action: PdfLinkAction
)

data class SlideBitmapPair(
    val slideBitmap: Bitmap?,
    val beamerNotesBitmap: Bitmap?,
    val effectiveLayout: BeamerLayoutMode,
    val links: List<PdfSlideLink> = emptyList()
)


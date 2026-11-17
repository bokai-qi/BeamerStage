package com.example.ui.presenter

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.BeamerAppContainer
import com.example.data.local.DeckEntity
import com.example.data.local.PreferencesRepository
import com.example.data.local.PresentationRepository
import com.example.display.ExternalDisplayController
import com.example.display.ProjectorRenderState
import com.example.model.AnnotationStroke
import com.example.model.AppThemeMode
import com.example.model.BeamerLayoutMode
import com.example.model.ChalkboardState
import com.example.model.ChalkboardStyle
import com.example.model.ExternalDisplayInfo
import com.example.model.InteractionTool
import com.example.model.LaserColor
import com.example.model.LaserPointerState
import com.example.model.NormalizedPoint
import com.example.model.PdfLinkAction
import com.example.model.PdfOutlineEntry
import com.example.model.PdfSlideLink
import com.example.model.PenColor
import com.example.model.PenStrokeWidth
import com.example.model.SlideViewportState
import com.example.model.SpotlightState
import com.example.model.TimerDisplayMode
import com.example.model.UserPreferences
import com.example.pdf.AnnotatedPdfExporter
import com.example.pdf.PdfSlideEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.hypot
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class HapticCue {
    STEP,
    LINK,
    SNAP,
    ERASE,
    TIMER_WARNING,
    TIMER_EXPIRED
}

data class PresenterUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val deck: DeckEntity? = null,
    val requestedLayoutMode: BeamerLayoutMode = BeamerLayoutMode.AUTO,
    val effectiveLayoutMode: BeamerLayoutMode = BeamerLayoutMode.SINGLE,
    val currentPageIndex: Int = 0,
    val totalPages: Int = 0,
    val currentSlideBitmap: Bitmap? = null,
    val beamerNotesBitmap: Bitmap? = null,
    val nextSlideBitmap: Bitmap? = null,
    val currentNoteText: String = "",
    val activeTool: InteractionTool = InteractionTool.NAVIGATE,
    val isEraserActive: Boolean = false,
    val eraserPosition: NormalizedPoint? = null,
    val laserColor: LaserColor = LaserColor.CRIMSON,
    val penColor: PenColor = PenColor.CRIMSON,
    val penStrokeWidth: PenStrokeWidth = PenStrokeWidth.MEDIUM,
    val laserState: LaserPointerState = LaserPointerState(),
    val spotlightState: SpotlightState = SpotlightState(),
    val viewportState: SlideViewportState = SlideViewportState(),
    val chalkboardState: ChalkboardState = ChalkboardState(),
    val currentSlideStrokes: List<AnnotationStroke> = emptyList(),
    val activeStroke: AnnotationStroke? = null,
    val canRedo: Boolean = false,
    val isFrozen: Boolean = false,
    val frozenPageIndex: Int? = null,
    val frozenSlideBitmap: Bitmap? = null,
    val frozenStrokes: List<AnnotationStroke> = emptyList(),
    val frozenChalkboardState: ChalkboardState = ChalkboardState(),
    val isBlackout: Boolean = false,
    val connectedDisplay: ExternalDisplayInfo? = null,
    val elapsedSeconds: Long = 0L,
    val isTimerRunning: Boolean = false,
    val targetMinutes: Int = 15,
    val wallClockText: String = "",
    val preferences: UserPreferences = UserPreferences(),
    val slideGridThumbnails: Map<Int, Bitmap?> = emptyMap(),
    val currentSlideLinks: List<PdfSlideLink> = emptyList(),
    val outlines: List<PdfOutlineEntry> = emptyList(),
    val showLinkHints: Boolean = true,
    val jumpBackPageIndex: Int? = null,
    val pendingExternalUri: String? = null,
    val isExportingPdf: Boolean = false,
    val exportMessage: String? = null
) {
    val projectedBitmap: Bitmap?
        get() = if (isFrozen) frozenSlideBitmap else currentSlideBitmap

    val projectedStrokes: List<AnnotationStroke>
        get() = if (isFrozen) frozenStrokes else currentSlideStrokes + listOfNotNull(activeStroke)

    val projectedChalkboardState: ChalkboardState
        get() = if (isFrozen) frozenChalkboardState else chalkboardState
}

class PresenterViewModel(
    private val deckId: String,
    private val repository: PresentationRepository,
    private val preferencesRepository: PreferencesRepository,
    private val pdfEngine: PdfSlideEngine,
    private val displayController: ExternalDisplayController
) : ViewModel() {

    private val _uiState = MutableStateFlow(PresenterUiState(wallClockText = formatCurrentWallClock()))
    val uiState: StateFlow<PresenterUiState> = _uiState.asStateFlow()

    private val _hapticEvents = MutableSharedFlow<HapticCue>(extraBufferCapacity = 8)
    val hapticEvents: SharedFlow<HapticCue> = _hapticEvents.asSharedFlow()

    @Volatile
    var isModalTextInputActive: Boolean = false

    private var notesMap: Map<Int, String> = emptyMap()
    private var annotationsMap: Map<Int, List<AnnotationStroke>> = emptyMap()
    private val redoStacks = mutableMapOf<Int, List<AnnotationStroke>>()
    private var previousToolBeforeBoard: InteractionTool? = null
    private var isActiveStrokeSnappedLine: Boolean = false
    private var timerJob: Job? = null
    private var clockJob: Job? = null
    private var renderJob: Job? = null
    private var erasedDuringGesture: Boolean = false
    private var warned85Percent: Boolean = false
    private var warned100Percent: Boolean = false

    init {
        displayController.setPresenterSessionActive(true)
        observePreferences()
        observeExternalDisplay()
        startWallClockTicker()
        loadDeckAndData()
    }

    private fun activeAnnotationKey(state: PresenterUiState = _uiState.value): Int {
        return if (state.chalkboardState.isActive) {
            -1000 - state.chalkboardState.boardIndex
        } else {
            state.currentPageIndex
        }
    }

    private fun emitHaptic(cue: HapticCue) {
        if (_uiState.value.preferences.hapticFeedbackEnabled) {
            _hapticEvents.tryEmit(cue)
        }
    }

    private fun startWallClockTicker() {
        clockJob = viewModelScope.launch {
            while (true) {
                _uiState.update { it.copy(wallClockText = formatCurrentWallClock()) }
                delay(15_000L)
            }
        }
    }

    private fun observePreferences() {
        viewModelScope.launch {
            preferencesRepository.userPreferences.collect { prefs ->
                _uiState.update { state ->
                    state.copy(
                        preferences = prefs,
                        laserColor = prefs.defaultLaserColor,
                        laserState = state.laserState.copy(color = prefs.defaultLaserColor)
                    )
                }
            }
        }
    }

    private fun observeExternalDisplay() {
        viewModelScope.launch {
            displayController.connectedDisplay.collect { display ->
                _uiState.update { it.copy(connectedDisplay = display) }
                pushToExternalDisplay()
            }
        }
    }

    private fun loadDeckAndData() {
        viewModelScope.launch {
            val deck = repository.getDeckById(deckId)
            if (deck == null) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Presentation could not be found.")
                }
                return@launch
            }

            runCatching {
                val pageCount = pdfEngine.openDocument(deck.uriString)
                val initialPage = deck.lastPageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
                val mode = BeamerLayoutMode.entries.firstOrNull { it.name == deck.layoutModeName }
                    ?: BeamerLayoutMode.AUTO
                _uiState.update {
                    it.copy(
                        deck = deck.copy(pageCount = pageCount),
                        totalPages = pageCount,
                        currentPageIndex = initialPage,
                        requestedLayoutMode = mode,
                        targetMinutes = deck.targetDurationMinutes,
                        isLoading = false
                    )
                }
                repository.updateDeckProgress(deckId, initialPage)
                renderCurrentAndAdjacent(initialPage)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = err.localizedMessage ?: "Unable to read PDF file. It may have been moved."
                    )
                }
            }
        }

        viewModelScope.launch {
            repository.observeNotesMap(deckId).collect { map ->
                notesMap = map
                val page = _uiState.value.currentPageIndex
                _uiState.update { it.copy(currentNoteText = map[page].orEmpty()) }
            }
        }

        viewModelScope.launch {
            repository.observeAnnotationsMap(deckId).collect { map ->
                annotationsMap = map
                val maxStoredBoardIdx = map.keys
                    .filter { it <= -1000 }
                    .maxOfOrNull { -1000 - it } ?: 0
                _uiState.update { state ->
                    val key = activeAnnotationKey(state)
                    val totalBoards = maxOf(state.chalkboardState.totalBoards, maxStoredBoardIdx + 1)
                    state.copy(
                        currentSlideStrokes = map[key].orEmpty(),
                        canRedo = !redoStacks[key].isNullOrEmpty(),
                        chalkboardState = state.chalkboardState.copy(totalBoards = totalBoards)
                    )
                }
                pushToExternalDisplay()
            }
        }
    }

    fun goToSlide(targetIndex: Int) {
        val state = _uiState.value
        val maxIndex = (state.totalPages - 1).coerceAtLeast(0)
        val clamped = targetIndex.coerceIn(0, maxIndex)
        if (clamped == state.currentPageIndex && state.currentSlideBitmap != null && !state.chalkboardState.isActive) {
            return
        }

        if (!state.isTimerRunning && state.elapsedSeconds == 0L && clamped > 0) {
            startTimer()
        }

        isActiveStrokeSnappedLine = false
        emitHaptic(HapticCue.STEP)

        _uiState.update {
            val nextBoardState = it.chalkboardState.copy(isActive = false)
            it.copy(
                currentPageIndex = clamped,
                currentNoteText = notesMap[clamped].orEmpty(),
                currentSlideStrokes = annotationsMap[clamped].orEmpty(),
                canRedo = !redoStacks[clamped].isNullOrEmpty(),
                activeStroke = null,
                eraserPosition = null,
                chalkboardState = nextBoardState,
                viewportState = it.viewportState.copy(scale = 1f, panX = 0f, panY = 0f),
                spotlightState = it.spotlightState.copy(isActive = false, position = null),
                laserState = it.laserState.copy(isActive = false, position = null, trail = emptyList())
            )
        }
        viewModelScope.launch {
            repository.updateDeckProgress(deckId, clamped)
        }
        renderCurrentAndAdjacent(clamped)
    }

    fun nextSlide() {
        val state = _uiState.value
        if (state.chalkboardState.isActive) {
            if (state.chalkboardState.boardIndex + 1 < state.chalkboardState.totalBoards) {
                selectChalkboardPage(state.chalkboardState.boardIndex + 1)
            }
            return
        }
        if (state.currentPageIndex < state.totalPages - 1) {
            goToSlide(state.currentPageIndex + 1)
        }
    }

    fun previousSlide() {
        val state = _uiState.value
        if (state.chalkboardState.isActive) {
            if (state.chalkboardState.boardIndex > 0) {
                selectChalkboardPage(state.chalkboardState.boardIndex - 1)
            }
            return
        }
        if (state.currentPageIndex > 0) {
            goToSlide(state.currentPageIndex - 1)
        }
    }

    fun onPdfLinkClicked(link: PdfSlideLink) {
        emitHaptic(HapticCue.LINK)
        when (val action = link.action) {
            is PdfLinkAction.GoToPage -> {
                val current = _uiState.value.currentPageIndex
                if (action.pageIndex == current) return
                if (abs(action.pageIndex - current) > 1) {
                    _uiState.update { it.copy(jumpBackPageIndex = current) }
                } else if (_uiState.value.jumpBackPageIndex == action.pageIndex) {
                    _uiState.update { it.copy(jumpBackPageIndex = null) }
                }
                goToSlide(action.pageIndex)
            }
            is PdfLinkAction.OpenUri -> {
                _uiState.update { it.copy(pendingExternalUri = action.uri) }
            }
        }
    }

    fun returnFromJump() {
        val target = _uiState.value.jumpBackPageIndex ?: return
        _uiState.update { it.copy(jumpBackPageIndex = null) }
        goToSlide(target)
    }

    fun dismissJumpBack() {
        _uiState.update { it.copy(jumpBackPageIndex = null) }
    }

    fun toggleLinkHints() {
        _uiState.update { it.copy(showLinkHints = !it.showLinkHints) }
    }

    fun dismissExternalUri() {
        _uiState.update { it.copy(pendingExternalUri = null) }
    }

    fun setLayoutMode(mode: BeamerLayoutMode) {
        _uiState.update { it.copy(requestedLayoutMode = mode) }
        viewModelScope.launch {
            repository.updateDeckLayoutMode(deckId, mode)
        }
        renderCurrentAndAdjacent(_uiState.value.currentPageIndex)
    }

    fun setInteractionTool(tool: InteractionTool) {
        isActiveStrokeSnappedLine = false
        _uiState.update {
            it.copy(
                activeTool = tool,
                isEraserActive = false,
                eraserPosition = null,
                laserState = it.laserState.copy(isActive = false, position = null, trail = emptyList()),
                spotlightState = it.spotlightState.copy(isActive = false, position = null),
                activeStroke = null
            )
        }
        pushToExternalDisplay()
    }

    fun toggleEraser() {
        _uiState.update { state ->
            val supportsEraser = state.activeTool == InteractionTool.PEN ||
                state.activeTool == InteractionTool.HIGHLIGHTER
            if (!supportsEraser) state else state.copy(
                isEraserActive = !state.isEraserActive,
                eraserPosition = null,
                activeStroke = null
            )
        }
    }

    fun setLaserColor(color: LaserColor) {
        _uiState.update {
            it.copy(
                laserColor = color,
                laserState = it.laserState.copy(color = color)
            )
        }
        viewModelScope.launch { preferencesRepository.setDefaultLaserColor(color) }
        pushToExternalDisplay()
    }

    fun setPenColor(color: PenColor) {
        _uiState.update {
            it.copy(
                penColor = color,
                isEraserActive = false,
                eraserPosition = null
            )
        }
    }

    fun setPenStrokeWidth(width: PenStrokeWidth) {
        _uiState.update {
            it.copy(
                penStrokeWidth = width,
                isEraserActive = false,
                eraserPosition = null
            )
        }
    }

    // --- Spotlight ("Loupe") Controls ---

    fun updateSpotlight(point: NormalizedPoint) {
        val clamped = NormalizedPoint(point.x.coerceIn(0f, 1f), point.y.coerceIn(0f, 1f))
        _uiState.update { state ->
            state.copy(
                spotlightState = state.spotlightState.copy(
                    position = clamped,
                    isActive = true
                )
            )
        }
        pushToExternalDisplay()
    }

    fun endSpotlight() {
        _uiState.update { state ->
            state.copy(
                spotlightState = state.spotlightState.copy(
                    position = null,
                    isActive = false
                )
            )
        }
        pushToExternalDisplay()
    }

    fun setSpotlightRadius(radiusRatio: Float) {
        val clamped = radiusRatio.coerceIn(0.08f, 0.30f)
        _uiState.update { state ->
            state.copy(
                spotlightState = state.spotlightState.copy(radiusRatio = clamped)
            )
        }
        pushToExternalDisplay()
    }

    // --- Live Projector Pinch-Zoom & Pan Controls ---

    fun setViewport(scale: Float, panX: Float, panY: Float) {
        _uiState.update { state ->
            if (state.chalkboardState.isActive) return@update state
            val clampedScale = scale.coerceIn(1f, 5f)
            val nextVp = if (clampedScale <= 1.01f) {
                state.viewportState.copy(scale = 1f, panX = 0f, panY = 0f)
            } else {
                state.viewportState.copy(
                    scale = clampedScale,
                    panX = panX.coerceIn(-0.5f, 0.5f),
                    panY = panY.coerceIn(-0.5f, 0.5f)
                )
            }
            if (nextVp == state.viewportState) state else state.copy(viewportState = nextVp)
        }
        pushToExternalDisplay()
    }

    fun resetViewport() {
        _uiState.update {
            it.copy(viewportState = it.viewportState.copy(scale = 1f, panX = 0f, panY = 0f))
        }
        pushToExternalDisplay()
    }

    fun toggleMirrorZoomToProjector() {
        _uiState.update {
            it.copy(
                viewportState = it.viewportState.copy(
                    mirrorToProjector = !it.viewportState.mirrorToProjector
                )
            )
        }
        pushToExternalDisplay()
    }

    // --- Instant Chalkboard / Scratchpad Overlay ---

    fun toggleChalkboard() {
        isActiveStrokeSnappedLine = false
        _uiState.update { state ->
            val nextActive = !state.chalkboardState.isActive
            if (nextActive) {
                previousToolBeforeBoard = state.activeTool
                val boardIdx = state.chalkboardState.boardIndex
                val boardKey = -1000 - boardIdx
                val nextTool = if (state.activeTool == InteractionTool.NAVIGATE) {
                    InteractionTool.PEN
                } else {
                    state.activeTool
                }
                val smartColor = when {
                    !state.chalkboardState.style.isLight && state.penColor == PenColor.CHARCOAL -> PenColor.WHITE
                    state.chalkboardState.style.isLight && state.penColor == PenColor.WHITE -> PenColor.CHARCOAL
                    state.penColor == PenColor.CRIMSON && !state.chalkboardState.style.isLight -> PenColor.WHITE
                    else -> state.penColor
                }
                state.copy(
                    chalkboardState = state.chalkboardState.copy(isActive = true),
                    activeTool = nextTool,
                    penColor = smartColor,
                    isEraserActive = false,
                    activeStroke = null,
                    currentSlideStrokes = annotationsMap[boardKey].orEmpty(),
                    canRedo = !redoStacks[boardKey].isNullOrEmpty(),
                    laserState = state.laserState.copy(isActive = false, position = null, trail = emptyList()),
                    spotlightState = state.spotlightState.copy(isActive = false, position = null)
                )
            } else {
                val restoredTool = previousToolBeforeBoard ?: state.activeTool
                previousToolBeforeBoard = null
                val page = state.currentPageIndex
                val smartColor = if (state.penColor == PenColor.WHITE) PenColor.CRIMSON else state.penColor
                state.copy(
                    chalkboardState = state.chalkboardState.copy(isActive = false),
                    activeTool = restoredTool,
                    penColor = smartColor,
                    isEraserActive = false,
                    activeStroke = null,
                    currentSlideStrokes = annotationsMap[page].orEmpty(),
                    canRedo = !redoStacks[page].isNullOrEmpty(),
                    laserState = state.laserState.copy(isActive = false, position = null, trail = emptyList()),
                    spotlightState = state.spotlightState.copy(isActive = false, position = null)
                )
            }
        }
        emitHaptic(HapticCue.STEP)
        pushToExternalDisplay()
    }

    fun setChalkboardStyle(style: ChalkboardStyle) {
        _uiState.update { state ->
            val adjustedColor = when {
                style.isLight && state.penColor == PenColor.WHITE -> PenColor.CHARCOAL
                !style.isLight && state.penColor == PenColor.CHARCOAL -> PenColor.WHITE
                else -> state.penColor
            }
            state.copy(
                chalkboardState = state.chalkboardState.copy(style = style),
                penColor = adjustedColor
            )
        }
        pushToExternalDisplay()
    }

    fun selectChalkboardPage(index: Int) {
        _uiState.update { state ->
            val clamped = index.coerceIn(0, (state.chalkboardState.totalBoards - 1).coerceAtLeast(0))
            val key = -1000 - clamped
            state.copy(
                chalkboardState = state.chalkboardState.copy(boardIndex = clamped),
                activeStroke = null,
                currentSlideStrokes = annotationsMap[key].orEmpty(),
                canRedo = !redoStacks[key].isNullOrEmpty()
            )
        }
        emitHaptic(HapticCue.STEP)
        pushToExternalDisplay()
    }

    fun addChalkboardPage() {
        _uiState.update { state ->
            val nextIndex = state.chalkboardState.totalBoards
            val key = -1000 - nextIndex
            state.copy(
                chalkboardState = state.chalkboardState.copy(
                    boardIndex = nextIndex,
                    totalBoards = nextIndex + 1
                ),
                activeStroke = null,
                currentSlideStrokes = annotationsMap[key].orEmpty(),
                canRedo = !redoStacks[key].isNullOrEmpty()
            )
        }
        emitHaptic(HapticCue.STEP)
        pushToExternalDisplay()
    }

    fun handleEscapeAction(): Boolean {
        val state = _uiState.value
        return when {
            state.isBlackout -> {
                toggleBlackout()
                true
            }
            state.chalkboardState.isActive -> {
                toggleChalkboard()
                true
            }
            state.isFrozen -> {
                toggleFreeze()
                true
            }
            state.viewportState.isZoomed -> {
                resetViewport()
                true
            }
            else -> false
        }
    }

    fun toggleSimplePresentingMode() {
        val next = !_uiState.value.preferences.simplePresentingMode
        viewModelScope.launch { preferencesRepository.setSimplePresentingMode(next) }
    }

    fun setSimplePresentingMode(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setSimplePresentingMode(enabled) }
    }

    fun setPortraitNotesWeight(weight: Float) {
        viewModelScope.launch { preferencesRepository.setPortraitNotesWeight(weight) }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
    }

    fun cycleTimerDisplayMode() {
        val current = _uiState.value.preferences.timerDisplayMode
        val entries = TimerDisplayMode.entries
        val next = entries[(entries.indexOf(current) + 1) % entries.size]
        viewModelScope.launch { preferencesRepository.setTimerDisplayMode(next) }
    }

    fun updateLaserPointer(point: NormalizedPoint) {
        val clamped = NormalizedPoint(point.x.coerceIn(0f, 1f), point.y.coerceIn(0f, 1f))
        _uiState.update { state ->
            val updatedTrail = (state.laserState.trail + clamped).takeLast(14)
            state.copy(
                laserState = state.laserState.copy(
                    position = clamped,
                    trail = updatedTrail,
                    color = state.laserColor,
                    isActive = true
                )
            )
        }
        pushToExternalDisplay()
    }

    fun endLaserPointer() {
        _uiState.update { state ->
            state.copy(
                laserState = state.laserState.copy(
                    position = null,
                    trail = emptyList(),
                    isActive = false
                )
            )
        }
        pushToExternalDisplay()
    }

    // --- Rich Vector Ink Annotations (Pen, Highlighter, Widths, Redo, Straight-Line Snap) ---

    fun startStroke(point: NormalizedPoint) {
        isActiveStrokeSnappedLine = false
        val state = _uiState.value
        val isHighlighter = state.activeTool == InteractionTool.HIGHLIGHTER
        val clamped = NormalizedPoint(point.x.coerceIn(0f, 1f), point.y.coerceIn(0f, 1f))
        val widthRatio = if (isHighlighter) {
            state.penStrokeWidth.highlighterRatio
        } else {
            state.penStrokeWidth.penRatio
        }
        val newStroke = AnnotationStroke(
            id = UUID.randomUUID().toString(),
            points = listOf(clamped),
            colorHex = state.penColor.colorHex,
            strokeWidthRatio = widthRatio,
            isHighlighter = isHighlighter
        )
        _uiState.update { it.copy(activeStroke = newStroke) }
        pushToExternalDisplay()
    }

    fun appendStrokePoint(point: NormalizedPoint) {
        val clamped = NormalizedPoint(point.x.coerceIn(0f, 1f), point.y.coerceIn(0f, 1f))
        _uiState.update { state ->
            val current = state.activeStroke ?: return@update state
            if (isActiveStrokeSnappedLine) {
                val startPt = current.points.firstOrNull() ?: clamped
                return@update state.copy(activeStroke = current.copy(points = listOf(startPt, clamped)))
            }
            val last = current.points.lastOrNull()
            if (last != null && hypot((clamped.x - last.x).toDouble(), (clamped.y - last.y).toDouble()) < 0.003) {
                return@update state
            }
            state.copy(activeStroke = current.copy(points = current.points + clamped))
        }
        pushToExternalDisplay()
    }

    fun snapActiveStrokeToLine() {
        val current = _uiState.value.activeStroke ?: return
        if (current.points.size < 2) return
        isActiveStrokeSnappedLine = true
        val start = current.points.first()
        val end = current.points.last()
        _uiState.update { state ->
            val stroke = state.activeStroke ?: return@update state
            state.copy(activeStroke = stroke.copy(points = listOf(start, end)))
        }
        emitHaptic(HapticCue.SNAP)
        pushToExternalDisplay()
    }

    fun cancelActiveStroke() {
        isActiveStrokeSnappedLine = false
        _uiState.update { it.copy(activeStroke = null) }
        pushToExternalDisplay()
    }

    fun finishStroke() {
        isActiveStrokeSnappedLine = false
        val state = _uiState.value
        val stroke = state.activeStroke ?: return
        val key = activeAnnotationKey(state)
        redoStacks.remove(key)
        val updatedList = state.currentSlideStrokes + stroke
        _uiState.update {
            it.copy(
                activeStroke = null,
                currentSlideStrokes = updatedList,
                canRedo = false,
                frozenStrokes = if (it.isFrozen && matchesFrozenTarget(it, key)) updatedList else it.frozenStrokes
            )
        }
        pushToExternalDisplay()
        viewModelScope.launch {
            repository.saveSlideAnnotations(deckId, key, updatedList)
        }
    }

    fun undoLastStroke() {
        val state = _uiState.value
        if (state.currentSlideStrokes.isEmpty()) return
        val key = activeAnnotationKey(state)
        val removed = state.currentSlideStrokes.last()
        redoStacks[key] = redoStacks[key].orEmpty() + removed
        val updated = state.currentSlideStrokes.dropLast(1)
        _uiState.update {
            it.copy(
                currentSlideStrokes = updated,
                canRedo = true,
                frozenStrokes = if (it.isFrozen && matchesFrozenTarget(it, key)) updated else it.frozenStrokes
            )
        }
        emitHaptic(HapticCue.STEP)
        pushToExternalDisplay()
        viewModelScope.launch {
            repository.saveSlideAnnotations(deckId, key, updated)
        }
    }

    fun redoLastStroke() {
        val state = _uiState.value
        val key = activeAnnotationKey(state)
        val stack = redoStacks[key].orEmpty()
        if (stack.isEmpty()) return
        val restored = stack.last()
        val nextStack = stack.dropLast(1)
        if (nextStack.isEmpty()) redoStacks.remove(key) else redoStacks[key] = nextStack

        val updated = state.currentSlideStrokes + restored
        _uiState.update {
            it.copy(
                currentSlideStrokes = updated,
                canRedo = nextStack.isNotEmpty(),
                frozenStrokes = if (it.isFrozen && matchesFrozenTarget(it, key)) updated else it.frozenStrokes
            )
        }
        emitHaptic(HapticCue.STEP)
        pushToExternalDisplay()
        viewModelScope.launch {
            repository.saveSlideAnnotations(deckId, key, updated)
        }
    }

    fun clearCurrentSlideAnnotations() {
        val state = _uiState.value
        val key = activeAnnotationKey(state)
        if (state.currentSlideStrokes.isNotEmpty()) {
            redoStacks[key] = state.currentSlideStrokes
        }
        _uiState.update {
            it.copy(
                currentSlideStrokes = emptyList(),
                activeStroke = null,
                canRedo = !redoStacks[key].isNullOrEmpty(),
                frozenStrokes = if (it.isFrozen && matchesFrozenTarget(it, key)) emptyList() else it.frozenStrokes
            )
        }
        pushToExternalDisplay()
        viewModelScope.launch {
            repository.saveSlideAnnotations(deckId, key, emptyList())
        }
    }

    fun startErase(point: NormalizedPoint) {
        erasedDuringGesture = false
        val clamped = NormalizedPoint(point.x.coerceIn(0f, 1f), point.y.coerceIn(0f, 1f))
        _uiState.update { it.copy(eraserPosition = clamped) }
        eraseAt(clamped)
    }

    fun moveErase(point: NormalizedPoint) {
        val clamped = NormalizedPoint(point.x.coerceIn(0f, 1f), point.y.coerceIn(0f, 1f))
        _uiState.update { it.copy(eraserPosition = clamped) }
        eraseAt(clamped)
    }

    fun endErase() {
        val state = _uiState.value
        val key = activeAnnotationKey(state)
        val strokesToSave = state.currentSlideStrokes
        _uiState.update { it.copy(eraserPosition = null) }
        if (erasedDuringGesture) {
            erasedDuringGesture = false
            viewModelScope.launch {
                repository.saveSlideAnnotations(deckId, key, strokesToSave)
            }
        }
    }

    private fun eraseAt(point: NormalizedPoint) {
        val state = _uiState.value
        val existing = state.currentSlideStrokes
        if (existing.isEmpty()) return
        val remaining = StrokeHitTest.removeStrokesNear(existing, point)
        if (remaining.size != existing.size) {
            erasedDuringGesture = true
            val erasedItems = existing.filter { item -> remaining.none { it.id == item.id } }
            val key = activeAnnotationKey(state)
            redoStacks[key] = redoStacks[key].orEmpty() + erasedItems
            emitHaptic(HapticCue.ERASE)
            _uiState.update {
                it.copy(
                    currentSlideStrokes = remaining,
                    canRedo = true,
                    frozenStrokes = if (it.isFrozen && matchesFrozenTarget(it, key)) remaining else it.frozenStrokes
                )
            }
            pushToExternalDisplay()
        }
    }

    private fun matchesFrozenTarget(state: PresenterUiState, key: Int): Boolean {
        return if (state.frozenChalkboardState.isActive) {
            key == (-1000 - state.frozenChalkboardState.boardIndex)
        } else {
            key == state.frozenPageIndex
        }
    }

    fun toggleFreeze() {
        _uiState.update { state ->
            if (!state.isFrozen) {
                state.copy(
                    isFrozen = true,
                    frozenPageIndex = state.currentPageIndex,
                    frozenSlideBitmap = state.currentSlideBitmap,
                    frozenStrokes = state.currentSlideStrokes,
                    frozenChalkboardState = state.chalkboardState
                )
            } else {
                state.copy(
                    isFrozen = false,
                    frozenPageIndex = null,
                    frozenSlideBitmap = null,
                    frozenStrokes = emptyList(),
                    frozenChalkboardState = ChalkboardState()
                )
            }
        }
        emitHaptic(HapticCue.STEP)
        pushToExternalDisplay()
    }

    fun toggleBlackout() {
        _uiState.update { it.copy(isBlackout = !it.isBlackout) }
        emitHaptic(HapticCue.STEP)
        pushToExternalDisplay()
    }

    fun saveNoteForCurrentSlide(text: String) {
        val page = _uiState.value.currentPageIndex
        _uiState.update { it.copy(currentNoteText = text) }
        viewModelScope.launch {
            repository.saveSlideNote(deckId, page, text)
        }
    }

    fun adjustNoteFontSize(deltaSp: Float) {
        val current = _uiState.value.preferences.noteFontSizeSp
        viewModelScope.launch {
            preferencesRepository.setNoteFontSizeSp(current + deltaSp)
        }
    }

    fun updateTargetDuration(minutes: Int) {
        val clamped = minutes.coerceIn(1, 180)
        warned85Percent = false
        warned100Percent = false
        _uiState.update { it.copy(targetMinutes = clamped) }
        viewModelScope.launch {
            repository.updateTargetDuration(deckId, clamped)
        }
    }

    fun toggleTimer() {
        if (_uiState.value.isTimerRunning) {
            pauseTimer()
        } else {
            startTimer()
        }
    }

    fun startTimer() {
        if (timerJob?.isActive == true) return
        _uiState.update { it.copy(isTimerRunning = true) }
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                var cueToEmit: HapticCue? = null
                _uiState.update { cur ->
                    val nextElapsed = cur.elapsedSeconds + 1L
                    val targetSecs = (cur.targetMinutes * 60L).coerceAtLeast(60L)
                    val warnThreshold = (targetSecs * 0.85).toLong()
                    if (!warned85Percent && nextElapsed >= warnThreshold && nextElapsed < targetSecs) {
                        warned85Percent = true
                        cueToEmit = HapticCue.TIMER_WARNING
                    } else if (!warned100Percent && nextElapsed >= targetSecs) {
                        warned100Percent = true
                        cueToEmit = HapticCue.TIMER_EXPIRED
                    }
                    cur.copy(
                        elapsedSeconds = nextElapsed,
                        wallClockText = formatCurrentWallClock()
                    )
                }
                cueToEmit?.let(::emitHaptic)
            }
        }
    }

    fun pauseTimer() {
        timerJob?.cancel()
        timerJob = null
        _uiState.update { it.copy(isTimerRunning = false) }
    }

    fun resetTimer() {
        pauseTimer()
        warned85Percent = false
        warned100Percent = false
        _uiState.update { it.copy(elapsedSeconds = 0L) }
    }

    fun exportAnnotatedPdf(context: Context, onShareReady: (Uri) -> Unit) {
        val state = _uiState.value
        val deck = state.deck ?: return
        if (state.isExportingPdf) return

        _uiState.update { it.copy(isExportingPdf = true, exportMessage = null) }
        viewModelScope.launch {
            runCatching {
                AnnotatedPdfExporter.exportDeckWithAnnotations(
                    context = context.applicationContext,
                    pdfEngine = pdfEngine,
                    deck = deck,
                    requestedMode = state.requestedLayoutMode,
                    annotationsByPage = annotationsMap,
                    chalkboardStyle = state.chalkboardState.style
                )
            }.onSuccess { uri ->
                _uiState.update { it.copy(isExportingPdf = false) }
                onShareReady(uri)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isExportingPdf = false,
                        exportMessage = err.localizedMessage ?: "Failed to export annotated PDF."
                    )
                }
            }
        }
    }

    fun dismissExportMessage() {
        _uiState.update { it.copy(exportMessage = null) }
    }

    fun loadAllGridThumbnails() {
        val state = _uiState.value
        val deck = state.deck ?: return
        viewModelScope.launch {
            for (idx in 0 until state.totalPages) {
                if (!_uiState.value.slideGridThumbnails.containsKey(idx)) {
                    val bmp = runCatching {
                        pdfEngine.renderThumbnail(deck.uriString, idx, state.requestedLayoutMode, 220)
                    }.getOrNull()
                    _uiState.update { current ->
                        current.copy(slideGridThumbnails = current.slideGridThumbnails + (idx to bmp))
                    }
                }
            }
        }
    }

    fun clearGridThumbnails() {
        if (_uiState.value.slideGridThumbnails.isNotEmpty()) {
            _uiState.update { it.copy(slideGridThumbnails = emptyMap()) }
        }
    }

    private fun renderCurrentAndAdjacent(pageIndex: Int) {
        renderJob?.cancel()
        renderJob = viewModelScope.launch {
            val state = _uiState.value
            val deck = state.deck ?: return@launch
            val mode = state.requestedLayoutMode

            val currentPair = runCatching {
                pdfEngine.renderSlidePair(
                    uriString = deck.uriString,
                    pageIndex = pageIndex,
                    requestedMode = mode,
                    targetSlideWidthPx = 1680,
                    includeNotesBitmap = true
                )
            }.getOrNull()

            val nextBmp = if (pageIndex + 1 < state.totalPages) {
                runCatching {
                    pdfEngine.renderThumbnail(
                        uriString = deck.uriString,
                        pageIndex = pageIndex + 1,
                        requestedMode = mode,
                        targetWidthPx = 480
                    )
                }.getOrNull()
            } else {
                null
            }

            _uiState.update { current ->
                current.copy(
                    currentSlideBitmap = currentPair?.slideBitmap ?: current.currentSlideBitmap,
                    beamerNotesBitmap = currentPair?.beamerNotesBitmap,
                    effectiveLayoutMode = currentPair?.effectiveLayout ?: current.effectiveLayoutMode,
                    currentSlideLinks = currentPair?.links ?: emptyList(),
                    outlines = if (!currentPair?.outlines.isNullOrEmpty()) currentPair!!.outlines else current.outlines,
                    nextSlideBitmap = nextBmp
                )
            }
            pushToExternalDisplay()

            if (pageIndex + 1 < state.totalPages) {
                runCatching {
                    pdfEngine.renderSlidePair(deck.uriString, pageIndex + 1, mode, 1680, false)
                }
            }
        }
    }

    private fun pushToExternalDisplay() {
        val state = _uiState.value
        displayController.updateOutput(
            ProjectorRenderState(
                bitmap = state.projectedBitmap,
                strokes = state.projectedStrokes,
                laserState = state.laserState,
                spotlightState = state.spotlightState,
                viewportState = if (state.isFrozen) SlideViewportState() else state.viewportState,
                chalkboardState = state.projectedChalkboardState,
                isBlackout = state.isBlackout
            )
        )
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        clockJob?.cancel()
        renderJob?.cancel()
        redoStacks.clear()
        _uiState.update {
            it.copy(
                currentSlideBitmap = null,
                beamerNotesBitmap = null,
                nextSlideBitmap = null,
                frozenSlideBitmap = null,
                slideGridThumbnails = emptyMap(),
                currentSlideLinks = emptyList(),
                outlines = emptyList()
            )
        }
        displayController.setPresenterSessionActive(false)
        displayController.updateOutput(ProjectorRenderState())
        pdfEngine.releaseSessionNonBlocking()
    }

    companion object {
        private fun formatCurrentWallClock(): String {
            return SimpleDateFormat("HH:mm", Locale.US).format(Date())
        }
    }

    class Factory(
        private val deckId: String,
        private val container: BeamerAppContainer
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PresenterViewModel(
                deckId = deckId,
                repository = container.presentationRepository,
                preferencesRepository = container.preferencesRepository,
                pdfEngine = container.pdfSlideEngine,
                displayController = container.externalDisplayController
            ) as T
        }
    }
}

object StrokeHitTest {
    private const val DEFAULT_ERASER_RADIUS = 0.032f

    fun removeStrokesNear(
        strokes: List<AnnotationStroke>,
        point: NormalizedPoint,
        eraserRadius: Float = DEFAULT_ERASER_RADIUS
    ): List<AnnotationStroke> {
        return strokes.filterNot { stroke -> isPointNearStroke(point, stroke, eraserRadius) }
    }

    fun isPointNearStroke(
        point: NormalizedPoint,
        stroke: AnnotationStroke,
        eraserRadius: Float = DEFAULT_ERASER_RADIUS
    ): Boolean {
        val pts = stroke.points
        if (pts.isEmpty()) return false
        val effectiveRadius = maxOf(eraserRadius, stroke.strokeWidthRatio * 1.45f)
        if (pts.size == 1) {
            return hypot((point.x - pts[0].x).toDouble(), (point.y - pts[0].y).toDouble()) <= effectiveRadius
        }
        for (i in 1 until pts.size) {
            if (distancePointToSegment(point, pts[i - 1], pts[i]) <= effectiveRadius) {
                return true
            }
        }
        return false
    }

    fun distancePointToSegment(
        p: NormalizedPoint,
        a: NormalizedPoint,
        b: NormalizedPoint
    ): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val lenSq = dx * dx + dy * dy
        if (lenSq <= 1e-8f) {
            return hypot((p.x - a.x).toDouble(), (p.y - a.y).toDouble()).toFloat()
        }
        val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / lenSq).coerceIn(0f, 1f)
        val projX = a.x + t * dx
        val projY = a.y + t * dy
        return hypot((p.x - projX).toDouble(), (p.y - projY).toDouble()).toFloat()
    }
}

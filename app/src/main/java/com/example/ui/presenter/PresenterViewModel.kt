package com.example.ui.presenter

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.BeamerAppContainer
import com.example.data.local.DeckEntity
import com.example.data.local.PreferencesRepository
import com.example.data.local.PresentationRepository
import com.example.display.ExternalDisplayController
import com.example.model.AnnotationStroke
import com.example.model.AppThemeMode
import com.example.model.BeamerLayoutMode
import com.example.model.ExternalDisplayInfo
import com.example.model.InteractionTool
import com.example.model.LaserColor
import com.example.model.LaserPointerState
import com.example.model.NormalizedPoint
import com.example.model.PdfLinkAction
import com.example.model.PdfSlideLink
import com.example.model.PenColor
import com.example.model.UserPreferences
import com.example.pdf.PdfSlideEngine
import kotlin.math.abs
import kotlin.math.hypot
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

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
    val laserState: LaserPointerState = LaserPointerState(),
    val currentSlideStrokes: List<AnnotationStroke> = emptyList(),
    val activeStroke: AnnotationStroke? = null,
    val isFrozen: Boolean = false,
    val frozenPageIndex: Int? = null,
    val frozenSlideBitmap: Bitmap? = null,
    val frozenStrokes: List<AnnotationStroke> = emptyList(),
    val isBlackout: Boolean = false,
    val connectedDisplay: ExternalDisplayInfo? = null,
    val elapsedSeconds: Long = 0L,
    val isTimerRunning: Boolean = false,
    val targetMinutes: Int = 15,
    val preferences: UserPreferences = UserPreferences(),
    val slideGridThumbnails: Map<Int, Bitmap?> = emptyMap(),
    val currentSlideLinks: List<PdfSlideLink> = emptyList(),
    val showLinkHints: Boolean = true,
    val jumpBackPageIndex: Int? = null,
    val pendingExternalUri: String? = null
) {
    val projectedBitmap: Bitmap?
        get() = if (isFrozen) frozenSlideBitmap else currentSlideBitmap

    val projectedStrokes: List<AnnotationStroke>
        get() = if (isFrozen) frozenStrokes else currentSlideStrokes + listOfNotNull(activeStroke)
}

class PresenterViewModel(
    private val deckId: String,
    private val repository: PresentationRepository,
    private val preferencesRepository: PreferencesRepository,
    private val pdfEngine: PdfSlideEngine,
    private val displayController: ExternalDisplayController
) : ViewModel() {

    private val _uiState = MutableStateFlow(PresenterUiState())
    val uiState: StateFlow<PresenterUiState> = _uiState.asStateFlow()

    private var notesMap: Map<Int, String> = emptyMap()
    private var annotationsMap: Map<Int, List<AnnotationStroke>> = emptyMap()
    private var timerJob: Job? = null
    private var renderJob: Job? = null
    private var erasedDuringGesture: Boolean = false

    init {
        displayController.setPresenterSessionActive(true)
        observePreferences()
        observeExternalDisplay()
        loadDeckAndData()
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
                val page = _uiState.value.currentPageIndex
                _uiState.update { it.copy(currentSlideStrokes = map[page].orEmpty()) }
                pushToExternalDisplay()
            }
        }
    }

    fun goToSlide(targetIndex: Int) {
        val state = _uiState.value
        val maxIndex = (state.totalPages - 1).coerceAtLeast(0)
        val clamped = targetIndex.coerceIn(0, maxIndex)
        if (clamped == state.currentPageIndex && state.currentSlideBitmap != null) return

        // Auto-start timer when speaker advances past slide 1 for the first time
        if (!state.isTimerRunning && state.elapsedSeconds == 0L && clamped > 0) {
            startTimer()
        }

        _uiState.update {
            it.copy(
                currentPageIndex = clamped,
                currentNoteText = notesMap[clamped].orEmpty(),
                currentSlideStrokes = annotationsMap[clamped].orEmpty(),
                activeStroke = null,
                eraserPosition = null,
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
        if (state.currentPageIndex < state.totalPages - 1) {
            goToSlide(state.currentPageIndex + 1)
        }
    }

    fun previousSlide() {
        val state = _uiState.value
        if (state.currentPageIndex > 0) {
            goToSlide(state.currentPageIndex - 1)
        }
    }

    fun onPdfLinkClicked(link: PdfSlideLink) {
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
        _uiState.update {
            it.copy(
                activeTool = tool,
                isEraserActive = false,
                eraserPosition = null,
                laserState = it.laserState.copy(isActive = false, position = null, trail = emptyList()),
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

    fun toggleSimplePresentingMode() {
        val next = !_uiState.value.preferences.simplePresentingMode
        viewModelScope.launch { preferencesRepository.setSimplePresentingMode(next) }
    }

    fun setSimplePresentingMode(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setSimplePresentingMode(enabled) }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
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

    fun startStroke(point: NormalizedPoint) {
        val state = _uiState.value
        val isHighlighter = state.activeTool == InteractionTool.HIGHLIGHTER
        val clamped = NormalizedPoint(point.x.coerceIn(0f, 1f), point.y.coerceIn(0f, 1f))
        val newStroke = AnnotationStroke(
            id = UUID.randomUUID().toString(),
            points = listOf(clamped),
            colorHex = state.penColor.colorHex,
            strokeWidthRatio = if (isHighlighter) 0.024f else 0.0055f,
            isHighlighter = isHighlighter
        )
        _uiState.update { it.copy(activeStroke = newStroke) }
        pushToExternalDisplay()
    }

    fun appendStrokePoint(point: NormalizedPoint) {
        val clamped = NormalizedPoint(point.x.coerceIn(0f, 1f), point.y.coerceIn(0f, 1f))
        _uiState.update { state ->
            val current = state.activeStroke ?: return@update state
            val last = current.points.lastOrNull()
            if (last != null && kotlin.math.hypot((clamped.x - last.x).toDouble(), (clamped.y - last.y).toDouble()) < 0.003) {
                return@update state
            }
            state.copy(activeStroke = current.copy(points = current.points + clamped))
        }
        pushToExternalDisplay()
    }

    fun finishStroke() {
        val state = _uiState.value
        val stroke = state.activeStroke ?: return
        val page = state.currentPageIndex
        val updatedList = state.currentSlideStrokes + stroke
        _uiState.update {
            it.copy(
                activeStroke = null,
                currentSlideStrokes = updatedList,
                frozenStrokes = if (it.isFrozen && it.frozenPageIndex == page) updatedList else it.frozenStrokes
            )
        }
        pushToExternalDisplay()
        viewModelScope.launch {
            repository.saveSlideAnnotations(deckId, page, updatedList)
        }
    }

    fun undoLastStroke() {
        val state = _uiState.value
        if (state.currentSlideStrokes.isEmpty()) return
        val page = state.currentPageIndex
        val updated = state.currentSlideStrokes.dropLast(1)
        _uiState.update {
            it.copy(
                currentSlideStrokes = updated,
                frozenStrokes = if (it.isFrozen && it.frozenPageIndex == page) updated else it.frozenStrokes
            )
        }
        pushToExternalDisplay()
        viewModelScope.launch {
            repository.saveSlideAnnotations(deckId, page, updated)
        }
    }

    fun clearCurrentSlideAnnotations() {
        val page = _uiState.value.currentPageIndex
        _uiState.update {
            it.copy(
                currentSlideStrokes = emptyList(),
                activeStroke = null,
                frozenStrokes = if (it.isFrozen && it.frozenPageIndex == page) emptyList() else it.frozenStrokes
            )
        }
        pushToExternalDisplay()
        viewModelScope.launch {
            repository.saveSlideAnnotations(deckId, page, emptyList())
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
        val page = state.currentPageIndex
        val strokesToSave = state.currentSlideStrokes
        _uiState.update { it.copy(eraserPosition = null) }
        if (erasedDuringGesture) {
            erasedDuringGesture = false
            viewModelScope.launch {
                repository.saveSlideAnnotations(deckId, page, strokesToSave)
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
            val page = state.currentPageIndex
            _uiState.update {
                it.copy(
                    currentSlideStrokes = remaining,
                    frozenStrokes = if (it.isFrozen && it.frozenPageIndex == page) remaining else it.frozenStrokes
                )
            }
            pushToExternalDisplay()
        }
    }

    fun toggleFreeze() {
        _uiState.update { state ->
            if (!state.isFrozen) {
                state.copy(
                    isFrozen = true,
                    frozenPageIndex = state.currentPageIndex,
                    frozenSlideBitmap = state.currentSlideBitmap,
                    frozenStrokes = state.currentSlideStrokes
                )
            } else {
                state.copy(
                    isFrozen = false,
                    frozenPageIndex = null,
                    frozenSlideBitmap = null,
                    frozenStrokes = emptyList()
                )
            }
        }
        pushToExternalDisplay()
    }

    fun toggleBlackout() {
        _uiState.update { it.copy(isBlackout = !it.isBlackout) }
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
                _uiState.update { it.copy(elapsedSeconds = it.elapsedSeconds + 1L) }
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
        _uiState.update { it.copy(elapsedSeconds = 0L) }
    }

    fun loadAllGridThumbnails() {
        val state = _uiState.value
        val deck = state.deck ?: return
        viewModelScope.launch {
            for (idx in 0 until state.totalPages) {
                if (!_uiState.value.slideGridThumbnails.containsKey(idx)) {
                    val bmp = runCatching {
                        pdfEngine.renderThumbnail(deck.uriString, idx, state.requestedLayoutMode, 300)
                    }.getOrNull()
                    _uiState.update { current ->
                        current.copy(slideGridThumbnails = current.slideGridThumbnails + (idx to bmp))
                    }
                }
            }
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
                    targetSlideWidthPx = 1600,
                    includeNotesBitmap = true
                )
            }.getOrNull()

            val nextBmp = if (pageIndex + 1 < state.totalPages) {
                runCatching {
                    pdfEngine.renderThumbnail(
                        uriString = deck.uriString,
                        pageIndex = pageIndex + 1,
                        requestedMode = mode,
                        targetWidthPx = 560
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
                    nextSlideBitmap = nextBmp
                )
            }
            pushToExternalDisplay()

            // Pre-warm cache for next and previous slides so navigation feels instant
            if (pageIndex + 1 < state.totalPages) {
                runCatching {
                    pdfEngine.renderSlidePair(deck.uriString, pageIndex + 1, mode, 1600, true)
                }
            }
            if (pageIndex - 1 >= 0) {
                runCatching {
                    pdfEngine.renderSlidePair(deck.uriString, pageIndex - 1, mode, 1600, true)
                }
            }
        }
    }

    private fun pushToExternalDisplay() {
        val state = _uiState.value
        displayController.updateOutput(
            bitmap = state.projectedBitmap,
            strokes = state.projectedStrokes,
            laserState = state.laserState,
            isBlackout = state.isBlackout
        )
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        displayController.setPresenterSessionActive(false)
        displayController.updateOutput(null, emptyList(), LaserPointerState(), false)
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

package com.example.ui.library

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.BeamerAppContainer
import com.example.data.local.DeckEntity
import com.example.data.local.PreferencesRepository
import com.example.data.local.PresentationRepository
import com.example.display.ExternalDisplayController
import com.example.model.AppThemeMode
import com.example.model.BeamerLayoutMode
import com.example.model.DarkThemeStyle
import com.example.model.ExternalDisplayInfo
import com.example.model.LaserColor
import com.example.model.LibrarySortOrder
import com.example.model.UserPreferences
import com.example.pdf.AnnotatedPdfExporter
import com.example.pdf.PdfSlideEngine
import com.example.pdf.SampleBeamerGenerator
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LibraryDeckItem(
    val deck: DeckEntity,
    val layoutMode: BeamerLayoutMode,
    val thumbnail: Bitmap?
)

data class LibraryUiState(
    val decks: List<LibraryDeckItem> = emptyList(),
    val totalDecksCount: Int = 0,
    val searchQuery: String = "",
    val sortOrder: LibrarySortOrder = LibrarySortOrder.RECENT,
    val connectedDisplay: ExternalDisplayInfo? = null,
    val preferences: UserPreferences = UserPreferences(),
    val isImporting: Boolean = false,
    val exportingDeckId: String? = null,
    val errorMessage: String? = null
)

private data class FilterSortState(
    val query: String = "",
    val sortOrder: LibrarySortOrder = LibrarySortOrder.RECENT,
    val isImporting: Boolean = false,
    val exportingDeckId: String? = null,
    val message: String? = null
)

class LibraryViewModel(
    private val appContext: Context,
    private val repository: PresentationRepository,
    private val preferencesRepository: PreferencesRepository,
    private val pdfEngine: PdfSlideEngine,
    private val sampleGenerator: SampleBeamerGenerator,
    displayController: ExternalDisplayController
) : ViewModel() {

    private val thumbnails = MutableStateFlow<Map<String, Bitmap?>>(emptyMap())
    private val filterSortState = MutableStateFlow(FilterSortState())

    val uiState: StateFlow<LibraryUiState> = combine(
        repository.allDecks,
        thumbnails,
        displayController.connectedDisplay,
        preferencesRepository.userPreferences,
        filterSortState
    ) { decks, thumbMap, display, prefs, uiFilter ->
        val visibleDecks = decks.filter { deck ->
            (prefs.showDemoDecks || !deck.isBundledSample) &&
                (uiFilter.query.isBlank() || deck.title.contains(uiFilter.query.trim(), ignoreCase = true))
        }

        val sortedDecks = when (uiFilter.sortOrder) {
            LibrarySortOrder.RECENT -> visibleDecks.sortedByDescending { it.lastOpenedAt }
            LibrarySortOrder.TITLE -> visibleDecks.sortedBy { it.title.lowercase() }
            LibrarySortOrder.PAGES -> visibleDecks.sortedByDescending { it.pageCount }
        }

        val items = sortedDecks.map { deck ->
            val mode = BeamerLayoutMode.entries.firstOrNull { it.name == deck.layoutModeName }
                ?: BeamerLayoutMode.AUTO
            LibraryDeckItem(
                deck = deck,
                layoutMode = mode,
                thumbnail = thumbMap[deck.id]
            )
        }

        LibraryUiState(
            decks = items,
            totalDecksCount = decks.size,
            searchQuery = uiFilter.query,
            sortOrder = uiFilter.sortOrder,
            connectedDisplay = display,
            preferences = prefs,
            isImporting = uiFilter.isImporting,
            exportingDeckId = uiFilter.exportingDeckId,
            errorMessage = uiFilter.message
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LibraryUiState()
    )

    init {
        viewModelScope.launch {
            runCatching { sampleGenerator.ensureSamplesSeeded() }
        }
        viewModelScope.launch {
            repository.allDecks.collect { decks ->
                decks.forEach { deck ->
                    if (!thumbnails.value.containsKey(deck.id)) {
                        launch {
                            val mode = BeamerLayoutMode.entries.firstOrNull { it.name == deck.layoutModeName }
                                ?: BeamerLayoutMode.AUTO
                            val bmp = runCatching {
                                pdfEngine.renderThumbnail(deck.uriString, 0, mode, 380)
                            }.getOrNull()
                            thumbnails.update { it + (deck.id to bmp) }
                        }
                    }
                }
            }
        }
    }

    fun setSearchQuery(query: String) {
        filterSortState.update { it.copy(query = query) }
    }

    fun setSortOrder(order: LibrarySortOrder) {
        filterSortState.update { it.copy(sortOrder = order) }
    }

    fun importPdfUri(uri: Uri, onDeckReady: (String) -> Unit) {
        viewModelScope.launch {
            filterSortState.update { it.copy(isImporting = true, message = null) }
            runCatching {
                val displayTitle = resolveFileName(uri)
                    .removeSuffix(".pdf")
                    .removeSuffix(".PDF")
                    .ifBlank { "Untitled Beamer Talk" }

                val persistedOk = if (uri.scheme == "content") {
                    runCatching {
                        appContext.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                        true
                    }.getOrDefault(false)
                } else {
                    true
                }

                val finalUriStr = if (persistedOk) {
                    uri.toString()
                } else {
                    copyIncomingUriToAppStorage(uri)
                }

                val (pageCount, detectedMode) = pdfEngine.inspectPageCountAndTitle(finalUriStr)
                val deckId = sha256Short(finalUriStr)
                val existing = repository.getDeckById(deckId)
                val entity = DeckEntity(
                    id = deckId,
                    uriString = finalUriStr,
                    title = existing?.title ?: displayTitle,
                    pageCount = pageCount,
                    lastPageIndex = existing?.lastPageIndex?.coerceIn(0, (pageCount - 1).coerceAtLeast(0)) ?: 0,
                    layoutModeName = existing?.layoutModeName
                        ?: if (detectedMode == BeamerLayoutMode.SLIDE_LEFT_NOTES_RIGHT) {
                            BeamerLayoutMode.AUTO.name
                        } else {
                            BeamerLayoutMode.SINGLE.name
                        },
                    targetDurationMinutes = existing?.targetDurationMinutes ?: 15,
                    isBundledSample = false,
                    lastOpenedAt = System.currentTimeMillis()
                )
                repository.upsertDeck(entity)
                deckId
            }.onSuccess { deckId ->
                filterSortState.update { it.copy(isImporting = false, message = null) }
                onDeckReady(deckId)
            }.onFailure { err ->
                filterSortState.update {
                    it.copy(
                        isImporting = false,
                        message = err.localizedMessage ?: "Failed to open PDF document"
                    )
                }
            }
        }
    }

    private suspend fun copyIncomingUriToAppStorage(uri: Uri): String = withContext(Dispatchers.IO) {
        val dir = File(appContext.filesDir, "imported_pdfs").apply { mkdirs() }
        val fileId = sha256Short(uri.toString())
        val outFile = File(dir, "$fileId.pdf")
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(outFile).use { output ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buf)
                    if (read < 0) break
                    output.write(buf, 0, read)
                }
            }
        } ?: throw IllegalStateException("Unable to read shared PDF stream.")
        Uri.fromFile(outFile).toString()
    }

    fun renameDeck(deckId: String, newTitle: String) {
        viewModelScope.launch {
            repository.renameDeck(deckId, newTitle)
        }
    }

    fun resetDeckProgressAndAnnotations(deckId: String) {
        viewModelScope.launch {
            repository.resetDeckProgressAndAnnotations(deckId)
            filterSortState.update { it.copy(message = "Slide progress and ink cleared.") }
        }
    }

    fun exportDeckAnnotatedPdf(
        context: Context,
        deckId: String,
        onShareReady: (Uri, String) -> Unit
    ) {
        if (filterSortState.value.exportingDeckId != null) return
        viewModelScope.launch {
            val deck = repository.getDeckById(deckId) ?: return@launch
            filterSortState.update { it.copy(exportingDeckId = deckId, message = null) }
            runCatching {
                val mode = BeamerLayoutMode.entries.firstOrNull { it.name == deck.layoutModeName }
                    ?: BeamerLayoutMode.AUTO
                val annotations = repository.observeAnnotationsMap(deckId).first()
                val uri = AnnotatedPdfExporter.exportDeckWithAnnotations(
                    context = context.applicationContext,
                    pdfEngine = pdfEngine,
                    deck = deck,
                    requestedMode = mode,
                    annotationsByPage = annotations
                )
                uri to deck.title
            }.onSuccess { (uri, title) ->
                filterSortState.update { it.copy(exportingDeckId = null) }
                onShareReady(uri, title)
            }.onFailure { err ->
                filterSortState.update {
                    it.copy(
                        exportingDeckId = null,
                        message = err.localizedMessage ?: "Failed to export annotated PDF."
                    )
                }
            }
        }
    }

    fun updateDeckLayoutMode(deckId: String, mode: BeamerLayoutMode) {
        viewModelScope.launch {
            repository.updateDeckLayoutMode(deckId, mode)
            val deck = repository.getDeckById(deckId) ?: return@launch
            val bmp = runCatching {
                pdfEngine.renderThumbnail(deck.uriString, 0, mode, 380)
            }.getOrNull()
            thumbnails.update { it + (deckId to bmp) }
        }
    }

    fun updateTargetDuration(deckId: String, minutes: Int) {
        viewModelScope.launch {
            repository.updateTargetDuration(deckId, minutes)
        }
    }

    fun deleteDeck(deckId: String) {
        viewModelScope.launch {
            val deck = repository.getDeckById(deckId)
            repository.deleteDeckById(deckId)
            thumbnails.update { it - deckId }
            // Clean up internally copied PDF if it came from a non-persistable intent
            deck?.uriString?.let { uriStr ->
                runCatching {
                    val parsed = Uri.parse(uriStr)
                    if (parsed.scheme == "file") {
                        val f = File(parsed.path.orEmpty())
                        if (f.parentFile?.name == "imported_pdfs" && f.exists()) {
                            f.delete()
                        }
                    }
                }
            }
        }
    }

    fun setVolumeKeysNavigate(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setVolumeKeysNavigate(enabled) }
    }

    fun setKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setKeepScreenOn(enabled) }
    }

    fun setHapticFeedbackEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setHapticFeedbackEnabled(enabled) }
    }

    fun setShowDemoDecks(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setShowDemoDecks(enabled) }
    }

    fun setNoteFontSizeSp(sizeSp: Float) {
        viewModelScope.launch { preferencesRepository.setNoteFontSizeSp(sizeSp) }
    }

    fun setDefaultLaserColor(color: LaserColor) {
        viewModelScope.launch { preferencesRepository.setDefaultLaserColor(color) }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
    }

    fun setDarkThemeStyle(style: DarkThemeStyle) {
        viewModelScope.launch {
            preferencesRepository.setDarkThemeStyle(style)
            if (uiState.value.preferences.themeMode == AppThemeMode.LIGHT) {
                preferencesRepository.setThemeMode(AppThemeMode.DARK)
            }
        }
    }

    fun setSimplePresentingMode(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setSimplePresentingMode(enabled) }
    }

    fun dismissError() {
        filterSortState.update { it.copy(message = null) }
    }

    private fun resolveFileName(uri: Uri): String {
        val cursor = runCatching {
            appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        }.getOrNull()
        cursor?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) {
                    val name = it.getString(idx)
                    if (!name.isNullOrBlank()) return name
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "Presentation.pdf"
    }

    private fun sha256Short(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.take(12).joinToString("") { "%02x".format(it) }
    }

    class Factory(
        private val appContext: Context,
        private val container: BeamerAppContainer
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LibraryViewModel(
                appContext = appContext,
                repository = container.presentationRepository,
                preferencesRepository = container.preferencesRepository,
                pdfEngine = container.pdfSlideEngine,
                sampleGenerator = container.sampleBeamerGenerator,
                displayController = container.externalDisplayController
            ) as T
        }
    }
}

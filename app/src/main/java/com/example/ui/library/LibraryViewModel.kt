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
import com.example.model.BeamerLayoutMode
import com.example.model.ExternalDisplayInfo
import com.example.model.LaserColor
import com.example.model.UserPreferences
import com.example.pdf.PdfSlideEngine
import com.example.pdf.SampleBeamerGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.security.MessageDigest

data class LibraryDeckItem(
    val deck: DeckEntity,
    val layoutMode: BeamerLayoutMode,
    val thumbnail: Bitmap?
)

data class LibraryUiState(
    val decks: List<LibraryDeckItem> = emptyList(),
    val connectedDisplay: ExternalDisplayInfo? = null,
    val preferences: UserPreferences = UserPreferences(),
    val isImporting: Boolean = false,
    val errorMessage: String? = null
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
    private val statusState = MutableStateFlow<Pair<Boolean, String?>>(false to null)

    val uiState: StateFlow<LibraryUiState> = combine(
        repository.allDecks,
        thumbnails,
        displayController.connectedDisplay,
        preferencesRepository.userPreferences,
        statusState
    ) { decks, thumbMap, display, prefs, (importing, error) ->
        val items = decks.map { deck ->
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
            connectedDisplay = display,
            preferences = prefs,
            isImporting = importing,
            errorMessage = error
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

    fun importPdfUri(uri: Uri, onDeckReady: (String) -> Unit) {
        viewModelScope.launch {
            statusState.value = true to null
            runCatching {
                runCatching {
                    appContext.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                val uriStr = uri.toString()
                val title = resolveFileName(uri).removeSuffix(".pdf").ifBlank { "Untitled Beamer Talk" }
                val (pageCount, detectedMode) = pdfEngine.inspectPageCountAndTitle(uriStr)
                val deckId = sha256Short(uriStr)
                val existing = repository.getDeckById(deckId)
                val entity = DeckEntity(
                    id = deckId,
                    uriString = uriStr,
                    title = title,
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
                statusState.value = false to null
                onDeckReady(deckId)
            }.onFailure { err ->
                statusState.value = false to (err.localizedMessage ?: "Failed to open PDF document")
            }
        }
    }

    fun updateDeckLayoutMode(deckId: String, mode: BeamerLayoutMode) {
        viewModelScope.launch {
            repository.updateDeckLayoutMode(deckId, mode)
            // Refresh thumbnail for updated crop
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
            repository.deleteDeckById(deckId)
            thumbnails.update { it - deckId }
        }
    }

    fun setVolumeKeysNavigate(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setVolumeKeysNavigate(enabled) }
    }

    fun setKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setKeepScreenOn(enabled) }
    }

    fun setNoteFontSizeSp(sizeSp: Float) {
        viewModelScope.launch { preferencesRepository.setNoteFontSizeSp(sizeSp) }
    }

    fun setDefaultLaserColor(color: LaserColor) {
        viewModelScope.launch { preferencesRepository.setDefaultLaserColor(color) }
    }

    fun dismissError() {
        statusState.value = statusState.value.first to null
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

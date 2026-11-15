package com.example.data.local

import com.example.model.AnnotationStroke
import com.example.model.BeamerLayoutMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class PresentationRepository(private val dao: PresentationDao) {

    private val json = Json { ignoreUnknownKeys = true }

    val allDecks: Flow<List<DeckEntity>> = dao.observeAllDecks()

    fun observeDeck(deckId: String): Flow<DeckEntity?> = dao.observeDeck(deckId)

    suspend fun getDeckById(deckId: String): DeckEntity? = dao.getDeckById(deckId)

    suspend fun upsertDeck(deck: DeckEntity) = dao.upsertDeck(deck)

    suspend fun updateDeckProgress(deckId: String, pageIndex: Int) {
        dao.updateDeckProgress(deckId, pageIndex, System.currentTimeMillis())
    }

    suspend fun updateDeckLayoutMode(deckId: String, mode: BeamerLayoutMode) {
        dao.updateDeckLayoutMode(deckId, mode.name)
    }

    suspend fun updateTargetDuration(deckId: String, minutes: Int) {
        dao.updateTargetDuration(deckId, minutes.coerceIn(1, 180))
    }

    suspend fun deleteDeckById(deckId: String) = dao.deleteDeckById(deckId)

    fun observeNotesMap(deckId: String): Flow<Map<Int, String>> {
        return dao.observeNotesForDeck(deckId).map { list ->
            list.associate { it.pageIndex to it.noteText }
        }
    }

    suspend fun saveSlideNote(deckId: String, pageIndex: Int, noteText: String) {
        dao.upsertSlideNote(
            SlideNoteEntity(
                deckId = deckId,
                pageIndex = pageIndex,
                noteText = noteText
            )
        )
    }

    fun observeAnnotationsMap(deckId: String): Flow<Map<Int, List<AnnotationStroke>>> {
        return dao.observeAnnotationsForDeck(deckId).map { entities ->
            entities.associate { entity ->
                val strokes = runCatching {
                    json.decodeFromString<List<AnnotationStroke>>(entity.strokesJson)
                }.getOrDefault(emptyList())
                entity.pageIndex to strokes
            }
        }
    }

    suspend fun saveSlideAnnotations(deckId: String, pageIndex: Int, strokes: List<AnnotationStroke>) {
        if (strokes.isEmpty()) {
            dao.clearSlideAnnotation(deckId, pageIndex)
        } else {
            dao.upsertSlideAnnotation(
                SlideAnnotationEntity(
                    deckId = deckId,
                    pageIndex = pageIndex,
                    strokesJson = json.encodeToString(strokes)
                )
            )
        }
    }

    suspend fun clearAllDeckAnnotations(deckId: String) {
        dao.clearAllDeckAnnotations(deckId)
    }
}

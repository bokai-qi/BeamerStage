package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PresentationDao {

    @Query("SELECT * FROM decks ORDER BY lastOpenedAt DESC")
    fun observeAllDecks(): Flow<List<DeckEntity>>

    @Query("SELECT * FROM decks WHERE id = :deckId LIMIT 1")
    fun observeDeck(deckId: String): Flow<DeckEntity?>

    @Query("SELECT * FROM decks WHERE id = :deckId LIMIT 1")
    suspend fun getDeckById(deckId: String): DeckEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDeck(deck: DeckEntity)

    @Query("UPDATE decks SET lastPageIndex = :pageIndex, lastOpenedAt = :timestamp WHERE id = :deckId")
    suspend fun updateDeckProgress(deckId: String, pageIndex: Int, timestamp: Long)

    @Query("UPDATE decks SET layoutModeName = :layoutModeName WHERE id = :deckId")
    suspend fun updateDeckLayoutMode(deckId: String, layoutModeName: String)

    @Query("UPDATE decks SET targetDurationMinutes = :minutes WHERE id = :deckId")
    suspend fun updateTargetDuration(deckId: String, minutes: Int)

    @Query("DELETE FROM decks WHERE id = :deckId")
    suspend fun deleteDeckById(deckId: String)

    @Query("SELECT * FROM slide_notes WHERE deckId = :deckId")
    fun observeNotesForDeck(deckId: String): Flow<List<SlideNoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSlideNote(note: SlideNoteEntity)

    @Query("SELECT * FROM slide_annotations WHERE deckId = :deckId")
    fun observeAnnotationsForDeck(deckId: String): Flow<List<SlideAnnotationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSlideAnnotation(annotation: SlideAnnotationEntity)

    @Query("DELETE FROM slide_annotations WHERE deckId = :deckId AND pageIndex = :pageIndex")
    suspend fun clearSlideAnnotation(deckId: String, pageIndex: Int)

    @Query("DELETE FROM slide_annotations WHERE deckId = :deckId")
    suspend fun clearAllDeckAnnotations(deckId: String)
}

package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "decks")
data class DeckEntity(
    @PrimaryKey val id: String,
    val uriString: String,
    val title: String,
    val pageCount: Int,
    val lastPageIndex: Int = 0,
    val layoutModeName: String = "AUTO",
    val targetDurationMinutes: Int = 15,
    val isBundledSample: Boolean = false,
    val lastOpenedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "slide_notes",
    primaryKeys = ["deckId", "pageIndex"]
)
data class SlideNoteEntity(
    val deckId: String,
    val pageIndex: Int,
    val noteText: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "slide_annotations",
    primaryKeys = ["deckId", "pageIndex"]
)
data class SlideAnnotationEntity(
    val deckId: String,
    val pageIndex: Int,
    val strokesJson: String,
    val updatedAt: Long = System.currentTimeMillis()
)

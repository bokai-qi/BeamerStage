package com.example

import com.example.model.AnnotationStroke
import com.example.model.BeamerLayoutMode
import com.example.model.NormalizedPoint
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PresenterLogicTest {

    @Test
    fun annotationStrokesRoundTripSerialization() {
        val strokes = listOf(
            AnnotationStroke(
                id = "s1",
                points = listOf(NormalizedPoint(0.1f, 0.2f), NormalizedPoint(0.8f, 0.7f)),
                colorHex = 0xFFF43F5E,
                strokeWidthRatio = 0.0055f,
                isHighlighter = false
            )
        )
        val encoded = Json.encodeToString(strokes)
        val decoded = Json.decodeFromString<List<AnnotationStroke>>(encoded)
        assertEquals(strokes, decoded)
    }

    @Test
    fun beamerLayoutModesHaveDistinctBadges() {
        val badges = BeamerLayoutMode.entries.map { it.badgeLabel }.toSet()
        assertEquals(BeamerLayoutMode.entries.size, badges.size)
        assertTrue(badges.contains("SLIDE | NOTE"))
    }
}

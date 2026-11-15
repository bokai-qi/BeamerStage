package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.LaserColor
import com.example.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "beamer_stage_prefs")

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val VOLUME_KEYS_NAVIGATE = booleanPreferencesKey("volume_keys_navigate")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val NOTE_FONT_SIZE_SP = floatPreferencesKey("note_font_size_sp")
        val DEFAULT_LASER_COLOR = stringPreferencesKey("default_laser_color")
        val SHOW_TIMER_BAR = booleanPreferencesKey("show_timer_bar")
    }

    val userPreferences: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        val laserColorName = prefs[Keys.DEFAULT_LASER_COLOR] ?: LaserColor.CRIMSON.name
        val laserColor = LaserColor.entries.firstOrNull { it.name == laserColorName } ?: LaserColor.CRIMSON
        UserPreferences(
            volumeKeysNavigate = prefs[Keys.VOLUME_KEYS_NAVIGATE] ?: true,
            keepScreenOn = prefs[Keys.KEEP_SCREEN_ON] ?: true,
            noteFontSizeSp = prefs[Keys.NOTE_FONT_SIZE_SP] ?: 16f,
            defaultLaserColor = laserColor,
            showTimerBar = prefs[Keys.SHOW_TIMER_BAR] ?: true
        )
    }

    suspend fun setVolumeKeysNavigate(enabled: Boolean) {
        context.dataStore.edit { it[Keys.VOLUME_KEYS_NAVIGATE] = enabled }
    }

    suspend fun setKeepScreenOn(enabled: Boolean) {
        context.dataStore.edit { it[Keys.KEEP_SCREEN_ON] = enabled }
    }

    suspend fun setNoteFontSizeSp(sizeSp: Float) {
        context.dataStore.edit { it[Keys.NOTE_FONT_SIZE_SP] = sizeSp.coerceIn(12f, 28f) }
    }

    suspend fun setDefaultLaserColor(color: LaserColor) {
        context.dataStore.edit { it[Keys.DEFAULT_LASER_COLOR] = color.name }
    }

    suspend fun setShowTimerBar(show: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_TIMER_BAR] = show }
    }
}

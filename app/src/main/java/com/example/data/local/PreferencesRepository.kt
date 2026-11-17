package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AppThemeMode
import com.example.model.DarkThemeStyle
import com.example.model.LaserColor
import com.example.model.TimerDisplayMode
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
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DARK_THEME_STYLE = stringPreferencesKey("dark_theme_style")
        val SIMPLE_PRESENTING_MODE = booleanPreferencesKey("simple_presenting_mode")
        val HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback_enabled")
        val SHOW_DEMO_DECKS = booleanPreferencesKey("show_demo_decks")
        val TIMER_DISPLAY_MODE = stringPreferencesKey("timer_display_mode")
        val PORTRAIT_NOTES_WEIGHT = floatPreferencesKey("portrait_notes_weight")
    }

    val userPreferences: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        val laserColorName = prefs[Keys.DEFAULT_LASER_COLOR] ?: LaserColor.CRIMSON.name
        val laserColor = LaserColor.entries.firstOrNull { it.name == laserColorName } ?: LaserColor.CRIMSON
        val themeName = prefs[Keys.THEME_MODE] ?: AppThemeMode.DARK.name
        val themeMode = AppThemeMode.entries.firstOrNull { it.name == themeName } ?: AppThemeMode.DARK
        val darkStyleName = prefs[Keys.DARK_THEME_STYLE] ?: DarkThemeStyle.STAGE.name
        val darkStyle = DarkThemeStyle.entries.firstOrNull { it.name == darkStyleName } ?: DarkThemeStyle.STAGE
        val timerModeName = prefs[Keys.TIMER_DISPLAY_MODE] ?: TimerDisplayMode.ELAPSED.name
        val timerMode = TimerDisplayMode.entries.firstOrNull { it.name == timerModeName } ?: TimerDisplayMode.ELAPSED

        UserPreferences(
            volumeKeysNavigate = prefs[Keys.VOLUME_KEYS_NAVIGATE] ?: true,
            keepScreenOn = prefs[Keys.KEEP_SCREEN_ON] ?: true,
            noteFontSizeSp = prefs[Keys.NOTE_FONT_SIZE_SP] ?: 16f,
            defaultLaserColor = laserColor,
            showTimerBar = prefs[Keys.SHOW_TIMER_BAR] ?: true,
            themeMode = themeMode,
            darkThemeStyle = darkStyle,
            simplePresentingMode = prefs[Keys.SIMPLE_PRESENTING_MODE] ?: false,
            hapticFeedbackEnabled = prefs[Keys.HAPTIC_FEEDBACK] ?: true,
            showDemoDecks = prefs[Keys.SHOW_DEMO_DECKS] ?: true,
            timerDisplayMode = timerMode,
            portraitNotesWeight = (prefs[Keys.PORTRAIT_NOTES_WEIGHT] ?: 0.62f).coerceIn(0.30f, 0.78f)
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

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setDarkThemeStyle(style: DarkThemeStyle) {
        context.dataStore.edit { it[Keys.DARK_THEME_STYLE] = style.name }
    }

    suspend fun setSimplePresentingMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SIMPLE_PRESENTING_MODE] = enabled }
    }

    suspend fun setHapticFeedbackEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HAPTIC_FEEDBACK] = enabled }
    }

    suspend fun setShowDemoDecks(show: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_DEMO_DECKS] = show }
    }

    suspend fun setTimerDisplayMode(mode: TimerDisplayMode) {
        context.dataStore.edit { it[Keys.TIMER_DISPLAY_MODE] = mode.name }
    }

    suspend fun setPortraitNotesWeight(weight: Float) {
        context.dataStore.edit { it[Keys.PORTRAIT_NOTES_WEIGHT] = weight.coerceIn(0.30f, 0.78f) }
    }
}

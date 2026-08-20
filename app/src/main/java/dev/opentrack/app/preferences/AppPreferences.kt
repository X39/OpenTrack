package dev.opentrack.app.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.opentrack.app.domain.model.TimestampPrecision
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.openTrackPreferences by preferencesDataStore(name = "open_track_preferences")

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

data class UserPreferences(
    val onboardingComplete: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val weekStartsMonday: Boolean = true,
    val reduceMotion: Boolean = false,
    val defaultTimestampPrecision: TimestampPrecision = TimestampPrecision.DATE_TIME,
)

class AppPreferences(private val context: Context) {
    private object Keys {
        val onboardingComplete = booleanPreferencesKey("onboarding_complete")
        val themeMode = stringPreferencesKey("theme_mode")
        val weekStartsMonday = booleanPreferencesKey("week_starts_monday")
        val reduceMotion = booleanPreferencesKey("reduce_motion")
        val defaultTimestampPrecision = stringPreferencesKey("default_timestamp_precision")
    }

    val values: Flow<UserPreferences> = context.openTrackPreferences.data.map { values ->
        UserPreferences(
            onboardingComplete = values[Keys.onboardingComplete] ?: false,
            themeMode = values[Keys.themeMode]
                ?.let { stored -> ThemeMode.entries.firstOrNull { it.name == stored } }
                ?: ThemeMode.SYSTEM,
            weekStartsMonday = values[Keys.weekStartsMonday] ?: true,
            reduceMotion = values[Keys.reduceMotion] ?: false,
            defaultTimestampPrecision = values[Keys.defaultTimestampPrecision]
                ?.let { stored -> TimestampPrecision.entries.firstOrNull { it.name == stored } }
                ?: TimestampPrecision.DATE_TIME,
        )
    }

    suspend fun completeOnboarding() {
        context.openTrackPreferences.edit { it[Keys.onboardingComplete] = true }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.openTrackPreferences.edit { it[Keys.themeMode] = mode.name }
    }

    suspend fun setWeekStartsMonday(enabled: Boolean) {
        context.openTrackPreferences.edit { it[Keys.weekStartsMonday] = enabled }
    }

    suspend fun setReduceMotion(enabled: Boolean) {
        context.openTrackPreferences.edit { it[Keys.reduceMotion] = enabled }
    }

    suspend fun setDefaultTimestampPrecision(precision: TimestampPrecision) {
        context.openTrackPreferences.edit { it[Keys.defaultTimestampPrecision] = precision.name }
    }
}

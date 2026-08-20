package dev.opentrack.app

import android.app.Application
import dev.opentrack.app.data.local.OpenTrackDatabase
import dev.opentrack.app.data.repository.RoomTrackerRepository
import dev.opentrack.app.domain.repository.TrackerRepository
import dev.opentrack.app.preferences.AppPreferences
import dev.opentrack.app.usecase.TrackingActions

class OpenTrackApplication : Application() {
    val container: OpenTrackContainer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val repository = RoomTrackerRepository(OpenTrackDatabase.get(this))
        OpenTrackContainer(
            repository = repository,
            preferences = AppPreferences(this),
            trackingActions = TrackingActions(repository),
        )
    }
}

data class OpenTrackContainer(
    val repository: TrackerRepository,
    val preferences: AppPreferences,
    val trackingActions: TrackingActions,
)

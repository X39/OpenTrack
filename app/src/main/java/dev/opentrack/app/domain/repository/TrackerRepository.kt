package dev.opentrack.app.domain.repository

import dev.opentrack.app.domain.model.BackupSnapshot
import dev.opentrack.app.domain.model.Dashboard
import dev.opentrack.app.domain.model.TrackerDefinition
import dev.opentrack.app.domain.model.TrackerEntry
import kotlinx.coroutines.flow.Flow

interface TrackerRepository {
    fun observeTrackers(includeArchived: Boolean = false): Flow<List<TrackerDefinition>>
    fun observeTracker(trackerId: String): Flow<TrackerDefinition?>
    fun observeEntries(trackerId: String? = null): Flow<List<TrackerEntry>>
    fun observeEntry(entryId: String): Flow<TrackerEntry?>
    fun observeDashboards(): Flow<List<Dashboard>>

    suspend fun getTracker(trackerId: String): TrackerDefinition?
    suspend fun getEntry(entryId: String): TrackerEntry?
    suspend fun snapshot(): BackupSnapshot

    suspend fun saveTracker(definition: TrackerDefinition)
    suspend fun archiveTracker(trackerId: String, archived: Boolean = true)
    suspend fun deleteTrackerPermanently(trackerId: String)
    suspend fun saveEntry(entry: TrackerEntry)
    suspend fun deleteEntry(entryId: String)
    suspend fun saveDashboard(dashboard: Dashboard)
    suspend fun deleteDashboard(dashboardId: String)
    suspend fun replaceAll(snapshot: BackupSnapshot)
}

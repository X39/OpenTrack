package dev.opentrack.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface OpenTrackDao {
    @Query("SELECT * FROM trackers ORDER BY position, name COLLATE NOCASE")
    fun observeTrackers(): Flow<List<TrackerEntity>>

    @Query("SELECT * FROM tracker_fields ORDER BY trackerId, position")
    fun observeFields(): Flow<List<TrackerFieldEntity>>

    @Query("SELECT * FROM choice_options ORDER BY fieldId, position")
    fun observeOptions(): Flow<List<ChoiceOptionEntity>>

    @Query("SELECT * FROM quick_presets ORDER BY trackerId, position")
    fun observePresets(): Flow<List<QuickPresetEntity>>

    @Query("SELECT * FROM quick_preset_values")
    fun observePresetValues(): Flow<List<QuickPresetValueEntity>>

    @Query("SELECT * FROM quick_add_configs")
    fun observeQuickAddConfigs(): Flow<List<QuickAddConfigEntity>>

    @Query("SELECT * FROM entries ORDER BY localEpochDay DESC, COALESCE(localSecondOfDay, -1) DESC, createdAtMillis DESC")
    fun observeEntries(): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries WHERE trackerId = :trackerId ORDER BY localEpochDay DESC, COALESCE(localSecondOfDay, -1) DESC, createdAtMillis DESC")
    fun observeEntries(trackerId: String): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries WHERE id = :entryId")
    fun observeEntry(entryId: String): Flow<EntryEntity?>

    @Query("SELECT * FROM entry_values")
    fun observeEntryValues(): Flow<List<EntryValueEntity>>

    @Query("SELECT v.* FROM entry_values v INNER JOIN entries e ON e.id = v.entryId WHERE e.trackerId = :trackerId")
    fun observeEntryValues(trackerId: String): Flow<List<EntryValueEntity>>

    @Query("SELECT * FROM dashboards ORDER BY position, name COLLATE NOCASE")
    fun observeDashboards(): Flow<List<DashboardEntity>>

    @Query("SELECT * FROM dashboard_widgets ORDER BY dashboardId, position")
    fun observeWidgets(): Flow<List<DashboardWidgetEntity>>

    @Query("SELECT * FROM dashboard_series ORDER BY widgetId, position")
    fun observeSeries(): Flow<List<DashboardSeriesEntity>>

    @Query("SELECT * FROM trackers") suspend fun trackers(): List<TrackerEntity>
    @Query("SELECT * FROM tracker_fields") suspend fun fields(): List<TrackerFieldEntity>
    @Query("SELECT * FROM choice_options") suspend fun options(): List<ChoiceOptionEntity>
    @Query("SELECT * FROM quick_presets") suspend fun presets(): List<QuickPresetEntity>
    @Query("SELECT * FROM quick_preset_values") suspend fun presetValues(): List<QuickPresetValueEntity>
    @Query("SELECT * FROM quick_add_configs") suspend fun quickAddConfigs(): List<QuickAddConfigEntity>
    @Query("SELECT * FROM entries") suspend fun entries(): List<EntryEntity>
    @Query("SELECT * FROM entry_values") suspend fun entryValues(): List<EntryValueEntity>
    @Query("SELECT * FROM dashboards") suspend fun dashboards(): List<DashboardEntity>
    @Query("SELECT * FROM dashboard_widgets") suspend fun widgets(): List<DashboardWidgetEntity>
    @Query("SELECT * FROM dashboard_series") suspend fun series(): List<DashboardSeriesEntity>

    @Query("SELECT * FROM trackers WHERE id = :trackerId") suspend fun tracker(trackerId: String): TrackerEntity?
    @Query("SELECT * FROM entries WHERE id = :entryId") suspend fun entry(entryId: String): EntryEntity?
    @Query("SELECT EXISTS(SELECT 1 FROM entries WHERE trackerId = :trackerId LIMIT 1)")
    suspend fun hasEntries(trackerId: String): Boolean
    @Query("SELECT * FROM entry_values WHERE entryId = :entryId") suspend fun valuesForEntry(entryId: String): List<EntryValueEntity>

    @Upsert suspend fun upsertTrackers(values: List<TrackerEntity>)
    @Upsert suspend fun upsertFields(values: List<TrackerFieldEntity>)
    @Upsert suspend fun upsertOptions(values: List<ChoiceOptionEntity>)
    @Upsert suspend fun upsertPresets(values: List<QuickPresetEntity>)
    @Upsert suspend fun upsertPresetValues(values: List<QuickPresetValueEntity>)
    @Upsert suspend fun upsertQuickAddConfigs(values: List<QuickAddConfigEntity>)
    @Upsert suspend fun upsertEntries(values: List<EntryEntity>)
    @Upsert suspend fun upsertEntryValues(values: List<EntryValueEntity>)
    @Upsert suspend fun upsertDashboards(values: List<DashboardEntity>)
    @Upsert suspend fun upsertWidgets(values: List<DashboardWidgetEntity>)
    @Upsert suspend fun upsertSeries(values: List<DashboardSeriesEntity>)

    @Query("UPDATE trackers SET archivedAtMillis = :archivedAt, updatedAtMillis = :updatedAt WHERE id = :trackerId")
    suspend fun setTrackerArchived(trackerId: String, archivedAt: Long?, updatedAt: Long)

    @Query("DELETE FROM trackers WHERE id = :trackerId") suspend fun deleteTracker(trackerId: String)
    @Query("DELETE FROM dashboard_widgets WHERE NOT EXISTS (SELECT 1 FROM dashboard_series WHERE dashboard_series.widgetId = dashboard_widgets.id)")
    suspend fun deleteEmptyWidgets()
    @Query("DELETE FROM entries WHERE id = :entryId") suspend fun deleteEntry(entryId: String)
    @Query("DELETE FROM entry_values WHERE entryId = :entryId") suspend fun deleteEntryValues(entryId: String)
    @Query("DELETE FROM quick_add_configs WHERE trackerId = :trackerId") suspend fun deleteQuickAddConfig(trackerId: String)
    @Query("DELETE FROM quick_presets WHERE trackerId = :trackerId") suspend fun deletePresetsForTracker(trackerId: String)
    @Query("DELETE FROM dashboard_series WHERE widgetId IN (SELECT id FROM dashboard_widgets WHERE dashboardId = :dashboardId)")
    suspend fun deleteSeriesForDashboard(dashboardId: String)
    @Query("DELETE FROM dashboard_widgets WHERE dashboardId = :dashboardId") suspend fun deleteWidgetsForDashboard(dashboardId: String)
    @Query("DELETE FROM dashboards WHERE id = :dashboardId") suspend fun deleteDashboard(dashboardId: String)

    @Query("DELETE FROM dashboard_series") suspend fun clearSeries()
    @Query("DELETE FROM dashboard_widgets") suspend fun clearWidgets()
    @Query("DELETE FROM dashboards") suspend fun clearDashboards()
    @Query("DELETE FROM entry_values") suspend fun clearEntryValues()
    @Query("DELETE FROM entries") suspend fun clearEntries()
    @Query("DELETE FROM quick_add_configs") suspend fun clearQuickAddConfigs()
    @Query("DELETE FROM quick_preset_values") suspend fun clearPresetValues()
    @Query("DELETE FROM quick_presets") suspend fun clearPresets()
    @Query("DELETE FROM choice_options") suspend fun clearOptions()
    @Query("DELETE FROM tracker_fields") suspend fun clearFields()
    @Query("DELETE FROM trackers") suspend fun clearTrackers()
}

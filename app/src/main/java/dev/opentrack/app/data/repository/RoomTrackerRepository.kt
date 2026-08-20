package dev.opentrack.app.data.repository

import androidx.room.withTransaction
import dev.opentrack.app.data.local.OpenTrackDatabase
import dev.opentrack.app.data.mapper.assembleDashboards
import dev.opentrack.app.data.mapper.assembleDefinitions
import dev.opentrack.app.data.mapper.assembleEntries
import dev.opentrack.app.data.mapper.toEntity
import dev.opentrack.app.data.mapper.toQuickAddEntity
import dev.opentrack.app.data.mapper.toValueEntities
import dev.opentrack.app.domain.model.BackupSnapshot
import dev.opentrack.app.domain.model.Dashboard
import dev.opentrack.app.domain.model.DomainValidationException
import dev.opentrack.app.domain.model.DomainValidator
import dev.opentrack.app.domain.model.TrackerDefinition
import dev.opentrack.app.domain.model.TrackerEntry
import dev.opentrack.app.domain.repository.TrackerRepository
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class RoomTrackerRepository(
    private val database: OpenTrackDatabase,
) : TrackerRepository {
    private val dao = database.dao()

    private data class DefinitionRows(
        val trackers: List<dev.opentrack.app.data.local.TrackerEntity>,
        val fields: List<dev.opentrack.app.data.local.TrackerFieldEntity>,
        val options: List<dev.opentrack.app.data.local.ChoiceOptionEntity>,
    )

    private data class PresetRows(
        val presets: List<dev.opentrack.app.data.local.QuickPresetEntity>,
        val values: List<dev.opentrack.app.data.local.QuickPresetValueEntity>,
        val configs: List<dev.opentrack.app.data.local.QuickAddConfigEntity>,
    )

    private fun definitionFlow(): Flow<List<TrackerDefinition>> {
        val definitionRows = combine(
            dao.observeTrackers(),
            dao.observeFields(),
            dao.observeOptions(),
        ) { trackers, fields, options -> DefinitionRows(trackers, fields, options) }
        val presetRows = combine(
            dao.observePresets(),
            dao.observePresetValues(),
            dao.observeQuickAddConfigs(),
        ) { presets, values, configs -> PresetRows(presets, values, configs) }
        return combine(definitionRows, presetRows) { definitions, presets ->
            assembleDefinitions(
                definitions.trackers,
                definitions.fields,
                definitions.options,
                presets.presets,
                presets.values,
                presets.configs,
            )
        }.distinctUntilChanged()
    }

    override fun observeTrackers(includeArchived: Boolean): Flow<List<TrackerDefinition>> =
        definitionFlow().map { definitions ->
            if (includeArchived) definitions else definitions.filter { it.archivedAt == null }
        }

    override fun observeTracker(trackerId: String): Flow<TrackerDefinition?> =
        definitionFlow().map { definitions -> definitions.firstOrNull { it.id == trackerId } }

    override fun observeEntries(trackerId: String?): Flow<List<TrackerEntry>> {
        val entryFlow = trackerId?.let(dao::observeEntries) ?: dao.observeEntries()
        val valueFlow = trackerId?.let(dao::observeEntryValues) ?: dao.observeEntryValues()
        return combine(entryFlow, valueFlow, definitionFlow()) { entries, values, definitions ->
            assembleEntries(entries, values, definitions)
        }.distinctUntilChanged()
    }

    override fun observeEntry(entryId: String): Flow<TrackerEntry?> = combine(
        dao.observeEntry(entryId),
        dao.observeEntryValues(),
        definitionFlow(),
    ) { entry, values, definitions ->
        entry?.let { assembleEntries(listOf(it), values.filter { value -> value.entryId == entryId }, definitions).singleOrNull() }
    }.distinctUntilChanged()

    override fun observeDashboards(): Flow<List<Dashboard>> = combine(
        dao.observeDashboards(),
        dao.observeWidgets(),
        dao.observeSeries(),
    ) { dashboards, widgets, series -> assembleDashboards(dashboards, widgets, series) }
        .distinctUntilChanged()

    override suspend fun getTracker(trackerId: String): TrackerDefinition? =
        definitionsOnce().firstOrNull { it.id == trackerId }

    override suspend fun getEntry(entryId: String): TrackerEntry? {
        val entity = dao.entry(entryId) ?: return null
        return assembleEntries(listOf(entity), dao.valuesForEntry(entryId), definitionsOnce()).singleOrNull()
    }

    override suspend fun snapshot(): BackupSnapshot = database.withTransaction {
        val definitions = definitionsOnce()
        val entries = assembleEntries(dao.entries(), dao.entryValues(), definitions)
        val dashboards = assembleDashboards(dao.dashboards(), dao.widgets(), dao.series())
        BackupSnapshot(definitions, entries, dashboards)
    }

    override suspend fun saveTracker(definition: TrackerDefinition) {
        DomainValidator.validate(definition)
        database.withTransaction {
            definitionsOnce().firstOrNull { it.id == definition.id }?.let { current ->
                validateCompatibleEdit(current, definition, dao.hasEntries(definition.id))
            }
            dao.upsertTrackers(listOf(definition.toEntity()))

            val now = Instant.now().toEpochMilli()
            val currentFields = dao.fields().filter { it.trackerId == definition.id }
            val incomingFieldIds = definition.fields.mapTo(hashSetOf()) { it.id }
            val fieldsToArchive = currentFields.filterNot { it.id in incomingFieldIds }
                .map { it.copy(archivedAtMillis = it.archivedAtMillis ?: now) }
            dao.upsertFields(definition.fields.map { it.toEntity(definition.id) } + fieldsToArchive)

            val currentFieldIds = (currentFields.map { it.id } + definition.fields.map { it.id }).toSet()
            val currentOptions = dao.options().filter { it.fieldId in currentFieldIds }
            val incomingOptions = definition.fields.flatMap { field ->
                field.options.map { option -> option.toEntity(field.id) }
            }
            val incomingOptionIds = incomingOptions.mapTo(hashSetOf()) { it.id }
            val optionsToArchive = currentOptions.filterNot { it.id in incomingOptionIds }
                .map { it.copy(archivedAtMillis = it.archivedAtMillis ?: now) }
            dao.upsertOptions(incomingOptions + optionsToArchive)

            dao.deleteQuickAddConfig(definition.id)
            dao.deletePresetsForTracker(definition.id)
            dao.upsertPresets(definition.presets.map { it.toEntity(definition.id) })
            dao.upsertPresetValues(definition.presets.flatMap { it.toValueEntities() })
            dao.upsertQuickAddConfigs(listOf(definition.toQuickAddEntity()))
        }
    }

    override suspend fun archiveTracker(trackerId: String, archived: Boolean) {
        val now = Instant.now().toEpochMilli()
        dao.setTrackerArchived(trackerId, if (archived) now else null, now)
    }

    override suspend fun deleteTrackerPermanently(trackerId: String) {
        database.withTransaction {
            dao.deleteTracker(trackerId)
            dao.deleteEmptyWidgets()
        }
    }

    override suspend fun saveEntry(entry: TrackerEntry) {
        val definition = getTracker(entry.trackerId)
            ?: throw DomainValidationException("Tracker ${entry.trackerId} does not exist")
        DomainValidator.validateEntry(definition, entry)
        database.withTransaction {
            dao.upsertEntries(listOf(entry.toEntity()))
            dao.deleteEntryValues(entry.id)
            dao.upsertEntryValues(entry.toValueEntities())
        }
    }

    override suspend fun deleteEntry(entryId: String) {
        dao.deleteEntry(entryId)
    }

    override suspend fun saveDashboard(dashboard: Dashboard) {
        val definitions = definitionsOnce()
        DomainValidator.validate(BackupSnapshot(definitions, emptyList(), listOf(dashboard)))
        database.withTransaction {
            dao.deleteSeriesForDashboard(dashboard.id)
            dao.deleteWidgetsForDashboard(dashboard.id)
            dao.upsertDashboards(listOf(dashboard.toEntity()))
            dao.upsertWidgets(dashboard.widgets.map { it.toEntity(dashboard.id) })
            dao.upsertSeries(dashboard.widgets.flatMap { widget -> widget.series.map { it.toEntity(widget.id) } })
        }
    }

    override suspend fun deleteDashboard(dashboardId: String) {
        dao.deleteDashboard(dashboardId)
    }

    override suspend fun replaceAll(snapshot: BackupSnapshot) {
        DomainValidator.validate(snapshot)
        database.withTransaction {
            clearAll()

            dao.upsertTrackers(snapshot.trackers.map { it.toEntity() })
            dao.upsertFields(snapshot.trackers.flatMap { definition ->
                definition.fields.map { it.toEntity(definition.id) }
            })
            dao.upsertOptions(snapshot.trackers.flatMap { definition ->
                definition.fields.flatMap { field -> field.options.map { it.toEntity(field.id) } }
            })
            dao.upsertPresets(snapshot.trackers.flatMap { definition ->
                definition.presets.map { it.toEntity(definition.id) }
            })
            dao.upsertPresetValues(snapshot.trackers.flatMap { definition ->
                definition.presets.flatMap { it.toValueEntities() }
            })
            dao.upsertQuickAddConfigs(snapshot.trackers.map { it.toQuickAddEntity() })
            dao.upsertEntries(snapshot.entries.map { it.toEntity() })
            dao.upsertEntryValues(snapshot.entries.flatMap { it.toValueEntities() })
            dao.upsertDashboards(snapshot.dashboards.map { it.toEntity() })
            dao.upsertWidgets(snapshot.dashboards.flatMap { dashboard ->
                dashboard.widgets.map { it.toEntity(dashboard.id) }
            })
            dao.upsertSeries(snapshot.dashboards.flatMap { dashboard ->
                dashboard.widgets.flatMap { widget -> widget.series.map { it.toEntity(widget.id) } }
            })
        }
    }

    private suspend fun definitionsOnce(): List<TrackerDefinition> = assembleDefinitions(
        dao.trackers(),
        dao.fields(),
        dao.options(),
        dao.presets(),
        dao.presetValues(),
        dao.quickAddConfigs(),
    )

    private fun validateCompatibleEdit(
        current: TrackerDefinition,
        updated: TrackerDefinition,
        hasHistory: Boolean,
    ) {
        require(current.kind == updated.kind) { "Tracker type cannot change after creation" }
        val currentFields = current.fields.associateBy { it.id }
        updated.fields.forEach fieldLoop@{ field ->
            val previous = currentFields[field.id]
            if (previous == null) {
                require(!hasHistory || !field.required) { "New fields must be optional when a tracker already has entries" }
                return@fieldLoop
            }
            require(previous.kind == field.kind) { "Field type cannot change after entries may exist" }
            require(previous.unit == field.unit) { "Field unit cannot change after creation" }
            require(previous.decimalPlaces == field.decimalPlaces) { "Field precision cannot change after creation" }
            val previousOptions = previous.options.associateBy { it.id }
            field.options.forEach optionLoop@{ option ->
                val oldOption = previousOptions[option.id] ?: return@optionLoop
                require(oldOption.payloadKind == option.payloadKind) { "Option payload type cannot change after creation" }
                require(oldOption.payloadUnit == option.payloadUnit) { "Option payload unit cannot change after creation" }
                require(oldOption.radioScore == option.radioScore) { "Rating score cannot change after creation" }
            }
        }
    }

    private suspend fun clearAll() {
        dao.clearSeries()
        dao.clearWidgets()
        dao.clearDashboards()
        dao.clearEntryValues()
        dao.clearEntries()
        dao.clearQuickAddConfigs()
        dao.clearPresetValues()
        dao.clearPresets()
        dao.clearOptions()
        dao.clearFields()
        dao.clearTrackers()
    }
}

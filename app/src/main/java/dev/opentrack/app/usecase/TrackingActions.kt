package dev.opentrack.app.usecase

import dev.opentrack.app.domain.model.DomainValidationException
import dev.opentrack.app.domain.model.DomainValidator
import dev.opentrack.app.domain.model.FieldKind
import dev.opentrack.app.domain.model.FieldValue
import dev.opentrack.app.domain.model.QuickAddMode
import dev.opentrack.app.domain.model.RecordedAt
import dev.opentrack.app.domain.model.TimestampPresetMode
import dev.opentrack.app.domain.model.TrackerDefinition
import dev.opentrack.app.domain.model.TrackerEntry
import dev.opentrack.app.domain.model.TrackerKind
import dev.opentrack.app.domain.repository.TrackerRepository
import java.time.Clock
import java.time.Instant

sealed interface QuickAddResult {
    data class Recorded(val entry: TrackerEntry) : QuickAddResult
    data class NeedsInput(val tracker: TrackerDefinition) : QuickAddResult
    data class MissingTracker(val trackerId: String) : QuickAddResult
}

class TrackingActions(
    private val repository: TrackerRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    suspend fun quickAdd(trackerId: String, presetId: String? = null): QuickAddResult {
        val tracker = repository.getTracker(trackerId)
            ?: return QuickAddResult.MissingTracker(trackerId)

        val resolvedPreset = (presetId ?: tracker.quickAdd.defaultPresetId)
            ?.let { id -> tracker.presets.firstOrNull { it.id == id } }
        val values = linkedMapOf<String, FieldValue>()

        resolvedPreset?.values?.let(values::putAll)
        resolvedPreset?.timestampModes?.forEach { (fieldId, mode) ->
            val field = tracker.fields.firstOrNull { it.id == fieldId } ?: return@forEach
            val precision = field.timestampPrecision ?: tracker.timestampPrecision
            when (mode) {
                TimestampPresetMode.NOW,
                TimestampPresetMode.TODAY,
                -> values[fieldId] = FieldValue.Timestamp(RecordedAt.now(precision, clock))
                TimestampPresetMode.LITERAL -> Unit
            }
        }

        if (resolvedPreset == null && tracker.quickAdd.mode != QuickAddMode.OPEN_EDITOR) {
            tracker.fields.singleOrNull()?.let { field ->
                when (field.kind) {
                    FieldKind.COUNTER -> values[field.id] = FieldValue.Integer(field.counterQuickDelta)
                    else -> Unit
                }
            }
        }

        val entry = TrackerEntry(
            trackerId = tracker.id,
            recordedAt = RecordedAt.now(tracker.timestampPrecision, clock),
            values = values,
            note = resolvedPreset?.note?.trim()?.ifBlank { null },
            createdAt = Instant.now(clock),
            updatedAt = Instant.now(clock),
        )

        val canRecord = tracker.kind == TrackerKind.TIMESTAMP || runCatching {
            DomainValidator.validateEntry(tracker, entry)
        }.isSuccess

        return if (canRecord && tracker.quickAdd.mode != QuickAddMode.OPEN_EDITOR) {
            repository.saveEntry(entry)
            QuickAddResult.Recorded(entry)
        } else {
            QuickAddResult.NeedsInput(tracker)
        }
    }

    suspend fun record(
        tracker: TrackerDefinition,
        values: Map<String, FieldValue>,
        recordedAt: RecordedAt = RecordedAt.now(tracker.timestampPrecision, clock),
        note: String? = null,
        entryId: String? = null,
    ): TrackerEntry {
        val existing = entryId?.let { repository.getEntry(it) }
        val now = Instant.now(clock)
        val entry = TrackerEntry(
            id = existing?.id ?: dev.opentrack.app.domain.model.newId(),
            trackerId = tracker.id,
            recordedAt = recordedAt,
            values = values,
            note = note?.trim()?.ifBlank { null },
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        DomainValidator.validateEntry(tracker, entry)
        repository.saveEntry(entry)
        return entry
    }

    suspend fun undo(entryId: String) {
        repository.deleteEntry(entryId)
    }
}

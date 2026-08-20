package dev.opentrack.app.presentation

import dev.opentrack.app.domain.model.EnumPayloadKind
import dev.opentrack.app.domain.model.FieldKind
import dev.opentrack.app.domain.model.FieldValue
import dev.opentrack.app.domain.model.RecordedAt
import dev.opentrack.app.domain.model.TrackerDefinition
import dev.opentrack.app.domain.model.TrackerEntry
import dev.opentrack.app.ui.model.BuilderFieldKindUi
import dev.opentrack.app.ui.model.BuilderPayloadKindUi
import dev.opentrack.app.ui.model.QuickLogAction
import dev.opentrack.app.ui.model.QuickLogFieldUi
import dev.opentrack.app.ui.model.QuickLogOptionUi
import dev.opentrack.app.ui.model.QuickLogUiState
import java.time.Clock
import java.time.Duration

internal object QuickLogLogic {
    fun initial(
        tracker: TrackerDefinition,
        existing: TrackerEntry? = null,
        clock: Clock = Clock.systemDefaultZone(),
    ): QuickLogDraft {
        val activeFields = tracker.fields.filter { it.archivedAt == null }.sortedBy { it.order }
        val first = activeFields.firstOrNull()
        val recordedAt = existing?.recordedAt ?: RecordedAt.now(tracker.timestampPrecision, clock)
        val fieldTimestamps = activeFields.mapNotNull { field ->
            if (field.kind != FieldKind.TIMESTAMP) return@mapNotNull null
            val stored = (existing?.values?.get(field.id) as? FieldValue.Timestamp)?.value
            val value = stored ?: if (field.required) {
                RecordedAt.now(field.timestampPrecision ?: tracker.timestampPrecision, clock)
            } else null
            value?.let { field.id to it }
        }.toMap()
        val firstValue = first?.let { existing?.values?.get(it.id) }
        val selectedOptionId = (firstValue as? FieldValue.Choice)?.optionId
        val options = when (first?.kind) {
            FieldKind.ENUM, FieldKind.RADIO -> first.options.filter { it.archivedAt == null }.map {
                QuickLogOptionUi(
                    id = it.id,
                    label = it.label,
                    supporting = it.payloadLabel,
                    selected = it.id == selectedOptionId,
                    payloadKind = it.payloadKind.toUi(),
                    payloadUnit = it.payloadUnit,
                )
            }
            FieldKind.BOOLEAN -> listOf(
                QuickLogOptionUi("true", "Yes", selected = (firstValue as? FieldValue.BooleanValue)?.value == true),
                QuickLogOptionUi("false", "No", selected = (firstValue as? FieldValue.BooleanValue)?.value == false),
            )
            else -> emptyList()
        }
        val value = when (firstValue) {
            is FieldValue.Choice -> firstValue.payload?.editable().orEmpty()
            null -> ""
            else -> firstValue.editable()
        }
        val fields = if (tracker.kind == dev.opentrack.app.domain.model.TrackerKind.GROUP) {
            activeFields.map { field ->
                val stored = existing?.values?.get(field.id)
                val selectedId = (stored as? FieldValue.Choice)?.optionId
                    ?: (stored as? FieldValue.BooleanValue)?.value?.toString()
                QuickLogFieldUi(
                    id = field.id,
                    label = field.label,
                    value = when (stored) {
                        is FieldValue.Choice -> stored.payload?.editable().orEmpty()
                        null -> if (field.kind == FieldKind.TIMESTAMP && field.required) {
                            fieldTimestamps[field.id]?.fullLabel().orEmpty()
                        } else ""
                        else -> stored.editable()
                    },
                    kind = field.kind.toUi(),
                    options = when (field.kind) {
                        FieldKind.ENUM, FieldKind.RADIO -> field.options.filter { it.archivedAt == null }.map { option ->
                            QuickLogOptionUi(
                                id = option.id,
                                label = option.label,
                                supporting = option.payloadLabel,
                                selected = selectedId == option.id,
                                payloadKind = option.payloadKind.toUi(),
                                payloadUnit = option.payloadUnit,
                            )
                        }
                        FieldKind.BOOLEAN -> listOf(
                            QuickLogOptionUi("true", "Yes", selected = selectedId == "true"),
                            QuickLogOptionUi("false", "No", selected = selectedId == "false"),
                        )
                        else -> emptyList()
                    },
                    placeholder = placeholder(field.kind),
                    required = field.required,
                    suffix = field.unit,
                )
            }
        } else emptyList()
        val ui = QuickLogUiState(
            trackerId = tracker.id,
            title = if (existing == null) "Log ${tracker.name}" else "Edit ${tracker.name}",
            kind = tracker.kind.toUi(),
            glyph = tracker.glyphUi(),
            accent = tracker.accentUi(),
            timestampLabel = recordedAt.fullLabel(),
            value = value,
            unit = first?.unit,
            options = options,
            fields = fields,
            counterDelta = (firstValue as? FieldValue.Integer)?.value?.toInt()
                ?: first?.counterQuickDelta?.toInt()
                ?: 1,
            note = existing?.note.orEmpty(),
            editingEntryId = existing?.id,
        )
        val draft = QuickLogDraft(
            tracker = tracker,
            ui = ui,
            editingEntryId = existing?.id,
            recordedAt = recordedAt,
            fieldTimestamps = fieldTimestamps,
        )
        return draft.copy(ui = validate(draft))
    }

    fun reduce(draft: QuickLogDraft, action: QuickLogAction): QuickLogDraft {
        val current = draft.ui
        val next = when (action) {
            QuickLogAction.Dismiss,
            QuickLogAction.Save,
            QuickLogAction.Delete,
            QuickLogAction.EditTimestamp,
            -> current
            QuickLogAction.CounterIncrement -> current.copy(counterDelta = current.counterDelta + 1)
            QuickLogAction.CounterDecrement -> current.copy(counterDelta = current.counterDelta - 1)
            QuickLogAction.CounterCorrection -> current.copy(counterDelta = -current.counterDelta)
            is QuickLogAction.CounterDeltaChanged -> current.copy(
                counterDelta = action.value.coerceIn(-9999, 9999),
                errorMessage = null,
            )
            is QuickLogAction.ValueChanged -> current.copy(value = action.value, errorMessage = null)
            is QuickLogAction.OptionSelected -> current.copy(
                options = current.options.map { it.copy(selected = it.id == action.id) },
                errorMessage = null,
            )
            is QuickLogAction.FieldChanged -> current.copy(
                fields = current.fields.map { if (it.id == action.id) it.copy(value = action.value, error = null) else it },
                errorMessage = null,
            )
            is QuickLogAction.FieldOptionSelected -> current.copy(
                fields = current.fields.map { field ->
                    if (field.id == action.fieldId) {
                        field.copy(options = field.options.map { it.copy(selected = it.id == action.optionId) }, error = null)
                    } else field
                },
                errorMessage = null,
            )
            is QuickLogAction.EditFieldTimestamp -> current.copy(
                fields = current.fields.map { field ->
                    if (field.id == action.fieldId) field.copy(value = RecordedAt.now(
                        draft.tracker.fields.firstOrNull { it.id == action.fieldId }?.timestampPrecision
                            ?: draft.tracker.timestampPrecision,
                    ).fullLabel(), error = null) else field
                },
            )
            is QuickLogAction.NoteChanged -> current.copy(note = action.value)
        }
        val nextDraft = draft.copy(ui = next)
        return nextDraft.copy(ui = validate(nextDraft))
    }

    fun values(draft: QuickLogDraft): Map<String, FieldValue> {
        val tracker = draft.tracker
        val fields = tracker.fields.filter { it.archivedAt == null }.sortedBy { it.order }
        if (tracker.kind == dev.opentrack.app.domain.model.TrackerKind.TIMESTAMP) return emptyMap()
        if (tracker.kind == dev.opentrack.app.domain.model.TrackerKind.GROUP) {
            return fields.mapNotNull { field ->
                val uiField = draft.ui.fields.firstOrNull { it.id == field.id }
                val raw = uiField?.value.orEmpty()
                val selected = uiField?.options?.firstOrNull { it.selected }?.id
                if (raw.isBlank() && selected == null && !field.required) null
                else if (field.kind == FieldKind.TIMESTAMP) {
                    field.id to FieldValue.Timestamp(
                        draft.fieldTimestamps[field.id] ?: error("Choose ${field.label.lowercase()}")
                    )
                } else field.id to parse(field.kind, raw, field, selected)
            }.toMap()
        }
        val field = fields.single()
        val selected = draft.ui.options.firstOrNull { it.selected }?.id
        val value = when (field.kind) {
            FieldKind.COUNTER -> FieldValue.Integer(draft.ui.counterDelta.toLong())
            FieldKind.ENUM, FieldKind.RADIO, FieldKind.BOOLEAN -> parse(field.kind, draft.ui.value, field, selected)
            else -> parse(field.kind, draft.ui.value, field, selected)
        }
        return mapOf(field.id to value)
    }

    fun withSaving(draft: QuickLogDraft, saving: Boolean) = draft.copy(ui = draft.ui.copy(saving = saving))

    fun withTimestamp(draft: QuickLogDraft, fieldId: String?, value: RecordedAt): QuickLogDraft {
        val updated = if (fieldId == null) draft.copy(
            recordedAt = value,
            ui = draft.ui.copy(timestampLabel = value.fullLabel()),
        ) else draft.copy(
            fieldTimestamps = draft.fieldTimestamps + (fieldId to value),
            ui = draft.ui.copy(fields = draft.ui.fields.map { field ->
                if (field.id == fieldId) field.copy(value = value.fullLabel(), error = null) else field
            }),
        )
        return updated.copy(ui = validate(updated))
    }

    fun withError(draft: QuickLogDraft, message: String) = draft.copy(
        ui = draft.ui.copy(saving = false, errorMessage = message),
    )

    private fun validate(draft: QuickLogDraft): QuickLogUiState {
        val valid = runCatching {
            val values = values(draft)
            dev.opentrack.app.domain.model.DomainValidator.validateEntry(
                draft.tracker,
                TrackerEntry(
                    id = draft.editingEntryId ?: "validation",
                    trackerId = draft.tracker.id,
                    recordedAt = draft.recordedAt ?: RecordedAt.now(draft.tracker.timestampPrecision),
                    values = values,
                    note = draft.ui.note.trim().ifBlank { null },
                ),
            )
        }.isSuccess
        return draft.ui.copy(canSave = valid && !draft.ui.saving)
    }

    private fun parse(
        kind: FieldKind,
        raw: String,
        field: dev.opentrack.app.domain.model.TrackerField,
        selected: String?,
    ): FieldValue = when (kind) {
        FieldKind.VALUE -> FieldValue.Decimal(
            parseDecimalInput(raw)?.takeIf { it.isFinite() } ?: error("Enter a finite number"),
        )
        FieldKind.COUNTER -> FieldValue.Integer(raw.trim().toLongOrNull() ?: error("Enter a whole number"))
        FieldKind.BOOLEAN -> when (selected ?: raw.trim().lowercase()) {
            "true", "yes", "1" -> FieldValue.BooleanValue(true)
            "false", "no", "0" -> FieldValue.BooleanValue(false)
            else -> error("Choose Yes or No")
        }
        FieldKind.DURATION -> FieldValue.DurationValue(parseDuration(raw, field.unit))
        FieldKind.TIMESTAMP -> FieldValue.Timestamp(
            RecordedAt.now(field.timestampPrecision ?: dev.opentrack.app.domain.model.TimestampPrecision.DATE_TIME),
        )
        FieldKind.ENUM, FieldKind.RADIO -> {
            val optionId = selected ?: field.options.firstOrNull {
                it.id == raw || it.label.equals(raw.trim(), ignoreCase = true)
            }?.id ?: error("Choose an option")
            val option = field.options.firstOrNull { it.id == optionId } ?: error("Choose an option")
            val payload = when (option.payloadKind) {
                EnumPayloadKind.NONE -> null
                EnumPayloadKind.DECIMAL -> FieldValue.Decimal(
                    parseDecimalInput(raw)?.takeIf { it.isFinite() } ?: error("Enter a finite number"),
                )
                EnumPayloadKind.INTEGER -> FieldValue.Integer(raw.trim().toLongOrNull() ?: error("Enter a whole number"))
                EnumPayloadKind.DURATION -> FieldValue.DurationValue(parseDuration(raw, option.payloadUnit))
                EnumPayloadKind.TEXT -> FieldValue.Text(raw.trim().ifBlank { error("Enter a value") })
            }
            FieldValue.Choice(optionId, payload)
        }
    }

    private fun parseDuration(raw: String, unit: String?): Duration {
        val trimmed = raw.trim()
        if (":" in trimmed) {
            val parts = trimmed.split(':').map { it.toLongOrNull() ?: error("Use hours:minutes") }
            require(parts.size == 2) { "Use hours:minutes" }
            require(parts[0] >= 0 && parts[1] in 0..59) { "Use nonnegative hours and 0–59 minutes" }
            return Duration.ofHours(parts[0]).plusMinutes(parts[1])
        }
        val amount = parseDecimalInput(trimmed)?.takeIf { it.isFinite() } ?: error("Enter a duration")
        require(amount >= 0) { "Duration cannot be negative" }
        val secondsPerUnit = when (unit?.trim()?.lowercase()) {
            "s", "sec", "secs", "second", "seconds" -> 1.0
            "h", "hr", "hrs", "hour", "hours" -> 3_600.0
            "d", "day", "days" -> 86_400.0
            else -> 60.0
        }
        return Duration.ofMillis((amount * secondsPerUnit * 1_000).toLong())
    }

    private fun placeholder(kind: FieldKind) = when (kind) {
        FieldKind.VALUE -> "0.0"
        FieldKind.COUNTER -> "0"
        FieldKind.BOOLEAN -> "Yes or No"
        FieldKind.DURATION -> "Minutes or h:mm"
        FieldKind.TIMESTAMP -> "Now"
        FieldKind.ENUM, FieldKind.RADIO -> "Option"
    }

    private fun FieldKind.toUi() = when (this) {
        FieldKind.TIMESTAMP -> BuilderFieldKindUi.MOMENT
        FieldKind.VALUE -> BuilderFieldKindUi.NUMBER
        FieldKind.ENUM -> BuilderFieldKindUi.CHOICE
        FieldKind.RADIO -> BuilderFieldKindUi.RATING
        FieldKind.BOOLEAN -> BuilderFieldKindUi.BOOLEAN
        FieldKind.COUNTER -> BuilderFieldKindUi.COUNTER
        FieldKind.DURATION -> BuilderFieldKindUi.DURATION
    }

    private fun EnumPayloadKind.toUi() = when (this) {
        EnumPayloadKind.NONE -> BuilderPayloadKindUi.NONE
        EnumPayloadKind.DECIMAL -> BuilderPayloadKindUi.NUMBER
        EnumPayloadKind.INTEGER -> BuilderPayloadKindUi.INTEGER
        EnumPayloadKind.DURATION -> BuilderPayloadKindUi.DURATION
        EnumPayloadKind.TEXT -> BuilderPayloadKindUi.TEXT
    }

    private fun FieldValue.editable(): String = when (this) {
        is FieldValue.Decimal -> value.toString()
        is FieldValue.Integer -> value.toString()
        is FieldValue.BooleanValue -> if (value) "Yes" else "No"
        is FieldValue.DurationValue -> (value.seconds / 60.0).toString().removeSuffix(".0")
        is FieldValue.Text -> value
        is FieldValue.Choice -> optionId
        is FieldValue.Timestamp -> value.fullLabel()
    }
}

package dev.opentrack.app.presentation

import dev.opentrack.app.domain.model.ChoiceOption
import dev.opentrack.app.domain.model.CalendarSpan
import dev.opentrack.app.domain.model.CalendarRange
import dev.opentrack.app.domain.model.CalendarWeekStart
import dev.opentrack.app.domain.model.DomainValidator
import dev.opentrack.app.domain.model.EnumPayloadKind
import dev.opentrack.app.domain.model.FieldKind
import dev.opentrack.app.domain.model.FieldValue
import dev.opentrack.app.domain.model.QuickAddConfig
import dev.opentrack.app.domain.model.QuickAddMode
import dev.opentrack.app.domain.model.QuickPreset
import dev.opentrack.app.domain.model.TimestampPrecision
import dev.opentrack.app.domain.model.TimestampCalendarConfig
import dev.opentrack.app.domain.model.TrackerDefinition
import dev.opentrack.app.domain.model.TrackerField
import dev.opentrack.app.domain.model.TrackerKind
import dev.opentrack.app.domain.model.newId
import dev.opentrack.app.ui.model.BuilderFieldKindUi
import dev.opentrack.app.ui.model.CalendarSpanUi
import dev.opentrack.app.ui.model.CalendarRangeUi
import dev.opentrack.app.ui.model.CalendarWeekStartUi
import dev.opentrack.app.ui.model.BuilderFieldUi
import dev.opentrack.app.ui.model.BuilderOptionUi
import dev.opentrack.app.ui.model.BuilderPayloadKindUi
import dev.opentrack.app.ui.model.QuickLogModeUi
import dev.opentrack.app.ui.model.TimestampPrecisionUi
import dev.opentrack.app.ui.model.TrackerBuilderAction
import dev.opentrack.app.ui.model.TrackerBuilderUiState
import dev.opentrack.app.ui.model.TrackerKindUi
import dev.opentrack.app.ui.theme.SignalPalette
import java.time.Instant
import java.time.Clock

internal object TrackerBuilderLogic {
    private const val LAST_STEP = 3

    fun initial(existing: TrackerDefinition? = null): TrackerBuilderUiState {
        if (existing == null) return TrackerBuilderUiState(
            accent = SignalPalette.Moss,
            templates = builderTemplateUi(),
            canContinue = false,
        )
        return fromDefinition(existing, editing = true)
    }

    fun fromTemplate(
        key: String,
        metric: Boolean = true,
        clock: Clock = Clock.systemUTC(),
    ): TrackerBuilderUiState = fromDefinition(
        definition = starterTemplateDefinition(key, metric, clock),
        editing = false,
        selectedTemplateId = key,
    )

    private fun fromDefinition(
        definition: TrackerDefinition,
        editing: Boolean,
        selectedTemplateId: String? = null,
    ): TrackerBuilderUiState {
        val existing = definition
        val activeFields = existing.fields.filter { it.archivedAt == null }.sortedBy { it.order }
        val primary = activeFields.firstOrNull()
        return recalculate(
            TrackerBuilderUiState(
                editingTrackerId = existing.id.takeIf { editing },
                selectedTemplateId = selectedTemplateId,
                templates = builderTemplateUi(),
                name = existing.name,
                kind = existing.kind.toUi(),
                glyph = existing.glyphUi(),
                accent = existing.accentUi(),
                precision = existing.timestampPrecision.toUi(),
                calendarShowDayNumber = existing.timestampCalendar.showDayNumber,
                calendarShowCount = existing.timestampCalendar.showCount,
                calendarShowWeekdayHeader = existing.timestampCalendar.showWeekdayHeader,
                calendarWeekStart = existing.timestampCalendar.weekStart.toUi(),
                calendarSpan = existing.timestampCalendar.span.toUi(),
                calendarRange = existing.timestampCalendar.range.toUi(),
                calendarShowEmptyDays = existing.timestampCalendar.showEmptyDays,
                unit = primary?.unit.orEmpty(),
                options = primary?.options.orEmpty().filter { it.archivedAt == null }
                    .map { it.toUi(payloadKindLocked = editing) },
                fields = if (existing.kind == TrackerKind.GROUP) activeFields.map { field ->
                    BuilderFieldUi(
                        id = field.id,
                        label = field.label,
                        kind = field.kind.toUi(),
                        required = field.required,
                        unit = field.unit.orEmpty(),
                        options = field.options.filter { it.archivedAt == null }
                            .map { it.toUi(payloadKindLocked = editing) },
                        structureLocked = editing,
                        requiredLocked = editing,
                    )
                } else emptyList(),
                quickLogMode = existing.quickAdd.mode.toUi(),
                quickPreset = existing.presets.firstOrNull { it.id == existing.quickAdd.defaultPresetId }
                    ?.let { preset ->
                        val value = primary?.id?.let(preset.values::get) ?: preset.values.values.firstOrNull()
                        value?.toEditableString(primary)
                    }
                    .orEmpty(),
                counterDelta = primary?.counterQuickDelta?.toInt() ?: 1,
                addToDashboard = true,
                canContinue = true,
            ),
        )
    }

    fun reduce(state: TrackerBuilderUiState, action: TrackerBuilderAction): TrackerBuilderUiState {
        val next = when (action) {
            TrackerBuilderAction.Back -> state.copy(step = (state.step - 1).coerceAtLeast(0), errorMessage = null)
            TrackerBuilderAction.Next -> if (canContinue(state)) {
                state.copy(step = (state.step + 1).coerceAtMost(LAST_STEP), errorMessage = null)
            } else state.copy(errorMessage = validationMessage(state))
            TrackerBuilderAction.Save -> state
            is TrackerBuilderAction.TemplateSelected -> if (state.editingTrackerId == null) {
                fromTemplate(action.id).copy(
                    step = state.step,
                    addToDashboard = state.addToDashboard,
                    templates = state.templates,
                )
            } else state
            is TrackerBuilderAction.NameChanged -> state.copy(name = action.value, errorMessage = null)
            is TrackerBuilderAction.KindSelected -> if (state.editingTrackerId == null) {
                withDefaults(state.copy(kind = action.kind, selectedTemplateId = null, errorMessage = null))
            } else state
            is TrackerBuilderAction.PrecisionSelected -> state.copy(precision = action.precision)
            is TrackerBuilderAction.AccentSelected -> state.copy(accent = action.accent)
            is TrackerBuilderAction.CalendarShowDayNumberChanged -> state.copy(calendarShowDayNumber = action.value)
            is TrackerBuilderAction.CalendarShowCountChanged -> state.copy(calendarShowCount = action.value)
            is TrackerBuilderAction.CalendarShowWeekdayHeaderChanged -> state.copy(calendarShowWeekdayHeader = action.value)
            is TrackerBuilderAction.CalendarWeekStartChanged -> state.copy(calendarWeekStart = action.value)
            is TrackerBuilderAction.CalendarSpanChanged -> state.copy(calendarSpan = action.value)
            is TrackerBuilderAction.CalendarRangeChanged -> state.copy(calendarRange = action.value)
            is TrackerBuilderAction.CalendarShowEmptyDaysChanged -> state.copy(calendarShowEmptyDays = action.value)
            is TrackerBuilderAction.UnitChanged -> if (state.editingTrackerId == null) {
                state.copy(unit = action.value)
            } else state
            is TrackerBuilderAction.QuickModeSelected -> state.copy(
                quickLogMode = if (state.kind in setOf(
                        TrackerKindUi.MOMENT,
                        TrackerKindUi.COUNTER,
                        TrackerKindUi.GROUP,
                    )
                ) QuickLogModeUi.SMART else action.mode,
            )
            is TrackerBuilderAction.QuickPresetChanged -> state.copy(quickPreset = action.value)
            is TrackerBuilderAction.CounterDeltaChanged -> state.copy(
                counterDelta = action.value.coerceIn(-9999, 9999).let { if (it == 0) 1 else it },
            )
            is TrackerBuilderAction.AddToDashboardChanged -> state.copy(addToDashboard = action.value)

            TrackerBuilderAction.AddOption -> state.copy(
                options = state.options + newOption(state.options.size),
                editingOptionId = null,
            )
            is TrackerBuilderAction.EditOption -> state.copy(editingOptionId = action.id)
            is TrackerBuilderAction.RemoveOption -> state.copy(
                options = state.options.filterNot { it.id == action.id },
                editingOptionId = state.editingOptionId.takeUnless { it == action.id },
            )
            is TrackerBuilderAction.OptionLabelChanged -> state.copy(options = state.options.update(action.id) {
                it.copy(label = action.value)
            })
            is TrackerBuilderAction.OptionPayloadKindChanged -> state.copy(options = state.options.update(action.id) {
                if (it.payloadKindLocked) it else it.copy(payloadKind = action.value)
            })
            is TrackerBuilderAction.OptionPayloadLabelChanged -> state.copy(options = state.options.update(action.id) {
                it.copy(payloadLabel = action.value)
            })
            is TrackerBuilderAction.OptionPayloadUnitChanged -> state.copy(options = state.options.update(action.id) {
                if (it.payloadKindLocked) it else it.copy(payloadUnit = action.value)
            })
            TrackerBuilderAction.CloseOptionEditor -> state.copy(editingOptionId = null)

            TrackerBuilderAction.AddField -> {
                val field = BuilderFieldUi(
                    id = newId(),
                    label = "Field ${state.fields.size + 1}",
                    kind = BuilderFieldKindUi.NUMBER,
                    requiredLocked = state.editingTrackerId != null,
                )
                state.copy(fields = state.fields + field, editingFieldId = field.id)
            }
            is TrackerBuilderAction.EditField -> state.copy(editingFieldId = action.id)
            is TrackerBuilderAction.RemoveField -> state.copy(
                fields = state.fields.filterNot { it.id == action.id },
                editingFieldId = state.editingFieldId.takeUnless { it == action.id },
            )
            is TrackerBuilderAction.FieldLabelChanged -> state.copy(fields = state.fields.update(action.id) {
                it.copy(label = action.value)
            })
            is TrackerBuilderAction.FieldKindChanged -> state.copy(fields = state.fields.update(action.id) { field ->
                if (field.structureLocked) field else field.copy(
                    kind = action.value,
                    options = if (action.value.supportsOptions && field.options.isEmpty()) {
                        defaultOptions(action.value)
                    } else if (!action.value.supportsOptions) emptyList() else field.options,
                )
            })
            is TrackerBuilderAction.FieldRequiredChanged -> state.copy(fields = state.fields.update(action.id) {
                if (it.requiredLocked) it else it.copy(required = action.value)
            })
            is TrackerBuilderAction.FieldUnitChanged -> state.copy(fields = state.fields.update(action.id) {
                if (it.structureLocked) it else it.copy(unit = action.value)
            })
            is TrackerBuilderAction.FieldAddOption -> state.copy(fields = state.fields.update(action.fieldId) { field ->
                field.copy(options = field.options + newOption(field.options.size))
            })
            is TrackerBuilderAction.FieldRemoveOption -> state.copy(fields = state.fields.update(action.fieldId) { field ->
                field.copy(options = field.options.filterNot { it.id == action.optionId })
            })
            is TrackerBuilderAction.FieldOptionLabelChanged -> state.updateFieldOption(action.fieldId, action.optionId) {
                it.copy(label = action.value)
            }
            is TrackerBuilderAction.FieldOptionPayloadKindChanged -> state.updateFieldOption(action.fieldId, action.optionId) {
                if (it.payloadKindLocked) it else it.copy(payloadKind = action.value)
            }
            is TrackerBuilderAction.FieldOptionPayloadLabelChanged -> state.updateFieldOption(action.fieldId, action.optionId) {
                it.copy(payloadLabel = action.value)
            }
            is TrackerBuilderAction.FieldOptionPayloadUnitChanged -> state.updateFieldOption(action.fieldId, action.optionId) {
                if (it.payloadKindLocked) it else it.copy(payloadUnit = action.value)
            }
            TrackerBuilderAction.CloseFieldEditor -> state.copy(editingFieldId = null)
        }
        return recalculate(next)
    }

    fun build(state: TrackerBuilderUiState, existing: TrackerDefinition? = null): TrackerDefinition {
        val kindUi = requireNotNull(state.kind) { "Choose a tracker type" }
        val kind = kindUi.toDomain()
        val precision = state.precision.toDomain()
        val previousFields = existing?.fields.orEmpty().associateBy { it.id }
        val fields = when (kind) {
            TrackerKind.TIMESTAMP -> emptyList()
            TrackerKind.GROUP -> state.fields.mapIndexed { index, field ->
                val previous = previousFields[field.id]
                TrackerField(
                    id = field.id,
                    label = field.label.trim(),
                    kind = previous?.kind ?: field.kind.toDomain(),
                    required = previous?.required ?: field.required,
                    order = index,
                    unit = previous?.unit ?: field.unit.trim().ifBlank { null },
                    decimalPlaces = previous?.decimalPlaces ?: 2,
                    counterQuickDelta = previous?.counterQuickDelta ?: 1,
                    timestampPrecision = if (field.kind == BuilderFieldKindUi.MOMENT) {
                        previous?.timestampPrecision ?: precision
                    } else null,
                    options = field.options.mapIndexed { optionIndex, option ->
                        option.toDomain(
                            fieldKind = field.kind,
                            index = optionIndex,
                            previous = previous?.options?.firstOrNull { it.id == option.id },
                        )
                    },
                    archivedAt = previous?.archivedAt,
                )
            }
            else -> {
                val previous = existing?.fields?.firstOrNull { it.archivedAt == null }
                val fieldKind = if (kind == TrackerKind.RADIO) BuilderFieldKindUi.RATING else BuilderFieldKindUi.CHOICE
                listOf(
                    TrackerField(
                        id = previous?.id ?: newId(),
                        label = previous?.label ?: standaloneFieldLabel(kind),
                        kind = previous?.kind ?: kindUi.toDomainField(),
                        required = previous?.required ?: true,
                        unit = previous?.unit ?: state.unit.trim().ifBlank { null },
                        decimalPlaces = previous?.decimalPlaces ?: 2,
                        counterQuickDelta = if (kind == TrackerKind.COUNTER) {
                            state.counterDelta.toLong()
                        } else previous?.counterQuickDelta ?: 1,
                        timestampPrecision = previous?.timestampPrecision,
                        options = if (kind == TrackerKind.ENUM || kind == TrackerKind.RADIO) {
                            state.options.mapIndexed { index, option ->
                                option.toDomain(
                                    fieldKind = fieldKind,
                                    index = index,
                                    previous = previous?.options?.firstOrNull { it.id == option.id },
                                )
                            }
                        } else emptyList(),
                        archivedAt = previous?.archivedAt,
                    ),
                )
            }
        }
        require(kind != TrackerKind.GROUP || fields.isNotEmpty()) { "Add at least one field" }
        val preset = buildPreset(state, kind, fields)
        if (state.quickLogMode == QuickLogModeUi.PRESET && kind !in setOf(
                TrackerKind.TIMESTAMP,
                TrackerKind.COUNTER,
                TrackerKind.GROUP,
            )
        ) {
            require(preset != null) { "Enter a valid one-tap value or exact option name" }
        }
        val now = Instant.now()
        val definition = TrackerDefinition(
            id = existing?.id ?: newId(),
            name = state.name.trim(),
            description = existing?.description,
            kind = kind,
            timestampPrecision = precision,
            iconKey = state.glyph.name,
            colorArgb = state.accent.value.toLong(),
            timestampCalendar = TimestampCalendarConfig(
                showDayNumber = state.calendarShowDayNumber,
                showCount = state.calendarShowCount,
                showWeekdayHeader = state.calendarShowWeekdayHeader,
                weekStart = state.calendarWeekStart.toDomain(),
                span = state.calendarSpan.toDomain(),
                range = state.calendarRange.toDomain(),
                showEmptyDays = state.calendarShowEmptyDays,
            ),
            order = existing?.order ?: 0,
            fields = fields,
            presets = preset?.let(::listOf).orEmpty(),
            quickAdd = QuickAddConfig(
                mode = if (kind == TrackerKind.GROUP) {
                    QuickAddMode.OPEN_EDITOR
                } else if (kind in setOf(TrackerKind.TIMESTAMP, TrackerKind.COUNTER)) {
                    QuickAddMode.AUTO
                } else when (state.quickLogMode) {
                    QuickLogModeUi.SMART -> QuickAddMode.AUTO
                    QuickLogModeUi.PRESET -> if (preset == null) QuickAddMode.OPEN_EDITOR else QuickAddMode.DEFAULT_PRESET
                },
                defaultPresetId = preset?.id,
            ),
            archivedAt = existing?.archivedAt,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        DomainValidator.validate(definition)
        return definition
    }

    private fun buildPreset(
        state: TrackerBuilderUiState,
        kind: TrackerKind,
        fields: List<TrackerField>,
    ): QuickPreset? {
        if (state.quickLogMode != QuickLogModeUi.PRESET || state.quickPreset.isBlank()) return null
        val field = fields.singleOrNull() ?: return null
        val raw = state.quickPreset.trim()
        val value = when (kind) {
            TrackerKind.VALUE -> parseDecimalInput(raw)?.takeIf { it.isFinite() }?.let(FieldValue::Decimal)
            TrackerKind.COUNTER -> raw.toLongOrNull()?.let(FieldValue::Integer)
            TrackerKind.BOOLEAN -> when (raw.lowercase()) {
                "true", "yes", "1" -> FieldValue.BooleanValue(true)
                "false", "no", "0" -> FieldValue.BooleanValue(false)
                else -> null
            }
            TrackerKind.ENUM, TrackerKind.RADIO -> field.options.firstOrNull {
                it.label.equals(raw, ignoreCase = true) && it.payloadKind == EnumPayloadKind.NONE
            }?.let { FieldValue.Choice(it.id) }
            TrackerKind.DURATION -> parseDuration(raw, field.unit)?.let(FieldValue::DurationValue)
            else -> null
        } ?: return null
        return QuickPreset(label = "Quick add", values = mapOf(field.id to value))
    }

    private fun recalculate(state: TrackerBuilderUiState) = state.copy(canContinue = canContinue(state))

    private fun canContinue(state: TrackerBuilderUiState): Boolean = when (state.step) {
        0 -> state.name.isNotBlank() && state.kind != null
        1 -> when (state.kind) {
            TrackerKindUi.CHOICE -> state.options.isNotEmpty() && state.options.all { it.label.isNotBlank() }
            TrackerKindUi.RATING -> state.options.size >= 2 && state.options.all { it.label.isNotBlank() }
            TrackerKindUi.GROUP -> state.fields.isNotEmpty() && state.fields.all { field ->
                field.label.isNotBlank() && when (field.kind) {
                    BuilderFieldKindUi.CHOICE -> field.options.isNotEmpty()
                    BuilderFieldKindUi.RATING -> field.options.size >= 2
                    else -> true
                }
            }
            else -> true
        }
        else -> true
    }

    private fun validationMessage(state: TrackerBuilderUiState) = when {
        state.name.isBlank() -> "Give this tracker a name"
        state.kind == null -> "Choose what you want to track"
        state.kind == TrackerKindUi.RATING && state.options.size < 2 -> "Add at least two rating options"
        state.kind == TrackerKindUi.CHOICE && state.options.isEmpty() -> "Add at least one choice"
        state.kind == TrackerKindUi.GROUP && state.fields.isEmpty() -> "Add at least one field"
        else -> "Complete the labels and options on this step"
    }

    private fun withDefaults(state: TrackerBuilderUiState): TrackerBuilderUiState = when (state.kind) {
        TrackerKindUi.CHOICE -> if (state.options.isEmpty()) {
            state.copy(options = defaultOptions(BuilderFieldKindUi.CHOICE))
        } else state
        TrackerKindUi.RATING -> if (state.options.size < 2) {
            state.copy(options = defaultOptions(BuilderFieldKindUi.RATING))
        } else state
        TrackerKindUi.GROUP -> if (state.fields.isEmpty()) state.copy(
            fields = listOf(BuilderFieldUi(newId(), "Value", BuilderFieldKindUi.NUMBER, required = true)),
        ) else state
        else -> state
    }

    private fun standaloneFieldLabel(kind: TrackerKind) = when (kind) {
        TrackerKind.VALUE -> "Value"
        TrackerKind.ENUM -> "Choice"
        TrackerKind.RADIO -> "Rating"
        TrackerKind.BOOLEAN -> "State"
        TrackerKind.COUNTER -> "Amount"
        TrackerKind.DURATION -> "Duration"
        else -> "Value"
    }

    private val BuilderFieldKindUi.supportsOptions: Boolean
        get() = this == BuilderFieldKindUi.CHOICE || this == BuilderFieldKindUi.RATING

    private fun defaultOptions(kind: BuilderFieldKindUi): List<BuilderOptionUi> = when (kind) {
        BuilderFieldKindUi.RATING -> listOf("Low", "Normal", "High").map { BuilderOptionUi(newId(), it) }
        else -> listOf("Option 1", "Option 2").map { BuilderOptionUi(newId(), it) }
    }

    private fun newOption(index: Int) = BuilderOptionUi(newId(), "Option ${index + 1}")

    private fun ChoiceOption.toUi(payloadKindLocked: Boolean = false) = BuilderOptionUi(
        id = id,
        label = label,
        payloadKind = payloadKind.toUi(),
        payloadLabel = payloadLabel.orEmpty(),
        payloadUnit = payloadUnit.orEmpty(),
        payloadKindLocked = payloadKindLocked,
    )

    private fun BuilderOptionUi.toDomain(
        fieldKind: BuilderFieldKindUi,
        index: Int,
        previous: ChoiceOption? = null,
    ) = ChoiceOption(
        id = id,
        label = label.trim(),
        colorArgb = previous?.colorArgb,
        order = index,
        radioScore = if (fieldKind == BuilderFieldKindUi.RATING) {
            previous?.radioScore ?: (index + 1).toDouble()
        } else null,
        payloadKind = previous?.payloadKind
            ?: if (fieldKind == BuilderFieldKindUi.RATING) EnumPayloadKind.NONE else payloadKind.toDomain(),
        payloadLabel = payloadLabel.trim().ifBlank { null },
        payloadUnit = previous?.payloadUnit ?: payloadUnit.trim().ifBlank { null },
        archivedAt = previous?.archivedAt,
    )

    private fun BuilderPayloadKindUi.toDomain() = when (this) {
        BuilderPayloadKindUi.NONE -> EnumPayloadKind.NONE
        BuilderPayloadKindUi.NUMBER -> EnumPayloadKind.DECIMAL
        BuilderPayloadKindUi.INTEGER -> EnumPayloadKind.INTEGER
        BuilderPayloadKindUi.DURATION -> EnumPayloadKind.DURATION
        BuilderPayloadKindUi.TEXT -> EnumPayloadKind.TEXT
    }

    private fun EnumPayloadKind.toUi() = when (this) {
        EnumPayloadKind.NONE -> BuilderPayloadKindUi.NONE
        EnumPayloadKind.DECIMAL -> BuilderPayloadKindUi.NUMBER
        EnumPayloadKind.INTEGER -> BuilderPayloadKindUi.INTEGER
        EnumPayloadKind.DURATION -> BuilderPayloadKindUi.DURATION
        EnumPayloadKind.TEXT -> BuilderPayloadKindUi.TEXT
    }

    private fun TrackerKindUi.toDomainField(): FieldKind = when (this) {
        TrackerKindUi.MOMENT -> FieldKind.TIMESTAMP
        TrackerKindUi.NUMBER -> FieldKind.VALUE
        TrackerKindUi.CHOICE -> FieldKind.ENUM
        TrackerKindUi.RATING -> FieldKind.RADIO
        TrackerKindUi.BOOLEAN -> FieldKind.BOOLEAN
        TrackerKindUi.COUNTER -> FieldKind.COUNTER
        TrackerKindUi.DURATION -> FieldKind.DURATION
        TrackerKindUi.GROUP -> FieldKind.VALUE
    }

    private fun BuilderFieldKindUi.toDomain() = when (this) {
        BuilderFieldKindUi.MOMENT -> FieldKind.TIMESTAMP
        BuilderFieldKindUi.NUMBER -> FieldKind.VALUE
        BuilderFieldKindUi.CHOICE -> FieldKind.ENUM
        BuilderFieldKindUi.RATING -> FieldKind.RADIO
        BuilderFieldKindUi.BOOLEAN -> FieldKind.BOOLEAN
        BuilderFieldKindUi.COUNTER -> FieldKind.COUNTER
        BuilderFieldKindUi.DURATION -> FieldKind.DURATION
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

    private fun TimestampPrecision.toUi() = when (this) {
        TimestampPrecision.DAY -> TimestampPrecisionUi.DAY
        TimestampPrecision.DATE_TIME -> TimestampPrecisionUi.DATE_AND_TIME
    }

    private fun TimestampPrecisionUi.toDomain() = when (this) {
        TimestampPrecisionUi.DAY -> TimestampPrecision.DAY
        TimestampPrecisionUi.DATE_AND_TIME -> TimestampPrecision.DATE_TIME
    }

    private fun CalendarWeekStart.toUi() = when (this) {
        CalendarWeekStart.APP_DEFAULT -> CalendarWeekStartUi.APP_DEFAULT
        CalendarWeekStart.MONDAY -> CalendarWeekStartUi.MONDAY
        CalendarWeekStart.SUNDAY -> CalendarWeekStartUi.SUNDAY
    }

    private fun CalendarWeekStartUi.toDomain() = when (this) {
        CalendarWeekStartUi.APP_DEFAULT -> CalendarWeekStart.APP_DEFAULT
        CalendarWeekStartUi.MONDAY -> CalendarWeekStart.MONDAY
        CalendarWeekStartUi.SUNDAY -> CalendarWeekStart.SUNDAY
    }

    private fun CalendarSpan.toUi() = when (this) {
        CalendarSpan.ONE_WEEK -> CalendarSpanUi.ONE_WEEK
        CalendarSpan.TWO_WEEKS -> CalendarSpanUi.TWO_WEEKS
    }

    private fun CalendarSpanUi.toDomain() = when (this) {
        CalendarSpanUi.ONE_WEEK -> CalendarSpan.ONE_WEEK
        CalendarSpanUi.TWO_WEEKS -> CalendarSpan.TWO_WEEKS
    }

    private fun CalendarRange.toUi() = when (this) {
        CalendarRange.FOUR_WEEKS -> CalendarRangeUi.FOUR_WEEKS
        CalendarRange.SIX_WEEKS -> CalendarRangeUi.SIX_WEEKS
        CalendarRange.TWELVE_WEEKS -> CalendarRangeUi.TWELVE_WEEKS
    }

    private fun CalendarRangeUi.toDomain() = when (this) {
        CalendarRangeUi.FOUR_WEEKS -> CalendarRange.FOUR_WEEKS
        CalendarRangeUi.SIX_WEEKS -> CalendarRange.SIX_WEEKS
        CalendarRangeUi.TWELVE_WEEKS -> CalendarRange.TWELVE_WEEKS
    }

    private fun QuickAddMode.toUi() = when (this) {
        QuickAddMode.AUTO, QuickAddMode.OPEN_EDITOR -> QuickLogModeUi.SMART
        QuickAddMode.DEFAULT_PRESET -> QuickLogModeUi.PRESET
    }

    private fun FieldValue.toEditableString(field: TrackerField?): String = when (this) {
        is FieldValue.Decimal -> value.toString()
        is FieldValue.Integer -> value.toString()
        is FieldValue.BooleanValue -> if (value) "Yes" else "No"
        is FieldValue.DurationValue -> value.toMinutes().toString()
        is FieldValue.Text -> value
        is FieldValue.Choice -> field?.options?.firstOrNull { it.id == optionId }?.label ?: optionId
        is FieldValue.Timestamp -> value.localDate.toString()
    }

    private fun parseDuration(raw: String, unit: String?): java.time.Duration? {
        val trimmed = raw.trim()
        if (":" in trimmed) {
            val parts = trimmed.split(':').map { it.toLongOrNull() ?: return null }
            if (parts.size != 2 || parts[0] < 0 || parts[1] !in 0..59) return null
            return java.time.Duration.ofHours(parts[0]).plusMinutes(parts[1])
        }
        val amount = parseDecimalInput(trimmed)?.takeIf { it.isFinite() && it >= 0 } ?: return null
        val secondsPerUnit = when (unit?.trim()?.lowercase()) {
            "s", "sec", "secs", "second", "seconds" -> 1.0
            "h", "hr", "hrs", "hour", "hours" -> 3_600.0
            "d", "day", "days" -> 86_400.0
            else -> 60.0
        }
        return java.time.Duration.ofMillis((amount * secondsPerUnit * 1_000).toLong())
    }

    private fun <T> List<T>.update(id: String, transform: (T) -> T): List<T> where T : Any = map { value ->
        val valueId = when (value) {
            is BuilderOptionUi -> value.id
            is BuilderFieldUi -> value.id
            else -> null
        }
        if (valueId == id) transform(value) else value
    }

    private fun TrackerBuilderUiState.updateFieldOption(
        fieldId: String,
        optionId: String,
        transform: (BuilderOptionUi) -> BuilderOptionUi,
    ) = copy(fields = fields.update(fieldId) { field ->
        field.copy(options = field.options.update(optionId, transform))
    })
}

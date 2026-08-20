package dev.opentrack.app.data.mapper

import dev.opentrack.app.data.local.ChoiceOptionEntity
import dev.opentrack.app.data.local.DashboardEntity
import dev.opentrack.app.data.local.DashboardSeriesEntity
import dev.opentrack.app.data.local.DashboardWidgetEntity
import dev.opentrack.app.data.local.EntryEntity
import dev.opentrack.app.data.local.EntryValueEntity
import dev.opentrack.app.data.local.QuickAddConfigEntity
import dev.opentrack.app.data.local.QuickPresetEntity
import dev.opentrack.app.data.local.QuickPresetValueEntity
import dev.opentrack.app.data.local.StoredValueColumns
import dev.opentrack.app.data.local.TrackerEntity
import dev.opentrack.app.data.local.TrackerFieldEntity
import dev.opentrack.app.domain.model.ChoiceOption
import dev.opentrack.app.domain.model.Dashboard
import dev.opentrack.app.domain.model.DashboardSeries
import dev.opentrack.app.domain.model.DashboardWidget
import dev.opentrack.app.domain.model.EnumPayloadKind
import dev.opentrack.app.domain.model.FieldKind
import dev.opentrack.app.domain.model.FieldValue
import dev.opentrack.app.domain.model.QuickAddConfig
import dev.opentrack.app.domain.model.QuickPreset
import dev.opentrack.app.domain.model.RecordedAt
import dev.opentrack.app.domain.model.TimestampPrecision
import dev.opentrack.app.domain.model.TrackerDefinition
import dev.opentrack.app.domain.model.TrackerEntry
import dev.opentrack.app.domain.model.TrackerField
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

internal fun TrackerDefinition.toEntity() = TrackerEntity(
    id = id,
    name = name.trim(),
    description = description?.trim()?.takeIf(String::isNotEmpty),
    kind = kind,
    timestampPrecision = timestampPrecision,
    iconKey = iconKey,
    colorArgb = colorArgb,
    position = order,
    archivedAtMillis = archivedAt?.toEpochMilli(),
    createdAtMillis = createdAt.toEpochMilli(),
    updatedAtMillis = updatedAt.toEpochMilli(),
)

internal fun TrackerField.toEntity(trackerId: String) = TrackerFieldEntity(
    id = id,
    trackerId = trackerId,
    label = label.trim(),
    kind = kind,
    required = required,
    position = order,
    unit = unit?.trim()?.takeIf(String::isNotEmpty),
    decimalPlaces = decimalPlaces,
    counterQuickDelta = counterQuickDelta,
    timestampPrecision = timestampPrecision,
    archivedAtMillis = archivedAt?.toEpochMilli(),
)

internal fun ChoiceOption.toEntity(fieldId: String) = ChoiceOptionEntity(
    id = id,
    fieldId = fieldId,
    label = label.trim(),
    colorArgb = colorArgb,
    position = order,
    radioScore = radioScore,
    payloadKind = payloadKind,
    payloadLabel = payloadLabel?.trim()?.takeIf(String::isNotEmpty),
    payloadUnit = payloadUnit?.trim()?.takeIf(String::isNotEmpty),
    archivedAtMillis = archivedAt?.toEpochMilli(),
)

internal fun QuickPreset.toEntity(trackerId: String) = QuickPresetEntity(
    id = id,
    trackerId = trackerId,
    label = label.trim(),
    position = order,
    note = note?.takeIf(String::isNotBlank),
)

internal fun QuickPreset.toValueEntities(): List<QuickPresetValueEntity> =
    (values.keys + timestampModes.keys).map { fieldId ->
        QuickPresetValueEntity(
            presetId = id,
            fieldId = fieldId,
            timestampMode = timestampModes[fieldId],
            stored = values[fieldId]?.toStored() ?: StoredValueColumns(),
        )
    }

internal fun TrackerDefinition.toQuickAddEntity() = QuickAddConfigEntity(
    trackerId = id,
    mode = quickAdd.mode,
    defaultPresetId = quickAdd.defaultPresetId,
)

internal fun TrackerEntry.toEntity(): EntryEntity {
    val timestamp = recordedAt.toColumns()
    return EntryEntity(
        id = id,
        trackerId = trackerId,
        precision = recordedAt.precision,
        localEpochDay = recordedAt.localDate.toEpochDay(),
        instantMillis = timestamp.instantMillis,
        zoneId = timestamp.zoneId,
        offsetSeconds = timestamp.offsetSeconds,
        localSecondOfDay = timestamp.localSecondOfDay,
        note = note?.takeIf(String::isNotBlank),
        createdAtMillis = createdAt.toEpochMilli(),
        updatedAtMillis = updatedAt.toEpochMilli(),
    )
}

internal fun TrackerEntry.toValueEntities(): List<EntryValueEntity> = values.map { (fieldId, value) ->
    EntryValueEntity(id, fieldId, value.toStored())
}

internal fun Dashboard.toEntity() = DashboardEntity(id, name.trim(), order)
internal fun DashboardWidget.toEntity(dashboardId: String) = DashboardWidgetEntity(
    id, dashboardId, kind, title?.trim()?.takeIf(String::isNotEmpty), chartStyle, range, bucket,
    order, span, visible,
)
internal fun DashboardSeries.toEntity(widgetId: String) = DashboardSeriesEntity(
    id, widgetId, trackerId, fieldId, optionId, metric, aggregation, colorArgb, order, presetId,
)

internal fun assembleDefinitions(
    trackers: List<TrackerEntity>,
    fields: List<TrackerFieldEntity>,
    options: List<ChoiceOptionEntity>,
    presets: List<QuickPresetEntity>,
    presetValues: List<QuickPresetValueEntity>,
    configs: List<QuickAddConfigEntity>,
): List<TrackerDefinition> {
    val optionsByField = options.groupBy { it.fieldId }
    val fieldsByTracker = fields.groupBy { it.trackerId }
    val valuesByPreset = presetValues.groupBy { it.presetId }
    val presetsByTracker = presets.groupBy { it.trackerId }
    val configByTracker = configs.associateBy { it.trackerId }

    return trackers.map { tracker ->
        val domainFields = fieldsByTracker[tracker.id].orEmpty()
            .sortedBy { it.position }
            .map { field -> field.toDomain(optionsByField[field.id].orEmpty()) }
        val fieldMap = domainFields.associateBy { it.id }
        val domainPresets = presetsByTracker[tracker.id].orEmpty().sortedBy { it.position }.map { preset ->
            preset.toDomain(valuesByPreset[preset.id].orEmpty(), fieldMap)
        }
        val config = configByTracker[tracker.id]
        TrackerDefinition(
            id = tracker.id,
            name = tracker.name,
            description = tracker.description,
            kind = tracker.kind,
            timestampPrecision = tracker.timestampPrecision,
            iconKey = tracker.iconKey,
            colorArgb = tracker.colorArgb,
            order = tracker.position,
            fields = domainFields,
            presets = domainPresets,
            quickAdd = QuickAddConfig(config?.mode ?: dev.opentrack.app.domain.model.QuickAddMode.AUTO, config?.defaultPresetId),
            archivedAt = tracker.archivedAtMillis?.let(Instant::ofEpochMilli),
            createdAt = Instant.ofEpochMilli(tracker.createdAtMillis),
            updatedAt = Instant.ofEpochMilli(tracker.updatedAtMillis),
        )
    }.sortedWith(compareBy(TrackerDefinition::order, TrackerDefinition::name))
}

internal fun assembleEntries(
    entries: List<EntryEntity>,
    values: List<EntryValueEntity>,
    definitions: List<TrackerDefinition>,
): List<TrackerEntry> {
    val fields = definitions.flatMap { it.fields }.associateBy { it.id }
    val valuesByEntry = values.groupBy { it.entryId }
    return entries.map { entry ->
        val domainValues = valuesByEntry[entry.id].orEmpty().mapNotNull { stored ->
            val field = fields[stored.fieldId] ?: return@mapNotNull null
            stored.stored.toDomain(field)?.let { stored.fieldId to it }
        }.toMap()
        TrackerEntry(
            id = entry.id,
            trackerId = entry.trackerId,
            recordedAt = entry.toRecordedAt(),
            values = domainValues,
            note = entry.note,
            createdAt = Instant.ofEpochMilli(entry.createdAtMillis),
            updatedAt = Instant.ofEpochMilli(entry.updatedAtMillis),
        )
    }
}

internal fun assembleDashboards(
    dashboards: List<DashboardEntity>,
    widgets: List<DashboardWidgetEntity>,
    series: List<DashboardSeriesEntity>,
): List<Dashboard> {
    val seriesByWidget = series.groupBy { it.widgetId }
    val widgetsByDashboard = widgets.groupBy { it.dashboardId }
    return dashboards.sortedBy { it.position }.map { dashboard ->
        Dashboard(
            id = dashboard.id,
            name = dashboard.name,
            order = dashboard.position,
            widgets = widgetsByDashboard[dashboard.id].orEmpty().sortedBy { it.position }.map { widget ->
                DashboardWidget(
                    id = widget.id,
                    kind = widget.kind,
                    title = widget.title,
                    chartStyle = widget.chartStyle,
                    range = widget.rangePreset,
                    bucket = widget.bucket,
                    order = widget.position,
                    span = widget.span,
                    visible = widget.visible,
                    series = seriesByWidget[widget.id].orEmpty().sortedBy { it.position }.map { item ->
                        DashboardSeries(
                            id = item.id,
                            trackerId = item.trackerId,
                            fieldId = item.fieldId,
                            optionId = item.optionId,
                            metric = item.metric,
                            aggregation = item.aggregation,
                            colorArgb = item.colorArgb,
                            order = item.position,
                            presetId = item.presetId,
                        )
                    },
                )
            },
        )
    }
}

private fun TrackerFieldEntity.toDomain(options: List<ChoiceOptionEntity>) = TrackerField(
    id = id,
    label = label,
    kind = kind,
    required = required,
    order = position,
    unit = unit,
    decimalPlaces = decimalPlaces,
    counterQuickDelta = counterQuickDelta,
    timestampPrecision = timestampPrecision,
    options = options.sortedBy { it.position }.map { it.toDomain() },
    archivedAt = archivedAtMillis?.let(Instant::ofEpochMilli),
)

private fun ChoiceOptionEntity.toDomain() = ChoiceOption(
    id, label, colorArgb, position, radioScore, payloadKind, payloadLabel, payloadUnit,
    archivedAtMillis?.let(Instant::ofEpochMilli),
)

private fun QuickPresetEntity.toDomain(
    storedValues: List<QuickPresetValueEntity>,
    fields: Map<String, TrackerField>,
): QuickPreset {
    val values = storedValues.mapNotNull { item ->
        val field = fields[item.fieldId] ?: return@mapNotNull null
        item.stored.toDomain(field)?.let { item.fieldId to it }
    }.toMap()
    val modes = storedValues.mapNotNull { item -> item.timestampMode?.let { item.fieldId to it } }.toMap()
    return QuickPreset(id, label, position, values, modes, note)
}

private fun RecordedAt.toColumns(): StoredValueColumns = when (this) {
    is RecordedAt.Day -> StoredValueColumns(
        timestampPrecision = TimestampPrecision.DAY,
        localEpochDay = localDate.toEpochDay(),
    )
    is RecordedAt.DateTime -> {
        val local = instant.atOffset(recordedOffset)
        StoredValueColumns(
            timestampPrecision = TimestampPrecision.DATE_TIME,
            localEpochDay = localDate.toEpochDay(),
            instantMillis = instant.toEpochMilli(),
            zoneId = zoneId.id,
            offsetSeconds = recordedOffset.totalSeconds,
            localSecondOfDay = local.toLocalTime().toSecondOfDay(),
        )
    }
}

private fun FieldValue.toStored(): StoredValueColumns = when (this) {
    is FieldValue.Decimal -> StoredValueColumns(decimalValue = value)
    is FieldValue.Integer -> StoredValueColumns(integerValue = value)
    is FieldValue.BooleanValue -> StoredValueColumns(booleanValue = value)
    is FieldValue.DurationValue -> StoredValueColumns(durationMillis = value.toMillis())
    is FieldValue.Text -> StoredValueColumns(textValue = value)
    is FieldValue.Timestamp -> value.toColumns()
    is FieldValue.Choice -> {
        val payloadColumns = payload?.toStored() ?: StoredValueColumns()
        payloadColumns.copy(optionId = optionId)
    }
}

private fun StoredValueColumns.toDomain(field: TrackerField): FieldValue? = when (field.kind) {
    FieldKind.VALUE -> decimalValue?.let(FieldValue::Decimal)
    FieldKind.COUNTER -> integerValue?.let(FieldValue::Integer)
    FieldKind.BOOLEAN -> booleanValue?.let(FieldValue::BooleanValue)
    FieldKind.DURATION -> durationMillis?.let { FieldValue.DurationValue(Duration.ofMillis(it)) }
    FieldKind.TIMESTAMP -> toRecordedAt()?.let(FieldValue::Timestamp)
    FieldKind.RADIO -> optionId?.let { FieldValue.Choice(it) }
    FieldKind.ENUM -> optionId?.let { selectedId ->
        val option = field.options.firstOrNull { it.id == selectedId } ?: return@let null
        FieldValue.Choice(selectedId, payload(option.payloadKind))
    }
}

private fun StoredValueColumns.payload(kind: EnumPayloadKind): FieldValue? = when (kind) {
    EnumPayloadKind.NONE -> null
    EnumPayloadKind.DECIMAL -> decimalValue?.let(FieldValue::Decimal)
    EnumPayloadKind.INTEGER -> integerValue?.let(FieldValue::Integer)
    EnumPayloadKind.DURATION -> durationMillis?.let { FieldValue.DurationValue(Duration.ofMillis(it)) }
    EnumPayloadKind.TEXT -> textValue?.let(FieldValue::Text)
}

private fun EntryEntity.toRecordedAt(): RecordedAt = when (precision) {
    TimestampPrecision.DAY -> RecordedAt.Day(LocalDate.ofEpochDay(localEpochDay))
    TimestampPrecision.DATE_TIME -> RecordedAt.DateTime(
        instant = Instant.ofEpochMilli(requireNotNull(instantMillis)),
        zoneId = ZoneId.of(requireNotNull(zoneId)),
        recordedOffset = ZoneOffset.ofTotalSeconds(requireNotNull(offsetSeconds)),
    )
}

private fun StoredValueColumns.toRecordedAt(): RecordedAt? = when (timestampPrecision) {
    TimestampPrecision.DAY -> localEpochDay?.let { RecordedAt.Day(LocalDate.ofEpochDay(it)) }
    TimestampPrecision.DATE_TIME -> if (instantMillis != null && zoneId != null && offsetSeconds != null) {
        RecordedAt.DateTime(
            Instant.ofEpochMilli(instantMillis),
            ZoneId.of(zoneId),
            ZoneOffset.ofTotalSeconds(offsetSeconds),
        )
    } else null
    null -> null
}

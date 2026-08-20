package dev.opentrack.app.presentation

import androidx.compose.ui.graphics.Color
import dev.opentrack.app.domain.model.Aggregation
import dev.opentrack.app.domain.model.AnalyticsMetric
import dev.opentrack.app.domain.model.ChartStyle
import dev.opentrack.app.domain.model.CalendarSpan
import dev.opentrack.app.domain.model.CalendarRange
import dev.opentrack.app.domain.model.CalendarWeekStart
import dev.opentrack.app.domain.model.Dashboard
import dev.opentrack.app.domain.model.DashboardSeries
import dev.opentrack.app.domain.model.DashboardWidget
import dev.opentrack.app.domain.model.FieldKind
import dev.opentrack.app.domain.model.FieldValue
import dev.opentrack.app.domain.model.TrackerField
import dev.opentrack.app.domain.model.TimeRangePreset
import dev.opentrack.app.domain.model.TimeBucket
import dev.opentrack.app.domain.model.TrackerDefinition
import dev.opentrack.app.domain.model.TrackerEntry
import dev.opentrack.app.domain.model.TrackerKind
import dev.opentrack.app.domain.model.TimestampCalendarConfig
import dev.opentrack.app.domain.model.WidgetSpan
import dev.opentrack.app.preferences.ThemeMode
import dev.opentrack.app.preferences.UserPreferences
import dev.opentrack.app.ui.model.CalendarDayUi
import dev.opentrack.app.ui.model.CalendarGridUi
import dev.opentrack.app.ui.model.ChartBarUi
import dev.opentrack.app.ui.model.ChartPointUi
import dev.opentrack.app.ui.model.DashboardEditorItemUi
import dev.opentrack.app.ui.model.DashboardEditorUiState
import dev.opentrack.app.ui.model.DashboardUiState
import dev.opentrack.app.ui.model.DashboardWidgetUi
import dev.opentrack.app.ui.model.DateRangeUi
import dev.opentrack.app.ui.model.DetailChartUi
import dev.opentrack.app.ui.model.DistributionPartUi
import dev.opentrack.app.ui.model.EntryUi
import dev.opentrack.app.ui.model.HistoryDayUi
import dev.opentrack.app.ui.model.HistoryUiState
import dev.opentrack.app.ui.model.InsightUi
import dev.opentrack.app.ui.model.SettingsUiState
import dev.opentrack.app.ui.model.TimestampPrecisionUi
import dev.opentrack.app.ui.model.TrackerDetailUiState
import dev.opentrack.app.ui.model.TrackerSummaryUi
import dev.opentrack.app.ui.model.WidgetChartUi
import dev.opentrack.app.ui.model.WidgetSizeUi
import dev.opentrack.app.ui.theme.SignalPalette
import java.time.Clock
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

internal object UiModelMapper {
    fun trackerSummaries(
        definitions: List<TrackerDefinition>,
        entries: List<TrackerEntry>,
        includeArchived: Boolean,
        clock: Clock,
    ): List<TrackerSummaryUi> {
        val today = LocalDate.now(clock)
        return definitions
            .filter { includeArchived || it.archivedAt == null }
            .sortedWith(compareBy<TrackerDefinition> { it.order }.thenBy { it.name.lowercase() })
            .map { definition ->
                val latest = entries.asSequence()
                    .filter { it.trackerId == definition.id }
                    .maxWithOrNull(recordedEntryComparator)
                TrackerSummaryUi(
                    id = definition.id,
                    name = definition.name,
                    kind = definition.kind.toUi(),
                    glyph = definition.glyphUi(),
                    accent = definition.accentUi(),
                    lastValue = latest?.primaryValue(definition) ?: "No entries yet",
                    lastTracked = latest?.recordedAt?.fullLabel(today) ?: "Never tracked",
                    pinned = true,
                    archived = definition.archivedAt != null,
                )
            }
    }

    fun history(
        definitions: List<TrackerDefinition>,
        entries: List<TrackerEntry>,
        query: String,
        trackerId: String?,
        range: DateRangeUi?,
        clock: Clock,
    ): HistoryUiState {
        val definitionsById = definitions.associateBy { it.id }
        val normalizedQuery = query.trim().lowercase()
        val today = LocalDate.now(clock)
        val visible = entries.asSequence()
            .filter { trackerId == null || it.trackerId == trackerId }
            .filter { range == null || it in filterRange(entries, range, today) }
            .mapNotNull { entry -> definitionsById[entry.trackerId]?.let { entry to it } }
            .filter { (entry, definition) ->
                normalizedQuery.isBlank() ||
                    definition.name.lowercase().contains(normalizedQuery) ||
                    entry.primaryValue(definition).lowercase().contains(normalizedQuery) ||
                    entry.note.orEmpty().lowercase().contains(normalizedQuery)
            }
            .sortedWith { left, right -> recordedEntryComparator.compare(right.first, left.first) }
            .toList()
        val days = visible.groupBy { it.first.recordedAt.localDate }.map { (date, values) ->
            HistoryDayUi(
                label = when (date) {
                    today -> "Today"
                    today.minusDays(1) -> "Yesterday"
                    else -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                },
                entries = values.map { (entry, definition) -> entry.toUi(definition, today) },
            )
        }
        return HistoryUiState(
            days = days,
            trackerFilterLabel = trackerId?.let { definitionsById[it]?.name } ?: "All trackers",
            dateFilterLabel = range?.let { "Last ${it.label}" } ?: "Any time",
            query = query,
            isLoading = false,
        )
    }

    fun dashboard(
        definitions: List<TrackerDefinition>,
        entries: List<TrackerEntry>,
        dashboards: List<Dashboard>,
        clock: Clock,
        weekStartsMonday: Boolean = true,
    ): DashboardUiState {
        val today = LocalDate.now(clock)
        val definitionsById = definitions.associateBy { it.id }
        val configured = dashboards.firstOrNull()?.widgets
            .orEmpty()
            .filter { it.visible }
            .sortedBy { it.order }
            .mapNotNull { widget ->
                val tracker = widget.series.firstNotNullOfOrNull { definitionsById[it.trackerId] }
                    ?: return@mapNotNull null
                widget.toUi(tracker, entries.filter { it.trackerId == tracker.id }, today, weekStartsMonday)
            }
        val widgets = if (dashboards.isNotEmpty()) configured else definitions
            .filter { it.archivedAt == null }
            .sortedBy { it.order }
            .take(6)
            .map { tracker ->
                val trackerEntries = entries.filter { it.trackerId == tracker.id }
                derivedWidget(tracker, trackerEntries, today, weekStartsMonday)
            }
        return DashboardUiState(
            dateLabel = today.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")),
            widgets = widgets,
            isLoading = false,
        )
    }

    fun dashboardEditor(
        definitions: List<TrackerDefinition>,
        entries: List<TrackerEntry>,
        dashboards: List<Dashboard>,
        clock: Clock,
        weekStartsMonday: Boolean = true,
    ): DashboardEditorUiState {
        val today = LocalDate.now(clock)
        val definitionsById = definitions.associateBy { it.id }
        return DashboardEditorUiState(
            items = dashboards.firstOrNull()?.widgets.orEmpty().sortedBy { it.order }.mapNotNull { widget ->
                val tracker = widget.series.firstNotNullOfOrNull { definitionsById[it.trackerId] }
                    ?: return@mapNotNull null
                DashboardEditorItemUi(
                    widget = widget.toUi(tracker, entries.filter { it.trackerId == tracker.id }, today, weekStartsMonday),
                    visible = widget.visible,
                )
            },
            hasTrackers = definitions.any { it.archivedAt == null },
        )
    }

    fun detail(
        definition: TrackerDefinition,
        allEntries: List<TrackerEntry>,
        tab: dev.opentrack.app.ui.model.DetailTabUi,
        range: DateRangeUi,
        clock: Clock,
        weekStartsMonday: Boolean = true,
    ): TrackerDetailUiState {
        val today = LocalDate.now(clock)
        val entries = filterRange(allEntries.filter { it.trackerId == definition.id }, range, today)
            .sortedWith(recordedEntryComparator)
        val latest = entries.lastOrNull()
        val summary = trackerSummaries(listOf(definition), allEntries, true, clock).single()
        val numeric = entries.mapNotNull { it.numericValue(definition) }
        val charts = detailCharts(definition, entries, today, weekStartsMonday)
        val insights = buildList {
            add(InsightUi("Entries", entries.size.toString()))
            if (numeric.isNotEmpty()) {
                add(InsightUi("Average", formatNumber(numeric.average())))
                add(InsightUi("Range", "${formatNumber(numeric.min())}–${formatNumber(numeric.max())}"))
            } else if (entries.isNotEmpty()) {
                val trackedDays = entries.map { it.recordedAt.localDate }.distinct().size
                add(InsightUi("Active days", trackedDays.toString()))
            }
        }
        return TrackerDetailUiState(
            tracker = summary,
            tab = tab,
            range = range,
            headline = latest?.primaryValue(definition) ?: "No entries yet",
            headlineContext = latest?.recordedAt?.fullLabel(today) ?: "Tap + to record your first entry",
            insights = insights,
            charts = charts,
            entries = entries.asReversed().map { it.toUi(definition, today) },
        )
    }

    fun settings(preferences: UserPreferences) = SettingsUiState(
        themeLabel = when (preferences.themeMode) {
            ThemeMode.SYSTEM -> "System default"
            ThemeMode.LIGHT -> "Light"
            ThemeMode.DARK -> "Dark"
        },
        weekStartsOnLabel = if (preferences.weekStartsMonday) "Monday" else "Sunday",
        defaultPrecision = when (preferences.defaultTimestampPrecision) {
            dev.opentrack.app.domain.model.TimestampPrecision.DAY -> TimestampPrecisionUi.DAY
            dev.opentrack.app.domain.model.TimestampPrecision.DATE_TIME -> TimestampPrecisionUi.DATE_AND_TIME
        },
    )

    private fun TrackerEntry.toUi(definition: TrackerDefinition, today: LocalDate) = EntryUi(
        id = id,
        trackerId = trackerId,
        trackerName = definition.name,
        glyph = definition.glyphUi(),
        accent = definition.accentUi(),
        primaryValue = primaryValue(definition),
        supportingValue = supportingValue(definition),
        timeLabel = recordedAt.timeLabel(),
        dayLabel = recordedAt.dayLabel(today),
    )

    private fun DashboardWidget.toUi(
        tracker: TrackerDefinition,
        entries: List<TrackerEntry>,
        today: LocalDate,
        weekStartsMonday: Boolean,
    ): DashboardWidgetUi {
        val ranged = filterRange(entries, range.toUi(), today).sortedWith(recordedEntryComparator)
        val latest = ranged.lastOrNull()
        val configured = series.single()
        val field = configured.fieldId?.let { id -> tracker.fields.firstOrNull { it.id == id } }
            ?: tracker.fields.firstOrNull { it.archivedAt == null }
        val values = configured.values(field, ranged)
        return DashboardWidgetUi(
            id = id,
            trackerId = tracker.id,
            title = title?.takeIf { it.isNotBlank() } ?: tracker.name,
            glyph = tracker.glyphUi(),
            accent = tracker.accentUi(),
            metric = configured.metricLabel(tracker, field, ranged, values, today),
            context = when (configured.metric) {
                AnalyticsMetric.LAST_RECORDED -> "${ranged.size} ${if (ranged.size == 1) "entry" else "entries"}"
                else -> latest?.recordedAt?.fullLabel(today) ?: "No entries yet"
            },
            chart = configured.chart(
                chartStyle, bucket, field, ranged, values, today,
                tracker.timestampCalendar, weekStartsMonday,
            ),
            size = if (span == WidgetSpan.WIDE) WidgetSizeUi.WIDE else WidgetSizeUi.COMPACT,
        )
    }

    private data class SeriesValue(val entry: TrackerEntry, val value: Double)

    private fun DashboardSeries.values(
        field: TrackerField?,
        entries: List<TrackerEntry>,
    ): List<SeriesValue> = entries.mapNotNull { entry ->
        val numeric = when (metric) {
            AnalyticsMetric.OCCURRENCE_COUNT, AnalyticsMetric.LAST_RECORDED -> 1.0
            AnalyticsMetric.ENUM_COUNT -> {
                val choice = field?.let { entry.values[it.id] } as? FieldValue.Choice
                if (choice != null && (optionId == null || choice.optionId == optionId)) 1.0 else null
            }
            AnalyticsMetric.TRUE_COUNT -> {
                val value = field?.let { entry.values[it.id] } as? FieldValue.BooleanValue
                if (value?.value == true) 1.0 else null
            }
            AnalyticsMetric.TRUE_RATE -> {
                val value = field?.let { entry.values[it.id] } as? FieldValue.BooleanValue
                value?.let { if (it.value) 1.0 else 0.0 }
            }
            AnalyticsMetric.LATEST_VALUE,
            AnalyticsMetric.NUMERIC_VALUE,
            AnalyticsMetric.ENUM_PAYLOAD,
            AnalyticsMetric.RADIO_SCORE,
            AnalyticsMetric.COUNTER_SUM,
            AnalyticsMetric.COUNTER_RUNNING_TOTAL,
            AnalyticsMetric.DURATION_TOTAL,
            AnalyticsMetric.DURATION_AVERAGE -> field?.numericValue(entry, optionId)
        }
        numeric?.takeIf { it.isFinite() }?.let { SeriesValue(entry, it) }
    }

    private fun TrackerField.numericValue(entry: TrackerEntry, optionFilter: String?): Double? = when (val value = entry.values[id]) {
        is FieldValue.Decimal -> value.value
        is FieldValue.Integer -> value.value.toDouble()
        is FieldValue.DurationValue -> value.value.toDisplayAmount(unit)
        is FieldValue.BooleanValue -> if (value.value) 1.0 else 0.0
        is FieldValue.Choice -> {
            if (optionFilter != null && value.optionId != optionFilter) null
            else {
                val option = options.firstOrNull { it.id == value.optionId }
                option?.radioScore ?: when (val payload = value.payload) {
                    is FieldValue.Decimal -> payload.value
                    is FieldValue.Integer -> payload.value.toDouble()
                    is FieldValue.DurationValue -> payload.value.toDisplayAmount(option?.payloadUnit)
                    else -> null
                }
            }
        }
        else -> null
    }

    private fun DashboardSeries.metricLabel(
        tracker: TrackerDefinition,
        field: TrackerField?,
        entries: List<TrackerEntry>,
        values: List<SeriesValue>,
        today: LocalDate,
    ): String = when (metric) {
        AnalyticsMetric.LAST_RECORDED -> entries.lastOrNull()?.recordedAt?.fullLabel(today) ?: "Not tracked"
        AnalyticsMetric.OCCURRENCE_COUNT -> "${entries.size} recorded"
        AnalyticsMetric.ENUM_COUNT, AnalyticsMetric.TRUE_COUNT -> formatNumber(values.sumOf { it.value }, 0)
        AnalyticsMetric.TRUE_RATE -> values.takeIf { it.isNotEmpty() }
            ?.let { "${(it.map(SeriesValue::value).average() * 100).roundToInt()}%" } ?: "Not tracked"
        AnalyticsMetric.LATEST_VALUE -> values.lastOrNull()?.value
            ?.let { formatNumber(it, field?.decimalPlaces ?: 2) }
            ?: entries.lastOrNull()?.primaryValue(tracker)
            ?: "Not tracked"
        AnalyticsMetric.COUNTER_SUM, AnalyticsMetric.COUNTER_RUNNING_TOTAL, AnalyticsMetric.DURATION_TOTAL ->
            values.takeIf { it.isNotEmpty() }?.sumOf { it.value }
                ?.let { formatNumber(it, field?.decimalPlaces ?: 2) } ?: "Not tracked"
        AnalyticsMetric.DURATION_AVERAGE -> values.takeIf { it.isNotEmpty() }?.map(SeriesValue::value)?.average()
            ?.let { formatNumber(it, field?.decimalPlaces ?: 2) } ?: "Not tracked"
        AnalyticsMetric.NUMERIC_VALUE, AnalyticsMetric.ENUM_PAYLOAD, AnalyticsMetric.RADIO_SCORE ->
            aggregate(values.map(SeriesValue::value), aggregation)?.let {
                formatNumber(it, field?.decimalPlaces ?: 2)
            } ?: "Not tracked"
    }

    private fun DashboardSeries.chart(
        style: ChartStyle,
        bucket: TimeBucket,
        field: TrackerField?,
        entries: List<TrackerEntry>,
        sourceValues: List<SeriesValue>,
        today: LocalDate,
        calendarConfig: TimestampCalendarConfig,
        weekStartsMonday: Boolean,
    ): WidgetChartUi {
        if (entries.isEmpty()) return WidgetChartUi.None
        val resolvedStyle = if (style != ChartStyle.AUTO) style else when (metric) {
            AnalyticsMetric.OCCURRENCE_COUNT, AnalyticsMetric.LAST_RECORDED -> ChartStyle.CALENDAR
            AnalyticsMetric.ENUM_COUNT, AnalyticsMetric.TRUE_COUNT, AnalyticsMetric.TRUE_RATE -> ChartStyle.DISTRIBUTION
            else -> ChartStyle.LINE
        }
        if (resolvedStyle == ChartStyle.CALENDAR) {
            return WidgetChartUi.Calendar(
                calendar(entries, today, calendarConfig, weekStartsMonday),
                "${entries.size} entries",
            )
        }
        if (resolvedStyle == ChartStyle.DISTRIBUTION) {
            val parts = field?.let { distribution(it, entries) }.orEmpty()
            if (parts.isNotEmpty()) return WidgetChartUi.Distribution(parts, "${entries.size} entries")
        }
        val values = if (metric == AnalyticsMetric.COUNTER_RUNNING_TOTAL) {
            var total = 0.0
            sourceValues.map { value -> total += value.value; value.copy(value = total) }
        } else sourceValues
        if (values.isEmpty()) return WidgetChartUi.None
        if (resolvedStyle == ChartStyle.BAR) {
            val bars = values.groupBy { it.entry.bucketLabel(bucket) }.map { (label, points) ->
                val value = aggregate(points.map(SeriesValue::value), aggregation) ?: 0.0
                ChartBarUi(label, value.toFloat(), formatNumber(value, field?.decimalPlaces ?: 2))
            }.takeLast(12)
            return WidgetChartUi.Bars(bars, "${values.size} values")
        }
        return WidgetChartUi.Sparkline(
            points = values.mapIndexed { index, value ->
                ChartPointUi(
                    x = index.toFloat(),
                    y = value.value.toFloat(),
                    label = value.entry.recordedAt.localDate.toString(),
                    valueLabel = formatNumber(value.value, field?.decimalPlaces ?: 2),
                )
            },
            summary = "${values.size} values",
        )
    }

    private fun TrackerEntry.bucketLabel(bucket: TimeBucket): String = when (bucket) {
        TimeBucket.EVENT -> id
        TimeBucket.DAY -> recordedAt.localDate.toString()
        TimeBucket.WEEK -> recordedAt.localDate.minusDays((recordedAt.localDate.dayOfWeek.value - 1).toLong()).toString()
        TimeBucket.MONTH -> recordedAt.localDate.withDayOfMonth(1).toString()
    }

    private fun aggregate(values: List<Double>, aggregation: Aggregation): Double? {
        if (values.isEmpty()) return null
        return when (aggregation) {
            Aggregation.NONE, Aggregation.LAST -> values.last()
            Aggregation.COUNT -> values.size.toDouble()
            Aggregation.SUM -> values.sum()
            Aggregation.AVERAGE -> values.average()
            Aggregation.MINIMUM -> values.min()
            Aggregation.MAXIMUM -> values.max()
        }
    }

    private fun derivedWidget(
        tracker: TrackerDefinition,
        entries: List<TrackerEntry>,
        today: LocalDate,
        weekStartsMonday: Boolean,
    ) = DashboardWidgetUi(
        id = "derived-${tracker.id}",
        trackerId = tracker.id,
        title = tracker.name,
        glyph = tracker.glyphUi(),
        accent = tracker.accentUi(),
        metric = widgetMetric(tracker, entries),
            context = entries.maxWithOrNull(recordedEntryComparator)?.recordedAt?.fullLabel(today) ?: "No entries yet",
            chart = chart(tracker, entries.sortedWith(recordedEntryComparator), today, weekStartsMonday),
    )

    private fun widgetMetric(tracker: TrackerDefinition, entries: List<TrackerEntry>): String {
        val latest = entries.maxWithOrNull(recordedEntryComparator)
        return when (tracker.kind) {
            TrackerKind.TIMESTAMP -> "${entries.size} recorded"
            TrackerKind.COUNTER -> {
                val total = entries.sumOf { it.numericValue(tracker) ?: 0.0 }
                formatNumber(total, 0)
            }
            else -> latest?.primaryValue(tracker) ?: "Not tracked"
        }
    }

    private fun chart(
        tracker: TrackerDefinition,
        entries: List<TrackerEntry>,
        today: LocalDate,
        weekStartsMonday: Boolean,
    ): WidgetChartUi = when (tracker.kind) {
        TrackerKind.VALUE, TrackerKind.COUNTER, TrackerKind.DURATION -> {
            val points = numericPoints(tracker, entries)
            if (points.isEmpty()) WidgetChartUi.None else WidgetChartUi.Sparkline(
                points = points,
                summary = "${points.size} values",
            )
        }
        TrackerKind.ENUM, TrackerKind.RADIO, TrackerKind.BOOLEAN -> {
            val parts = distribution(tracker, entries)
            if (parts.isEmpty()) WidgetChartUi.None else WidgetChartUi.Distribution(
                parts = parts,
                summary = "${entries.size} entries",
            )
        }
        TrackerKind.TIMESTAMP, TrackerKind.GROUP -> {
            val grid = calendar(entries, today, tracker.timestampCalendar, weekStartsMonday)
            if (entries.isEmpty()) WidgetChartUi.None else WidgetChartUi.Calendar(grid, "${entries.size} entries")
        }
    }

    private fun detailCharts(
        tracker: TrackerDefinition,
        entries: List<TrackerEntry>,
        today: LocalDate,
        weekStartsMonday: Boolean,
    ): List<DetailChartUi> = when (tracker.kind) {
        TrackerKind.VALUE, TrackerKind.COUNTER, TrackerKind.DURATION -> {
            val points = numericPoints(tracker, entries)
            if (points.isEmpty()) emptyList() else listOf(
                DetailChartUi.Line(
                    title = "Trend",
                    summary = "${points.size} values",
                    points = points,
                    startLabel = entries.first().recordedAt.localDate.toString(),
                    endLabel = entries.last().recordedAt.localDate.toString(),
                ),
            )
        }
        TrackerKind.ENUM, TrackerKind.RADIO, TrackerKind.BOOLEAN -> {
            val parts = distribution(tracker, entries)
            if (parts.isEmpty()) emptyList() else listOf(
                DetailChartUi.Distribution("Distribution", "${entries.size} entries", parts),
            )
        }
        TrackerKind.TIMESTAMP -> if (entries.isEmpty()) emptyList() else listOf(
            DetailChartUi.Calendar(
                "Activity",
                "${entries.size} entries",
                calendar(entries, today, tracker.timestampCalendar, weekStartsMonday),
            ),
        )
        TrackerKind.GROUP -> groupDetailCharts(tracker, entries, today, weekStartsMonday)
    }

    private fun groupDetailCharts(
        tracker: TrackerDefinition,
        entries: List<TrackerEntry>,
        today: LocalDate,
        weekStartsMonday: Boolean,
    ): List<DetailChartUi> = buildList {
        if (entries.isNotEmpty()) {
            add(
                DetailChartUi.Calendar(
                    "Activity",
                    "${entries.size} entries",
                    calendar(entries, today, TimestampCalendarConfig(), weekStartsMonday),
                ),
            )
        }
        tracker.fields.asSequence()
            .filter { it.archivedAt == null }
            .sortedBy { it.order }
            .forEach { field ->
                when (field.kind) {
                    FieldKind.VALUE, FieldKind.COUNTER, FieldKind.DURATION -> {
                        val points = numericPoints(field, entries)
                        if (points.isNotEmpty()) {
                            add(
                                DetailChartUi.Line(
                                    title = field.label,
                                    summary = buildString {
                                        append(points.size)
                                        append(if (points.size == 1) " value" else " values")
                                        field.unit?.takeIf { it.isNotBlank() }?.let { append(" in ").append(it) }
                                    },
                                    points = points,
                                    startLabel = points.first().label,
                                    endLabel = points.last().label,
                                ),
                            )
                        }
                    }
                    FieldKind.ENUM, FieldKind.RADIO, FieldKind.BOOLEAN -> {
                        val parts = distribution(field, entries)
                        val count = parts.sumOf { it.value.toDouble() }.toInt()
                        if (count > 0) {
                            add(
                                DetailChartUi.Distribution(
                                    title = field.label,
                                    summary = "$count ${if (count == 1) "entry" else "entries"}",
                                    parts = parts,
                                ),
                            )
                        }
                    }
                    FieldKind.TIMESTAMP -> {
                        val dates = entries.mapNotNull { entry ->
                            (entry.values[field.id] as? FieldValue.Timestamp)?.value?.localDate
                        }
                        if (dates.isNotEmpty()) {
                            add(
                                DetailChartUi.Calendar(
                                    title = "${field.label} activity",
                                    summary = "${dates.size} ${if (dates.size == 1) "timestamp" else "timestamps"}",
                                    grid = calendarDates(dates, today, TimestampCalendarConfig(), weekStartsMonday),
                                ),
                            )
                        }
                    }
                }
            }
    }

    private fun numericPoints(
        tracker: TrackerDefinition,
        entries: List<TrackerEntry>,
    ): List<ChartPointUi> {
        var runningTotal = 0.0
        return entries.mapIndexedNotNull { index, entry ->
            val raw = entry.numericValue(tracker) ?: return@mapIndexedNotNull null
            val value = if (tracker.kind == TrackerKind.COUNTER) {
                runningTotal += raw
                runningTotal
            } else raw
            ChartPointUi(
                x = index.toFloat(),
                y = value.toFloat(),
                label = entry.recordedAt.localDate.toString(),
                valueLabel = formatNumber(value),
            )
        }
    }

    private fun numericPoints(
        field: TrackerField,
        entries: List<TrackerEntry>,
    ): List<ChartPointUi> {
        var runningTotal = 0.0
        var pointIndex = 0
        return entries.mapNotNull { entry ->
            val raw = when (val value = entry.values[field.id]) {
                is FieldValue.Decimal -> value.value
                is FieldValue.Integer -> value.value.toDouble()
                is FieldValue.DurationValue -> value.value.toDisplayAmount(field.unit)
                else -> null
            } ?: return@mapNotNull null
            val plotted = if (field.kind == FieldKind.COUNTER) {
                runningTotal += raw
                runningTotal
            } else raw
            ChartPointUi(
                x = pointIndex++.toFloat(),
                y = plotted.toFloat(),
                label = entry.recordedAt.localDate.toString(),
                valueLabel = formatNumber(plotted, field.decimalPlaces),
            )
        }
    }

    private fun distribution(
        tracker: TrackerDefinition,
        entries: List<TrackerEntry>,
    ): List<DistributionPartUi> {
        val field = tracker.fields.firstOrNull() ?: return emptyList()
        return distribution(field, entries)
    }

    private fun distribution(
        field: TrackerField,
        entries: List<TrackerEntry>,
    ): List<DistributionPartUi> {
        val colors = listOf(SignalPalette.Moss, SignalPalette.Sky, SignalPalette.Coral, SignalPalette.Lilac)
        val labels = entries.mapNotNull { entry ->
            when (val value = entry.values[field.id]) {
                is FieldValue.Choice -> field.options.firstOrNull { it.id == value.optionId }?.label
                is FieldValue.BooleanValue -> if (value.value) "Yes" else "No"
                else -> null
            }
        }
        return labels.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.mapIndexed { index, item ->
            DistributionPartUi(item.key, item.value.toFloat(), colors[index % colors.size])
        }
    }

    private fun calendar(
        entries: List<TrackerEntry>,
        today: LocalDate,
        config: TimestampCalendarConfig,
        weekStartsMonday: Boolean,
    ): CalendarGridUi {
        return calendarDates(entries.map { it.recordedAt.localDate }, today, config, weekStartsMonday)
    }

    private fun calendarDates(
        dates: List<LocalDate>,
        today: LocalDate,
        config: TimestampCalendarConfig,
        weekStartsMonday: Boolean,
    ): CalendarGridUi {
        val counts = dates.groupingBy { it }.eachCount()
        val max = counts.values.maxOrNull()?.coerceAtLeast(1) ?: 1
        val firstDay = when (config.weekStart) {
            CalendarWeekStart.APP_DEFAULT -> if (weekStartsMonday) DayOfWeek.MONDAY else DayOfWeek.SUNDAY
            CalendarWeekStart.MONDAY -> DayOfWeek.MONDAY
            CalendarWeekStart.SUNDAY -> DayOfWeek.SUNDAY
        }
        val columns = if (config.span == CalendarSpan.TWO_WEEKS) 14 else 7
        val daysUntilWeekEnd = Math.floorMod(
            firstDay.value + 6 - today.dayOfWeek.value,
            7,
        )
        val totalDays = when (config.range) {
            CalendarRange.FOUR_WEEKS -> 28
            CalendarRange.SIX_WEEKS -> 42
            CalendarRange.TWELVE_WEEKS -> 84
        }
        val lastDate = today.plusDays(daysUntilWeekEnd.toLong())
        val firstDate = lastDate.minusDays((totalDays - 1).toLong())
        val days = (0L until totalDays.toLong()).map { offset ->
            val date = firstDate.plusDays(offset)
            val count = counts[date] ?: 0
            CalendarDayUi(
                key = date.toString(),
                dayNumber = date.dayOfMonth.toString(),
                count = count,
                isToday = date == today,
                visible = config.showEmptyDays || count > 0,
                intensity = count.toFloat() / max,
                contentDescription = "$date: $count ${if (count == 1) "entry" else "entries"}",
            )
        }
        val weekdayLabels = (0 until columns).map { offset ->
            DayOfWeek.of(((firstDay.value - 1 + offset) % 7) + 1)
                .getDisplayName(java.time.format.TextStyle.NARROW, java.util.Locale.getDefault())
        }
        return CalendarGridUi(
            days = days,
            weekdayLabels = weekdayLabels,
            columns = columns,
            showDayNumber = config.showDayNumber,
            showCount = config.showCount,
            showWeekdayHeader = config.showWeekdayHeader,
        )
    }

    private fun filterRange(
        entries: List<TrackerEntry>,
        range: DateRangeUi,
        today: LocalDate,
    ): List<TrackerEntry> {
        val days = when (range) {
            DateRangeUi.WEEK -> 7L
            DateRangeUi.MONTH -> 30L
            DateRangeUi.QUARTER -> 90L
            DateRangeUi.YEAR -> 365L
            DateRangeUi.ALL -> return entries
        }
        val first = today.minusDays(days - 1)
        return entries.filter { !it.recordedAt.localDate.isBefore(first) }
    }

    private fun TimeRangePreset.toUi() = when (this) {
        TimeRangePreset.SEVEN_DAYS -> DateRangeUi.WEEK
        TimeRangePreset.THIRTY_DAYS -> DateRangeUi.MONTH
        TimeRangePreset.THREE_MONTHS -> DateRangeUi.QUARTER
        TimeRangePreset.ONE_YEAR -> DateRangeUi.YEAR
        TimeRangePreset.ALL -> DateRangeUi.ALL
    }
}

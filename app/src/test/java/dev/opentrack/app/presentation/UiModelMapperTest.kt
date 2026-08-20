package dev.opentrack.app.presentation

import com.google.common.truth.Truth.assertThat
import dev.opentrack.app.domain.model.ChoiceOption
import dev.opentrack.app.domain.model.Aggregation
import dev.opentrack.app.domain.model.AnalyticsMetric
import dev.opentrack.app.domain.model.ChartStyle
import dev.opentrack.app.domain.model.CalendarRange
import dev.opentrack.app.domain.model.CalendarSpan
import dev.opentrack.app.domain.model.CalendarWeekStart
import dev.opentrack.app.domain.model.Dashboard
import dev.opentrack.app.domain.model.DashboardSeries
import dev.opentrack.app.domain.model.DashboardWidget
import dev.opentrack.app.domain.model.DashboardWidgetKind
import dev.opentrack.app.domain.model.FieldKind
import dev.opentrack.app.domain.model.FieldValue
import dev.opentrack.app.domain.model.EnumPayloadKind
import dev.opentrack.app.domain.model.RecordedAt
import dev.opentrack.app.domain.model.TrackerDefinition
import dev.opentrack.app.domain.model.TrackerEntry
import dev.opentrack.app.domain.model.TrackerField
import dev.opentrack.app.domain.model.TrackerKind
import dev.opentrack.app.domain.model.TimestampCalendarConfig
import dev.opentrack.app.ui.model.DateRangeUi
import dev.opentrack.app.ui.model.DetailTabUi
import dev.opentrack.app.ui.model.DetailChartUi
import dev.opentrack.app.ui.model.WidgetChartUi
import dev.opentrack.app.ui.model.ChartStyleUi
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Test

class UiModelMapperTest {
    private val now = Instant.parse("2026-08-19T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun `group detail exposes activity and field specific charts`() {
        val value = TrackerField(id = "value", label = "Weight", kind = FieldKind.VALUE)
        val moodOption = ChoiceOption(id = "high", label = "High", radioScore = 3.0)
        val mood = TrackerField(
            id = "mood",
            label = "Energy",
            kind = FieldKind.RADIO,
            options = listOf(
                ChoiceOption(id = "low", label = "Low", radioScore = 1.0),
                moodOption,
            ),
        )
        val flag = TrackerField(id = "flag", label = "Exercised", kind = FieldKind.BOOLEAN)
        val tracker = TrackerDefinition(
            id = "check-in",
            name = "Check-in",
            kind = TrackerKind.GROUP,
            fields = listOf(value, mood, flag),
        )
        val entry = TrackerEntry(
            trackerId = tracker.id,
            recordedAt = RecordedAt.Day(LocalDate.of(2026, 8, 19)),
            values = mapOf(
                value.id to FieldValue.Decimal(72.4),
                mood.id to FieldValue.Choice(moodOption.id),
                flag.id to FieldValue.BooleanValue(true),
            ),
        )

        val detail = UiModelMapper.detail(
            tracker,
            listOf(entry),
            DetailTabUi.OVERVIEW,
            DateRangeUi.ALL,
            clock,
        )

        assertThat(detail.charts.map { it.title })
            .containsExactly("Activity", "Weight", "Energy", "Energy trend", "Exercised")
            .inOrder()
    }

    @Test
    fun `an existing empty dashboard stays empty`() {
        val tracker = TrackerDefinition(name = "Coffee", kind = TrackerKind.TIMESTAMP)

        val firstRun = UiModelMapper.dashboard(listOf(tracker), emptyList(), emptyList(), clock)
        val customizedEmpty = UiModelMapper.dashboard(
            listOf(tracker),
            emptyList(),
            listOf(Dashboard(id = "dashboard", widgets = emptyList())),
            clock,
        )

        assertThat(firstRun.widgets).hasSize(1)
        assertThat(customizedEmpty.widgets).isEmpty()
    }

    @Test
    fun `latest summary follows recorded time rather than creation time`() {
        val field = TrackerField(id = "value", label = "Value", kind = FieldKind.VALUE)
        val tracker = TrackerDefinition(
            id = "weight",
            name = "Weight",
            kind = TrackerKind.VALUE,
            fields = listOf(field),
        )
        val laterRecorded = TrackerEntry(
            id = "later-recorded",
            trackerId = tracker.id,
            recordedAt = RecordedAt.Day(LocalDate.of(2026, 8, 19)),
            values = mapOf(field.id to FieldValue.Decimal(72.0)),
            createdAt = Instant.parse("2026-08-18T00:00:00Z"),
        )
        val laterCreated = TrackerEntry(
            id = "later-created",
            trackerId = tracker.id,
            recordedAt = RecordedAt.Day(LocalDate.of(2026, 8, 18)),
            values = mapOf(field.id to FieldValue.Decimal(99.0)),
            createdAt = Instant.parse("2026-08-19T23:00:00Z"),
        )

        val summary = UiModelMapper.trackerSummaries(
            listOf(tracker),
            listOf(laterRecorded, laterCreated),
            includeArchived = false,
            clock = clock,
        ).single()

        assertThat(summary.lastValue).isEqualTo("72")
    }

    @Test
    fun `dashboard honors configured true rate metric`() {
        val field = TrackerField(id = "state", label = "State", kind = FieldKind.BOOLEAN)
        val tracker = TrackerDefinition(
            id = "energy",
            name = "Energy",
            kind = TrackerKind.BOOLEAN,
            fields = listOf(field),
        )
        val entries = listOf(true, false).mapIndexed { index, value ->
            TrackerEntry(
                id = "entry-$index",
                trackerId = tracker.id,
                recordedAt = RecordedAt.Day(LocalDate.of(2026, 8, 18 + index)),
                values = mapOf(field.id to FieldValue.BooleanValue(value)),
            )
        }
        val widget = DashboardWidget(
            id = "widget",
            kind = DashboardWidgetKind.SUMMARY,
            chartStyle = ChartStyle.DISTRIBUTION,
            series = listOf(
                DashboardSeries(
                    trackerId = tracker.id,
                    fieldId = field.id,
                    metric = AnalyticsMetric.TRUE_RATE,
                    aggregation = Aggregation.AVERAGE,
                ),
            ),
        )

        val dashboard = UiModelMapper.dashboard(
            listOf(tracker),
            entries,
            listOf(Dashboard(id = "dashboard", widgets = listOf(widget))),
            clock,
        )

        assertThat(dashboard.widgets.single().metric).isEqualTo("50%")
    }

    @Test
    fun `timestamp calendar exposes counts labels alignment and configured span`() {
        val tracker = TrackerDefinition(
            id = "moments",
            name = "Moments",
            kind = TrackerKind.TIMESTAMP,
            timestampCalendar = TimestampCalendarConfig(
                showDayNumber = true,
                showCount = true,
                showWeekdayHeader = true,
                weekStart = CalendarWeekStart.SUNDAY,
                span = CalendarSpan.TWO_WEEKS,
                range = CalendarRange.FOUR_WEEKS,
            ),
        )
        val entries = List(2) { index ->
            TrackerEntry(
                id = "entry-$index",
                trackerId = tracker.id,
                recordedAt = RecordedAt.Day(LocalDate.of(2026, 8, 19)),
            )
        }

        val detail = UiModelMapper.detail(
            tracker, entries, DetailTabUi.OVERVIEW, DateRangeUi.ALL, clock,
            weekStartsMonday = true,
        )
        val calendar = detail.charts.single() as DetailChartUi.Calendar
        val today = calendar.grid.days.single { it.isToday }

        assertThat(calendar.grid.columns).isEqualTo(14)
        assertThat(calendar.grid.days).hasSize(28)
        assertThat(calendar.grid.weekdayLabels.first()).isEqualTo("S")
        assertThat(LocalDate.parse(calendar.grid.days.first().key).dayOfWeek.name).isEqualTo("SUNDAY")
        assertThat(today.dayNumber).isEqualTo("19")
        assertThat(today.count).isEqualTo(2)
        assertThat(today.contentDescription).contains("2 entries")
    }

    @Test
    fun `group exercise payloads expose kg progress across time`() {
        val bench = ChoiceOption(
            id = "bench",
            label = "Bench press",
            payloadKind = EnumPayloadKind.DECIMAL,
            payloadLabel = "Weight",
            payloadUnit = "kg",
        )
        val exercise = TrackerField(
            id = "exercise",
            label = "Exercise",
            kind = FieldKind.ENUM,
            options = listOf(bench),
        )
        val tracker = TrackerDefinition(
            id = "gym",
            name = "Gym progress",
            kind = TrackerKind.GROUP,
            fields = listOf(exercise),
        )
        val entries = listOf(60.0, 67.5).mapIndexed { index, weight ->
            TrackerEntry(
                id = "set-$index",
                trackerId = tracker.id,
                recordedAt = RecordedAt.Day(LocalDate.of(2026, 8, 18 + index)),
                values = mapOf(exercise.id to FieldValue.Choice(bench.id, FieldValue.Decimal(weight))),
            )
        }

        val detail = UiModelMapper.detail(
            tracker, entries, DetailTabUi.OVERVIEW, DateRangeUi.ALL, clock,
        )
        val progress = detail.charts.filterIsInstance<DetailChartUi.Line>()
            .single { it.title == "Bench press · Weight" }

        assertThat(progress.summary).isEqualTo("2 values in kg")
        assertThat(progress.points.map { it.y }).containsExactly(60f, 67.5f).inOrder()
    }

    @Test
    fun `dashboard maps area scatter and donut to distinct graph models`() {
        val valueField = TrackerField(id = "value", label = "Weight", kind = FieldKind.VALUE)
        val valueTracker = TrackerDefinition(
            id = "weight",
            name = "Weight",
            kind = TrackerKind.VALUE,
            fields = listOf(valueField),
        )
        val valueEntries = listOf(70.0, 71.0).mapIndexed { index, value ->
            TrackerEntry(
                trackerId = valueTracker.id,
                recordedAt = RecordedAt.Day(LocalDate.of(2026, 8, 18 + index)),
                values = mapOf(valueField.id to FieldValue.Decimal(value)),
            )
        }
        fun widget(style: ChartStyle) = DashboardWidget(
            id = style.name,
            kind = DashboardWidgetKind.CHART,
            chartStyle = style,
            series = listOf(
                DashboardSeries(
                    trackerId = valueTracker.id,
                    fieldId = valueField.id,
                    metric = AnalyticsMetric.NUMERIC_VALUE,
                    aggregation = Aggregation.LAST,
                ),
            ),
        )

        val area = UiModelMapper.dashboard(
            listOf(valueTracker), valueEntries,
            listOf(Dashboard(widgets = listOf(widget(ChartStyle.AREA)))), clock,
        ).widgets.single().chart
        val scatter = UiModelMapper.dashboard(
            listOf(valueTracker), valueEntries,
            listOf(Dashboard(widgets = listOf(widget(ChartStyle.SCATTER)))), clock,
        ).widgets.single().chart

        assertThat(area).isInstanceOf(WidgetChartUi.Area::class.java)
        assertThat(scatter).isInstanceOf(WidgetChartUi.Scatter::class.java)

        val booleanField = TrackerField(id = "done", label = "Done", kind = FieldKind.BOOLEAN)
        val booleanTracker = TrackerDefinition(
            id = "habit",
            name = "Habit",
            kind = TrackerKind.BOOLEAN,
            fields = listOf(booleanField),
        )
        val booleanEntries = listOf(true, false).mapIndexed { index, value ->
            TrackerEntry(
                trackerId = booleanTracker.id,
                recordedAt = RecordedAt.Day(LocalDate.of(2026, 8, 18 + index)),
                values = mapOf(booleanField.id to FieldValue.BooleanValue(value)),
            )
        }
        val donutWidget = DashboardWidget(
            kind = DashboardWidgetKind.CHART,
            chartStyle = ChartStyle.DONUT,
            series = listOf(
                DashboardSeries(
                    trackerId = booleanTracker.id,
                    fieldId = booleanField.id,
                    metric = AnalyticsMetric.TRUE_RATE,
                    aggregation = Aggregation.AVERAGE,
                ),
            ),
        )
        val donut = UiModelMapper.dashboard(
            listOf(booleanTracker), booleanEntries,
            listOf(Dashboard(widgets = listOf(donutWidget))), clock,
        ).widgets.single().chart
        val editor = UiModelMapper.dashboardEditor(
            listOf(booleanTracker), booleanEntries,
            listOf(Dashboard(widgets = listOf(donutWidget))), clock,
        )

        assertThat(donut).isInstanceOf(WidgetChartUi.Donut::class.java)
        assertThat(editor.items.single().availableChartStyles).contains(ChartStyleUi.DONUT)
        assertThat(editor.items.single().availableChartStyles).doesNotContain(ChartStyleUi.SCATTER)
    }
}

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
import dev.opentrack.app.domain.model.RecordedAt
import dev.opentrack.app.domain.model.TrackerDefinition
import dev.opentrack.app.domain.model.TrackerEntry
import dev.opentrack.app.domain.model.TrackerField
import dev.opentrack.app.domain.model.TrackerKind
import dev.opentrack.app.domain.model.TimestampCalendarConfig
import dev.opentrack.app.ui.model.DateRangeUi
import dev.opentrack.app.ui.model.DetailTabUi
import dev.opentrack.app.ui.model.DetailChartUi
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
            .containsExactly("Activity", "Weight", "Energy", "Exercised")
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
}

package dev.opentrack.app.domain.model

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

enum class TrackerKind { TIMESTAMP, VALUE, ENUM, RADIO, BOOLEAN, COUNTER, DURATION, GROUP }
enum class FieldKind { TIMESTAMP, VALUE, ENUM, RADIO, BOOLEAN, COUNTER, DURATION }
enum class TimestampPrecision { DAY, DATE_TIME }
enum class EnumPayloadKind { NONE, DECIMAL, INTEGER, DURATION, TEXT }
enum class QuickAddMode { AUTO, OPEN_EDITOR, DEFAULT_PRESET }
enum class TimestampPresetMode { LITERAL, NOW, TODAY }
enum class DashboardWidgetKind { QUICK_ADD, LAST_RECORDED, LATEST_VALUE, SUMMARY, CHART }
enum class ChartStyle { AUTO, LINE, BAR, AREA, DISTRIBUTION, CALENDAR }
enum class TimeBucket { EVENT, DAY, WEEK, MONTH }
enum class TimeRangePreset { SEVEN_DAYS, THIRTY_DAYS, THREE_MONTHS, ONE_YEAR, ALL }
enum class AnalyticsMetric {
    OCCURRENCE_COUNT,
    LAST_RECORDED,
    LATEST_VALUE,
    NUMERIC_VALUE,
    ENUM_COUNT,
    ENUM_PAYLOAD,
    RADIO_SCORE,
    TRUE_COUNT,
    TRUE_RATE,
    COUNTER_SUM,
    COUNTER_RUNNING_TOTAL,
    DURATION_TOTAL,
    DURATION_AVERAGE,
}
enum class Aggregation { NONE, LAST, COUNT, SUM, AVERAGE, MINIMUM, MAXIMUM }
enum class WidgetSpan { COMPACT, WIDE }

fun newId(): String = UUID.randomUUID().toString()

sealed interface RecordedAt {
    val localDate: LocalDate
    val precision: TimestampPrecision

    data class Day(override val localDate: LocalDate) : RecordedAt {
        override val precision: TimestampPrecision = TimestampPrecision.DAY
    }

    data class DateTime(
        val instant: Instant,
        val zoneId: ZoneId,
        val recordedOffset: ZoneOffset = instant.atZone(zoneId).offset,
    ) : RecordedAt {
        override val precision: TimestampPrecision = TimestampPrecision.DATE_TIME
        override val localDate: LocalDate = instant.atOffset(recordedOffset).toLocalDate()
    }

    companion object {
        fun now(
            precision: TimestampPrecision,
            clock: Clock = Clock.systemDefaultZone(),
            zoneId: ZoneId = clock.zone,
        ): RecordedAt = when (precision) {
            TimestampPrecision.DAY -> Day(LocalDate.now(clock.withZone(zoneId)))
            TimestampPrecision.DATE_TIME -> DateTime(clock.instant(), zoneId)
        }
    }
}

sealed interface FieldValue {
    data class Decimal(val value: Double) : FieldValue
    data class Integer(val value: Long) : FieldValue
    data class BooleanValue(val value: Boolean) : FieldValue
    data class DurationValue(val value: Duration) : FieldValue
    data class Text(val value: String) : FieldValue
    data class Choice(val optionId: String, val payload: FieldValue? = null) : FieldValue
    data class Timestamp(val value: RecordedAt) : FieldValue
}

data class ChoiceOption(
    val id: String = newId(),
    val label: String,
    val colorArgb: Long? = null,
    val order: Int = 0,
    val radioScore: Double? = null,
    val payloadKind: EnumPayloadKind = EnumPayloadKind.NONE,
    val payloadLabel: String? = null,
    val payloadUnit: String? = null,
    val archivedAt: Instant? = null,
)

data class TrackerField(
    val id: String = newId(),
    val label: String,
    val kind: FieldKind,
    val required: Boolean = true,
    val order: Int = 0,
    val unit: String? = null,
    val decimalPlaces: Int = 2,
    val counterQuickDelta: Long = 1,
    val timestampPrecision: TimestampPrecision? = null,
    val options: List<ChoiceOption> = emptyList(),
    val archivedAt: Instant? = null,
)

data class QuickPreset(
    val id: String = newId(),
    val label: String,
    val order: Int = 0,
    val values: Map<String, FieldValue> = emptyMap(),
    val timestampModes: Map<String, TimestampPresetMode> = emptyMap(),
    val note: String? = null,
)

data class QuickAddConfig(
    val mode: QuickAddMode = QuickAddMode.AUTO,
    val defaultPresetId: String? = null,
)

data class TrackerDefinition(
    val id: String = newId(),
    val name: String,
    val description: String? = null,
    val kind: TrackerKind,
    val timestampPrecision: TimestampPrecision = TimestampPrecision.DATE_TIME,
    val iconKey: String? = null,
    val colorArgb: Long? = null,
    val order: Int = 0,
    val fields: List<TrackerField> = emptyList(),
    val presets: List<QuickPreset> = emptyList(),
    val quickAdd: QuickAddConfig = QuickAddConfig(),
    val archivedAt: Instant? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = createdAt,
)

data class TrackerEntry(
    val id: String = newId(),
    val trackerId: String,
    val recordedAt: RecordedAt,
    val values: Map<String, FieldValue> = emptyMap(),
    val note: String? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = createdAt,
)

data class DashboardSeries(
    val id: String = newId(),
    val trackerId: String,
    val fieldId: String? = null,
    val optionId: String? = null,
    val metric: AnalyticsMetric,
    val aggregation: Aggregation = Aggregation.NONE,
    val colorArgb: Long? = null,
    val order: Int = 0,
    val presetId: String? = null,
)

data class DashboardWidget(
    val id: String = newId(),
    val kind: DashboardWidgetKind,
    val title: String? = null,
    val chartStyle: ChartStyle = ChartStyle.AUTO,
    val range: TimeRangePreset = TimeRangePreset.THIRTY_DAYS,
    val bucket: TimeBucket = TimeBucket.DAY,
    val order: Int = 0,
    val span: WidgetSpan = WidgetSpan.COMPACT,
    val visible: Boolean = true,
    val series: List<DashboardSeries>,
)

data class Dashboard(
    val id: String = newId(),
    val name: String = "Dashboard",
    val order: Int = 0,
    val widgets: List<DashboardWidget> = emptyList(),
)

data class BackupSnapshot(
    val trackers: List<TrackerDefinition>,
    val entries: List<TrackerEntry>,
    val dashboards: List<Dashboard>,
    val createdAt: Instant = Instant.now(),
)

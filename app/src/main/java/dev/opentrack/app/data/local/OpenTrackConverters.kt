package dev.opentrack.app.data.local

import androidx.room.TypeConverter
import dev.opentrack.app.domain.model.Aggregation
import dev.opentrack.app.domain.model.CalendarSpan
import dev.opentrack.app.domain.model.CalendarRange
import dev.opentrack.app.domain.model.CalendarWeekStart
import dev.opentrack.app.domain.model.AnalyticsMetric
import dev.opentrack.app.domain.model.ChartStyle
import dev.opentrack.app.domain.model.DashboardWidgetKind
import dev.opentrack.app.domain.model.EnumPayloadKind
import dev.opentrack.app.domain.model.FieldKind
import dev.opentrack.app.domain.model.QuickAddMode
import dev.opentrack.app.domain.model.TimeBucket
import dev.opentrack.app.domain.model.TimeRangePreset
import dev.opentrack.app.domain.model.TimestampPrecision
import dev.opentrack.app.domain.model.TimestampPresetMode
import dev.opentrack.app.domain.model.TrackerKind
import dev.opentrack.app.domain.model.WidgetSpan

class OpenTrackConverters {
    @TypeConverter fun calendarWeekStart(value: CalendarWeekStart?): String? = value?.name
    @TypeConverter fun calendarWeekStart(value: String?): CalendarWeekStart? = value?.let(CalendarWeekStart::valueOf)
    @TypeConverter fun calendarSpan(value: CalendarSpan?): String? = value?.name
    @TypeConverter fun calendarSpan(value: String?): CalendarSpan? = value?.let(CalendarSpan::valueOf)
    @TypeConverter fun calendarRange(value: CalendarRange?): String? = value?.name
    @TypeConverter fun calendarRange(value: String?): CalendarRange? = value?.let(CalendarRange::valueOf)
    @TypeConverter fun trackerKind(value: TrackerKind?): String? = value?.name
    @TypeConverter fun trackerKind(value: String?): TrackerKind? = value?.let(TrackerKind::valueOf)
    @TypeConverter fun fieldKind(value: FieldKind?): String? = value?.name
    @TypeConverter fun fieldKind(value: String?): FieldKind? = value?.let(FieldKind::valueOf)
    @TypeConverter fun precision(value: TimestampPrecision?): String? = value?.name
    @TypeConverter fun precision(value: String?): TimestampPrecision? = value?.let(TimestampPrecision::valueOf)
    @TypeConverter fun payloadKind(value: EnumPayloadKind?): String? = value?.name
    @TypeConverter fun payloadKind(value: String?): EnumPayloadKind? = value?.let(EnumPayloadKind::valueOf)
    @TypeConverter fun quickAddMode(value: QuickAddMode?): String? = value?.name
    @TypeConverter fun quickAddMode(value: String?): QuickAddMode? = value?.let(QuickAddMode::valueOf)
    @TypeConverter fun timestampPresetMode(value: TimestampPresetMode?): String? = value?.name
    @TypeConverter fun timestampPresetMode(value: String?): TimestampPresetMode? = value?.let(TimestampPresetMode::valueOf)
    @TypeConverter fun widgetKind(value: DashboardWidgetKind?): String? = value?.name
    @TypeConverter fun widgetKind(value: String?): DashboardWidgetKind? = value?.let(DashboardWidgetKind::valueOf)
    @TypeConverter fun chartStyle(value: ChartStyle?): String? = value?.name
    @TypeConverter fun chartStyle(value: String?): ChartStyle? = value?.let(ChartStyle::valueOf)
    @TypeConverter fun range(value: TimeRangePreset?): String? = value?.name
    @TypeConverter fun range(value: String?): TimeRangePreset? = value?.let(TimeRangePreset::valueOf)
    @TypeConverter fun bucket(value: TimeBucket?): String? = value?.name
    @TypeConverter fun bucket(value: String?): TimeBucket? = value?.let(TimeBucket::valueOf)
    @TypeConverter fun span(value: WidgetSpan?): String? = value?.name
    @TypeConverter fun span(value: String?): WidgetSpan? = value?.let(WidgetSpan::valueOf)
    @TypeConverter fun metric(value: AnalyticsMetric?): String? = value?.name
    @TypeConverter fun metric(value: String?): AnalyticsMetric? = value?.let(AnalyticsMetric::valueOf)
    @TypeConverter fun aggregation(value: Aggregation?): String? = value?.name
    @TypeConverter fun aggregation(value: String?): Aggregation? = value?.let(Aggregation::valueOf)
}

package dev.opentrack.app.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
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

@Entity(
    tableName = "trackers",
    indices = [Index("archivedAtMillis"), Index("position")],
)
data class TrackerEntity(
    @androidx.room.PrimaryKey val id: String,
    val name: String,
    val description: String?,
    val kind: TrackerKind,
    val timestampPrecision: TimestampPrecision,
    val iconKey: String?,
    val colorArgb: Long?,
    @androidx.room.ColumnInfo(defaultValue = "1") val calendarShowDayNumber: Boolean,
    @androidx.room.ColumnInfo(defaultValue = "1") val calendarShowCount: Boolean,
    @androidx.room.ColumnInfo(defaultValue = "1") val calendarShowWeekdayHeader: Boolean,
    @androidx.room.ColumnInfo(defaultValue = "'APP_DEFAULT'") val calendarWeekStart: CalendarWeekStart,
    @androidx.room.ColumnInfo(defaultValue = "'TWO_WEEKS'") val calendarSpan: CalendarSpan,
    @androidx.room.ColumnInfo(defaultValue = "'SIX_WEEKS'") val calendarRange: CalendarRange,
    @androidx.room.ColumnInfo(defaultValue = "1") val calendarShowEmptyDays: Boolean,
    val position: Int,
    val archivedAtMillis: Long?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

@Entity(
    tableName = "tracker_fields",
    foreignKeys = [ForeignKey(
        entity = TrackerEntity::class,
        parentColumns = ["id"],
        childColumns = ["trackerId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("trackerId"), Index(value = ["trackerId", "position"])],
)
data class TrackerFieldEntity(
    @androidx.room.PrimaryKey val id: String,
    val trackerId: String,
    val label: String,
    val kind: FieldKind,
    val required: Boolean,
    val position: Int,
    val unit: String?,
    val decimalPlaces: Int,
    val counterQuickDelta: Long,
    val timestampPrecision: TimestampPrecision?,
    val archivedAtMillis: Long?,
)

@Entity(
    tableName = "choice_options",
    foreignKeys = [ForeignKey(
        entity = TrackerFieldEntity::class,
        parentColumns = ["id"],
        childColumns = ["fieldId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("fieldId"), Index(value = ["fieldId", "position"])],
)
data class ChoiceOptionEntity(
    @androidx.room.PrimaryKey val id: String,
    val fieldId: String,
    val label: String,
    val colorArgb: Long?,
    val position: Int,
    val radioScore: Double?,
    val payloadKind: EnumPayloadKind,
    val payloadLabel: String?,
    val payloadUnit: String?,
    val archivedAtMillis: Long?,
)

@Entity(
    tableName = "quick_presets",
    foreignKeys = [ForeignKey(
        entity = TrackerEntity::class,
        parentColumns = ["id"],
        childColumns = ["trackerId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("trackerId"), Index(value = ["trackerId", "position"])],
)
data class QuickPresetEntity(
    @androidx.room.PrimaryKey val id: String,
    val trackerId: String,
    val label: String,
    val position: Int,
    val note: String?,
)

@Entity(
    tableName = "quick_add_configs",
    foreignKeys = [
        ForeignKey(
            entity = TrackerEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackerId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = QuickPresetEntity::class,
            parentColumns = ["id"],
            childColumns = ["defaultPresetId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("defaultPresetId")],
)
data class QuickAddConfigEntity(
    @androidx.room.PrimaryKey val trackerId: String,
    val mode: QuickAddMode,
    val defaultPresetId: String?,
)

data class StoredValueColumns(
    val optionId: String? = null,
    val decimalValue: Double? = null,
    val integerValue: Long? = null,
    val booleanValue: Boolean? = null,
    val durationMillis: Long? = null,
    val textValue: String? = null,
    val timestampPrecision: TimestampPrecision? = null,
    val localEpochDay: Long? = null,
    val instantMillis: Long? = null,
    val zoneId: String? = null,
    val offsetSeconds: Int? = null,
    val localSecondOfDay: Int? = null,
)

@Entity(
    tableName = "quick_preset_values",
    primaryKeys = ["presetId", "fieldId"],
    foreignKeys = [
        ForeignKey(
            entity = QuickPresetEntity::class,
            parentColumns = ["id"],
            childColumns = ["presetId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TrackerFieldEntity::class,
            parentColumns = ["id"],
            childColumns = ["fieldId"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [Index("fieldId")],
)
data class QuickPresetValueEntity(
    val presetId: String,
    val fieldId: String,
    val timestampMode: TimestampPresetMode?,
    @Embedded(prefix = "stored_") val stored: StoredValueColumns,
)

@Entity(
    tableName = "entries",
    foreignKeys = [ForeignKey(
        entity = TrackerEntity::class,
        parentColumns = ["id"],
        childColumns = ["trackerId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [
        Index("trackerId"),
        Index(value = ["trackerId", "localEpochDay"]),
        Index(value = ["trackerId", "instantMillis"]),
        Index("updatedAtMillis"),
    ],
)
data class EntryEntity(
    @androidx.room.PrimaryKey val id: String,
    val trackerId: String,
    val precision: TimestampPrecision,
    val localEpochDay: Long,
    val instantMillis: Long?,
    val zoneId: String?,
    val offsetSeconds: Int?,
    val localSecondOfDay: Int?,
    val note: String?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

@Entity(
    tableName = "entry_values",
    primaryKeys = ["entryId", "fieldId"],
    foreignKeys = [
        ForeignKey(
            entity = EntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TrackerFieldEntity::class,
            parentColumns = ["id"],
            childColumns = ["fieldId"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [Index("fieldId"), Index("stored_optionId")],
)
data class EntryValueEntity(
    val entryId: String,
    val fieldId: String,
    @Embedded(prefix = "stored_") val stored: StoredValueColumns,
)

@Entity(tableName = "dashboards", indices = [Index("position")])
data class DashboardEntity(
    @androidx.room.PrimaryKey val id: String,
    val name: String,
    val position: Int,
)

@Entity(
    tableName = "dashboard_widgets",
    foreignKeys = [ForeignKey(
        entity = DashboardEntity::class,
        parentColumns = ["id"],
        childColumns = ["dashboardId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("dashboardId"), Index(value = ["dashboardId", "position"])],
)
data class DashboardWidgetEntity(
    @androidx.room.PrimaryKey val id: String,
    val dashboardId: String,
    val kind: DashboardWidgetKind,
    val title: String?,
    val chartStyle: ChartStyle,
    val rangePreset: TimeRangePreset,
    val bucket: TimeBucket,
    val position: Int,
    val span: WidgetSpan,
    val visible: Boolean,
)

@Entity(
    tableName = "dashboard_series",
    foreignKeys = [
        ForeignKey(
            entity = DashboardWidgetEntity::class,
            parentColumns = ["id"],
            childColumns = ["widgetId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TrackerEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("widgetId"), Index("trackerId"), Index("fieldId"), Index("optionId")],
)
data class DashboardSeriesEntity(
    @androidx.room.PrimaryKey val id: String,
    val widgetId: String,
    val trackerId: String,
    val fieldId: String?,
    val optionId: String?,
    val metric: AnalyticsMetric,
    val aggregation: Aggregation,
    val colorArgb: Long?,
    val position: Int,
    val presetId: String?,
)

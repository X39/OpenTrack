package dev.opentrack.app.ui.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
enum class TrackerKindUi(val label: String, val description: String) {
    MOMENT("Moment", "Record that something happened"),
    NUMBER("Number", "Track a measured value"),
    CHOICE("Choice", "Choose an option with an optional value"),
    RATING("Rating", "Pick one value on an ordered scale"),
    GROUP("Group", "Record several related fields together"),
    BOOLEAN("Yes / No", "Track a simple state"),
    COUNTER("Counter", "Add or subtract a running amount"),
    DURATION("Duration", "Track elapsed time"),
}

@Immutable
enum class TrackerGlyphUi {
    PULSE, SCALE, FITNESS, MOOD, MEDICATION, SLEEP, WATER, TIMER, CHECK, COUNTER,
}

@Immutable
enum class TimestampPrecisionUi(val label: String) {
    DAY("Day only"), DATE_AND_TIME("Date and time"),
}

@Immutable
enum class WidgetSizeUi(val label: String) {
    COMPACT("Compact"), WIDE("Wide"),
}

@Immutable
enum class WidgetMetricUi(val label: String) {
    LATEST("Latest value"), LAST_TRACKED("Last tracked"), TREND("Trend"),
    COUNT("Count"), STREAK("Streak"), DISTRIBUTION("Distribution"),
}

@Immutable
data class ChartPointUi(
    val x: Float,
    val y: Float,
    val label: String,
    val valueLabel: String = y.toString(),
)

@Immutable
data class ChartBarUi(
    val label: String,
    val value: Float,
    val valueLabel: String = value.toString(),
    val color: Color? = null,
)

@Immutable
data class DistributionPartUi(
    val label: String,
    val value: Float,
    val color: Color,
)

@Immutable
data class CalendarDayUi(
    val key: String,
    val intensity: Float,
    val contentDescription: String,
)

@Immutable
sealed interface WidgetChartUi {
    @Immutable
    data object None : WidgetChartUi

    @Immutable
    data class Sparkline(val points: List<ChartPointUi>, val summary: String) : WidgetChartUi

    @Immutable
    data class Bars(val bars: List<ChartBarUi>, val summary: String) : WidgetChartUi

    @Immutable
    data class Distribution(val parts: List<DistributionPartUi>, val summary: String) : WidgetChartUi

    @Immutable
    data class Calendar(val days: List<CalendarDayUi>, val summary: String) : WidgetChartUi
}

@Immutable
data class DashboardWidgetUi(
    val id: String,
    val trackerId: String,
    val title: String,
    val glyph: TrackerGlyphUi,
    val accent: Color,
    val metric: String,
    val context: String,
    val chart: WidgetChartUi = WidgetChartUi.None,
    val size: WidgetSizeUi = WidgetSizeUi.WIDE,
    val quickActionLabel: String = "Log $title",
)

@Immutable
data class DashboardUiState(
    val greeting: String = "Your signals",
    val dateLabel: String = "",
    val widgets: List<DashboardWidgetUi> = emptyList(),
    val isLoading: Boolean = false,
)

@Immutable
data class TrackerSummaryUi(
    val id: String,
    val name: String,
    val kind: TrackerKindUi,
    val glyph: TrackerGlyphUi,
    val accent: Color,
    val lastValue: String,
    val lastTracked: String,
    val pinned: Boolean = true,
    val archived: Boolean = false,
)

@Immutable
data class EntryUi(
    val id: String,
    val trackerId: String,
    val trackerName: String,
    val glyph: TrackerGlyphUi,
    val accent: Color,
    val primaryValue: String,
    val supportingValue: String? = null,
    val timeLabel: String,
    val dayLabel: String,
)

@Immutable
data class HistoryDayUi(val label: String, val entries: List<EntryUi>)

@Immutable
data class HistoryUiState(
    val days: List<HistoryDayUi> = emptyList(),
    val trackerFilterLabel: String = "All trackers",
    val dateFilterLabel: String = "Any time",
    val query: String = "",
    val isLoading: Boolean = false,
)

@Immutable
data class OnboardingTemplateUi(
    val id: String,
    val title: String,
    val description: String,
    val glyph: TrackerGlyphUi,
    val accent: Color,
    val selected: Boolean = false,
)

@Immutable
data class OnboardingUiState(
    val page: Int = 0,
    val templates: List<OnboardingTemplateUi> = emptyList(),
    val usesMetricUnits: Boolean = true,
)

@Immutable
data class BuilderTemplateUi(
    val id: String,
    val title: String,
    val description: String,
    val glyph: TrackerGlyphUi,
    val accent: Color,
)

@Immutable
enum class DetailTabUi(val label: String) { OVERVIEW("Overview"), ENTRIES("Entries") }

@Immutable
enum class DateRangeUi(val label: String) {
    WEEK("7D"), MONTH("30D"), QUARTER("3M"), YEAR("1Y"), ALL("All"),
}

@Immutable
sealed interface DetailChartUi {
    val title: String
    val summary: String

    @Immutable
    data class Line(
        override val title: String,
        override val summary: String,
        val points: List<ChartPointUi>,
        val startLabel: String,
        val endLabel: String,
    ) : DetailChartUi

    @Immutable
    data class Bars(
        override val title: String,
        override val summary: String,
        val bars: List<ChartBarUi>,
    ) : DetailChartUi

    @Immutable
    data class Distribution(
        override val title: String,
        override val summary: String,
        val parts: List<DistributionPartUi>,
    ) : DetailChartUi

    @Immutable
    data class Calendar(
        override val title: String,
        override val summary: String,
        val days: List<CalendarDayUi>,
    ) : DetailChartUi
}

@Immutable
data class InsightUi(val label: String, val value: String)

@Immutable
data class TrackerDetailUiState(
    val tracker: TrackerSummaryUi,
    val tab: DetailTabUi = DetailTabUi.OVERVIEW,
    val range: DateRangeUi = DateRangeUi.MONTH,
    val headline: String,
    val headlineContext: String,
    val insights: List<InsightUi> = emptyList(),
    val charts: List<DetailChartUi> = emptyList(),
    val entries: List<EntryUi> = emptyList(),
)

@Immutable
data class DashboardEditorItemUi(
    val widget: DashboardWidgetUi,
    val visible: Boolean = true,
)

@Immutable
data class DashboardEditorUiState(
    val items: List<DashboardEditorItemUi> = emptyList(),
    val hasTrackers: Boolean = false,
)

@Immutable
data class BackupUiState(
    val automaticBackupEnabled: Boolean = true,
    val exportInProgress: Boolean = false,
    val importInProgress: Boolean = false,
    val lastExportLabel: String? = null,
)

@Immutable
data class SettingsUiState(
    val themeLabel: String = "System default",
    val weekStartsOnLabel: String = "Monday",
    val defaultPrecision: TimestampPrecisionUi = TimestampPrecisionUi.DATE_AND_TIME,
    val backup: BackupUiState = BackupUiState(),
)

@Immutable
data class BuilderOptionUi(
    val id: String,
    val label: String,
    val payloadKind: BuilderPayloadKindUi = BuilderPayloadKindUi.NONE,
    val payloadLabel: String = "",
    val payloadUnit: String = "",
    val payloadKindLocked: Boolean = false,
)

@Immutable
enum class BuilderPayloadKindUi(val label: String) {
    NONE("No payload"), NUMBER("Number"), INTEGER("Whole number"),
    DURATION("Duration"), TEXT("Short text"),
}

@Immutable
enum class BuilderFieldKindUi(val label: String) {
    MOMENT("Timestamp / Moment"), NUMBER("Value / Number"), CHOICE("Enum / Choice"),
    RATING("Radio / Rating"), BOOLEAN("Boolean / Yes-No"), COUNTER("Counter"),
    DURATION("Duration"),
}

@Immutable
data class BuilderFieldUi(
    val id: String,
    val label: String,
    val kind: BuilderFieldKindUi,
    val required: Boolean = false,
    val unit: String = "",
    val options: List<BuilderOptionUi> = emptyList(),
    val structureLocked: Boolean = false,
    val requiredLocked: Boolean = false,
)

@Immutable
enum class QuickLogModeUi(val label: String) {
    SMART("Smart prompt"), PRESET("One-tap preset"),
}

@Immutable
data class TrackerBuilderUiState(
    val step: Int = 0,
    val editingTrackerId: String? = null,
    val selectedTemplateId: String? = null,
    val templates: List<BuilderTemplateUi> = emptyList(),
    val name: String = "",
    val kind: TrackerKindUi? = null,
    val glyph: TrackerGlyphUi = TrackerGlyphUi.PULSE,
    val accent: Color,
    val precision: TimestampPrecisionUi = TimestampPrecisionUi.DATE_AND_TIME,
    val unit: String = "",
    val options: List<BuilderOptionUi> = emptyList(),
    val fields: List<BuilderFieldUi> = emptyList(),
    val quickLogMode: QuickLogModeUi = QuickLogModeUi.SMART,
    val quickPreset: String = "",
    val counterDelta: Int = 1,
    val addToDashboard: Boolean = true,
    val editingOptionId: String? = null,
    val editingFieldId: String? = null,
    val canContinue: Boolean = false,
    val errorMessage: String? = null,
)

sealed interface TrackerBuilderAction {
    data object Back : TrackerBuilderAction
    data object Next : TrackerBuilderAction
    data object Save : TrackerBuilderAction
    data class TemplateSelected(val id: String) : TrackerBuilderAction
    data class NameChanged(val value: String) : TrackerBuilderAction
    data class KindSelected(val kind: TrackerKindUi) : TrackerBuilderAction
    data class PrecisionSelected(val precision: TimestampPrecisionUi) : TrackerBuilderAction
    data class UnitChanged(val value: String) : TrackerBuilderAction
    data class QuickModeSelected(val mode: QuickLogModeUi) : TrackerBuilderAction
    data class QuickPresetChanged(val value: String) : TrackerBuilderAction
    data class CounterDeltaChanged(val value: Int) : TrackerBuilderAction
    data class AddToDashboardChanged(val value: Boolean) : TrackerBuilderAction
    data object AddOption : TrackerBuilderAction
    data class EditOption(val id: String) : TrackerBuilderAction
    data class RemoveOption(val id: String) : TrackerBuilderAction
    data class OptionLabelChanged(val id: String, val value: String) : TrackerBuilderAction
    data class OptionPayloadKindChanged(val id: String, val value: BuilderPayloadKindUi) : TrackerBuilderAction
    data class OptionPayloadLabelChanged(val id: String, val value: String) : TrackerBuilderAction
    data class OptionPayloadUnitChanged(val id: String, val value: String) : TrackerBuilderAction
    data object CloseOptionEditor : TrackerBuilderAction
    data object AddField : TrackerBuilderAction
    data class EditField(val id: String) : TrackerBuilderAction
    data class RemoveField(val id: String) : TrackerBuilderAction
    data class FieldLabelChanged(val id: String, val value: String) : TrackerBuilderAction
    data class FieldKindChanged(val id: String, val value: BuilderFieldKindUi) : TrackerBuilderAction
    data class FieldRequiredChanged(val id: String, val value: Boolean) : TrackerBuilderAction
    data class FieldUnitChanged(val id: String, val value: String) : TrackerBuilderAction
    data class FieldAddOption(val fieldId: String) : TrackerBuilderAction
    data class FieldRemoveOption(val fieldId: String, val optionId: String) : TrackerBuilderAction
    data class FieldOptionLabelChanged(val fieldId: String, val optionId: String, val value: String) : TrackerBuilderAction
    data class FieldOptionPayloadKindChanged(val fieldId: String, val optionId: String, val value: BuilderPayloadKindUi) : TrackerBuilderAction
    data class FieldOptionPayloadLabelChanged(val fieldId: String, val optionId: String, val value: String) : TrackerBuilderAction
    data class FieldOptionPayloadUnitChanged(val fieldId: String, val optionId: String, val value: String) : TrackerBuilderAction
    data object CloseFieldEditor : TrackerBuilderAction
}

@Immutable
data class QuickLogOptionUi(
    val id: String,
    val label: String,
    val supporting: String? = null,
    val selected: Boolean = false,
    val payloadKind: BuilderPayloadKindUi = BuilderPayloadKindUi.NONE,
    val payloadUnit: String? = null,
)

@Immutable
data class QuickLogFieldUi(
    val id: String,
    val label: String,
    val value: String,
    val kind: BuilderFieldKindUi = BuilderFieldKindUi.NUMBER,
    val options: List<QuickLogOptionUi> = emptyList(),
    val placeholder: String = "",
    val required: Boolean = false,
    val suffix: String? = null,
    val error: String? = null,
)

@Immutable
data class QuickLogUiState(
    val trackerId: String,
    val title: String,
    val kind: TrackerKindUi,
    val glyph: TrackerGlyphUi,
    val accent: Color,
    val timestampLabel: String,
    val value: String = "",
    val unit: String? = null,
    val options: List<QuickLogOptionUi> = emptyList(),
    val fields: List<QuickLogFieldUi> = emptyList(),
    val counterDelta: Int = 1,
    val note: String = "",
    val editingEntryId: String? = null,
    val canSave: Boolean = true,
    val saving: Boolean = false,
    val errorMessage: String? = null,
)

sealed interface QuickLogAction {
    data object Dismiss : QuickLogAction
    data object Save : QuickLogAction
    data object Delete : QuickLogAction
    data object EditTimestamp : QuickLogAction
    data object CounterIncrement : QuickLogAction
    data object CounterDecrement : QuickLogAction
    data object CounterCorrection : QuickLogAction
    data class CounterDeltaChanged(val value: Int) : QuickLogAction
    data class ValueChanged(val value: String) : QuickLogAction
    data class OptionSelected(val id: String) : QuickLogAction
    data class FieldChanged(val id: String, val value: String) : QuickLogAction
    data class FieldOptionSelected(val fieldId: String, val optionId: String) : QuickLogAction
    data class EditFieldTimestamp(val fieldId: String) : QuickLogAction
    data class NoteChanged(val value: String) : QuickLogAction
}

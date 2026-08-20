package dev.opentrack.app.presentation

import androidx.compose.runtime.Immutable
import dev.opentrack.app.domain.model.RecordedAt
import dev.opentrack.app.domain.model.TrackerDefinition
import dev.opentrack.app.domain.model.TrackerEntry
import dev.opentrack.app.preferences.UserPreferences
import dev.opentrack.app.ui.model.DashboardEditorUiState
import dev.opentrack.app.ui.model.DashboardUiState
import dev.opentrack.app.ui.model.DateRangeUi
import dev.opentrack.app.ui.model.DetailTabUi
import dev.opentrack.app.ui.model.HistoryUiState
import dev.opentrack.app.ui.model.OnboardingUiState
import dev.opentrack.app.ui.model.QuickLogUiState
import dev.opentrack.app.ui.model.SettingsUiState
import dev.opentrack.app.ui.model.TrackerBuilderUiState
import dev.opentrack.app.ui.model.TrackerDetailUiState
import dev.opentrack.app.ui.model.TrackerSummaryUi

@Immutable
enum class MainTab { DASHBOARD, TRACKERS, HISTORY, SETTINGS }

@Immutable
sealed interface AppDestination {
    data class Main(val tab: MainTab) : AppDestination
    data class TrackerDetail(val trackerId: String) : AppDestination
    data class TrackerBuilder(val trackerId: String? = null) : AppDestination
    data object DashboardEditor : AppDestination
    data object Backup : AppDestination
}

@Immutable
data class AppUiState(
    val ready: Boolean = false,
    val onboardingRequired: Boolean = false,
    val preferences: UserPreferences? = null,
    val destination: AppDestination = AppDestination.Main(MainTab.DASHBOARD),
    val dashboard: DashboardUiState = DashboardUiState(isLoading = true),
    val trackers: List<TrackerSummaryUi> = emptyList(),
    val history: HistoryUiState = HistoryUiState(isLoading = true),
    val detail: TrackerDetailUiState? = null,
    val dashboardEditor: DashboardEditorUiState = DashboardEditorUiState(),
    val builder: TrackerBuilderUiState? = null,
    val quickLog: QuickLogUiState? = null,
    val settings: SettingsUiState = SettingsUiState(),
    val onboarding: OnboardingUiState = OnboardingUiState(),
    val showArchived: Boolean = false,
    val message: String? = null,
    val canUndo: Boolean = false,
    val undoEntryId: String? = null,
    val importPreview: ImportPreviewUi? = null,
)

@Immutable
data class ImportPreviewUi(
    val trackerCount: Int,
    val entryCount: Int,
)

internal data class RepositorySnapshot(
    val trackers: List<TrackerDefinition> = emptyList(),
    val entries: List<TrackerEntry> = emptyList(),
    val dashboards: List<dev.opentrack.app.domain.model.Dashboard> = emptyList(),
)

internal data class QuickLogDraft(
    val tracker: TrackerDefinition,
    val ui: QuickLogUiState,
    val editingEntryId: String? = null,
    val recordedAt: RecordedAt? = null,
    val fieldTimestamps: Map<String, RecordedAt> = emptyMap(),
)

internal data class InteractionState(
    val destination: AppDestination = AppDestination.Main(MainTab.DASHBOARD),
    val historyQuery: String = "",
    val historyTrackerId: String? = null,
    val historyRange: DateRangeUi? = null,
    val detailTab: DetailTabUi = DetailTabUi.OVERVIEW,
    val detailRange: DateRangeUi = DateRangeUi.MONTH,
    val showArchived: Boolean = false,
    val builder: TrackerBuilderUiState? = null,
    val quickLog: QuickLogDraft? = null,
    val onboarding: OnboardingUiState = OnboardingUiState(),
    val message: String? = null,
    val undoEntryId: String? = null,
    val exportInProgress: Boolean = false,
    val importInProgress: Boolean = false,
    val lastExportLabel: String? = null,
    val importPreview: ImportPreviewUi? = null,
)

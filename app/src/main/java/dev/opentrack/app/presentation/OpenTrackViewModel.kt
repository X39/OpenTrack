package dev.opentrack.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.opentrack.app.domain.model.Aggregation
import dev.opentrack.app.domain.model.AnalyticsMetric
import dev.opentrack.app.domain.model.ChoiceOption
import dev.opentrack.app.domain.model.ChartStyle
import dev.opentrack.app.domain.model.Dashboard
import dev.opentrack.app.domain.model.DashboardSeries
import dev.opentrack.app.domain.model.DashboardWidget
import dev.opentrack.app.domain.model.DashboardWidgetKind
import dev.opentrack.app.domain.model.EnumPayloadKind
import dev.opentrack.app.domain.model.FieldKind
import dev.opentrack.app.domain.model.QuickAddConfig
import dev.opentrack.app.domain.model.QuickAddMode
import dev.opentrack.app.domain.model.TimeBucket
import dev.opentrack.app.domain.model.TimeRangePreset
import dev.opentrack.app.domain.model.TimestampPrecision
import dev.opentrack.app.domain.model.TrackerDefinition
import dev.opentrack.app.domain.model.TrackerField
import dev.opentrack.app.domain.model.TrackerKind
import dev.opentrack.app.domain.model.WidgetSpan
import dev.opentrack.app.domain.model.BackupSnapshot
import dev.opentrack.app.domain.model.newId
import dev.opentrack.app.domain.repository.TrackerRepository
import dev.opentrack.app.preferences.AppPreferences
import dev.opentrack.app.preferences.ThemeMode
import dev.opentrack.app.ui.model.BackupUiState
import dev.opentrack.app.ui.model.DateRangeUi
import dev.opentrack.app.ui.model.DetailTabUi
import dev.opentrack.app.ui.model.OnboardingTemplateUi
import dev.opentrack.app.ui.model.OnboardingUiState
import dev.opentrack.app.ui.model.QuickLogAction
import dev.opentrack.app.ui.model.TimestampPrecisionUi
import dev.opentrack.app.ui.model.TrackerBuilderAction
import dev.opentrack.app.ui.model.TrackerGlyphUi
import dev.opentrack.app.ui.theme.SignalPalette
import dev.opentrack.app.usecase.QuickAddResult
import dev.opentrack.app.usecase.TrackingActions
import java.io.InputStream
import java.io.OutputStream
import java.io.Writer
import java.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class OpenTrackViewModel(
    private val repository: TrackerRepository,
    private val preferences: AppPreferences,
    private val trackingActions: TrackingActions,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {
    private var pendingImportSnapshot: BackupSnapshot? = null
    private val dashboardMutation = Mutex()
    private val interaction = kotlinx.coroutines.flow.MutableStateFlow(
        InteractionState(onboarding = OnboardingUiState(templates = starterTemplateUi())),
    )

    private val repositorySnapshot = combine(
        repository.observeTrackers(includeArchived = true),
        repository.observeEntries(),
        repository.observeDashboards(),
    ) { trackers, entries, dashboards -> RepositorySnapshot(trackers, entries, dashboards) }

    val state: StateFlow<AppUiState> = combine(
        repositorySnapshot,
        preferences.values,
        interaction,
    ) { snapshot, userPreferences, local ->
        val summaries = UiModelMapper.trackerSummaries(
            snapshot.trackers,
            snapshot.entries,
            local.showArchived,
            clock,
        ).map { summary ->
            val configured = snapshot.dashboards.firstOrNull()?.widgets.orEmpty()
            val pinned = snapshot.dashboards.isEmpty() || configured.any { widget ->
                widget.visible && widget.series.any { it.trackerId == summary.id }
            }
            summary.copy(pinned = pinned)
        }
        val detail = (local.destination as? AppDestination.TrackerDetail)?.trackerId?.let { id ->
            snapshot.trackers.firstOrNull { it.id == id }?.let { tracker ->
                UiModelMapper.detail(tracker, snapshot.entries, local.detailTab, local.detailRange, clock)
            }
        }
        val settings = UiModelMapper.settings(userPreferences).copy(
            backup = BackupUiState(
                automaticBackupEnabled = true,
                exportInProgress = local.exportInProgress,
                importInProgress = local.importInProgress,
                lastExportLabel = local.lastExportLabel,
            ),
        )
        AppUiState(
            ready = true,
            onboardingRequired = !userPreferences.onboardingComplete,
            preferences = userPreferences,
            destination = local.destination,
            dashboard = UiModelMapper.dashboard(snapshot.trackers, snapshot.entries, snapshot.dashboards, clock),
            trackers = summaries,
            history = UiModelMapper.history(
                snapshot.trackers,
                snapshot.entries,
                local.historyQuery,
                local.historyTrackerId,
                local.historyRange,
                clock,
            ),
            detail = detail,
            dashboardEditor = UiModelMapper.dashboardEditor(
                snapshot.trackers,
                snapshot.entries,
                snapshot.dashboards,
                clock,
            ),
            builder = local.builder,
            quickLog = local.quickLog?.ui,
            settings = settings,
            onboarding = local.onboarding,
            showArchived = local.showArchived,
            message = local.message,
            canUndo = local.undoEntryId != null,
            undoEntryId = local.undoEntryId,
            importPreview = local.importPreview,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppUiState())

    fun selectTab(tab: MainTab) = update { it.copy(destination = AppDestination.Main(tab), message = null) }

    fun openTracker(trackerId: String) = update {
        it.copy(destination = AppDestination.TrackerDetail(trackerId), detailTab = DetailTabUi.OVERVIEW)
    }

    fun createTracker() {
        val precision = when (state.value.preferences?.defaultTimestampPrecision ?: TimestampPrecision.DATE_TIME) {
            TimestampPrecision.DAY -> TimestampPrecisionUi.DAY
            TimestampPrecision.DATE_TIME -> TimestampPrecisionUi.DATE_AND_TIME
        }
        update {
            it.copy(
                destination = AppDestination.TrackerBuilder(),
                builder = TrackerBuilderLogic.initial().copy(precision = precision),
                message = null,
            )
        }
    }

    fun editTracker(trackerId: String) {
        viewModelScope.launch {
            val tracker = repository.getTracker(trackerId) ?: return@launch showMessage("Tracker not found")
            update {
                it.copy(
                    destination = AppDestination.TrackerBuilder(trackerId),
                    builder = TrackerBuilderLogic.initial(tracker),
                    message = null,
                )
            }
        }
    }

    fun openDashboardEditor() = update { it.copy(destination = AppDestination.DashboardEditor) }
    fun openBackup() = update { it.copy(destination = AppDestination.Backup) }

    fun navigateBack() = update { current ->
        val next = when (current.destination) {
            AppDestination.DashboardEditor -> AppDestination.Main(MainTab.DASHBOARD)
            AppDestination.Backup -> AppDestination.Main(MainTab.SETTINGS)
            is AppDestination.TrackerBuilder -> AppDestination.Main(MainTab.TRACKERS)
            is AppDestination.TrackerDetail -> AppDestination.Main(MainTab.TRACKERS)
            is AppDestination.Main -> current.destination
        }
        current.copy(destination = next, builder = null, quickLog = null, message = null)
    }

    fun setHistoryQuery(query: String) = update { it.copy(historyQuery = query) }

    fun cycleHistoryTracker() {
        val trackers = state.value.trackers.filterNot { it.archived }
        update { current ->
            val ids = listOf<String?>(null) + trackers.map { it.id }
            val index = ids.indexOf(current.historyTrackerId).coerceAtLeast(0)
            current.copy(historyTrackerId = ids[(index + 1) % ids.size])
        }
    }

    fun cycleHistoryRange() = update { current ->
        val values = listOf<DateRangeUi?>(null, DateRangeUi.WEEK, DateRangeUi.MONTH, DateRangeUi.YEAR)
        val index = values.indexOf(current.historyRange).coerceAtLeast(0)
        current.copy(historyRange = values[(index + 1) % values.size])
    }

    fun openFirstQuickLog() {
        val trackerId = interaction.value.historyTrackerId
            ?: state.value.trackers.firstOrNull { !it.archived }?.id
        if (trackerId == null) createTracker() else quickLog(trackerId)
    }

    fun quickLog(trackerId: String, presetId: String? = null) {
        viewModelScope.launch {
            when (val result = trackingActions.quickAdd(trackerId, presetId)) {
                is QuickAddResult.Recorded -> update {
                    it.copy(
                        quickLog = null,
                        message = "Recorded ${state.value.trackers.firstOrNull { item -> item.id == trackerId }?.name ?: "entry"}",
                        undoEntryId = result.entry.id,
                    )
                }
                is QuickAddResult.NeedsInput -> update {
                    it.copy(quickLog = QuickLogLogic.initial(result.tracker, clock = clock), message = null)
                }
                is QuickAddResult.MissingTracker -> showMessage("Tracker not found")
            }
        }
    }

    fun editEntry(entryId: String) {
        viewModelScope.launch {
            val entry = repository.getEntry(entryId) ?: return@launch showMessage("Entry not found")
            val tracker = repository.getTracker(entry.trackerId) ?: return@launch showMessage("Tracker not found")
            update { it.copy(quickLog = QuickLogLogic.initial(tracker, entry, clock), message = null) }
        }
    }

    fun onQuickLogAction(action: QuickLogAction) {
        val draft = interaction.value.quickLog ?: return
        when (action) {
            QuickLogAction.Dismiss -> update { it.copy(quickLog = null) }
            QuickLogAction.Save -> saveQuickLog(draft)
            else -> update { it.copy(quickLog = QuickLogLogic.reduce(draft, action)) }
        }
    }

    fun quickLogTimestampPrecision(fieldId: String?): TimestampPrecision {
        val draft = interaction.value.quickLog ?: return TimestampPrecision.DATE_TIME
        return fieldId?.let { id ->
            draft.tracker.fields.firstOrNull { it.id == id }?.timestampPrecision
        } ?: draft.tracker.timestampPrecision
    }

    fun quickLogTimestamp(fieldId: String?): dev.opentrack.app.domain.model.RecordedAt? {
        val draft = interaction.value.quickLog ?: return null
        return fieldId?.let { draft.fieldTimestamps[it] } ?: draft.recordedAt
    }

    fun setQuickLogTimestamp(fieldId: String?, value: dev.opentrack.app.domain.model.RecordedAt) {
        val draft = interaction.value.quickLog ?: return
        update { it.copy(quickLog = QuickLogLogic.withTimestamp(draft, fieldId, value)) }
    }

    fun deleteEntry(entryId: String) {
        viewModelScope.launch {
            runCatching { repository.deleteEntry(entryId) }
                .onSuccess { update { it.copy(quickLog = null, message = "Entry deleted") } }
                .onFailure { showMessage(it.message ?: "Could not delete entry") }
        }
    }

    fun undoLastQuickLog() {
        val entryId = interaction.value.undoEntryId ?: return
        viewModelScope.launch {
            runCatching { trackingActions.undo(entryId) }
                .onSuccess { update { it.copy(undoEntryId = null, message = "Recording undone") } }
                .onFailure { showMessage(it.message ?: "Could not undo") }
        }
    }

    fun clearMessage() = update { it.copy(message = null, undoEntryId = null) }

    fun onBuilderAction(action: TrackerBuilderAction) {
        val builder = interaction.value.builder ?: return
        when (action) {
            TrackerBuilderAction.Back -> if (builder.step == 0) navigateBack() else update {
                it.copy(builder = TrackerBuilderLogic.reduce(builder, action))
            }
            TrackerBuilderAction.Save -> saveBuilder(builder)
            else -> update { it.copy(builder = TrackerBuilderLogic.reduce(builder, action)) }
        }
    }

    fun toggleShowArchived() = update { it.copy(showArchived = !it.showArchived) }

    fun archiveTracker(trackerId: String, archived: Boolean) {
        viewModelScope.launch {
            runCatching { repository.archiveTracker(trackerId, archived) }
                .onFailure { showMessage(it.message ?: "Could not update tracker") }
        }
    }

    fun setTrackerPinned(trackerId: String, pinned: Boolean) = mutateDashboard { dashboard ->
        val existing = dashboard.widgets.filterNot { widget -> widget.series.any { it.trackerId == trackerId } }
        val tracker = repository.getTracker(trackerId)
        if (pinned && tracker != null) dashboard.copy(widgets = existing + defaultWidget(tracker, existing.size))
        else dashboard.copy(widgets = existing.mapIndexed { index, widget -> widget.copy(order = index) })
    }

    fun addDashboardWidget() {
        val active = state.value.trackers.filterNot { it.archived }
        if (active.isEmpty()) {
            createTracker()
            return
        }
        mutateDashboard { dashboard ->
            val usage = dashboard.widgets.flatMap { it.series }.groupingBy { it.trackerId }.eachCount()
            val summary = active.minWithOrNull(compareBy({ usage[it.id] ?: 0 }, { it.name }))
            val tracker = summary
                ?.id?.let { repository.getTracker(it) }
                ?: return@mutateDashboard dashboard
            dashboard.copy(
                widgets = dashboard.widgets + defaultWidget(
                    tracker = tracker,
                    order = dashboard.widgets.size,
                    variant = usage[tracker.id] ?: 0,
                ),
            )
        }
    }

    fun setWidgetVisible(widgetId: String, visible: Boolean) = mutateDashboard { dashboard ->
        dashboard.copy(widgets = dashboard.widgets.map { if (it.id == widgetId) it.copy(visible = visible) else it })
    }

    fun editDashboardWidget(widgetId: String) = mutateDashboard { dashboard ->
        dashboard.copy(widgets = dashboard.widgets.map { widget ->
            if (widget.id != widgetId) widget else widget.copy(
                span = if (widget.span == WidgetSpan.WIDE) WidgetSpan.COMPACT else WidgetSpan.WIDE,
            )
        })
    }

    fun removeDashboardWidget(widgetId: String) = mutateDashboard { dashboard ->
        dashboard.copy(widgets = dashboard.widgets.filterNot { it.id == widgetId }.mapIndexed { index, widget ->
            widget.copy(order = index)
        })
    }

    fun moveDashboardWidget(widgetId: String, delta: Int) = mutateDashboard { dashboard ->
        val widgets = dashboard.widgets.sortedBy { it.order }.toMutableList()
        val from = widgets.indexOfFirst { it.id == widgetId }
        if (from < 0) return@mutateDashboard dashboard
        val to = (from + delta).coerceIn(0, widgets.lastIndex)
        if (to != from) widgets.add(to, widgets.removeAt(from))
        dashboard.copy(widgets = widgets.mapIndexed { index, widget -> widget.copy(order = index) })
    }

    fun selectDetailTab(tab: DetailTabUi) = update { it.copy(detailTab = tab) }

    fun selectDetailRange(range: DateRangeUi) = update { it.copy(detailRange = range) }

    fun cycleTheme() {
        val next = when (state.value.preferences?.themeMode ?: ThemeMode.SYSTEM) {
            ThemeMode.SYSTEM -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.SYSTEM
        }
        viewModelScope.launch { preferences.setThemeMode(next) }
    }

    fun toggleWeekStart() {
        val current = state.value.preferences?.weekStartsMonday ?: true
        viewModelScope.launch { preferences.setWeekStartsMonday(!current) }
    }

    fun cycleDefaultPrecision() {
        val current = state.value.preferences?.defaultTimestampPrecision ?: TimestampPrecision.DATE_TIME
        val next = if (current == TimestampPrecision.DATE_TIME) TimestampPrecision.DAY else TimestampPrecision.DATE_TIME
        viewModelScope.launch { preferences.setDefaultTimestampPrecision(next) }
    }

    fun toggleOnboardingTemplate(id: String) = update { current ->
        current.copy(onboarding = current.onboarding.copy(
            templates = current.onboarding.templates.map { if (it.id == id) it.copy(selected = !it.selected) else it },
        ))
    }

    fun setMetricUnits(metric: Boolean) = update {
        it.copy(onboarding = it.onboarding.copy(usesMetricUnits = metric))
    }

    fun onboardingBack() = update {
        it.copy(onboarding = it.onboarding.copy(page = (it.onboarding.page - 1).coerceAtLeast(0)))
    }

    fun onboardingContinue() {
        val onboarding = interaction.value.onboarding
        if (onboarding.page < 2) {
            update { it.copy(onboarding = onboarding.copy(page = onboarding.page + 1)) }
            return
        }
        viewModelScope.launch {
            createSelectedTemplates(onboarding)
            preferences.completeOnboarding()
        }
    }

    fun buildCustomFromOnboarding() {
        viewModelScope.launch {
            preferences.completeOnboarding()
            createTracker()
        }
    }

    fun markExportStarted() = update { it.copy(exportInProgress = true, message = null) }
    fun markImportStarted() = update { it.copy(importInProgress = true, message = null) }
    fun reportTransferError(message: String) = update {
        it.copy(exportInProgress = false, importInProgress = false, message = message)
    }

    fun exportBackup(output: OutputStream) {
        viewModelScope.launch {
            update { it.copy(exportInProgress = true) }
            runCatching {
                output.use { stream ->
                    withContext(Dispatchers.IO) { BackupTransfer.write(repository.snapshot(), stream) }
                }
            }.onSuccess {
                update {
                    it.copy(
                        exportInProgress = false,
                        lastExportLabel = "Just now",
                        message = "Backup exported",
                    )
                }
            }.onFailure { error ->
                update { it.copy(exportInProgress = false, message = error.message ?: "Export failed") }
            }
        }
    }

    fun prepareImport(input: InputStream) {
        viewModelScope.launch {
            update { it.copy(importInProgress = true) }
            runCatching {
                input.use { stream -> withContext(Dispatchers.IO) { BackupTransfer.read(stream) } }
            }.onSuccess { result ->
                pendingImportSnapshot = result.snapshot
                update {
                    it.copy(
                        importInProgress = false,
                        importPreview = ImportPreviewUi(result.trackerCount, result.entryCount),
                    )
                }
            }.onFailure { error ->
                update { it.copy(importInProgress = false, message = error.message ?: "Import failed") }
            }
        }
    }

    fun confirmImport() {
        val snapshot = pendingImportSnapshot ?: return
        update { it.copy(importInProgress = true, importPreview = null) }
        viewModelScope.launch {
            runCatching { repository.replaceAll(snapshot) }
                .onSuccess {
                    pendingImportSnapshot = null
                    update { it.copy(importInProgress = false, message = "Backup restored") }
                }
                .onFailure { error ->
                    update { it.copy(importInProgress = false, message = error.message ?: "Import failed") }
                }
        }
    }

    fun cancelImport() {
        pendingImportSnapshot = null
        update { it.copy(importPreview = null, importInProgress = false) }
    }

    fun exportTrackerCsv(trackerId: String, writer: Writer) {
        viewModelScope.launch {
            runCatching {
                writer.use { destination ->
                    val snapshot = repository.snapshot()
                    val tracker = snapshot.trackers.firstOrNull { it.id == trackerId } ?: error("Tracker not found")
                    withContext(Dispatchers.IO) {
                        dev.opentrack.app.data.export.TrackerCsvExporter.write(tracker, snapshot.entries, destination)
                    }
                }
            }.onSuccess {
                showMessage("CSV exported")
            }.onFailure { error ->
                showMessage(error.message ?: "CSV export failed")
            }
        }
    }

    private fun saveQuickLog(draft: QuickLogDraft) {
        update { it.copy(quickLog = QuickLogLogic.withSaving(draft, true)) }
        viewModelScope.launch {
            runCatching {
                trackingActions.record(
                    tracker = draft.tracker,
                    values = QuickLogLogic.values(draft),
                    recordedAt = draft.recordedAt ?: dev.opentrack.app.domain.model.RecordedAt.now(
                        draft.tracker.timestampPrecision,
                        clock,
                    ),
                    note = draft.ui.note,
                    entryId = draft.editingEntryId,
                )
            }.onSuccess { entry ->
                update {
                    it.copy(
                        quickLog = null,
                        message = if (draft.editingEntryId == null) "Entry recorded" else "Entry updated",
                        undoEntryId = if (draft.editingEntryId == null) entry.id else null,
                    )
                }
            }.onFailure { error ->
                update { current ->
                    current.copy(quickLog = QuickLogLogic.withError(draft, error.message ?: "Could not save"))
                }
            }
        }
    }

    private fun saveBuilder(builder: dev.opentrack.app.ui.model.TrackerBuilderUiState) {
        viewModelScope.launch {
            val existing = builder.editingTrackerId?.let { repository.getTracker(it) }
            val tracker = runCatching {
                val tracker = TrackerBuilderLogic.build(builder, existing)
                repository.saveTracker(tracker)
                tracker
            }.getOrElse { error ->
                update { it.copy(builder = builder.copy(errorMessage = error.message ?: "Could not save tracker")) }
                return@launch
            }
            val dashboardError = if (builder.addToDashboard) {
                runCatching { ensureDashboardWidget(tracker) }.exceptionOrNull()
            } else null
            update {
                it.copy(
                    destination = AppDestination.TrackerDetail(tracker.id),
                    builder = null,
                    message = when {
                        dashboardError != null -> "Tracker saved, but the dashboard could not be updated"
                        existing == null -> "Tracker created"
                        else -> "Tracker updated"
                    },
                )
            }
        }
    }

    private fun mutateDashboard(transform: suspend (Dashboard) -> Dashboard) {
        viewModelScope.launch {
            runCatching {
                dashboardMutation.withLock {
                    val existing = repository.snapshot().dashboards.firstOrNull() ?: Dashboard(name = "Dashboard")
                    repository.saveDashboard(transform(existing))
                }
            }.onFailure { showMessage(it.message ?: "Could not update dashboard") }
        }
    }

    private suspend fun ensureDashboardWidget(tracker: TrackerDefinition) {
        dashboardMutation.withLock {
            val dashboard = repository.snapshot().dashboards.firstOrNull() ?: Dashboard(name = "Dashboard")
            if (dashboard.widgets.any { widget -> widget.series.any { it.trackerId == tracker.id } }) return@withLock
            repository.saveDashboard(dashboard.copy(widgets = dashboard.widgets + defaultWidget(tracker, dashboard.widgets.size)))
        }
    }

    private fun defaultWidget(tracker: TrackerDefinition, order: Int, variant: Int = 0): DashboardWidget {
        val alternate = variant % 2 == 1
        val activeFields = tracker.fields.filter { it.archivedAt == null }.sortedBy { it.order }
        val numericGroupField = activeFields.firstOrNull {
            it.kind in setOf(FieldKind.VALUE, FieldKind.COUNTER, FieldKind.DURATION, FieldKind.RADIO)
        }
        val metric = when (tracker.kind) {
            TrackerKind.TIMESTAMP -> if (alternate) AnalyticsMetric.OCCURRENCE_COUNT else AnalyticsMetric.LAST_RECORDED
            TrackerKind.GROUP -> if (alternate && numericGroupField != null) AnalyticsMetric.NUMERIC_VALUE else AnalyticsMetric.OCCURRENCE_COUNT
            TrackerKind.VALUE -> if (alternate) AnalyticsMetric.NUMERIC_VALUE else AnalyticsMetric.LATEST_VALUE
            TrackerKind.ENUM -> if (alternate) AnalyticsMetric.OCCURRENCE_COUNT else AnalyticsMetric.ENUM_COUNT
            TrackerKind.RADIO -> if (alternate) AnalyticsMetric.ENUM_COUNT else AnalyticsMetric.RADIO_SCORE
            TrackerKind.BOOLEAN -> if (alternate) AnalyticsMetric.TRUE_COUNT else AnalyticsMetric.TRUE_RATE
            TrackerKind.COUNTER -> if (alternate) AnalyticsMetric.COUNTER_SUM else AnalyticsMetric.COUNTER_RUNNING_TOTAL
            TrackerKind.DURATION -> if (alternate) AnalyticsMetric.DURATION_AVERAGE else AnalyticsMetric.DURATION_TOTAL
        }
        val fieldId = when {
            tracker.kind == TrackerKind.TIMESTAMP -> null
            tracker.kind == TrackerKind.GROUP -> numericGroupField?.id
            else -> activeFields.firstOrNull()?.id
        }
        val chartStyle = when (metric) {
            AnalyticsMetric.LAST_RECORDED, AnalyticsMetric.OCCURRENCE_COUNT -> ChartStyle.CALENDAR
            AnalyticsMetric.ENUM_COUNT, AnalyticsMetric.TRUE_COUNT, AnalyticsMetric.TRUE_RATE -> ChartStyle.DISTRIBUTION
            else -> ChartStyle.LINE
        }
        val widgetTitle = if (!alternate) tracker.name else when (metric) {
            AnalyticsMetric.OCCURRENCE_COUNT -> "${tracker.name} activity"
            AnalyticsMetric.NUMERIC_VALUE -> "${tracker.name} average"
            AnalyticsMetric.ENUM_COUNT -> "${tracker.name} distribution"
            AnalyticsMetric.TRUE_COUNT -> "${tracker.name} yes count"
            AnalyticsMetric.COUNTER_SUM -> "${tracker.name} change total"
            AnalyticsMetric.DURATION_AVERAGE -> "${tracker.name} average"
            else -> tracker.name
        }
        return DashboardWidget(
            kind = when (tracker.kind) {
                TrackerKind.TIMESTAMP -> DashboardWidgetKind.LAST_RECORDED
                TrackerKind.VALUE, TrackerKind.RADIO -> DashboardWidgetKind.LATEST_VALUE
                else -> DashboardWidgetKind.SUMMARY
            },
            title = widgetTitle,
            chartStyle = chartStyle,
            range = TimeRangePreset.THIRTY_DAYS,
            bucket = TimeBucket.DAY,
            order = order,
            span = WidgetSpan.WIDE,
            series = listOf(
                DashboardSeries(
                    trackerId = tracker.id,
                    fieldId = fieldId,
                    metric = metric,
                    aggregation = when (metric) {
                        AnalyticsMetric.OCCURRENCE_COUNT, AnalyticsMetric.ENUM_COUNT -> Aggregation.COUNT
                        AnalyticsMetric.LATEST_VALUE -> Aggregation.LAST
                        AnalyticsMetric.RADIO_SCORE, AnalyticsMetric.TRUE_RATE,
                        AnalyticsMetric.NUMERIC_VALUE, AnalyticsMetric.DURATION_AVERAGE -> Aggregation.AVERAGE
                        else -> Aggregation.SUM
                    },
                ),
            ),
        )
    }

    private suspend fun createSelectedTemplates(onboarding: OnboardingUiState) {
        val selected = onboarding.templates.filter { it.selected }.mapNotNull { template ->
            starterDefinition(template.id, onboarding.usesMetricUnits)
        }
        val existingNames = repository.snapshot().trackers.mapTo(hashSetOf()) { it.name.lowercase() }
        selected.filterNot { it.name.lowercase() in existingNames }.forEach { tracker ->
            repository.saveTracker(tracker)
            ensureDashboardWidget(tracker)
        }
    }

    private fun showMessage(message: String) = update { it.copy(message = message) }

    private inline fun update(transform: (InteractionState) -> InteractionState) {
        interaction.value = transform(interaction.value)
    }

    class Factory(
        private val repository: TrackerRepository,
        private val preferences: AppPreferences,
        private val trackingActions: TrackingActions,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(OpenTrackViewModel::class.java))
            return OpenTrackViewModel(repository, preferences, trackingActions) as T
        }
    }
}

private fun starterTemplateUi() = listOf(
    OnboardingTemplateUi("water", "Water", "Count glasses through the day", TrackerGlyphUi.WATER, SignalPalette.Sky, true),
    OnboardingTemplateUi("weight", "Weight", "Track a measured value over time", TrackerGlyphUi.SCALE, SignalPalette.Moss, true),
    OnboardingTemplateUi("mood", "Energy", "Rate your energy from low to high", TrackerGlyphUi.MOOD, SignalPalette.Sun),
    OnboardingTemplateUi("gym", "Workout", "Record which exercise you completed", TrackerGlyphUi.FITNESS, SignalPalette.Coral),
)

private fun starterDefinition(id: String, metric: Boolean): TrackerDefinition? {
    val trackerId = newId()
    return when (id) {
        "water" -> TrackerDefinition(
            id = trackerId,
            name = "Water",
            kind = TrackerKind.COUNTER,
            iconKey = TrackerGlyphUi.WATER.name,
            colorArgb = SignalPalette.Sky.value.toLong(),
            fields = listOf(TrackerField(label = "Glasses", kind = FieldKind.COUNTER, unit = "glasses")),
        )
        "weight" -> TrackerDefinition(
            id = trackerId,
            name = "Weight",
            kind = TrackerKind.VALUE,
            iconKey = TrackerGlyphUi.SCALE.name,
            colorArgb = SignalPalette.Moss.value.toLong(),
            fields = listOf(TrackerField(label = "Weight", kind = FieldKind.VALUE, unit = if (metric) "kg" else "lb")),
            quickAdd = QuickAddConfig(QuickAddMode.OPEN_EDITOR),
        )
        "mood" -> {
            val options = listOf("Low", "Normal", "High").mapIndexed { index, label ->
                ChoiceOption(label = label, order = index, radioScore = (index + 1).toDouble())
            }
            TrackerDefinition(
                id = trackerId,
                name = "Energy",
                kind = TrackerKind.RADIO,
                iconKey = TrackerGlyphUi.MOOD.name,
                colorArgb = SignalPalette.Sun.value.toLong(),
                fields = listOf(TrackerField(label = "Energy", kind = FieldKind.RADIO, options = options)),
                quickAdd = QuickAddConfig(QuickAddMode.OPEN_EDITOR),
            )
        }
        "gym" -> {
            val weightUnit = if (metric) "kg" else "lb"
            val options = listOf(
                ChoiceOption(
                    label = "Bench press",
                    order = 0,
                    payloadKind = EnumPayloadKind.DECIMAL,
                    payloadLabel = "Weight",
                    payloadUnit = weightUnit,
                ),
                ChoiceOption(
                    label = "Butterfly",
                    order = 1,
                    payloadKind = EnumPayloadKind.DECIMAL,
                    payloadLabel = "Weight",
                    payloadUnit = weightUnit,
                ),
                ChoiceOption(
                    label = "Push-ups",
                    order = 2,
                    payloadKind = EnumPayloadKind.INTEGER,
                    payloadLabel = "Repetitions",
                    payloadUnit = "reps",
                ),
            )
            TrackerDefinition(
                id = trackerId,
                name = "Workout",
                kind = TrackerKind.ENUM,
                iconKey = TrackerGlyphUi.FITNESS.name,
                colorArgb = SignalPalette.Coral.value.toLong(),
                fields = listOf(TrackerField(label = "Exercise", kind = FieldKind.ENUM, options = options)),
                quickAdd = QuickAddConfig(QuickAddMode.OPEN_EDITOR),
            )
        }
        else -> null
    }
}

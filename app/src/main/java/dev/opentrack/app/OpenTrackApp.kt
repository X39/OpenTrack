package dev.opentrack.app

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.opentrack.app.domain.model.RecordedAt
import dev.opentrack.app.domain.model.TimestampPrecision
import dev.opentrack.app.preferences.ThemeMode
import dev.opentrack.app.presentation.AppDestination
import dev.opentrack.app.presentation.MainTab
import dev.opentrack.app.presentation.OpenTrackViewModel
import dev.opentrack.app.ui.model.QuickLogAction
import dev.opentrack.app.ui.screen.BackupScreen
import dev.opentrack.app.ui.screen.DashboardEditorScreen
import dev.opentrack.app.ui.screen.DashboardScreen
import dev.opentrack.app.ui.screen.HistoryScreen
import dev.opentrack.app.ui.screen.OnboardingScreen
import dev.opentrack.app.ui.screen.QuickLogSheet
import dev.opentrack.app.ui.screen.SettingsScreen
import dev.opentrack.app.ui.screen.TrackerBuilderScreen
import dev.opentrack.app.ui.screen.TrackerDetailScreen
import dev.opentrack.app.ui.screen.TrackerListScreen
import dev.opentrack.app.ui.theme.SignalTheme
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@Composable
fun OpenTrackApp(
    viewModel: OpenTrackViewModel,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onExportCsv: (String) -> Unit,
    onOpenSystemBackup: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val darkTheme = when (state.preferences?.themeMode ?: ThemeMode.SYSTEM) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    SignalTheme(darkTheme = darkTheme) {
        if (!state.ready) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@SignalTheme
        }

        val snackbarHostState = remember { SnackbarHostState() }
        var showAbout by remember { mutableStateOf(false) }
        var pendingDeleteEntryId by remember { mutableStateOf<String?>(null) }
        val context = LocalContext.current

        LaunchedEffect(state.message, state.undoEntryId) {
            val message = state.message ?: return@LaunchedEffect
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = if (state.canUndo) "Undo" else null,
                withDismissAction = true,
            )
            if (result == SnackbarResult.ActionPerformed && state.canUndo) viewModel.undoLastQuickLog()
            else viewModel.clearMessage()
        }

        BackHandler(
            enabled = state.quickLog != null ||
                (state.onboardingRequired && state.onboarding.page > 0) ||
                (!state.onboardingRequired && state.destination != AppDestination.Main(MainTab.DASHBOARD)),
        ) {
            when {
                state.quickLog != null -> viewModel.onQuickLogAction(QuickLogAction.Dismiss)
                state.onboardingRequired && state.onboarding.page > 0 -> viewModel.onboardingBack()
                state.destination is AppDestination.Main -> viewModel.selectTab(MainTab.DASHBOARD)
                else -> viewModel.navigateBack()
            }
        }

        if (state.onboardingRequired) {
            OnboardingScreen(
                state = state.onboarding,
                onTemplateToggled = viewModel::toggleOnboardingTemplate,
                onMetricUnitsChanged = viewModel::setMetricUnits,
                onContinue = viewModel::onboardingContinue,
                onBack = viewModel::onboardingBack,
                onBuildCustom = viewModel::buildCustomFromOnboarding,
            )
        } else {
            val showBottomBar = state.destination is AppDestination.Main
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    if (showBottomBar) {
                        MainNavigationBar(
                            selected = (state.destination as AppDestination.Main).tab,
                            onSelected = viewModel::selectTab,
                        )
                    }
                },
            ) { outerPadding ->
                val screenModifier = Modifier.fillMaxSize().padding(outerPadding)
                when (val destination = state.destination) {
                    is AppDestination.Main -> when (destination.tab) {
                        MainTab.DASHBOARD -> DashboardScreen(
                            state = state.dashboard,
                            onOpenTracker = viewModel::openTracker,
                            onQuickLog = viewModel::quickLog,
                            onCustomize = viewModel::openDashboardEditor,
                            onCreateTracker = viewModel::createTracker,
                            modifier = screenModifier,
                        )
                        MainTab.TRACKERS -> TrackerListScreen(
                            trackers = state.trackers,
                            onCreateTracker = viewModel::createTracker,
                            onOpenTracker = viewModel::openTracker,
                            onQuickLog = viewModel::quickLog,
                            onPinnedChanged = viewModel::setTrackerPinned,
                            modifier = screenModifier,
                        )
                        MainTab.HISTORY -> HistoryScreen(
                            state = state.history,
                            onQueryChanged = viewModel::setHistoryQuery,
                            onTrackerFilter = viewModel::cycleHistoryTracker,
                            onDateFilter = viewModel::cycleHistoryRange,
                            onOpenEntry = viewModel::editEntry,
                            onQuickLog = viewModel::openFirstQuickLog,
                            modifier = screenModifier,
                        )
                        MainTab.SETTINGS -> SettingsScreen(
                            state = state.settings,
                            onTheme = viewModel::cycleTheme,
                            onPrecision = viewModel::cycleDefaultPrecision,
                            onOpenBackup = viewModel::openBackup,
                            onExportAll = onExportBackup,
                            onImport = onImportBackup,
                            onAbout = { showAbout = true },
                            modifier = screenModifier,
                        )
                    }
                    is AppDestination.TrackerDetail -> state.detail?.let { detail ->
                        TrackerDetailScreen(
                            state = detail,
                            onBack = viewModel::navigateBack,
                            onQuickLog = viewModel::quickLog,
                            onEdit = viewModel::editTracker,
                            onTabSelected = viewModel::selectDetailTab,
                            onRangeSelected = viewModel::selectDetailRange,
                            onOpenEntry = viewModel::editEntry,
                            onExportCsv = onExportCsv,
                            modifier = screenModifier,
                        )
                    }
                    is AppDestination.TrackerBuilder -> state.builder?.let { builder ->
                        TrackerBuilderScreen(
                            state = builder,
                            onAction = viewModel::onBuilderAction,
                            onClose = viewModel::navigateBack,
                            modifier = screenModifier,
                        )
                    }
                    AppDestination.DashboardEditor -> DashboardEditorScreen(
                        state = state.dashboardEditor,
                        onBack = viewModel::navigateBack,
                        onAddWidget = viewModel::addDashboardWidget,
                        onToggleVisible = viewModel::setWidgetVisible,
                        onEditWidget = viewModel::editDashboardWidget,
                        onRemoveWidget = viewModel::removeDashboardWidget,
                        onMoveWidget = viewModel::moveDashboardWidget,
                        modifier = screenModifier,
                    )
                    AppDestination.Backup -> BackupScreen(
                        state = state.settings.backup,
                        onBack = viewModel::navigateBack,
                        onOpenSystemBackup = onOpenSystemBackup,
                        onExport = onExportBackup,
                        onImport = onImportBackup,
                        modifier = screenModifier,
                    )
                }
            }
        }

        state.quickLog?.let { quickLog ->
            QuickLogSheet(
                state = quickLog,
                onAction = { action ->
                    when (action) {
                        QuickLogAction.EditTimestamp -> showTimestampPicker(context, viewModel, null)
                        is QuickLogAction.EditFieldTimestamp -> showTimestampPicker(context, viewModel, action.fieldId)
                        QuickLogAction.Delete -> pendingDeleteEntryId = quickLog.editingEntryId
                        else -> viewModel.onQuickLogAction(action)
                    }
                },
            )
        }

        pendingDeleteEntryId?.let { entryId ->
            AlertDialog(
                onDismissRequest = { pendingDeleteEntryId = null },
                title = { Text("Delete this entry?") },
                text = { Text("This removes the entry permanently. Exported backups are not changed.") },
                confirmButton = {
                    Button(
                        onClick = {
                            pendingDeleteEntryId = null
                            viewModel.deleteEntry(entryId)
                        },
                    ) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeleteEntryId = null }) { Text("Cancel") }
                },
            )
        }

        state.importPreview?.let { preview ->
            AlertDialog(
                onDismissRequest = viewModel::cancelImport,
                title = { Text("Restore this backup?") },
                text = {
                    Text(
                        "This backup contains ${preview.trackerCount} trackers and ${preview.entryCount} entries. " +
                            "Restoring replaces all current OpenTrack data.",
                    )
                },
                confirmButton = { Button(onClick = viewModel::confirmImport) { Text("Replace and restore") } },
                dismissButton = { TextButton(onClick = viewModel::cancelImport) { Text("Cancel") } },
            )
        }

        if (showAbout) {
            AlertDialog(
                onDismissRequest = { showAbout = false },
                title = { Text("OpenTrack") },
                text = {
                    Text(
                        "Version ${BuildConfig.VERSION_NAME}. Your tracking data stays on this device unless you export or back it up.",
                    )
                },
                confirmButton = { TextButton(onClick = { showAbout = false }) { Text("Done") } },
            )
        }
    }
}

@Composable
private fun MainNavigationBar(selected: MainTab, onSelected: (MainTab) -> Unit) {
    NavigationBar {
        val items = listOf(
            Triple(MainTab.DASHBOARD, "Dashboard", Icons.Rounded.Dashboard),
            Triple(MainTab.TRACKERS, "Trackers", Icons.AutoMirrored.Rounded.ViewList),
            Triple(MainTab.HISTORY, "History", Icons.Rounded.History),
            Triple(MainTab.SETTINGS, "Settings", Icons.Rounded.Settings),
        )
        items.forEach { (tab, label, icon) ->
            NavigationBarItem(
                selected = tab == selected,
                onClick = { onSelected(tab) },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}

private fun showTimestampPicker(context: Context, viewModel: OpenTrackViewModel, fieldId: String?) {
    val precision = viewModel.quickLogTimestampPrecision(fieldId)
    val current = viewModel.quickLogTimestamp(fieldId)
    val zone = (current as? RecordedAt.DateTime)?.zoneId ?: ZoneId.systemDefault()
    val currentDate = current?.localDate ?: LocalDate.now(zone)
    val currentTime = (current as? RecordedAt.DateTime)?.instant?.atZone(zone)?.toLocalTime()
        ?: LocalTime.now(zone)
    DatePickerDialog(
        context,
        { _, year, zeroBasedMonth, day ->
            val date = LocalDate.of(year, zeroBasedMonth + 1, day)
            if (precision == TimestampPrecision.DAY) {
                viewModel.setQuickLogTimestamp(fieldId, RecordedAt.Day(date))
            } else {
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        val zoned = date.atTime(hour, minute).atZone(zone)
                        viewModel.setQuickLogTimestamp(fieldId, RecordedAt.DateTime(zoned.toInstant(), zone))
                    },
                    currentTime.hour,
                    currentTime.minute,
                    true,
                ).show()
            }
        },
        currentDate.year,
        currentDate.monthValue - 1,
        currentDate.dayOfMonth,
    ).show()
}

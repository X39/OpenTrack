package dev.opentrack.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.opentrack.app.ui.chart.SignalBarChart
import dev.opentrack.app.ui.chart.SignalCalendarHeatmap
import dev.opentrack.app.ui.chart.SignalDistributionBar
import dev.opentrack.app.ui.chart.SignalLineChart
import dev.opentrack.app.ui.component.EntryRow
import dev.opentrack.app.ui.component.QuickAddButton
import dev.opentrack.app.ui.component.SectionHeader
import dev.opentrack.app.ui.component.SignalEmptyState
import dev.opentrack.app.ui.component.SignalTopBar
import dev.opentrack.app.ui.component.TrackerGlyph
import dev.opentrack.app.ui.model.DateRangeUi
import dev.opentrack.app.ui.model.DetailChartUi
import dev.opentrack.app.ui.model.DetailTabUi
import dev.opentrack.app.ui.model.TrackerDetailUiState

@Composable
fun TrackerDetailScreen(
    state: TrackerDetailUiState,
    onBack: () -> Unit,
    onQuickLog: (String) -> Unit,
    onEdit: (String) -> Unit,
    onTabSelected: (DetailTabUi) -> Unit,
    onRangeSelected: (DateRangeUi) -> Unit,
    onOpenEntry: (String) -> Unit,
    onExportCsv: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            SignalTopBar(state.tracker.name, onBack = onBack, actions = {
                IconButton(onClick = { onExportCsv(state.tracker.id) }) { Icon(Icons.Rounded.Download, "Export CSV") }
                IconButton(onClick = { onEdit(state.tracker.id) }) { Icon(Icons.Rounded.Edit, "Edit tracker") }
            })
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TrackerDetailHeader(state, onQuickLog)
            DetailTabs(state.tab, onTabSelected)
            if (state.tab == DetailTabUi.OVERVIEW) {
                OverviewContent(state, onRangeSelected)
            } else {
                EntryContent(state, onOpenEntry)
            }
        }
    }
}

@Composable
private fun TrackerDetailHeader(state: TrackerDetailUiState, onQuickLog: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrackerGlyph(state.tracker.glyph, state.tracker.accent, null, Modifier.size(52.dp))
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(state.headline, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(state.headlineContext, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        QuickAddButton("Log ${state.tracker.name}", state.tracker.accent, { onQuickLog(state.tracker.id) })
    }
}

@Composable
private fun DetailTabs(selected: DetailTabUi, onSelected: (DetailTabUi) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        DetailTabUi.entries.forEach { tab ->
            val active = tab == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        CircleShape,
                    )
                    .heightIn(min = 48.dp)
                    .selectable(
                        selected = active,
                        role = Role.Tab,
                        onClick = { onSelected(tab) },
                    )
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    tab.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (tab != DetailTabUi.entries.last()) Spacer(Modifier.size(8.dp))
        }
    }
}

@Composable
private fun OverviewContent(state: TrackerDetailUiState, onRangeSelected: (DateRangeUi) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DateRangeUi.entries.forEach { range ->
                    FilterChip(
                        selected = state.range == range,
                        onClick = { onRangeSelected(range) },
                        label = { Text(range.label) },
                    )
                }
            }
        }
        if (state.insights.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader("At a glance")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.insights.take(3).forEach { insight ->
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surface,
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(insight.value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    Text(insight.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (state.charts.isEmpty()) {
            item { SignalEmptyState("More signal needed", "Log a few entries and trends will appear here.") }
        } else {
            itemsIndexed(state.charts, key = { index, chart -> "${chart.title}-$index" }) { _, chart ->
                DetailChartCard(chart, state.tracker.accent)
            }
        }
    }
}

@Composable
private fun DetailChartCard(chart: DetailChartUi, accent: androidx.compose.ui.graphics.Color) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(chart.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            when (chart) {
                is DetailChartUi.Line -> SignalLineChart(chart.points, chart.summary, accent, chart.startLabel, chart.endLabel, Modifier.fillMaxWidth())
                is DetailChartUi.Bars -> SignalBarChart(chart.bars, chart.summary, accent, Modifier.fillMaxWidth())
                is DetailChartUi.Distribution -> SignalDistributionBar(chart.parts, chart.summary, Modifier.fillMaxWidth())
                is DetailChartUi.Calendar -> SignalCalendarHeatmap(chart.days, chart.summary, accent, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun EntryContent(state: TrackerDetailUiState, onOpenEntry: (String) -> Unit) {
    if (state.entries.isEmpty()) {
        SignalEmptyState("No entries yet", "Tap + to record the first one.")
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            items(state.entries, key = { it.id }) { entry ->
                EntryRow(entry, { onOpenEntry(entry.id) }, showTrackerName = false)
            }
        }
    }
}

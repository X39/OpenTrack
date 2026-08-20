package dev.opentrack.app.ui.screen

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.opentrack.app.ui.component.SignalEmptyState
import dev.opentrack.app.ui.component.SignalTopBar
import dev.opentrack.app.ui.component.TrackerGlyph
import dev.opentrack.app.ui.model.DashboardEditorItemUi
import dev.opentrack.app.ui.model.DashboardEditorUiState
import dev.opentrack.app.ui.model.ChartStyleUi

@Composable
fun DashboardEditorScreen(
    state: DashboardEditorUiState,
    onBack: () -> Unit,
    onAddWidget: () -> Unit,
    onToggleVisible: (String, Boolean) -> Unit,
    onEditWidget: (String) -> Unit,
    onChartStyleChanged: (String, ChartStyleUi) -> Unit,
    onRemoveWidget: (String) -> Unit,
    onMoveWidget: (String, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { SignalTopBar("Customize dashboard", onBack = onBack) },
        bottomBar = {
            Button(
                onClick = onAddWidget,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.size(8.dp))
                Text(if (state.hasTrackers) "Add widget" else "Create tracker")
            }
        },
    ) { padding ->
        if (state.items.isEmpty()) {
            SignalEmptyState(
                title = "A blank canvas",
                message = if (state.hasTrackers) {
                    "Add useful summaries and charts from your trackers, then arrange them to suit your day."
                } else {
                    "Create a tracker first, then add its summaries and charts here."
                },
                actionLabel = if (state.hasTrackers) "Add widget" else "Create tracker",
                onAction = onAddWidget,
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(
                        "Choose what appears and arrange it. Add a tracker again for an alternate metric and graph.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                itemsIndexed(state.items, key = { _, item -> item.widget.id }) { index, item ->
                    EditorWidgetRow(
                        item = item,
                        canMoveUp = index > 0,
                        canMoveDown = index < state.items.lastIndex,
                        onToggle = { onToggleVisible(item.widget.id, it) },
                        onEdit = { onEditWidget(item.widget.id) },
                        onChartStyleChanged = { onChartStyleChanged(item.widget.id, it) },
                        onRemove = { onRemoveWidget(item.widget.id) },
                        onMoveUp = { onMoveWidget(item.widget.id, -1) },
                        onMoveDown = { onMoveWidget(item.widget.id, 1) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorWidgetRow(
    item: DashboardEditorItemUi,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onChartStyleChanged: (ChartStyleUi) -> Unit,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TrackerGlyph(item.widget.glyph, item.widget.accent, null, Modifier.size(40.dp))
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.widget.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "${item.widget.size.label} · ${item.chartStyle.label} · ${item.widget.metric}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = item.visible, onCheckedChange = onToggle)
            }
            Text("Graph style", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item.availableChartStyles.forEach { style ->
                    FilterChip(
                        selected = item.chartStyle == style,
                        onClick = { onChartStyleChanged(style) },
                        label = { Text(style.label) },
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onMoveUp, enabled = canMoveUp) { Icon(Icons.Rounded.ArrowUpward, "Move up") }
                IconButton(onClick = onMoveDown, enabled = canMoveDown) { Icon(Icons.Rounded.ArrowDownward, "Move down") }
                IconButton(onClick = onEdit) { Icon(Icons.Rounded.AspectRatio, "Toggle widget size") }
                IconButton(onClick = onRemove) { Icon(Icons.Rounded.DeleteOutline, "Remove widget") }
            }
        }
    }
}

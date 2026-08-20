package dev.opentrack.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.opentrack.app.ui.component.SignalEmptyState
import dev.opentrack.app.ui.component.SignalTopBar
import dev.opentrack.app.ui.component.TrackerWidgetCard
import dev.opentrack.app.ui.model.DashboardUiState
import dev.opentrack.app.ui.model.WidgetSizeUi

@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onOpenTracker: (String) -> Unit,
    onQuickLog: (String) -> Unit,
    onCustomize: () -> Unit,
    onCreateTracker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            SignalTopBar(title = state.greeting, actions = {
                IconButton(onClick = onCreateTracker) { Icon(Icons.Rounded.Add, "Create tracker") }
                IconButton(onClick = onCustomize) { Icon(Icons.Rounded.Tune, "Customize dashboard") }
            })
        },
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(170.dp),
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.dateLabel.isNotBlank()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        state.dateLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
            if (state.isLoading) {
                item(span = { GridItemSpan(maxLineSpan) }) { CircularProgressIndicator() }
            } else if (state.widgets.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SignalEmptyState(
                        title = "Start noticing",
                        message = "Create a tracker and its most useful signal will appear here.",
                        actionLabel = "Create tracker",
                        onAction = onCreateTracker,
                    )
                }
            } else {
                items(
                    items = state.widgets,
                    key = { it.id },
                    span = { GridItemSpan(if (it.size == WidgetSizeUi.WIDE) maxLineSpan else 1) },
                ) { widget ->
                    TrackerWidgetCard(
                        widget = widget,
                        onOpen = { onOpenTracker(widget.trackerId) },
                        onQuickLog = { onQuickLog(widget.trackerId) },
                    )
                }
            }
        }
    }
}

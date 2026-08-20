package dev.opentrack.app.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.opentrack.app.ui.component.QuickAddButton
import dev.opentrack.app.ui.component.SignalEmptyState
import dev.opentrack.app.ui.component.SignalTopBar
import dev.opentrack.app.ui.component.TrackerGlyph
import dev.opentrack.app.ui.model.TrackerSummaryUi

@Composable
fun TrackerListScreen(
    trackers: List<TrackerSummaryUi>,
    onCreateTracker: () -> Unit,
    onOpenTracker: (String) -> Unit,
    onQuickLog: (String) -> Unit,
    onPinnedChanged: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            SignalTopBar("Trackers", actions = {
                IconButton(onClick = onCreateTracker) { Icon(Icons.Rounded.Add, "Create tracker") }
            })
        },
    ) { padding ->
        if (trackers.isEmpty()) {
            SignalEmptyState(
                title = "No trackers yet",
                message = "Choose a template or build exactly what you want to notice.",
                actionLabel = "Create tracker",
                onAction = onCreateTracker,
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(trackers, key = { it.id }) { tracker ->
                    TrackerListCard(
                        tracker = tracker,
                        onOpen = { onOpenTracker(tracker.id) },
                        onQuickLog = { onQuickLog(tracker.id) },
                        onPinnedChanged = { onPinnedChanged(tracker.id, !tracker.pinned) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackerListCard(
    tracker: TrackerSummaryUi,
    onOpen: () -> Unit,
    onQuickLog: () -> Unit,
    onPinnedChanged: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            TrackerGlyph(tracker.glyph, tracker.accent, null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(tracker.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    listOf(tracker.lastValue, tracker.lastTracked).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onPinnedChanged) {
                Icon(
                    Icons.Rounded.PushPin,
                    contentDescription = if (tracker.pinned) "Remove from dashboard" else "Pin to dashboard",
                    tint = if (tracker.pinned) tracker.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            QuickAddButton("Log ${tracker.name}", tracker.accent, onQuickLog)
        }
    }
}

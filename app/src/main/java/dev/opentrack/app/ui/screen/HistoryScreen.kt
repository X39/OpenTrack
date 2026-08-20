package dev.opentrack.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.opentrack.app.ui.component.EntryRow
import dev.opentrack.app.ui.component.SignalEmptyState
import dev.opentrack.app.ui.component.SignalTopBar
import dev.opentrack.app.ui.model.HistoryUiState

@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onQueryChanged: (String) -> Unit,
    onTrackerFilter: () -> Unit,
    onDateFilter: () -> Unit,
    onOpenEntry: (String) -> Unit,
    onQuickLog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            SignalTopBar("History", actions = {
                IconButton(onClick = onQuickLog) { Icon(Icons.Rounded.Add, "Log an entry") }
            })
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChanged,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text("Search entries") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
            )
            androidx.compose.foundation.layout.Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AssistChip(
                    onClick = onTrackerFilter,
                    label = { Text(state.trackerFilterLabel) },
                    leadingIcon = { Icon(Icons.Rounded.FilterList, null, Modifier.padding(2.dp)) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surface),
                )
                AssistChip(
                    onClick = onDateFilter,
                    label = { Text(state.dateFilterLabel) },
                    leadingIcon = { Icon(Icons.Rounded.CalendarMonth, null, Modifier.padding(2.dp)) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surface),
                )
            }
            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.days.isEmpty()) {
                SignalEmptyState(
                    title = if (state.query.isBlank()) "Nothing logged yet" else "No matching entries",
                    message = if (state.query.isBlank()) "Your timeline will collect every signal you record." else "Try another search or clear the filters.",
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    state.days.forEach { day ->
                        item(key = "header-${day.label}") {
                            Text(
                                day.label,
                                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        items(day.entries, key = { it.id }) { entry -> EntryRow(entry, { onOpenEntry(entry.id) }) }
                    }
                }
            }
        }
    }
}

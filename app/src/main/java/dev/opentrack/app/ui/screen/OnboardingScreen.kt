package dev.opentrack.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.opentrack.app.ui.component.TrackerGlyph
import dev.opentrack.app.ui.model.OnboardingTemplateUi
import dev.opentrack.app.ui.model.OnboardingUiState

@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onTemplateToggled: (String) -> Unit,
    onMetricUnitsChanged: (Boolean) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    onBuildCustom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state.page) {
        0 -> WelcomePage(onContinue, modifier)
        1 -> TemplatePage(state, onTemplateToggled, onMetricUnitsChanged, onContinue, onBack, onBuildCustom, modifier)
        else -> ReadyPage(state.templates.count { it.selected }, onContinue, onBack, modifier)
    }
}

@Composable
private fun WelcomePage(onContinue: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Box(
                Modifier.size(72.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Rounded.ShowChart, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Notice the patterns\nthat shape your days.", style = MaterialTheme.typography.displaySmall)
                Text(
                    "Build simple trackers for anything, log in seconds, and turn everyday signals into useful insight.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(12.dp))
                    Column {
                        Text("Private by default", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Your data stays on this device unless you export or back it up.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().height(54.dp)) {
            Text("Get started")
            Spacer(Modifier.size(8.dp))
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null)
        }
    }
}

@Composable
private fun TemplatePage(
    state: OnboardingUiState,
    onTemplateToggled: (String) -> Unit,
    onMetricUnitsChanged: (Boolean) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    onBuildCustom: () -> Unit,
    modifier: Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                Text("Choose a starting point", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        },
        bottomBar = {
            Column(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onContinue,
                    enabled = state.templates.any { it.selected },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("Create selected trackers") }
                OutlinedButton(onClick = onBuildCustom, modifier = Modifier.fillMaxWidth()) { Text("Build a custom tracker") }
            }
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Column(Modifier.padding(bottom = 6.dp)) {
                    Text("Pick one or more. You can change every detail later.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Metric units", style = MaterialTheme.typography.titleMedium)
                            Text("Kilograms and kilometres", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = state.usesMetricUnits, onCheckedChange = onMetricUnitsChanged)
                    }
                }
            }
            items(state.templates, key = { it.id }) { template ->
                TemplateCard(template, onClick = { onTemplateToggled(template.id) })
            }
        }
    }
}

@Composable
private fun TemplateCard(template: OnboardingTemplateUi, onClick: () -> Unit) {
    Card(
        modifier = Modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (template.selected) template.accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface,
        ),
        border = if (template.selected) androidx.compose.foundation.BorderStroke(1.5.dp, template.accent) else null,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TrackerGlyph(template.glyph, template.accent, null)
                Checkbox(checked = template.selected, onCheckedChange = { onClick() })
            }
            Text(template.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(template.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ReadyPage(count: Int, onContinue: () -> Unit, onBack: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(36.dp),
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Your dashboard is ready.", style = MaterialTheme.typography.displaySmall)
            Text(
                "$count ${if (count == 1) "tracker" else "trackers"} will be waiting. Tap + on any card whenever you want to log.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(onClick = onContinue, Modifier.fillMaxWidth().height(54.dp)) { Text("Open my dashboard") }
    }
}

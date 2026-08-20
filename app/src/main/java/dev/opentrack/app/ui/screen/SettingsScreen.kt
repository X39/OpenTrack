package dev.opentrack.app.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.opentrack.app.ui.component.SectionHeader
import dev.opentrack.app.ui.component.SignalTopBar
import dev.opentrack.app.ui.model.BackupUiState
import dev.opentrack.app.ui.model.SettingsUiState

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onTheme: () -> Unit,
    onPrecision: () -> Unit,
    onOpenBackup: () -> Unit,
    onExportAll: () -> Unit,
    onImport: () -> Unit,
    onAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier, topBar = { SignalTopBar("Settings") }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            item { SectionHeader("Appearance", Modifier.padding(top = 8.dp, bottom = 4.dp)) }
            item { SettingsRow(Icons.Rounded.DarkMode, "Theme", state.themeLabel, onTheme) }
            item { SectionHeader("Tracking defaults", Modifier.padding(top = 22.dp, bottom = 4.dp)) }
            item { SettingsRow(Icons.Rounded.Schedule, "Default timestamp", state.defaultPrecision.label, onPrecision) }
            item { SectionHeader("Your data", Modifier.padding(top = 22.dp, bottom = 10.dp)) }
            item {
                BackupSummaryCard(state.backup, onOpenBackup, onExportAll, onImport)
            }
            item { SectionHeader("About", Modifier.padding(top = 22.dp, bottom = 4.dp)) }
            item { SettingsRow(Icons.Rounded.Info, "About OpenTrack", "Privacy and version", onAbout) }
        }
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier.size(40.dp).then(Modifier),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
}

@Composable
private fun BackupSummaryCard(
    backup: BackupUiState,
    onOpenBackup: () -> Unit,
    onExportAll: () -> Unit,
    onImport: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (backup.automaticBackupEnabled) Icons.Rounded.CloudDone else Icons.Rounded.Backup,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Backup and portability", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (backup.automaticBackupEnabled) "Android cloud backup allowed" else "Automatic backup unavailable",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (backup.exportInProgress || backup.importInProgress) LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f), enabled = !backup.importInProgress) {
                    Icon(Icons.Rounded.Upload, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Import")
                }
                Button(onClick = onExportAll, modifier = Modifier.weight(1f), enabled = !backup.exportInProgress) {
                    Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Export")
                }
            }
            Text(
                "Manage backup details",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(onClick = onOpenBackup)
                    .padding(vertical = 12.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
fun BackupScreen(
    state: BackupUiState,
    onBack: () -> Unit,
    onOpenSystemBackup: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier, topBar = { SignalTopBar("Backup & export", onBack = onBack) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.CloudDone, null, tint = MaterialTheme.colorScheme.primary)
                        Text("Automatic Android backup", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "When device backup is enabled, Android can securely restore OpenTrack after a reinstall or when you move phones.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(onClick = onOpenSystemBackup) { Text("Open system backup settings") }
                    }
                }
            }
            item {
                BackupActionCard(
                    icon = Icons.Rounded.Download,
                    title = "Export a backup",
                    description = "Save a portable ZIP containing tracker definitions, dashboard settings, and one CSV per tracker.",
                    button = "Choose destination",
                    loading = state.exportInProgress,
                    footer = state.lastExportLabel,
                    onClick = onExport,
                )
            }
            item {
                BackupActionCard(
                    icon = Icons.Rounded.Upload,
                    title = "Import a backup",
                    description = "Review backup counts and confirm before replacing current OpenTrack data.",
                    button = "Choose backup file",
                    loading = state.importInProgress,
                    onClick = onImport,
                )
            }
        }
    }
}

@Composable
private fun BackupActionCard(
    icon: ImageVector,
    title: String,
    description: String,
    button: String,
    loading: Boolean,
    footer: String? = null,
    onClick: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            Button(onClick = onClick, enabled = !loading) { Text(button) }
            if (footer != null) Text(footer, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

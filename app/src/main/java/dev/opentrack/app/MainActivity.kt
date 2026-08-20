package dev.opentrack.app

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import dev.opentrack.app.presentation.OpenTrackViewModel
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private val viewModel: OpenTrackViewModel by viewModels {
        val container = (application as OpenTrackApplication).container
        OpenTrackViewModel.Factory(container.repository, container.preferences, container.trackingActions)
    }

    private var pendingCsvTrackerId: String? = null

    private val createBackup = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        runCatching { contentResolver.openOutputStream(uri, "w") ?: error("Could not open the selected file") }
            .onSuccess(viewModel::exportBackup)
            .onFailure { viewModel.reportTransferError(it.message ?: "Backup export failed") }
    }

    private val openBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        runCatching { contentResolver.openInputStream(uri) ?: error("Could not open the selected backup") }
            .onSuccess(viewModel::prepareImport)
            .onFailure { viewModel.reportTransferError(it.message ?: "Backup import failed") }
    }

    private val createCsv = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        val trackerId = pendingCsvTrackerId.also { pendingCsvTrackerId = null }
            ?: return@registerForActivityResult
        if (uri == null) return@registerForActivityResult
        runCatching {
            contentResolver.openOutputStream(uri, "w")?.bufferedWriter(Charsets.UTF_8)
                ?: error("Could not open the selected file")
        }.onSuccess { writer ->
            viewModel.exportTrackerCsv(trackerId, writer)
        }.onFailure { viewModel.reportTransferError(it.message ?: "CSV export failed") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingCsvTrackerId = savedInstanceState?.getString(STATE_PENDING_CSV_TRACKER)
        enableEdgeToEdge()
        setContent {
            OpenTrackApp(
                viewModel = viewModel,
                onExportBackup = {
                    createBackup.launch("opentrack-backup-${LocalDate.now()}.zip")
                },
                onImportBackup = {
                    openBackup.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
                },
                onExportCsv = { trackerId ->
                    pendingCsvTrackerId = trackerId
                    val trackerName = viewModel.state.value.trackers.firstOrNull { it.id == trackerId }?.name
                        ?: "tracker"
                    val safeName = trackerName.lowercase()
                        .replace(Regex("[^a-z0-9._-]+"), "-")
                        .trim('-')
                        .ifBlank { "tracker" }
                    createCsv.launch("$safeName-${LocalDate.now()}.csv")
                },
                onOpenSystemBackup = {
                    runCatching { startActivity(Intent("android.settings.BACKUP_SETTINGS")) }
                        .onFailure { viewModel.reportTransferError("System backup settings are unavailable") }
                },
            )
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_PENDING_CSV_TRACKER, pendingCsvTrackerId)
        super.onSaveInstanceState(outState)
    }

    private companion object {
        const val STATE_PENDING_CSV_TRACKER = "pending_csv_tracker"
    }
}

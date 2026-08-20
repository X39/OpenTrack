package dev.opentrack.app.usecase

import com.google.common.truth.Truth.assertThat
import dev.opentrack.app.domain.model.BackupSnapshot
import dev.opentrack.app.domain.model.Dashboard
import dev.opentrack.app.domain.model.FieldKind
import dev.opentrack.app.domain.model.FieldValue
import dev.opentrack.app.domain.model.QuickAddConfig
import dev.opentrack.app.domain.model.QuickAddMode
import dev.opentrack.app.domain.model.TimestampPrecision
import dev.opentrack.app.domain.model.TrackerDefinition
import dev.opentrack.app.domain.model.TrackerEntry
import dev.opentrack.app.domain.model.TrackerField
import dev.opentrack.app.domain.model.TrackerKind
import dev.opentrack.app.domain.repository.TrackerRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class TrackingActionsTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-19T08:30:00Z"), ZoneOffset.UTC)

    @Test
    fun timestampQuickAddRecordsImmediately() = runTest {
        val tracker = TrackerDefinition(name = "Coffee", kind = TrackerKind.TIMESTAMP)
        val repository = FakeRepository(listOf(tracker))

        val result = TrackingActions(repository, clock).quickAdd(tracker.id)

        assertThat(result).isInstanceOf(QuickAddResult.Recorded::class.java)
        assertThat(repository.entries.value).hasSize(1)
        assertThat(repository.entries.value.single().values).isEmpty()
    }

    @Test
    fun counterQuickAddUsesConfiguredDelta() = runTest {
        val field = TrackerField(label = "Glasses", kind = FieldKind.COUNTER, counterQuickDelta = 1)
        val tracker = TrackerDefinition(
            name = "Water",
            kind = TrackerKind.COUNTER,
            fields = listOf(field),
            quickAdd = QuickAddConfig(QuickAddMode.AUTO),
        )
        val repository = FakeRepository(listOf(tracker))

        val result = TrackingActions(repository, clock).quickAdd(tracker.id)

        assertThat(result).isInstanceOf(QuickAddResult.Recorded::class.java)
        assertThat(repository.entries.value.single().values[field.id]).isEqualTo(FieldValue.Integer(1))
    }

    @Test
    fun valueTrackerRequestsInput() = runTest {
        val tracker = TrackerDefinition(
            name = "Weight",
            kind = TrackerKind.VALUE,
            fields = listOf(TrackerField(label = "Weight", kind = FieldKind.VALUE, unit = "kg")),
        )
        val repository = FakeRepository(listOf(tracker))

        val result = TrackingActions(repository, clock).quickAdd(tracker.id)

        assertThat(result).isInstanceOf(QuickAddResult.NeedsInput::class.java)
        assertThat(repository.entries.value).isEmpty()
    }
}

private class FakeRepository(initialTrackers: List<TrackerDefinition>) : TrackerRepository {
    private val trackers = MutableStateFlow(initialTrackers)
    val entries = MutableStateFlow<List<TrackerEntry>>(emptyList())
    private val dashboards = MutableStateFlow<List<Dashboard>>(emptyList())

    override fun observeTrackers(includeArchived: Boolean): Flow<List<TrackerDefinition>> = trackers
    override fun observeTracker(trackerId: String): Flow<TrackerDefinition?> =
        MutableStateFlow(trackers.value.firstOrNull { it.id == trackerId })
    override fun observeEntries(trackerId: String?): Flow<List<TrackerEntry>> = entries
    override fun observeEntry(entryId: String): Flow<TrackerEntry?> =
        MutableStateFlow(entries.value.firstOrNull { it.id == entryId })
    override fun observeDashboards(): Flow<List<Dashboard>> = dashboards
    override suspend fun getTracker(trackerId: String) = trackers.value.firstOrNull { it.id == trackerId }
    override suspend fun getEntry(entryId: String) = entries.value.firstOrNull { it.id == entryId }
    override suspend fun snapshot() = BackupSnapshot(trackers.value, entries.value, dashboards.value)
    override suspend fun saveTracker(definition: TrackerDefinition) {
        trackers.value = trackers.value.filterNot { it.id == definition.id } + definition
    }
    override suspend fun archiveTracker(trackerId: String, archived: Boolean) = Unit
    override suspend fun deleteTrackerPermanently(trackerId: String) = Unit
    override suspend fun saveEntry(entry: TrackerEntry) {
        entries.value = entries.value.filterNot { it.id == entry.id } + entry
    }
    override suspend fun deleteEntry(entryId: String) {
        entries.value = entries.value.filterNot { it.id == entryId }
    }
    override suspend fun saveDashboard(dashboard: Dashboard) {
        dashboards.value = dashboards.value.filterNot { it.id == dashboard.id } + dashboard
    }
    override suspend fun deleteDashboard(dashboardId: String) = Unit
    override suspend fun replaceAll(snapshot: BackupSnapshot) = Unit
}


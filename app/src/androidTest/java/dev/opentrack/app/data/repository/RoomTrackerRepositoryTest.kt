package dev.opentrack.app.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.opentrack.app.data.local.OpenTrackDatabase
import dev.opentrack.app.domain.model.Aggregation
import dev.opentrack.app.domain.model.AnalyticsMetric
import dev.opentrack.app.domain.model.BackupSnapshot
import dev.opentrack.app.domain.model.Dashboard
import dev.opentrack.app.domain.model.DashboardSeries
import dev.opentrack.app.domain.model.DashboardWidget
import dev.opentrack.app.domain.model.DashboardWidgetKind
import dev.opentrack.app.domain.model.FieldKind
import dev.opentrack.app.domain.model.FieldValue
import dev.opentrack.app.domain.model.RecordedAt
import dev.opentrack.app.domain.model.TrackerDefinition
import dev.opentrack.app.domain.model.TrackerEntry
import dev.opentrack.app.domain.model.TrackerField
import dev.opentrack.app.domain.model.TrackerKind
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomTrackerRepositoryTest {
    private lateinit var database: OpenTrackDatabase
    private lateinit var repository: RoomTrackerRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = OpenTrackDatabase.inMemory(context)
        repository = RoomTrackerRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun logicalSnapshotRoundTripsThroughNormalizedRoomTables() {
        runBlocking {
            val field = TrackerField(
                id = "weight-field",
                label = "Weight",
                kind = FieldKind.VALUE,
                unit = "kg",
            )
            val tracker = TrackerDefinition(
                id = "weight",
                name = "Weight",
                kind = TrackerKind.VALUE,
                fields = listOf(field),
            )
            val entry = TrackerEntry(
                id = "entry",
                trackerId = tracker.id,
                recordedAt = RecordedAt.Day(LocalDate.of(2026, 8, 19)),
                values = mapOf(field.id to FieldValue.Decimal(72.4)),
                note = "Morning",
            )
            val dashboard = Dashboard(
                id = "dashboard",
                widgets = listOf(
                    DashboardWidget(
                        id = "widget",
                        kind = DashboardWidgetKind.CHART,
                        series = listOf(
                            DashboardSeries(
                                id = "series",
                                trackerId = tracker.id,
                                fieldId = field.id,
                                metric = AnalyticsMetric.NUMERIC_VALUE,
                                aggregation = Aggregation.AVERAGE,
                            ),
                        ),
                    ),
                ),
            )

            repository.saveTracker(tracker)
            repository.saveEntry(entry)
            repository.saveDashboard(dashboard)
            val snapshot = repository.snapshot()

            repository.replaceAll(BackupSnapshot(emptyList(), emptyList(), emptyList()))
            assertThat(repository.snapshot().trackers).isEmpty()
            repository.replaceAll(snapshot)
            val restored = repository.snapshot()

            assertThat(restored.trackers).containsExactlyElementsIn(snapshot.trackers)
            assertThat(restored.entries).containsExactlyElementsIn(snapshot.entries)
            assertThat(restored.dashboards).containsExactlyElementsIn(snapshot.dashboards)
        }
    }

    @Test
    fun existingUnitCannotRelabelHistoricalValues() = runBlocking {
        val field = TrackerField(id = "distance-field", label = "Distance", kind = FieldKind.VALUE, unit = "km")
        val tracker = TrackerDefinition(
            id = "distance",
            name = "Distance",
            kind = TrackerKind.VALUE,
            fields = listOf(field),
        )
        repository.saveTracker(tracker)
        repository.saveEntry(
            TrackerEntry(
                trackerId = tracker.id,
                recordedAt = RecordedAt.Day(LocalDate.of(2026, 8, 19)),
                values = mapOf(field.id to FieldValue.Decimal(5.0)),
            ),
        )

        val error = runCatching {
            repository.saveTracker(tracker.copy(fields = listOf(field.copy(unit = "mi"))))
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(error).hasMessageThat().contains("unit")
    }
}

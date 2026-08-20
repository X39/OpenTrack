package dev.opentrack.app.domain.analytics

import com.google.common.truth.Truth.assertThat
import dev.opentrack.app.domain.model.FieldValue
import dev.opentrack.app.domain.model.RecordedAt
import dev.opentrack.app.domain.model.TrackerEntry
import dev.opentrack.app.domain.template.StarterTemplates
import java.time.LocalDate
import org.junit.Test

class TrackerAnalyticsTest {
    @Test fun `numeric summary and counter running total are deterministic`() {
        val definition = StarterTemplates.instantiate(StarterTemplates.WATER)
        val field = definition.fields.single()
        val entries = listOf(1L, 2L, -1L).mapIndexed { index, delta ->
            TrackerEntry(
                id = "entry-$index",
                trackerId = definition.id,
                recordedAt = RecordedAt.Day(LocalDate.of(2026, 8, 17 + index)),
                values = mapOf(field.id to FieldValue.Integer(delta)),
            )
        }

        val points = TrackerAnalytics.numericSeries(definition, entries)
        val summary = TrackerAnalytics.numericSummary(points)!!
        assertThat(summary.latest).isEqualTo(-1.0)
        assertThat(summary.average).isWithin(0.0001).of(2.0 / 3.0)
        assertThat(TrackerAnalytics.counterRunningTotal(points).map { it.value })
            .containsExactly(1.0, 3.0, 2.0).inOrder()
    }

    @Test fun `count by day permits duplicate timestamps`() {
        val date = LocalDate.of(2026, 8, 19)
        val entries = List(3) { TrackerEntry(trackerId = "tracker", recordedAt = RecordedAt.Day(date)) }
        assertThat(TrackerAnalytics.countByDay(entries)[date]).isEqualTo(3)
    }
}


package dev.opentrack.app.data.export

import com.google.common.truth.Truth.assertThat
import dev.opentrack.app.domain.model.FieldValue
import dev.opentrack.app.domain.model.RecordedAt
import dev.opentrack.app.domain.model.TrackerEntry
import dev.opentrack.app.domain.template.StarterTemplates
import java.io.StringReader
import java.time.LocalDate
import org.junit.Test

class TrackerCsvExporterTest {
    @Test fun `csv escapes notes and preserves enum payloads`() {
        val definition = StarterTemplates.instantiate(StarterTemplates.WORKOUT_SET)
        val field = definition.fields.single()
        val pushUps = field.options.first { it.label == "Push-ups" }
        val entry = TrackerEntry(
            id = "entry-id",
            trackerId = definition.id,
            recordedAt = RecordedAt.Day(LocalDate.of(2026, 8, 19)),
            values = mapOf(field.id to FieldValue.Choice(pushUps.id, FieldValue.Integer(20))),
            note = "Hard, but good\nset",
        )

        val csv = TrackerCsvExporter.export(definition, listOf(entry))
        val rows = Csv.parse(StringReader(csv))

        assertThat(rows).hasSize(2)
        assertThat(rows[1]).contains("Push-ups")
        assertThat(rows[1]).contains("20")
        assertThat(rows[1].last()).isEqualTo("Hard, but good\nset")
    }

    @Test fun `user text is spreadsheet safe while negative numbers stay numeric`() {
        val base = StarterTemplates.instantiate(StarterTemplates.WEIGHT)
        val field = base.fields.single().copy(label = "=HYPERLINK(\"bad\")")
        val definition = base.copy(fields = listOf(field))
        val entry = TrackerEntry(
            trackerId = definition.id,
            recordedAt = RecordedAt.Day(LocalDate.of(2026, 8, 19)),
            values = mapOf(field.id to FieldValue.Decimal(-2.5)),
            note = "@SUM(A1:A2)",
        )

        val rows = Csv.parse(StringReader(TrackerCsvExporter.export(definition, listOf(entry))))

        assertThat(rows[0]).contains("'=HYPERLINK(\"bad\") (kg)")
        assertThat(rows[1]).contains("-2.5")
        assertThat(rows[1].last()).isEqualTo("'@SUM(A1:A2)")
    }
}

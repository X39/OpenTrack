package dev.opentrack.app.domain.model

import com.google.common.truth.Truth.assertThat
import dev.opentrack.app.domain.template.StarterTemplates
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertThrows
import org.junit.Test

class DomainValidatorTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-19T10:15:30Z"), ZoneOffset.UTC)

    @Test fun `all starter templates are valid`() {
        StarterTemplates.available.forEach { descriptor ->
            DomainValidator.validate(StarterTemplates.instantiate(descriptor.key, clock = clock))
        }
    }

    @Test fun `enum payload must match selected option schema`() {
        val definition = StarterTemplates.instantiate(StarterTemplates.WORKOUT_SET, clock = clock)
        val field = definition.fields.single()
        val pushUps = field.options.first { it.label == "Push-ups" }
        val invalid = TrackerEntry(
            trackerId = definition.id,
            recordedAt = RecordedAt.now(definition.timestampPrecision, clock),
            values = mapOf(field.id to FieldValue.Choice(pushUps.id, FieldValue.Decimal(12.0))),
        )

        val error = assertThrows(DomainValidationException::class.java) {
            DomainValidator.validateEntry(definition, invalid)
        }
        assertThat(error).hasMessageThat().contains("integer payload")
    }

    @Test fun `group requires every required field`() {
        val definition = StarterTemplates.instantiate(StarterTemplates.DAILY_CHECK_IN, clock = clock)
        val entry = TrackerEntry(
            trackerId = definition.id,
            recordedAt = RecordedAt.now(definition.timestampPrecision, clock),
        )

        assertThrows(DomainValidationException::class.java) {
            DomainValidator.validateEntry(definition, entry)
        }
    }

    @Test fun `snapshot accepts an entry recorded before a required field existed`() {
        val oldField = TrackerField(id = "old", label = "Old", kind = FieldKind.VALUE)
        val newField = TrackerField(id = "new", label = "New", kind = FieldKind.VALUE)
        val definition = TrackerDefinition(
            id = "tracker",
            name = "Evolving tracker",
            kind = TrackerKind.GROUP,
            fields = listOf(oldField, newField),
        )
        val historical = TrackerEntry(
            trackerId = definition.id,
            recordedAt = RecordedAt.Day(java.time.LocalDate.of(2026, 8, 1)),
            values = mapOf(oldField.id to FieldValue.Decimal(1.0)),
        )

        DomainValidator.validate(BackupSnapshot(listOf(definition), listOf(historical), emptyList()))
        assertThrows(DomainValidationException::class.java) {
            DomainValidator.validateEntry(definition, historical)
        }
    }
}

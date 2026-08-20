package dev.opentrack.app.presentation

import com.google.common.truth.Truth.assertThat
import dev.opentrack.app.domain.model.ChoiceOption
import dev.opentrack.app.domain.model.EnumPayloadKind
import dev.opentrack.app.domain.model.FieldKind
import dev.opentrack.app.domain.model.FieldValue
import dev.opentrack.app.domain.model.TimestampPrecision
import dev.opentrack.app.domain.model.TrackerDefinition
import dev.opentrack.app.domain.model.TrackerField
import dev.opentrack.app.domain.model.TrackerKind
import dev.opentrack.app.domain.template.StarterTemplates
import dev.opentrack.app.ui.model.BuilderPayloadKindUi
import dev.opentrack.app.ui.model.QuickLogAction
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Test

class QuickLogLogicTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-19T08:30:00Z"), ZoneOffset.UTC)

    @Test
    fun `enum payload input records its type and unit`() {
        val tracker = StarterTemplates.instantiate(StarterTemplates.WORKOUT_SET, clock = clock)
        val field = tracker.fields.single()
        val bench = field.options.first { it.label == "Bench press" }

        val initial = QuickLogLogic.initial(tracker, clock = clock)
        val optionUi = initial.ui.options.first { it.id == bench.id }
        assertThat(optionUi.payloadKind).isEqualTo(BuilderPayloadKindUi.NUMBER)
        assertThat(optionUi.payloadUnit).isEqualTo("kg")
        assertThat(initial.ui.canSave).isFalse()

        val selected = QuickLogLogic.reduce(initial, QuickLogAction.OptionSelected(bench.id))
        assertThat(selected.ui.canSave).isFalse()
        val completed = QuickLogLogic.reduce(selected, QuickLogAction.ValueChanged("82.5"))

        assertThat(completed.ui.canSave).isTrue()
        assertThat(QuickLogLogic.values(completed)[field.id])
            .isEqualTo(FieldValue.Choice(bench.id, FieldValue.Decimal(82.5)))
    }

    @Test
    fun `mixed group validates required timestamp and enum payload`() {
        val timestamp = TrackerField(
            id = "when",
            label = "Started",
            kind = FieldKind.TIMESTAMP,
            timestampPrecision = TimestampPrecision.DATE_TIME,
        )
        val option = ChoiceOption(
            id = "push-ups",
            label = "Push-ups",
            payloadKind = EnumPayloadKind.INTEGER,
            payloadLabel = "Repetitions",
            payloadUnit = "reps",
        )
        val exercise = TrackerField(
            id = "exercise",
            label = "Exercise",
            kind = FieldKind.ENUM,
            options = listOf(option),
        )
        val tracker = TrackerDefinition(
            id = "workout",
            name = "Workout",
            kind = TrackerKind.GROUP,
            fields = listOf(timestamp, exercise),
        )

        val initial = QuickLogLogic.initial(tracker, clock = clock)
        assertThat(initial.fieldTimestamps).containsKey(timestamp.id)
        assertThat(initial.ui.canSave).isFalse()

        val selected = QuickLogLogic.reduce(
            initial,
            QuickLogAction.FieldOptionSelected(exercise.id, option.id),
        )
        val completed = QuickLogLogic.reduce(
            selected,
            QuickLogAction.FieldChanged(exercise.id, "20"),
        )
        val values = QuickLogLogic.values(completed)

        assertThat(completed.ui.canSave).isTrue()
        assertThat(values[timestamp.id]).isInstanceOf(FieldValue.Timestamp::class.java)
        assertThat(values[exercise.id])
            .isEqualTo(FieldValue.Choice(option.id, FieldValue.Integer(20)))
    }

    @Test
    fun `duration numeric input respects configured hours`() {
        val tracker = StarterTemplates.instantiate(StarterTemplates.SLEEP, clock = clock)
        val draft = QuickLogLogic.reduce(
            QuickLogLogic.initial(tracker, clock = clock),
            QuickLogAction.ValueChanged("8"),
        )

        assertThat(draft.ui.canSave).isTrue()
        assertThat(QuickLogLogic.values(draft)[tracker.fields.single().id])
            .isEqualTo(FieldValue.DurationValue(Duration.ofHours(8)))
    }

    @Test
    fun `non finite numeric input never enables save`() {
        val tracker = StarterTemplates.instantiate(StarterTemplates.WEIGHT, clock = clock)
        val draft = QuickLogLogic.reduce(
            QuickLogLogic.initial(tracker, clock = clock),
            QuickLogAction.ValueChanged("NaN"),
        )

        assertThat(draft.ui.canSave).isFalse()
    }

    @Test
    fun `decimal comma from localized keyboard is accepted`() {
        val tracker = StarterTemplates.instantiate(StarterTemplates.WEIGHT, clock = clock)
        val draft = QuickLogLogic.reduce(
            QuickLogLogic.initial(tracker, clock = clock),
            QuickLogAction.ValueChanged("72,5"),
        )

        assertThat(draft.ui.canSave).isTrue()
        assertThat(QuickLogLogic.values(draft)[tracker.fields.single().id])
            .isEqualTo(FieldValue.Decimal(72.5))
    }
}

package dev.opentrack.app.presentation

import com.google.common.truth.Truth.assertThat
import dev.opentrack.app.domain.model.FieldKind
import dev.opentrack.app.domain.model.FieldValue
import dev.opentrack.app.domain.model.QuickAddMode
import dev.opentrack.app.domain.model.TrackerDefinition
import dev.opentrack.app.domain.model.TrackerField
import dev.opentrack.app.domain.model.TrackerKind
import dev.opentrack.app.domain.template.StarterTemplates
import dev.opentrack.app.ui.model.QuickLogModeUi
import dev.opentrack.app.ui.model.TrackerBuilderAction
import dev.opentrack.app.ui.model.TrackerKindUi
import java.time.Duration
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Test

class TrackerBuilderLogicTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-20T00:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `counter builder preserves a negative quick delta`() {
        var state = TrackerBuilderLogic.initial()
        state = TrackerBuilderLogic.reduce(state, TrackerBuilderAction.NameChanged("Inventory"))
        state = TrackerBuilderLogic.reduce(state, TrackerBuilderAction.KindSelected(TrackerKindUi.COUNTER))
        state = TrackerBuilderLogic.reduce(state, TrackerBuilderAction.CounterDeltaChanged(-2))

        val definition = TrackerBuilderLogic.build(state)

        assertThat(definition.fields.single().counterQuickDelta).isEqualTo(-2)
    }

    @Test
    fun `existing tracker and field types are locked`() {
        val field = TrackerField(id = "value", label = "Value", kind = FieldKind.VALUE)
        val existing = TrackerDefinition(
            id = "tracker",
            name = "Weight",
            kind = TrackerKind.VALUE,
            fields = listOf(field),
        )
        var state = TrackerBuilderLogic.initial(existing)

        state = TrackerBuilderLogic.reduce(state, TrackerBuilderAction.KindSelected(TrackerKindUi.COUNTER))

        assertThat(state.kind).isEqualTo(TrackerKindUi.NUMBER)
    }

    @Test
    fun `duration preset uses the tracker's configured unit`() {
        var state = TrackerBuilderLogic.initial()
        state = TrackerBuilderLogic.reduce(state, TrackerBuilderAction.NameChanged("Sleep"))
        state = TrackerBuilderLogic.reduce(state, TrackerBuilderAction.KindSelected(TrackerKindUi.DURATION))
        state = TrackerBuilderLogic.reduce(state, TrackerBuilderAction.UnitChanged("hours"))
        state = TrackerBuilderLogic.reduce(state, TrackerBuilderAction.QuickModeSelected(QuickLogModeUi.PRESET))
        state = TrackerBuilderLogic.reduce(state, TrackerBuilderAction.QuickPresetChanged("8"))

        val definition = TrackerBuilderLogic.build(state)

        assertThat(definition.quickAdd.mode).isEqualTo(QuickAddMode.DEFAULT_PRESET)
        assertThat(definition.presets.single().values.values.single())
            .isEqualTo(FieldValue.DurationValue(Duration.ofHours(8)))
    }

    @Test
    fun `editing choice preset keeps its human option label`() {
        val existing = StarterTemplates.instantiate(StarterTemplates.ENERGY)
        val field = existing.fields.single()
        val normal = field.options.first { it.label == "Normal" }
        val preset = dev.opentrack.app.domain.model.QuickPreset(
            id = "preset",
            label = "Normal",
            values = mapOf(field.id to FieldValue.Choice(normal.id)),
        )
        val configured = existing.copy(
            presets = listOf(preset),
            quickAdd = dev.opentrack.app.domain.model.QuickAddConfig(
                QuickAddMode.DEFAULT_PRESET,
                preset.id,
            ),
        )

        val state = TrackerBuilderLogic.initial(configured)
        val rebuilt = TrackerBuilderLogic.build(state, configured)

        assertThat(state.quickPreset).isEqualTo("Normal")
        assertThat(rebuilt.presets.single().values[field.id]).isEqualTo(FieldValue.Choice(normal.id))
    }

    @Test
    fun `editing preserves existing numeric schema`() {
        val existing = StarterTemplates.instantiate(StarterTemplates.WEIGHT)
        var state = TrackerBuilderLogic.initial(existing)
        state = TrackerBuilderLogic.reduce(state, TrackerBuilderAction.UnitChanged("lb"))

        val rebuilt = TrackerBuilderLogic.build(state, existing)

        assertThat(rebuilt.fields.single().unit).isEqualTo("kg")
        assertThat(rebuilt.fields.single().decimalPlaces).isEqualTo(1)
    }

    @Test
    fun `every common template opens as a new editable tracker`() {
        StarterTemplates.available.forEach { template ->
            val state = TrackerBuilderLogic.fromTemplate(template.key, clock = clock)

            assertThat(state.editingTrackerId).isNull()
            assertThat(state.selectedTemplateId).isEqualTo(template.key)
            assertThat(state.canContinue).isTrue()
            assertThat(state.fields.all { !it.structureLocked && !it.requiredLocked }).isTrue()
            assertThat(state.options.all { !it.payloadKindLocked }).isTrue()
            TrackerBuilderLogic.build(state)
        }
    }

    @Test
    fun `workout template follows the selected unit system`() {
        val metric = TrackerBuilderLogic.build(
            TrackerBuilderLogic.fromTemplate(StarterTemplates.WORKOUT_SET, metric = true, clock = clock),
        )
        val imperial = TrackerBuilderLogic.build(
            TrackerBuilderLogic.fromTemplate(StarterTemplates.WORKOUT_SET, metric = false, clock = clock),
        )

        assertThat(metric.fields.single().options.first().payloadUnit).isEqualTo("kg")
        assertThat(imperial.fields.single().options.first().payloadUnit).isEqualTo("lb")
    }
}

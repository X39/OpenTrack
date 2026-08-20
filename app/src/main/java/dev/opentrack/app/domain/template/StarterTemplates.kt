package dev.opentrack.app.domain.template

import dev.opentrack.app.domain.model.ChoiceOption
import dev.opentrack.app.domain.model.EnumPayloadKind
import dev.opentrack.app.domain.model.FieldKind
import dev.opentrack.app.domain.model.TrackerDefinition
import dev.opentrack.app.domain.model.TrackerField
import dev.opentrack.app.domain.model.TrackerKind
import java.time.Clock

data class StarterTemplateDescriptor(
    val key: String,
    val title: String,
    val description: String,
    val defaultIconKey: String,
)

object StarterTemplates {
    const val MOMENT = "moment"
    const val WEIGHT = "weight"
    const val ENERGY = "energy"
    const val WORKOUT_SET = "workout_set"
    const val WATER = "water"
    const val SLEEP = "sleep"
    const val DAILY_CHECK_IN = "daily_check_in"

    val available: List<StarterTemplateDescriptor> = listOf(
        StarterTemplateDescriptor(MOMENT, "One-tap occurrence", "Record that something happened.", "bolt"),
        StarterTemplateDescriptor(WEIGHT, "Weight", "Track a numeric body-weight trend.", "scale"),
        StarterTemplateDescriptor(ENERGY, "Energy", "Rate energy from low to high.", "energy"),
        StarterTemplateDescriptor(WORKOUT_SET, "Workout set", "Choose an exercise and record weight or reps.", "fitness"),
        StarterTemplateDescriptor(WATER, "Water", "Add one serving with every tap.", "water"),
        StarterTemplateDescriptor(SLEEP, "Sleep", "Record sleep duration.", "sleep"),
        StarterTemplateDescriptor(DAILY_CHECK_IN, "Daily check-in", "Capture energy, exercise, and a daily win together.", "check"),
    )

    fun instantiate(
        key: String,
        name: String? = null,
        unit: String? = null,
        clock: Clock = Clock.systemUTC(),
    ): TrackerDefinition {
        val now = clock.instant()
        return when (key) {
            MOMENT -> TrackerDefinition(
                name = name ?: "Moment",
                kind = TrackerKind.TIMESTAMP,
                iconKey = "bolt",
                createdAt = now,
                updatedAt = now,
            )
            WEIGHT -> TrackerDefinition(
                name = name ?: "Weight",
                kind = TrackerKind.VALUE,
                iconKey = "scale",
                fields = listOf(TrackerField(label = "Weight", kind = FieldKind.VALUE, unit = unit ?: "kg", decimalPlaces = 1)),
                createdAt = now,
                updatedAt = now,
            )
            ENERGY -> TrackerDefinition(
                name = name ?: "Energy",
                kind = TrackerKind.RADIO,
                iconKey = "energy",
                fields = listOf(ratingField("Energy", listOf("Low", "Normal", "High"))),
                createdAt = now,
                updatedAt = now,
            )
            WORKOUT_SET -> workout(name ?: "Workout set", unit ?: "kg", now)
            WATER -> TrackerDefinition(
                name = name ?: "Water",
                kind = TrackerKind.COUNTER,
                iconKey = "water",
                fields = listOf(TrackerField(label = "Serving", kind = FieldKind.COUNTER, unit = unit ?: "glass", counterQuickDelta = 1)),
                createdAt = now,
                updatedAt = now,
            )
            SLEEP -> TrackerDefinition(
                name = name ?: "Sleep",
                kind = TrackerKind.DURATION,
                iconKey = "sleep",
                fields = listOf(TrackerField(label = "Duration", kind = FieldKind.DURATION, unit = "hours")),
                createdAt = now,
                updatedAt = now,
            )
            DAILY_CHECK_IN -> dailyCheckIn(name ?: "Daily check-in", now)
            else -> throw IllegalArgumentException("Unknown starter template: $key")
        }
    }

    private fun workout(name: String, weightUnit: String, now: java.time.Instant): TrackerDefinition {
        val field = TrackerField(
            label = "Exercise",
            kind = FieldKind.ENUM,
            options = listOf(
                ChoiceOption(label = "Bench press", order = 0, payloadKind = EnumPayloadKind.DECIMAL, payloadLabel = "Weight", payloadUnit = weightUnit),
                ChoiceOption(label = "Butterfly", order = 1, payloadKind = EnumPayloadKind.DECIMAL, payloadLabel = "Weight", payloadUnit = weightUnit),
                ChoiceOption(label = "Push-ups", order = 2, payloadKind = EnumPayloadKind.INTEGER, payloadLabel = "Repetitions", payloadUnit = "reps"),
            ),
        )
        return TrackerDefinition(name = name, kind = TrackerKind.ENUM, iconKey = "fitness", fields = listOf(field), createdAt = now, updatedAt = now)
    }

    private fun dailyCheckIn(name: String, now: java.time.Instant): TrackerDefinition = TrackerDefinition(
        name = name,
        kind = TrackerKind.GROUP,
        iconKey = "check",
        fields = listOf(
            ratingField("Energy", listOf("Low", "Normal", "High"), order = 0),
            TrackerField(label = "Exercised", kind = FieldKind.BOOLEAN, required = false, order = 1),
            TrackerField(label = "Focus time", kind = FieldKind.DURATION, required = false, order = 2),
        ),
        createdAt = now,
        updatedAt = now,
    )

    private fun ratingField(label: String, labels: List<String>, order: Int = 0) = TrackerField(
        label = label,
        kind = FieldKind.RADIO,
        order = order,
        options = labels.mapIndexed { index, optionLabel ->
            ChoiceOption(label = optionLabel, order = index, radioScore = (index + 1).toDouble())
        },
    )
}

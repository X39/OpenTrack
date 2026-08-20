package dev.opentrack.app.domain.analytics

import dev.opentrack.app.domain.model.EnumPayloadKind
import dev.opentrack.app.domain.model.FieldKind
import dev.opentrack.app.domain.model.FieldValue
import dev.opentrack.app.domain.model.RecordedAt
import dev.opentrack.app.domain.model.TrackerDefinition
import dev.opentrack.app.domain.model.TrackerEntry
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.math.sqrt

data class NumericPoint(
    val entryId: String,
    val recordedAt: RecordedAt,
    val value: Double,
)

data class NumericSummary(
    val count: Int,
    val latest: Double,
    val previous: Double?,
    val delta: Double?,
    val minimum: Double,
    val maximum: Double,
    val average: Double,
)

data class BooleanSummary(
    val total: Int,
    val trueCount: Int,
    val falseCount: Int,
    val trueRate: Double,
)

data class IntervalSummary(
    val intervalCount: Int,
    val minimum: Duration,
    val maximum: Duration,
    val average: Duration,
)

data class ChoiceCount(
    val optionId: String,
    val label: String,
    val count: Int,
    val proportion: Double,
)

object TrackerAnalytics {
    fun countByDay(entries: List<TrackerEntry>): Map<LocalDate, Int> = entries
        .groupingBy { it.recordedAt.localDate }
        .eachCount()
        .toSortedMap()

    fun numericSeries(
        definition: TrackerDefinition,
        entries: List<TrackerEntry>,
        fieldId: String? = definition.fields.singleOrNull()?.id,
        optionId: String? = null,
    ): List<NumericPoint> {
        val field = definition.fields.firstOrNull { it.id == fieldId } ?: return emptyList()
        return entries.asSequence()
            .filter { it.trackerId == definition.id }
            .mapNotNull { entry ->
                val stored = entry.values[field.id] ?: return@mapNotNull null
                numericValue(field.kind, field.options.associateBy { it.id }, stored, optionId)
                    ?.let { NumericPoint(entry.id, entry.recordedAt, it) }
            }
            .sortedWith(compareBy({ it.recordedAt.localDate }, { sortInstant(it.recordedAt) }))
            .toList()
    }

    fun numericSummary(points: List<NumericPoint>): NumericSummary? {
        if (points.isEmpty()) return null
        val latest = points.last().value
        val previous = points.getOrNull(points.lastIndex - 1)?.value
        return NumericSummary(
            count = points.size,
            latest = latest,
            previous = previous,
            delta = previous?.let { latest - it },
            minimum = points.minOf { it.value },
            maximum = points.maxOf { it.value },
            average = points.sumOf { it.value } / points.size,
        )
    }

    fun counterRunningTotal(points: List<NumericPoint>, initialValue: Long = 0): List<NumericPoint> {
        var total = initialValue.toDouble()
        return points.map { point ->
            total += point.value
            point.copy(value = total)
        }
    }

    fun booleanSummary(entries: List<TrackerEntry>, fieldId: String): BooleanSummary? {
        val values = entries.mapNotNull { (it.values[fieldId] as? FieldValue.BooleanValue)?.value }
        if (values.isEmpty()) return null
        val trueCount = values.count { it }
        return BooleanSummary(values.size, trueCount, values.size - trueCount, trueCount.toDouble() / values.size)
    }

    fun choiceDistribution(
        definition: TrackerDefinition,
        entries: List<TrackerEntry>,
        fieldId: String? = definition.fields.singleOrNull()?.id,
    ): List<ChoiceCount> {
        val field = definition.fields.firstOrNull { it.id == fieldId } ?: return emptyList()
        val counts = entries.asSequence()
            .filter { it.trackerId == definition.id }
            .mapNotNull { (it.values[field.id] as? FieldValue.Choice)?.optionId }
            .groupingBy { it }
            .eachCount()
        val total = counts.values.sum()
        return field.options.sortedBy { it.order }.map { option ->
            val count = counts[option.id] ?: 0
            ChoiceCount(option.id, option.label, count, if (total == 0) 0.0 else count.toDouble() / total)
        }
    }

    fun intervalSummary(entries: List<TrackerEntry>): IntervalSummary? {
        val instants = entries.map(::sortInstant).sorted()
        if (instants.size < 2) return null
        val intervals = instants.zipWithNext { first, second -> Duration.between(first, second) }
        val averageMillis = intervals.map(Duration::toMillis).average().toLong()
        return IntervalSummary(
            intervalCount = intervals.size,
            minimum = intervals.minOrNull()!!,
            maximum = intervals.maxOrNull()!!,
            average = Duration.ofMillis(averageMillis),
        )
    }

    fun standardDeviation(points: List<NumericPoint>): Double? {
        if (points.isEmpty()) return null
        val mean = points.sumOf { it.value } / points.size
        return sqrt(points.sumOf { (it.value - mean) * (it.value - mean) } / points.size)
    }

    private fun numericValue(
        kind: FieldKind,
        options: Map<String, dev.opentrack.app.domain.model.ChoiceOption>,
        value: FieldValue,
        optionFilter: String?,
    ): Double? = when (kind) {
        FieldKind.VALUE -> (value as? FieldValue.Decimal)?.value
        FieldKind.COUNTER -> (value as? FieldValue.Integer)?.value?.toDouble()
        FieldKind.DURATION -> (value as? FieldValue.DurationValue)?.value?.toMillis()?.div(1000.0)
        FieldKind.BOOLEAN -> (value as? FieldValue.BooleanValue)?.let { if (it.value) 1.0 else 0.0 }
        FieldKind.RADIO -> (value as? FieldValue.Choice)?.let { options[it.optionId]?.radioScore }
        FieldKind.ENUM -> (value as? FieldValue.Choice)?.let { choice ->
            if (optionFilter != null && choice.optionId != optionFilter) return@let null
            when (options[choice.optionId]?.payloadKind) {
                EnumPayloadKind.DECIMAL -> (choice.payload as? FieldValue.Decimal)?.value
                EnumPayloadKind.INTEGER -> (choice.payload as? FieldValue.Integer)?.value?.toDouble()
                EnumPayloadKind.DURATION -> (choice.payload as? FieldValue.DurationValue)?.value?.toMillis()?.div(1000.0)
                EnumPayloadKind.NONE, EnumPayloadKind.TEXT, null -> null
            }
        }
        FieldKind.TIMESTAMP -> null
    }

    private fun sortInstant(entry: TrackerEntry): Instant = sortInstant(entry.recordedAt)

    private fun sortInstant(recordedAt: RecordedAt): Instant = when (recordedAt) {
        is RecordedAt.Day -> recordedAt.localDate.atStartOfDay(java.time.ZoneOffset.UTC).toInstant()
        is RecordedAt.DateTime -> recordedAt.instant
    }
}

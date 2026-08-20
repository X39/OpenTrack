package dev.opentrack.app.presentation

import androidx.compose.ui.graphics.Color
import dev.opentrack.app.domain.model.FieldKind
import dev.opentrack.app.domain.model.FieldValue
import dev.opentrack.app.domain.model.RecordedAt
import dev.opentrack.app.domain.model.TrackerDefinition
import dev.opentrack.app.domain.model.TrackerEntry
import dev.opentrack.app.domain.model.TrackerKind
import dev.opentrack.app.ui.model.TrackerGlyphUi
import dev.opentrack.app.ui.model.TrackerKindUi
import dev.opentrack.app.ui.theme.SignalPalette
import java.text.DecimalFormat
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.LocalTime
import java.util.Locale

internal fun TrackerKind.toUi(): TrackerKindUi = when (this) {
    TrackerKind.TIMESTAMP -> TrackerKindUi.MOMENT
    TrackerKind.VALUE -> TrackerKindUi.NUMBER
    TrackerKind.ENUM -> TrackerKindUi.CHOICE
    TrackerKind.RADIO -> TrackerKindUi.RATING
    TrackerKind.GROUP -> TrackerKindUi.GROUP
    TrackerKind.BOOLEAN -> TrackerKindUi.BOOLEAN
    TrackerKind.COUNTER -> TrackerKindUi.COUNTER
    TrackerKind.DURATION -> TrackerKindUi.DURATION
}

internal fun TrackerKindUi.toDomain(): TrackerKind = when (this) {
    TrackerKindUi.MOMENT -> TrackerKind.TIMESTAMP
    TrackerKindUi.NUMBER -> TrackerKind.VALUE
    TrackerKindUi.CHOICE -> TrackerKind.ENUM
    TrackerKindUi.RATING -> TrackerKind.RADIO
    TrackerKindUi.GROUP -> TrackerKind.GROUP
    TrackerKindUi.BOOLEAN -> TrackerKind.BOOLEAN
    TrackerKindUi.COUNTER -> TrackerKind.COUNTER
    TrackerKindUi.DURATION -> TrackerKind.DURATION
}

internal fun TrackerDefinition.glyphUi(): TrackerGlyphUi = iconKey
    ?.uppercase(Locale.ROOT)
    ?.let { runCatching { TrackerGlyphUi.valueOf(it) }.getOrNull() }
    ?: when (kind) {
        TrackerKind.TIMESTAMP -> TrackerGlyphUi.PULSE
        TrackerKind.VALUE -> TrackerGlyphUi.SCALE
        TrackerKind.ENUM -> TrackerGlyphUi.FITNESS
        TrackerKind.RADIO -> TrackerGlyphUi.MOOD
        TrackerKind.GROUP -> TrackerGlyphUi.PULSE
        TrackerKind.BOOLEAN -> TrackerGlyphUi.CHECK
        TrackerKind.COUNTER -> TrackerGlyphUi.COUNTER
        TrackerKind.DURATION -> TrackerGlyphUi.TIMER
    }

private val fallbackColors = listOf(
    SignalPalette.Moss,
    SignalPalette.Sky,
    SignalPalette.Coral,
    SignalPalette.Lilac,
    SignalPalette.Rose,
    SignalPalette.Sun,
)

internal fun TrackerDefinition.accentUi(): Color = colorArgb
    ?.let { Color(it.toULong()) }
    ?: fallbackColors[Math.floorMod(id.hashCode(), fallbackColors.size)]

internal fun RecordedAt.date(): LocalDate = localDate

internal fun RecordedAt.dayLabel(today: LocalDate = LocalDate.now()): String = when (localDate) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> localDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
}

internal fun RecordedAt.timeLabel(): String = when (this) {
    is RecordedAt.Day -> "All day"
    is RecordedAt.DateTime -> instant.atZone(recordedOffset).format(DateTimeFormatter.ofPattern("HH:mm"))
}

internal fun RecordedAt.fullLabel(today: LocalDate = LocalDate.now()): String =
    if (this is RecordedAt.Day) dayLabel(today) else "${dayLabel(today)}, ${timeLabel()}"

internal fun TrackerEntry.primaryValue(definition: TrackerDefinition): String {
    if (definition.kind == TrackerKind.TIMESTAMP) return "Recorded"
    val active = definition.fields.filter { it.archivedAt == null }.sortedBy { it.order }
    if (definition.kind == TrackerKind.GROUP) {
        val parts = active.mapNotNull { field ->
            values[field.id]?.let { "${field.label}: ${formatFieldValue(field.kind, it, definition, field.id)}" }
        }
        return parts.take(2).joinToString(" · ").ifBlank { "Recorded" }
    }
    val field = active.firstOrNull() ?: return "Recorded"
    return values[field.id]?.let { formatFieldValue(field.kind, it, definition, field.id) } ?: "Recorded"
}

internal fun TrackerEntry.supportingValue(definition: TrackerDefinition): String? {
    val unit = definition.fields.firstOrNull()?.unit?.trim()?.ifBlank { null }
    return when {
        !note.isNullOrBlank() -> note
        definition.kind == TrackerKind.VALUE && unit != null -> unit
        definition.kind == TrackerKind.DURATION -> "duration"
        else -> null
    }
}

internal fun formatFieldValue(
    kind: FieldKind,
    value: FieldValue,
    definition: TrackerDefinition,
    fieldId: String,
): String {
    val field = definition.fields.firstOrNull { it.id == fieldId }
    return when (value) {
        is FieldValue.Decimal -> formatNumber(value.value, field?.decimalPlaces ?: 2)
        is FieldValue.Integer -> when (kind) {
            FieldKind.COUNTER -> if (value.value >= 0) "+${value.value}" else value.value.toString()
            else -> value.value.toString()
        }
        is FieldValue.BooleanValue -> if (value.value) "Yes" else "No"
        is FieldValue.DurationValue -> formatDuration(value.value)
        is FieldValue.Text -> value.value
        is FieldValue.Timestamp -> value.value.fullLabel()
        is FieldValue.Choice -> {
            val option = field?.options?.firstOrNull { it.id == value.optionId }
            val payload = value.payload?.let {
                formatFieldValue(kind, it, definition, fieldId)
            }
            listOfNotNull(option?.label ?: "Choice", payload).joinToString(" · ")
        }
    }
}

internal fun formatNumber(value: Double, decimalPlaces: Int = 2): String {
    val places = decimalPlaces.coerceIn(0, 6)
    val pattern = if (places == 0) "0" else "0.${"#".repeat(places)}"
    return DecimalFormat(pattern).format(value)
}

/** Accepts both decimal separators shown by Android keyboards across locales. */
internal fun parseDecimalInput(value: String): Double? {
    val trimmed = value.trim().replace('\u00a0', ' ')
    if (trimmed.isEmpty()) return null
    val normalized = if (',' in trimmed && '.' !in trimmed) trimmed.replace(',', '.') else trimmed
    return normalized.toDoubleOrNull()
}

internal fun formatDuration(value: Duration): String {
    val hours = value.toHours()
    val minutes = value.minusHours(hours).toMinutes()
    val seconds = value.minusHours(hours).minusMinutes(minutes).seconds
    return buildList {
        if (hours > 0) add("${hours}h")
        if (minutes > 0) add("${minutes}m")
        if (isEmpty() || seconds > 0) add("${seconds}s")
    }.joinToString(" ")
}

internal fun TrackerEntry.numericValue(definition: TrackerDefinition): Double? {
    val field = definition.fields.firstOrNull { it.archivedAt == null } ?: return null
    return when (val value = values[field.id]) {
        is FieldValue.Decimal -> value.value
        is FieldValue.Integer -> value.value.toDouble()
        is FieldValue.DurationValue -> value.value.toDisplayAmount(field.unit)
        is FieldValue.BooleanValue -> if (value.value) 1.0 else 0.0
        is FieldValue.Choice -> {
            val option = field.options.firstOrNull { it.id == value.optionId }
            option?.radioScore ?: when (val payload = value.payload) {
                is FieldValue.Decimal -> payload.value
                is FieldValue.Integer -> payload.value.toDouble()
                is FieldValue.DurationValue -> payload.value.toDisplayAmount(option?.payloadUnit)
                else -> null
            }
        }
        else -> null
    }
}

internal fun Duration.toDisplayAmount(unit: String?): Double = when (unit?.trim()?.lowercase()) {
    "s", "sec", "secs", "second", "seconds" -> toMillis() / 1_000.0
    "h", "hr", "hrs", "hour", "hours" -> toMillis() / 3_600_000.0
    "d", "day", "days" -> toMillis() / 86_400_000.0
    else -> toMillis() / 60_000.0
}

internal val recordedEntryComparator: Comparator<TrackerEntry> =
    compareBy<TrackerEntry> { it.recordedAt.localDate }
        .thenBy {
            when (val recordedAt = it.recordedAt) {
                is RecordedAt.Day -> LocalTime.MIN
                is RecordedAt.DateTime -> recordedAt.instant.atOffset(recordedAt.recordedOffset).toLocalTime()
            }
        }
        .thenBy { it.createdAt }

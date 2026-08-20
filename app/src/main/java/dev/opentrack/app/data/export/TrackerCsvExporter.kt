package dev.opentrack.app.data.export

import dev.opentrack.app.domain.model.EnumPayloadKind
import dev.opentrack.app.domain.model.FieldKind
import dev.opentrack.app.domain.model.FieldValue
import dev.opentrack.app.domain.model.RecordedAt
import dev.opentrack.app.domain.model.TrackerDefinition
import dev.opentrack.app.domain.model.TrackerEntry
import dev.opentrack.app.domain.model.TrackerField
import java.io.StringWriter
import java.io.Writer
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.format.DateTimeFormatter

object TrackerCsvExporter {
    fun export(definition: TrackerDefinition, entries: List<TrackerEntry>): String =
        StringWriter().also { write(definition, entries, it) }.toString()

    fun write(definition: TrackerDefinition, entries: List<TrackerEntry>, writer: Writer) {
        val fields = definition.fields.sortedBy { it.order }
        val columns = fields.flatMap(::columnsFor)
        Csv.writeRow(
            writer,
            listOf("entry_id", "date", "date_time", "time_zone") +
                columns.map { Csv.spreadsheetSafeText(it.header) } + "note",
        )

        entries.asSequence()
            .filter { it.trackerId == definition.id }
            .sortedWith(compareBy({ it.recordedAt.localDate }, { instantSortKey(it.recordedAt) }, TrackerEntry::createdAt))
            .forEach { entry ->
                val dateTime = entry.recordedAt as? RecordedAt.DateTime
                val fixed = listOf(
                    Csv.spreadsheetSafeText(entry.id),
                    entry.recordedAt.localDate.toString(),
                    dateTime?.instant?.atOffset(dateTime.recordedOffset)?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                    dateTime?.zoneId?.id,
                )
                Csv.writeRow(
                    writer,
                    fixed + columns.map { it.value(entry.values[it.field.id]) } + Csv.spreadsheetSafeText(entry.note),
                )
            }
    }

    private data class ExportColumn(
        val field: TrackerField,
        val header: String,
        val value: (FieldValue?) -> String?,
    )

    private fun columnsFor(field: TrackerField): List<ExportColumn> {
        val unit = field.unit?.let { " ($it)" }.orEmpty()
        return when (field.kind) {
            FieldKind.ENUM -> listOf(
                ExportColumn(field, field.label) { value ->
                    val choice = value as? FieldValue.Choice
                    Csv.spreadsheetSafeText(field.options.firstOrNull { it.id == choice?.optionId }?.label ?: choice?.optionId)
                },
                ExportColumn(field, "${field.label} payload") { value ->
                    val choice = value as? FieldValue.Choice ?: return@ExportColumn null
                    formatPayload(choice.payload, field.options.firstOrNull { it.id == choice.optionId }?.payloadKind)
                },
                ExportColumn(field, "${field.label} payload unit") { value ->
                    val choice = value as? FieldValue.Choice ?: return@ExportColumn null
                    Csv.spreadsheetSafeText(field.options.firstOrNull { it.id == choice.optionId }?.payloadUnit)
                },
            )
            FieldKind.RADIO -> listOf(ExportColumn(field, field.label) { value ->
                val choice = value as? FieldValue.Choice
                Csv.spreadsheetSafeText(field.options.firstOrNull { it.id == choice?.optionId }?.label ?: choice?.optionId)
            })
            FieldKind.VALUE -> listOf(ExportColumn(field, field.label + unit) { (it as? FieldValue.Decimal)?.value?.formatNumber() })
            FieldKind.COUNTER -> listOf(ExportColumn(field, field.label + unit) { (it as? FieldValue.Integer)?.value?.toString() })
            FieldKind.BOOLEAN -> listOf(ExportColumn(field, field.label) { (it as? FieldValue.BooleanValue)?.value?.toString() })
            FieldKind.DURATION -> listOf(ExportColumn(field, "${field.label} (seconds)") { value ->
                (value as? FieldValue.DurationValue)?.value?.toMillis()?.let(::formatMillisAsSeconds)
            })
            FieldKind.TIMESTAMP -> listOf(ExportColumn(field, field.label) { value ->
                when (val timestamp = (value as? FieldValue.Timestamp)?.value) {
                    is RecordedAt.Day -> timestamp.localDate.toString()
                    is RecordedAt.DateTime -> timestamp.instant.atOffset(timestamp.recordedOffset).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    null -> null
                }
            })
        }
    }

    private fun formatPayload(value: FieldValue?, kind: EnumPayloadKind?): String? = when (kind) {
        EnumPayloadKind.DECIMAL -> (value as? FieldValue.Decimal)?.value?.formatNumber()
        EnumPayloadKind.INTEGER -> (value as? FieldValue.Integer)?.value?.toString()
        EnumPayloadKind.DURATION -> (value as? FieldValue.DurationValue)?.value?.toMillis()?.let(::formatMillisAsSeconds)
        EnumPayloadKind.TEXT -> Csv.spreadsheetSafeText((value as? FieldValue.Text)?.value)
        EnumPayloadKind.NONE, null -> null
    }

    private fun Double.formatNumber(): String = BigDecimal.valueOf(this).stripTrailingZeros().toPlainString()

    private fun formatMillisAsSeconds(millis: Long): String = BigDecimal.valueOf(millis)
        .divide(BigDecimal.valueOf(1_000), 3, RoundingMode.DOWN)
        .stripTrailingZeros()
        .toPlainString()

    private fun instantSortKey(recordedAt: RecordedAt): Long = when (recordedAt) {
        is RecordedAt.Day -> Long.MIN_VALUE
        is RecordedAt.DateTime -> recordedAt.instant.toEpochMilli()
    }
}

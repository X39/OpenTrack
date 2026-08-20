package dev.opentrack.app.data.backup

import dev.opentrack.app.data.export.TrackerCsvExporter
import dev.opentrack.app.domain.model.Aggregation
import dev.opentrack.app.domain.model.AnalyticsMetric
import dev.opentrack.app.domain.model.BackupSnapshot
import dev.opentrack.app.domain.model.CalendarSpan
import dev.opentrack.app.domain.model.CalendarRange
import dev.opentrack.app.domain.model.CalendarWeekStart
import dev.opentrack.app.domain.model.ChartStyle
import dev.opentrack.app.domain.model.ChoiceOption
import dev.opentrack.app.domain.model.Dashboard
import dev.opentrack.app.domain.model.DashboardSeries
import dev.opentrack.app.domain.model.DashboardWidget
import dev.opentrack.app.domain.model.DashboardWidgetKind
import dev.opentrack.app.domain.model.DomainValidator
import dev.opentrack.app.domain.model.EnumPayloadKind
import dev.opentrack.app.domain.model.FieldKind
import dev.opentrack.app.domain.model.FieldValue
import dev.opentrack.app.domain.model.QuickAddConfig
import dev.opentrack.app.domain.model.QuickAddMode
import dev.opentrack.app.domain.model.QuickPreset
import dev.opentrack.app.domain.model.RecordedAt
import dev.opentrack.app.domain.model.TimeBucket
import dev.opentrack.app.domain.model.TimeRangePreset
import dev.opentrack.app.domain.model.TimestampPrecision
import dev.opentrack.app.domain.model.TimestampCalendarConfig
import dev.opentrack.app.domain.model.TimestampPresetMode
import dev.opentrack.app.domain.model.TrackerDefinition
import dev.opentrack.app.domain.model.TrackerEntry
import dev.opentrack.app.domain.model.TrackerField
import dev.opentrack.app.domain.model.TrackerKind
import dev.opentrack.app.domain.model.WidgetSpan
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupReadResult(
    val snapshot: BackupSnapshot,
    val formatVersion: Int,
    val trackerCount: Int,
    val entryCount: Int,
)

class BackupValidationException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Portable logical backup. The ZIP contains a versioned logical snapshot plus human-readable CSVs;
 * it deliberately never stores the Room database file.
 */
object LogicalBackupCodec {
    const val FORMAT_VERSION = 2
    private const val FORMAT = "opentrack-logical-backup"
    private const val MANIFEST = "manifest.json"
    private const val SNAPSHOT = "snapshot.bin"
    private const val MAGIC = "OpenTrackLogicalSnapshot"
    private const val MAX_ARCHIVE_BYTES = 64L * 1024L * 1024L
    private const val MAX_SNAPSHOT_BYTES = 32 * 1024 * 1024
    private const val MAX_MANIFEST_BYTES = 64 * 1024
    private const val MAX_ZIP_ENTRIES = 10_000
    private const val MAX_ENTRIES = 1_000_000
    private const val MAX_STRING_BYTES = 4 * 1024 * 1024

    fun write(snapshot: BackupSnapshot, output: OutputStream) {
        DomainValidator.validate(snapshot)
        val snapshotBytes = ByteArrayOutputStream().also { buffer ->
            SnapshotWriter(DataOutputStream(buffer)).write(snapshot)
        }.toByteArray()
        require(snapshotBytes.size <= MAX_SNAPSHOT_BYTES) { "Backup snapshot is too large" }
        val checksum = sha256(snapshotBytes)
        val manifest = manifest(snapshot, checksum).toByteArray(StandardCharsets.UTF_8)

        val zip = ZipOutputStream(output)
        writeEntry(zip, MANIFEST, manifest)
        writeEntry(zip, SNAPSHOT, snapshotBytes)
        snapshot.trackers.forEach { definition ->
            val csv = TrackerCsvExporter.export(
                definition,
                snapshot.entries.filter { it.trackerId == definition.id },
            ).toByteArray(StandardCharsets.UTF_8)
            writeEntry(zip, csvPath(definition), csv)
        }
        zip.finish()
        zip.flush()
    }

    @Throws(BackupValidationException::class)
    fun read(input: InputStream): BackupReadResult {
        try {
            var manifestBytes: ByteArray? = null
            var snapshotBytes: ByteArray? = null
            var totalBytes = 0L
            val seen = mutableSetOf<String>()
            val zip = ZipInputStream(input)
            while (true) {
                val entry = zip.nextEntry ?: break
                if (seen.size >= MAX_ZIP_ENTRIES) throw BackupValidationException("Backup has too many ZIP entries")
                validatePath(entry)
                if (!seen.add(entry.name)) throw BackupValidationException("Duplicate ZIP entry: ${entry.name}")
                val bytes = when (entry.name) {
                    MANIFEST -> readBounded(zip, MAX_MANIFEST_BYTES)
                    SNAPSHOT -> readBounded(zip, MAX_SNAPSHOT_BYTES)
                    else -> null
                }
                val entryBytes = bytes?.size?.toLong() ?: drainBounded(zip, MAX_ARCHIVE_BYTES - totalBytes)
                totalBytes += entryBytes
                if (totalBytes > MAX_ARCHIVE_BYTES) throw BackupValidationException("Backup archive is too large")
                when (entry.name) {
                    MANIFEST -> manifestBytes = bytes
                    SNAPSHOT -> snapshotBytes = bytes
                }
                zip.closeEntry()
            }

            val manifestText = manifestBytes?.toString(StandardCharsets.UTF_8)
                ?: throw BackupValidationException("Backup has no manifest")
            val data = snapshotBytes ?: throw BackupValidationException("Backup has no logical snapshot")
            val metadata = parseManifest(manifestText)
            if (metadata.format != FORMAT) throw BackupValidationException("Not an OpenTrack backup")
            if (metadata.version !in 1..FORMAT_VERSION) {
                throw BackupValidationException("Unsupported backup version ${metadata.version}")
            }
            if (sha256(data) != metadata.sha256) throw BackupValidationException("Backup checksum does not match")

            val snapshot = SnapshotReader(
                DataInputStream(ByteArrayInputStream(data)),
                metadata.version,
            ).read()
            DomainValidator.validate(snapshot)
            if (snapshot.trackers.size != metadata.trackerCount || snapshot.entries.size != metadata.entryCount) {
                throw BackupValidationException("Backup counts do not match its manifest")
            }
            return BackupReadResult(snapshot, metadata.version, metadata.trackerCount, metadata.entryCount)
        } catch (error: BackupValidationException) {
            throw error
        } catch (error: Exception) {
            throw BackupValidationException("Backup is malformed: ${error.message ?: error::class.java.simpleName}", error)
        }
    }

    private data class ManifestData(
        val format: String,
        val version: Int,
        val sha256: String,
        val trackerCount: Int,
        val entryCount: Int,
    )

    private fun manifest(snapshot: BackupSnapshot, checksum: String): String = """
        {
          "format":"$FORMAT",
          "version":$FORMAT_VERSION,
          "createdAt":"${snapshot.createdAt}",
          "snapshotSha256":"$checksum",
          "trackerCount":${snapshot.trackers.size},
          "entryCount":${snapshot.entries.size},
          "dashboardCount":${snapshot.dashboards.size}
        }
    """.trimIndent()

    private fun parseManifest(text: String): ManifestData {
        fun string(name: String): String = Regex("\\\"$name\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"")
            .find(text)?.groupValues?.get(1) ?: throw BackupValidationException("Manifest is missing $name")
        fun int(name: String): Int = Regex("\\\"$name\\\"\\s*:\\s*(\\d+)")
            .find(text)?.groupValues?.get(1)?.toIntOrNull() ?: throw BackupValidationException("Manifest is missing $name")
        return ManifestData(string("format"), int("version"), string("snapshotSha256"), int("trackerCount"), int("entryCount"))
    }

    private fun validatePath(entry: ZipEntry) {
        val name = entry.name
        if (entry.isDirectory || name.startsWith('/') || name.startsWith('\\') || name.contains("..") || name.contains('\\')) {
            throw BackupValidationException("Unsafe ZIP entry path")
        }
        if (name != MANIFEST && name != SNAPSHOT && !(name.startsWith("csv/") && name.endsWith(".csv"))) {
            throw BackupValidationException("Unexpected ZIP entry: $name")
        }
        val entryLimit = if (name == SNAPSHOT) MAX_SNAPSHOT_BYTES.toLong() else MAX_ARCHIVE_BYTES
        if (entry.size > entryLimit || entry.compressedSize > MAX_ARCHIVE_BYTES) {
            throw BackupValidationException("ZIP entry is too large")
        }
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun readBounded(input: InputStream, maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) throw BackupValidationException("ZIP entry is too large")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun drainBounded(input: InputStream, maxBytes: Long): Long {
        if (maxBytes < 0L) throw BackupValidationException("Backup archive is too large")
        val buffer = ByteArray(8192)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) throw BackupValidationException("Backup archive is too large")
        }
        return total
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private fun csvPath(definition: TrackerDefinition): String {
        val safe = definition.name.replace(Regex("[^A-Za-z0-9_-]+"), "_").trim('_').take(48).ifBlank { "tracker" }
        return "csv/${safe}-${definition.id.take(8)}.csv"
    }

    private class SnapshotWriter(private val output: DataOutputStream) {
        fun write(snapshot: BackupSnapshot) {
            string(MAGIC)
            output.writeInt(FORMAT_VERSION)
            output.writeLong(snapshot.createdAt.toEpochMilli())
            list(snapshot.trackers, ::tracker)
            list(snapshot.entries, ::entry)
            list(snapshot.dashboards, ::dashboard)
            output.flush()
        }

        private fun tracker(value: TrackerDefinition) {
            string(value.id); string(value.name); nullableString(value.description); enum(value.kind); enum(value.timestampPrecision)
            nullableString(value.iconKey); nullableLong(value.colorArgb); output.writeInt(value.order); nullableInstant(value.archivedAt)
            output.writeLong(value.createdAt.toEpochMilli()); output.writeLong(value.updatedAt.toEpochMilli())
            list(value.fields, ::field); list(value.presets, ::preset)
            enum(value.quickAdd.mode); nullableString(value.quickAdd.defaultPresetId)
            output.writeBoolean(value.timestampCalendar.showDayNumber)
            output.writeBoolean(value.timestampCalendar.showCount)
            output.writeBoolean(value.timestampCalendar.showWeekdayHeader)
            enum(value.timestampCalendar.weekStart)
            enum(value.timestampCalendar.span)
            enum(value.timestampCalendar.range)
            output.writeBoolean(value.timestampCalendar.showEmptyDays)
        }

        private fun field(value: TrackerField) {
            string(value.id); string(value.label); enum(value.kind); output.writeBoolean(value.required); output.writeInt(value.order)
            nullableString(value.unit); output.writeInt(value.decimalPlaces); output.writeLong(value.counterQuickDelta)
            nullableEnum(value.timestampPrecision); nullableInstant(value.archivedAt); list(value.options, ::option)
        }

        private fun option(value: ChoiceOption) {
            string(value.id); string(value.label); nullableLong(value.colorArgb); output.writeInt(value.order)
            nullableDouble(value.radioScore); enum(value.payloadKind); nullableString(value.payloadLabel); nullableString(value.payloadUnit)
            nullableInstant(value.archivedAt)
        }

        private fun preset(value: QuickPreset) {
            string(value.id); string(value.label); output.writeInt(value.order); nullableString(value.note)
            output.writeInt(value.values.size)
            value.values.toSortedMap().forEach { (fieldId, fieldValue) -> string(fieldId); value(fieldValue) }
            output.writeInt(value.timestampModes.size)
            value.timestampModes.toSortedMap().forEach { (fieldId, mode) -> string(fieldId); enum(mode) }
        }

        private fun entry(value: TrackerEntry) {
            string(value.id); string(value.trackerId); recordedAt(value.recordedAt); nullableString(value.note)
            output.writeLong(value.createdAt.toEpochMilli()); output.writeLong(value.updatedAt.toEpochMilli())
            output.writeInt(value.values.size)
            value.values.toSortedMap().forEach { (fieldId, fieldValue) -> string(fieldId); value(fieldValue) }
        }

        private fun dashboard(value: Dashboard) {
            string(value.id); string(value.name); output.writeInt(value.order); list(value.widgets, ::widget)
        }

        private fun widget(value: DashboardWidget) {
            string(value.id); enum(value.kind); nullableString(value.title); enum(value.chartStyle); enum(value.range); enum(value.bucket)
            output.writeInt(value.order); enum(value.span); output.writeBoolean(value.visible); list(value.series, ::series)
        }

        private fun series(value: DashboardSeries) {
            string(value.id); string(value.trackerId); nullableString(value.fieldId); nullableString(value.optionId)
            enum(value.metric); enum(value.aggregation); nullableLong(value.colorArgb); output.writeInt(value.order); nullableString(value.presetId)
        }

        private fun value(value: FieldValue) {
            when (value) {
                is FieldValue.Decimal -> { output.writeByte(1); output.writeDouble(value.value) }
                is FieldValue.Integer -> { output.writeByte(2); output.writeLong(value.value) }
                is FieldValue.BooleanValue -> { output.writeByte(3); output.writeBoolean(value.value) }
                is FieldValue.DurationValue -> { output.writeByte(4); output.writeLong(value.value.toMillis()) }
                is FieldValue.Text -> { output.writeByte(5); string(value.value) }
                is FieldValue.Choice -> { output.writeByte(6); string(value.optionId); output.writeBoolean(value.payload != null); value.payload?.let(::value) }
                is FieldValue.Timestamp -> { output.writeByte(7); recordedAt(value.value) }
            }
        }

        private fun recordedAt(value: RecordedAt) {
            when (value) {
                is RecordedAt.Day -> { output.writeByte(0); output.writeLong(value.localDate.toEpochDay()) }
                is RecordedAt.DateTime -> {
                    output.writeByte(1); output.writeLong(value.instant.toEpochMilli()); string(value.zoneId.id)
                    output.writeInt(value.recordedOffset.totalSeconds)
                }
            }
        }

        private fun <T> list(values: List<T>, writer: (T) -> Unit) {
            output.writeInt(values.size); values.forEach(writer)
        }
        private fun enum(value: Enum<*>) = string(value.name)
        private fun nullableEnum(value: Enum<*>?) = nullableString(value?.name)
        private fun nullableInstant(value: Instant?) = nullableLong(value?.toEpochMilli())
        private fun nullableLong(value: Long?) { output.writeBoolean(value != null); value?.let(output::writeLong) }
        private fun nullableDouble(value: Double?) { output.writeBoolean(value != null); value?.let(output::writeDouble) }
        private fun nullableString(value: String?) { if (value == null) output.writeInt(-1) else string(value) }
        private fun string(value: String) {
            val bytes = value.toByteArray(StandardCharsets.UTF_8)
            require(bytes.size <= MAX_STRING_BYTES) { "Backup text value is too large" }
            output.writeInt(bytes.size); output.write(bytes)
        }
    }

    private class SnapshotReader(
        private val input: DataInputStream,
        private val expectedVersion: Int,
    ) {
        private var version: Int = 0

        fun read(): BackupSnapshot {
            if (string() != MAGIC) throw BackupValidationException("Logical snapshot header is invalid")
            version = input.readInt()
            if (version !in 1..FORMAT_VERSION) throw BackupValidationException("Logical snapshot version is unsupported")
            if (version != expectedVersion) throw BackupValidationException("Backup versions do not match")
            val createdAt = Instant.ofEpochMilli(input.readLong())
            val trackers = list(::tracker)
            val entries = list(::entry)
            val dashboards = list(::dashboard)
            val result = BackupSnapshot(trackers, entries, dashboards, createdAt)
            if (input.read() != -1) throw BackupValidationException("Logical snapshot contains trailing data")
            return result
        }

        private fun tracker(): TrackerDefinition {
            val id = string()
            val name = string()
            val description = nullableString()
            val kind = enum<TrackerKind>()
            val timestampPrecision = enum<TimestampPrecision>()
            val iconKey = nullableString()
            val colorArgb = nullableLong()
            val order = input.readInt()
            val archivedAt = nullableInstant()
            val createdAt = Instant.ofEpochMilli(input.readLong())
            val updatedAt = Instant.ofEpochMilli(input.readLong())
            val fields = list(::field)
            val presets = list(::preset)
            val quickAddMode = enum<QuickAddMode>()
            val defaultPresetId = nullableString()
            val timestampCalendar = if (version >= 2) {
                TimestampCalendarConfig(
                    showDayNumber = input.readBoolean(),
                    showCount = input.readBoolean(),
                    showWeekdayHeader = input.readBoolean(),
                    weekStart = enum<CalendarWeekStart>(),
                    span = enum<CalendarSpan>(),
                    range = enum<CalendarRange>(),
                    showEmptyDays = input.readBoolean(),
                )
            } else TimestampCalendarConfig()
            return TrackerDefinition(
                id = id,
                name = name,
                description = description,
                kind = kind,
                timestampPrecision = timestampPrecision,
                iconKey = iconKey,
                colorArgb = colorArgb,
                timestampCalendar = timestampCalendar,
                order = order,
                fields = fields,
                presets = presets,
                quickAdd = QuickAddConfig(quickAddMode, defaultPresetId),
                archivedAt = archivedAt,
                createdAt = createdAt,
                updatedAt = updatedAt,
            )
        }

        private fun field(): TrackerField {
            val id = string()
            val label = string()
            val kind = enum<FieldKind>()
            val required = input.readBoolean()
            val order = input.readInt()
            val unit = nullableString()
            val decimalPlaces = input.readInt()
            val counterQuickDelta = input.readLong()
            val timestampPrecision = nullableEnum<TimestampPrecision>()
            val archivedAt = nullableInstant()
            val options = list(::option)
            return TrackerField(
                id = id,
                label = label,
                kind = kind,
                required = required,
                order = order,
                unit = unit,
                decimalPlaces = decimalPlaces,
                counterQuickDelta = counterQuickDelta,
                timestampPrecision = timestampPrecision,
                options = options,
                archivedAt = archivedAt,
            )
        }

        private fun option(): ChoiceOption {
            val id = string()
            val label = string()
            val colorArgb = nullableLong()
            val order = input.readInt()
            val radioScore = nullableDouble()
            val payloadKind = enum<EnumPayloadKind>()
            val payloadLabel = nullableString()
            val payloadUnit = nullableString()
            val archivedAt = nullableInstant()
            return ChoiceOption(
                id = id,
                label = label,
                colorArgb = colorArgb,
                order = order,
                radioScore = radioScore,
                payloadKind = payloadKind,
                payloadLabel = payloadLabel,
                payloadUnit = payloadUnit,
                archivedAt = archivedAt,
            )
        }

        private fun preset(): QuickPreset {
            val id = string()
            val label = string()
            val order = input.readInt()
            val note = nullableString()
            val valueCount = count()
            val values = buildMap {
                repeat(valueCount) { put(string(), value()) }
            }
            val modeCount = count()
            val modes = buildMap {
                repeat(modeCount) { put(string(), enum<TimestampPresetMode>()) }
            }
            return QuickPreset(id, label, order, values, modes, note)
        }

        private fun entry(): TrackerEntry {
            val id = string()
            val trackerId = string()
            val recordedAt = recordedAt()
            val note = nullableString()
            val createdAt = Instant.ofEpochMilli(input.readLong())
            val updatedAt = Instant.ofEpochMilli(input.readLong())
            val valueCount = count()
            val values = buildMap {
                repeat(valueCount) { put(string(), value()) }
            }
            return TrackerEntry(id, trackerId, recordedAt, values, note, createdAt, updatedAt)
        }

        private fun dashboard(): Dashboard {
            val id = string()
            val name = string()
            val order = input.readInt()
            val widgets = list(::widget)
            return Dashboard(id = id, name = name, order = order, widgets = widgets)
        }

        private fun widget(): DashboardWidget {
            val id = string()
            val kind = enum<DashboardWidgetKind>()
            val title = nullableString()
            val chartStyle = enum<ChartStyle>()
            val range = enum<TimeRangePreset>()
            val bucket = enum<TimeBucket>()
            val order = input.readInt()
            val span = enum<WidgetSpan>()
            val visible = input.readBoolean()
            val series = list(::series)
            return DashboardWidget(
                id = id,
                kind = kind,
                title = title,
                chartStyle = chartStyle,
                range = range,
                bucket = bucket,
                order = order,
                span = span,
                visible = visible,
                series = series,
            )
        }

        private fun series(): DashboardSeries {
            val id = string()
            val trackerId = string()
            val fieldId = nullableString()
            val optionId = nullableString()
            val metric = enum<AnalyticsMetric>()
            val aggregation = enum<Aggregation>()
            val colorArgb = nullableLong()
            val order = input.readInt()
            val presetId = nullableString()
            return DashboardSeries(
                id = id,
                trackerId = trackerId,
                fieldId = fieldId,
                optionId = optionId,
                metric = metric,
                aggregation = aggregation,
                colorArgb = colorArgb,
                order = order,
                presetId = presetId,
            )
        }

        private fun value(depth: Int = 0): FieldValue {
            if (depth > 2) throw BackupValidationException("Nested values are too deep")
            return when (input.readUnsignedByte()) {
                1 -> FieldValue.Decimal(input.readDouble())
                2 -> FieldValue.Integer(input.readLong())
                3 -> FieldValue.BooleanValue(input.readBoolean())
                4 -> FieldValue.DurationValue(Duration.ofMillis(input.readLong()))
                5 -> FieldValue.Text(string())
                6 -> {
                    val optionId = string()
                    val payload = if (input.readBoolean()) value(depth + 1) else null
                    FieldValue.Choice(optionId, payload)
                }
                7 -> FieldValue.Timestamp(recordedAt())
                else -> throw BackupValidationException("Unknown stored value type")
            }
        }

        private fun recordedAt(): RecordedAt = when (input.readUnsignedByte()) {
            0 -> RecordedAt.Day(LocalDate.ofEpochDay(input.readLong()))
            1 -> {
                val instant = Instant.ofEpochMilli(input.readLong())
                val zoneId = ZoneId.of(string())
                val recordedOffset = ZoneOffset.ofTotalSeconds(input.readInt())
                RecordedAt.DateTime(instant, zoneId, recordedOffset)
            }
            else -> throw BackupValidationException("Unknown timestamp type")
        }

        private fun count(): Int = input.readInt().also {
            if (it !in 0..MAX_ENTRIES) throw BackupValidationException("Logical snapshot collection is too large")
        }
        private fun <T> list(reader: () -> T): List<T> = List(count()) { reader() }
        private inline fun <reified T : Enum<T>> enum(): T = enumValueOf(string())
        private inline fun <reified T : Enum<T>> nullableEnum(): T? = nullableString()?.let(::enumValueOf)
        private fun nullableInstant(): Instant? = nullableLong()?.let(Instant::ofEpochMilli)
        private fun nullableLong(): Long? = if (input.readBoolean()) input.readLong() else null
        private fun nullableDouble(): Double? = if (input.readBoolean()) input.readDouble() else null
        private fun nullableString(): String? {
            val length = input.readInt()
            if (length == -1) return null
            return string(length)
        }
        private fun string(): String = string(input.readInt())
        private fun string(length: Int): String {
            if (length !in 0..MAX_STRING_BYTES) throw BackupValidationException("Logical snapshot text is too large")
            val bytes = ByteArray(length)
            try { input.readFully(bytes) } catch (error: EOFException) { throw BackupValidationException("Logical snapshot ended early", error) }
            return bytes.toString(StandardCharsets.UTF_8)
        }
    }
}

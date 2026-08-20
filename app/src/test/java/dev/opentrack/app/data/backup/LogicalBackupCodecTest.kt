package dev.opentrack.app.data.backup

import com.google.common.truth.Truth.assertThat
import dev.opentrack.app.domain.model.BackupSnapshot
import dev.opentrack.app.domain.model.FieldValue
import dev.opentrack.app.domain.model.RecordedAt
import dev.opentrack.app.domain.model.TrackerEntry
import dev.opentrack.app.domain.template.StarterTemplates
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertThrows
import org.junit.Test

class LogicalBackupCodecTest {
    @Test fun `logical backup round trips and validates`() {
        val instant = Instant.parse("2026-08-19T10:15:30Z")
        val definition = StarterTemplates.instantiate(
            StarterTemplates.WEIGHT,
            clock = Clock.fixed(instant, ZoneOffset.UTC),
        )
        val field = definition.fields.single()
        val entry = TrackerEntry(
            id = "entry-id",
            trackerId = definition.id,
            recordedAt = RecordedAt.DateTime(instant, ZoneOffset.UTC),
            values = mapOf(field.id to FieldValue.Decimal(78.4)),
            note = "Morning",
            createdAt = instant,
            updatedAt = instant,
        )
        val original = BackupSnapshot(listOf(definition), listOf(entry), emptyList(), instant)
        val bytes = ByteArrayOutputStream().also { LogicalBackupCodec.write(original, it) }.toByteArray()

        val decoded = LogicalBackupCodec.read(ByteArrayInputStream(bytes))

        assertThat(decoded.trackerCount).isEqualTo(1)
        assertThat(decoded.entryCount).isEqualTo(1)
        assertThat(decoded.snapshot).isEqualTo(original)

        val names = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val zipEntry = zip.nextEntry ?: break
                names += zipEntry.name
            }
        }
        assertThat(names).contains("manifest.json")
        assertThat(names).contains("snapshot.bin")
        assertThat(names.any { it.startsWith("csv/") && it.endsWith(".csv") }).isTrue()
    }

    @Test fun `tampered logical snapshot is rejected by checksum validation`() {
        val instant = Instant.parse("2026-08-19T10:15:30Z")
        val definition = StarterTemplates.instantiate(
            StarterTemplates.MOMENT,
            clock = Clock.fixed(instant, ZoneOffset.UTC),
        )
        val original = BackupSnapshot(listOf(definition), emptyList(), emptyList(), instant)
        val valid = ByteArrayOutputStream().also { LogicalBackupCodec.write(original, it) }.toByteArray()
        val tampered = rewriteSnapshot(valid) { bytes -> bytes.copyOf().also { it[it.lastIndex] = (it.last() + 1).toByte() } }

        val error = assertThrows(BackupValidationException::class.java) {
            LogicalBackupCodec.read(ByteArrayInputStream(tampered))
        }
        assertThat(error).hasMessageThat().contains("checksum")
    }

    @Test fun `hostile zip traversal path is rejected before import`() {
        val hostile = zipOf("../outside.csv", "bad".toByteArray())

        val error = assertThrows(BackupValidationException::class.java) {
            LogicalBackupCodec.read(ByteArrayInputStream(hostile))
        }

        assertThat(error).hasMessageThat().contains("Unsafe ZIP entry path")
    }

    @Test fun `malformed truncated archive is rejected`() {
        val truncated = zipOf("manifest.json", "{\"format\":\"opentrack-logical-backup\"}".toByteArray())

        assertThrows(BackupValidationException::class.java) {
            LogicalBackupCodec.read(ByteArrayInputStream(truncated))
        }
    }

    @Test fun `oversized logical text is rejected at write boundary`() {
        val instant = Instant.parse("2026-08-19T10:15:30Z")
        val definition = StarterTemplates.instantiate(StarterTemplates.MOMENT).copy(
            description = "x".repeat(4 * 1024 * 1024 + 1),
            createdAt = instant,
            updatedAt = instant,
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            LogicalBackupCodec.write(BackupSnapshot(listOf(definition), emptyList(), emptyList(), instant), ByteArrayOutputStream())
        }
        assertThat(error).hasMessageThat().contains("too large")
    }

    private fun rewriteSnapshot(input: ByteArray, transform: (ByteArray) -> ByteArray): ByteArray {
        val entries = mutableListOf<Pair<String, ByteArray>>()
        ZipInputStream(ByteArrayInputStream(input)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val bytes = zip.readBytes()
                entries += entry.name to if (entry.name == "snapshot.bin") transform(bytes) else bytes
            }
        }
        return ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                entries.forEach { (name, bytes) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
        }.toByteArray()
    }

    private fun zipOf(name: String, bytes: ByteArray): ByteArray = ByteArrayOutputStream().also { output ->
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(bytes)
            zip.closeEntry()
        }
    }.toByteArray()
}

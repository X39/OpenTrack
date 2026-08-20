package dev.opentrack.app.presentation

import dev.opentrack.app.data.backup.LogicalBackupCodec
import dev.opentrack.app.data.backup.BackupReadResult
import dev.opentrack.app.domain.model.BackupSnapshot
import java.io.InputStream
import java.io.OutputStream

internal object BackupTransfer {
    fun write(snapshot: BackupSnapshot, output: OutputStream) {
        LogicalBackupCodec.write(snapshot, output)
    }

    fun read(input: InputStream): BackupReadResult = LogicalBackupCodec.read(input)
}

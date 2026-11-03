package com.gympad.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val BACKUP_VERSION = 1

@Serializable
data class BackupFile(
    val version: Int = BACKUP_VERSION,
    val exportedAt: Long,
    val exercises: List<Exercise>,
    val routines: List<Routine>,
    val slots: List<ExerciseSlot>,
    val variants: List<SlotVariant>,
    val sessions: List<WorkoutSession>,
    val sessionExercises: List<SessionExercise>,
    val sets: List<SetEntry>,
)

enum class InvalidBackupReason { NOT_A_BACKUP, TOO_NEW, MISSING_ROUTINES }

class InvalidBackupException(val reason: InvalidBackupReason, cause: Throwable? = null) : Exception(reason.name, cause)

private val backupJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
}

fun BackupFile.encode(): String = backupJson.encodeToString(BackupFile.serializer(), this)

fun decodeBackup(text: String): BackupFile {
    val backup = try {
        backupJson.decodeFromString(BackupFile.serializer(), text)
    } catch (e: IllegalArgumentException) {
        throw InvalidBackupException(InvalidBackupReason.NOT_A_BACKUP, e)
    }
    if (backup.version > BACKUP_VERSION) {
        throw InvalidBackupException(InvalidBackupReason.TOO_NEW)
    }
    if (backup.routines.map { it.letter }.toSet() != ROUTINE_LETTERS.toSet()) {
        throw InvalidBackupException(InvalidBackupReason.MISSING_ROUTINES)
    }
    return backup
}

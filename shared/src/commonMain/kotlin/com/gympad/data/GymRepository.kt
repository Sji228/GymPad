package com.gympad.data

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.gympad.domain.BuiltInNames
import com.gympad.domain.matching
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

val ROUTINE_LETTERS = listOf("A", "B", "C", "D", "E")

data class SetTarget(
    val epochDay: Long,
    val routineId: Long,
    val slotId: Long,
    val exerciseId: Long,
)

class GymRepository(private val db: GymDatabase) {
    private val dao = db.dao()

    // Keystrokes must land in the order they were typed, and two quick edits must not both
    // try to create today's session.
    private val writeLock = Mutex()

    private suspend fun <T> write(block: suspend () -> T): T =
        writeLock.withLock { db.useWriterConnection { it.immediateTransaction { block() } } }

    fun observeTemplate() = dao.observeTemplate()
    fun observeRoutines() = dao.observeRoutines()
    fun observeExercises() = dao.observeExercises()
    fun observeDay(epochDay: Long) = dao.observeDay(epochDay)
    fun observeStatusRows() = dao.observeStatusRows()
    fun observeFirstSessionDay() = dao.observeFirstSessionDay()
    fun observeHistory(exerciseIds: List<Long>): Flow<List<HistoryRow>> = dao.observeHistory(exerciseIds)

    suspend fun seedIfEmpty() = write {
        if (dao.routineCount() > 0) return@write
        val names = mapOf("A" to "Push", "B" to "Pull", "C" to "Legs")
        val ids = ROUTINE_LETTERS.associateWith { dao.insert(Routine(letter = it, name = names[it].orEmpty())) }
        seedRoutine(
            ids.getValue("A"),
            listOf("Barbell Bench Press", "Dumbbell Bench Press", "Chest Press Machine"),
            listOf("Overhead Press", "Dumbbell Shoulder Press"),
            listOf("Cable Fly"),
        )
        seedRoutine(
            ids.getValue("B"),
            listOf("Barbell Row", "Seated Cable Row"),
            listOf("Pull-up", "Lat Pulldown"),
        )
        seedRoutine(
            ids.getValue("C"),
            listOf("Back Squat", "Leg Press"),
            listOf("Romanian Deadlift", "Leg Curl"),
        )
    }

    private suspend fun seedRoutine(routineId: Long, vararg slots: List<String>) {
        slots.forEachIndexed { position, exercises ->
            val slotId = dao.insert(ExerciseSlot(routineId = routineId, position = position))
            exercises.forEachIndexed { variantPosition, name ->
                dao.insert(SlotVariant(slotId = slotId, exerciseId = exerciseIdFor(name), position = variantPosition))
            }
        }
    }

    private suspend fun exerciseIdFor(name: String): Long =
        dao.allExercises().matching(name)?.id ?: dao.insert(Exercise(name = BuiltInNames.canonicalExercise(name)))

    /** Sessions are created on first interaction, so browsing a routine tab leaves no trace. */
    private suspend fun ensureRow(target: SetTarget): SessionExercise {
        val session = dao.findSession(target.epochDay, target.routineId)
            ?: createSession(target)
        val rows = dao.sessionRows(session.id, target.slotId)
        rows.firstOrNull { it.exerciseId == target.exerciseId }?.let { return it }
        val slot = dao.getSlot(target.slotId)
        val row = sessionRow(session.id, slot, target.exerciseId, plannedSets = rows.firstOrNull()?.plannedSets)
        return row.copy(id = dao.insert(row))
    }

    private suspend fun createSession(target: SetTarget): WorkoutSession {
        val session = WorkoutSession(
            epochDay = target.epochDay,
            routineId = target.routineId,
            startedAt = System.currentTimeMillis(),
        )
        val sessionId = dao.insert(session)
        for (slot in dao.slotsOf(target.routineId)) {
            val exerciseId = if (slot.id == target.slotId) {
                target.exerciseId
            } else {
                dao.variantsOf(slot.id).firstOrNull()?.exerciseId ?: continue
            }
            dao.insert(sessionRow(sessionId, slot, exerciseId, plannedSets = null))
        }
        return session.copy(id = sessionId)
    }

    private fun sessionRow(
        sessionId: Long,
        slot: ExerciseSlot,
        exerciseId: Long,
        plannedSets: Int?,
    ) = SessionExercise(
        sessionId = sessionId,
        slotId = slot.id,
        exerciseId = exerciseId,
        position = slot.position,
        plannedSets = plannedSets ?: slot.defaultSets,
    )

    suspend fun editSet(target: SetTarget, setIndex: Int, edit: (SetEntry) -> SetEntry) = write {
        val row = ensureRow(target)
        val current = dao.getSet(row.id, setIndex) ?: SetEntry(row.id, setIndex)
        val updated = edit(current)
        if (updated.weightKg == null && updated.reps == null && !updated.completed) {
            dao.deleteSet(row.id, setIndex)
        } else {
            dao.upsert(updated)
        }
    }

    suspend fun changePlannedSets(target: SetTarget, plannedSets: Int) = write {
        val row = ensureRow(target)
        dao.setPlannedSets(row.sessionId, target.slotId, plannedSets)
        dao.deleteSetsFrom(row.id, plannedSets)
    }

    suspend fun updateRoutineName(routine: Routine, name: String) = write {
        dao.update(routine.copy(name = name))
    }

    suspend fun addSlot(routineId: Long, name: String) = write {
        val position = (dao.slotsOf(routineId).maxOfOrNull { it.position } ?: -1) + 1
        val slotId = dao.insert(ExerciseSlot(routineId = routineId, position = position))
        dao.insert(SlotVariant(slotId = slotId, exerciseId = exerciseIdFor(name), position = 0))
    }

    suspend fun setDefaultSets(slotId: Long, sets: Int) = write {
        dao.update(dao.getSlot(slotId).copy(defaultSets = sets))
    }

    suspend fun moveSlot(slotId: Long, delta: Int) = write {
        val slot = dao.getSlot(slotId)
        val slots = dao.slotsOf(slot.routineId)
        val other = slots.getOrNull(slots.indexOfFirst { it.id == slotId } + delta) ?: return@write
        dao.update(slot.copy(position = other.position))
        dao.update(other.copy(position = slot.position))
    }

    suspend fun deleteSlot(slotId: Long, today: Long) = write {
        dao.deleteUnloggedRows(slotId, today)
        dao.deleteSlot(slotId)
    }

    suspend fun addVariant(slotId: Long, name: String) = write {
        val variants = dao.variantsOf(slotId)
        val exerciseId = exerciseIdFor(name)
        if (variants.none { it.exerciseId == exerciseId }) {
            val position = (variants.maxOfOrNull { it.position } ?: -1) + 1
            dao.insert(SlotVariant(slotId = slotId, exerciseId = exerciseId, position = position))
        }
    }

    suspend fun makeVariantMain(slotId: Long, variantId: Long) = write {
        dao.variantsOf(slotId).sortedByDescending { it.id == variantId }.forEachIndexed { position, variant ->
            dao.update(variant.copy(position = position))
        }
    }

    suspend fun removeVariant(variantId: Long) = write {
        dao.deleteVariant(variantId)
    }

    /** Points the variant at another exercise, whose own history follows it. */
    suspend fun pointVariantAt(slotId: Long, variantId: Long, name: String) = write {
        val variants = dao.variantsOf(slotId)
        val exerciseId = exerciseIdFor(name)
        if (variants.none { it.exerciseId == exerciseId }) {
            dao.update(variants.first { it.id == variantId }.copy(exerciseId = exerciseId))
        }
    }

    /** Fixes a name in place: every routine using this exercise and its history keep working. */
    suspend fun renameExercise(exerciseId: Long, name: String) = write {
        val canonical = BuiltInNames.canonicalExercise(name)
        val taken = dao.allExercises().any { it.id != exerciseId && it.name.equals(canonical, ignoreCase = true) }
        if (!taken) dao.update(dao.getExercise(exerciseId).copy(name = canonical))
    }

    suspend fun exportBackup(): BackupFile = write {
        BackupFile(
            exportedAt = System.currentTimeMillis(),
            exercises = dao.allExercises(),
            routines = dao.allRoutines(),
            slots = dao.allSlots(),
            variants = dao.allVariants(),
            sessions = dao.allSessions(),
            sessionExercises = dao.allSessionExercises(),
            sets = dao.allSets(),
        )
    }

    /** Runs in one transaction: a backup with broken references leaves current data untouched. */
    suspend fun replaceAllData(backup: BackupFile) = write {
        dao.clearSets()
        dao.clearSessionExercises()
        dao.clearSessions()
        dao.clearVariants()
        dao.clearSlots()
        dao.clearRoutines()
        dao.clearExercises()
        dao.insertExercises(backup.exercises)
        dao.insertRoutines(backup.routines)
        dao.insertSlots(backup.slots)
        dao.insertVariants(backup.variants)
        dao.insertSessions(backup.sessions)
        dao.insertSessionExercises(backup.sessionExercises)
        dao.insertSets(backup.sets)
    }
}

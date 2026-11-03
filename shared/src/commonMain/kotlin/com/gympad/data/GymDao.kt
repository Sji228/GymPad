package com.gympad.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class TemplateRow(
    val routineId: Long,
    val slotId: Long,
    val slotPosition: Int,
    val defaultSets: Int,
    val variantId: Long?,
    val variantPosition: Int?,
    val exerciseId: Long?,
    val exerciseName: String?,
)

data class DayRow(
    val routineId: Long,
    val sessionExerciseId: Long,
    val slotId: Long?,
    val exerciseId: Long,
    val exerciseName: String,
    val position: Int,
    val plannedSets: Int,
    val setIndex: Int?,
    val weightKg: Double?,
    val reps: Int?,
    val completed: Boolean?,
)

data class StatusRow(
    val epochDay: Long,
    val sessionId: Long,
    val sessionExerciseId: Long,
    val slotId: Long?,
    val plannedSets: Int,
    val completedSets: Int,
)

data class HistoryRow(
    val exerciseId: Long,
    val epochDay: Long,
    val sessionExerciseId: Long,
    val setIndex: Int,
    val weightKg: Double,
    val reps: Int,
)

@Dao
interface GymDao {
    @Query(
        """
        SELECT sl.routineId, sl.id AS slotId, sl.position AS slotPosition, sl.defaultSets,
               v.id AS variantId, v.position AS variantPosition, e.id AS exerciseId, e.name AS exerciseName
        FROM exercise_slot sl
        LEFT JOIN slot_variant v ON v.slotId = sl.id
        LEFT JOIN exercise e ON e.id = v.exerciseId
        ORDER BY sl.routineId, sl.position, v.position
        """
    )
    fun observeTemplate(): Flow<List<TemplateRow>>

    @Query(
        """
        SELECT s.routineId, se.id AS sessionExerciseId, se.slotId, se.exerciseId, x.name AS exerciseName,
               se.position, se.plannedSets, e.setIndex, e.weightKg, e.reps, e.completed
        FROM workout_session s
        JOIN session_exercise se ON se.sessionId = s.id
        JOIN exercise x ON x.id = se.exerciseId
        LEFT JOIN set_entry e ON e.sessionExerciseId = se.id
        WHERE s.epochDay = :epochDay
        ORDER BY se.position, se.id, e.setIndex
        """
    )
    fun observeDay(epochDay: Long): Flow<List<DayRow>>

    @Query(
        """
        SELECT s.epochDay, s.id AS sessionId, se.id AS sessionExerciseId, se.slotId, se.plannedSets,
               (SELECT COUNT(*) FROM set_entry e WHERE e.sessionExerciseId = se.id AND e.completed = 1) AS completedSets
        FROM workout_session s
        JOIN session_exercise se ON se.sessionId = s.id
        """
    )
    fun observeStatusRows(): Flow<List<StatusRow>>

    @Query(
        """
        SELECT se.exerciseId, s.epochDay, se.id AS sessionExerciseId, e.setIndex, e.weightKg, e.reps
        FROM set_entry e
        JOIN session_exercise se ON se.id = e.sessionExerciseId
        JOIN workout_session s ON s.id = se.sessionId
        WHERE e.completed = 1 AND e.weightKg IS NOT NULL AND e.reps IS NOT NULL AND se.exerciseId IN (:exerciseIds)
        """
    )
    fun observeHistory(exerciseIds: List<Long>): Flow<List<HistoryRow>>

    @Query("SELECT MIN(epochDay) FROM workout_session")
    fun observeFirstSessionDay(): Flow<Long?>

    @Query("SELECT * FROM routine ORDER BY letter")
    fun observeRoutines(): Flow<List<Routine>>

    @Query("SELECT * FROM exercise ORDER BY name COLLATE NOCASE")
    fun observeExercises(): Flow<List<Exercise>>

    @Query("SELECT COUNT(*) FROM routine")
    suspend fun routineCount(): Int

    @Query("SELECT * FROM exercise WHERE id = :id")
    suspend fun getExercise(id: Long): Exercise

    @Query("SELECT * FROM exercise_slot WHERE routineId = :routineId ORDER BY position")
    suspend fun slotsOf(routineId: Long): List<ExerciseSlot>

    @Query("SELECT * FROM exercise_slot WHERE id = :id")
    suspend fun getSlot(id: Long): ExerciseSlot

    @Query("SELECT * FROM slot_variant WHERE slotId = :slotId ORDER BY position")
    suspend fun variantsOf(slotId: Long): List<SlotVariant>

    @Query("SELECT * FROM workout_session WHERE epochDay = :epochDay AND routineId = :routineId")
    suspend fun findSession(epochDay: Long, routineId: Long): WorkoutSession?

    @Query("SELECT * FROM session_exercise WHERE sessionId = :sessionId AND slotId = :slotId")
    suspend fun sessionRows(sessionId: Long, slotId: Long): List<SessionExercise>

    @Query("SELECT * FROM set_entry WHERE sessionExerciseId = :sessionExerciseId AND setIndex = :setIndex")
    suspend fun getSet(sessionExerciseId: Long, setIndex: Int): SetEntry?

    @Insert suspend fun insert(exercise: Exercise): Long
    @Insert suspend fun insert(routine: Routine): Long
    @Insert suspend fun insert(slot: ExerciseSlot): Long
    @Insert suspend fun insert(variant: SlotVariant): Long
    @Insert suspend fun insert(session: WorkoutSession): Long
    @Insert suspend fun insert(row: SessionExercise): Long

    @Update suspend fun update(exercise: Exercise)
    @Update suspend fun update(routine: Routine)
    @Update suspend fun update(slot: ExerciseSlot)
    @Update suspend fun update(variant: SlotVariant)

    @Upsert suspend fun upsert(entry: SetEntry)

    @Query("DELETE FROM set_entry WHERE sessionExerciseId = :sessionExerciseId AND setIndex = :setIndex")
    suspend fun deleteSet(sessionExerciseId: Long, setIndex: Int)

    @Query("DELETE FROM set_entry WHERE sessionExerciseId = :sessionExerciseId AND setIndex >= :fromIndex")
    suspend fun deleteSetsFrom(sessionExerciseId: Long, fromIndex: Int)

    @Query("UPDATE session_exercise SET plannedSets = :plannedSets WHERE sessionId = :sessionId AND slotId = :slotId")
    suspend fun setPlannedSets(sessionId: Long, slotId: Long, plannedSets: Int)

    @Query("DELETE FROM exercise_slot WHERE id = :id")
    suspend fun deleteSlot(id: Long)

    @Query("DELETE FROM slot_variant WHERE id = :id")
    suspend fun deleteVariant(id: Long)

    // Otherwise an untouched exercise removed mid-day would leave today permanently yellow.
    @Query(
        """
        DELETE FROM session_exercise WHERE slotId = :slotId
          AND sessionId IN (SELECT id FROM workout_session WHERE epochDay = :epochDay)
          AND id NOT IN (SELECT sessionExerciseId FROM set_entry WHERE completed = 1)
        """
    )
    suspend fun deleteUnloggedRows(slotId: Long, epochDay: Long)

    @Query("SELECT * FROM exercise") suspend fun allExercises(): List<Exercise>
    @Query("SELECT * FROM routine") suspend fun allRoutines(): List<Routine>
    @Query("SELECT * FROM exercise_slot") suspend fun allSlots(): List<ExerciseSlot>
    @Query("SELECT * FROM slot_variant") suspend fun allVariants(): List<SlotVariant>
    @Query("SELECT * FROM workout_session") suspend fun allSessions(): List<WorkoutSession>
    @Query("SELECT * FROM session_exercise") suspend fun allSessionExercises(): List<SessionExercise>
    @Query("SELECT * FROM set_entry") suspend fun allSets(): List<SetEntry>

    @Insert suspend fun insertExercises(items: List<Exercise>)
    @Insert suspend fun insertRoutines(items: List<Routine>)
    @Insert suspend fun insertSlots(items: List<ExerciseSlot>)
    @Insert suspend fun insertVariants(items: List<SlotVariant>)
    @Insert suspend fun insertSessions(items: List<WorkoutSession>)
    @Insert suspend fun insertSessionExercises(items: List<SessionExercise>)
    @Insert suspend fun insertSets(items: List<SetEntry>)

    @Query("DELETE FROM set_entry") suspend fun clearSets()
    @Query("DELETE FROM session_exercise") suspend fun clearSessionExercises()
    @Query("DELETE FROM workout_session") suspend fun clearSessions()
    @Query("DELETE FROM slot_variant") suspend fun clearVariants()
    @Query("DELETE FROM exercise_slot") suspend fun clearSlots()
    @Query("DELETE FROM routine") suspend fun clearRoutines()
    @Query("DELETE FROM exercise") suspend fun clearExercises()
}

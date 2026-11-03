package com.gympad.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "exercise", indices = [Index(value = ["name"], unique = true)])
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Serializable
@Entity(tableName = "routine", indices = [Index(value = ["letter"], unique = true)])
data class Routine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val letter: String,
    val name: String = "",
)

@Serializable
@Entity(
    tableName = "exercise_slot",
    foreignKeys = [
        ForeignKey(Routine::class, ["id"], ["routineId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("routineId")],
)
data class ExerciseSlot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineId: Long,
    val position: Int,
    val defaultSets: Int = 3,
)

@Serializable
@Entity(
    tableName = "slot_variant",
    foreignKeys = [
        ForeignKey(ExerciseSlot::class, ["id"], ["slotId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(Exercise::class, ["id"], ["exerciseId"]),
    ],
    indices = [Index("slotId"), Index("exerciseId")],
)
data class SlotVariant(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val slotId: Long,
    val exerciseId: Long,
    val position: Int,
)

@Serializable
@Entity(
    tableName = "workout_session",
    foreignKeys = [
        ForeignKey(Routine::class, ["id"], ["routineId"]),
    ],
    indices = [Index(value = ["epochDay", "routineId"], unique = true), Index("routineId")],
)
data class WorkoutSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val routineId: Long,
    val startedAt: Long,
)

/** Planned sets are copied from the template at creation, so routine edits can't change past days. */
@Serializable
@Entity(
    tableName = "session_exercise",
    foreignKeys = [
        ForeignKey(WorkoutSession::class, ["id"], ["sessionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ExerciseSlot::class, ["id"], ["slotId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(Exercise::class, ["id"], ["exerciseId"]),
    ],
    indices = [
        Index(value = ["sessionId", "slotId", "exerciseId"], unique = true),
        Index("slotId"),
        Index("exerciseId"),
    ],
)
data class SessionExercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val slotId: Long?,
    val exerciseId: Long,
    val position: Int,
    val plannedSets: Int,
)

@Serializable
@Entity(
    tableName = "set_entry",
    primaryKeys = ["sessionExerciseId", "setIndex"],
    foreignKeys = [
        ForeignKey(SessionExercise::class, ["id"], ["sessionExerciseId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class SetEntry(
    val sessionExerciseId: Long,
    val setIndex: Int,
    val weightKg: Double? = null,
    val reps: Int? = null,
    val completed: Boolean = false,
    val completedAt: Long? = null,
)

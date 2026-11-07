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

/**
 * Nutrition is per 100 g (or 100 ml for drinks, as labels give it). Products from the bundled
 * catalog are copied here when first logged, so the diary and backups never depend on the catalog.
 */
@Serializable
@Entity(tableName = "food", indices = [Index("barcode"), Index(value = ["catalogCode"], unique = true)])
data class Food(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Only basic catalog foods have a Polish name; product names stay as printed on the pack. */
    val namePl: String? = null,
    val brand: String? = null,
    val barcode: String? = null,
    val catalogCode: String? = null,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val portionGrams: Double? = null,
    val packageGrams: Double? = null,
    /** Hidden from search and recents; entries that use it keep showing it. */
    val archived: Boolean = false,
)

@Serializable
@Entity(
    tableName = "food_entry",
    foreignKeys = [ForeignKey(Food::class, ["id"], ["foodId"])],
    indices = [Index("epochDay"), Index("foodId")],
)
data class FoodEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val meal: Int,
    val foodId: Long,
    val grams: Double,
    val createdAt: Long,
)

/** A single row: daily targets, each optional. */
@Serializable
@Entity(tableName = "nutrition_goals")
data class NutritionGoals(
    @PrimaryKey val id: Int = 1,
    val kcal: Int? = null,
    val protein: Int? = null,
    val carbs: Int? = null,
    val fat: Int? = null,
)

/** One body measurement on one day; logging the same one again that day replaces it. */
@Serializable
@Entity(tableName = "measurement", indices = [Index(value = ["kind", "epochDay"], unique = true)])
data class Measurement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val epochDay: Long,
    val value: Double,
)

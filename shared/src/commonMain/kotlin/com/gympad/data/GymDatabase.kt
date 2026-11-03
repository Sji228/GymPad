package com.gympad.data

import androidx.room.AutoMigration
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

@Database(
    entities = [
        Exercise::class,
        Routine::class,
        ExerciseSlot::class,
        SlotVariant::class,
        WorkoutSession::class,
        SessionExercise::class,
        SetEntry::class,
    ],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2, spec = DropSlotLabels::class)],
)
@ConstructedBy(GymDatabaseConstructor::class)
abstract class GymDatabase : RoomDatabase() {
    abstract fun dao(): GymDao
}

@DeleteColumn(tableName = "exercise_slot", columnName = "label")
@DeleteColumn(tableName = "session_exercise", columnName = "exerciseName")
@DeleteColumn(tableName = "session_exercise", columnName = "slotLabel")
class DropSlotLabels : AutoMigrationSpec

@Suppress("KotlinNoActualForExpect")
expect object GymDatabaseConstructor : RoomDatabaseConstructor<GymDatabase> {
    override fun initialize(): GymDatabase
}

fun RoomDatabase.Builder<GymDatabase>.buildGymDatabase(): GymDatabase =
    setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

package com.gympad

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.gympad.data.GymDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.io.path.Path

class MigrationTest {
    @get:Rule val tempFolder = TemporaryFolder()

    @Test fun `version 1 data survives dropping the slot labels`() {
        val helper = MigrationTestHelper(
            schemaDirectoryPath = Path("schemas"),
            databasePath = Path(tempFolder.root.absolutePath, "migration.db"),
            driver = BundledSQLiteDriver(),
            databaseClass = GymDatabase::class,
        )
        helper.createDatabase(1).apply {
            execSQL("INSERT INTO exercise (id, name) VALUES (1, 'Bench')")
            execSQL("INSERT INTO routine (id, letter, name) VALUES (1, 'A', '')")
            execSQL("INSERT INTO exercise_slot (id, routineId, position, label, defaultSets) VALUES (1, 1, 0, 'Press', 4)")
            execSQL("INSERT INTO slot_variant (id, slotId, exerciseId, position) VALUES (1, 1, 1, 0)")
            execSQL("INSERT INTO workout_session (id, epochDay, routineId, startedAt) VALUES (1, 100, 1, 0)")
            execSQL(
                "INSERT INTO session_exercise (id, sessionId, slotId, exerciseId, exerciseName, slotLabel, position, plannedSets) " +
                    "VALUES (1, 1, 1, 1, 'Bench', 'Press', 0, 4)",
            )
            execSQL("INSERT INTO set_entry (sessionExerciseId, setIndex, weightKg, reps, completed) VALUES (1, 0, 80.0, 8, 1)")
            close()
        }

        val migrated = helper.runMigrationsAndValidate(2)
        migrated.prepare("SELECT weightKg, reps FROM set_entry").use { statement ->
            statement.step()
            assertEquals(80.0, statement.getDouble(0), 0.0)
            assertEquals(8, statement.getInt(1))
        }
        migrated.prepare("SELECT defaultSets FROM exercise_slot").use { statement ->
            statement.step()
            assertEquals(4, statement.getInt(0))
        }
        migrated.prepare("SELECT plannedSets, exerciseId FROM session_exercise").use { statement ->
            statement.step()
            assertEquals(4, statement.getInt(0))
            assertEquals(1, statement.getInt(1))
        }
        migrated.close()
    }
}

package com.gympad.data

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class EntryRow(
    val entryId: Long,
    val meal: Int,
    val grams: Double,
    @Embedded val food: Food,
)

data class DayEnergyRow(val epochDay: Long, val kcal: Double)

data class RecentFoodRow(
    @Embedded val food: Food,
    val lastGrams: Double,
)

@Dao
interface FoodDao {
    @Query(
        """
        SELECT e.id AS entryId, e.meal, e.grams, f.*
        FROM food_entry e JOIN food f ON f.id = e.foodId
        WHERE e.epochDay = :epochDay
        ORDER BY e.meal, e.createdAt, e.id
        """
    )
    fun observeEntries(epochDay: Long): Flow<List<EntryRow>>

    @Query(
        """
        SELECT e.epochDay, SUM(e.grams * f.kcal / 100.0) AS kcal
        FROM food_entry e JOIN food f ON f.id = e.foodId
        GROUP BY e.epochDay
        """
    )
    fun observeDayEnergy(): Flow<List<DayEnergyRow>>

    @Query("SELECT MIN(epochDay) FROM food_entry")
    fun observeFirstEntryDay(): Flow<Long?>

    @Query("SELECT * FROM nutrition_goals WHERE id = 1")
    fun observeGoals(): Flow<NutritionGoals?>

    /** Each food once, newest first, with the amount it was last logged in. */
    @Query(
        """
        SELECT f.*, e.grams AS lastGrams
        FROM food_entry e JOIN food f ON f.id = e.foodId
        WHERE f.archived = 0 AND e.id = (SELECT id FROM food_entry WHERE foodId = f.id ORDER BY createdAt DESC, id DESC LIMIT 1)
        ORDER BY e.createdAt DESC
        LIMIT :limit
        """
    )
    fun observeRecentFoods(limit: Int): Flow<List<RecentFoodRow>>

    @Query("SELECT * FROM food WHERE archived = 0 ORDER BY name COLLATE NOCASE")
    fun observeFoods(): Flow<List<Food>>

    @Query("SELECT * FROM food WHERE barcode IN (:codes) AND archived = 0 ORDER BY catalogCode IS NOT NULL LIMIT 1")
    suspend fun findByBarcode(codes: List<String>): Food?

    @Query("SELECT * FROM food WHERE catalogCode = :code")
    suspend fun findByCatalogCode(code: String): Food?

    @Query("SELECT * FROM food WHERE id = :id")
    suspend fun getFood(id: Long): Food

    @Query("SELECT grams FROM food_entry WHERE foodId = :foodId ORDER BY createdAt DESC, id DESC LIMIT 1")
    suspend fun lastGrams(foodId: Long): Double?

    @Query("SELECT * FROM food_entry WHERE id = :id")
    suspend fun getEntry(id: Long): FoodEntry?

    @Query("SELECT * FROM food_entry WHERE epochDay = :epochDay AND meal = :meal ORDER BY createdAt, id")
    suspend fun entriesOf(epochDay: Long, meal: Int): List<FoodEntry>

    @Insert suspend fun insert(food: Food): Long
    @Insert suspend fun insert(entry: FoodEntry): Long
    @Update suspend fun update(food: Food)
    @Update suspend fun update(entry: FoodEntry)
    @Upsert suspend fun upsert(goals: NutritionGoals)

    @Query("DELETE FROM food_entry WHERE id = :id")
    suspend fun deleteEntry(id: Long)

    @Query("SELECT * FROM food") suspend fun allFoods(): List<Food>
    @Query("SELECT * FROM food_entry") suspend fun allEntries(): List<FoodEntry>
    @Query("SELECT * FROM nutrition_goals WHERE id = 1") suspend fun goals(): NutritionGoals?

    @Insert suspend fun insertFoods(items: List<Food>)
    @Insert suspend fun insertEntries(items: List<FoodEntry>)

    @Query("DELETE FROM food_entry") suspend fun clearEntries()
    @Query("DELETE FROM food") suspend fun clearFoods()
    @Query("DELETE FROM nutrition_goals") suspend fun clearGoals()
}

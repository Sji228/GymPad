package com.gympad.data

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.gympad.domain.FoodItem
import com.gympad.domain.Meal
import com.gympad.domain.barcodeVariants
import com.gympad.domain.looksLikeBarcode
import com.gympad.domain.matches
import com.gympad.domain.searchWords
import com.gympad.domain.toItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/** What the user types when creating or fixing their own food. Values are per 100 g. */
data class FoodDraft(
    val name: String,
    val brand: String?,
    val barcode: String?,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val portionGrams: Double?,
)

/** Shown where a brand would be, so an estimate is never mistaken for label data. */
const val AI_BRAND = "Gemini"

class FoodRepository(private val db: GymDatabase, private val catalog: FoodCatalog) {
    private val dao = db.foodDao()
    private val writeLock = Mutex()

    private suspend fun <T> write(block: suspend () -> T): T =
        writeLock.withLock { db.useWriterConnection { it.immediateTransaction { block() } } }

    fun observeEntries(epochDay: Long) = dao.observeEntries(epochDay)
    fun observeDayEnergy() = dao.observeDayEnergy()
    fun observeFirstEntryDay() = dao.observeFirstEntryDay()
    fun observeGoals(): Flow<NutritionGoals> = dao.observeGoals().map { it ?: NutritionGoals() }

    /** The food history shown when adding: each food once, newest first. */
    fun observeRecent(limit: Int = 100): Flow<List<FoodItem>> =
        dao.observeRecentFoods(limit).map { rows -> rows.map { it.food.toItem(it.lastGrams) } }

    fun observeOwnFoods(): Flow<List<FoodItem>> =
        dao.observeFoods().map { foods -> foods.filter { it.catalogCode == null }.map { it.toItem() } }

    /** Foods already in the diary come first, so a product logged before keeps its remembered amount. */
    suspend fun search(query: String): List<FoodItem> {
        if (looksLikeBarcode(query)) return listOfNotNull(findBarcode(query))
        val words = searchWords(query)
        if (words.isEmpty()) return emptyList()
        val own = dao.observeFoods().first().map { it.toItem() }.filter { it.matches(words) }
        val ownCodes = own.mapNotNull { it.catalogCode }.toSet()
        val fromCatalog = catalog.search(words).filter { it.catalogCode !in ownCodes }
        return (own.withLastGrams() + fromCatalog).take(60)
    }

    suspend fun findBarcode(code: String): FoodItem? {
        dao.findByBarcode(barcodeVariants(code))?.let { return listOf(it.toItem()).withLastGrams().first() }
        val product = catalog.findBarcode(code) ?: return null
        return dao.findByCatalogCode(product.catalogCode!!)?.let { listOf(it.toItem()).withLastGrams().first() } ?: product
    }

    private suspend fun List<FoodItem>.withLastGrams(): List<FoodItem> =
        map { item -> item.foodId?.let { item.copy(lastGrams = dao.lastGrams(it)) } ?: item }

    private suspend fun foodIdFor(item: FoodItem): Long {
        item.foodId?.let { return it }
        val code = requireNotNull(item.catalogCode)
        dao.findByCatalogCode(code)?.let { existing ->
            if (existing.archived) dao.update(existing.copy(archived = false))
            return existing.id
        }
        return dao.insert(
            Food(
                name = item.name,
                namePl = item.namePl,
                brand = item.brand,
                barcode = item.barcode,
                catalogCode = code,
                kcal = item.per100.kcal,
                protein = item.per100.protein,
                carbs = item.per100.carbs,
                fat = item.per100.fat,
                portionGrams = item.portionGrams,
                packageGrams = item.packageGrams,
            ),
        )
    }

    suspend fun addEntry(epochDay: Long, meal: Meal, item: FoodItem, grams: Double) = write {
        dao.insert(
            FoodEntry(
                epochDay = epochDay,
                meal = meal.code,
                foodId = foodIdFor(item),
                grams = grams,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    /**
     * Logs foods estimated from a photo. Each becomes its own food, so it shows up in recents for a
     * one-tap repeat; the "ai:" code keeps them out of My foods, which is for foods typed in by hand.
     */
    suspend fun addEstimated(epochDay: Long, meal: Meal, foods: List<Pair<EstimatedFood, Double>>) = write {
        val now = System.currentTimeMillis()
        foods.forEachIndexed { i, (food, grams) ->
            val per100 = 100.0 / food.grams
            val foodId = dao.insert(
                Food(
                    name = food.name,
                    brand = AI_BRAND,
                    catalogCode = "ai:${UUID.randomUUID()}",
                    kcal = food.kcal * per100,
                    protein = food.protein * per100,
                    carbs = food.carbs * per100,
                    fat = food.fat * per100,
                    portionGrams = food.grams,
                ),
            )
            dao.insert(FoodEntry(epochDay = epochDay, meal = meal.code, foodId = foodId, grams = grams, createdAt = now + i))
        }
    }

    suspend fun updateEntry(entryId: Long, grams: Double, meal: Meal) = write {
        val entry = dao.getEntry(entryId) ?: return@write
        dao.update(entry.copy(grams = grams, meal = meal.code))
    }

    suspend fun deleteEntry(entryId: Long) = write { dao.deleteEntry(entryId) }

    /** Brings back an entry removed by mistake, with its original id and place in the meal. */
    suspend fun restoreEntry(entry: FoodEntry) = write { dao.insert(entry) }

    suspend fun entry(entryId: Long): FoodEntry? = dao.getEntry(entryId)

    /** Copies another day's meal, for the breakfast that's the same every morning. */
    suspend fun copyMeal(fromDay: Long, fromMeal: Meal, toDay: Long, toMeal: Meal) = write {
        val now = System.currentTimeMillis()
        dao.entriesOf(fromDay, fromMeal.code).forEachIndexed { i, entry ->
            dao.insert(entry.copy(id = 0, epochDay = toDay, meal = toMeal.code, createdAt = now + i))
        }
    }

    suspend fun mealEntryCount(epochDay: Long, meal: Meal): Int = dao.entriesOf(epochDay, meal.code).size

    suspend fun createFood(draft: FoodDraft): FoodItem = write {
        val food = draft.toFood()
        food.copy(id = dao.insert(food)).toItem()
    }

    /** Fixing a food's values also fixes every day it was logged on, which is what a typo fix should do. */
    suspend fun updateFood(foodId: Long, draft: FoodDraft) = write {
        val food = dao.getFood(foodId)
        dao.update(draft.toFood().copy(id = food.id, namePl = food.namePl, catalogCode = food.catalogCode))
    }

    suspend fun archiveFood(foodId: Long) = write {
        dao.update(dao.getFood(foodId).copy(archived = true))
    }

    suspend fun setGoals(goals: NutritionGoals) = write { dao.upsert(goals.copy(id = 1)) }

    private fun FoodDraft.toFood() = Food(
        name = name.trim(),
        brand = brand?.trim()?.ifEmpty { null },
        barcode = barcode?.trim()?.ifEmpty { null },
        kcal = kcal,
        protein = protein,
        carbs = carbs,
        fat = fat,
        portionGrams = portionGrams,
    )
}

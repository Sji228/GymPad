package com.gympad.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gympad.data.EntryRow
import com.gympad.data.FoodDraft
import com.gympad.data.FoodEntry
import com.gympad.data.FoodRepository
import com.gympad.data.NutritionGoals
import com.gympad.domain.DayStatus
import com.gympad.domain.FoodItem
import com.gympad.domain.Macros
import com.gympad.domain.Meal
import com.gympad.domain.foodDayStatuses
import com.gympad.domain.toItem
import com.gympad.domain.total
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

data class FoodEntryUi(val entryId: Long, val meal: Meal, val grams: Double, val item: FoodItem, val macros: Macros)

data class MealUi(val meal: Meal, val entries: List<FoodEntryUi>, val totals: Macros)

data class FoodUiState(
    val today: Long,
    val selectedDay: Long,
    val firstDay: Long,
    val statuses: Map<Long, DayStatus> = emptyMap(),
    val meals: List<MealUi> = Meal.entries.map { MealUi(it, emptyList(), Macros.ZERO) },
    val totals: Macros = Macros.ZERO,
    val goals: NutritionGoals = NutritionGoals(),
) {
    /** The ribbon and calendar reach a year ahead, for planning meals. */
    val lastDay: Long get() = today + 365
}

/** Search results for the add-food sheet. Null query means the sheet shows recents instead. */
data class FoodSearchState(
    val query: String = "",
    val results: List<FoodItem> = emptyList(),
    val searching: Boolean = false,
)

/** A scanned or typed barcode: found → straight to the amount, missing → offer to create the food. */
sealed interface BarcodeResult {
    data class Found(val item: FoodItem) : BarcodeResult
    data class Missing(val code: String) : BarcodeResult
}

/** The last removed entry, so a slip of the finger can be undone. */
data class RemovedEntry(val entry: FoodEntry, val item: FoodItem)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class FoodViewModel(
    private val repository: FoodRepository,
    private val installDay: Long,
) : ViewModel() {
    private val today = MutableStateFlow(LocalDate.now().toEpochDay())
    private val selectedDay = MutableStateFlow(today.value)
    private val entries = selectedDay.flatMapLatest(repository::observeEntries)

    val uiState: StateFlow<FoodUiState> = combine(
        combine(today, selectedDay, ::Pair),
        entries,
        repository.observeDayEnergy(),
        repository.observeGoals(),
        repository.observeFirstEntryDay(),
    ) { (today, day), rows, energy, goals, firstEntry ->
        val entries = rows.map(::toEntryUi)
        val meals = Meal.entries.map { meal ->
            val mealEntries = entries.filter { it.meal == meal }
            MealUi(meal, mealEntries, mealEntries.map { it.macros }.total())
        }
        FoodUiState(
            today = today,
            selectedDay = day,
            firstDay = minOf(installDay, firstEntry ?: installDay, today, day),
            statuses = foodDayStatuses(energy, goals),
            meals = meals,
            totals = entries.map { it.macros }.total(),
            goals = goals,
        )
    }.flowOn(Dispatchers.Default).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        FoodUiState(today.value, today.value, minOf(installDay, today.value)),
    )

    val recent: StateFlow<List<FoodItem>> =
        repository.observeRecent().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val ownFoods: StateFlow<List<FoodItem>> =
        repository.observeOwnFoods().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val query = MutableStateFlow("")
    private val _search = MutableStateFlow(FoodSearchState())
    val search: StateFlow<FoodSearchState> = _search

    private val _removed = MutableStateFlow<RemovedEntry?>(null)
    val removed: StateFlow<RemovedEntry?> = _removed

    init {
        viewModelScope.launch {
            query.mapLatest { text ->
                if (text.isBlank()) {
                    FoodSearchState()
                } else {
                    // Waits for a pause in typing, so the catalog isn't scanned for every letter.
                    _search.value = _search.value.copy(query = text, searching = true)
                    delay(150)
                    FoodSearchState(text, repository.search(text))
                }
            }.collect { _search.value = it }
        }
        viewModelScope.launch {
            while (true) {
                val now = LocalDateTime.now()
                delay(Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay()).toMillis() + 1_000)
                refreshToday()
            }
        }
    }

    private fun toEntryUi(row: EntryRow): FoodEntryUi {
        val item = row.food.toItem()
        return FoodEntryUi(row.entryId, Meal.of(row.meal), row.grams, item, item.macrosFor(row.grams))
    }

    fun refreshToday() {
        val newToday = LocalDate.now().toEpochDay()
        val oldToday = today.value
        if (newToday == oldToday) return
        today.value = newToday
        if (selectedDay.value == oldToday) selectedDay.value = newToday
    }

    fun selectDay(epochDay: Long) {
        selectedDay.value = epochDay
        _removed.value = null
    }

    fun setQuery(text: String) {
        query.value = text
        if (text.isBlank()) _search.value = FoodSearchState()
    }

    suspend fun lookUpBarcode(code: String): BarcodeResult {
        val trimmed = code.trim()
        return repository.findBarcode(trimmed)?.let { BarcodeResult.Found(it) } ?: BarcodeResult.Missing(trimmed)
    }

    fun addEntry(meal: Meal, item: FoodItem, grams: Double) {
        val day = selectedDay.value
        viewModelScope.launch { repository.addEntry(day, meal, item, grams) }
    }

    fun updateEntry(entryId: Long, grams: Double, meal: Meal) {
        viewModelScope.launch { repository.updateEntry(entryId, grams, meal) }
    }

    fun deleteEntry(entry: FoodEntryUi) {
        viewModelScope.launch {
            val saved = repository.entry(entry.entryId) ?: return@launch
            repository.deleteEntry(entry.entryId)
            _removed.value = RemovedEntry(saved, entry.item)
        }
    }

    fun undoDelete() {
        val removed = _removed.value ?: return
        _removed.value = null
        viewModelScope.launch { repository.restoreEntry(removed.entry) }
    }

    fun dismissUndo() {
        _removed.value = null
    }

    /** The most recent earlier day that has this meal, or null when there is none to copy. */
    suspend fun previousMealDay(meal: Meal): Long? {
        val day = selectedDay.value
        for (back in 1..14) {
            if (repository.mealEntryCount(day - back, meal) > 0) return day - back
        }
        return null
    }

    fun copyMeal(fromDay: Long, meal: Meal) {
        val day = selectedDay.value
        viewModelScope.launch { repository.copyMeal(fromDay, meal, day, meal) }
    }

    suspend fun createFood(draft: FoodDraft): FoodItem = repository.createFood(draft)

    fun updateFood(foodId: Long, draft: FoodDraft) {
        viewModelScope.launch { repository.updateFood(foodId, draft) }
    }

    fun archiveFood(foodId: Long) {
        viewModelScope.launch { repository.archiveFood(foodId) }
    }

    fun setGoals(goals: NutritionGoals) {
        viewModelScope.launch { repository.setGoals(goals) }
    }
}

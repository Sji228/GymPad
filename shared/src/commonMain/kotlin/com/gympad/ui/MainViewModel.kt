package com.gympad.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gympad.data.DayRow
import com.gympad.data.GymRepository
import com.gympad.data.HistoryRow
import com.gympad.data.Routine
import com.gympad.data.SetTarget
import com.gympad.domain.DayStatus
import com.gympad.domain.ExerciseCardUi
import com.gympad.domain.SetRowUi
import com.gympad.domain.TemplateSlot
import com.gympad.domain.dayStatuses
import com.gympad.domain.editableCards
import com.gympad.domain.readOnlyCards
import com.gympad.domain.toTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

data class MainUiState(
    val today: Long,
    val selectedDay: Long,
    val firstDay: Long,
    val statuses: Map<Long, DayStatus> = emptyMap(),
    val routines: List<Routine> = emptyList(),
    val routineId: Long? = null,
    val loggedRoutineIds: Set<Long> = emptySet(),
    val cards: List<ExerciseCardUi> = emptyList(),
) {
    val isReadOnly: Boolean get() = selectedDay != today

    val days: DayRange get() = DayRange(firstDay, today, today, selectedDay, statuses)
}

private data class Selection(
    val today: Long,
    val day: Long,
    val explicitRoutineId: Long?,
    val chosenVariants: Map<Long, Long>,
)

private data class Content(
    val routines: List<Routine>,
    val slots: List<TemplateSlot>,
    val dayRows: List<DayRow>,
    val history: List<HistoryRow>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val repository: GymRepository,
    private val installDay: Long,
) : ViewModel() {
    private val today = MutableStateFlow(LocalDate.now().toEpochDay())
    private val selectedDay = MutableStateFlow(today.value)
    private val explicitRoutineId = MutableStateFlow<Long?>(null)
    private val chosenVariants = MutableStateFlow<Map<Long, Long>>(emptyMap())

    private val selection = combine(today, selectedDay, explicitRoutineId, chosenVariants, ::Selection)
    private val slots = repository.observeTemplate().map(::toTemplate)
    private val dayRows = selectedDay.flatMapLatest(repository::observeDay)

    private val history = combine(slots, dayRows) { slots, rows ->
        (slots.flatMap { slot -> slot.variants.map { it.exerciseId } } + rows.map { it.exerciseId }).distinct().sorted()
    }.distinctUntilChanged().flatMapLatest { ids ->
        if (ids.isEmpty()) flowOf(emptyList()) else repository.observeHistory(ids)
    }

    private val content = combine(repository.observeRoutines(), slots, dayRows, history, ::Content)
    private val statuses = repository.observeStatusRows().map(::dayStatuses)

    val uiState: StateFlow<MainUiState> = combine(
        selection,
        content,
        statuses,
        repository.observeFirstSessionDay(),
    ) { selection, content, statuses, firstSessionDay ->
        val routineId = resolveRoutine(selection, content)
        val routineRows = content.dayRows.filter { it.routineId == routineId }
        val cards = if (selection.day == selection.today) {
            editableCards(
                slots = content.slots.filter { it.routineId == routineId },
                dayRows = routineRows,
                history = content.history,
                chosenVariants = selection.chosenVariants,
                epochDay = selection.day,
            )
        } else {
            val routineLogged = routineRows.any { it.completed == true }
            if (routineLogged) readOnlyCards(routineRows, content.history, selection.day) else emptyList()
        }
        MainUiState(
            today = selection.today,
            selectedDay = selection.day,
            firstDay = minOf(installDay, firstSessionDay ?: installDay, selection.today),
            statuses = statuses,
            routines = content.routines,
            routineId = routineId,
            loggedRoutineIds = content.dayRows.filter { it.completed == true }.map { it.routineId }.toSet(),
            cards = cards,
        )
    }.flowOn(Dispatchers.Default).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        MainUiState(today.value, today.value, minOf(installDay, today.value)),
    )

    private fun resolveRoutine(selection: Selection, content: Content): Long? =
        selection.explicitRoutineId
            ?: content.dayRows.firstOrNull { it.completed == true }?.routineId
            ?: content.dayRows.firstOrNull { it.setIndex != null }?.routineId
            ?: content.routines.firstOrNull()?.id

    init {
        viewModelScope.launch {
            while (true) {
                val now = LocalDateTime.now()
                val midnight = now.toLocalDate().plusDays(1).atStartOfDay()
                delay(Duration.between(now, midnight).toMillis() + 1_000)
                refreshToday()
            }
        }
    }

    fun refreshToday() {
        val newToday = LocalDate.now().toEpochDay()
        val oldToday = today.value
        if (newToday == oldToday) return
        today.value = newToday
        if (selectedDay.value == oldToday) selectDay(newToday)
    }

    fun selectDay(epochDay: Long) {
        selectedDay.value = epochDay
        explicitRoutineId.value = null
        chosenVariants.value = emptyMap()
    }

    fun selectRoutine(routineId: Long) {
        explicitRoutineId.value = routineId
    }

    fun selectVariant(slotId: Long, exerciseId: Long) {
        chosenVariants.update { it + (slotId to exerciseId) }
    }

    private fun target(card: ExerciseCardUi): SetTarget? {
        val state = uiState.value
        if (state.isReadOnly) return null
        return SetTarget(state.today, state.routineId ?: return null, card.slotId ?: return null, card.exerciseId)
    }

    fun setWeight(card: ExerciseCardUi, index: Int, weightKg: Double?) {
        val target = target(card) ?: return
        viewModelScope.launch { repository.editSet(target, index) { it.copy(weightKg = weightKg) } }
    }

    fun setReps(card: ExerciseCardUi, index: Int, reps: Int?) {
        val target = target(card) ?: return
        viewModelScope.launch { repository.editSet(target, index) { it.copy(reps = reps) } }
    }

    /** Falls back to ghosts only for fields still empty in the database, so a just-typed value always wins. */
    fun toggleSet(card: ExerciseCardUi, row: SetRowUi) {
        val target = target(card) ?: return
        viewModelScope.launch {
            repository.editSet(target, row.index) { entry ->
                if (entry.completed) {
                    entry.copy(completed = false, completedAt = null)
                } else {
                    entry.copy(
                        weightKg = entry.weightKg ?: row.ghost.weightKg ?: 0.0,
                        reps = entry.reps ?: row.ghost.reps,
                        completed = true,
                        completedAt = System.currentTimeMillis(),
                    )
                }
            }
        }
    }

    fun changeSetCount(card: ExerciseCardUi, delta: Int) {
        val target = target(card) ?: return
        val count = (card.sets.size + delta).coerceAtLeast(1)
        viewModelScope.launch { repository.changePlannedSets(target, count) }
    }
}

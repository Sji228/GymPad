package com.gympad.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gympad.data.Exercise
import com.gympad.data.GymRepository
import com.gympad.data.Routine
import com.gympad.domain.TemplateSlot
import com.gympad.domain.toTemplate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class EditorUiState(
    val routines: List<Routine> = emptyList(),
    val routine: Routine? = null,
    val allSlots: List<TemplateSlot> = emptyList(),
    val exercises: List<Exercise> = emptyList(),
) {
    val slots: List<TemplateSlot> get() = allSlots.filter { it.routineId == routine?.id }
}

class EditorViewModel(private val repository: GymRepository) : ViewModel() {
    private val selectedId = MutableStateFlow<Long?>(null)

    val uiState: StateFlow<EditorUiState> = combine(
        repository.observeRoutines(),
        repository.observeTemplate(),
        repository.observeExercises(),
        selectedId,
    ) { routines, templateRows, exercises, id ->
        EditorUiState(
            routines = routines,
            routine = routines.firstOrNull { it.id == id } ?: routines.firstOrNull(),
            allSlots = toTemplate(templateRows),
            exercises = exercises,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EditorUiState())

    private fun launch(block: suspend GymRepository.() -> Unit) {
        viewModelScope.launch { repository.block() }
    }

    fun selectRoutine(id: Long?) { selectedId.value = id }
    fun renameRoutine(routine: Routine, name: String) = launch { updateRoutineName(routine, name) }
    fun addSlot(routineId: Long, name: String) = launch { addSlot(routineId, name) }
    fun setDefaultSets(slotId: Long, sets: Int) = launch { setDefaultSets(slotId, sets) }
    fun moveSlot(slotId: Long, delta: Int) = launch { moveSlot(slotId, delta) }
    fun deleteSlot(slotId: Long) = launch { deleteSlot(slotId, LocalDate.now().toEpochDay()) }
    fun addVariant(slotId: Long, name: String) = launch { addVariant(slotId, name) }
    fun makeVariantMain(slotId: Long, variantId: Long) = launch { makeVariantMain(slotId, variantId) }
    fun removeVariant(variantId: Long) = launch { removeVariant(variantId) }
    fun pointVariantAt(slotId: Long, variantId: Long, name: String) = launch { pointVariantAt(slotId, variantId, name) }
    fun renameExercise(exerciseId: Long, name: String) = launch { renameExercise(exerciseId, name) }
}

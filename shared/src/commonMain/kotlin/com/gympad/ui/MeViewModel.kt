package com.gympad.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gympad.data.GymRepository
import com.gympad.domain.BodyMeasure
import com.gympad.domain.BodyPoint
import com.gympad.domain.bodyPoints
import com.gympad.domain.latestHeight
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class MeUiState(
    val selected: BodyMeasure = BodyMeasure.WEIGHT,
    val points: List<BodyPoint> = emptyList(),
    val height: Double? = null,
    /** Last value of each measure, to prefill the next entry like a ghost value. */
    val latest: Map<BodyMeasure, Double> = emptyMap(),
    val today: Long = LocalDate.now().toEpochDay(),
)

class MeViewModel(private val repository: GymRepository) : ViewModel() {
    private val selected = MutableStateFlow(BodyMeasure.WEIGHT)

    val uiState: StateFlow<MeUiState> = combine(selected, repository.observeMeasurements()) { measure, all ->
        MeUiState(
            selected = measure,
            points = bodyPoints(all, measure),
            height = latestHeight(all),
            latest = BodyMeasure.entries.mapNotNull { m -> bodyPoints(all, m).lastOrNull()?.let { m to it.value } }.toMap(),
            today = LocalDate.now().toEpochDay(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MeUiState())

    fun select(measure: BodyMeasure) {
        selected.value = measure
    }

    fun save(measure: BodyMeasure, epochDay: Long, value: Double, replacingId: Long? = null) {
        viewModelScope.launch { repository.saveMeasurement(measure.code, epochDay, value, replacingId) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.deleteMeasurement(id) }
    }
}

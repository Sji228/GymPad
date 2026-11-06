package com.gympad.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gympad.data.AiError
import com.gympad.data.AiException
import com.gympad.data.EstimatedFood
import com.gympad.data.FoodRepository
import com.gympad.data.MealAnalyzer
import com.gympad.domain.AppLanguage
import com.gympad.domain.Meal
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Where the photo of one meal is: written about, being estimated, waiting for review, or failed. */
sealed interface PhotoStep {
    val meal: Meal
    val photo: ByteArray

    data class Note(override val meal: Meal, override val photo: ByteArray, val text: String = "") : PhotoStep
    data class Analyzing(override val meal: Meal, override val photo: ByteArray, val text: String) : PhotoStep
    data class Review(
        override val meal: Meal,
        override val photo: ByteArray,
        val text: String,
        val foods: List<EstimatedFood>,
        val note: String?,
    ) : PhotoStep
    data class Failed(override val meal: Meal, override val photo: ByteArray, val text: String, val error: AiError) : PhotoStep
}

data class KeySetupState(val checking: Boolean = false, val error: AiError? = null)

class MealPhotoViewModel(
    private val analyzer: MealAnalyzer,
    private val foods: FoodRepository,
) : ViewModel() {
    private val _step = MutableStateFlow<PhotoStep?>(null)
    val step: StateFlow<PhotoStep?> = _step

    private val _keySetup = MutableStateFlow(KeySetupState())
    val keySetup: StateFlow<KeySetupState> = _keySetup

    /** The meal the camera is open for. */
    private val _camera = MutableStateFlow<Meal?>(null)
    val camera: StateFlow<Meal?> = _camera

    private val _setupOpen = MutableStateFlow(false)
    val setupOpen: StateFlow<Boolean> = _setupOpen

    // A camera tap without a key opens the guide first, then carries on to the camera.
    private var pendingMeal: Meal? = null
    private var analysis: Job? = null

    fun maskedKey(): String? = analyzer.maskedKey()

    fun requestPhoto(meal: Meal) {
        if (analyzer.hasKey()) {
            _camera.value = meal
        } else {
            pendingMeal = meal
            _setupOpen.value = true
        }
    }

    fun openSetup() {
        pendingMeal = null
        _keySetup.value = KeySetupState()
        _setupOpen.value = true
    }

    fun closeSetup() {
        pendingMeal = null
        _keySetup.value = KeySetupState()
        _setupOpen.value = false
    }

    fun saveKey(key: String) {
        if (key.isBlank()) return
        _keySetup.value = KeySetupState(checking = true)
        viewModelScope.launch {
            _keySetup.value = try {
                analyzer.saveKey(key)
                _setupOpen.value = false
                pendingMeal?.let { _camera.value = it }
                pendingMeal = null
                KeySetupState()
            } catch (e: AiException) {
                KeySetupState(error = e.error)
            }
        }
    }

    fun removeKey() {
        analyzer.removeKey()
        _keySetup.value = KeySetupState()
    }

    fun photoTaken(jpeg: ByteArray) {
        val meal = _camera.value ?: return
        _camera.value = null
        _step.value = PhotoStep.Note(meal, jpeg)
    }

    fun closeCamera() {
        _camera.value = null
    }

    fun retake() {
        val meal = _step.value?.meal ?: return
        close()
        _camera.value = meal
    }

    /** From a failed estimate: fix the key, then try the same photo again. */
    fun changeKey() {
        _keySetup.value = KeySetupState()
        _setupOpen.value = true
    }

    /** Skipping the note sends the photo alone; Gemini then goes by what it sees. */
    fun analyze(text: String, language: AppLanguage) {
        val current = _step.value ?: return
        _step.value = PhotoStep.Analyzing(current.meal, current.photo, text)
        analysis?.cancel()
        analysis = viewModelScope.launch {
            _step.value = try {
                val estimate = analyzer.analyze(current.photo, text, language)
                PhotoStep.Review(current.meal, current.photo, text, estimate.foods, estimate.note)
            } catch (e: AiException) {
                PhotoStep.Failed(current.meal, current.photo, text, e.error)
            }
        }
    }

    fun editNote() {
        val current = _step.value ?: return
        val text = when (current) {
            is PhotoStep.Note -> current.text
            is PhotoStep.Analyzing -> current.text
            is PhotoStep.Review -> current.text
            is PhotoStep.Failed -> current.text
        }
        analysis?.cancel()
        _step.value = PhotoStep.Note(current.meal, current.photo, text)
    }

    fun add(epochDay: Long, chosen: List<Pair<EstimatedFood, Double>>) {
        val current = _step.value ?: return
        _step.value = null
        if (chosen.isEmpty()) return
        viewModelScope.launch { foods.addEstimated(epochDay, current.meal, chosen) }
    }

    fun close() {
        analysis?.cancel()
        _step.value = null
    }
}

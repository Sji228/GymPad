package com.gympad.ui.strings

import androidx.compose.runtime.staticCompositionLocalOf
import com.gympad.data.AiError
import com.gympad.data.Routine
import com.gympad.domain.AppLanguage
import com.gympad.domain.BuiltInNames
import com.gympad.domain.LiftValue
import com.gympad.domain.FoodItem
import com.gympad.domain.Meal
import com.gympad.domain.displayName
import com.gympad.domain.formatAmount
import com.gympad.domain.formatWeight
import java.time.LocalDate

val LocalStrings = staticCompositionLocalOf<AppStrings> { EnglishStrings }

val AppLanguage.strings: AppStrings
    get() = when (this) {
        AppLanguage.ENGLISH -> EnglishStrings
        AppLanguage.POLISH -> PolishStrings
    }

/**
 * Every text in the app. A plain interface rather than resource files: a missing translation is a
 * compile error, plurals are ordinary code, and switching language needs no restart.
 */
interface AppStrings {
    val language: AppLanguage
    val decimalSeparator: Char
    val bodyweight: String

    /** Monday first, like the ribbon. */
    val weekdayNames: List<String>
    val weekdayLetters: List<String>
    val monthNames: List<String>

    /** Polish changes a month's form inside a date: "listopad" but "1 listopada". */
    val monthNamesInDate: List<String>
    val monthNamesShort: List<String>

    val today: String
    val cancel: String
    val back: String
    val menu: String
    val readOnly: String
    val routines: String
    val backup: String
    val languageLabel: String
    val history: String
    val openHistory: String
    val closeHistory: String
    val legendComplete: String
    val legendIncomplete: String
    val legendRest: String
    fun emptyRoutineTitle(routine: String): String
    val emptyRoutineDetail: String
    val restDayTitle: String
    val restDayDetail: String
    val notTrainedTitle: String
    fun notTrainedDetail(routine: String): String
    val addExercise: String

    val removeSet: String
    val addSet: String
    val skipped: String
    val collapse: String
    val expand: String
    val completed: String
    fun editExercise(name: String): String
    fun addVariantTo(name: String): String
    val addVariant: String
    val last: String
    val personalRecord: String
    val columnSet: String
    val columnReps: String
    fun repsValue(reps: Int): String
    fun setsNotDone(count: Int): String
    fun decreaseWeight(name: String, set: Int): String
    fun increaseWeight(name: String, set: Int): String
    fun weightField(name: String, set: Int): String
    fun repsField(name: String, set: Int): String
    fun completeSet(name: String, set: Int): String
    fun undoSet(name: String, set: Int): String

    val exerciseFallback: String
    val variants: String
    val variantsExplanation: String
    val sets: String
    val plannedSets: String
    val fewerSets: String
    val moreSets: String
    val removeFromRoutine: String
    val removeConfirm: String
    val mainVariant: String
    fun makeMain(name: String): String
    fun renameVariant(name: String): String
    fun removeVariant(name: String): String
    val exerciseNamePlaceholder: String
    val newVariantName: String
    val saveVariant: String
    val alreadyVariant: String
    fun linksHistory(name: String): String
    val renameKeepsHistory: String
    fun addTo(routine: String): String
    val newExerciseName: String

    val routineNameLabel: String
    fun routinePlaceholder(letter: String): String
    val routineNameField: String
    val exercisesLabel: String
    fun setCount(count: Int): String
    val alternativesPrefix: String
    fun moveUp(name: String): String
    fun moveDown(name: String): String

    val exportTitle: String
    val exportDetail: String
    val importTitle: String
    val importDetail: String
    val replaceTitle: String
    fun replaceDetail(summary: String): String
    val replaceConfirm: String
    fun workoutCount(count: Int): String
    fun exported(summary: String): String
    fun restored(summary: String): String
    val exportFailed: String
    val importFailed: String
    val backupNotRecognized: String
    val backupTooNew: String
    val backupMissingRoutines: String
    val backupDamaged: String

    val workout: String
    val food: String
    val mealNames: List<String>
    val kcal: String
    val protein: String
    val carbs: String
    val fat: String
    val proteinShort: String
    val carbsShort: String
    val fatShort: String
    fun kcalLeft(kcal: String): String
    fun kcalOver(kcal: String): String
    val setGoals: String
    val goalsTitle: String
    val goalsDetail: String
    val caloriesGoal: String
    fun gramsGoal(nutrient: String): String
    val save: String
    fun addFoodTo(meal: String): String
    fun mealPhoto(meal: String): String
    val searchFood: String
    val scanBarcode: String
    val recent: String
    val myFoods: String
    val noResults: String
    val emptyFoodList: String
    val createFood: String
    fun notInDatabase(code: String): String
    val foodNameLabel: String
    val brandLabel: String
    val barcodeLabel: String
    val optional: String
    val per100g: String
    val portionGramsLabel: String
    val amount: String
    val portion: String
    val wholePackage: String
    val lastTime: String
    val add: String
    val mealLabel: String
    val removeEntry: String
    fun removedEntry(name: String): String
    val undo: String
    fun copyMealFrom(day: String): String
    val yesterday: String
    val editFood: String
    val archiveFood: String
    val editFoodNote: String
    fun editEntry(name: String): String
    val legendOnTarget: String
    val legendOffTarget: String
    val legendNothingLogged: String
    val catalogCredit: String
    val cameraPermission: String
    val allowCamera: String
    val pointAtBarcode: String
    val flashlight: String
    val close: String
    fun foodEntryCount(count: Int): String

    val aiPhotos: String
    val aiSetupTitle: String
    val aiSetupIntro: String
    val aiSetupSteps: List<String>
    val openAiStudio: String
    val apiKey: String
    val pasteKey: String
    val saveKey: String
    val checkingKey: String
    fun savedKey(masked: String): String
    val removeKey: String
    val aiSetupFinePrint: String
    val aiInvalidKey: String
    val aiRateLimited: String
    val aiOffline: String
    val aiBusy: String
    val aiBlocked: String
    val aiUnreadable: String
    val aiNoKey: String
    fun aiOther(message: String): String
    val takePhoto: String
    val choosePhoto: String
    val describeMeal: String
    val notePlaceholder: String
    val noteDescription: String
    val estimate: String
    val skipNote: String
    val analyzing: String
    val retake: String
    val editNote: String
    val tryAgain: String
    val changeKey: String
    val noFoodFound: String
    val estimateFailed: String
    fun includeFood(name: String): String
    fun gramsOf(name: String): String
}

fun AppStrings.localizeDecimal(number: String): String = number.replace('.', decimalSeparator)

fun AppStrings.weightText(weightKg: Double?): String = localizeDecimal(formatWeight(weightKg))

private fun AppStrings.liftWeight(weightKg: Double): String =
    if (weightKg == 0.0) bodyweight else weightText(weightKg)

fun AppStrings.liftText(lift: LiftValue): String = "${liftWeight(lift.weightKg)}×${lift.reps}"

fun AppStrings.recordText(lift: LiftValue): String {
    val weight = if (lift.weightKg == 0.0) bodyweight else "${weightText(lift.weightKg)} kg"
    return "$weight × ${lift.reps}"
}

fun AppStrings.weightWithUnit(weightKg: Double?): String = when (weightKg) {
    null -> ""
    0.0 -> bodyweight
    else -> "${weightText(weightKg)} kg"
}

fun AppStrings.weekdayName(date: LocalDate): String = weekdayNames[date.dayOfWeek.ordinal]

fun AppStrings.weekdayLetter(date: LocalDate): String = weekdayLetters[date.dayOfWeek.ordinal]

fun AppStrings.fullDayText(date: LocalDate): String =
    "${weekdayName(date)}, ${date.dayOfMonth} ${monthNamesInDate[date.monthValue - 1]}"

fun AppStrings.fullDateText(date: LocalDate): String =
    "${date.dayOfMonth} ${monthNamesInDate[date.monthValue - 1]} ${date.year}"

fun AppStrings.shortDateText(date: LocalDate): String =
    "${date.dayOfMonth} ${monthNamesShort[date.monthValue - 1]}"

fun AppStrings.exerciseName(name: String): String = BuiltInNames.exercise(name, language)

fun AppStrings.routineName(routine: Routine): String =
    BuiltInNames.routine(routine.name, language).ifBlank { routine.letter }

fun AppStrings.backupSummary(workouts: Int, sets: Int, foodEntries: Int): String =
    "${workoutCount(workouts)}, ${setCount(sets)}, ${foodEntryCount(foodEntries)}"

fun AppStrings.mealName(meal: Meal): String = mealNames[meal.ordinal]

fun AppStrings.kcalText(kcal: Double): String = "${Math.round(kcal)} ${this.kcal}"

fun AppStrings.gramsText(grams: Double): String = "${localizeDecimal(formatAmount(grams))} g"

fun AppStrings.amountText(value: Double): String = localizeDecimal(formatAmount(value))

fun AppStrings.foodName(item: FoodItem): String = item.displayName(language)

fun AppStrings.aiErrorText(error: AiError): String = when (error) {
    AiError.InvalidKey -> aiInvalidKey
    AiError.RateLimited -> aiRateLimited
    AiError.Offline -> aiOffline
    AiError.Busy -> aiBusy
    AiError.Blocked -> aiBlocked
    AiError.Unreadable -> aiUnreadable
    AiError.NoKey -> aiNoKey
    is AiError.Other -> aiOther(error.message)
}

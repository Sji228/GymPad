package com.gympad.ui.strings

import com.gympad.domain.AppLanguage

object EnglishStrings : AppStrings {
    override val language = AppLanguage.ENGLISH
    override val decimalSeparator = '.'
    override val bodyweight = "BW"

    override val weekdayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    override val weekdayLetters = listOf("M", "T", "W", "T", "F", "S", "S")
    override val monthNames = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )
    override val monthNamesInDate = monthNames
    override val monthNamesShort = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    override val today = "Today"
    override val cancel = "Cancel"
    override val back = "Back"
    override val menu = "Menu"
    override val readOnly = "Read-only"
    override val routines = "Routines"
    override val backup = "Backup"
    override val languageLabel = "Language"
    override val history = "History"
    override val openHistory = "Open history"
    override val closeHistory = "Close history"
    override val legendComplete = "Complete"
    override val legendIncomplete = "Incomplete"
    override val legendRest = "Rest"
    override fun emptyRoutineTitle(routine: String) = "$routine is empty"
    override val emptyRoutineDetail = "Add the first exercise to this routine."
    override val restDayTitle = "Rest day"
    override val restDayDetail = "No workout logged."
    override val notTrainedTitle = "Not trained"
    override fun notTrainedDetail(routine: String) = "$routine wasn't logged on this day."
    override val addExercise = "Add exercise"

    override val removeSet = "Remove set"
    override val addSet = "Add set"
    override val skipped = "Skipped"
    override val collapse = "Collapse"
    override val expand = "Expand"
    override val completed = "Completed"
    override fun editExercise(name: String) = "Edit $name"
    override fun addVariantTo(name: String) = "Add variant to $name"
    override val addVariant = "Add variant"
    override val last = "Last"
    override val personalRecord = "PR"
    override val columnSet = "Set"
    override val columnReps = "Reps"
    override fun repsValue(reps: Int) = "$reps reps"
    override fun setsNotDone(count: Int) = if (count == 1) "1 set not done" else "$count sets not done"
    override fun decreaseWeight(name: String, set: Int) = "Decrease $name set $set weight"
    override fun increaseWeight(name: String, set: Int) = "Increase $name set $set weight"
    override fun weightField(name: String, set: Int) = "$name set $set weight"
    override fun repsField(name: String, set: Int) = "$name set $set reps"
    override fun completeSet(name: String, set: Int) = "Complete $name set $set"
    override fun undoSet(name: String, set: Int) = "Undo $name set $set"

    override val exerciseFallback = "Exercise"
    override val variants = "Variants"
    override val variantsExplanation = "Swap between these on the card when equipment is taken. Each one keeps its own history and PR."
    override val sets = "Sets"
    override val plannedSets = "Planned for each workout"
    override val fewerSets = "Fewer sets"
    override val moreSets = "More sets"
    override val removeFromRoutine = "Remove from routine"
    override val removeConfirm = "Tap again to remove. Past workouts are kept."
    override val mainVariant = "Main · loads first"
    override fun makeMain(name: String) = "Make $name the main variant"
    override fun renameVariant(name: String) = "Rename $name"
    override fun removeVariant(name: String) = "Remove $name"
    override val exerciseNamePlaceholder = "Exercise name"
    override val newVariantName = "New variant name"
    override val saveVariant = "Save variant"
    override val alreadyVariant = "Already a variant here."
    override fun linksHistory(name: String) = "Links to the existing history of $name."
    override val renameKeepsHistory = "Renaming keeps this variant's history."
    override fun addTo(routine: String) = "Add to $routine"
    override val newExerciseName = "New exercise name"

    override val routineNameLabel = "Name"
    override fun routinePlaceholder(letter: String) = "Routine $letter"
    override val routineNameField = "Routine name"
    override val exercisesLabel = "Exercises"
    override fun setCount(count: Int) = if (count == 1) "1 set" else "$count sets"
    override val alternativesPrefix = "or"
    override fun moveUp(name: String) = "Move $name up"
    override fun moveDown(name: String) = "Move $name down"

    override val exportTitle = "Export"
    override val exportDetail = "Save all routines and workouts to a JSON file."
    override val importTitle = "Import"
    override val importDetail = "Replace everything in the app with a backup file."
    override val replaceTitle = "Replace all data?"
    override fun replaceDetail(summary: String) = "Everything in the app will be replaced by this backup: $summary."
    override val replaceConfirm = "Replace"
    override fun workoutCount(count: Int) = if (count == 1) "1 workout" else "$count workouts"
    override fun exported(summary: String) = "Saved. $summary."
    override fun restored(summary: String) = "Restored. $summary."
    override val exportFailed = "Couldn't save the file."
    override val importFailed = "Couldn't read the file."
    override val backupNotRecognized = "This file isn't a GymPad backup."
    override val backupTooNew = "This backup was made by a newer version of GymPad."
    override val backupMissingRoutines = "This backup doesn't contain routines A to E."
    override val backupDamaged = "This backup is damaged. Your current data was not changed."
}

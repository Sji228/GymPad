package com.gympad.ui.strings

import com.gympad.domain.AppLanguage

/** Polish has three plural forms: 1 seria, 2 serie, 5 serii. The teens always take the last one. */
internal fun polishPlural(count: Int, one: String, few: String, many: String): String = when {
    count == 1 -> one
    count % 10 in 2..4 && count % 100 !in 12..14 -> few
    else -> many
}

object PolishStrings : AppStrings {
    override val language = AppLanguage.POLISH
    override val decimalSeparator = ','
    override val bodyweight = "MC"

    override val weekdayNames = listOf("Poniedziałek", "Wtorek", "Środa", "Czwartek", "Piątek", "Sobota", "Niedziela")
    override val weekdayLetters = listOf("P", "W", "Ś", "C", "P", "S", "N")
    override val monthNames = listOf(
        "Styczeń", "Luty", "Marzec", "Kwiecień", "Maj", "Czerwiec",
        "Lipiec", "Sierpień", "Wrzesień", "Październik", "Listopad", "Grudzień",
    )
    override val monthNamesInDate = listOf(
        "stycznia", "lutego", "marca", "kwietnia", "maja", "czerwca",
        "lipca", "sierpnia", "września", "października", "listopada", "grudnia",
    )
    override val monthNamesShort = listOf("sty", "lut", "mar", "kwi", "maj", "cze", "lip", "sie", "wrz", "paź", "lis", "gru")

    override val today = "Dziś"
    override val cancel = "Anuluj"
    override val back = "Wstecz"
    override val menu = "Menu"
    override val readOnly = "Tylko do odczytu"
    override val routines = "Plany"
    override val backup = "Kopia zapasowa"
    override val languageLabel = "Język"
    override val history = "Historia"
    override val openHistory = "Otwórz historię"
    override val closeHistory = "Zamknij historię"
    override val legendComplete = "Ukończony"
    override val legendIncomplete = "Nieukończony"
    override val legendRest = "Odpoczynek"
    override fun emptyRoutineTitle(routine: String) = "$routine: brak ćwiczeń"
    override val emptyRoutineDetail = "Dodaj pierwsze ćwiczenie do tego planu."
    override val restDayTitle = "Dzień odpoczynku"
    override val restDayDetail = "Brak zapisanego treningu."
    override val notTrainedTitle = "Bez treningu"
    override fun notTrainedDetail(routine: String) = "Tego dnia nie zapisano treningu: $routine."
    override val addExercise = "Dodaj ćwiczenie"

    override val removeSet = "Usuń serię"
    override val addSet = "Dodaj serię"
    override val skipped = "Pominięte"
    override val collapse = "Zwiń"
    override val expand = "Rozwiń"
    override val completed = "Wykonano"
    override fun editExercise(name: String) = "Edytuj: $name"
    override fun addVariantTo(name: String) = "Dodaj wariant: $name"
    override val addVariant = "Dodaj wariant"
    override val last = "Ostatnio"
    override val personalRecord = "PR"
    override val columnSet = "Nr"
    override val columnReps = "Powt."
    override fun repsValue(reps: Int) = "$reps powt."
    override fun setsNotDone(count: Int) = "$count " + polishPlural(count, "seria niewykonana", "serie niewykonane", "serii niewykonanych")
    override fun decreaseWeight(name: String, set: Int) = "Zmniejsz ciężar: $name, seria $set"
    override fun increaseWeight(name: String, set: Int) = "Zwiększ ciężar: $name, seria $set"
    override fun weightField(name: String, set: Int) = "Ciężar: $name, seria $set"
    override fun repsField(name: String, set: Int) = "Powtórzenia: $name, seria $set"
    override fun completeSet(name: String, set: Int) = "Zakończ serię $set: $name"
    override fun undoSet(name: String, set: Int) = "Cofnij serię $set: $name"

    override val exerciseFallback = "Ćwiczenie"
    override val variants = "Warianty"
    override val variantsExplanation = "Przełączaj je na karcie, gdy sprzęt jest zajęty. Każdy wariant ma własną historię i PR."
    override val sets = "Serie"
    override val plannedSets = "Planowane na każdy trening"
    override val fewerSets = "Mniej serii"
    override val moreSets = "Więcej serii"
    override val removeFromRoutine = "Usuń z planu"
    override val removeConfirm = "Stuknij jeszcze raz, aby usunąć. Poprzednie treningi zostają."
    override val mainVariant = "Główny · ładuje się pierwszy"
    override fun makeMain(name: String) = "Ustaw jako główny: $name"
    override fun renameVariant(name: String) = "Zmień nazwę: $name"
    override fun removeVariant(name: String) = "Usuń: $name"
    override val exerciseNamePlaceholder = "Nazwa ćwiczenia"
    override val newVariantName = "Nazwa nowego wariantu"
    override val saveVariant = "Zapisz wariant"
    override val alreadyVariant = "To już jest wariant tego ćwiczenia."
    override fun linksHistory(name: String) = "Połączy się z historią: $name."
    override val renameKeepsHistory = "Zmiana nazwy zachowuje historię tego wariantu."
    override fun addTo(routine: String) = "Dodaj do: $routine"
    override val newExerciseName = "Nazwa nowego ćwiczenia"

    override val routineNameLabel = "Nazwa"
    override fun routinePlaceholder(letter: String) = "Plan $letter"
    override val routineNameField = "Nazwa planu"
    override val exercisesLabel = "Ćwiczenia"
    override fun setCount(count: Int) = "$count " + polishPlural(count, "seria", "serie", "serii")
    override val alternativesPrefix = "lub"
    override fun moveUp(name: String) = "Przesuń w górę: $name"
    override fun moveDown(name: String) = "Przesuń w dół: $name"

    override val exportTitle = "Eksportuj"
    override val exportDetail = "Zapisz wszystkie plany i treningi do pliku JSON."
    override val importTitle = "Importuj"
    override val importDetail = "Zastąp wszystko w aplikacji danymi z pliku kopii."
    override val replaceTitle = "Zastąpić wszystkie dane?"
    override fun replaceDetail(summary: String) = "Wszystko w aplikacji zostanie zastąpione tą kopią: $summary."
    override val replaceConfirm = "Zastąp"
    override fun workoutCount(count: Int) = "$count " + polishPlural(count, "trening", "treningi", "treningów")
    override fun exported(summary: String) = "Zapisano: $summary."
    override fun restored(summary: String) = "Przywrócono: $summary."
    override val exportFailed = "Nie udało się zapisać pliku."
    override val importFailed = "Nie udało się odczytać pliku."
    override val backupNotRecognized = "To nie jest plik kopii GymPad."
    override val backupTooNew = "Ta kopia pochodzi z nowszej wersji GymPad."
    override val backupMissingRoutines = "Ta kopia nie zawiera planów od A do E."
    override val backupDamaged = "Ta kopia jest uszkodzona. Twoje obecne dane nie zostały zmienione."
}

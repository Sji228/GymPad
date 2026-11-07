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
    override val exportDetail = "Zapisz wszystkie plany, treningi, dziennik jedzenia i pomiary do pliku JSON."
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

    override val workout = "Trening"
    override val food = "Jedzenie"
    override val mealNames = listOf("Śniadanie", "II śniadanie", "Obiad", "Przekąski", "Kolacja")
    override val kcal = "kcal"
    override val protein = "Białko"
    override val carbs = "Węglowodany"
    override val fat = "Tłuszcz"
    override val proteinShort = "B"
    override val carbsShort = "W"
    override val fatShort = "T"
    override fun kcalLeft(kcal: String) = "Zostało $kcal"
    override fun kcalOver(kcal: String) = "$kcal ponad cel"
    override val setGoals = "Ustaw cele"
    override val goalsTitle = "Cele dzienne"
    override val goalsDetail = "Zostaw pole puste, by widzieć tylko sumę."
    override val caloriesGoal = "Kalorie (kcal)"
    override fun gramsGoal(nutrient: String) = "$nutrient (g)"
    override val save = "Zapisz"
    override fun addFoodTo(meal: String) = "Dodaj: $meal"
    override fun mealPhoto(meal: String) = "Zdjęcie posiłku: $meal"
    override val searchFood = "Szukaj lub wpisz kod kreskowy"
    override val scanBarcode = "Skanuj kod kreskowy"
    override val recent = "Historia"
    override val myFoods = "Moje produkty"
    override val noResults = "Nic nie znaleziono. Sprawdź pisownię albo utwórz produkt samodzielnie."
    override val emptyFoodList = "Wyszukaj produkt lub zeskanuj jego kod. Dodane produkty pojawią się tutaj, by dodać je jednym dotknięciem."
    override val createFood = "Utwórz produkt"
    override fun notInDatabase(code: String) = "Kodu $code nie ma jeszcze w bazie. Wpisz dane z etykiety raz, a następnym razem skaner go znajdzie."
    override val foodNameLabel = "Nazwa"
    override val brandLabel = "Marka"
    override val barcodeLabel = "Kod kreskowy"
    override val optional = "opcjonalnie"
    override val per100g = "Na 100 g"
    override val portionGramsLabel = "Porcja (g)"
    override val amount = "Ilość"
    override val portion = "Porcja"
    override val wholePackage = "Opakowanie"
    override val lastTime = "Ostatnio"
    override val add = "Dodaj"
    override val mealLabel = "Posiłek"
    override val removeEntry = "Usuń"
    override fun removedEntry(name: String) = "Usunięto: $name"
    override val undo = "Cofnij"
    override fun copyMealFrom(day: String) = "Kopiuj z $day"
    override val yesterday = "wczoraj"
    override val editFood = "Edytuj produkt"
    override val archiveFood = "Usuń z moich produktów"
    override val editFoodNote = "Zmiany obejmą każdy dzień, w którym dodano ten produkt."
    override fun editEntry(name: String) = "Edytuj: $name"
    override val legendOnTarget = "W celu"
    override val legendOffTarget = "Poza celem"
    override val legendNothingLogged = "Brak wpisów"
    override val catalogCredit = "Dane produktów: Open Food Facts (ODbL), podstawowe produkty: USDA."
    override val cameraPermission = "GymPad potrzebuje aparatu do odczytu kodów kreskowych. Nic nie jest nagrywane ani wysyłane."
    override val allowCamera = "Zezwól na aparat"
    override val pointAtBarcode = "Skieruj aparat na kod kreskowy"
    override val flashlight = "Latarka"
    override val close = "Zamknij"
    override fun foodEntryCount(count: Int) = "$count " + polishPlural(count, "wpis", "wpisy", "wpisów") + " w dzienniku"

    override val aiPhotos = "Twój klucz API"
    override val aiSetupTitle = "Zdjęcia posiłków z Gemini"
    override val aiSetupIntro = "Zrób zdjęcie posiłku, a Gemini od Google oszacuje, co jest na talerzu: produkty, ich wagę, kalorie i makroskładniki. Potrzebny jest Twój własny klucz API, który jest darmowy:"
    override val aiSetupSteps = listOf(
        "Otwórz Google AI Studio przyciskiem poniżej i zaloguj się kontem Google.",
        "Kliknij „Create API key”. Jeśli zapyta o projekt, pozwól mu utworzyć nowy.",
        "Skopiuj klucz. To długi kod zaczynający się od „AIza”.",
        "Wklej go poniżej i kliknij Zapisz klucz.",
    )
    override val openAiStudio = "Otwórz Google AI Studio"
    override val apiKey = "Klucz API"
    override val pasteKey = "Wklej klucz API"
    override val saveKey = "Zapisz klucz"
    override val checkingKey = "Sprawdzam klucz…"
    override fun savedKey(masked: String) = "Zapisany klucz: $masked"
    override val removeKey = "Usuń klucz"
    override val aiSetupFinePrint = "Darmowy limit wystarcza na kilka posiłków dziennie. W darmowej wersji Google może wykorzystywać zdjęcia do ulepszania swoich usług. Klucz zostaje na tym telefonie i nie trafia do kopii zapasowej."
    override val aiInvalidKey = "Google nie przyjął tego klucza. Sprawdź, czy skopiowano go w całości."
    override val aiRateLimited = "Darmowy limit Gemini chwilowo się wyczerpał. Spróbuj ponownie za minutę."
    override val aiOffline = "Brak połączenia z internetem."
    override val aiBusy = "Gemini jest teraz przeciążony. Spróbuj za chwilę."
    override val aiBlocked = "Gemini odrzucił to zdjęcie. Spróbuj innego."
    override val aiUnreadable = "Nie udało się odczytać odpowiedzi Gemini. Spróbuj ponownie."
    override val aiNoKey = "Najpierw dodaj klucz Gemini."
    override fun aiOther(message: String) = "Błąd Gemini: $message"
    override val takePhoto = "Zrób zdjęcie"
    override val choosePhoto = "Wybierz z galerii"
    override val describeMeal = "Coś dodać?"
    override val notePlaceholder = "np. smażone na oliwie, ok. 200 g"
    override val noteDescription = "Opis posiłku"
    override val estimate = "Oszacuj"
    override val skipNote = "Pomiń, użyj samego zdjęcia"
    override val analyzing = "Gemini ogląda Twój posiłek…"
    override val retake = "Zrób nowe zdjęcie"
    override val editNote = "Zmień opis"
    override val tryAgain = "Spróbuj ponownie"
    override val changeKey = "Zmień klucz"
    override val noFoodFound = "Gemini nie znalazł jedzenia na tym zdjęciu."
    override val estimateFailed = "Nie udało się oszacować"
    override fun includeFood(name: String) = "Uwzględnij: $name"
    override fun gramsOf(name: String) = "$name, gramy"

    override val me = "Pomiary"
    override val measureNames = listOf(
        "Masa ciała", "Szyja/kark", "Klatka piersiowa", "Talia", "Brzuch", "Biodra", "Udo", "Łydka", "Ramię/biceps",
        "Zawartość tłuszczu", "BMI", "Wzrost",
    )
    override val emptyMeasure = "Brak pomiarów. Dodaj pierwszy, a tutaj pojawi się wykres."
    override val bmiNeedsHeight = "Wpisz raz swój wzrost, a BMI policzy się z każdej zapisanej masy ciała."
    override val bmiNeedsWeight = "Zapisz masę ciała, a pojawi się tu Twoje BMI."
    override fun heightValue(height: String) = "Wzrost: $height"
    override val setHeight = "Ustaw wzrost"
    override val changeHeight = "Zmień wzrost"
    override val dateLabel = "Data"
    override val previousDay = "Poprzedni dzień"
    override val nextDay = "Następny dzień"
    override fun changeSince(change: String, date: String) = "$change od $date"
    override fun editMeasurement(name: String, date: String) = "Edytuj: $name, $date"
    override fun measurementCount(count: Int) = "$count " + polishPlural(count, "pomiar", "pomiary", "pomiarów")
}

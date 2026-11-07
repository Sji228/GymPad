package com.gympad

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.room.Room
import com.gympad.data.FakeGoogle
import com.gympad.data.FoodCatalog
import com.gympad.data.FoodDraft
import com.gympad.data.FoodRepository
import com.gympad.data.GymDatabase
import com.gympad.data.GymRepository
import com.gympad.data.HttpResult
import com.gympad.data.LanguageStore
import com.gympad.data.MealAnalyzer
import com.gympad.data.MemorySettings
import com.gympad.data.NutritionGoals
import com.gympad.data.SetTarget
import com.gympad.data.TWO_FOODS
import com.gympad.data.buildGymDatabase
import com.gympad.data.decodeBackup
import com.gympad.data.encode
import com.gympad.data.geminiReply
import com.gympad.domain.AppLanguage
import com.gympad.domain.BuiltInNames
import com.gympad.domain.Meal
import com.gympad.domain.toTemplate
import com.gympad.ui.LocalMealCamera
import com.gympad.ui.strings.EnglishStrings
import com.gympad.ui.strings.PolishStrings
import com.gympad.ui.strings.fullDateText
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.imageio.ImageIO
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalTestApi::class)
class AppFlowTest {
    @get:Rule val tempFolder = TemporaryFolder()

    private val today = LocalDate.now().toEpochDay()
    private val screens = File("build/screens").apply { mkdirs() }

    private fun SkikoComposeUiTest.shot(name: String) {
        waitForIdle()
        ImageIO.write(captureToImage().toAwtImage(), "png", File(screens, "$name.png"))
    }

    private fun SkikoComposeUiTest.await(condition: () -> Boolean) = waitUntil(timeoutMillis = 8_000, condition = condition)

    private fun SkikoComposeUiTest.awaitText(text: String, substring: Boolean = false) = await {
        onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
    }

    private fun SkikoComposeUiTest.awaitDescription(text: String) = await {
        onAllNodes(hasContentDescription(text)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun SkikoComposeUiTest.tap(description: String) {
        awaitDescription(description)
        onNodeWithContentDescription(description).performClick()
    }

    private fun SkikoComposeUiTest.type(description: String, text: String) {
        awaitDescription(description)
        onNodeWithContentDescription(description).performClick()
        onNodeWithContentDescription(description).performTextReplacement(text)
    }

    private lateinit var food: FoodRepository
    private val aiSettings = MemorySettings()
    private var google = FakeGoogle { _, _, _ -> HttpResult(200, """{"models":[]}""") }

    /** A small plate-coloured JPEG standing in for a camera photo. */
    private fun mealPhoto(): ByteArray {
        val image = BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply {
            color = java.awt.Color(0xE8, 0xD9, 0xB5)
            fillRect(0, 0, 64, 48)
            color = java.awt.Color(0xB5, 0x6A, 0x2E)
            fillOval(10, 10, 30, 20)
            dispose()
        }
        return ByteArrayOutputStream().also { ImageIO.write(image, "jpg", it) }.toByteArray()
    }

    private val languageStore = object : LanguageStore {
        var stored: String? = "en"
        override fun load() = stored
        override fun save(code: String) {
            stored = code
        }
    }

    private fun app(
        prepare: suspend (GymRepository) -> Unit = {},
        block: suspend SkikoComposeUiTest.(GymRepository) -> Unit,
    ) {
        val database = Room.databaseBuilder<GymDatabase>(name = File(tempFolder.root, "test.db").absolutePath)
            .buildGymDatabase()
        val repository = GymRepository(database)
        val catalog = FoodCatalog(File(tempFolder.root, "catalog"))
        food = FoodRepository(database, catalog)
        val analyzer = MealAnalyzer(aiSettings, google)
        runBlocking {
            repository.seedIfEmpty()
            prepare(repository)
        }
        val stores = object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
        val lifecycle = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        }
        try {
            runSkikoComposeUiTest(size = Size(824f, 1830f), density = Density(2f)) {
                setContent {
                    CompositionLocalProvider(
                        LocalViewModelStoreOwner provides stores,
                        LocalLifecycleOwner provides lifecycle,
                        LocalMealCamera provides { onCaptured, _ -> LaunchedEffect(Unit) { onCaptured(mealPhoto()) } },
                    ) {
                        GymPadRoot(repository, food, analyzer, installEpochDay = today - 200, languageStore)
                    }
                }
                awaitText("Barbell Bench Press")
                block(repository)
            }
        } finally {
            stores.viewModelStore.clear()
            catalog.close()
            database.close()
        }
    }

    /** Logs bench on a past day, ticking one set per entry in [lifts]. */
    private suspend fun logBench(repository: GymRepository, daysAgo: Long, lifts: List<Pair<Double, Int>>) {
        val routine = repository.observeRoutines().first().first { it.letter == "A" }
        val slot = toTemplate(repository.observeTemplate().first()).first { it.routineId == routine.id }
        val target = SetTarget(today - daysAgo, routine.id, slot.slotId, slot.variants.first().exerciseId)
        lifts.forEachIndexed { index, (weight, reps) ->
            repository.editSet(target, index) { it.copy(weightKg = weight, reps = reps, completed = true, completedAt = 1L) }
        }
    }

    /** Ticks every planned set of every exercise in routine A, which makes the day green. */
    private suspend fun logWholeRoutine(repository: GymRepository, daysAgo: Long) {
        val routine = repository.observeRoutines().first().first { it.letter == "A" }
        toTemplate(repository.observeTemplate().first()).filter { it.routineId == routine.id }.forEach { slot ->
            val target = SetTarget(today - daysAgo, routine.id, slot.slotId, slot.variants.first().exerciseId)
            repeat(slot.defaultSets) { index ->
                repository.editSet(target, index) { it.copy(weightKg = 60.0, reps = 10, completed = true, completedAt = 1L) }
            }
        }
    }

    @Test fun `typing cascades, ghosts confirm in one tap, steppers adjust`() = app { repository ->
        shot("01_today_fresh")
        type("Barbell Bench Press set 1 weight", "80")
        type("Barbell Bench Press set 1 reps", "8")
        shot("02_typed_set_one")
        tap("Complete Barbell Bench Press set 1")
        awaitText("1/3")
        tap("Complete Barbell Bench Press set 2")
        awaitText("2/3")
        tap("Increase Barbell Bench Press set 3 weight")
        tap("Complete Barbell Bench Press set 3")
        awaitText("3/3")
        shot("03_logged_all_sets")

        val sets = repository.exportBackup().sets.sortedBy { it.setIndex }
        assertEquals(listOf(80.0, 80.0, 82.5), sets.map { it.weightKg })
        assertEquals(listOf(8, 8, 8), sets.map { it.reps })
        assertTrue(sets.all { it.completed })
    }

    @Test fun `ticking with no reps anywhere does nothing`() = app { repository ->
        tap("Complete Barbell Bench Press set 1")
        waitForIdle()
        shot("04_missing_reps")
        assertTrue(repository.exportBackup().sets.isEmpty())
    }

    @Test fun `undoing a set makes it editable again`() = app { repository ->
        type("Barbell Bench Press set 1 weight", "80")
        type("Barbell Bench Press set 1 reps", "8")
        tap("Complete Barbell Bench Press set 1")
        awaitText("1/3")
        tap("Undo Barbell Bench Press set 1")
        awaitText("0/3")
        type("Barbell Bench Press set 1 weight", "90")
        tap("Complete Barbell Bench Press set 1")
        awaitText("1/3")
        assertEquals(90.0, repository.exportBackup().sets.single().weightKg!!, 0.0)
    }

    @Test fun `swapping to a named variant uses its own history`() = app(
        prepare = { logBench(it, daysAgo = 3, lifts = listOf(100.0 to 5, 90.0 to 8, 80.0 to 10)) },
    ) {
        awaitText("100 kg × 5")
        shot("05_ghosts_from_history")
        tap("Complete Barbell Bench Press set 1")
        awaitText("1/3")
        onNodeWithText("Dumbbell Bench Press").performClick()
        awaitDescription("Dumbbell Bench Press set 1 weight")
        shot("06_swapped_variant")
        assertEquals(0, onAllNodesWithText("100 kg × 5").fetchSemanticsNodes().size)
        onNodeWithText("Barbell Bench Press").performClick()
        awaitText("100 kg × 5")
    }

    @Test fun `variants are added and renamed from the card`() = app { repository ->
        onNode(hasScrollToNodeAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .performScrollToNode(hasContentDescription("Add variant to Cable Fly"))
        tap("Add variant to Cable Fly")
        awaitDescription("New variant name")
        waitForIdle()
        shot("07_add_variant_from_card")
        type("New variant name", "Pec Deck")
        tap("Save variant")
        awaitDescription("Rename Pec Deck")
        shot("08_edit_exercise_sheet")
        tap("Rename Pec Deck")
        awaitDescription("Rename Pec Deck")
        type("Rename Pec Deck", "Pec Fly Machine")
        shot("09_renaming_variant")
        tap("Save variant")
        awaitDescription("Rename Pec Fly Machine")
        assertTrue(repository.observeExercises().first().any { it.name == "Pec Fly Machine" })
        assertTrue(repository.observeExercises().first().none { it.name == "Pec Deck" })

        tap("Make Pec Fly Machine the main variant")
        awaitText("Main · loads first")
        shot("09b_variant_made_main")
        val slots = toTemplate(repository.observeTemplate().first())
        assertEquals("Pec Fly Machine", slots.first { s -> s.variants.any { it.name == "Cable Fly" } }.variants.first().name)
    }

    @Test fun `past days are read-only and history shows statuses`() = app(
        prepare = { repository ->
            logBench(repository, daysAgo = 2, lifts = listOf(100.0 to 5, 90.0 to 8))
            logBench(repository, daysAgo = 5, lifts = listOf(95.0 to 5, 85.0 to 8, 75.0 to 10))
            logBench(repository, daysAgo = 9, lifts = listOf(95.0 to 5))
            listOf(4L, 7L, 11L, 14L, 16L, 18L, 21L, 25L, 28L, 32L, 35L, 39L, 42L, 46L, 49L).forEach { logWholeRoutine(repository, it) }
        },
    ) {
        tap("Open history")
        awaitText("History")
        shot("10_history")
        val format = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)
        tap(LocalDate.ofEpochDay(today - 2).format(format))
        awaitText(LocalDate.ofEpochDay(today - 2).format(format))
        shot("11_past_day_read_only")
        onAllNodes(hasSetTextAction()).assertCountEquals(0)
        assertEquals(0, onAllNodes(hasContentDescription("Edit", substring = true)).fetchSemanticsNodes().size)
        awaitText("1 set not done")
        awaitText("Skipped")
    }

    @Test fun `routine screen names routines and adds exercises`() = app { repository ->
        tap("Menu")
        awaitText("Routines")
        shot("12_drawer")
        onNodeWithText("Routines").performClick()
        awaitDescription("Routine name")
        waitForIdle()
        shot("13_routines")
        onNodeWithText("D").performClick()
        await { onAllNodes(hasText("Routine D")).fetchSemanticsNodes().isNotEmpty() }
        type("Routine name", "Arms")
        await { runBlocking { repository.observeRoutines().first().any { it.name == "Arms" } } }
        onNodeWithText("Add exercise").performClick()
        awaitDescription("New exercise name")
        waitForIdle()
        shot("14_add_exercise")
        type("New exercise name", "Barbell Curl")
        onAllNodesWithText("Add exercise").onLast().performClick()
        awaitText("Barbell Curl")
        shot("15_routine_after_add")
    }

    @Test fun `empty routine offers to add an exercise`() = app {
        awaitText("D")
        onNodeWithText("D").performClick()
        awaitText("D is empty")
        shot("16_empty_routine")
    }

    private fun SkikoComposeUiTest.switchLanguage(nativeName: String) {
        tap("Menu")
        awaitText(nativeName)
        onNodeWithText(nativeName).performClick()
        waitForIdle()
    }

    @Test fun `switching language keeps every bit of progress`() = app(
        prepare = { logBench(it, daysAgo = 3, lifts = listOf(100.0 to 5, 90.0 to 8, 82.5 to 10)) },
    ) { repository ->
        awaitText("100 kg × 5")
        val before = repository.exportBackup()

        switchLanguage("Polski")
        awaitText("Dziś")
        awaitText("Wyciskanie sztangi na ławce")
        awaitText("100 kg × 5")
        assertEquals("pl", languageStore.stored)
        assertEquals("Changing language must write nothing to the database", before, repository.exportBackup().copy(exportedAt = before.exportedAt))
        shot("pl_01_today_with_history")

        // Logging in Polish lands on the same exercise, with its ghosts and comma decimals.
        val bench = "Wyciskanie sztangi na ławce"
        tap("Zakończ serię 1: $bench")
        awaitText("1/3")
        tap("Zwiększ ciężar: $bench, seria 3")
        tap("Zakończ serię 3: $bench")
        awaitText("2/3")
        awaitText("85")
        shot("pl_02_logged")
        val polishSets = repository.exportBackup()
        assertEquals("No exercise may be created by logging in another language", before.exercises, polishSets.exercises)
        assertEquals(setOf(100.0, 85.0), polishSets.sets.filter { it.sessionExerciseId != before.sets.first().sessionExerciseId }.map { it.weightKg }.toSet())

        switchLanguage("English")
        awaitText("Today")
        awaitText("Barbell Bench Press")
        awaitText("2/3")
        awaitText("100 kg × 5")
        assertEquals(polishSets.sets, repository.exportBackup().sets)
    }

    @Test fun `typing a built-in's Polish name reaches the same exercise`() = app { repository ->
        val slots = toTemplate(repository.observeTemplate().first())
        val fly = slots.first { slot -> slot.variants.any { it.name == "Cable Fly" } }
        val squatId = repository.observeExercises().first().first { it.name == "Back Squat" }.id
        val countBefore = repository.observeExercises().first().size

        repository.addVariant(fly.slotId, "PRZYSIAD ZE SZTANGĄ")
        await { runBlocking { toTemplate(repository.observeTemplate().first()).first { it.slotId == fly.slotId }.variants.size == 2 } }
        val added = toTemplate(repository.observeTemplate().first()).first { it.slotId == fly.slotId }.variants.last()
        assertEquals(squatId, added.exerciseId)
        assertEquals(countBefore, repository.observeExercises().first().size)

        repository.addVariant(fly.slotId, "Łydki Własne")
        repository.addSlot(slots.last().routineId, "łydki własne")
        val exercises = repository.observeExercises().first()
        assertEquals("Different case in Polish letters is the same exercise", 1, exercises.count { it.name.equals("Łydki Własne", ignoreCase = true) })
        assertEquals("Łydki Własne", exercises.first { it.name.equals("Łydki Własne", ignoreCase = true) }.name)
    }

    @Test fun `every built-in exercise has a Polish name`() = app { repository ->
        repository.observeExercises().first().forEach { exercise ->
            assertTrue(exercise.name, BuiltInNames.exercise(exercise.name, AppLanguage.POLISH) != exercise.name)
        }
    }

    @Test fun `every screen renders in Polish`() = app(
        prepare = {
            logBench(it, daysAgo = 2, lifts = listOf(100.0 to 5, 90.0 to 8))
            logBench(it, daysAgo = 5, lifts = listOf(95.0 to 5, 85.0 to 8, 75.0 to 10))
        },
    ) {
        switchLanguage("Polski")
        awaitText("Dziś")
        shot("pl_03_today")
        tap("Menu")
        awaitText("JĘZYK")
        shot("pl_04_drawer")
        onNodeWithText("Polski").performClick()
        waitForIdle()
        tap("Edytuj: Wyciskanie sztangi na ławce")
        awaitText("WARIANTY")
        waitForIdle()
        shot("pl_05_edit_sheet")
        onNodeWithContentDescription("Zmień nazwę: Wyciskanie hantli na ławce").performClick()
        awaitText("Zmiana nazwy zachowuje historię tego wariantu.")
        shot("pl_06_rename")
        tap("Anuluj")
    }

    @Test fun `Polish history, past day and routines render`() = app(
        prepare = {
            logBench(it, daysAgo = 2, lifts = listOf(100.0 to 5, 90.0 to 8))
            logBench(it, daysAgo = 5, lifts = listOf(95.0 to 5, 85.0 to 8, 75.0 to 10))
        },
    ) {
        switchLanguage("Polski")
        tap("Otwórz historię")
        awaitText("Historia")
        shot("pl_07_history")
        val date = PolishStrings.fullDateText(LocalDate.ofEpochDay(today - 2))
        tap(date)
        awaitText(date)
        shot("pl_08_past_day")
        awaitText("1 seria niewykonana")
        awaitText("Pominięte")
        awaitText("Dziś")
        onAllNodesWithText("Dziś").onLast().performClick()
        waitForIdle()
        tap("Menu")
        awaitText("Plany")
        onNodeWithText("Plany").performClick()
        awaitDescription("Nazwa planu")
        waitForIdle()
        shot("pl_09_routines")
        onNodeWithText("Nogi").performClick()
        waitForIdle()
        shot("pl_10_routines_legs")
    }

    @Test fun `Polish backup screen and empty routine render`() = app {
        switchLanguage("Polski")
        onNodeWithText("D").performClick()
        awaitText("D: brak ćwiczeń")
        shot("pl_11_empty_routine")
        tap("Menu")
        awaitText("Kopia zapasowa")
        onNodeWithText("Kopia zapasowa").performClick()
        awaitText("Eksportuj")
        shot("pl_12_backup")
    }

    @Test fun `backup screen renders and round trip restores everything`() = app(
        prepare = { logBench(it, daysAgo = 1, lifts = listOf(100.0 to 5)) },
    ) { repository ->
        tap("Menu")
        awaitText("Backup")
        onNodeWithText("Backup").performClick()
        awaitText("Export")
        shot("17_backup")

        val before = repository.exportBackup()
        val restored = decodeBackup(before.encode())
        repository.replaceAllData(restored)
        val after = repository.exportBackup()
        assertEquals(before.sets, after.sets)
        assertEquals(before.exercises.toSet(), after.exercises.toSet())
        assertEquals(before.slots.toSet(), after.slots.toSet())
    }

    private fun SkikoComposeUiTest.openFood(menuLabel: String = "Food") {
        tap("Menu")
        awaitText(menuLabel)
        onNodeWithText(menuLabel).performClick()
        awaitDescription(if (menuLabel == "Food") "Add to Breakfast" else "Dodaj: Śniadanie")
        waitForIdle()
    }

    private suspend fun logFood(query: String, meal: Meal, grams: Double, daysAgo: Long = 0) {
        val item = food.search(query).first()
        food.addEntry(today - daysAgo, meal, item, grams)
    }

    @Test fun `food diary adds from search and totals the day`() = app(
        prepare = {
            food.setGoals(NutritionGoals(kcal = 2400, protein = 160, carbs = 250, fat = 70))
            logFood("oats", Meal.BREAKFAST, 80.0)
            logFood("chicken breast raw", Meal.LUNCH, 200.0)
            logFood("white rice dry", Meal.LUNCH, 100.0)
        },
    ) {
        openFood()
        awaitText("Chicken breast, raw")
        shot("food_01_day")

        tap("Add to Snacks")
        awaitDescription("Search, or type a barcode")
        waitForIdle()
        shot("food_02_add_recent")
        type("Search, or type a barcode", "banan")
        awaitText("Banana")
        shot("food_03_search")
        onNodeWithText("Banana").performClick()
        awaitDescription("Amount")
        type("Amount", "150")
        awaitText("134 kcal")
        shot("food_04_amount")
        onNodeWithText("Add").performClick()
        awaitText("Banana · 150 g")

        val entries = runBlocking { food.observeEntries(today).first() }
        val banana = entries.single { it.food.name == "Banana" }
        assertEquals(Meal.SNACKS.code, banana.meal)
        assertEquals(150.0, banana.grams, 0.0)
        // Logging the same catalog food again reuses its diary copy.
        runBlocking { logFood("banana", Meal.SUPPER, 100.0) }
        assertEquals(1, runBlocking { food.observeEntries(today).first() }.map { it.food.id }.filter { it == banana.food.id }.distinct().size)
        assertEquals(2, runBlocking { food.observeEntries(today).first() }.count { it.food.id == banana.food.id })
    }

    @Test fun `entries are edited, moved, removed and restored`() = app(
        prepare = { logFood("egg", Meal.BREAKFAST, 100.0) },
    ) {
        openFood()
        awaitText("Egg")
        onNodeWithText("Egg").performClick()
        awaitDescription("Amount")
        type("Amount", "150")
        onAllNodesWithText("Supper").onLast().performClick()
        waitForIdle()
        shot("food_05_edit_entry")
        onNodeWithText("Save").performClick()
        await { runBlocking { food.observeEntries(today).first() }.single().let { it.grams == 150.0 && it.meal == Meal.SUPPER.code } }

        onNodeWithText("Egg").performClick()
        awaitText("Remove")
        onNodeWithText("Remove").performClick()
        awaitText("Removed Egg")
        shot("food_06_undo")
        onNodeWithText("Undo").performClick()
        await { runBlocking { food.observeEntries(today).first() }.size == 1 }
    }

    @Test fun `unknown barcode creates a food that is found next time`() = app {
        openFood()
        tap("Add to Breakfast")
        type("Search, or type a barcode", "5909990000017")
        awaitText("Barcode 5909990000017 isn't in the database yet. Enter it once from the label and scanning will find it next time.")
        onNodeWithText("Create food").performClick()
        awaitDescription("Name")
        type("Name", "Homemade granola")
        type("Calories (kcal)", "450")
        type("Protein (g)", "12")
        type("Carbs (g)", "60")
        type("Fat (g)", "17")
        waitForIdle()
        shot("food_07_create")
        onNodeWithText("Save").performClick()
        awaitDescription("Amount")
        type("Amount", "60")
        onNodeWithText("Add").performClick()
        awaitText("Homemade granola · 60 g")

        val found = runBlocking { food.findBarcode("5909990000017") }
        assertEquals("Homemade granola", found?.name)
        assertEquals(60.0, found?.lastGrams)
    }

    @Test fun `future days are open for planning and past meals can be copied`() = app(
        prepare = { logFood("oats", Meal.BREAKFAST, 70.0, daysAgo = 1) },
    ) {
        openFood()
        awaitText("Copy from yesterday")
        shot("food_08_copy_offer")
        onNodeWithText("Copy from yesterday").performClick()
        awaitText("Oats")
        assertEquals(70.0, runBlocking { food.observeEntries(today).first() }.single().grams, 0.0)

        val tomorrow = LocalDate.ofEpochDay(today + 1)
        tap(tomorrow.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)))
        // Today's breakfast is now the one to copy, and today is tomorrow's yesterday.
        awaitText("Copy from yesterday")
        tap("Add to Lunch")
        type("Search, or type a barcode", "apple")
        awaitText("Apple")
        onNodeWithText("Apple").performClick()
        awaitDescription("Amount")
        onNodeWithText("Add").performClick()
        await { runBlocking { food.observeEntries(today + 1).first() }.size == 1 }
    }

    @Test fun `food screens render in Polish`() = app(
        prepare = {
            food.setGoals(NutritionGoals(kcal = 2200, protein = 150))
            logFood("jajko", Meal.BREAKFAST, 120.0)
            logFood("chleb zytni", Meal.BREAKFAST, 64.0)
        },
    ) {
        switchLanguage("Polski")
        openFood("Jedzenie")
        awaitText("Jajko")
        shot("pl_food_01_day")
        tap("Dodaj: Obiad")
        type("Szukaj lub wpisz kod kreskowy", "piers z kurczaka")
        awaitText("Pierś z kurczaka, surowa")
        shot("pl_food_02_search")
    }

    @Test fun `backup carries the food diary`() = app(
        prepare = {
            food.setGoals(NutritionGoals(kcal = 2000))
            logFood("oats", Meal.BREAKFAST, 80.0, daysAgo = 2)
            food.createFood(FoodDraft("Protein bar", "Acme", null, 380.0, 30.0, 40.0, 12.0, 60.0))
        },
    ) { repository ->
        val before = repository.exportBackup()
        assertEquals(1, before.foodEntries.size)
        assertEquals(2, before.foods.size)
        val restored = decodeBackup(before.encode())
        repository.replaceAllData(restored)
        val after = repository.exportBackup()
        assertEquals(before.foods.toSet(), after.foods.toSet())
        assertEquals(before.foodEntries, after.foodEntries)
        assertEquals(2000, after.nutritionGoals?.kcal)

        // A backup from before food tracking still restores, with an empty diary.
        val oldFormat = before.copy(version = 1, foods = emptyList(), foodEntries = emptyList(), nutritionGoals = null).encode()
        repository.replaceAllData(decodeBackup(oldFormat))
        assertTrue(repository.exportBackup().foodEntries.isEmpty())
    }

    @Test fun `meal photo goes from the key guide to a reviewed diary entry`() {
        google = FakeGoogle { method, _, _ ->
            if (method == "GET") HttpResult(200, """{"models":[]}""") else HttpResult(200, geminiReply(TWO_FOODS))
        }
        app {
            openFood()
            tap("Photo of Lunch")
            awaitText("Meal photos with Gemini")
            waitForIdle()
            shot("ai_01_guide")
            type("API key", "AIzaTestKey123456")
            onNodeWithText("Save key").performClick()

            awaitText("Anything to add?")
            assertEquals("AIzaTestKey123456", aiSettings.key)
            type("Note about the meal", "fried in olive oil")
            waitForIdle()
            shot("ai_02_note")
            onNodeWithText("Estimate").performClick()

            awaitText("Fried chicken breast")
            awaitText("Assumed one tablespoon of olive oil.")
            shot("ai_03_review")
            type("White rice, grams", "200")
            awaitText("260 kcal · P 5 · C 56 · F 1")
            onAllNodesWithText("Add to Lunch").onLast().performClick()

            await { runBlocking { food.observeEntries(today).first() }.size == 2 }
            val entries = runBlocking { food.observeEntries(today).first() }
            assertTrue(entries.all { it.meal == Meal.LUNCH.code && it.food.brand == "Gemini" })
            val rice = entries.single { it.food.name == "White rice" }
            assertEquals(200.0, rice.grams, 0.0)
            assertEquals(130.0, rice.food.kcal, 0.01)
            val sent = google.requests.last().third!!
            assertTrue("fried in olive oil" in sent)
            awaitText("Fried chicken breast")
            shot("ai_04_logged")
            // AI foods come back as recents for a one-tap repeat, but stay out of My foods.
            assertTrue(runBlocking { food.observeOwnFoods().first() }.isEmpty())
        }
    }

    @Test fun `a refused key keeps the guide open with the reason`() {
        google = FakeGoogle { _, _, _ -> HttpResult(400, """{"error":{"code":400,"message":"API key not valid."}}""") }
        app {
            openFood()
            tap("Photo of Breakfast")
            type("API key", "nope")
            onNodeWithText("Save key").performClick()
            awaitText("Google didn't accept this key. Check that you copied all of it.")
            shot("ai_05_bad_key")
            assertEquals(null, aiSettings.key)
        }
    }

    private fun SkikoComposeUiTest.openMe(label: String = "Me") {
        tap("Menu")
        awaitText(label)
        onNodeWithText(label).performClick()
        waitForIdle()
    }

    @Test fun `menu lists screens first, then settings`() = app {
        tap("Menu")
        awaitText("Your API key")
        waitForIdle()
        shot("me_00_drawer")
        val order = listOf("Workout", "Food", "Me", "Routines", "Your API key", "Backup")
        val tops = order.map { label -> onNodeWithText(label).fetchSemanticsNode().boundsInRoot.top }
        assertEquals(tops.sorted(), tops)
    }

    @Test fun `measurements are logged per day and charted`() = app(
        prepare = {
            it.saveMeasurement("waist", today - 30, 88.0)
            it.saveMeasurement("waist", today - 14, 86.5)
            it.saveMeasurement("waist", today - 3, 85.0)
        },
    ) { repository ->
        openMe()
        awaitText("Body weight")
        onNodeWithText("Waist").performClick()
        awaitText("85 cm")
        awaitText("−3 cm since ${LocalDate.ofEpochDay(today - 30).dayOfMonth} ${LocalDate.ofEpochDay(today - 30).month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH)}")
        shot("me_01_waist_chart")

        onNodeWithText("Add").performClick()
        awaitDescription("Waist")
        type("Waist", "84,5")
        waitForIdle()
        shot("me_02_add")
        onNodeWithText("Save").performClick()
        awaitText("84.5 cm")
        val waist = runBlocking { repository.observeMeasurements().first() }.filter { it.kind == "waist" }
        assertEquals(4, waist.size)
        assertEquals(84.5, waist.single { it.epochDay == today }.value, 0.0)

        // Saving again on the same day replaces that day's value.
        onNodeWithText("Add").performClick()
        type("Waist", "84")
        onNodeWithText("Save").performClick()
        await { runBlocking { repository.observeMeasurements().first() }.count { it.kind == "waist" } == 4 }

        // Past entries can be fixed or removed.
        onNodeWithText(EnglishStrings.fullDateText(LocalDate.ofEpochDay(today - 14))).performClick()
        awaitText("Remove")
        onNodeWithText("Remove").performClick()
        await { runBlocking { repository.observeMeasurements().first() }.count { it.kind == "waist" } == 3 }
    }

    @Test fun `BMI is worked out from height and body weight`() = app(
        prepare = {
            it.saveMeasurement("weight", today - 20, 86.0)
            it.saveMeasurement("weight", today - 2, 83.2)
        },
    ) {
        openMe()
        onNodeWithText("BMI").performClick()
        awaitText("Enter your height once, and BMI is worked out from every body weight you log.")
        onNodeWithText("Set height").performClick()
        awaitDescription("Height")
        type("Height", "182")
        onNodeWithText("Save").performClick()
        awaitText("25.1")
        awaitText("Height: 182 cm")
        shot("me_03_bmi")
    }

    @Test fun `Me renders in Polish`() = app(
        prepare = {
            it.saveMeasurement("weight", today - 40, 88.0)
            it.saveMeasurement("weight", today - 25, 86.4)
            it.saveMeasurement("weight", today - 10, 85.1)
            it.saveMeasurement("weight", today, 84.3)
        },
    ) {
        switchLanguage("Polski")
        openMe("Pomiary")
        awaitText("Zawartość tłuszczu")
        awaitText("84,3 kg")
        shot("pl_me_01_weight")
    }

    @Test fun `food history keeps the last hundred foods and the totals stay pinned`() = app(
        prepare = {
            food.setGoals(NutritionGoals(kcal = 2500, protein = 160, carbs = 280, fat = 80))
            listOf("apple", "banana", "oats", "egg", "white rice dry", "chicken breast raw", "salmon raw", "tofu", "almonds",
                "walnuts", "milk 2", "butter", "honey", "carrot", "tomato", "cucumber", "broccoli", "spinach", "pear", "kiwi",
                "plum", "peach", "grapes", "avocado", "rye bread", "potatoes boiled", "pasta cooked", "cod raw")
                .forEachIndexed { i, query -> logFood(query, Meal.entries[i % Meal.entries.size], 100.0) }
        },
    ) {
        assertEquals(28, runBlocking { food.observeRecent().first() }.size)
        openFood()
        awaitText("Protein")
        onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText("Supper"))
        waitForIdle()
        awaitText("2500 kcal", substring = true)
        shot("food_09_pinned_totals")
        tap("Add to Supper")
        awaitText("HISTORY")
        awaitText("Cod, raw")
    }

    @Test fun `backup carries measurements`() = app(
        prepare = { it.saveMeasurement("chest", today - 1, 104.0) },
    ) { repository ->
        val before = repository.exportBackup()
        assertEquals(1, before.measurements.size)
        repository.replaceAllData(decodeBackup(before.encode()))
        assertEquals(before.measurements, repository.exportBackup().measurements)
    }
}

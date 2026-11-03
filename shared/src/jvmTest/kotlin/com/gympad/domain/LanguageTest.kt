package com.gympad.domain

import com.gympad.data.Exercise
import com.gympad.ui.strings.EnglishStrings
import com.gympad.ui.strings.PolishStrings
import com.gympad.ui.strings.fullDateText
import com.gympad.ui.strings.fullDayText
import com.gympad.ui.strings.liftText
import com.gympad.ui.strings.localizeDecimal
import com.gympad.ui.strings.recordText
import com.gympad.ui.strings.shortDateText
import com.gympad.ui.strings.weightText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class LanguageTest {
    private val sunday = LocalDate.of(2026, 11, 1)

    @Test fun `stored choice beats the device language`() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.resolve("en", "pl"))
        assertEquals(AppLanguage.POLISH, AppLanguage.resolve("pl", "en"))
    }

    @Test fun `first launch follows the device language and falls back to English`() {
        assertEquals(AppLanguage.POLISH, AppLanguage.resolve(null, "pl"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.resolve(null, "en"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.resolve(null, "de"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.resolve("xx", "de"))
    }

    @Test fun `Polish plurals use all three forms`() {
        val expected = mapOf(
            1 to "1 seria", 2 to "2 serie", 4 to "4 serie", 5 to "5 serii", 11 to "11 serii",
            12 to "12 serii", 14 to "14 serii", 21 to "21 serii", 22 to "22 serie", 112 to "112 serii",
        )
        expected.forEach { (count, text) -> assertEquals(text, PolishStrings.setCount(count)) }
        assertEquals("1 trening", PolishStrings.workoutCount(1))
        assertEquals("3 treningi", PolishStrings.workoutCount(3))
        assertEquals("5 treningów", PolishStrings.workoutCount(5))
        assertEquals("2 serie niewykonane", PolishStrings.setsNotDone(2))
    }

    @Test fun `English plurals`() {
        assertEquals("1 set", EnglishStrings.setCount(1))
        assertEquals("2 sets", EnglishStrings.setCount(2))
        assertEquals("1 workout", EnglishStrings.workoutCount(1))
        assertEquals("1 set not done", EnglishStrings.setsNotDone(1))
    }

    @Test fun `dates use the right grammatical form`() {
        assertEquals("Niedziela, 1 listopada", PolishStrings.fullDayText(sunday))
        assertEquals("1 listopada 2026", PolishStrings.fullDateText(sunday))
        assertEquals("1 lis", PolishStrings.shortDateText(sunday))
        assertEquals("Listopad", PolishStrings.monthNames[sunday.monthValue - 1])
        assertEquals("Sunday, 1 November", EnglishStrings.fullDayText(sunday))
        assertEquals("1 Nov", EnglishStrings.shortDateText(sunday))
    }

    @Test fun `numbers use each language's decimal separator`() {
        assertEquals("82,5", PolishStrings.weightText(82.5))
        assertEquals("82.5", EnglishStrings.weightText(82.5))
        assertEquals("80", PolishStrings.weightText(80.0))
        assertEquals("82,5×8", PolishStrings.liftText(LiftValue(82.5, 8)))
        assertEquals("82,5", PolishStrings.localizeDecimal(sanitizeWeight("82,5")))
    }

    @Test fun `bodyweight lifts have a label in both languages`() {
        assertEquals("BW×12", EnglishStrings.liftText(LiftValue(0.0, 12)))
        assertEquals("BW × 12", EnglishStrings.recordText(LiftValue(0.0, 12)))
        assertEquals("MC×12", PolishStrings.liftText(LiftValue(0.0, 12)))
        assertEquals("82,5 kg × 5", PolishStrings.recordText(LiftValue(82.5, 5)))
    }

    @Test fun `built-in names translate for display and map back`() {
        assertEquals("Przysiad ze sztangą", BuiltInNames.exercise("Back Squat", AppLanguage.POLISH))
        assertEquals("Back Squat", BuiltInNames.exercise("Back Squat", AppLanguage.ENGLISH))
        assertEquals("Back Squat", BuiltInNames.canonicalExercise("przysiad ze sztangą"))
    }

    @Test fun `names the user typed are never translated`() {
        assertEquals("Wyciskanie francuskie", BuiltInNames.exercise("Wyciskanie francuskie", AppLanguage.POLISH))
        assertEquals("Wyciskanie francuskie", BuiltInNames.exercise("Wyciskanie francuskie", AppLanguage.ENGLISH))
        assertEquals("Wyciskanie francuskie", BuiltInNames.canonicalExercise("Wyciskanie francuskie"))
    }

    @Test fun `matching ignores case for Polish letters too`() {
        val exercises = listOf(Exercise(1, "Back Squat"), Exercise(2, "Łydki na maszynie"))
        assertEquals(2L, exercises.matching("ŁYDKI NA MASZYNIE")?.id)
        assertEquals(1L, exercises.matching("PRZYSIAD ZE SZTANGĄ")?.id)
        assertEquals(1L, exercises.matching("back squat")?.id)
        assertNull(exercises.matching("Nowe ćwiczenie"))
    }

    @Test fun `an exercise the user named wins over a built-in with the same wording`() {
        val exercises = listOf(Exercise(1, "Back Squat"), Exercise(2, "Przysiad ze sztangą"))
        assertEquals(2L, exercises.matching("Przysiad ze sztangą")?.id)
    }
}

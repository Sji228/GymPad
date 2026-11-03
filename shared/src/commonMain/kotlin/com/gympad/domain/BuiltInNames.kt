package com.gympad.domain

import com.gympad.data.Exercise

/**
 * Names the app itself ships are stored in canonical English and translated only for display.
 * Names the user typed are their own data and are never translated.
 *
 * History, PRs and ghost values all follow the exercise id, so changing the language writes
 * nothing to the database and can't lose or split any progress.
 */
object BuiltInNames {
    private val polishExercises = mapOf(
        "Barbell Bench Press" to "Wyciskanie sztangi na ławce",
        "Dumbbell Bench Press" to "Wyciskanie hantli na ławce",
        "Chest Press Machine" to "Wyciskanie na maszynie",
        "Overhead Press" to "Wyciskanie żołnierskie",
        "Dumbbell Shoulder Press" to "Wyciskanie hantli nad głowę",
        "Cable Fly" to "Rozpiętki na wyciągu",
        "Barbell Row" to "Wiosłowanie sztangą",
        "Seated Cable Row" to "Wiosłowanie na wyciągu siedząc",
        "Pull-up" to "Podciąganie na drążku",
        "Lat Pulldown" to "Ściąganie drążka wyciągu",
        "Back Squat" to "Przysiad ze sztangą",
        "Leg Press" to "Wypychanie na suwnicy",
        "Romanian Deadlift" to "Martwy ciąg rumuński",
        "Leg Curl" to "Uginanie nóg na maszynie",
    )

    private val canonicalByPolish = polishExercises.entries.associate { (english, polish) ->
        polish.lowercase() to english
    }

    private val polishRoutines = mapOf("Legs" to "Nogi")

    fun exercise(name: String, language: AppLanguage): String = when (language) {
        AppLanguage.ENGLISH -> name
        AppLanguage.POLISH -> polishExercises[name] ?: name
    }

    fun routine(name: String, language: AppLanguage): String = when (language) {
        AppLanguage.ENGLISH -> name
        AppLanguage.POLISH -> polishRoutines[name] ?: name
    }

    /** Typing a built-in's Polish name must reach the same exercise, not start a second history. */
    fun canonicalExercise(typed: String): String = canonicalByPolish[typed.lowercase()] ?: typed
}

/**
 * Compared in Kotlin because SQLite's NOCASE only folds ASCII, which would treat "Łydki" and
 * "łydki" as two different exercises. A literal match comes first so an exercise the user named
 * themselves is never captured by a built-in translation with the same wording.
 */
fun List<Exercise>.matching(typed: String): Exercise? {
    val canonical = BuiltInNames.canonicalExercise(typed)
    return firstOrNull { it.name.equals(typed, ignoreCase = true) }
        ?: firstOrNull { it.name.equals(canonical, ignoreCase = true) }
}

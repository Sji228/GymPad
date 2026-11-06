package com.gympad

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.room.Room
import com.gympad.data.AiSettingsStore
import com.gympad.data.FoodCatalog
import com.gympad.data.FoodRepository
import com.gympad.data.GymDatabase
import com.gympad.data.GymRepository
import com.gympad.data.LanguageStore
import com.gympad.data.MealAnalyzer
import com.gympad.data.buildGymDatabase
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.LocalDate

fun main() {
    val dataDir = File(System.getProperty("user.home"), ".local/share/GymPad").apply { mkdirs() }
    val database = Room.databaseBuilder<GymDatabase>(name = File(dataDir, "gympad.db").absolutePath)
        .buildGymDatabase()
    val repository = GymRepository(database)
    val foodRepository = FoodRepository(database, FoodCatalog(File(dataDir, "catalog")))
    val languageFile = File(dataDir, "language")
    val languageStore = object : LanguageStore {
        override fun load() = languageFile.takeIf { it.exists() }?.readText()
        override fun save(code: String) = languageFile.writeText(code)
    }
    val aiDir = File(dataDir, "gemini")
    val mealAnalyzer = MealAnalyzer(
        object : AiSettingsStore {
            fun file(name: String) = File(aiDir, name)
            fun read(name: String) = file(name).takeIf { it.exists() }?.readText()?.ifBlank { null }
            fun write(name: String, value: String?) {
                aiDir.mkdirs()
                if (value == null) file(name).delete() else file(name).writeText(value)
            }
            override fun loadKey() = read("key")
            override fun saveKey(key: String?) = write("key", key)
            override fun loadModel() = read("model")
            override fun saveModel(model: String?) = write("model", model)
        },
    )
    runBlocking { repository.seedIfEmpty() }

    // There's no install date on desktop; starting at New Year keeps the ribbon and year view browsable.
    val installEpochDay = LocalDate.now().withDayOfYear(1).toEpochDay()

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "GymPad",
            state = rememberWindowState(width = 400.dp, height = 820.dp),
        ) {
            GymPadRoot(repository, foodRepository, mealAnalyzer, installEpochDay, languageStore)
        }
    }
}

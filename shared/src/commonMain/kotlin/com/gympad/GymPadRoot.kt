package com.gympad

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gympad.data.FoodRepository
import com.gympad.data.GymRepository
import com.gympad.data.LanguageStore
import com.gympad.data.MealAnalyzer
import com.gympad.domain.AppLanguage
import com.gympad.ui.AiSetupSheet
import com.gympad.ui.AppDrawer
import com.gympad.ui.BackupScreen
import com.gympad.ui.BackupViewModel
import com.gympad.ui.Destination
import com.gympad.ui.EditorScreen
import com.gympad.ui.EditorViewModel
import com.gympad.ui.FoodScreen
import com.gympad.ui.FoodViewModel
import com.gympad.ui.MainScreen
import com.gympad.ui.MainViewModel
import com.gympad.ui.MealPhotoViewModel
import com.gympad.ui.strings.LocalStrings
import com.gympad.ui.strings.strings
import com.gympad.ui.theme.GymPadTheme
import java.util.Locale

private enum class Screen { MAIN, FOOD, EDITOR, BACKUP }

@Composable
fun GymPadRoot(
    repository: GymRepository,
    foodRepository: FoodRepository,
    mealAnalyzer: MealAnalyzer,
    installEpochDay: Long,
    languageStore: LanguageStore,
) {
    var language by remember { mutableStateOf(AppLanguage.resolve(languageStore.load(), Locale.getDefault().language)) }
    CompositionLocalProvider(LocalStrings provides language.strings) {
        GymPadTheme {
            AppContent(
                repository = repository,
                foodRepository = foodRepository,
                mealAnalyzer = mealAnalyzer,
                installEpochDay = installEpochDay,
                language = language,
                onSelectLanguage = {
                    language = it
                    languageStore.save(it.code)
                },
            )
        }
    }
}

@Composable
private fun AppContent(
    repository: GymRepository,
    foodRepository: FoodRepository,
    mealAnalyzer: MealAnalyzer,
    installEpochDay: Long,
    language: AppLanguage,
    onSelectLanguage: (AppLanguage) -> Unit,
) {
    Surface {
        var screen by rememberSaveable { mutableStateOf(Screen.MAIN) }
        // Routines and Backup return to whichever of Workout or Food they were opened from.
        var home by rememberSaveable { mutableStateOf(Destination.WORKOUT) }
        val mainViewModel = viewModel { MainViewModel(repository, installEpochDay) }
        val editorViewModel = viewModel { EditorViewModel(repository) }
        val foodViewModel = viewModel { FoodViewModel(foodRepository, installEpochDay) }
        val mealPhotoViewModel = viewModel { MealPhotoViewModel(mealAnalyzer, foodRepository) }
        val aiSetupOpen by mealPhotoViewModel.setupOpen.collectAsStateWithLifecycle()
        LifecycleResumeEffect(Unit) {
            mainViewModel.refreshToday()
            foodViewModel.refreshToday()
            onPauseOrDispose { }
        }
        fun homeScreen() = if (home == Destination.FOOD) Screen.FOOD else Screen.MAIN
        val drawer: @Composable (closeDrawer: () -> Unit) -> Unit = { closeDrawer ->
            AppDrawer(
                current = home,
                language = language,
                onSelectLanguage = {
                    onSelectLanguage(it)
                    closeDrawer()
                },
                onNavigate = {
                    closeDrawer()
                    home = it
                    screen = homeScreen()
                },
                onRoutines = {
                    closeDrawer()
                    editorViewModel.selectRoutine(mainViewModel.uiState.value.routineId)
                    screen = Screen.EDITOR
                },
                onAiPhotos = {
                    closeDrawer()
                    mealPhotoViewModel.openSetup()
                },
                onBackup = {
                    closeDrawer()
                    screen = Screen.BACKUP
                },
            )
        }
        when (screen) {
            Screen.MAIN -> MainScreen(
                viewModel = mainViewModel,
                editor = editorViewModel,
                drawerContent = drawer,
            )
            Screen.FOOD -> FoodScreen(
                viewModel = foodViewModel,
                mealPhoto = mealPhotoViewModel,
                drawerContent = drawer,
                onBack = {
                    home = Destination.WORKOUT
                    screen = Screen.MAIN
                },
            )
            Screen.EDITOR -> EditorScreen(
                viewModel = editorViewModel,
                onBack = { screen = homeScreen() },
            )
            Screen.BACKUP -> BackupScreen(
                viewModel = viewModel { BackupViewModel(repository) },
                onBack = { screen = homeScreen() },
            )
        }
        if (aiSetupOpen) {
            AiSetupSheet(mealPhotoViewModel)
        }
    }
}

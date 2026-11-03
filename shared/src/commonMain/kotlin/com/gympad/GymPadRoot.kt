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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gympad.data.GymRepository
import com.gympad.data.LanguageStore
import com.gympad.domain.AppLanguage
import com.gympad.ui.BackupScreen
import com.gympad.ui.BackupViewModel
import com.gympad.ui.EditorScreen
import com.gympad.ui.EditorViewModel
import com.gympad.ui.MainScreen
import com.gympad.ui.MainViewModel
import com.gympad.ui.strings.LocalStrings
import com.gympad.ui.strings.strings
import com.gympad.ui.theme.GymPadTheme
import java.util.Locale

private enum class Screen { MAIN, EDITOR, BACKUP }

@Composable
fun GymPadRoot(repository: GymRepository, installEpochDay: Long, languageStore: LanguageStore) {
    var language by remember { mutableStateOf(AppLanguage.resolve(languageStore.load(), Locale.getDefault().language)) }
    CompositionLocalProvider(LocalStrings provides language.strings) {
        GymPadTheme {
            AppContent(
                repository = repository,
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
    installEpochDay: Long,
    language: AppLanguage,
    onSelectLanguage: (AppLanguage) -> Unit,
) {
    Surface {
        var screen by rememberSaveable { mutableStateOf(Screen.MAIN) }
        val mainViewModel = viewModel { MainViewModel(repository, installEpochDay) }
        val editorViewModel = viewModel { EditorViewModel(repository) }
        LifecycleResumeEffect(Unit) {
            mainViewModel.refreshToday()
            onPauseOrDispose { }
        }
        when (screen) {
            Screen.MAIN -> MainScreen(
                viewModel = mainViewModel,
                editor = editorViewModel,
                language = language,
                onSelectLanguage = onSelectLanguage,
                onOpenEditor = { routineId ->
                    editorViewModel.selectRoutine(routineId)
                    screen = Screen.EDITOR
                },
                onOpenBackup = { screen = Screen.BACKUP },
            )
            Screen.EDITOR -> EditorScreen(
                viewModel = editorViewModel,
                onBack = { screen = Screen.MAIN },
            )
            Screen.BACKUP -> BackupScreen(
                viewModel = viewModel { BackupViewModel(repository) },
                onBack = { screen = Screen.MAIN },
            )
        }
    }
}

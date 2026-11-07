package com.gympad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.sqlite.SQLiteException
import com.gympad.data.BackupFile
import com.gympad.data.GymRepository
import com.gympad.data.InvalidBackupException
import com.gympad.data.InvalidBackupReason
import com.gympad.data.decodeBackup
import com.gympad.data.encode
import com.gympad.ui.strings.AppStrings
import com.gympad.ui.strings.LocalStrings
import com.gympad.ui.strings.backupSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate

sealed interface BackupMessage {
    data class Exported(val workouts: Int, val sets: Int, val foodEntries: Int, val measurements: Int) : BackupMessage
    data class Restored(val workouts: Int, val sets: Int, val foodEntries: Int, val measurements: Int) : BackupMessage
    data class Invalid(val reason: InvalidBackupReason) : BackupMessage
    data object ExportFailed : BackupMessage
    data object ImportFailed : BackupMessage
    data object Damaged : BackupMessage
}

data class BackupUiState(
    val message: BackupMessage? = null,
    val pendingImport: BackupFile? = null,
)

class BackupViewModel(private val repository: GymRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState

    fun export(writeFile: suspend (String) -> Unit) {
        viewModelScope.launch {
            _uiState.value = try {
                val backup = repository.exportBackup()
                withContext(Dispatchers.IO) { writeFile(backup.encode()) }
                BackupUiState(message = BackupMessage.Exported(backup.sessions.size, backup.sets.size, backup.foodEntries.size, backup.measurements.size))
            } catch (_: IOException) {
                BackupUiState(message = BackupMessage.ExportFailed)
            }
        }
    }

    fun readImport(readFile: suspend () -> String) {
        viewModelScope.launch {
            _uiState.value = try {
                val backup = withContext(Dispatchers.IO) { decodeBackup(readFile()) }
                BackupUiState(pendingImport = backup)
            } catch (_: IOException) {
                BackupUiState(message = BackupMessage.ImportFailed)
            } catch (e: InvalidBackupException) {
                BackupUiState(message = BackupMessage.Invalid(e.reason))
            }
        }
    }

    fun confirmImport() {
        val backup = _uiState.value.pendingImport ?: return
        viewModelScope.launch {
            _uiState.value = try {
                repository.replaceAllData(backup)
                BackupUiState(message = BackupMessage.Restored(backup.sessions.size, backup.sets.size, backup.foodEntries.size, backup.measurements.size))
            } catch (_: SQLiteException) {
                BackupUiState(message = BackupMessage.Damaged)
            }
        }
    }

    fun cancelImport() {
        _uiState.value = BackupUiState()
    }
}

private fun AppStrings.text(message: BackupMessage): String = when (message) {
    is BackupMessage.Exported -> exported(backupSummary(message.workouts, message.sets, message.foodEntries, message.measurements))
    is BackupMessage.Restored -> restored(backupSummary(message.workouts, message.sets, message.foodEntries, message.measurements))
    is BackupMessage.Invalid -> when (message.reason) {
        InvalidBackupReason.NOT_A_BACKUP -> backupNotRecognized
        InvalidBackupReason.TOO_NEW -> backupTooNew
        InvalidBackupReason.MISSING_ROUTINES -> backupMissingRoutines
    }
    BackupMessage.ExportFailed -> exportFailed
    BackupMessage.ImportFailed -> importFailed
    BackupMessage.Damaged -> backupDamaged
}

@Composable
fun BackupScreen(viewModel: BackupViewModel, onBack: () -> Unit) {
    val strings = LocalStrings.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val picker = rememberBackupFilePicker(onExportTo = viewModel::export, onImportFrom = viewModel::readImport)
    val colors = MaterialTheme.colorScheme

    PlatformBackHandler(onBack = onBack)

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding(),
    ) {
        ScreenTopBar(strings.backup, onBack)
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BackupAction(
                icon = GymIcons.Export,
                title = strings.exportTitle,
                detail = strings.exportDetail,
                onClick = { picker.pickExportFile("gympad-backup-${LocalDate.now()}.json") },
            )
            BackupAction(
                icon = GymIcons.Import,
                title = strings.importTitle,
                detail = strings.importDetail,
                onClick = picker.pickImportFile,
            )
            state.message?.let { message ->
                Text(
                    strings.text(message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                )
            }
        }
    }

    state.pendingImport?.let { backup ->
        AlertDialog(
            onDismissRequest = viewModel::cancelImport,
            containerColor = colors.surfaceContainerHigh,
            title = { Text(strings.replaceTitle, style = MaterialTheme.typography.titleMedium) },
            text = {
                Text(
                    strings.replaceDetail(strings.backupSummary(backup.sessions.size, backup.sets.size, backup.foodEntries.size, backup.measurements.size)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmImport) {
                    Text(strings.replaceConfirm, style = MaterialTheme.typography.labelLarge, color = colors.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelImport) {
                    Text(strings.cancel, style = MaterialTheme.typography.labelLarge)
                }
            },
        )
    }
}

@Composable
private fun BackupAction(icon: ImageVector, title: String, detail: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(colors.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = colors.onSurfaceVariant)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

package com.gympad.ui

import androidx.compose.runtime.Composable

@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)

class BackupFilePicker(
    val pickExportFile: (suggestedName: String) -> Unit,
    val pickImportFile: () -> Unit,
)

@Composable
expect fun rememberBackupFilePicker(
    onExportTo: (writeFile: suspend (String) -> Unit) -> Unit,
    onImportFrom: (readFile: suspend () -> String) -> Unit,
): BackupFilePicker

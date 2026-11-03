package com.gympad.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.io.IOException

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled, onBack)
}

@Composable
actual fun rememberBackupFilePicker(
    onExportTo: (writeFile: suspend (String) -> Unit) -> Unit,
    onImportFrom: (readFile: suspend () -> String) -> Unit,
): BackupFilePicker {
    val resolver = LocalContext.current.contentResolver
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            onExportTo { text ->
                val stream = resolver.openOutputStream(uri, "wt") ?: throw IOException("Can't open file")
                stream.bufferedWriter().use { it.write(text) }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            onImportFrom {
                val stream = resolver.openInputStream(uri) ?: throw IOException("Can't open file")
                stream.bufferedReader().use { it.readText() }
            }
        }
    }
    return remember(exportLauncher, importLauncher) {
        BackupFilePicker(
            pickExportFile = exportLauncher::launch,
            pickImportFile = { importLauncher.launch(arrayOf("application/json", "text/*", "application/octet-stream")) },
        )
    }
}

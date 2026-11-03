package com.gympad.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

// A desktop window has no system back; every screen also has an on-screen way back.
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

@Composable
actual fun rememberBackupFilePicker(
    onExportTo: (writeFile: suspend (String) -> Unit) -> Unit,
    onImportFrom: (readFile: suspend () -> String) -> Unit,
): BackupFilePicker = remember {
    BackupFilePicker(
        pickExportFile = { name ->
            chooseFile("Export backup", FileDialog.SAVE, name)?.let { file ->
                onExportTo { text -> file.writeText(text) }
            }
        },
        pickImportFile = {
            chooseFile("Import backup", FileDialog.LOAD, null)?.let { file ->
                onImportFrom { file.readText() }
            }
        },
    )
}

private fun chooseFile(title: String, mode: Int, suggestedName: String?): File? {
    val dialog = FileDialog(null as Frame?, title, mode)
    dialog.file = suggestedName
    dialog.isVisible = true
    val name = dialog.file ?: return null
    return File(dialog.directory, name)
}

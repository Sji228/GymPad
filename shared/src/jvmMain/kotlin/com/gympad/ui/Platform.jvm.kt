package com.gympad.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import java.awt.FileDialog
import java.awt.Frame
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

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

actual val canScanBarcodes = false

@Composable
actual fun BarcodeScanner(onScanned: (String) -> Unit, onDismiss: () -> Unit) {
    LaunchedEffect(Unit) { onDismiss() }
}

/** Desktop has no camera: picking an image file stands in for it, which is enough to try the flow. */
@Composable
actual fun MealCamera(onCaptured: (ByteArray) -> Unit, onDismiss: () -> Unit) {
    LaunchedEffect(Unit) {
        val file = chooseFile("Meal photo", FileDialog.LOAD, null)
        val image = file?.let { runCatching { ImageIO.read(it) }.getOrNull() }
        if (image == null) {
            onDismiss()
            return@LaunchedEffect
        }
        val scale = minOf(1.0, 1280.0 / maxOf(image.width, image.height))
        val width = (image.width * scale).toInt()
        val height = (image.height * scale).toInt()
        val scaled = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        scaled.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            drawImage(image, 0, 0, width, height, null)
            dispose()
        }
        val out = ByteArrayOutputStream()
        ImageIO.write(scaled, "jpg", out)
        onCaptured(out.toByteArray())
    }
}

private fun chooseFile(title: String, mode: Int, suggestedName: String?): File? {
    val dialog = FileDialog(null as Frame?, title, mode)
    dialog.file = suggestedName
    dialog.isVisible = true
    val name = dialog.file ?: return null
    return File(dialog.directory, name)
}

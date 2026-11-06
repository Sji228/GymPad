package com.gympad.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

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

/** Desktop has no camera; there a barcode can still be typed into the search field. */
expect val canScanBarcodes: Boolean

/** A full-screen camera view that reports the first product barcode it reads. */
@Composable
expect fun BarcodeScanner(onScanned: (String) -> Unit, onDismiss: () -> Unit)

/** A full-screen camera for one meal photo, with a gallery button. Reports a JPEG sized for upload. */
@Composable
expect fun MealCamera(onCaptured: (ByteArray) -> Unit, onDismiss: () -> Unit)

typealias MealCameraContent = @Composable (onCaptured: (ByteArray) -> Unit, onDismiss: () -> Unit) -> Unit

/** Swappable, so tests can hand in a photo without a camera. */
val LocalMealCamera = staticCompositionLocalOf<MealCameraContent> { { onCaptured, onDismiss -> MealCamera(onCaptured, onDismiss) } }

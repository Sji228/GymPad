package com.gympad.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gympad.ui.strings.LocalStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max

/** Big enough for Gemini to tell rice from couscous, small enough to upload in a moment. */
private const val UPLOAD_EDGE = 1280

@Composable
actual fun MealCamera(onCaptured: (ByteArray) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var denials by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        granted = allowed
        if (!allowed) denials++
    }
    LaunchedEffect(Unit) {
        if (!granted) permission.launch(Manifest.permission.CAMERA)
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                val jpeg = withContext(Dispatchers.Default) { runCatching { jpegFromUri(context, uri) }.getOrNull() }
                if (jpeg != null) onCaptured(jpeg) else busy = false
            }
        }
    }
    val controller = remember {
        LifecycleCameraController(context).apply { setEnabledUseCases(CameraController.IMAGE_CAPTURE) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (granted) {
                CapturePreview(controller)
            } else if (denials > 0) {
                Column(
                    Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(strings.cameraPermission, style = MaterialTheme.typography.bodyMedium, color = Color.White, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(20.dp))
                    PrimaryButton(
                        strings.allowCamera,
                        onClick = {
                            val canAsk = (context as? Activity)?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == true
                            if (canAsk) {
                                permission.launch(Manifest.permission.CAMERA)
                            } else {
                                context.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                                )
                            }
                        },
                    )
                }
            }
            Row(Modifier.fillMaxWidth().safeDrawingPadding().padding(8.dp)) {
                IconAction(GymIcons.Close, strings.close, onDismiss, tint = Color.White)
            }
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .safeDrawingPadding()
                    .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconAction(
                    GymIcons.Gallery,
                    strings.choosePhoto,
                    onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    tint = Color.White,
                    size = 56.dp,
                    iconSize = 26.dp,
                    enabled = !busy,
                )
                Shutter(
                    enabled = granted && !busy,
                    description = strings.takePhoto,
                    onClick = {
                        busy = true
                        controller.takePicture(
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(image: ImageProxy) {
                                    val rotation = image.imageInfo.rotationDegrees
                                    val bitmap = image.toBitmap()
                                    image.close()
                                    scope.launch {
                                        onCaptured(withContext(Dispatchers.Default) { encodeForUpload(bitmap, rotation) })
                                    }
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    busy = false
                                }
                            },
                        )
                    },
                )
                Spacer(Modifier.size(56.dp))
            }
            if (busy) {
                CircularProgressIndicator(Modifier.align(Alignment.Center).size(40.dp), color = Color.White)
            }
        }
    }
}

@Composable
private fun CapturePreview(controller: LifecycleCameraController) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        controller.bindToLifecycle(lifecycleOwner)
        onDispose { controller.unbind() }
    }
    AndroidView(
        factory = { viewContext ->
            PreviewView(viewContext).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                this.controller = controller
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun Shutter(enabled: Boolean, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(76.dp)
            .border(4.dp, Color.White.copy(alpha = if (enabled) 1f else 0.4f), CircleShape)
            .padding(8.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (enabled) 1f else 0.4f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
    )
}

/** Turns the picture upright and shrinks it to [UPLOAD_EDGE] on its longer side. */
private fun encodeForUpload(source: Bitmap, rotationDegrees: Int): ByteArray {
    val scale = minOf(1f, UPLOAD_EDGE.toFloat() / max(source.width, source.height))
    val matrix = Matrix().apply {
        postScale(scale, scale)
        postRotate(rotationDegrees.toFloat())
    }
    val upright = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    return ByteArrayOutputStream().use { out ->
        upright.compress(Bitmap.CompressFormat.JPEG, 85, out)
        out.toByteArray()
    }
}

private fun jpegFromUri(context: Context, uri: Uri): ByteArray? {
    val resolver = context.contentResolver
    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        // ImageDecoder also applies the photo's EXIF rotation.
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
            val longest = max(info.size.width, info.size.height)
            if (longest > UPLOAD_EDGE) {
                val scale = UPLOAD_EDGE.toFloat() / longest
                decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= UPLOAD_EDGE) sample *= 2
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
    } ?: return null
    return encodeForUpload(bitmap, 0)
}

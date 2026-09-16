package chat.ratatosk.desktop.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import chat.ratatosk.desktop.ui.Strings
import chat.ratatosk.desktop.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.image.BufferedImage
import java.io.File

/** Сторона окошка кадрирования. */
private val VIEWPORT = 280.dp
private const val MIN_SCALE = 1f
private const val MAX_SCALE = 5f

/**
 * Кадрирование картинки под аватарку: колесо — масштаб, перетаскивание —
 * сдвиг. Показывается то же, что уйдёт в аккаунт, — круг поверх квадрата
 * только подсказывает, как лицо увидят в списках.
 *
 * Не получилось сжать — [onError]; звать `setAvatar(null)` здесь нельзя,
 * это стёрло бы прежнее лицо (ревью 2.7).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AvatarCropDialog(
    file: File,
    maxBytes: Int,
    title: String = Strings.AVATAR_CROP_TITLE,
    onDismiss: () -> Unit,
    onResult: (ByteArray) -> Unit,
    onError: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val viewportPx = remember(density) { with(density) { VIEWPORT.roundToPx() } }

    // Читаем с ограничением: фото на 50 Мп в памяти нам не нужно.
    var source by remember(file) { mutableStateOf<BufferedImage?>(null) }
    var loading by remember(file) { mutableStateOf(true) }
    var scale by remember(file) { mutableStateOf(1f) }
    var offsetX by remember(file) { mutableStateOf(0f) }
    var offsetY by remember(file) { mutableStateOf(0f) }
    var saving by remember(file) { mutableStateOf(false) }

    LaunchedEffect(file) {
        val image = withContext(Dispatchers.IO) { ImageUtils.readImageBounded(file, maxSide = 1024) }
        source = image
        loading = false
        if (image == null) {
            onError(Strings.AVATAR_CROP_FAILED)
            onDismiss()
        }
    }

    val bitmap = remember(source) { source?.toComposeImageBitmap() }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(title) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(VIEWPORT)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .onPointerEvent(PointerEventType.Scroll) { event ->
                            val delta = event.changes.first().scrollDelta.y
                            if (delta != 0f) scale = (scale * if (delta < 0) 1.1f else 1 / 1.1f).coerceIn(MIN_SCALE, MAX_SCALE)
                        }
                        .pointerInput(file) {
                            detectDragGestures { _, dragAmount ->
                                offsetX += dragAmount.x
                                offsetY += dragAmount.y
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        loading -> CircularProgressIndicator()
                        bitmap != null -> Image(
                            bitmap = bitmap,
                            contentDescription = file.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    translationX = offsetX
                                    translationY = offsetY
                                },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(Strings.AVATAR_CROP_HINT, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                Slider(
                    value = scale,
                    onValueChange = { scale = it },
                    valueRange = MIN_SCALE..MAX_SCALE,
                    modifier = Modifier.width(VIEWPORT),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = bitmap != null && !saving,
                onClick = {
                    val image = source ?: return@TextButton
                    saving = true
                    scope.launch {
                        val bytes = withContext(Dispatchers.IO) {
                            val rect = ImageUtils.avatarCropRect(image.width, image.height, scale, offsetX, offsetY, viewportPx)
                            ImageUtils.cropAvatar(image, rect, maxBytes)
                        }
                        saving = false
                        if (bytes != null) onResult(bytes) else onError(Strings.AVATAR_CROP_FAILED)
                        onDismiss()
                    }
                },
            ) { Text(Strings.SAVE) }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text(Strings.CANCEL) } },
    )
}

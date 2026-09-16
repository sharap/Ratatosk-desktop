package chat.ratatosk.desktop.ui.media

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import chat.ratatosk.desktop.model.ViewerMedia
import chat.ratatosk.desktop.ui.Strings
import chat.ratatosk.desktop.util.ClipboardUtils
import chat.ratatosk.desktop.util.FilePicker
import chat.ratatosk.desktop.util.FileUtils
import chat.ratatosk.desktop.util.Log

/**
 * Просмотр картинки своим окном: его можно растянуть или увести на второй
 * монитор, не закрывая переписку. Колесо — масштаб, перетаскивание — сдвиг,
 * двойной щелчок — вернуть как было, Esc — закрыть.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MediaViewerWindow(media: ViewerMedia, onClose: () -> Unit) {
    val state = rememberWindowState(width = 1000.dp, height = 720.dp, position = WindowPosition(Alignment.Center))
    var scale by remember(media.file) { mutableStateOf(1f) }
    var offsetX by remember(media.file) { mutableStateOf(0f) }
    var offsetY by remember(media.file) { mutableStateOf(0f) }

    // Читаем один раз на файл: картинка может быть на десятки мегабайт.
    // Декодер тот же, что у превью в чате: ImageIO не знает WebP, которым
    // снимают экран на телефоне.
    val bitmap: ImageBitmap? = remember(media.file) {
        runCatching { org.jetbrains.skia.Image.makeFromEncoded(media.file.readBytes()).toComposeImageBitmap() }
            .onFailure { Log.w(TAG, "Failed to read ${media.name}", it) }
            .getOrNull()
    }

    fun reset() {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
    }

    Window(
        onCloseRequest = onClose,
        state = state,
        title = media.name,
        onPreviewKeyEvent = { e ->
            if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) { onClose(); true } else false
        },
    ) {
        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            Column(Modifier.fillMaxSize()) {
                ViewerBar(
                    name = media.name,
                    scale = scale,
                    onZoomIn = { scale = (scale * 1.25f).coerceAtMost(MAX_SCALE) },
                    onZoomOut = { scale = (scale / 1.25f).coerceAtLeast(MIN_SCALE) },
                    onReset = { reset() },
                    onCopy = { bitmap?.let { ClipboardUtils.copyImage(it.toAwtImage()) } },
                    onSaveAs = {
                        val target = FilePicker.saveFile(Strings.FILE_SAVE, media.name)
                        if (target != null) runCatching { media.file.copyTo(target, overwrite = true) }
                            .onFailure { Log.w(TAG, "Failed to save ${media.name}", it) }
                    },
                    onShowInFolder = { FileUtils.openDirectory(media.file) },
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        // Увеличенная картинка рисуется дальше своих границ:
                        // без обрезки она наезжала на кнопки в шапке.
                        .clipToBounds()
                        .background(Color.Black)
                        // Колесо масштабирует и с Ctrl, и без: в просмотрщике
                        // прокручивать всё равно нечего.
                        .onPointerEvent(PointerEventType.Scroll) { event ->
                            val delta = event.changes.first().scrollDelta.y
                            if (delta != 0f) scale = (scale * if (delta < 0) 1.15f else 1 / 1.15f).coerceIn(MIN_SCALE, MAX_SCALE)
                        }
                        .pointerInput(media.file) {
                            detectTapGestures(onDoubleTap = { reset() })
                        }
                        .pointerInput(media.file) {
                            detectDragGestures { _, dragAmount ->
                                offsetX += dragAmount.x
                                offsetY += dragAmount.y
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (bitmap == null) {
                        Text(Strings.MEDIA_CANNOT_SHOW, color = Color.White)
                    } else {
                        Image(
                            bitmap = bitmap,
                            contentDescription = media.name,
                            contentScale = ContentScale.Fit,
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
            }
        }
    }
}

@Composable
private fun ViewerBar(
    name: String,
    scale: Float,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onReset: () -> Unit,
    onCopy: () -> Unit,
    onSaveAs: () -> Unit,
    onShowInFolder: () -> Unit,
) {
    Surface(tonalElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            IconButton(onClick = onZoomOut) { Icon(Icons.Default.ZoomOut, Strings.MEDIA_ZOOM_OUT) }
            TextButton(onClick = onReset) { Text("${(scale * 100).toInt()}%") }
            IconButton(onClick = onZoomIn) { Icon(Icons.Default.ZoomIn, Strings.MEDIA_ZOOM_IN) }
            IconButton(onClick = onCopy) { Icon(Icons.Default.ContentCopy, Strings.MEDIA_COPY) }
            IconButton(onClick = onSaveAs) { Icon(Icons.Default.SaveAlt, Strings.FILE_SAVE) }
            IconButton(onClick = onShowInFolder) { Icon(Icons.Default.FolderOpen, Strings.CHAT_SHOW_IN_FOLDER) }
        }
    }
}

private const val MIN_SCALE = 0.1f
private const val MAX_SCALE = 10f
private const val TAG = "MediaViewer"

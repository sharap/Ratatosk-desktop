package chat.ratatosk.desktop.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter

/**
 * Лицо человека или группы.
 *
 * @param size сторона круга; `null` — размер задаёт [modifier] (так фото
 *   профиля растягивается на всю ширину панели, как в Android).
 * @param shape круг в списках, прямоугольник — для большого фото.
 */
@Composable
fun Avatar(
    avatarBytes: ByteArray?,
    name: String,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp? = 40.dp,
    shape: androidx.compose.ui.graphics.Shape = CircleShape
) {
    Surface(
        modifier = if (size != null) modifier.size(size) else modifier,
        shape = shape,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        if (avatarBytes != null) {
            Image(
                painter = rememberAsyncImagePainter(model = avatarBytes),
                contentDescription = name,
                modifier = Modifier.fillMaxSize().clip(shape),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = name.take(1).uppercase(),
                    style = if (size == null || size > 60.dp) MaterialTheme.typography.displayMedium else MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

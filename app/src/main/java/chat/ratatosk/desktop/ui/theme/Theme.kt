package chat.ratatosk.desktop.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

@Composable
fun RatatoskTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeColor: Color = Color.Unspecified,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        themeColor != Color.Unspecified -> {
            val base = if (darkTheme) DarkColorScheme else LightColorScheme
            val isDark = themeColor.luminance() < 0.5f
            val isNeutral = themeColor == Color.Black || themeColor == Color.White || themeColor == Color.Gray
            
            base.copy(
                primary = themeColor,
                primaryContainer = if (isNeutral) {
                    if (darkTheme) Color.DarkGray else Color.LightGray
                } else themeColor,
                onPrimaryContainer = if (isDark) Color.White else Color.Black,
                secondary = themeColor.copy(alpha = 0.7f),
                onPrimary = if (isDark) Color.White else Color.Black,
                outline = themeColor.copy(alpha = 0.5f)
            )
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

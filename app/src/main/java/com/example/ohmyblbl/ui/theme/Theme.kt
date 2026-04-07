package cn.harkerhand.ohmyblbl.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val BlblColorScheme = lightColorScheme(
    primary = BlblPink,
    onPrimary = BlblSurface,
    secondary = BlblPinkSoft,
    onSecondary = BlblText,
    tertiary = BlblPinkStrong,
    background = BlblBgBottom,
    onBackground = BlblText,
    surface = BlblSurface,
    onSurface = BlblText,
    surfaceVariant = BlblSurfaceAlt,
    onSurfaceVariant = BlblTextSoft,
    outline = BlblBorder,
)

@Composable
fun OhMyBLBLTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = BlblColorScheme,
        typography = Typography,
        content = content,
    )
}


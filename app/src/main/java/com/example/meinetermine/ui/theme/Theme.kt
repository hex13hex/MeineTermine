package com.example.meinetermine.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = RedPrimary,
    onPrimary = White,

    secondary = RedDark,
    onSecondary = White,

    tertiary = RedPrimary,
    onTertiary = White,

    background = Black,
    onBackground = White,

    surface = DarkBackground,
    onSurface = White,

    surfaceVariant = Color(0xFF242424),
    onSurfaceVariant = LightGray
)

private val LightColorScheme = lightColorScheme(
    primary = RedPrimary,
    onPrimary = White,

    secondary = RedDark,
    onSecondary = White,

    tertiary = RedPrimary,
    onTertiary = White,

    background = White,
    onBackground = Black,

    surface = White,
    onSurface = Black,

    surfaceVariant = LightGray,
    onSurfaceVariant = Black
)

@Composable
fun MeineTermineTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {

    val colorScheme =
        if (darkTheme) {
            DarkColorScheme
        } else {
            LightColorScheme
        }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
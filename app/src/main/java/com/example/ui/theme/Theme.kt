package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = PolishedDarkPrimary,
    onPrimary = PolishedDarkOnPrimary,
    primaryContainer = PolishedDarkPrimaryContainer,
    onPrimaryContainer = PolishedDarkOnPrimaryContainer,
    background = PolishedDarkBackground,
    onBackground = PolishedDarkOnBackground,
    surface = PolishedDarkSurface,
    onSurface = PolishedDarkOnSurface,
    surfaceVariant = PolishedDarkSurfaceVariant,
    onSurfaceVariant = PolishedDarkOnSurfaceVariant,
    outline = PolishedDarkOutline,
    secondary = PolishedDarkSecondary,
    onSecondary = PolishedDarkOnSecondary,
    secondaryContainer = PolishedDarkSecondaryContainer,
    onSecondaryContainer = PolishedDarkOnSecondaryContainer
)

private val LightColorScheme = lightColorScheme(
    primary = PolishedPrimary,
    onPrimary = PolishedOnPrimary,
    primaryContainer = PolishedPrimaryContainer,
    onPrimaryContainer = PolishedOnPrimaryContainer,
    background = PolishedBackground,
    onBackground = PolishedOnBackground,
    surface = PolishedSurface,
    onSurface = PolishedOnSurface,
    surfaceVariant = PolishedSurfaceVariant,
    onSurfaceVariant = PolishedOnSurfaceVariant,
    outline = PolishedOutline,
    secondary = PolishedSecondary,
    onSecondary = PolishedOnSecondary,
    secondaryContainer = PolishedSecondaryContainer,
    onSecondaryContainer = PolishedOnSecondaryContainer
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Set to false to adhere strictly to the "Professional Polish" lavender/purple theme
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
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

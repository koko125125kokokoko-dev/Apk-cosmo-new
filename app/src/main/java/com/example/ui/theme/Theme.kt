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
    primary = FlatDarkPrimary,
    onPrimary = FlatDarkOnPrimary,
    primaryContainer = FlatDarkSurfaceVariant,
    onPrimaryContainer = FlatDarkTextPrimary,
    secondary = FlatDarkSecondary,
    background = FlatDarkBackground,
    onBackground = FlatDarkTextPrimary,
    surface = FlatDarkSurface,
    onSurface = FlatDarkTextPrimary,
    surfaceVariant = FlatDarkSurfaceVariant,
    onSurfaceVariant = FlatDarkTextSecondary,
    outline = FlatDarkOutline
)

private val LightColorScheme = lightColorScheme(
    primary = FlatLightPrimary,
    onPrimary = FlatLightOnPrimary,
    primaryContainer = FlatLightSurfaceVariant,
    onPrimaryContainer = FlatLightTextPrimary,
    secondary = FlatLightSecondary,
    background = FlatLightBackground,
    onBackground = FlatLightTextPrimary,
    surface = FlatLightSurface,
    onSurface = FlatLightTextPrimary,
    surfaceVariant = FlatLightSurfaceVariant,
    onSurfaceVariant = FlatLightTextSecondary,
    outline = FlatLightOutline
)

@Composable
fun CosmoGameStoreTheme(
    themePreference: String = "SYSTEM", // "SYSTEM", "LIGHT", "DARK"
    dynamicColor: Boolean = false, // Preserve flat crisp branding
    content: @Composable () -> Unit
) {
    val isDark = when (themePreference) {
        "DARK" -> true
        "LIGHT" -> false
        else -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

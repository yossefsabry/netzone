package com.netzone.app.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0061A4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD1E4FF),
    onPrimaryContainer = Color(0xFF001D36),
    secondary = Color(0xFF535F70),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD7E3F7),
    onSecondaryContainer = Color(0xFF101C2B),
    tertiary = Color(0xFF6B5778),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF2DAFF),
    onTertiaryContainer = Color(0xFF251431),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF7F8FC),
    onBackground = Color(0xFF171B22),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE8ECF4),
    onSurfaceVariant = Color(0xFF4A5260),
    outline = Color(0xFF73777F),
    outlineVariant = Color(0xFFC3C7CF),
    inverseSurface = Color(0xFF2F3033),
    inverseOnSurface = Color(0xFFF1F0F4),
    inversePrimary = Color(0xFFA5C9FF),
    surfaceTint = Color(0xFF0061A4)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF8CB4FF),
    onPrimary = Color(0xFF08203D),
    primaryContainer = Color(0xFF142A44),
    onPrimaryContainer = Color(0xFFD9E7FF),
    secondary = Color(0xFFBEC7D8),
    onSecondary = Color(0xFF243040),
    secondaryContainer = Color(0xFF2C3949),
    onSecondaryContainer = Color(0xFFDAE4F6),
    tertiary = Color(0xFFD2BCFF),
    onTertiary = Color(0xFF33285C),
    tertiaryContainer = Color(0xFF443771),
    onTertiaryContainer = Color(0xFFECDEFF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF7F2A25),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0B0F15),
    onBackground = Color(0xFFE6EBF5),
    surface = Color(0xFF121824),
    onSurface = Color(0xFFE6EBF5),
    surfaceVariant = Color(0xFF1D2431),
    onSurfaceVariant = Color(0xFFB6C0D2),
    outline = Color(0xFF778295),
    outlineVariant = Color(0xFF313A4A),
    inverseSurface = Color(0xFFE6EBF5),
    inverseOnSurface = Color(0xFF18202C),
    inversePrimary = Color(0xFF275C93),
    surfaceTint = Color(0xFF8CB4FF)
)

@Composable
fun NetZoneTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            view.context.findActivity()?.let { activity ->
                val window = activity.window
                window.statusBarColor = colorScheme.surface.toArgb()
                window.navigationBarColor = colorScheme.surface.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

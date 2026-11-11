package com.pfm.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

class FinColors(val income: Color, val expense: Color, val card: Color)

val LocalFin = staticCompositionLocalOf { FinColors(Color(0xFF1B9E5A), Color(0xFFD93025), Color.White) }

private val LightScheme = lightColorScheme(
    primary = Color(0xFF1E6BFF), onPrimary = Color.White,
    background = Color(0xFFF4F6FA), onBackground = Color(0xFF1F2933),
    surface = Color.White, onSurface = Color(0xFF1F2933),
    surfaceVariant = Color(0xFFE9EDF4), onSurfaceVariant = Color(0xFF52606D)
)
private val DarkScheme = darkColorScheme(
    primary = Color(0xFF5B9BFF), onPrimary = Color(0xFF00184A),
    background = Color(0xFF0F1115), onBackground = Color(0xFFE8EAED),
    surface = Color(0xFF1A1D23), onSurface = Color(0xFFE8EAED),
    surfaceVariant = Color(0xFF262A32), onSurfaceVariant = Color(0xFFB0B7C3)
)

/** mode: "system" | "light" | "dark" — เปลี่ยนแล้วทุกหน้าเปลี่ยนตามทันที */
@Composable
fun PfmTheme(mode: String, content: @Composable () -> Unit) {
    val dark = when (mode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val fin = if (dark) FinColors(Color(0xFF6EE7A8), Color(0xFFFF8A80), Color(0xFF1A1D23))
    else FinColors(Color(0xFF1B9E5A), Color(0xFFD93025), Color.White)
    androidx.compose.runtime.CompositionLocalProvider(LocalFin provides fin) {
        MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, content = content)
    }
}

package com.madhu.filemanager

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
class Pal(
    val bg: Color,
    val surface: Color,
    val text: Color,
    val muted: Color,
    val line: Color,
    val accent: Color,
    val accentInk: Color,
    val danger: Color,
    val sel: Color,
    val top: Color
)

val LightPal = Pal(
    bg = Color(0xFFF2F4F7), surface = Color(0xFFFFFFFF), text = Color(0xFF14213D), muted = Color(0xFF667085),
    line = Color(0xFFE3E7EE), accent = Color(0xFFE8A317), accentInk = Color(0xFF14213D), danger = Color(0xFFC62828),
    sel = Color(0xFFFFF3D6), top = Color(0xFF14213D)
)

val DarkPal = Pal(
    bg = Color(0xFF0D1220), surface = Color(0xFF161D30), text = Color(0xFFE8ECF4), muted = Color(0xFF93A0B8),
    line = Color(0xFF242E47), accent = Color(0xFFF2B232), accentInk = Color(0xFF14213D), danger = Color(0xFFEF6B6B),
    sel = Color(0xFF2B2A1E), top = Color(0xFF0A0F1C)
)

val LocalPal = staticCompositionLocalOf { LightPal }

@Composable
fun FmTheme(dark: Boolean, content: @Composable () -> Unit) {
    val p = if (dark) DarkPal else LightPal
    val scheme = if (dark) {
        darkColorScheme(
            primary = p.accent, onPrimary = p.accentInk, background = p.bg, onBackground = p.text,
            surface = p.surface, onSurface = p.text, error = p.danger
        )
    } else {
        lightColorScheme(
            primary = p.accent, onPrimary = p.accentInk, background = p.bg, onBackground = p.text,
            surface = p.surface, onSurface = p.text, error = p.danger
        )
    }
    CompositionLocalProvider(LocalPal provides p) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

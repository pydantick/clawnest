package com.openclaw.app.ui

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle

// Base dark palette (matches the mockups).
val Bg = Color(0xFF0A0B0D)
val Surface = Color(0xFF16181D)
val SurfaceAlt = Color(0xFF1D2026)
val BorderC = Color(0xFF2A2E36)
val TextC = Color(0xFFE7E9EE)
val TextDim = Color(0xFF9AA0AA)
val UserBubble = Color(0xFF20242C)
val ErrorC = Color(0xFFE5534B)

// Persona accent colors.
val Green = Color(0xFF22C55E)
val Purple = Color(0xFF8A63D2)
val Orange = Color(0xFFE8833A)
val Blue = Color(0xFF3B82F6)
val Gray = Color(0xFFAAB0BA)
val Pink = Color(0xFFEC4899)

val SwatchColors = listOf(Green, Purple, Orange, Blue, Gray, Pink)

/** Resolve a persona's theme_color string (e.g. "#8a63d2") to a Color, with fallback. */
fun personaColor(hex: String?): Color {
    if (hex.isNullOrBlank()) return Green
    return try {
        val s = hex.removePrefix("#")
        val v = s.toLong(16)
        if (s.length <= 6) Color(0xFF000000 or v) else Color(v)
    } catch (_: Exception) {
        Green
    }
}

private val DarkColors = darkColorScheme(
    primary = Green,
    background = Bg,
    surface = Surface,
    onPrimary = Color.Black,
    onBackground = TextC,
    onSurface = TextC,
    error = ErrorC,
)

@Composable
fun OpenClawTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, typography = Typography()) {
        // Center each glyph within its line box and trim the font's extra leading, so text
        // sits visually centred in badges/chips/bubbles/list items instead of low.
        val centered = LocalTextStyle.current.merge(
            TextStyle(
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both,
                ),
            ),
        )
        CompositionLocalProvider(LocalTextStyle provides centered, content = content)
    }
}

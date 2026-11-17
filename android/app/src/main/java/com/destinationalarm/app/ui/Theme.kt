package com.destinationalarm.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BrandGreen = Color(0xFF46E07A)
val BrandBlue = Color(0xFF6BB6FF)
val BrandAmber = Color(0xFFFFC24B)
val Ink = Color(0xFF080B10)
val Card = Color(0xFF101620)
val CardRaised = Color(0xFF161E2A)

private val Colors = darkColorScheme(
    primary = BrandGreen,
    onPrimary = Color(0xFF04120A),
    primaryContainer = Color(0xFF1F4D2C),
    onPrimaryContainer = Color(0xFFB6F0C4),
    secondary = BrandBlue,
    onSecondary = Color(0xFF04121F),
    tertiary = BrandAmber,
    onTertiary = Color(0xFF1A1200),
    background = Ink,
    onBackground = Color(0xFFE9F0F7),
    surface = Card,
    onSurface = Color(0xFFE9F0F7),
    surfaceVariant = Color(0xFF182130),
    onSurfaceVariant = Color(0xFF93A1B3),
    outline = Color(0xFF26374A),
    outlineVariant = Color(0xFF1C2836),
    error = Color(0xFFFF6B63),
    onError = Color(0xFF1A0505)
)

@Composable
fun DestAlarmTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}

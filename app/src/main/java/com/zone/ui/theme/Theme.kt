package com.zone.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = ZoneTeal,
    onPrimary = ZoneOnTeal,
    primaryContainer = AlbumBg,
    onPrimaryContainer = ZoneOnTeal,

    secondary = ZoneBlue,
    onSecondary = ZoneOnBlue,
    secondaryContainer = ButtonColor,
    onSecondaryContainer = ZoneBlack,

    background = ZoneBlack,
    onBackground = ZoneTextPrimary,

    surface = ZoneSurface,
    onSurface = ZoneTextPrimary,
    surfaceVariant = ZoneSurfaceVariant,
    onSurfaceVariant = ZoneTextSecondary,

    outline = ZoneOutline,
    outlineVariant = ZoneOutline
)

@Composable
fun ZoneTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
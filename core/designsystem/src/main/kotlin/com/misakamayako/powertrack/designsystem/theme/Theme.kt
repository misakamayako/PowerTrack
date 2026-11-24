package com.misakamayako.powertrack.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

private val DarkColors = darkColorScheme(
    primary = PtColors.Orange80,
    onPrimary = PtColors.Orange10,
    primaryContainer = PtColors.Orange30,
    onPrimaryContainer = PtColors.Orange90,
    secondary = PtColors.Teal80,
    onSecondary = PtColors.Teal10,
    secondaryContainer = PtColors.Teal30,
    onSecondaryContainer = PtColors.Teal90,
    background = PtColors.Neutral6,
    onBackground = PtColors.Neutral90,
    surface = PtColors.Neutral10,
    onSurface = PtColors.Neutral90,
    surfaceVariant = PtColors.Neutral20,
    onSurfaceVariant = PtColors.Neutral90,
    outline = PtColors.Neutral30,
    error = PtColors.Danger,
)

private val LightColors = lightColorScheme(
    primary = PtColors.Orange40,
    onPrimary = PtColors.Neutral95,
    primaryContainer = PtColors.Orange90,
    onPrimaryContainer = PtColors.Orange10,
    secondary = PtColors.Teal40,
    onSecondary = PtColors.Neutral95,
    secondaryContainer = PtColors.Teal90,
    onSecondaryContainer = PtColors.Teal10,
    background = PtColors.Neutral95,
    onBackground = PtColors.Neutral10,
    surface = PtColors.Neutral95,
    onSurface = PtColors.Neutral10,
    surfaceVariant = PtColors.Neutral90,
    onSurfaceVariant = PtColors.Neutral20,
    outline = PtColors.Neutral30,
    error = PtColors.Danger,
)

private val PtTypography = Typography().let { base ->
    base.copy(
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = PtType.label),
    )
}

@Composable
fun PowerTrackTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = PtTypography,
        content = content,
    )
}

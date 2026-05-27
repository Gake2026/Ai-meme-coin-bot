package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val CosmicDarkColorScheme = darkColorScheme(
    primary = CyberGreen,
    surface = SurfaceBg,
    background = DarkBg,
    onPrimary = SurfaceBg,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    error = WarningRed,
    secondary = CyberBlue,
    tertiary = OrangeGold
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CosmicDarkColorScheme,
        typography = Typography,
        content = content
    )
}

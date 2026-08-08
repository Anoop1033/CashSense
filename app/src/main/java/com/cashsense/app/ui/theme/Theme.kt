package com.cashsense.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = BrandGreen,
    secondary = BrandGreenLight,
    background = BackgroundLight,
    surface = SurfaceLight
)

private val DarkColors = darkColorScheme(
    primary = BrandGreenLight,
    secondary = BrandGreen
)

@Composable
fun CashSenseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = CashSenseTypography,
        content = content
    )
}

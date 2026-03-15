package com.liquidmusicglass.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LiquidDarkScheme = darkColorScheme(
    primary = Color(0xFFFFFFFF),
    secondary = Color(0xFFB8C2D3),
    background = Color(0xFF080A0F),
    surface = Color(0xFF10141D)
)

@Composable
fun LiquidMusicGlassTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LiquidDarkScheme,
        typography = LiquidTypography,
        content = content
    )
}
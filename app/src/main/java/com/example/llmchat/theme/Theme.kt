package com.example.llmchat.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = TideLight,
    onPrimary = Night,
    primaryContainer = NightTideContainer,
    background = Night,
    surface = NightSurface,
    onSurface = Paper,
    onSurfaceVariant = Color(0xFFBEC9C5),
  )

private val LightColorScheme =
  lightColorScheme(
    primary = Tide,
    onPrimary = PaperSurface,
    primaryContainer = TideContainer,
    onPrimaryContainer = Ink,
    background = Paper,
    surface = PaperSurface,
    onSurface = Ink,
    onSurfaceVariant = InkMuted,
  )

@Composable
fun LLMChatTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val StageDarkColorScheme = darkColorScheme(
    primary = SkyCyan,
    onPrimary = StageObsidian,
    primaryContainer = SkyCyanContainer,
    onPrimaryContainer = SkyCyan,
    secondary = EmeraldSignal,
    onSecondary = StageObsidian,
    secondaryContainer = EmeraldContainer,
    onSecondaryContainer = EmeraldSignal,
    tertiary = LaserCrimson,
    onTertiary = StageTextPrimary,
    tertiaryContainer = LaserCrimsonContainer,
    onTertiaryContainer = LaserCrimson,
    background = StageObsidian,
    onBackground = StageTextPrimary,
    surface = StageSurface,
    onSurface = StageTextPrimary,
    surfaceVariant = StageSurfaceElevated,
    onSurfaceVariant = StageTextSecondary,
    surfaceContainerLowest = StageObsidian,
    surfaceContainerLow = StageSurface,
    surfaceContainer = StageSurfaceElevated,
    surfaceContainerHigh = StageSurfaceHighest,
    outline = StageOutline,
    outlineVariant = StageOutlineSubtle,
    error = LaserCrimson,
    onError = StageTextPrimary
)

private val StageLightColorScheme = lightColorScheme(
    primary = SkyCyanDark,
    onPrimary = LightSurface,
    primaryContainer = SkyCyan.copy(alpha = 0.18f),
    onPrimaryContainer = SkyCyanDark,
    secondary = EmeraldSignal,
    onSecondary = LightSurface,
    tertiary = LaserCrimson,
    onTertiary = LightSurface,
    background = LightCanvas,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightOutline,
    outlineVariant = LightOutline.copy(alpha = 0.5f),
    error = LaserCrimson,
    onError = LightSurface
)

val BeamerShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun BeamerStageTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) StageDarkColorScheme else StageLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = BeamerTypography,
        shapes = BeamerShapes,
        content = content
    )
}

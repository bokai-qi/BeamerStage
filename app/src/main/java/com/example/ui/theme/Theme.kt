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

private val StageOledDarkColorScheme = darkColorScheme(
    primary = SkyCyan,
    onPrimary = OledBlack,
    primaryContainer = SkyCyanContainer,
    onPrimaryContainer = SkyCyan,
    secondary = EmeraldSignal,
    onSecondary = OledBlack,
    secondaryContainer = EmeraldContainer,
    onSecondaryContainer = EmeraldSignal,
    tertiary = LaserCrimson,
    onTertiary = StageTextPrimary,
    tertiaryContainer = LaserCrimsonContainer,
    onTertiaryContainer = LaserCrimson,
    background = OledBlack,
    onBackground = StageTextPrimary,
    surface = OledBlack,
    onSurface = StageTextPrimary,
    surfaceVariant = OledSurfaceElevated,
    onSurfaceVariant = StageTextSecondary,
    surfaceContainerLowest = OledBlack,
    surfaceContainerLow = OledBlack,
    surfaceContainer = OledSurfaceElevated,
    surfaceContainerHigh = OledSurfaceHighest,
    outline = OledOutline,
    outlineVariant = OledOutlineSubtle,
    error = LaserCrimson,
    onError = StageTextPrimary
)

private val StageLightColorScheme = lightColorScheme(
    primary = SkyCyanDark,
    onPrimary = LightSurface,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = SkyCyanDark,
    secondary = LightSecondary,
    onSecondary = LightSurface,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightSecondary,
    tertiary = LightTertiary,
    onTertiary = LightSurface,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightTertiary,
    background = LightCanvas,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    surfaceContainerLowest = LightSurface,
    surfaceContainerLow = LightSurfaceElevated,
    surfaceContainer = LightSurfaceVariant,
    surfaceContainerHigh = LightSurfaceHigh,
    outline = LightOutline,
    outlineVariant = LightOutlineSubtle,
    error = LightTertiary,
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
    oledBlack: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        !darkTheme -> StageLightColorScheme
        oledBlack -> StageOledDarkColorScheme
        else -> StageDarkColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = BeamerTypography,
        shapes = BeamerShapes,
        content = content
    )
}

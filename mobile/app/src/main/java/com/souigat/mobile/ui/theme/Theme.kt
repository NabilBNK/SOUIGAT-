package com.souigat.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = BrandBlueDeep,
    onPrimary = SurfaceLowest,
    primaryContainer = BrandBlue,
    onPrimaryContainer = SurfaceLowest,
    secondary = InkSecondary,
    onSecondary = SurfaceLowest,
    secondaryContainer = SurfaceHigh,
    onSecondaryContainer = InkSecondary,
    tertiary = Success,
    onTertiary = SurfaceLowest,
    tertiaryContainer = SuccessSoft,
    onTertiaryContainer = Success,
    error = ErrorRed,
    onError = SurfaceLowest,
    errorContainer = ErrorSoft,
    onErrorContainer = ErrorRed,
    background = BackgroundLight,
    onBackground = InkPrimary,
    surface = SurfaceBright,
    onSurface = InkPrimary,
    surfaceVariant = Surface,
    onSurfaceVariant = InkSecondary,
    outline = InkTertiary,
    outlineVariant = OutlineLight,
    surfaceDim = SurfaceHighest,
    surfaceBright = SurfaceBright,
    surfaceContainerLowest = SurfaceLowest,
    surfaceContainerLow = SurfaceLow,
    surfaceContainer = Surface,
    surfaceContainerHigh = SurfaceHigh,
    surfaceContainerHighest = SurfaceHighest,
    inverseSurface = InkPrimary,
    inverseOnSurface = SurfaceBright,
    inversePrimary = BrandBlue
)

private val DarkColorScheme = darkColorScheme(
    primary = BrandBlue,
    onPrimary = SurfaceLowest,
    primaryContainer = BrandBlueDeep,
    onPrimaryContainer = SurfaceLowest,
    secondary = OnSurfaceVariantDark,
    onSecondary = InkPrimary,
    secondaryContainer = SurfaceDarkLow,
    onSecondaryContainer = OnSurfaceVariantDark,
    tertiary = SuccessSoft,
    onTertiary = InkPrimary,
    tertiaryContainer = Success,
    onTertiaryContainer = SuccessSoft,
    error = ErrorRed,
    onError = SurfaceLowest,
    errorContainer = ErrorSoft,
    onErrorContainer = ErrorRed,
    background = BackgroundDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceDarkLow,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineDark,
    surfaceDim = BackgroundDark,
    surfaceBright = SurfaceDark,
    surfaceContainerLowest = BackgroundDark,
    surfaceContainerLow = SurfaceDark,
    surfaceContainer = SurfaceDarkLow,
    surfaceContainerHigh = SurfaceDarkLow,
    surfaceContainerHighest = SurfaceDarkLow,
    inverseSurface = SurfaceBright,
    inverseOnSurface = InkPrimary,
    inversePrimary = BrandBlueDeep
)

@Composable
fun SouigatTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}

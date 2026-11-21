package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = DarkBrandTeal,
    onPrimary = Color(0xFF042630),
    primaryContainer = DarkBrandTealContainer,
    onPrimaryContainer = DarkOnBrandTealContainer,
    secondary = DarkBrandCoral,
    onSecondary = Color(0xFF3E1405),
    secondaryContainer = DarkBrandCoralContainer,
    onSecondaryContainer = DarkOnBrandCoralContainer,
    tertiary = DarkBrandSage,
    tertiaryContainer = DarkBrandSageContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = ErrorDark
)

private val LightColorScheme = lightColorScheme(
    primary = BrandTeal,
    onPrimary = Color.White,
    primaryContainer = BrandTealContainer,
    onPrimaryContainer = OnBrandTealContainer,
    secondary = BrandCoral,
    onSecondary = Color.White,
    secondaryContainer = BrandCoralContainer,
    onSecondaryContainer = OnBrandCoralContainer,
    tertiary = BrandSage,
    tertiaryContainer = BrandSageContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = ErrorLight
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun PetaPikirTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PetaPikirTypography,
        shapes = AppShapes,
        content = content
    )
}

package com.voltapracasa.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Identidade: índigo da noite + âmbar da "luz da varanda acesa" esperando o pet voltar. */
object Brand {
    val Ink = Color(0xFF1B2250)
    val Porch = Color(0xFFF7B32B)
    val Lost = Color(0xFFE5484D)
    val Sighting = Color(0xFF7C5CD6)
    val Found = Color(0xFF1C8DB5)
    val Adoption = Color(0xFF2E9E6B)
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF2C3A94),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE1E5FA),
    onPrimaryContainer = Brand.Ink,
    secondary = Brand.Porch,
    onSecondary = Brand.Ink,
    secondaryContainer = Color(0xFFFFEFC7),
    onSecondaryContainer = Color(0xFF4A3300),
    tertiary = Brand.Adoption,
    background = Color(0xFFF5F6FA),
    onBackground = Color(0xFF191C2B),
    surface = Color.White,
    onSurface = Color(0xFF191C2B),
    surfaceVariant = Color(0xFFE8EAF3),
    onSurfaceVariant = Color(0xFF565B75),
    outline = Color(0xFFC3C7D9),
    outlineVariant = Color(0xFFDDE0EC),
    error = Color(0xFFC8363B),
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color(0xFFEFF0F7)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB4BEFF),
    onPrimary = Brand.Ink,
    primaryContainer = Color(0xFF2C3A94),
    onPrimaryContainer = Color(0xFFE1E5FA),
    secondary = Brand.Porch,
    onSecondary = Brand.Ink,
    secondaryContainer = Color(0xFF4A3300),
    onSecondaryContainer = Color(0xFFFFEFC7),
    tertiary = Color(0xFF5FD39B),
    background = Color(0xFF0F1228),
    onBackground = Color(0xFFE6E8F5),
    surface = Color(0xFF181C3B),
    onSurface = Color(0xFFE6E8F5),
    surfaceVariant = Color(0xFF242850),
    onSurfaceVariant = Color(0xFFA7ABC6),
    outline = Color(0xFF4A4F78),
    outlineVariant = Color(0xFF30355E),
    error = Color(0xFFFF8A8D),
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = Color(0xFF0F1228),
    surfaceContainerLow = Color(0xFF181C3B),
    surfaceContainer = Color(0xFF181C3B),
    surfaceContainerHigh = Color(0xFF202549),
    surfaceContainerHighest = Color(0xFF242850)
)

private val baseType = Typography()

/** Títulos em serifa (cara de cartaz "procura-se"), texto corrido em sans. */
private val VoltaTypography = Typography(
    headlineLarge = baseType.headlineLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp),
    headlineMedium = baseType.headlineMedium.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
    headlineSmall = baseType.headlineSmall.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = baseType.titleLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = baseType.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = baseType.labelLarge.copy(fontWeight = FontWeight.SemiBold)
)

private val VoltaShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun VoltaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = VoltaTypography,
        shapes = VoltaShapes,
        content = content
    )
}

package com.daftar.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = DaftarColors.Copper,
    onPrimary = DaftarColors.Paper,
    secondary = DaftarColors.Gold,
    onSecondary = DaftarColors.Ink,
    tertiary = DaftarColors.Green,
    onTertiary = DaftarColors.Paper,
    error = DaftarColors.Red,
    onError = DaftarColors.Paper,
    background = DaftarColors.Paper,
    onBackground = DaftarColors.Ink,
    surface = DaftarColors.PaperSoft,
    onSurface = DaftarColors.Ink,
    surfaceVariant = DaftarColors.PaperDeep,
    onSurfaceVariant = DaftarColors.InkSoft,
    outline = DaftarColors.LineStrong,
    outlineVariant = DaftarColors.Line,
    inverseSurface = DaftarColors.Ink,
    inverseOnSurface = DaftarColors.Paper,
)

private val DaftarShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** The ledger is deliberately light-only: it is a paper artifact. */
@Composable
fun DaftarTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = DaftarTypography,
        shapes = DaftarShapes,
        content = content,
    )
}

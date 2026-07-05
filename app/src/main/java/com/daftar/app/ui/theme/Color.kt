package com.daftar.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Daftar palette — a paper-and-ink ledger aesthetic with copper/gold accents.
 * Mirrors the CSS custom properties of the design prototype.
 */
object DaftarColors {
    val Paper = Color(0xFFF3EEE1)
    val PaperDeep = Color(0xFFE8E0CC)
    val PaperSoft = Color(0xFFF8F3E6)
    val Ink = Color(0xFF161C1F)
    val InkSoft = Color(0xFF2E3A3D)
    val Muted = Color(0xFF6B7478)
    val MutedLight = Color(0xFF9BA3A5)
    val Copper = Color(0xFFA8541A)
    val CopperSoft = Color(0xFFC87343)
    val CopperDeep = Color(0xFF7B3D14)
    val Gold = Color(0xFFB89447)
    val GoldSoft = Color(0xFFD4B26C)
    val Red = Color(0xFFB3321F)
    val RedSoft = Color(0xFFCC5843)
    val Green = Color(0xFF2E6B4E)
    val GreenSoft = Color(0xFF4E8B6E)
    val GreenDeep = Color(0xFF1A3D2E)
    val Blue = Color(0xFF2C5A7A)
    val BlueSoft = Color(0xFF4C7A9A)
    val RedDeep = Color(0xFF5C1810)

    // Accents used on dark (ink) surfaces
    val LongGreen = Color(0xFF7BC89E)
    val ShortRed = Color(0xFFE89082)

    val WhatsApp = Color(0xFF25D366)

    // Hairlines
    val Line = Ink.copy(alpha = 0.08f)
    val LineStrong = Ink.copy(alpha = 0.16f)
    val LineDashed = Ink.copy(alpha = 0.25f)
    val LineOnDark = Paper.copy(alpha = 0.15f)
}

/** Positive / negative / neutral coloring for signed amounts. */
fun amountColor(value: Double, onDark: Boolean = false): Color = when {
    value > 0.5 -> if (onDark) DaftarColors.LongGreen else DaftarColors.Green
    value < -0.5 -> if (onDark) DaftarColors.ShortRed else DaftarColors.Red
    else -> if (onDark) DaftarColors.MutedLight else DaftarColors.Muted
}

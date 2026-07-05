package com.daftar.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.daftar.app.R

/**
 * Font roles mirroring the prototype:
 *  - Fraunces  → serif display: amounts, names, titles
 *  - Inter     → body text
 *  - JetBrains Mono → uppercase micro-labels, codes, currency tags
 *  - Noto Naskh Arabic → Pashto accents
 */
val Fraunces = FontFamily(
    Font(R.font.fraunces_400, FontWeight.Normal),
    Font(R.font.fraunces_500, FontWeight.Medium),
    Font(R.font.fraunces_600, FontWeight.SemiBold),
    Font(R.font.fraunces_700, FontWeight.Bold),
)

val Inter = FontFamily(
    Font(R.font.inter_400, FontWeight.Normal),
    Font(R.font.inter_500, FontWeight.Medium),
    Font(R.font.inter_600, FontWeight.SemiBold),
    Font(R.font.inter_700, FontWeight.Bold),
)

val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_400, FontWeight.Normal),
    Font(R.font.jetbrains_mono_500, FontWeight.Medium),
    Font(R.font.jetbrains_mono_600, FontWeight.SemiBold),
    Font(R.font.jetbrains_mono_700, FontWeight.Bold),
)

val NotoNaskhArabic = FontFamily(
    Font(R.font.noto_naskh_arabic_400, FontWeight.Normal),
    Font(R.font.noto_naskh_arabic_500, FontWeight.Medium),
    Font(R.font.noto_naskh_arabic_600, FontWeight.SemiBold),
    Font(R.font.noto_naskh_arabic_700, FontWeight.Bold),
)

val DaftarTypography = Typography(
    displayMedium = TextStyle(
        fontFamily = Fraunces,
        fontWeight = FontWeight.Medium,
        fontSize = 36.sp,
        letterSpacing = (-0.02).em,
    ),
    headlineMedium = TextStyle(
        fontFamily = Fraunces,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        letterSpacing = (-0.02).em,
    ),
    titleLarge = TextStyle(
        fontFamily = Fraunces,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        letterSpacing = (-0.01).em,
    ),
    titleMedium = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        letterSpacing = (-0.01).em,
    ),
    bodyLarge = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        letterSpacing = 0.1.em,
    ),
    labelMedium = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        letterSpacing = 0.15.em,
    ),
    labelSmall = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Bold,
        fontSize = 9.sp,
        letterSpacing = 0.2.em,
    ),
)

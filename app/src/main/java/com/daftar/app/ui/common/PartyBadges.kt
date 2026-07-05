package com.daftar.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daftar.app.domain.model.City
import com.daftar.app.domain.model.Counterparty
import com.daftar.app.domain.model.Customer
import com.daftar.app.ui.theme.DaftarColors
import com.daftar.app.ui.theme.JetBrainsMono
import com.daftar.app.ui.theme.NotoNaskhArabic

val City.badgeColor: Color
    get() = when (this) {
        City.KBL -> DaftarColors.Copper
        City.HRT -> DaftarColors.Green
        City.MZR -> DaftarColors.CopperDeep
        City.JAL -> DaftarColors.Gold
    }

private val CustomerGradients = listOf(
    listOf(Color(0xFF2E6B4E), Color(0xFF4E8B6E)),
    listOf(Color(0xFFA8541A), Color(0xFFC87343)),
    listOf(Color(0xFF7B3D14), Color(0xFFA8541A)),
    listOf(Color(0xFFB89447), Color(0xFFD4B26C)),
    listOf(Color(0xFF2E3A3D), Color(0xFF161C1F)),
    listOf(Color(0xFF556B4D), Color(0xFF7A8F6E)),
)

fun customerGradient(colorIndex: Int): Brush =
    Brush.linearGradient(CustomerGradients[colorIndex % CustomerGradients.size])

/** Partner badge: city-colored tile, Arabic initial, city dot. */
@Composable
fun PartnerBadge(partner: Counterparty, size: Dp = 44.dp) {
    Box(modifier = Modifier.size(size)) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(size * 0.27f))
                .background(partner.city.badgeColor),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = partner.initial,
                style = TextStyle(
                    fontFamily = NotoNaskhArabic,
                    fontWeight = FontWeight.Medium,
                    fontSize = (size.value * 0.42f).sp,
                    color = DaftarColors.Paper,
                ),
            )
        }
        Box(
            modifier = Modifier
                .size(size * 0.32f)
                .align(Alignment.BottomEnd)
                .offset(x = 2.dp, y = 2.dp)
                .clip(CircleShape)
                .background(partner.city.badgeColor)
                .border(2.dp, DaftarColors.Paper, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = partner.city.code.take(1),
                style = TextStyle(
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.16f).sp,
                    color = DaftarColors.Paper,
                ),
            )
        }
    }
}

/** Customer badge: gradient tile with Arabic initial. */
@Composable
fun CustomerBadge(customer: Customer, size: Dp = 44.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.27f))
            .background(customerGradient(customer.colorIndex)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = customer.initial,
            style = TextStyle(
                fontFamily = NotoNaskhArabic,
                fontWeight = FontWeight.Medium,
                fontSize = (size.value * 0.42f).sp,
                color = DaftarColors.Paper,
            ),
        )
    }
}

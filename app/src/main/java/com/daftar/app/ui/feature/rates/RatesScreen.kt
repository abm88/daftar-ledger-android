package com.daftar.app.ui.feature.rates

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.daftar.app.core.format.Formatters
import com.daftar.app.domain.model.RateBook
import com.daftar.app.domain.model.RatePair
import com.daftar.app.domain.repository.RatesRepository
import com.daftar.app.ui.common.IconSquareButton
import com.daftar.app.ui.common.LocalToaster
import com.daftar.app.ui.common.MonoLabel
import com.daftar.app.ui.common.ToastIcon
import com.daftar.app.ui.feature.statements.StatementHeaderBar
import com.daftar.app.ui.theme.DaftarColors
import com.daftar.app.ui.theme.Fraunces
import com.daftar.app.ui.theme.Inter
import com.daftar.app.ui.theme.JetBrainsMono
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class RatesViewModel @Inject constructor(
    ratesRepository: RatesRepository,
) : ViewModel() {
    val rateBook = ratesRepository.rateBook
        .map { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ratesRepository.rateBook.value)
}

/** Live rate sheet for the three classic pairs, with a broadcast action. */
@Composable
fun RatesScreen(
    navController: NavController,
    viewModel: RatesViewModel = hiltViewModel(),
) {
    val rateBook by viewModel.rateBook.collectAsStateWithLifecycle()
    val toaster = LocalToaster.current

    Column(modifier = Modifier.fillMaxWidth()) {
        StatementHeaderBar(
            title = "Live Rates",
            subtitle = "د اسعارو نرخ · today's market",
            onBack = { navController.popBackStack() },
        )
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            RatePair.entries.forEach { pair ->
                RatePairCard(pair, rateBook)
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(DaftarColors.Ink)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(DaftarColors.WhatsApp),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Icon(
                        Icons.Rounded.ChatBubble, null,
                        tint = DaftarColors.Paper, modifier = Modifier.size(18.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Share today's rates",
                        style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = DaftarColors.Paper),
                    )
                    Text(
                        "Broadcast to all accounts via WhatsApp",
                        style = TextStyle(fontFamily = Inter, fontSize = 11.sp, color = DaftarColors.MutedLight),
                    )
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(DaftarColors.Copper)
                        .clickable { toaster("Rate sheet sent", ToastIcon.SEND) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    androidx.compose.material3.Icon(
                        Icons.AutoMirrored.Rounded.Send, null,
                        tint = DaftarColors.Paper, modifier = Modifier.size(11.dp),
                    )
                    Text(
                        "SEND",
                        style = TextStyle(
                            fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold,
                            fontSize = 10.sp, letterSpacing = 0.1.em, color = DaftarColors.Paper,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
fun RatePairCard(pair: RatePair, rateBook: RateBook) {
    val rate = rateBook.pairs[pair] ?: return
    val up = rate.deltaPercent > 0
    val flat = rate.deltaPercent == 0.0
    val decimals = if (pair == RatePair.PKR_AFN) 3 else 2
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DaftarColors.PaperSoft)
            .border(1.dp, DaftarColors.LineStrong, RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                androidx.compose.material3.Icon(
                    imageVector = if (up) Icons.AutoMirrored.Rounded.TrendingUp else Icons.AutoMirrored.Rounded.TrendingDown,
                    contentDescription = null,
                    tint = if (up) DaftarColors.Green else DaftarColors.Red,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    pair.label,
                    style = TextStyle(
                        fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold,
                        fontSize = 13.sp, letterSpacing = 0.05.em, color = DaftarColors.Ink,
                    ),
                )
            }
            Text(
                text = when {
                    flat -> "— FLAT"
                    up -> "▲ ${Formatters.rate(rate.deltaPercent, 1)}%"
                    else -> "▼ ${Formatters.rate(abs(rate.deltaPercent), 1)}%"
                },
                style = TextStyle(
                    fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 10.sp,
                    color = when {
                        flat -> DaftarColors.Muted
                        up -> DaftarColors.Green
                        else -> DaftarColors.Red
                    },
                ),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                MonoLabel("Buy", color = DaftarColors.Green, fontSize = 9)
                Text(
                    Formatters.rate(rate.buy, decimals),
                    style = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 24.sp, color = DaftarColors.Green),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                MonoLabel("Sell", color = DaftarColors.Red, fontSize = 9)
                Text(
                    Formatters.rate(rate.sell, decimals),
                    style = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 24.sp, color = DaftarColors.Red),
                )
            }
        }
    }
}

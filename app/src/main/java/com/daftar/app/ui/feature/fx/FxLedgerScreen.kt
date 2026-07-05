package com.daftar.app.ui.feature.fx

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.daftar.app.core.format.Formatters
import com.daftar.app.core.time.TimeProvider
import com.daftar.app.domain.model.FxPosition
import com.daftar.app.domain.model.FxSide
import com.daftar.app.domain.model.FxTrade
import com.daftar.app.domain.repository.FxRepository
import com.daftar.app.domain.repository.RatesRepository
import com.daftar.app.domain.usecase.FxAnalytics
import com.daftar.app.ui.common.MonoLabel
import com.daftar.app.ui.navigation.DaftarDestinations
import com.daftar.app.ui.theme.DaftarColors
import com.daftar.app.ui.theme.Fraunces
import com.daftar.app.ui.theme.Inter
import com.daftar.app.ui.theme.JetBrainsMono
import com.daftar.app.ui.theme.NotoNaskhArabic
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class FxDayGroup(
    val label: String,
    val trades: List<FxTrade>,
    val dayPnl: Double,
)

data class FxLedgerUiState(
    val todayPnl: Double = 0.0,
    val todayTradeCount: Int = 0,
    val todayBuys: Int = 0,
    val todaySells: Int = 0,
    val positions: List<FxPosition> = emptyList(),
    val groups: List<FxDayGroup> = emptyList(),
    val totalTrades: Int = 0,
)

@HiltViewModel
class FxLedgerViewModel @Inject constructor(
    fxRepository: FxRepository,
    ratesRepository: RatesRepository,
    fxAnalytics: FxAnalytics,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    val uiState = combine(
        fxRepository.trades,
        ratesRepository.rateBook,
    ) { trades, rates ->
        val sorted = trades.sortedByDescending { it.timestampMillis }
        val todayStart = timeProvider.startOfTodayMillis()
        val todayTrades = sorted.filter { it.timestampMillis >= todayStart }

        val groups = sorted
            .groupBy { trade ->
                Math.floorDiv(trade.timestampMillis - todayStart, 86_400_000L)
            }
            .entries
            .sortedByDescending { it.key }
            .map { (dayOffset, dayTrades) ->
                val dayStart = todayStart + dayOffset * 86_400_000L
                FxDayGroup(
                    label = Formatters.relativeDayLabel(dayStart, todayStart),
                    trades = dayTrades,
                    dayPnl = dayTrades.sumOf { it.realizedPnlAfn ?: 0.0 },
                )
            }

        FxLedgerUiState(
            todayPnl = fxAnalytics.todayRealizedPnl(trades),
            todayTradeCount = todayTrades.size,
            todayBuys = todayTrades.count { it.side == FxSide.BUY },
            todaySells = todayTrades.count { it.side == FxSide.SELL },
            positions = fxAnalytics.openPositions(trades, rates),
            groups = groups,
            totalTrades = trades.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FxLedgerUiState())
}

/** FX ledger: today's realized P&L, open positions, day-grouped trade history. */
@Composable
fun FxLedgerScreen(
    navController: NavController,
    viewModel: FxLedgerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxWidth()) {
        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 110.dp),
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DaftarColors.Ink)
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    com.daftar.app.ui.common.IconSquareButton(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        { navController.popBackStack() },
                        onDark = true,
                    )
                    Column {
                        Text(
                            "FX Ledger",
                            style = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 18.sp, color = DaftarColors.Paper),
                        )
                        Text(
                            "د اسعارو د معاملو دفتر · P&L in AFN",
                            style = TextStyle(fontFamily = JetBrainsMono, fontSize = 10.sp, color = DaftarColors.GoldSoft),
                        )
                    }
                }
            }

            item {
                // Today's P&L card
                val profit = state.todayPnl > 0.5
                val loss = state.todayPnl < -0.5
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            when {
                                profit -> DaftarColors.Green
                                loss -> DaftarColors.Red
                                else -> DaftarColors.PaperDeep
                            },
                        )
                        .padding(18.dp),
                ) {
                    MonoLabel(
                        "Today · realized P&L",
                        color = if (profit || loss) DaftarColors.Paper.copy(alpha = 0.75f) else DaftarColors.Muted,
                        fontSize = 9,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = if (abs(state.todayPnl) < 0.5) "0"
                            else (if (state.todayPnl >= 0) "+" else "−") + Formatters.number(abs(state.todayPnl)),
                            style = TextStyle(
                                fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 34.sp,
                                color = if (profit || loss) DaftarColors.Paper else DaftarColors.Ink,
                            ),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "AFN",
                            style = TextStyle(
                                fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                                letterSpacing = 0.1.em,
                                color = if (profit || loss) DaftarColors.Paper.copy(alpha = 0.75f) else DaftarColors.Muted,
                            ),
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                    Text(
                        text = when {
                            profit -> "د نن ورځې ګټه"
                            loss -> "د نن ورځې زیان"
                            else -> "مساوي"
                        },
                        style = TextStyle(
                            fontFamily = NotoNaskhArabic, fontSize = 12.sp,
                            color = if (profit || loss) DaftarColors.Paper.copy(alpha = 0.85f) else DaftarColors.Muted,
                            textDirection = TextDirection.Rtl,
                        ),
                    )
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(
                        color = (if (profit || loss) DaftarColors.Paper else DaftarColors.Ink).copy(alpha = 0.15f),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        FxStat("Trades", state.todayTradeCount, profit || loss, Modifier.weight(1f))
                        FxStat("Buys", state.todayBuys, profit || loss, Modifier.weight(1f))
                        FxStat("Sells", state.todaySells, profit || loss, Modifier.weight(1f))
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MonoLabel("Open positions")
                    HorizontalDivider(Modifier.weight(1f), color = DaftarColors.Line)
                    MonoLabel("AFN basis")
                }
            }

            items(count = state.positions.size, key = { i -> "pos_" + state.positions[i].currency }) { i ->
                FxPositionRow(state.positions[i])
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 14.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MonoLabel("Trade history")
                    HorizontalDivider(Modifier.weight(1f), color = DaftarColors.Line)
                    MonoLabel("${state.totalTrades} entries")
                }
            }

            state.groups.forEach { group ->
                item(key = "fxday_" + group.label) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(top = 10.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MonoLabel(group.label, fontSize = 9)
                        HorizontalDivider(Modifier.weight(1f), color = DaftarColors.Line)
                        Text(
                            text = if (abs(group.dayPnl) >= 0.5) {
                                (if (group.dayPnl >= 0) "+" else "−") + Formatters.number(abs(group.dayPnl)) + " AFN"
                            } else "—",
                            style = TextStyle(
                                fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 10.sp,
                                color = when {
                                    group.dayPnl > 0.5 -> DaftarColors.Green
                                    group.dayPnl < -0.5 -> DaftarColors.Red
                                    else -> DaftarColors.Muted
                                },
                            ),
                        )
                    }
                }
                items(count = group.trades.size, key = { i -> group.trades[i].id }) { i ->
                    FxTradeRow(group.trades[i])
                }
            }
        }

        // FAB — new trade
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 24.dp)
                .size(56.dp)
                .clip(CircleShape)
                .background(DaftarColors.Copper)
                .clickable { navController.navigate(DaftarDestinations.NEW_FX) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Refresh, "New trade", tint = DaftarColors.Paper, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun FxStat(label: String, value: Int, onColor: Boolean, modifier: Modifier) {
    Column(modifier = modifier) {
        MonoLabel(
            label,
            color = if (onColor) DaftarColors.Paper.copy(alpha = 0.7f) else DaftarColors.Muted,
            fontSize = 9,
        )
        Text(
            text = value.toString(),
            style = TextStyle(
                fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 18.sp,
                color = if (onColor) DaftarColors.Paper else DaftarColors.Ink,
            ),
        )
    }
}

@Composable
private fun FxPositionRow(position: FxPosition) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(DaftarColors.PaperSoft)
            .border(1.dp, DaftarColors.Line, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (position.isFlat) DaftarColors.PaperDeep else DaftarColors.Ink),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                position.currency,
                style = TextStyle(
                    fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 10.sp,
                    color = if (position.isFlat) DaftarColors.Muted else DaftarColors.GoldSoft,
                ),
            )
        }
        if (position.isFlat) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "— no position",
                    style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = DaftarColors.Muted),
                )
                Text("flat", style = TextStyle(fontFamily = JetBrainsMono, fontSize = 10.sp, color = DaftarColors.MutedLight))
            }
        } else {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = Formatters.amount(position.quantity, position.currency),
                    style = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 17.sp, color = DaftarColors.Ink),
                )
                Text(
                    text = "avg cost: " + Formatters.rate(position.averageCostAfn, if (position.currency == "USD") 2 else 4) + " AFN",
                    style = TextStyle(fontFamily = JetBrainsMono, fontSize = 10.sp, color = DaftarColors.Muted),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                MonoLabel("Market (AFN)", fontSize = 8, letterSpacing = 0.1)
                Text(
                    text = Formatters.number(position.marketValueAfn),
                    style = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = DaftarColors.Ink),
                )
                Text(
                    text = (if (position.unrealizedPnlAfn >= 0) "+" else "−") +
                        Formatters.number(abs(position.unrealizedPnlAfn)) + " unrealized",
                    style = TextStyle(
                        fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold, fontSize = 9.sp,
                        color = when {
                            position.unrealizedPnlAfn > 0.5 -> DaftarColors.Green
                            position.unrealizedPnlAfn < -0.5 -> DaftarColors.Red
                            else -> DaftarColors.Muted
                        },
                    ),
                )
            }
        }
    }
}

@Composable
private fun FxTradeRow(trade: FxTrade) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (trade.side == FxSide.BUY) DaftarColors.Green.copy(alpha = 0.12f) else DaftarColors.Red.copy(alpha = 0.12f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                trade.side.name,
                style = TextStyle(
                    fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 9.sp,
                    letterSpacing = 0.1.em,
                    color = if (trade.side == FxSide.BUY) DaftarColors.Green else DaftarColors.Red,
                ),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${Formatters.amount(trade.fromAmount, trade.fromCurrency)} ${trade.fromCurrency} → " +
                    "${Formatters.amount(trade.toAmount, trade.toCurrency)} ${trade.toCurrency}",
                style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = DaftarColors.Ink),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "rate ${trade.rate} · ${trade.note ?: "no note"}",
                style = TextStyle(fontFamily = JetBrainsMono, fontSize = 10.sp, color = DaftarColors.Muted),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            MonoLabel(if (trade.realizedPnlAfn != null) "P&L" else "cost basis", fontSize = 8, letterSpacing = 0.1)
            Text(
                text = trade.realizedPnlAfn?.let {
                    (if (it >= 0) "+" else "−") + Formatters.number(abs(it)) + " AFN"
                } ?: "—",
                style = TextStyle(
                    fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 11.sp,
                    color = when {
                        trade.realizedPnlAfn == null -> DaftarColors.Muted
                        trade.realizedPnlAfn > 0.5 -> DaftarColors.Green
                        trade.realizedPnlAfn < -0.5 -> DaftarColors.Red
                        else -> DaftarColors.Muted
                    },
                ),
            )
        }
    }
}

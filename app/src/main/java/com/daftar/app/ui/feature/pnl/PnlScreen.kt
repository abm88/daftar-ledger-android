package com.daftar.app.ui.feature.pnl

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.daftar.app.domain.model.AssetCatalog
import com.daftar.app.domain.model.LedgerPeriod
import com.daftar.app.domain.model.PnlItem
import com.daftar.app.domain.model.PnlReport
import com.daftar.app.domain.model.PnlSource
import com.daftar.app.domain.repository.CashRepository
import com.daftar.app.domain.repository.FxRepository
import com.daftar.app.domain.repository.PartnerRepository
import com.daftar.app.domain.repository.RatesRepository
import com.daftar.app.domain.repository.SettingsRepository
import com.daftar.app.domain.usecase.CurrencyConverter
import com.daftar.app.domain.usecase.PnlCalculator
import com.daftar.app.ui.common.DaftarFilterChip
import com.daftar.app.ui.common.MonoLabel
import com.daftar.app.ui.feature.statements.StatementHeaderBar
import com.daftar.app.ui.theme.DaftarColors
import com.daftar.app.ui.theme.Fraunces
import com.daftar.app.ui.theme.Inter
import com.daftar.app.ui.theme.JetBrainsMono
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class PnlUiState(
    val period: LedgerPeriod = LedgerPeriod.ALL,
    val report: PnlReport? = null,
    val reportingCurrency: String = "AFN",
    val reportingDecimals: Int = 0,
    /** Converts an AFN amount into the reporting currency. */
    val toReporting: (Double) -> Double = { it },
)

@HiltViewModel
class PnlViewModel @Inject constructor(
    fxRepository: FxRepository,
    partnerRepository: PartnerRepository,
    cashRepository: CashRepository,
    ratesRepository: RatesRepository,
    settingsRepository: SettingsRepository,
    pnlCalculator: PnlCalculator,
    converter: CurrencyConverter,
) : ViewModel() {

    private val period = MutableStateFlow(LedgerPeriod.ALL)

    val uiState = combine(
        combine(fxRepository.trades, partnerRepository.partners) { t, p -> t to p },
        cashRepository.drawer,
        ratesRepository.rateBook,
        settingsRepository.settings,
        period,
    ) { (trades, partners), drawer, rates, settings, period ->
        PnlUiState(
            period = period,
            report = pnlCalculator.compute(period, trades, partners, drawer, rates, settings),
            reportingCurrency = settings.reportingCurrency,
            reportingDecimals = AssetCatalog.decimalsFor(settings.reportingCurrency),
            toReporting = { afn -> converter.toReporting("AFN", afn, rates, settings) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PnlUiState())

    fun setPeriod(value: LedgerPeriod) { period.value = value }
}

/** P&L ledger: period chips, headline, per-source cards, entry breakdown. */
@Composable
fun PnlScreen(
    navController: NavController,
    viewModel: PnlViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val report = state.report ?: return
    val rep = state.reportingCurrency
    val repDec = state.reportingDecimals

    val grandRep = state.toReporting(report.grandTotalAfn)
    val fxRep = state.toReporting(report.fxRealizedAfn)
    val commissionRep = state.toReporting(report.hawalaCommissionAfn)
    val revalRep = state.toReporting(report.unrealizedRevaluationAfn)

    Column(modifier = Modifier.fillMaxWidth()) {
        StatementHeaderBar(
            title = "Profit & Loss",
            subtitle = "د ګټې او زیان حساب · ${report.period.label.lowercase()} · in $rep",
            onBack = { navController.popBackStack() },
        )

        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        LedgerPeriod.TODAY to "Today",
                        LedgerPeriod.WEEK to "7 days",
                        LedgerPeriod.MONTH to "Month",
                        LedgerPeriod.ALL to "All",
                    ).forEach { (p, label) ->
                        DaftarFilterChip(label, state.period == p, { viewModel.setPeriod(p) })
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            item {
                val profit = grandRep > 0.5
                val loss = grandRep < -0.5
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            when {
                                profit -> DaftarColors.Green
                                loss -> DaftarColors.Red
                                else -> DaftarColors.Ink
                            },
                        )
                        .padding(18.dp),
                ) {
                    MonoLabel("Net P&L · ${report.period.label}", color = Color.White.copy(alpha = 0.75f), fontSize = 9)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = Formatters.signPrefix(grandRep).ifEmpty { "" } +
                                Formatters.compact(abs(grandRep), repDec),
                            style = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 34.sp, color = DaftarColors.Paper),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            rep,
                            style = TextStyle(
                                fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                                letterSpacing = 0.1.em, color = Color.White.copy(alpha = 0.75f),
                            ),
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Full: " + Formatters.signPrefix(grandRep) + Formatters.number(grandRep, repDec) + " " + rep,
                        style = TextStyle(fontFamily = JetBrainsMono, fontSize = 10.sp, color = Color.White.copy(alpha = 0.6f)),
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            item {
                PnlSourceCard(
                    icon = Icons.Rounded.Refresh, tint = DaftarColors.Copper,
                    label = "FX Trading",
                    meta = "${report.fxTradeCount} trade" + if (report.fxTradeCount != 1) "s" else "",
                    amountRep = fxRep, rep = rep, repDec = repDec,
                )
                PnlSourceCard(
                    icon = Icons.AutoMirrored.Rounded.Send, tint = DaftarColors.Blue,
                    label = "Hawala Commission",
                    meta = "${report.hawalaCount} hawala" + if (report.hawalaCount != 1) "s" else "",
                    amountRep = commissionRep, rep = rep, repDec = repDec,
                    forcePositivePrefix = true,
                )
                if (report.includesRevaluation) {
                    PnlSourceCard(
                        icon = Icons.Rounded.Balance, tint = DaftarColors.Gold,
                        label = "Revaluation",
                        meta = "Rate move on cash",
                        amountRep = revalRep, rep = rep, repDec = repDec,
                        badge = "UNREALIZED",
                    )
                }
                Spacer(Modifier.height(14.dp))
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    MonoLabel("P&L entries · ${report.items.size}")
                }
                Spacer(Modifier.height(8.dp))
            }

            if (report.items.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 30.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Rounded.BarChart, null, tint = DaftarColors.Muted, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "No P&L activity in this period",
                            style = TextStyle(fontFamily = Inter, fontSize = 13.sp, color = DaftarColors.Muted),
                        )
                    }
                }
            } else {
                items(count = report.items.size) { index ->
                    PnlItemRow(report.items[index], state)
                }
            }
        }
    }
}

@Composable
private fun PnlSourceCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    label: String,
    meta: String,
    amountRep: Double,
    rep: String,
    repDec: Int,
    badge: String? = null,
    forcePositivePrefix: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(DaftarColors.PaperSoft)
            .border(1.dp, DaftarColors.Line, RoundedCornerShape(13.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    label,
                    style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = DaftarColors.Ink),
                )
                if (badge != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(DaftarColors.Gold.copy(alpha = 0.2f))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    ) {
                        Text(
                            badge,
                            style = TextStyle(
                                fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold,
                                fontSize = 7.sp, letterSpacing = 0.1.em, color = DaftarColors.CopperDeep,
                            ),
                        )
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(meta, style = TextStyle(fontFamily = JetBrainsMono, fontSize = 10.sp, color = DaftarColors.Muted))
        }
        Text(
            text = (if (forcePositivePrefix) "+" else Formatters.signPrefix(amountRep, 0.5)) +
                Formatters.compact(abs(amountRep), repDec),
            style = TextStyle(
                fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 17.sp,
                color = when {
                    forcePositivePrefix || amountRep > 0.5 -> DaftarColors.Green
                    amountRep < -0.5 -> DaftarColors.Red
                    else -> DaftarColors.Muted
                },
            ),
        )
    }
}

@Composable
private fun PnlItemRow(item: PnlItem, state: PnlUiState) {
    val (icon, tint, tag) = when (item.source) {
        PnlSource.FX_TRADE -> Triple(Icons.Rounded.Refresh, DaftarColors.Copper, "FX")
        PnlSource.HAWALA_COMMISSION -> Triple(Icons.AutoMirrored.Rounded.Send, DaftarColors.Blue, "COMMISSION")
        PnlSource.REVALUATION -> Triple(Icons.Rounded.Balance, DaftarColors.Gold, "REVAL")
    }
    val amountRep = state.toReporting(item.amountAfn)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(DaftarColors.PaperSoft)
            .border(1.dp, DaftarColors.Line, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(13.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.label,
                style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = DaftarColors.Ink),
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(tint.copy(alpha = 0.12f))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                ) {
                    Text(
                        tag,
                        style = TextStyle(
                            fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold,
                            fontSize = 7.sp, letterSpacing = 0.08.em, color = tint,
                        ),
                    )
                }
                Text(
                    text = item.dateLabel + (item.partnerName?.let { " · $it" } ?: ""),
                    style = TextStyle(fontFamily = JetBrainsMono, fontSize = 9.sp, color = DaftarColors.Muted),
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = Formatters.signPrefix(amountRep, 0.5).ifEmpty { "" } +
                    Formatters.compact(abs(amountRep), state.reportingDecimals),
                style = TextStyle(
                    fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 15.sp,
                    color = when {
                        amountRep > 0.5 -> DaftarColors.Green
                        amountRep < -0.5 -> DaftarColors.Red
                        else -> DaftarColors.Muted
                    },
                ),
            )
            MonoLabel(state.reportingCurrency, fontSize = 8, letterSpacing = 0.1)
        }
    }
}

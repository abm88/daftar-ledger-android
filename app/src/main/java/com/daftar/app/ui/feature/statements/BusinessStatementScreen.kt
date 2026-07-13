package com.daftar.app.ui.feature.statements

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.daftar.app.core.format.Formatters
import com.daftar.app.core.time.TimeProvider
import com.daftar.app.domain.model.LedgerEntry
import com.daftar.app.domain.model.LedgerEntryKind
import com.daftar.app.domain.model.LedgerPeriod
import com.daftar.app.domain.model.ShopProfile
import com.daftar.app.domain.repository.RatesRepository
import com.daftar.app.domain.repository.SettingsRepository
import com.daftar.app.domain.usecase.ActivityFeedBuilder
import com.daftar.app.ui.common.DaftarFilterChip
import com.daftar.app.ui.common.IconSquareButton
import com.daftar.app.ui.common.MonoLabel
import com.daftar.app.ui.feature.ledger.LedgerFilter
import com.daftar.app.ui.theme.DaftarColors
import com.daftar.app.ui.theme.Inter
import com.daftar.app.ui.theme.JetBrainsMono
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class BusinessStatementUiState(
    val period: LedgerPeriod = LedgerPeriod.ALL,
    val filter: LedgerFilter = LedgerFilter.ALL,
    val entries: List<LedgerEntry> = emptyList(),
    val inflowAfn: Double = 0.0,
    val outflowAfn: Double = 0.0,
    val fromLabel: String = "—",
    val toLabel: String = "",
    val issuedLabel: String = "",
    val profile: ShopProfile = ShopProfile(),
) {
    val netAfn: Double get() = inflowAfn - outflowAfn
}

@HiltViewModel
class BusinessStatementViewModel @Inject constructor(
    activityFeedBuilder: ActivityFeedBuilder,
    ratesRepository: RatesRepository,
    settingsRepository: SettingsRepository,
    private val timeProvider: TimeProvider,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val period = MutableStateFlow(LedgerPeriod.ALL)
    private val filter = MutableStateFlow(
        when (savedStateHandle.get<String>("filter")) {
            "hawala" -> LedgerFilter.HAWALA
            "customer" -> LedgerFilter.CUSTOMER
            "fx" -> LedgerFilter.FX
            "settle" -> LedgerFilter.SETTLE
            else -> LedgerFilter.ALL
        },
    )

    val uiState = combine(
        activityFeedBuilder.observe(),
        ratesRepository.rateBook,
        settingsRepository.shopProfile,
        period,
        filter,
    ) { feed, rates, profile, period, filter ->
        val now = timeProvider.nowMillis()
        val todayStart = timeProvider.startOfTodayMillis()
        val (from, to) = when (period) {
            LedgerPeriod.TODAY -> todayStart to now + 1
            LedgerPeriod.YESTERDAY -> (todayStart - DAY_MS) to todayStart
            LedgerPeriod.WEEK -> (todayStart - 6 * DAY_MS) to now + 1
            LedgerPeriod.MONTH -> timeProvider.startOfMonthMillis() to now + 1
            LedgerPeriod.ALL -> 0L to now + 1
        }
        val entries = feed
            .filter { it.timestampMillis in from until to }
            .filter { filter.kind == null || it.kind == filter.kind }
            .sortedBy { it.timestampMillis }

        var inflow = 0.0
        var outflow = 0.0
        entries.forEach { entry ->
            val afn = rates.toAfnOrFallback(entry.currency, entry.amount)
            when {
                entry.direction > 0 -> inflow += afn
                entry.direction < 0 -> outflow += afn
            }
        }

        BusinessStatementUiState(
            period = period,
            filter = filter,
            entries = entries,
            inflowAfn = inflow,
            outflowAfn = outflow,
            fromLabel = if (period == LedgerPeriod.ALL) {
                entries.firstOrNull()?.let { Formatters.fullDateLabel(it.timestampMillis) } ?: "—"
            } else Formatters.fullDateLabel(from),
            toLabel = Formatters.fullDateLabel(now),
            issuedLabel = Formatters.fullDateLabel(now),
            profile = profile,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BusinessStatementUiState())

    fun setPeriod(value: LedgerPeriod) { period.value = value }
    fun setFilter(value: LedgerFilter) { filter.value = value }

    private companion object {
        const val DAY_MS = 86_400_000L
    }
}

/** Full-business statement across all activity, with period and type controls. */
@Composable
fun BusinessStatementScreen(
    navController: NavController,
    viewModel: BusinessStatementViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // v18 ledger-statement print doc ("business-statement-{period}").
    val printSpec = {
        com.daftar.app.core.print.StatementPrintSpec(
            jobName = "business-statement-" + state.period.label.lowercase().replace(Regex("[^a-z0-9]+"), "-"),
            docTitle = "Business Statement",
            pashtoTitle = "د سوداګرۍ راپور",
            metaLeftLabel = "Period",
            metaLeftValue = state.period.label,
            metaLeftSub = "${state.fromLabel} → ${state.toLabel}",
            metaRightLabel = "Issued",
            metaRightValue = state.issuedLabel,
            metaRightSub = "${state.entries.size} entries",
            summary = listOf(
                com.daftar.app.core.print.PrintSummaryCell(
                    "IN", "+" + Formatters.number(state.inflowAfn), "AFN-EQ",
                ),
                com.daftar.app.core.print.PrintSummaryCell(
                    "OUT", "−" + Formatters.number(state.outflowAfn), "AFN-EQ",
                ),
                com.daftar.app.core.print.PrintSummaryCell(
                    "NET",
                    Formatters.signPrefix(state.netAfn).ifEmpty { "" } + Formatters.number(abs(state.netAfn)),
                    "AFN-EQ",
                ),
            ),
            columns = listOf("Date", "Type", "Description", "Cur", "Debit", "Credit"),
            rows = state.entries.map { entry ->
                val (title, subtitle) = describeEntry(entry)
                val amountText = Formatters.number(entry.amount)
                listOf(
                    Formatters.fullDateLabel(entry.timestampMillis),
                    title,
                    subtitle,
                    entry.currency,
                    if (entry.direction < 0) amountText else "—",
                    if (entry.direction > 0) amountText else "—",
                )
            },
            profile = state.profile,
            issuedLabel = state.issuedLabel,
        )
    }
    val printContext = androidx.compose.ui.platform.LocalContext.current

    Column(modifier = Modifier.fillMaxWidth()) {
        StatementHeaderBar(
            title = "Business Statement",
            subtitle = "${state.period.label} · ${state.entries.size} entries",
            onBack = { navController.popBackStack() },
            trailing = {
                IconSquareButton(
                    androidx.compose.material.icons.Icons.Rounded.Download,
                    { com.daftar.app.core.print.StatementPrinter.print(printContext, printSpec()) },
                    onDark = true,
                )
            },
        )

        // Period & type controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(DaftarColors.PaperDeep)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            MonoLabel("Period", fontSize = 9)
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LedgerPeriod.entries.forEach { p ->
                    DaftarFilterChip(
                        label = when (p) {
                            LedgerPeriod.WEEK -> "7 days"
                            else -> p.label
                        },
                        selected = state.period == p,
                        onClick = { viewModel.setPeriod(p) },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            MonoLabel("Type", fontSize = 9)
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LedgerFilter.entries.forEach { f ->
                    DaftarFilterChip(
                        label = f.label,
                        selected = state.filter == f,
                        onClick = { viewModel.setFilter(f) },
                        selectedColor = DaftarColors.Copper,
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(DaftarColors.PaperSoft)
                .border(1.dp, DaftarColors.LineStrong, RoundedCornerShape(8.dp))
                .padding(18.dp),
        ) {
            StatementMasthead(state.profile, "Business Statement", "د کار راپور · ${state.period.pashtoLabel}")
            StatementMetaRow(
                leftLabel = "Period", leftValue = state.period.label,
                leftSub = "${state.fromLabel} → ${state.toLabel}",
                rightLabel = "Issued", rightValue = state.issuedLabel,
                rightSub = if (state.filter == LedgerFilter.ALL) "All activity types" else "${state.filter.label} only",
            )
            StatementSummary(
                listOf(
                    StatementSummaryCell("IN", "+" + Formatters.number(state.inflowAfn), "AFN · RECEIVED", DaftarColors.Green),
                    StatementSummaryCell("OUT", "−" + Formatters.number(state.outflowAfn), "AFN · PAID", DaftarColors.Red),
                    StatementSummaryCell(
                        "NET",
                        Formatters.signPrefix(state.netAfn).ifEmpty { "" } + Formatters.number(abs(state.netAfn)),
                        "AFN · NET",
                        when {
                            state.netAfn > 0 -> DaftarColors.Green
                            state.netAfn < 0 -> DaftarColors.Red
                            else -> DaftarColors.Muted
                        },
                    ),
                ),
            )

            Spacer(Modifier.height(16.dp))
            if (state.entries.isEmpty()) {
                MonoLabel("No activity in selected period", fontSize = 10)
            } else {
                state.entries.forEach { entry -> BusinessStatementLine(entry) }
            }

            StatementFooter(state.issuedLabel.uppercase())
        }

        StatementActions(modifier = Modifier.navigationBarsPadding(), printSpec = printSpec)
    }
}

@Composable
private fun BusinessStatementLine(entry: LedgerEntry) {
    val (title, subtitle) = describeEntry(entry)
    val kindLabel = when (entry.kind) {
        LedgerEntryKind.HAWALA -> "HAWALA"
        LedgerEntryKind.CUSTOMER_TX -> "CUSTOMER"
        LedgerEntryKind.FX -> "FX TRADE"
        LedgerEntryKind.SETTLEMENT -> "SETTLEMENT"
    }
    // v18 tints the tag with the entry's icon color rather than fixed copper.
    val kindColor = when (entry.kind) {
        LedgerEntryKind.HAWALA -> DaftarColors.Blue
        LedgerEntryKind.CUSTOMER_TX -> if (entry.direction > 0) DaftarColors.Green else DaftarColors.Red
        LedgerEntryKind.FX -> DaftarColors.CopperDeep
        LedgerEntryKind.SETTLEMENT -> DaftarColors.CopperDeep
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.width(64.dp)) {
                Text(
                    Formatters.dayMonthLabel(entry.timestampMillis),
                    style = TextStyle(fontFamily = JetBrainsMono, fontSize = 9.sp, color = DaftarColors.InkSoft),
                )
                Text(
                    Formatters.timeLabel(entry.timestampMillis),
                    style = TextStyle(fontFamily = JetBrainsMono, fontSize = 8.sp, color = DaftarColors.Muted),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        kindLabel,
                        style = TextStyle(
                            fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 7.sp,
                            letterSpacing = 0.08.em, color = kindColor,
                        ),
                    )
                    Text(
                        title,
                        style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = DaftarColors.Ink),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    subtitle,
                    style = TextStyle(fontFamily = Inter, fontSize = 9.sp, color = DaftarColors.Muted),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // v18's statement table leaves the debit/credit cells blank ("—")
            // for neutral rows (hawalas, FX) and prints amounts with 0 decimals.
            Text(
                text = if (entry.direction == 0) "—" else {
                    (if (entry.direction > 0) "+" else "−") +
                        Formatters.number(entry.amount) + " " + entry.currency
                },
                style = TextStyle(
                    fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 10.sp,
                    color = when {
                        entry.direction > 0 -> DaftarColors.Green
                        entry.direction < 0 -> DaftarColors.Red
                        else -> DaftarColors.Muted
                    },
                ),
            )
        }
        Spacer(Modifier.height(6.dp))
        HorizontalDivider(color = DaftarColors.Line)
    }
}

// v18 reuses the activity-feed titles/subtitles: "You Received · name",
// the account-funded "Hawala debit" special case, the "· pending" marker,
// and the FX realized-P&L suffix.
private fun describeEntry(entry: LedgerEntry): Pair<String, String> = when (entry) {
    is LedgerEntry.HawalaEntry -> {
        val h = entry.hawala
        val direction = if (h.type == com.daftar.app.domain.model.HawalaType.SEND) "Sent hawala" else "Received hawala"
        "$direction · ${entry.partner.shortName}" to buildString {
            append("${h.fromCity.code} → ${h.toCity.code} · ${h.senderName} → ${h.receiverName}")
            if (h.status == com.daftar.app.domain.model.HawalaStatus.PENDING) append(" · pending")
        }
    }
    is LedgerEntry.SettlementEntry ->
        "Settlement · ${entry.partner.shortName}" to (entry.hawala.note ?: "Position offset")
    is LedgerEntry.CustomerTxEntry -> {
        val title = if (entry.isHawalaLinked) "Hawala debit" else entry.tx.type.feedLabel
        "$title · ${entry.customer.name}" to
            (entry.tx.note ?: "Customer account · ${entry.customer.city.displayName}")
    }
    is LedgerEntry.FxEntry -> {
        val t = entry.trade
        val realized = t.realizedPnlAfn
        val plSuffix = if (realized != null && kotlin.math.abs(realized) >= 0.5) {
            " · " + (if (realized >= 0) "+" else "−") + Formatters.number(realized) +
                " AFN " + (if (realized >= 0) "profit" else "loss")
        } else ""
        "${if (t.side == com.daftar.app.domain.model.FxSide.SELL) "Sold" else "Bought"} ${t.fromCurrency} → ${t.toCurrency}" to
            "${Formatters.amount(t.fromAmount, t.fromCurrency)} ${t.fromCurrency} @ ${Formatters.ratePlain(t.rate)}$plSuffix"
    }
}

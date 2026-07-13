package com.daftar.app.ui.feature.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.daftar.app.core.format.Formatters
import com.daftar.app.core.time.TimeProvider
import com.daftar.app.domain.model.LedgerEntry
import com.daftar.app.domain.model.LedgerEntryKind
import com.daftar.app.domain.repository.RatesRepository
import com.daftar.app.domain.usecase.ActivityFeedBuilder
import com.daftar.app.ui.common.DaftarFilterChip
import com.daftar.app.ui.common.DaftarSearchField
import com.daftar.app.ui.common.IconSquareButton
import com.daftar.app.ui.common.MonoLabel
import com.daftar.app.ui.common.SyncIconButton
import com.daftar.app.ui.components.EmptyState
import com.daftar.app.ui.components.EmptyStateTone
import com.daftar.app.ui.components.ledgerFeedItems
import com.daftar.app.ui.feature.home.NewEntryFab
import com.daftar.app.ui.feature.home.openLedgerEntry
import com.daftar.app.ui.navigation.DaftarDestinations
import com.daftar.app.ui.theme.DaftarColors
import com.daftar.app.ui.theme.Fraunces
import com.daftar.app.ui.theme.Inter
import com.daftar.app.ui.theme.JetBrainsMono
import com.daftar.app.ui.theme.NotoNaskhArabic
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LedgerFilter(val label: String, val kind: LedgerEntryKind?) {
    ALL("All", null),
    HAWALA("Hawalas", LedgerEntryKind.HAWALA),
    CUSTOMER("Customer", LedgerEntryKind.CUSTOMER_TX),
    FX("FX", LedgerEntryKind.FX),
    SETTLE("Settle", LedgerEntryKind.SETTLEMENT),
}

data class GeneralLedgerUiState(
    val entries: List<LedgerEntry> = emptyList(),
    val totalCount: Int = 0,
    val todayCount: Int = 0,
    val counts: Map<LedgerFilter, Int> = emptyMap(),
    val filter: LedgerFilter = LedgerFilter.ALL,
    val search: String = "",
    val todayInAfn: Double = 0.0,
    val todayOutAfn: Double = 0.0,
    val todayStartMillis: Long = 0,
    val syncing: Boolean = false,
) {
    val todayNetAfn: Double get() = todayInAfn - todayOutAfn
}

@HiltViewModel
class GeneralLedgerViewModel @Inject constructor(
    activityFeedBuilder: ActivityFeedBuilder,
    ratesRepository: RatesRepository,
    private val timeProvider: TimeProvider,
    private val toastCenter: com.daftar.app.ui.common.ToastCenter,
) : ViewModel() {

    private val filter = MutableStateFlow(LedgerFilter.ALL)
    private val search = MutableStateFlow("")
    private val syncing = MutableStateFlow(false)

    // TODO(backend): replace the simulated delay with a real ledger-sync call.
    fun sync() {
        if (syncing.value) return
        viewModelScope.launch {
            syncing.value = true
            kotlinx.coroutines.delay(1100)
            syncing.value = false
            toastCenter.show("Ledger synced", com.daftar.app.ui.common.ToastIcon.REFRESH)
        }
    }

    val uiState = combine(
        activityFeedBuilder.observe(),
        ratesRepository.rateBook,
        filter,
        search,
        syncing,
    ) { feed, rates, filter, search, syncing ->
        val todayStart = timeProvider.startOfTodayMillis()
        val todayEntries = feed.filter { it.timestampMillis >= todayStart }
        var inAfn = 0.0
        var outAfn = 0.0
        todayEntries.forEach { entry ->
            // v18 toAfn falls back (USD→72, PKR→0.28, others pass through)
            // instead of dropping unquoted currencies to zero.
            val afn = rates.toAfnOrFallback(entry.currency, entry.amount)
            when {
                entry.direction > 0 -> inAfn += afn
                entry.direction < 0 -> outAfn += afn
            }
        }
        val query = search.trim().lowercase()
        val visible = feed
            .filter { filter.kind == null || it.kind == filter.kind }
            .filter { query.isEmpty() || entryMatches(it, query) }
        GeneralLedgerUiState(
            entries = visible,
            totalCount = feed.size,
            todayCount = todayEntries.size,
            counts = LedgerFilter.entries.associateWith { f ->
                if (f.kind == null) feed.size else feed.count { it.kind == f.kind }
            },
            filter = filter,
            search = search,
            todayInAfn = inAfn,
            todayOutAfn = outAfn,
            todayStartMillis = todayStart,
            syncing = syncing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GeneralLedgerUiState())

    fun setFilter(value: LedgerFilter) { filter.value = value }
    fun setSearch(value: String) { search.value = value }

    // v18 matches against the rendered title + subtitle + currency, so city
    // codes, "pending", the "You Received/You Gave" wording, and FX P&L text
    // are all searchable. The pickup code stays searchable as an Android extra.
    private fun entryMatches(entry: LedgerEntry, query: String): Boolean {
        val haystack = when (entry) {
            is LedgerEntry.SettlementEntry ->
                "settlement · ${entry.partner.shortName} ${entry.hawala.note ?: "Position offset"}"
            is LedgerEntry.HawalaEntry -> buildString {
                append(if (entry.hawala.type == com.daftar.app.domain.model.HawalaType.SEND) "Sent hawala · " else "Received hawala · ")
                append(entry.partner.shortName)
                append(" ${entry.hawala.fromCity.code} → ${entry.hawala.toCity.code}")
                append(" · ${entry.hawala.senderName} → ${entry.hawala.receiverName}")
                if (entry.hawala.status == com.daftar.app.domain.model.HawalaStatus.PENDING) append(" · pending")
                append(" ${entry.hawala.pickupCode}")
            }
            is LedgerEntry.CustomerTxEntry -> buildString {
                append(if (entry.isHawalaLinked) "Hawala debit · " else "${entry.tx.type.feedLabel} · ")
                append(entry.customer.name)
                append(" ${entry.tx.note ?: "Customer account · ${entry.customer.city.displayName}"}")
            }
            is LedgerEntry.FxEntry -> buildString {
                val trade = entry.trade
                append(if (trade.side == com.daftar.app.domain.model.FxSide.SELL) "Sold " else "Bought ")
                append("${trade.fromCurrency} → ${trade.toCurrency}")
                append(" @ ${Formatters.ratePlain(trade.rate)}")
                val realized = trade.realizedPnlAfn
                if (realized != null && kotlin.math.abs(realized) >= 0.5) {
                    append(if (realized >= 0) " profit" else " loss")
                }
                append(" ${trade.note ?: ""}")
            }
        } + " " + entry.currency
        return haystack.lowercase().contains(query)
    }
}

/** The dedicated General Ledger tab: today ribbon, filters, search, full feed. */
@Composable
fun GeneralLedgerScreen(
    navController: NavController,
    onOpenNewEntry: () -> Unit,
    viewModel: GeneralLedgerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier.statusBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 120.dp),
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = "عمومي دفتر",
                                style = TextStyle(
                                    fontFamily = NotoNaskhArabic,
                                    fontSize = 13.sp,
                                    color = DaftarColors.Muted,
                                    textDirection = TextDirection.Rtl,
                                ),
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(DaftarColors.Ink)
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                            ) {
                                MonoLabel("General Ledger", color = DaftarColors.Paper, fontSize = 9, letterSpacing = 0.1)
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "All activity",
                            style = MaterialTheme.typography.headlineMedium,
                            color = DaftarColors.Ink,
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "${state.totalCount} entries · ${state.todayCount} today",
                            style = TextStyle(fontFamily = Inter, fontSize = 12.sp, color = DaftarColors.Muted),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        IconSquareButton(
                            icon = Icons.Rounded.Print,
                            onClick = {
                                navController.navigate(
                                    DaftarDestinations.businessStatement(state.filter.name.lowercase()),
                                )
                            },
                        )
                        SyncIconButton(syncing = state.syncing, onClick = viewModel::sync)
                    }
                }
            }

            item { TodayRibbon(state) }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    LedgerFilter.entries.forEach { f ->
                        DaftarFilterChip(
                            label = f.label,
                            selected = state.filter == f,
                            onClick = { viewModel.setFilter(f) },
                            count = state.counts[f] ?: 0,
                        )
                    }
                }
            }

            item {
                DaftarSearchField(
                    value = state.search,
                    onValueChange = viewModel::setSearch,
                    placeholder = "Search ledger…",
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (state.entries.isEmpty()) {
                if (state.search.isBlank() && state.filter == LedgerFilter.ALL) {
                    // v18 first-run state with a New Entry CTA.
                    item {
                        EmptyState(
                            icon = Icons.AutoMirrored.Rounded.MenuBook,
                            title = "No activity yet",
                            pashto = "تر اوسه هیڅ فعالیت نشته",
                            sub = "Your trades, hawalas, and account entries will appear here as you record them. Tap New Entry to begin.",
                            tone = EmptyStateTone.COPPER,
                            ctaLabel = "New Entry · نوې لیکنه",
                            ctaIcon = Icons.Rounded.Add,
                            onCta = onOpenNewEntry,
                        )
                    }
                } else {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "No matching activity",
                                style = TextStyle(fontFamily = Inter, fontSize = 13.sp, color = DaftarColors.Muted),
                            )
                        }
                    }
                }
            } else {
                ledgerFeedItems(
                    entries = state.entries,
                    todayStartMillis = state.todayStartMillis,
                    onOpen = { entry -> openLedgerEntry(entry, navController) },
                )
            }
        }

        NewEntryFab(
            onClick = onOpenNewEntry,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 20.dp),
        )
    }
}

@Composable
private fun TodayRibbon(state: GeneralLedgerUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 14.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(DaftarColors.Ink)
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                androidx.compose.material3.Icon(
                    Icons.Rounded.Schedule, null,
                    tint = DaftarColors.GoldSoft,
                    modifier = Modifier.height(11.dp),
                )
                MonoLabel("Today · نن", color = DaftarColors.GoldSoft, fontSize = 9)
            }
            MonoLabel("AFN-equivalent", color = DaftarColors.GoldSoft.copy(alpha = 0.7f), fontSize = 9, letterSpacing = 0.1)
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            RibbonCell("In", "+" + Formatters.number(state.todayInAfn), DaftarColors.LongGreen, Modifier.weight(1f))
            RibbonCell("Out", "−" + Formatters.number(state.todayOutAfn), DaftarColors.ShortRed, Modifier.weight(1f))
            RibbonCell(
                label = "Net",
                value = (if (state.todayNetAfn >= 0) "+" else "−") + Formatters.number(state.todayNetAfn),
                color = when {
                    state.todayNetAfn > 0 -> DaftarColors.LongGreen
                    state.todayNetAfn < 0 -> DaftarColors.ShortRed
                    else -> DaftarColors.GoldSoft
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun RibbonCell(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier = modifier) {
        MonoLabel(label, color = DaftarColors.GoldSoft.copy(alpha = 0.85f), fontSize = 8)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = TextStyle(
                fontFamily = Fraunces,
                fontWeight = FontWeight.Medium,
                fontSize = 18.sp,
                color = color,
            ),
        )
    }
}

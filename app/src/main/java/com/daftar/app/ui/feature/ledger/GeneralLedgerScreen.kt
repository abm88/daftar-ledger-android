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
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Refresh
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
) {
    val todayNetAfn: Double get() = todayInAfn - todayOutAfn
}

@HiltViewModel
class GeneralLedgerViewModel @Inject constructor(
    activityFeedBuilder: ActivityFeedBuilder,
    ratesRepository: RatesRepository,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val filter = MutableStateFlow(LedgerFilter.ALL)
    private val search = MutableStateFlow("")

    val uiState = combine(
        activityFeedBuilder.observe(),
        ratesRepository.rateBook,
        filter,
        search,
    ) { feed, rates, filter, search ->
        val todayStart = timeProvider.startOfTodayMillis()
        val todayEntries = feed.filter { it.timestampMillis >= todayStart }
        var inAfn = 0.0
        var outAfn = 0.0
        todayEntries.forEach { entry ->
            val afn = rates.toAfn(entry.currency, entry.amount)
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
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GeneralLedgerUiState())

    fun setFilter(value: LedgerFilter) { filter.value = value }
    fun setSearch(value: String) { search.value = value }

    private fun entryMatches(entry: LedgerEntry, query: String): Boolean {
        val haystack = when (entry) {
            is LedgerEntry.HawalaEntry ->
                "${entry.partner.name} ${entry.hawala.senderName} ${entry.hawala.receiverName} ${entry.hawala.pickupCode}"
            is LedgerEntry.SettlementEntry -> "settlement ${entry.partner.name} ${entry.hawala.note ?: ""}"
            is LedgerEntry.CustomerTxEntry -> "${entry.customer.name} ${entry.tx.note ?: ""} ${entry.tx.type.label}"
            is LedgerEntry.FxEntry -> "${entry.trade.fromCurrency} ${entry.trade.toCurrency} ${entry.trade.note ?: ""}"
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
                        IconSquareButton(icon = Icons.Rounded.Refresh, onClick = {})
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
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (state.search.isNotBlank() || state.filter != LedgerFilter.ALL) {
                                "No matching activity"
                            } else "No activity yet",
                            style = TextStyle(fontFamily = Inter, fontSize = 13.sp, color = DaftarColors.Muted),
                        )
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
            MonoLabel("Today · نن", color = DaftarColors.GoldSoft, fontSize = 9)
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

package com.daftar.app.ui.feature.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
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
import com.daftar.app.domain.model.LedgerEntry
import com.daftar.app.domain.model.LedgerEntryKind
import com.daftar.app.domain.repository.RatesRepository
import com.daftar.app.domain.repository.LedgerRefreshRepository
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

/** One entry rendered as an accounting-table row (v20 Table view). */
data class LedgerTableRow(
    val title: String,
    val timeLabel: String,
    val isIn: Boolean,
    val amount: Double,
    val currency: String,
    val entry: LedgerEntry,
)

data class LedgerTableDay(val label: String, val netAfn: Double, val rows: List<LedgerTableRow>)

data class LedgerTableData(
    val days: List<LedgerTableDay> = emptyList(),
    val totalInAfn: Double = 0.0,
    val totalOutAfn: Double = 0.0,
) {
    val netAfn: Double get() = totalInAfn - totalOutAfn
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
    /** v20 persisted view mode: false = feed, true = accounting table. */
    val tableView: Boolean = false,
    val tableData: LedgerTableData = LedgerTableData(),
) {
    val todayNetAfn: Double get() = todayInAfn - todayOutAfn
}

@HiltViewModel
class GeneralLedgerViewModel @Inject constructor(
    activityFeedBuilder: ActivityFeedBuilder,
    ratesRepository: RatesRepository,
    private val settingsRepository: com.daftar.app.domain.repository.SettingsRepository,
    private val ledgerRefresh: LedgerRefreshRepository,
    private val timeProvider: TimeProvider,
    private val toastCenter: com.daftar.app.ui.common.ToastCenter,
) : ViewModel() {

    private val filter = MutableStateFlow(LedgerFilter.ALL)
    private val search = MutableStateFlow("")
    private val syncing = MutableStateFlow(false)

    fun sync() {
        if (syncing.value) return
        viewModelScope.launch {
            syncing.value = true
            val result = runCatching { ledgerRefresh.refresh() }
            syncing.value = false
            toastCenter.show(
                if (result.isSuccess) "Ledger synced" else result.exceptionOrNull()?.message ?: "Unable to sync ledger",
                if (result.isSuccess) com.daftar.app.ui.common.ToastIcon.REFRESH else com.daftar.app.ui.common.ToastIcon.CROSS,
            )
        }
    }

    val uiState = combine(
        activityFeedBuilder.observe(),
        ratesRepository.rateBook,
        settingsRepository.settings,
        combine(filter, search, syncing) { f, s, sy -> Triple(f, s, sy) },
    ) { feed, rates, settings, (filter, search, syncing) ->
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
            tableView = settings.ledgerTableView,
            tableData = buildTableData(visible, rates, todayStart),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GeneralLedgerUiState())

    fun setFilter(value: LedgerFilter) { filter.value = value }
    fun setSearch(value: String) { search.value = value }

    /** Persist the Feed/Table choice so it survives navigation and restarts. */
    fun setTableView(tableView: Boolean) {
        viewModelScope.launch { settingsRepository.setLedgerTableView(tableView) }
    }

    // Groups the visible entries by day, tagging each as money in/out and
    // accumulating AFN-equivalent day-nets and grand totals for the footer.
    private fun buildTableData(
        entries: List<LedgerEntry>,
        rates: com.daftar.app.domain.model.RateBook,
        todayStart: Long,
    ): LedgerTableData {
        if (entries.isEmpty()) return LedgerTableData()
        val byDay = entries.groupBy { Math.floorDiv(it.timestampMillis - todayStart, 86_400_000L) }
        var totalIn = 0.0
        var totalOut = 0.0
        val days = byDay.keys.sortedDescending().map { off ->
            val group = byDay.getValue(off)
            var dayIn = 0.0
            var dayOut = 0.0
            val rows = group.map { e ->
                val isIn = com.daftar.app.ui.components.ledgerEntryIsIncoming(e)
                val afn = rates.toAfnOrFallback(e.currency, e.amount)
                if (isIn) { dayIn += afn; totalIn += afn } else { dayOut += afn; totalOut += afn }
                LedgerTableRow(
                    title = com.daftar.app.ui.components.ledgerEntryTitle(e),
                    timeLabel = Formatters.timeLabel(e.timestampMillis),
                    isIn = isIn,
                    amount = e.amount,
                    currency = e.currency,
                    entry = e,
                )
            }
            LedgerTableDay(
                label = Formatters.relativeDayLabel(todayStart + off * 86_400_000L, todayStart),
                netAfn = dayIn - dayOut,
                rows = rows,
            )
        }
        return LedgerTableData(days, totalIn, totalOut)
    }

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

            // v20: search box and Feed/Table toggle share one row.
            item {
                LedgerControlsRow(
                    search = state.search,
                    onSearchChange = viewModel::setSearch,
                    tableView = state.tableView,
                    onSetTableView = viewModel::setTableView,
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
            } else if (state.tableView) {
                item {
                    LedgerTable(
                        data = state.tableData,
                        onOpen = { entry -> openLedgerEntry(entry, navController) },
                    )
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

/** Search box + Feed/Table toggle sharing one row (v20). */
@Composable
private fun LedgerControlsRow(
    search: String,
    onSearchChange: (String) -> Unit,
    tableView: Boolean,
    onSetTableView: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Inline search (no outer padding, so it sits flush next to the toggle).
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(DaftarColors.PaperSoft)
                .border(1.dp, DaftarColors.LineStrong, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                if (search.isEmpty()) {
                    Text(
                        text = "Search ledger…",
                        style = TextStyle(fontFamily = Inter, fontSize = 13.sp, color = DaftarColors.MutedLight),
                    )
                }
                androidx.compose.foundation.text.BasicTextField(
                    value = search,
                    onValueChange = onSearchChange,
                    textStyle = TextStyle(fontFamily = Inter, fontSize = 13.sp, color = DaftarColors.Ink),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            androidx.compose.material3.Icon(
                Icons.Rounded.Search, null,
                tint = DaftarColors.Muted,
                modifier = Modifier.size(16.dp),
            )
        }

        // Compact Feed/Table toggle.
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(DaftarColors.PaperDeep)
                .border(1.dp, DaftarColors.Line, RoundedCornerShape(12.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            LedgerViewButton(
                icon = Icons.AutoMirrored.Rounded.MenuBook,
                selected = !tableView,
                onClick = { onSetTableView(false) },
            )
            LedgerViewButton(
                icon = Icons.AutoMirrored.Rounded.List,
                selected = tableView,
                onClick = { onSetTableView(true) },
            )
        }
    }
}

@Composable
private fun LedgerViewButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) DaftarColors.Ink else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(
            icon, null,
            tint = if (selected) DaftarColors.Paper else DaftarColors.Muted,
            modifier = Modifier.size(15.dp),
        )
    }
}

/**
 * Accounting-style table: Entry / In (green) / Out (red) columns grouped by day
 * with day-nets and a totals footer. Small, low-weight text for max density.
 */
@Composable
private fun LedgerTable(data: LedgerTableData, onOpen: (LedgerEntry) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 30.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, DaftarColors.Line, RoundedCornerShape(14.dp))
            .background(DaftarColors.PaperSoft),
    ) {
        // Column header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DaftarColors.Ink)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TableHeaderCell("Entry · لیکنه", Modifier.weight(1f), DaftarColors.Paper, TextAlign.Start)
            TableHeaderCell("In · راغلې", Modifier.width(74.dp), DaftarColors.LongGreen, TextAlign.End)
            TableHeaderCell("Out · وتلې", Modifier.width(74.dp), DaftarColors.ShortRed, TextAlign.End)
        }

        data.days.forEach { day ->
            // Day divider
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DaftarColors.PaperDeep)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = day.label.uppercase(),
                    style = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold, fontSize = 8.5.sp, letterSpacing = 0.1.em, color = DaftarColors.InkSoft),
                )
                Text(
                    text = "Net " + (if (day.netAfn >= 0) "+" else "−") + Formatters.compact(kotlin.math.abs(day.netAfn)) + " AFN",
                    style = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold, fontSize = 8.5.sp, color = DaftarColors.Muted),
                )
            }
            day.rows.forEach { row -> LedgerTableRowView(row, onOpen) }
        }

        // Totals footer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DaftarColors.Ink)
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "TOTAL · ټول",
                    style = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold, fontSize = 8.5.sp, letterSpacing = 0.1.em, color = DaftarColors.GoldSoft),
                )
                Text(
                    text = "AFN-eq",
                    style = TextStyle(fontFamily = JetBrainsMono, fontSize = 7.sp, color = DaftarColors.GoldSoft.copy(alpha = 0.7f)),
                )
            }
            Text(
                text = "+" + Formatters.compact(data.totalInAfn),
                modifier = Modifier.width(74.dp),
                textAlign = TextAlign.End,
                style = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = DaftarColors.LongGreen),
            )
            Text(
                text = "−" + Formatters.compact(data.totalOutAfn),
                modifier = Modifier.width(74.dp),
                textAlign = TextAlign.End,
                style = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = DaftarColors.ShortRed),
            )
        }
        // Net line
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DaftarColors.PaperDeep)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Net across all entries · ",
                style = TextStyle(fontFamily = JetBrainsMono, fontSize = 9.5.sp, color = DaftarColors.Muted),
            )
            Text(
                text = (if (data.netAfn >= 0) "+" else "−") + Formatters.number(kotlin.math.abs(data.netAfn)) + " AFN",
                style = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 9.5.sp, color = if (data.netAfn >= 0) DaftarColors.Green else DaftarColors.Red),
            )
        }
    }
}

@Composable
private fun TableHeaderCell(text: String, modifier: Modifier, color: Color, align: TextAlign) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        textAlign = align,
        style = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold, fontSize = 8.sp, letterSpacing = 0.1.em, color = color),
    )
}

@Composable
private fun LedgerTableRowView(row: LedgerTableRow, onOpen: (LedgerEntry) -> Unit) {
    val (icon, tint) = com.daftar.app.ui.components.ledgerEntryIconStyle(row.entry)
    val iconBg = com.daftar.app.ui.components.ledgerEntryIconBackground(row.entry)
    val amountText = Formatters.number(row.amount, com.daftar.app.domain.model.AssetCatalog.decimalsFor(row.currency))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen(row.entry) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f).padding(end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier.size(22.dp).clip(RoundedCornerShape(7.dp)).background(iconBg),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(icon, null, tint = tint, modifier = Modifier.size(11.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.title,
                    style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = DaftarColors.Ink),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(
                    text = row.timeLabel,
                    style = TextStyle(fontFamily = JetBrainsMono, fontSize = 8.sp, color = DaftarColors.Muted),
                )
            }
        }
        TableNumCell(show = row.isIn, amount = amountText, currency = row.currency, color = DaftarColors.Green)
        TableNumCell(show = !row.isIn, amount = amountText, currency = row.currency, color = DaftarColors.Red)
    }
}

@Composable
private fun TableNumCell(show: Boolean, amount: String, currency: String, color: Color) {
    Column(
        modifier = Modifier.width(74.dp),
        horizontalAlignment = Alignment.End,
    ) {
        if (show) {
            Text(
                text = (if (color == DaftarColors.Green) "+" else "−") + amount,
                style = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, color = color),
            )
            Text(
                text = currency,
                style = TextStyle(fontFamily = JetBrainsMono, fontSize = 7.5.sp, letterSpacing = 0.06.em, color = DaftarColors.Muted),
            )
        } else {
            Text(
                text = "·",
                style = TextStyle(fontFamily = JetBrainsMono, fontSize = 12.sp, color = DaftarColors.LineStrong),
            )
        }
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

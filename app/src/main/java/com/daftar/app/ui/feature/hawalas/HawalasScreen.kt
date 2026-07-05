package com.daftar.app.ui.feature.hawalas

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.daftar.app.core.format.Formatters
import com.daftar.app.core.time.TimeProvider
import com.daftar.app.domain.model.Counterparty
import com.daftar.app.domain.model.Hawala
import com.daftar.app.domain.model.HawalaStatus
import com.daftar.app.domain.model.HawalaType
import com.daftar.app.domain.model.RatePair
import com.daftar.app.domain.repository.PartnerRepository
import com.daftar.app.domain.repository.RatesRepository
import com.daftar.app.ui.common.DaftarFilterChip
import com.daftar.app.ui.common.DaftarSearchField
import com.daftar.app.ui.common.MonoLabel
import com.daftar.app.ui.feature.accounts.EmptyNote
import com.daftar.app.ui.navigation.DaftarDestinations
import com.daftar.app.ui.theme.DaftarColors
import com.daftar.app.ui.theme.Fraunces
import com.daftar.app.ui.theme.Inter
import com.daftar.app.ui.theme.JetBrainsMono
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HawalaRowUi(val hawala: Hawala, val partner: Counterparty)

enum class HawalaStatusFilter { ALL, PENDING, PAID }

data class HawalasUiState(
    val totalCount: Int = 0,
    val pendingCount: Int = 0,
    val paidCount: Int = 0,
    val totalVolumeUsd: Double = 0.0,
    val partnerCount: Int = 0,
    val statusFilter: HawalaStatusFilter = HawalaStatusFilter.ALL,
    val currencyFilter: String = "all",
    val search: String = "",
    val today: List<HawalaRowUi> = emptyList(),
    val yesterday: List<HawalaRowUi> = emptyList(),
    val earlier: List<HawalaRowUi> = emptyList(),
) {
    val isEmpty: Boolean get() = today.isEmpty() && yesterday.isEmpty() && earlier.isEmpty()
}

@HiltViewModel
class HawalasViewModel @Inject constructor(
    partnerRepository: PartnerRepository,
    ratesRepository: RatesRepository,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val statusFilter = MutableStateFlow(HawalaStatusFilter.ALL)
    private val currencyFilter = MutableStateFlow("all")
    private val search = MutableStateFlow("")

    val uiState = combine(
        partnerRepository.partners,
        ratesRepository.rateBook,
        statusFilter,
        currencyFilter,
        search,
    ) { partners, rates, status, currency, query ->
        // Synthetic opening/settlement entries never show on the hawala daftar.
        val all = partners
            .flatMap { p -> p.hawalas.map { HawalaRowUi(it, p) } }
            .filter { !it.hawala.isSynthetic && it.hawala.type != HawalaType.SETTLEMENT }
            .sortedByDescending { it.hawala.timestampMillis }

        val usdAfn = rates.pairs[RatePair.USD_AFN]?.sell ?: 72.0
        val usdPkr = rates.pairs[RatePair.USD_PKR]?.sell ?: 285.0
        val totalUsd = all.sumOf { row ->
            when (row.hawala.currency) {
                "AFN" -> row.hawala.amount / usdAfn
                "PKR" -> row.hawala.amount / usdPkr
                else -> row.hawala.amount
            }
        }

        val q = query.trim().lowercase()
        val filtered = all.filter { row ->
            val h = row.hawala
            (status == HawalaStatusFilter.ALL ||
                (status == HawalaStatusFilter.PENDING && h.status == HawalaStatus.PENDING) ||
                (status == HawalaStatusFilter.PAID && h.status == HawalaStatus.PAID)) &&
                (currency == "all" || h.currency == currency) &&
                (q.isEmpty() ||
                    h.pickupCode.contains(query.trim()) ||
                    h.senderName.lowercase().contains(q) ||
                    h.receiverName.lowercase().contains(q) ||
                    row.partner.name.lowercase().contains(q))
        }

        val now = timeProvider.nowMillis()
        val (today, rest) = filtered.partition { now - it.hawala.timestampMillis < DAY_MS }
        val (yesterday, earlier) = rest.partition { now - it.hawala.timestampMillis < 2 * DAY_MS }

        HawalasUiState(
            totalCount = all.size,
            pendingCount = all.count { it.hawala.status == HawalaStatus.PENDING },
            paidCount = all.count { it.hawala.status == HawalaStatus.PAID },
            totalVolumeUsd = totalUsd,
            partnerCount = all.map { it.partner.id }.distinct().size,
            statusFilter = status,
            currencyFilter = currency,
            search = query,
            today = today,
            yesterday = yesterday,
            earlier = earlier,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HawalasUiState())

    fun setStatusFilter(value: HawalaStatusFilter) { statusFilter.value = value }
    fun setCurrencyFilter(value: String) { currencyFilter.value = value }
    fun setSearch(value: String) { search.value = value }

    private companion object {
        const val DAY_MS = 86_400_000L
    }
}

/** Hawala daftar: volume stats, filters, and the grouped transfer list. */
@Composable
fun HawalasScreen(
    navController: NavController,
    viewModel: HawalasViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier.statusBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 120.dp),
        ) {
            item {
                Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp)) {
                    MonoLabel("Hawala daftar · د حوالې دفتر", color = DaftarColors.CopperDeep)
                    Spacer(Modifier.height(4.dp))
                    Text("All Hawalas", style = MaterialTheme.typography.headlineMedium, color = DaftarColors.Ink)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "${state.totalCount} total · ${state.pendingCount} pending · ${state.paidCount} settled",
                        style = TextStyle(fontFamily = Inter, fontSize = 12.sp, color = DaftarColors.Muted),
                    )
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(DaftarColors.Ink)
                        .padding(16.dp),
                ) {
                    MonoLabel("Total volume", color = DaftarColors.GoldSoft, fontSize = 9)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = Formatters.number(state.totalVolumeUsd),
                            style = TextStyle(
                                fontFamily = Fraunces,
                                fontWeight = FontWeight.Medium,
                                fontSize = 28.sp,
                                color = DaftarColors.Paper,
                            ),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "USD-EQ",
                            style = TextStyle(
                                fontFamily = JetBrainsMono,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 0.1.em,
                                color = DaftarColors.GoldSoft,
                            ),
                            modifier = Modifier.padding(bottom = 5.dp),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${state.pendingCount} awaiting pickup · across ${state.partnerCount} partners",
                        style = TextStyle(fontFamily = JetBrainsMono, fontSize = 10.sp, color = DaftarColors.MutedLight),
                    )
                }
            }

            item {
                DaftarSearchField(state.search, viewModel::setSearch, "Search by code, name, or saraf…")
                Spacer(Modifier.height(10.dp))
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    DaftarFilterChip("All · ${state.totalCount}", state.statusFilter == HawalaStatusFilter.ALL, { viewModel.setStatusFilter(HawalaStatusFilter.ALL) })
                    DaftarFilterChip("Pending · ${state.pendingCount}", state.statusFilter == HawalaStatusFilter.PENDING, { viewModel.setStatusFilter(HawalaStatusFilter.PENDING) })
                    DaftarFilterChip("Settled · ${state.paidCount}", state.statusFilter == HawalaStatusFilter.PAID, { viewModel.setStatusFilter(HawalaStatusFilter.PAID) })
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf("all" to "All currencies", "USD" to "USD", "AFN" to "AFN", "PKR" to "PKR")
                        .forEach { (id, label) ->
                            DaftarFilterChip(
                                label = label,
                                selected = state.currencyFilter == id,
                                onClick = { viewModel.setCurrencyFilter(id) },
                                selectedColor = if (id == "all") DaftarColors.Ink else DaftarColors.Copper,
                            )
                        }
                }
                Spacer(Modifier.height(6.dp))
            }

            if (state.isEmpty) {
                item { EmptyNote("No hawalas match your filters") }
            } else {
                listOf(
                    "Today" to state.today,
                    "Yesterday" to state.yesterday,
                    "Earlier" to state.earlier,
                ).forEach { (title, rows) ->
                    if (rows.isNotEmpty()) {
                        item(key = "hgroup_$title") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp)
                                    .padding(top = 14.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                MonoLabel(title)
                                HorizontalDivider(Modifier.weight(1f), color = DaftarColors.Line)
                                MonoLabel(rows.size.toString(), color = DaftarColors.CopperDeep)
                            }
                        }
                        items(count = rows.size, key = { i -> rows[i].hawala.id }) { i ->
                            HawalaRow(rows[i]) {
                                navController.navigate(DaftarDestinations.hawalaDetail(rows[i].hawala.id))
                            }
                        }
                    }
                }
            }
        }

        // FAB — start a new hawala from here too
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 20.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(DaftarColors.Copper)
                .clickable { navController.navigate(DaftarDestinations.newHawala()) }
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.Send,
                contentDescription = null,
                tint = DaftarColors.Paper,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = "NEW HAWALA",
                style = TextStyle(
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 0.05.em,
                    color = DaftarColors.Paper,
                ),
            )
        }
    }
}

@Composable
private fun HawalaRow(row: HawalaRowUi, onClick: () -> Unit) {
    val h = row.hawala
    val send = h.type == HawalaType.SEND
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 5.dp)
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(12.dp))
            .background(DaftarColors.PaperSoft)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(if (send) DaftarColors.Red else DaftarColors.Green),
        )
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${h.fromCity.code} ${if (send) "→" else "←"} ${h.toCity.code}",
                    style = TextStyle(
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 0.15.em,
                        color = DaftarColors.Muted,
                    ),
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = Formatters.number(h.amount),
                        style = TextStyle(
                            fontFamily = Fraunces,
                            fontWeight = FontWeight.Medium,
                            fontSize = 17.sp,
                            color = DaftarColors.Ink,
                        ),
                    )
                    Text(
                        text = " ${h.currency}",
                        style = TextStyle(
                            fontFamily = JetBrainsMono,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.sp,
                            color = DaftarColors.Muted,
                        ),
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${h.senderName} → ${h.receiverName} · ${row.partner.shortName}",
                style = TextStyle(fontFamily = Inter, fontSize = 12.sp, color = DaftarColors.InkSoft),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (h.status == HawalaStatus.PENDING) {
                        Icon(
                            imageVector = Icons.Rounded.Key,
                            contentDescription = null,
                            tint = DaftarColors.CopperDeep,
                            modifier = Modifier.size(10.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = if (h.status == HawalaStatus.PENDING) h.pickupCode else "code · ${h.pickupCode}",
                        style = TextStyle(
                            fontFamily = JetBrainsMono,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            letterSpacing = if (h.status == HawalaStatus.PENDING) 0.2.em else 0.05.em,
                            color = if (h.status == HawalaStatus.PENDING) DaftarColors.Ink else DaftarColors.Muted,
                        ),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = h.dateLabel,
                        style = TextStyle(fontFamily = JetBrainsMono, fontSize = 9.sp, color = DaftarColors.MutedLight),
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (h.status == HawalaStatus.PENDING) DaftarColors.Copper.copy(alpha = 0.14f)
                                else DaftarColors.Green.copy(alpha = 0.12f),
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = if (h.status == HawalaStatus.PENDING) "PENDING" else "PAID",
                            style = TextStyle(
                                fontFamily = JetBrainsMono,
                                fontWeight = FontWeight.Bold,
                                fontSize = 8.sp,
                                letterSpacing = 0.1.em,
                                color = if (h.status == HawalaStatus.PENDING) DaftarColors.CopperDeep else DaftarColors.Green,
                            ),
                        )
                    }
                }
            }
        }
    }
}

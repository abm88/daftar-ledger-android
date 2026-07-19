package com.daftar.app.ui.feature.branches

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.daftar.app.domain.model.City
import com.daftar.app.domain.model.Counterparty
import com.daftar.app.domain.model.MoneyByCurrency
import com.daftar.app.domain.model.PartnerTier
import com.daftar.app.domain.repository.PartnerRepository
import com.daftar.app.domain.repository.LedgerRefreshRepository
import com.daftar.app.domain.usecase.AddPartnerUseCase
import com.daftar.app.domain.usecase.NewPartnerDraft
import com.daftar.app.domain.usecase.PositionCalculator
import com.daftar.app.ui.common.DaftarSearchField
import com.daftar.app.ui.common.MonoLabel
import com.daftar.app.ui.common.PartnerBadge
import com.daftar.app.ui.common.SyncIconButton
import com.daftar.app.ui.common.ToastCenter
import com.daftar.app.ui.common.ToastIcon
import com.daftar.app.ui.common.dashedBorder
import com.daftar.app.ui.components.DarkBalanceGrid
import com.daftar.app.ui.components.EmptyState
import com.daftar.app.ui.components.EmptyStateTone
import com.daftar.app.ui.components.PositionLines
import com.daftar.app.ui.feature.accounts.AddPartnerSheet
import com.daftar.app.ui.feature.accounts.EmptyNote
import com.daftar.app.ui.feature.accounts.ListHeader
import com.daftar.app.ui.feature.accounts.PartyListRow
import com.daftar.app.ui.navigation.DaftarDestinations
import com.daftar.app.ui.theme.DaftarColors
import com.daftar.app.ui.theme.Inter
import com.daftar.app.ui.theme.NotoNaskhArabic
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BranchRowUi(val partner: Counterparty, val position: MoneyByCurrency)

data class BranchesUiState(
    val search: String = "",
    val rows: List<BranchRowUi> = emptyList(),
    val total: Int = 0,
    val globalPosition: MoneyByCurrency = MoneyByCurrency(),
    val syncing: Boolean = false,
)

@HiltViewModel
class BranchesViewModel @Inject constructor(
    partnerRepository: PartnerRepository,
    private val positionCalculator: PositionCalculator,
    private val addPartnerUseCase: AddPartnerUseCase,
    private val ledgerRefresh: LedgerRefreshRepository,
    private val toastCenter: ToastCenter,
) : ViewModel() {

    private val search = MutableStateFlow("")
    private val syncing = MutableStateFlow(false)

    val uiState = combine(partnerRepository.partners, search, syncing) { partners, query, sync ->
        val q = query.trim().lowercase()
        BranchesUiState(
            search = query,
            rows = partners
                .map { BranchRowUi(it, positionCalculator.partnerPosition(it)) }
                .filter { row ->
                    q.isEmpty() || row.partner.name.lowercase().contains(q) ||
                        row.partner.phone.contains(query.trim())
                },
            total = partners.size,
            globalPosition = positionCalculator.globalPosition(partners),
            syncing = sync,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BranchesUiState())

    fun setSearch(value: String) { search.value = value }

    fun sync() {
        if (syncing.value) return
        viewModelScope.launch {
            syncing.value = true
            val result = runCatching { ledgerRefresh.refresh() }
            syncing.value = false
            toastCenter.show(
                if (result.isSuccess) "Ledger synced" else result.exceptionOrNull()?.message ?: "Unable to sync ledger",
                if (result.isSuccess) ToastIcon.REFRESH else ToastIcon.CROSS,
            )
        }
    }

    fun addPartner(
        name: String, shortName: String, initial: String, phone: String,
        city: City, tier: PartnerTier, openings: Map<String, Double>,
        onDone: () -> Unit,
    ) {
        viewModelScope.launch {
            val partner = addPartnerUseCase(
                NewPartnerDraft(name, shortName, initial, phone, city, tier, openings),
            )
            if (partner != null) {
                toastCenter.show("${partner.name} added", ToastIcon.PERSON_ADD)
                onDone()
            }
        }
    }
}

/**
 * Branches — the partner-saraf directory. v18 moved this out of the Accounts
 * tab into its own screen opened from Daftar → Branches, with a back arrow,
 * net-position card, exposure list, and an add-branch flow.
 */
@Composable
fun BranchesScreen(
    navController: NavController,
    viewModel: BranchesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var addPartnerOpen by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.statusBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 40.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(DaftarColors.PaperDeep)
                            .clickable { navController.popBackStack() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = DaftarColors.InkSoft,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = "د صرافانو څانګې",
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
                                MonoLabel("PARTNER SARAFS", color = DaftarColors.Paper, fontSize = 9, letterSpacing = 0.1)
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "Branches", style = MaterialTheme.typography.headlineMedium, color = DaftarColors.Ink)
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "${state.total} trading relationships",
                            style = TextStyle(fontFamily = Inter, fontSize = 12.sp, color = DaftarColors.Muted),
                        )
                    }
                }
                SyncIconButton(syncing = state.syncing, onClick = viewModel::sync)
            }
        }

        item { NetPositionCard(state.globalPosition) }

        if (state.total == 0) {
            // v18 first-run state — a branch is the prerequisite for hawalas.
            item {
                EmptyState(
                    icon = Icons.Rounded.Group,
                    title = "No branches yet",
                    pashto = "تر اوسه هیڅ څانګه نشته",
                    sub = "Add a partner saraf (branch) to send hawalas, settle balances, and track your net position with them.",
                    tone = EmptyStateTone.COPPER,
                    ctaLabel = "Add branch · نوې څانګه",
                    ctaIcon = Icons.Rounded.PersonAdd,
                    onCta = { addPartnerOpen = true },
                )
            }
        } else {
            item {
                DaftarSearchField(state.search, viewModel::setSearch, "Search saraf or phone…")
                Spacer(modifier = Modifier.height(8.dp))
            }
            item { ListHeader("Showing ${state.rows.size} of ${state.total}", "Exposure") }
            if (state.rows.isEmpty()) {
                item { EmptyNote("No branches match your search") }
            } else {
                items(count = state.rows.size, key = { i -> state.rows[i].partner.id }) { i ->
                    val row = state.rows[i]
                    PartyListRow(
                        badge = { PartnerBadge(row.partner) },
                        name = row.partner.name,
                        sub = "${row.partner.city.displayName} · ${row.partner.hawalas.size} hawalas · " +
                            row.partner.tier.label.replace("-", "").uppercase(),
                        onClick = { navController.navigate(DaftarDestinations.partnerDetail(row.partner.id)) },
                    ) {
                        PositionLines(row.position)
                    }
                }
            }
            // v18 shows the dashed add row only when at least one branch exists;
            // the rich empty state's CTA covers the zero case.
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                        .dashedBorder(DaftarColors.LineDashed, 1.5.dp, 14.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { addPartnerOpen = true }
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PersonAdd,
                        contentDescription = null,
                        tint = DaftarColors.InkSoft,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = "Add branch · نوې څانګه",
                        style = TextStyle(
                            fontFamily = Inter,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = DaftarColors.InkSoft,
                        ),
                    )
                }
            }
        }
    }

    if (addPartnerOpen) {
        AddPartnerSheet(
            onDismiss = { addPartnerOpen = false },
            onSave = { name, shortName, initial, phone, city, tier, openings ->
                viewModel.addPartner(name, shortName, initial, phone, city, tier, openings) {
                    addPartnerOpen = false
                }
            },
        )
    }
}

@Composable
private fun NetPositionCard(position: MoneyByCurrency) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 14.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(DaftarColors.Ink)
            .padding(bottom = 14.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.TrendingUp,
                    contentDescription = null,
                    tint = DaftarColors.GoldSoft,
                    modifier = Modifier.size(11.dp),
                )
                MonoLabel("Net Position · تجارتي حالت", color = DaftarColors.GoldSoft)
            }
            Spacer(modifier = Modifier.height(3.dp))
            MonoLabel("Across all branches", color = DaftarColors.MutedLight, fontSize = 9, letterSpacing = 0.1)
        }
        Spacer(modifier = Modifier.height(14.dp))
        // v18 posValueHtml signs on any non-zero amount (no ±0.5 dead zone).
        DarkBalanceGrid(
            position = position,
            statusFor = { amt -> if (amt > 0) "LONG" else if (amt < 0) "SHORT" else "FLAT" },
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

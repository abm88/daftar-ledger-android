package com.daftar.app.ui.feature.team

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.daftar.app.core.format.Formatters
import com.daftar.app.core.time.TimeProvider
import com.daftar.app.domain.model.AssetCatalog
import com.daftar.app.domain.model.Expense
import com.daftar.app.domain.model.TeamMember
import com.daftar.app.domain.repository.RatesRepository
import com.daftar.app.domain.repository.LedgerMutationRepository
import com.daftar.app.domain.repository.SettingsRepository
import com.daftar.app.domain.repository.TeamRepository
import com.daftar.app.domain.usecase.CurrencyConverter
import com.daftar.app.ui.common.FieldBox
import com.daftar.app.ui.common.FieldTextInput
import com.daftar.app.ui.common.IconSquareButton
import com.daftar.app.ui.common.MonoLabel
import com.daftar.app.ui.common.SheetHandle
import com.daftar.app.ui.common.SubmitButton
import com.daftar.app.ui.common.ToastCenter
import com.daftar.app.ui.common.ToastIcon
import com.daftar.app.ui.common.dashedBorder
import com.daftar.app.ui.components.EmptyState
import com.daftar.app.ui.components.EmptyStateTone
import com.daftar.app.ui.navigation.DaftarDestinations
import com.daftar.app.ui.theme.DaftarColors
import com.daftar.app.ui.theme.Fraunces
import com.daftar.app.ui.theme.Inter
import com.daftar.app.ui.theme.JetBrainsMono
import com.daftar.app.ui.theme.NotoNaskhArabic
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One member with their expenses and the reporting-currency total booked against them. */
data class TeamMemberRow(
    val member: TeamMember,
    val expenses: List<Expense>,
    val totalReporting: Double,
) {
    val expenseCount: Int get() = expenses.size
}

data class TeamUiState(
    val rows: List<TeamMemberRow> = emptyList(),
    val reportingCurrency: String = "AFN",
    val reportingSymbol: String = "؋",
    val reportingDecimals: Int = 0,
) {
    fun rowFor(memberId: String): TeamMemberRow? = rows.firstOrNull { it.member.id == memberId }
}

@HiltViewModel
class TeamViewModel @Inject constructor(
    private val teamRepository: TeamRepository,
    private val mutations: LedgerMutationRepository,
    ratesRepository: RatesRepository,
    settingsRepository: SettingsRepository,
    private val converter: CurrencyConverter,
    private val timeProvider: TimeProvider,
    private val toastCenter: ToastCenter,
) : ViewModel() {

    val uiState = combine(
        teamRepository.members,
        teamRepository.expenses,
        ratesRepository.rateBook,
        settingsRepository.settings,
    ) { members, expenses, rates, settings ->
        val rep = settings.reportingCurrency
        val rows = members.map { member ->
            val theirs = expenses
                .filter { it.teamMemberId == member.id }
                .sortedByDescending { it.timestampMillis }
            val total = theirs.sumOf { converter.toReporting(it.currency, it.amount, rates, settings) }
            TeamMemberRow(member, theirs, total)
        }
        TeamUiState(
            rows = rows,
            reportingCurrency = rep,
            reportingSymbol = AssetCatalog.symbolFor(rep),
            reportingDecimals = AssetCatalog.decimalsFor(rep),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TeamUiState())

    fun addMember(name: String, role: String, phone: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val now = timeProvider.nowMillis()
            val result = runCatching {
                mutations.createTeamMember(
                    TeamMember(
                        id = "tm_$now",
                        name = trimmed,
                        role = role,
                        phone = phone.trim().ifEmpty { null },
                    ),
                )
            }
            if (result.isSuccess) toastCenter.show("Member added · $trimmed", ToastIcon.PERSON_ADD)
            else toastCenter.show(result.exceptionOrNull()?.message ?: "Unable to add member", ToastIcon.CROSS)
        }
    }
}

/** Team members list (Daftar → Team · ټیم): expenses tracked per person. */
@Composable
fun TeamScreen(
    navController: NavController,
    viewModel: TeamViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var addOpen by rememberSaveableFalse()

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 120.dp),
    ) {
        item {
            TeamHeader(
                title = "Team",
                pashto = "ټیم",
                badge = "TEAM MEMBERS",
                sub = "${state.rows.size} members · expenses per person",
                onBack = { navController.popBackStack() },
            )
        }

        if (state.rows.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Rounded.PersonAdd,
                    title = "No team members yet",
                    pashto = "تر اوسه هیڅ غړی نشته",
                    sub = "Add partners or staff to record and track expenses against each person.",
                    tone = EmptyStateTone.COPPER,
                    ctaLabel = "Add member · نوی غړی",
                    ctaIcon = Icons.Rounded.PersonAdd,
                    onCta = { addOpen = true },
                )
            }
        } else {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    MonoLabel("${state.rows.size} members")
                    MonoLabel("Expenses · ${state.reportingCurrency}")
                }
            }
            items(state.rows, key = { it.member.id }) { row ->
                TeamMemberListRow(
                    row = row,
                    reportingDecimals = state.reportingDecimals,
                    onClick = { navController.navigate(DaftarDestinations.teamMemberDetail(row.member.id)) },
                )
            }
            item {
                AddDashedButton("Add member · نوی غړی") { addOpen = true }
            }
        }
    }

    if (addOpen) {
        AddTeamMemberSheet(
            onDismiss = { addOpen = false },
            onSave = { name, role, phone ->
                viewModel.addMember(name, role, phone)
                addOpen = false
            },
        )
    }
}

/** Member detail: total-expenses card + their expense list; Record expense pre-selects them. */
@Composable
fun TeamMemberDetailScreen(
    navController: NavController,
    memberId: String,
    viewModel: TeamViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val row = state.rowFor(memberId)

    if (row == null) {
        // Member was removed/not found — return to the list.
        Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
            TeamHeader("Team", "ټیم", "TEAM MEMBERS", "", onBack = { navController.popBackStack() })
        }
        return
    }
    val member = row.member

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 120.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconSquareButton(Icons.AutoMirrored.Rounded.ArrowBack, { navController.popBackStack() })
                TeamAvatar(member, 42.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = member.name,
                        style = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 20.sp, color = DaftarColors.Ink),
                    )
                    Text(
                        text = member.role + (member.phone?.let { " · $it" } ?: ""),
                        style = TextStyle(fontFamily = Inter, fontSize = 12.sp, color = DaftarColors.Muted),
                    )
                }
            }
        }

        item {
            TotalExpensesCard(
                total = row.totalReporting,
                symbol = state.reportingSymbol,
                reporting = state.reportingCurrency,
                decimals = state.reportingDecimals,
                count = row.expenseCount,
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MonoLabel("Expenses · ورداشتونه")
                androidx.compose.material3.HorizontalDivider(modifier = Modifier.weight(1f), color = DaftarColors.Line)
                MonoLabel("${row.expenseCount} entries", fontSize = 9)
            }
        }

        if (row.expenses.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Rounded.ReceiptLong,
                    title = "No expenses yet",
                    sub = "Record an expense against ${member.name.split(' ').first()} and it will show here.",
                    tone = EmptyStateTone.MUTED,
                    ctaLabel = "Record expense · ورداشت",
                    ctaIcon = Icons.Rounded.ReceiptLong,
                    onCta = { navController.navigate(DaftarDestinations.newExpense(member.id)) },
                )
            }
        } else {
            items(row.expenses, key = { it.id }) { expense ->
                ExpenseRow(expense)
            }
            item {
                AddDashedButton("Record expense · ورداشت") {
                    navController.navigate(DaftarDestinations.newExpense(member.id))
                }
            }
        }
    }
}

@Composable
private fun TeamHeader(
    title: String,
    pashto: String,
    badge: String,
    sub: String,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconSquareButton(Icons.AutoMirrored.Rounded.ArrowBack, onBack)
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = pashto,
                    style = TextStyle(fontFamily = NotoNaskhArabic, fontSize = 13.sp, color = DaftarColors.Muted, textDirection = TextDirection.Rtl),
                )
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(DaftarColors.Ink).padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    MonoLabel(badge, color = DaftarColors.Paper, fontSize = 9, letterSpacing = 0.1)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(text = title, style = MaterialTheme.typography.headlineMedium, color = DaftarColors.Ink)
            if (sub.isNotEmpty()) {
                Spacer(Modifier.height(3.dp))
                Text(text = sub, style = TextStyle(fontFamily = Inter, fontSize = 12.sp, color = DaftarColors.Muted))
            }
        }
    }
}

@Composable
private fun TeamMemberListRow(row: TeamMemberRow, reportingDecimals: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TeamAvatar(row.member, 44.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.member.name,
                style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = DaftarColors.Ink),
            )
            Text(
                text = row.member.role + (row.member.phone?.let { " · $it" } ?: ""),
                style = TextStyle(fontFamily = JetBrainsMono, fontSize = 10.sp, color = DaftarColors.Muted),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = (if (row.totalReporting > 0) "−" else "") + Formatters.compact(row.totalReporting, reportingDecimals),
                style = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = if (row.totalReporting > 0) DaftarColors.Red else DaftarColors.Muted),
            )
            Text(
                text = "${row.expenseCount} " + if (row.expenseCount == 1) "expense" else "expenses",
                style = TextStyle(fontFamily = JetBrainsMono, fontSize = 9.sp, color = DaftarColors.Muted),
            )
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = DaftarColors.Muted, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun TotalExpensesCard(total: Double, symbol: String, reporting: String, decimals: Int, count: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(DaftarColors.Ink)
            .padding(16.dp),
    ) {
        MonoLabel("Total expenses · ټول لګښت", color = DaftarColors.GoldSoft, fontSize = 9)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = (if (total > 0) "−" else "") + symbol + Formatters.number(total, decimals),
                style = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 30.sp, color = DaftarColors.ShortRed),
            )
            Spacer(Modifier.size(6.dp))
            Text(
                text = reporting,
                style = TextStyle(fontFamily = JetBrainsMono, fontSize = 13.sp, color = DaftarColors.GoldSoft),
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "$count " + (if (count == 1) "expense" else "expenses") + " recorded" + if (reporting != "AFN") " · reporting equivalent" else "",
            style = TextStyle(fontFamily = JetBrainsMono, fontSize = 10.sp, color = DaftarColors.MutedLight),
        )
    }
}

@Composable
private fun ExpenseRow(expense: Expense) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(DaftarColors.Red.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.ReceiptLong, null, tint = DaftarColors.Red, modifier = Modifier.size(15.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = expense.note?.takeIf { it.isNotBlank() } ?: "Expense",
                style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = DaftarColors.Ink),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = expense.dateLabel,
                style = TextStyle(fontFamily = JetBrainsMono, fontSize = 10.sp, color = DaftarColors.Muted),
            )
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "−" + Formatters.number(expense.amount, AssetCatalog.decimalsFor(expense.currency)),
                style = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = DaftarColors.Red),
            )
            Spacer(Modifier.size(4.dp))
            Text(
                text = expense.currency,
                style = TextStyle(fontFamily = JetBrainsMono, fontSize = 9.sp, color = DaftarColors.Muted),
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
    }
}

@Composable
private fun AddDashedButton(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .dashedBorder(DaftarColors.LineDashed, 1.5.dp, 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.PersonAdd, null, tint = DaftarColors.InkSoft, modifier = Modifier.size(14.dp))
        Spacer(Modifier.size(8.dp))
        Text(
            text = label,
            style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = DaftarColors.InkSoft),
        )
    }
}

/** Colored initial tile mirroring the prototype teamAvatar() palette. */
@Composable
fun TeamAvatar(member: TeamMember, size: Dp) {
    val colors = listOf(
        Color(0xFF8B5A2B), Color(0xFF2E6B4E), Color(0xFFB3321F), Color(0xFF7B3D14), Color(0xFF5A6B7B),
    )
    val idx = (member.name.firstOrNull()?.code ?: 0) % colors.size
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3.6f))
            .background(colors[idx]),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = member.initial,
            style = TextStyle(
                fontFamily = Fraunces, fontWeight = FontWeight.Medium,
                fontSize = (size.value * 0.4f).sp, color = DaftarColors.Paper,
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTeamMemberSheet(
    onDismiss: () -> Unit,
    onSave: (name: String, role: String, phone: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("Partner") }
    var phone by remember { mutableStateOf("") }
    val roles = listOf("Partner", "Owner", "Cashier", "Runner", "Staff")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DaftarColors.Paper,
        dragHandle = { SheetHandle() },
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(
                text = "Add team member",
                style = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 18.sp, color = DaftarColors.Ink),
            )
            Text(
                text = "نوی غړی اضافه کړئ",
                style = TextStyle(fontFamily = NotoNaskhArabic, fontSize = 12.sp, color = DaftarColors.Muted, textDirection = TextDirection.Rtl),
            )
            Spacer(Modifier.height(14.dp))
            FieldBox("Name · نوم", modifier = Modifier.fillMaxWidth()) {
                FieldTextInput(name, { name = it }, "Full name")
            }
            Spacer(Modifier.height(12.dp))
            MonoLabel("Role · دنده", fontSize = 9, letterSpacing = 0.2)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                roles.forEach { r ->
                    val on = role == r
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (on) DaftarColors.Ink else DaftarColors.PaperSoft)
                            .border(
                                1.dp,
                                if (on) DaftarColors.Ink else DaftarColors.LineStrong,
                                RoundedCornerShape(20.dp),
                            )
                            .clickable { role = r }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = r,
                            style = TextStyle(
                                fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                                color = if (on) DaftarColors.Paper else DaftarColors.InkSoft,
                            ),
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            FieldBox("Phone · تلیفون (optional)", modifier = Modifier.fillMaxWidth()) {
                FieldTextInput(phone, { phone = it }, "+93 …")
            }
            Spacer(Modifier.height(18.dp))
            SubmitButton(
                label = "Add member",
                icon = Icons.Rounded.PersonAdd,
                enabled = name.trim().isNotEmpty(),
                container = DaftarColors.Ink,
                onClick = { onSave(name, role, phone) },
            )
        }
    }
}

// Small helper to keep the two list screens tidy.
@Composable
private fun rememberSaveableFalse() = rememberSaveable { mutableStateOf(false) }

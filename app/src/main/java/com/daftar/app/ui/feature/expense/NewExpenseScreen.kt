package com.daftar.app.ui.feature.expense

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
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
import com.daftar.app.domain.model.AssetCatalog
import com.daftar.app.domain.model.Expense
import com.daftar.app.domain.model.TeamMember
import com.daftar.app.domain.repository.RatesRepository
import com.daftar.app.domain.repository.SettingsRepository
import com.daftar.app.domain.repository.TeamRepository
import com.daftar.app.domain.repository.LedgerMutationRepository
import com.daftar.app.ui.common.BigAmountInput
import com.daftar.app.ui.common.FieldBox
import com.daftar.app.ui.common.FieldTextInput
import com.daftar.app.ui.common.IconSquareButton
import com.daftar.app.ui.common.MonoLabel
import com.daftar.app.ui.common.SheetHandle
import com.daftar.app.ui.common.SubmitButton
import com.daftar.app.ui.common.ToastCenter
import com.daftar.app.ui.common.ToastIcon
import com.daftar.app.ui.feature.team.AddTeamMemberSheet
import com.daftar.app.ui.feature.team.TeamAvatar
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

data class NewExpenseForm(
    val teamMemberId: String? = null,
    val currency: String = "AFN",
    val amountText: String = "",
    val note: String = "",
    val pickerOpen: Boolean = false,
    val addMemberOpen: Boolean = false,
    val amountError: Boolean = false,
)

data class NewExpenseUiState(
    val form: NewExpenseForm = NewExpenseForm(),
    val members: List<TeamMember> = emptyList(),
    val selectedMember: TeamMember? = null,
    val activeCurrencies: List<String> = listOf("AFN"),
) {
    val amount: Double get() = form.amountText.toDoubleOrNull() ?: 0.0
}

@HiltViewModel
class NewExpenseViewModel @Inject constructor(
    private val teamRepository: TeamRepository,
    private val mutations: LedgerMutationRepository,
    ratesRepository: RatesRepository,
    settingsRepository: SettingsRepository,
    private val timeProvider: TimeProvider,
    private val toastCenter: ToastCenter,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val form: MutableStateFlow<NewExpenseForm>

    init {
        val settings = settingsRepository.settings.value
        val tradeCurrency = settings.tradeCurrency
        form = MutableStateFlow(
            NewExpenseForm(
                teamMemberId = savedStateHandle.get<String>("teamMemberId"),
                currency = tradeCurrency,
            ),
        )
    }

    val uiState = combine(
        form,
        teamRepository.members,
        settingsRepository.settings,
        ratesRepository.rateBook,
    ) { form, members, settings, _ ->
        val active = settings.activeCurrencies().map { it.code }
        NewExpenseUiState(
            form = form,
            members = members,
            selectedMember = members.firstOrNull { it.id == form.teamMemberId },
            activeCurrencies = if (form.currency in active) active else active + form.currency,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NewExpenseUiState())

    fun update(transform: (NewExpenseForm) -> NewExpenseForm) { form.value = transform(form.value) }
    fun setCurrency(code: String) { form.value = form.value.copy(currency = code) }

    /** Add a member inline (from the picker) and auto-select them for this expense. */
    fun addMemberAndSelect(name: String, role: String, phone: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val now = timeProvider.nowMillis()
            val member = TeamMember(
                id = "tm_$now",
                name = trimmed,
                role = role,
                phone = phone.trim().ifEmpty { null },
            )
            val created = runCatching { mutations.createTeamMember(member) }.getOrElse {
                toastCenter.show(it.message ?: "Unable to add member", ToastIcon.CROSS)
                return@launch
            }
            toastCenter.show("Member added · $trimmed", ToastIcon.PERSON_ADD)
            form.value = form.value.copy(teamMemberId = created.id, addMemberOpen = false, pickerOpen = false)
        }
    }

    fun save(onSaved: () -> Unit) {
        val state = uiState.value
        val f = state.form
        // v20: an expense must be booked against a team member.
        if (f.teamMemberId == null) {
            toastCenter.show("Choose a team member", ToastIcon.CROSS)
            update { it.copy(pickerOpen = true) }
            return
        }
        if (state.amount <= 0) {
            update { it.copy(amountError = true) }
            toastCenter.show("Enter an amount", ToastIcon.CROSS)
            return
        }
        viewModelScope.launch {
            val now = timeProvider.nowMillis()
            val expense = Expense(
                id = "exp_$now",
                amount = state.amount,
                currency = f.currency,
                teamMemberId = f.teamMemberId,
                note = f.note.trim().ifEmpty { null },
                timestampMillis = now,
                dateLabel = "Today",
            )
            val saved = runCatching { mutations.createExpense(expense) }
            if (saved.isFailure) {
                toastCenter.show(saved.exceptionOrNull()?.message ?: "Unable to record expense", ToastIcon.CROSS)
                return@launch
            }
            val member = teamRepository.memberById(f.teamMemberId)
            toastCenter.show(
                "Expense · ${member?.name ?: ""} · ${Formatters.number(state.amount, AssetCatalog.decimalsFor(f.currency))} ${f.currency}",
                ToastIcon.CHECK,
            )
            onSaved()
        }
    }
}

/** Expense entry: member picker → currency pills → amount → note → Record Expense. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewExpenseScreen(
    navController: NavController,
    viewModel: NewExpenseViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val form = state.form

    Column(modifier = Modifier.fillMaxWidth()) {
        // Red header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DaftarColors.Red)
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.ReceiptLong, null, tint = DaftarColors.Paper, modifier = Modifier.size(18.dp))
                }
                Column {
                    Text(
                        text = "Expense · ورداشت",
                        style = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 18.sp, color = DaftarColors.Paper),
                    )
                    Text(
                        text = "د لګښت ثبتول",
                        style = TextStyle(fontFamily = NotoNaskhArabic, fontSize = 12.sp, color = DaftarColors.Paper.copy(alpha = 0.85f), textDirection = TextDirection.Rtl),
                    )
                }
            }
            IconSquareButton(Icons.Rounded.Close, { navController.popBackStack() }, onDark = true)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            // Team member — required
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DaftarColors.PaperSoft)
                    .border(1.dp, DaftarColors.LineStrong, RoundedCornerShape(12.dp))
                    .clickable { viewModel.update { it.copy(pickerOpen = true) } }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val member = state.selectedMember
                if (member != null) {
                    TeamAvatar(member, 38.dp)
                } else {
                    Icon(Icons.Rounded.Person, null, tint = DaftarColors.InkSoft, modifier = Modifier.size(18.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    MonoLabel("Team member · ټیم غړی", fontSize = 9)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = member?.name ?: "Choose member",
                        style = TextStyle(
                            fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                            color = if (member != null) DaftarColors.Ink else DaftarColors.Muted,
                        ),
                    )
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = DaftarColors.Muted, modifier = Modifier.size(16.dp))
            }

            Spacer(Modifier.height(14.dp))
            // Currency pills (enabled currencies)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                state.activeCurrencies.forEach { code ->
                    val on = form.currency == code
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (on) DaftarColors.Ink else DaftarColors.PaperSoft)
                            .border(1.dp, if (on) DaftarColors.Ink else DaftarColors.LineStrong, RoundedCornerShape(20.dp))
                            .clickable { viewModel.setCurrency(code) }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Text(
                            text = AssetCatalog.symbolFor(code),
                            style = TextStyle(fontFamily = Inter, fontSize = 11.sp, color = if (on) DaftarColors.GoldSoft else DaftarColors.Muted),
                        )
                        Text(
                            text = code,
                            style = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.05.em, color = if (on) DaftarColors.Paper else DaftarColors.InkSoft),
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            BigAmountInput(
                value = form.amountText,
                onValueChange = { text -> viewModel.update { it.copy(amountText = text, amountError = false) } },
                currency = form.currency,
                label = "Amount · type amount",
                error = form.amountError,
            )

            Spacer(Modifier.height(12.dp))
            FieldBox("Note · description", modifier = Modifier.fillMaxWidth()) {
                FieldTextInput(form.note, { text -> viewModel.update { it.copy(note = text) } }, "e.g. Shop rent, tea, fuel")
            }

            Spacer(Modifier.height(20.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(DaftarColors.Paper)
                .padding(horizontal = 18.dp, vertical = 12.dp)
                .navigationBarsPadding(),
        ) {
            SubmitButton(
                label = "Record Expense · ثبت کړه",
                icon = Icons.Rounded.Check,
                container = DaftarColors.Red,
                onClick = { viewModel.save { navController.popBackStack() } },
            )
        }
    }

    if (form.pickerOpen) {
        ExpenseMemberPickerSheet(
            members = state.members,
            selectedId = form.teamMemberId,
            onDismiss = { viewModel.update { it.copy(pickerOpen = false) } },
            onPick = { id -> viewModel.update { it.copy(teamMemberId = id, pickerOpen = false) } },
            onAddMember = { viewModel.update { it.copy(addMemberOpen = true) } },
        )
    }

    if (form.addMemberOpen) {
        AddTeamMemberSheet(
            onDismiss = { viewModel.update { it.copy(addMemberOpen = false) } },
            onSave = { name, role, phone -> viewModel.addMemberAndSelect(name, role, phone) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseMemberPickerSheet(
    members: List<TeamMember>,
    selectedId: String?,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    onAddMember: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DaftarColors.Paper,
        dragHandle = { SheetHandle() },
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            if (members.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(DaftarColors.Copper.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.PersonAdd, null, tint = DaftarColors.Copper, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "No team members yet",
                        style = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 17.sp, color = DaftarColors.Ink),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Add a team member (partner or staff) to record this expense against them.",
                        style = TextStyle(fontFamily = Inter, fontSize = 12.sp, color = DaftarColors.Muted),
                    )
                    Spacer(Modifier.height(16.dp))
                    SubmitButton(label = "Add member · نوی غړی", icon = Icons.Rounded.PersonAdd, container = DaftarColors.Ink, onClick = onAddMember)
                }
                return@Column
            }
            Text(
                text = "Choose team member",
                style = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 18.sp, color = DaftarColors.Ink),
            )
            Spacer(Modifier.height(12.dp))
            members.forEach { m ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onPick(m.id) }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TeamAvatar(m, 40.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(m.name, style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = DaftarColors.Ink))
                        Text(
                            m.role + (m.phone?.let { " · $it" } ?: ""),
                            style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp, color = DaftarColors.Muted),
                        )
                    }
                    if (selectedId == m.id) {
                        Icon(Icons.Rounded.Check, null, tint = DaftarColors.Copper, modifier = Modifier.size(16.dp))
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onAddMember)
                    .padding(vertical = 12.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(DaftarColors.PaperDeep),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.PersonAdd, null, tint = DaftarColors.InkSoft, modifier = Modifier.size(16.dp))
                }
                Text(
                    "Add new member · نوی غړی",
                    style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = DaftarColors.InkSoft),
                )
            }
        }
    }
}

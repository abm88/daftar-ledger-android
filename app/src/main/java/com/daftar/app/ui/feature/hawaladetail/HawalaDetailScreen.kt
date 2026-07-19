package com.daftar.app.ui.feature.hawaladetail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
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
import com.daftar.app.domain.model.CommissionMode
import com.daftar.app.domain.model.Counterparty
import com.daftar.app.domain.model.Hawala
import com.daftar.app.domain.model.HawalaStatus
import com.daftar.app.domain.model.HawalaType
import com.daftar.app.domain.repository.PartnerRepository
import com.daftar.app.domain.usecase.MarkHawalaPaidUseCase
import com.daftar.app.ui.common.IconSquareButton
import com.daftar.app.ui.common.LocalToaster
import com.daftar.app.ui.common.MonoLabel
import com.daftar.app.ui.common.ToastCenter
import com.daftar.app.ui.common.ToastIcon
import com.daftar.app.ui.common.dashedBorder
import com.daftar.app.ui.components.DetailCard
import com.daftar.app.ui.components.DetailRow
import com.daftar.app.ui.components.DetailSectionTitle
import com.daftar.app.ui.common.PartnerBadge
import com.daftar.app.ui.feature.main.OPEN_TAB_HAWALAS
import com.daftar.app.ui.feature.main.OPEN_TAB_KEY
import com.daftar.app.ui.navigation.DaftarDestinations
import com.daftar.app.ui.theme.DaftarColors
import com.daftar.app.ui.theme.Fraunces
import com.daftar.app.ui.theme.JetBrainsMono
import com.daftar.app.ui.theme.NotoNaskhArabic
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class PayoutMethod { CASH, ACCOUNT }

data class PayoutState(
    val method: PayoutMethod = PayoutMethod.CASH,
    val customerId: String? = null,
    val pickerOpen: Boolean = false,
)

data class HawalaDetailUiState(
    val hawala: Hawala? = null,
    val partner: Counterparty? = null,
    val customers: List<com.daftar.app.domain.model.Customer> = emptyList(),
    val cancelConfirmOpen: Boolean = false,
    val payout: PayoutState? = null,
)

private data class HawalaDialogState(
    val cancelConfirmOpen: Boolean = false,
    val payout: PayoutState? = null,
)

@HiltViewModel
class HawalaDetailViewModel @Inject constructor(
    partnerRepository: PartnerRepository,
    customerRepository: com.daftar.app.domain.repository.CustomerRepository,
    private val markHawalaPaid: MarkHawalaPaidUseCase,
    private val cancelHawala: com.daftar.app.domain.usecase.CancelHawalaUseCase,
    private val payOutHawala: com.daftar.app.domain.usecase.PayOutHawalaUseCase,
    private val toastCenter: ToastCenter,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val hawalaId: String = checkNotNull(savedStateHandle["hawalaId"])
    private val dialog = kotlinx.coroutines.flow.MutableStateFlow(HawalaDialogState())

    val uiState = kotlinx.coroutines.flow.combine(
        partnerRepository.partners,
        customerRepository.customers,
        dialog,
    ) { partners, customers, dialog ->
        val found = partners.firstNotNullOfOrNull { partner ->
            partner.hawalas.firstOrNull { it.id == hawalaId }?.let { it to partner }
        }
        HawalaDetailUiState(
            hawala = found?.first,
            partner = found?.second,
            customers = customers,
            cancelConfirmOpen = dialog.cancelConfirmOpen,
            payout = dialog.payout,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HawalaDetailUiState())

    fun markPaid() {
        viewModelScope.launch {
            markHawalaPaid(hawalaId)
            toastCenter.show("Hawala marked paid out", ToastIcon.CHECK)
        }
    }

    // ---- Cancel Hawala ----
    fun openCancel() { dialog.value = dialog.value.copy(cancelConfirmOpen = true) }
    fun dismissCancel() { dialog.value = dialog.value.copy(cancelConfirmOpen = false) }
    fun confirmCancel(onCancelled: () -> Unit) {
        viewModelScope.launch {
            cancelHawala(hawalaId)
            dialog.value = dialog.value.copy(cancelConfirmOpen = false)
            toastCenter.show("Hawala cancelled", ToastIcon.CROSS)
            onCancelled()
        }
    }

    // ---- Pay Out (received hawala) ----
    fun openPayout() { dialog.value = dialog.value.copy(payout = PayoutState()) }
    fun dismissPayout() { dialog.value = dialog.value.copy(payout = null) }
    fun setPayoutMethod(method: PayoutMethod) {
        val p = dialog.value.payout ?: return
        dialog.value = dialog.value.copy(
            payout = p.copy(method = method, customerId = if (method == PayoutMethod.CASH) null else p.customerId),
        )
    }
    fun openPayoutPicker() {
        val p = dialog.value.payout ?: return
        dialog.value = dialog.value.copy(payout = p.copy(pickerOpen = true))
    }
    fun closePayoutPicker() {
        val p = dialog.value.payout ?: return
        dialog.value = dialog.value.copy(payout = p.copy(pickerOpen = false))
    }
    fun pickPayoutCustomer(customerId: String) {
        val p = dialog.value.payout ?: return
        dialog.value = dialog.value.copy(payout = p.copy(customerId = customerId, pickerOpen = false))
    }
    fun confirmPayout(onDone: () -> Unit) {
        val p = dialog.value.payout ?: return
        if (p.method == PayoutMethod.ACCOUNT && p.customerId == null) {
            toastCenter.show("Choose an account to credit", ToastIcon.CROSS)
            return
        }
        viewModelScope.launch {
            payOutHawala(hawalaId, if (p.method == PayoutMethod.ACCOUNT) p.customerId else null)
            dialog.value = dialog.value.copy(payout = null)
            toastCenter.show("Hawala paid out", ToastIcon.CHECK)
            onDone()
        }
    }
}

/** Full hawala record: route, pickup code, parties, financials, timeline. */
@Composable
fun HawalaDetailScreen(
    navController: NavController,
    viewModel: HawalaDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val toaster = LocalToaster.current
    val clipboard = LocalClipboardManager.current

    // v18 back-from-hawala always lands on the Hawalas tab, wherever the
    // detail was opened from (home feed, partner detail, post-issue).
    val backToHawalas: () -> Unit = {
        runCatching {
            navController.getBackStackEntry(DaftarDestinations.MAIN)
                .savedStateHandle[OPEN_TAB_KEY] = OPEN_TAB_HAWALAS
        }
        navController.popBackStack(DaftarDestinations.MAIN, inclusive = false)
    }

    val hawala = state.hawala
    val partner = state.partner
    if (hawala == null || partner == null) {
        // Stale or unknown id — v18 falls back to the home screen rather
        // than showing a blank page.
        LaunchedEffect(Unit) { navController.popBackStack(DaftarDestinations.MAIN, inclusive = false) }
        return
    }

    val pending = hawala.status == HawalaStatus.PENDING
    val send = hawala.type == HawalaType.SEND
    // v20 direction-aware status: receive → payout wording, send → pickup wording.
    val statusLabel = when {
        pending -> if (send) "PENDING PICKUP" else "PENDING PAYOUT"
        else -> if (send) "SETTLED" else "PAID OUT"
    }
    val commissionAmount = hawala.resolvedCommissionAmount
    val commissionLabel = if (hawala.commissionMode == CommissionMode.FIXED) "Fixed"
    else Formatters.rate(hawala.commissionPercent, 1) + "%"

    Column(modifier = Modifier.fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 20.dp),
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        // v18 keeps the ink header in both states; only the status
                        // chip and glow shift to green once paid.
                        .background(DaftarColors.Ink)
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp)
                        .padding(top = 8.dp, bottom = 20.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconSquareButton(Icons.AutoMirrored.Rounded.ArrowBack, backToHawalas, onDark = true)
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (pending) DaftarColors.Copper else DaftarColors.Green,
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = if (pending) Icons.Rounded.Schedule else Icons.Rounded.Check,
                                contentDescription = null,
                                tint = DaftarColors.Paper,
                                modifier = Modifier.size(10.dp),
                            )
                            Text(
                                text = statusLabel,
                                style = TextStyle(
                                    fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp, letterSpacing = 0.1.em, color = DaftarColors.Paper,
                                ),
                            )
                        }
                    }
                    // v20 removed the From/To city route from the header — the amount block stays.
                    Spacer(Modifier.height(20.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        MonoLabel(
                            if (hawala.type == HawalaType.SEND) "Amount sent" else "Amount received",
                            color = DaftarColors.GoldSoft, fontSize = 9, letterSpacing = 0.25,
                        )
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                text = Formatters.number(hawala.amount),
                                style = TextStyle(
                                    fontFamily = Fraunces,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 38.sp,
                                    letterSpacing = (-0.02).em,
                                    color = DaftarColors.Paper,
                                ),
                            )
                            Text(
                                text = " ${hawala.currency}",
                                style = TextStyle(
                                    fontFamily = JetBrainsMono,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    letterSpacing = 0.1.em,
                                    color = DaftarColors.GoldSoft,
                                ),
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        }
                    }
                }
            }

            item {
                // Pickup code block
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 16.dp)
                        .dashedBorder(DaftarColors.Copper, 2.dp, 14.dp)
                        .background(Color.White, RoundedCornerShape(14.dp))
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    MonoLabel("Pickup Code", color = DaftarColors.CopperDeep, fontSize = 9, letterSpacing = 0.3)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "د پیسو کوډ",
                        style = TextStyle(
                            fontFamily = NotoNaskhArabic,
                            fontSize = 12.sp,
                            color = DaftarColors.Muted,
                            textDirection = TextDirection.Rtl,
                        ),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = hawala.pickupCode.toCharArray().joinToString(" "),
                        style = TextStyle(
                            fontFamily = JetBrainsMono,
                            fontWeight = FontWeight.Bold,
                            fontSize = 34.sp,
                            color = DaftarColors.Ink,
                        ),
                    )
                    Spacer(Modifier.height(6.dp))
                    MonoLabel("Recipient presents this code with ID", fontSize = 9, letterSpacing = 0.1)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CodeAction("Copy", Icons.Rounded.Tag, Modifier.weight(1f)) {
                            clipboard.setText(AnnotatedString(hawala.pickupCode))
                            toaster("Code ${hawala.pickupCode} copied", ToastIcon.HASH)
                        }
                        CodeAction("WhatsApp", Icons.Rounded.Share, Modifier.weight(1f)) {
                            toaster("Code sent via WhatsApp", ToastIcon.SEND)
                        }
                    }
                }
            }

            item {
                DetailSectionTitle("Parties")
                DetailCard {
                    DetailRow(
                        label = "Sender · لیږونکی",
                        value = hawala.senderName,
                        sub = "${hawala.fromCity.displayName} · ${hawala.fromCity.code}",
                        icon = Icons.Rounded.ArrowUpward,
                        iconTint = DaftarColors.Red,
                        iconBackground = DaftarColors.Red.copy(alpha = 0.12f),
                    )
                    DetailRow(
                        label = "Receiver · ترلاسه کوونکی",
                        value = hawala.receiverName,
                        sub = "${hawala.toCity.displayName} · ${hawala.toCity.code}",
                        icon = Icons.Rounded.ArrowDownward,
                        iconTint = DaftarColors.Green,
                        iconBackground = DaftarColors.Green.copy(alpha = 0.12f),
                        showDivider = false,
                    )
                }
            }

            item {
                DetailSectionTitle("Partner")
                DetailCard {
                    DetailRow(
                        label = "Partner saraf",
                        value = partner.name,
                        // v18 folds city, phone, and tier into one sub-line.
                        sub = "${partner.city.displayName} · ${partner.phone} · " +
                            partner.tier.label.replace("-", "").uppercase() + " tier",
                        leading = { PartnerBadge(partner, 36.dp) },
                        trailing = {
                            Icon(
                                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = null,
                                tint = DaftarColors.Muted,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        showDivider = false,
                        modifier = Modifier.clickable {
                            navController.navigate(DaftarDestinations.partnerDetail(partner.id))
                        },
                    )
                }
            }

            item {
                DetailSectionTitle("Financials")
                DetailCard {
                    DetailRow(
                        label = "Commission",
                        value = commissionLabel,
                        aside = Formatters.amount(commissionAmount, hawala.currency) + " " + hawala.currency,
                        icon = Icons.Rounded.Tag,
                    )
                    DetailRow(
                        label = "Net to " + (if (hawala.type == HawalaType.SEND) "receiver" else "you"),
                        value = Formatters.amount(hawala.amount - commissionAmount, hawala.currency) +
                            " " + hawala.currency,
                        icon = Icons.Rounded.Balance,
                        showDivider = false,
                    )
                }
            }

            item {
                DetailSectionTitle("Status timeline")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(DaftarColors.PaperSoft)
                        .border(1.dp, DaftarColors.Line, RoundedCornerShape(14.dp))
                        .padding(14.dp),
                ) {
                    TimelineStep(
                        icon = Icons.Rounded.Check,
                        state = TimelineState.DONE,
                        label = "Hawala issued",
                        time = Formatters.dayMonthLabel(hawala.timestampMillis) + ", " +
                            Formatters.timeLabel(hawala.timestampMillis),
                    )
                    TimelineStep(
                        icon = Icons.Rounded.Share,
                        state = TimelineState.DONE,
                        label = "Code shared with partner",
                        time = "Auto-sent via WhatsApp",
                    )
                    TimelineStep(
                        icon = if (pending) Icons.Rounded.Schedule else Icons.Rounded.Check,
                        state = if (pending) TimelineState.CURRENT else TimelineState.DONE,
                        label = if (pending) {
                            if (send) "Awaiting pickup" else "Awaiting payout"
                        } else "Paid out to receiver",
                        time = if (pending) {
                            if (send) "Receiver presents the code to collect" else "Pay out when the receiver collects"
                        } else if (hawala.dateLabel == "Just paid") "Just now" else hawala.dateLabel,
                        isLast = pending,
                    )
                    if (!pending) {
                        TimelineStep(
                            icon = Icons.Rounded.Balance,
                            state = TimelineState.UPCOMING,
                            label = "Pending settlement with ${partner.shortName}",
                            time = "Will be netted at next reconciliation",
                            isLast = true,
                        )
                    }
                }
            }
        }

        // Bottom actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DaftarColors.Paper)
                .padding(horizontal = 18.dp, vertical = 14.dp)
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (pending) {
                // v20: pending receive pays out via a sheet; pending send marks paid.
                if (send) {
                    BottomAction(
                        label = "Mark Paid Out", icon = Icons.Rounded.Check,
                        background = DaftarColors.Green, modifier = Modifier.weight(1f),
                    ) { viewModel.markPaid() }
                } else {
                    BottomAction(
                        label = "Pay Out · ورکړه", icon = Icons.Rounded.Check,
                        background = DaftarColors.Green, modifier = Modifier.weight(1f),
                    ) { viewModel.openPayout() }
                }
                // v20: Share Code is replaced by a red Cancel Hawala (confirm first).
                BottomAction(
                    label = "Cancel Hawala", icon = Icons.Rounded.Close,
                    background = DaftarColors.Red, modifier = Modifier.weight(1f),
                ) { viewModel.openCancel() }
            } else {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.5.dp, DaftarColors.Ink, RoundedCornerShape(14.dp))
                        .clickable(onClick = backToHawalas)
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = DaftarColors.Ink, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "BACK TO ALL HAWALAS",
                        style = TextStyle(
                            fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold,
                            fontSize = 12.sp, letterSpacing = 0.08.em, color = DaftarColors.Ink,
                        ),
                    )
                }
            }
        }
    }

    // ---- Cancel-confirm + payout overlays ----
    if (state.cancelConfirmOpen) {
        CancelHawalaDialog(
            isReceive = !send,
            code = hawala.pickupCode,
            reversesDebit = hawala.senderCustomerId != null,
            onDismiss = viewModel::dismissCancel,
            onConfirm = { viewModel.confirmCancel(onCancelled = backToHawalas) },
        )
    }
    val payout = state.payout
    if (payout != null) {
        PayoutSheet(
            hawala = hawala,
            payout = payout,
            customers = state.customers,
            viewModel = viewModel,
            onDone = backToHawalas,
        )
    }
}

/** Confirm modal for Cancel Hawala — confirming cancels and returns to the list. */
@Composable
private fun CancelHawalaDialog(
    isReceive: Boolean,
    code: String,
    reversesDebit: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(DaftarColors.Paper)
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.size(54.dp).clip(CircleShape).background(DaftarColors.Red.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Close, null, tint = DaftarColors.Red, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Cancel this hawala?",
                style = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 19.sp, color = DaftarColors.Ink),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "This removes the ${if (isReceive) "received" else "sent"} hawala record (code $code)" +
                    (if (reversesDebit) " and reverses the account debit" else "") + ". This can't be undone.",
                style = TextStyle(fontFamily = com.daftar.app.ui.theme.Inter, fontSize = 12.5.sp, color = DaftarColors.Muted, lineHeight = 18.sp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.5.dp, DaftarColors.LineStrong, RoundedCornerShape(12.dp))
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 13.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text("Keep it", style = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = DaftarColors.Ink))
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(DaftarColors.Red)
                        .clickable(onClick = onConfirm)
                        .padding(vertical = 13.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text("Cancel Hawala", style = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = DaftarColors.Paper))
                }
            }
        }
    }
}

/** Payout sheet — pay a received hawala as cash, or credit its net to an account. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun PayoutSheet(
    hawala: Hawala,
    payout: PayoutState,
    customers: List<com.daftar.app.domain.model.Customer>,
    viewModel: HawalaDetailViewModel,
    onDone: () -> Unit,
) {
    val net = hawala.amount - hawala.resolvedCommissionAmount
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = viewModel::dismissPayout,
        containerColor = DaftarColors.Paper,
        dragHandle = { com.daftar.app.ui.common.SheetHandle() },
    ) {
        if (payout.pickerOpen) {
            Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                Text("Credit which account?", style = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 18.sp, color = DaftarColors.Ink))
                Spacer(Modifier.height(12.dp))
                if (customers.isEmpty()) {
                    Text("No accounts yet — add a customer account first, then pay out to it.",
                        style = TextStyle(fontFamily = com.daftar.app.ui.theme.Inter, fontSize = 12.sp, color = DaftarColors.Muted))
                }
                customers.forEach { c ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { viewModel.pickPayoutCustomer(c.id) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        com.daftar.app.ui.common.CustomerBadge(c, 40.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(c.name, style = TextStyle(fontFamily = com.daftar.app.ui.theme.Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = DaftarColors.Ink))
                            Text("${c.city.displayName} · ${c.transactions.size} entries", style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp, color = DaftarColors.Muted))
                        }
                        if (payout.customerId == c.id) {
                            Icon(Icons.Rounded.Check, null, tint = DaftarColors.Green, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            return@ModalBottomSheet
        }

        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("Pay out hawala", style = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 18.sp, color = DaftarColors.Ink))
            Text("د حوالې ورکړه", style = TextStyle(fontFamily = NotoNaskhArabic, fontSize = 12.sp, color = DaftarColors.Muted, textDirection = TextDirection.Rtl))
            Spacer(Modifier.height(14.dp))

            // Net-to-pay summary
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(DaftarColors.PaperDeep)
                    .border(1.dp, DaftarColors.Line, RoundedCornerShape(14.dp))
                    .padding(14.dp),
            ) {
                Text("${hawala.receiverName} · code ${hawala.pickupCode}",
                    style = TextStyle(fontFamily = JetBrainsMono, fontSize = 12.sp, color = DaftarColors.Muted))
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    MonoLabel("Net to pay", fontSize = 9)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(Formatters.amount(net, hawala.currency),
                            style = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 24.sp, color = DaftarColors.Green))
                        Text(" ${hawala.currency}",
                            style = TextStyle(fontFamily = JetBrainsMono, fontSize = 12.sp, color = DaftarColors.Muted),
                            modifier = Modifier.padding(bottom = 3.dp))
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            MonoLabel("How are you paying?", fontSize = 9)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PayoutMethodChip("Pay cash", "Hand over cash", Icons.Rounded.Balance, payout.method == PayoutMethod.CASH, Modifier.weight(1f)) {
                    viewModel.setPayoutMethod(PayoutMethod.CASH)
                }
                PayoutMethodChip("Credit account", "To a holder", Icons.Rounded.ArrowDownward, payout.method == PayoutMethod.ACCOUNT, Modifier.weight(1f)) {
                    viewModel.setPayoutMethod(PayoutMethod.ACCOUNT)
                }
            }

            if (payout.method == PayoutMethod.ACCOUNT) {
                Spacer(Modifier.height(12.dp))
                val chosen = customers.firstOrNull { it.id == payout.customerId }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DaftarColors.Green.copy(alpha = 0.05f))
                        .border(1.dp, DaftarColors.Green, RoundedCornerShape(12.dp))
                        .clickable { viewModel.openPayoutPicker() }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (chosen != null) com.daftar.app.ui.common.CustomerBadge(chosen, 38.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        MonoLabel("Receiver account", color = DaftarColors.Green, fontSize = 9)
                        Spacer(Modifier.height(2.dp))
                        Text(chosen?.name ?: "Choose account to credit",
                            style = TextStyle(fontFamily = com.daftar.app.ui.theme.Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = if (chosen != null) DaftarColors.Ink else DaftarColors.Muted))
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = DaftarColors.Muted, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(Modifier.height(18.dp))
            com.daftar.app.ui.common.SubmitButton(
                label = "Confirm Payout · ورکړه",
                icon = Icons.Rounded.Check,
                container = DaftarColors.Green,
                onClick = { viewModel.confirmPayout(onDone) },
            )
        }
    }
}

@Composable
private fun PayoutMethodChip(
    label: String,
    sub: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) DaftarColors.Ink else DaftarColors.PaperSoft)
            .border(1.dp, if (selected) DaftarColors.Ink else DaftarColors.LineStrong, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, tint = if (selected) DaftarColors.GoldSoft else DaftarColors.Muted, modifier = Modifier.size(14.dp))
        Column {
            Text(label, style = TextStyle(fontFamily = com.daftar.app.ui.theme.Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = if (selected) DaftarColors.Paper else DaftarColors.Ink))
            Text(sub, style = TextStyle(fontFamily = com.daftar.app.ui.theme.Inter, fontSize = 8.sp, color = if (selected) DaftarColors.MutedLight else DaftarColors.Muted))
        }
    }
}

@Composable
private fun RouteCity(code: String, name: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = code,
            style = TextStyle(
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                letterSpacing = 0.1.em,
                color = DaftarColors.Paper,
            ),
        )
        Spacer(Modifier.height(2.dp))
        MonoLabel(name, color = DaftarColors.GoldSoft, fontSize = 9, letterSpacing = 0.15)
    }
}

@Composable
private fun CodeAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(DaftarColors.PaperSoft)
            .border(1.dp, DaftarColors.LineStrong, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = DaftarColors.InkSoft, modifier = Modifier.size(11.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text = label.uppercase(),
            style = TextStyle(
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 0.1.em,
                color = DaftarColors.InkSoft,
            ),
        )
    }
}

private enum class TimelineState { DONE, CURRENT, UPCOMING }

@Composable
private fun TimelineStep(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    state: TimelineState,
    label: String,
    time: String,
    isLast: Boolean = false,
) {
    Row(
        modifier = Modifier
            .height(IntrinsicSize.Min)
            .padding(bottom = if (isLast) 4.dp else 0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    // v18 gives the current dot a soft copper halo ring.
                    .then(
                        if (state == TimelineState.CURRENT) {
                            Modifier.border(4.dp, DaftarColors.Copper.copy(alpha = 0.18f), CircleShape)
                        } else Modifier,
                    )
                    .clip(CircleShape)
                    .background(
                        when (state) {
                            TimelineState.DONE -> DaftarColors.Green
                            TimelineState.CURRENT -> DaftarColors.Copper
                            TimelineState.UPCOMING -> DaftarColors.PaperDeep
                        },
                    )
                    .border(
                        1.5.dp,
                        when (state) {
                            TimelineState.DONE -> DaftarColors.Green
                            TimelineState.CURRENT -> DaftarColors.Copper
                            TimelineState.UPCOMING -> DaftarColors.GoldSoft
                        },
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (state == TimelineState.UPCOMING) DaftarColors.Gold else DaftarColors.Paper,
                    modifier = Modifier.size(11.dp),
                )
            }
            if (!isLast) {
                // Connector rail linking this dot to the next step (v18).
                Box(
                    modifier = Modifier
                        .width(1.5.dp)
                        .weight(1f)
                        .background(DaftarColors.LineStrong),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.padding(bottom = if (isLast) 0.dp else 14.dp)) {
            Text(
                text = label,
                style = TextStyle(
                    fontFamily = com.daftar.app.ui.theme.Inter,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = if (state == TimelineState.UPCOMING) DaftarColors.Muted else DaftarColors.Ink,
                ),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = time,
                style = TextStyle(fontFamily = JetBrainsMono, fontSize = 10.sp, color = DaftarColors.Muted),
            )
        }
    }
}

@Composable
private fun BottomAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    background: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = DaftarColors.Paper, modifier = Modifier.size(14.dp))
        Spacer(Modifier.size(8.dp))
        Text(
            text = label.uppercase(),
            style = TextStyle(
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 0.08.em,
                color = DaftarColors.Paper,
            ),
        )
    }
}

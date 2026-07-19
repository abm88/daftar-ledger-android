package com.daftar.app.ui.feature.newhawala

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Schedule
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.daftar.app.domain.model.AssetCatalog
import com.daftar.app.domain.model.Counterparty
import com.daftar.app.domain.repository.PartnerRepository
import com.daftar.app.domain.repository.SettingsRepository
import com.daftar.app.domain.usecase.ReceiveHawalaDraft
import com.daftar.app.domain.usecase.RecordReceiveHawalaUseCase
import com.daftar.app.ui.common.BigAmountInput
import com.daftar.app.ui.common.FieldBox
import com.daftar.app.ui.common.FieldTextInput
import com.daftar.app.ui.common.IconSquareButton
import com.daftar.app.ui.common.MonoLabel
import com.daftar.app.ui.common.PartnerBadge
import com.daftar.app.ui.common.SheetHandle
import com.daftar.app.ui.common.SubmitButton
import com.daftar.app.ui.common.ToastCenter
import com.daftar.app.ui.common.ToastIcon
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

data class ReceiveHawalaForm(
    val currency: String = "AFN",
    val amountText: String = "",
    val senderName: String = "",
    val receiverName: String = "",
    val partnerId: String? = null,
    val code: String = "",
    val pickerOpen: Boolean = false,
    val amountError: Boolean = false,
)

data class ReceiveHawalaUiState(
    val form: ReceiveHawalaForm = ReceiveHawalaForm(),
    val partner: Counterparty? = null,
    val partners: List<Counterparty> = emptyList(),
    val activeCurrencies: List<String> = listOf("USD", "AFN", "PKR"),
) {
    val amount: Double get() = form.amountText.toDoubleOrNull() ?: 0.0
}

@HiltViewModel
class ReceiveHawalaViewModel @Inject constructor(
    partnerRepository: PartnerRepository,
    settingsRepository: SettingsRepository,
    private val recordReceive: RecordReceiveHawalaUseCase,
    private val toastCenter: ToastCenter,
) : ViewModel() {

    private val form = MutableStateFlow(
        ReceiveHawalaForm(
            currency = settingsRepository.settings.value.tradeCurrency,
            partnerId = partnerRepository.partners.value.firstOrNull()?.id,
        ),
    )

    val uiState = combine(
        form,
        partnerRepository.partners,
        settingsRepository.settings,
    ) { form, partners, settings ->
        val active = settings.activeCurrencies().map { it.code }
        ReceiveHawalaUiState(
            form = form,
            partner = partners.firstOrNull { it.id == form.partnerId },
            partners = partners,
            activeCurrencies = if (form.currency in active) active else active + form.currency,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReceiveHawalaUiState())

    fun update(transform: (ReceiveHawalaForm) -> ReceiveHawalaForm) { form.value = transform(form.value) }

    fun save(onSaved: (String) -> Unit) {
        val state = uiState.value
        val f = state.form
        when {
            state.amount <= 0 -> {
                update { it.copy(amountError = true) }
                toastCenter.show("Enter the amount received", ToastIcon.CROSS)
            }
            f.partnerId == null -> toastCenter.show("Choose the origin branch", ToastIcon.CROSS)
            f.receiverName.isBlank() -> toastCenter.show("Enter who will collect", ToastIcon.CROSS)
            else -> viewModelScope.launch {
                val id = recordReceive(
                    ReceiveHawalaDraft(
                        partnerId = f.partnerId,
                        pickupCode = f.code,
                        currency = f.currency,
                        amount = state.amount,
                        senderName = f.senderName,
                        receiverName = f.receiverName,
                    ),
                )
                if (id != null) {
                    toastCenter.show("Hawala recorded · pending payout", ToastIcon.CHECK)
                    onSaved(id)
                } else {
                    toastCenter.show("Check the form and try again", ToastIcon.CROSS)
                }
            }
        }
    }
}

/**
 * Receive form (phase one of a two-phase receive): records the incoming hawala
 * as pending. Payout — cash or account credit — happens later from the detail.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiveHawalaScreen(
    navController: NavController,
    viewModel: ReceiveHawalaViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val form = state.form

    Column(modifier = Modifier.fillMaxWidth()) {
        // Green header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DaftarColors.Green)
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "New Hawala · Receive",
                    style = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 18.sp, color = DaftarColors.Paper),
                )
                Text(
                    text = "د پیسو ترلاسه کول",
                    style = TextStyle(fontFamily = NotoNaskhArabic, fontSize = 12.sp, color = DaftarColors.Paper.copy(alpha = 0.85f), textDirection = TextDirection.Rtl),
                )
            }
            IconSquareButton(Icons.Rounded.Close, { navController.popBackStack() }, onDark = true)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            // Currency pills
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
                            .clickable { viewModel.update { it.copy(currency = code) } }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Text(AssetCatalog.symbolFor(code), style = TextStyle(fontFamily = Inter, fontSize = 11.sp, color = if (on) DaftarColors.GoldSoft else DaftarColors.Muted))
                        Text(code, style = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.05.em, color = if (on) DaftarColors.Paper else DaftarColors.InkSoft))
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            BigAmountInput(
                value = form.amountText,
                onValueChange = { text -> viewModel.update { it.copy(amountText = text, amountError = false) } },
                currency = form.currency,
                label = "Amount received · type amount",
                error = form.amountError,
            )

            Spacer(Modifier.height(12.dp))
            FieldBox("Sender · لیږونکی · at origin", modifier = Modifier.fillMaxWidth()) {
                FieldTextInput(form.senderName, { text -> viewModel.update { it.copy(senderName = text) } }, "Who sent the money")
            }
            Spacer(Modifier.height(10.dp))
            FieldBox("Receiver · ترلاسه کوونکی · who will collect", modifier = Modifier.fillMaxWidth()) {
                FieldTextInput(form.receiverName, { text -> viewModel.update { it.copy(receiverName = text) } }, "Who will collect here")
            }

            Spacer(Modifier.height(12.dp))
            // "recorded as pending, pay out later" note
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(11.dp))
                    .background(DaftarColors.Gold.copy(alpha = 0.1f))
                    .border(1.dp, DaftarColors.Gold.copy(alpha = 0.4f), RoundedCornerShape(11.dp))
                    .padding(horizontal = 13.dp, vertical = 11.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(Icons.Rounded.Schedule, null, tint = DaftarColors.CopperDeep, modifier = Modifier.size(14.dp))
                Text(
                    text = "Recorded as pending. You'll pay it out — cash or to an account — when the receiver comes to collect.",
                    style = TextStyle(fontFamily = Inter, fontSize = 11.sp, color = DaftarColors.InkSoft, lineHeight = 16.sp),
                )
            }

            Spacer(Modifier.height(12.dp))
            // Origin branch picker
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
                Icon(Icons.Rounded.Group, null, tint = DaftarColors.InkSoft, modifier = Modifier.size(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    MonoLabel("From branch · له کومې څانګې", fontSize = 9)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = state.partner?.let { "${it.name} · ${it.city.displayName}" } ?: "Choose origin branch",
                        style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = if (state.partner != null) DaftarColors.Ink else DaftarColors.Muted),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = DaftarColors.Muted, modifier = Modifier.size(16.dp))
            }

            Spacer(Modifier.height(12.dp))
            // Pickup code entry
            FieldBox("Pickup code · د ترلاسه کولو کوډ", modifier = Modifier.fillMaxWidth()) {
                FieldTextInput(
                    form.code,
                    { text -> viewModel.update { it.copy(code = text.filter { c -> c.isDigit() }) } },
                    "Enter the code from origin",
                    textStyle = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 0.2.em, color = DaftarColors.Ink),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                )
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
                label = "Save Hawala",
                icon = Icons.Rounded.Check,
                container = DaftarColors.Green,
                onClick = {
                    viewModel.save { hawalaId ->
                        navController.popBackStack()
                        navController.navigate(DaftarDestinations.hawalaDetail(hawalaId))
                    }
                },
            )
        }
    }

    if (form.pickerOpen) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.update { it.copy(pickerOpen = false) } },
            containerColor = DaftarColors.Paper,
            dragHandle = { SheetHandle() },
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                Text("Origin branch", style = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 18.sp, color = DaftarColors.Ink))
                Spacer(Modifier.height(12.dp))
                state.partners.forEach { partner ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { viewModel.update { it.copy(partnerId = partner.id, pickerOpen = false) } }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        PartnerBadge(partner, 40.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(partner.name, style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = DaftarColors.Ink))
                            Text("${partner.city.displayName} · ${partner.phone}", style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp, color = DaftarColors.Muted))
                        }
                        if (form.partnerId == partner.id) {
                            Icon(Icons.Rounded.Check, null, tint = DaftarColors.Green, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

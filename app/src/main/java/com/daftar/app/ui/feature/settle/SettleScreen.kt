package com.daftar.app.ui.feature.settle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
import com.daftar.app.domain.model.AssetCatalog
import com.daftar.app.domain.model.Counterparty
import com.daftar.app.domain.model.MoneyByCurrency
import com.daftar.app.domain.model.RatePair
import com.daftar.app.domain.repository.PartnerRepository
import com.daftar.app.domain.repository.RatesRepository
import com.daftar.app.domain.usecase.CurrencyConverter
import com.daftar.app.domain.usecase.PositionCalculator
import com.daftar.app.domain.usecase.SettlePartnerUseCase
import com.daftar.app.ui.common.IconSquareButton
import com.daftar.app.ui.common.MonoLabel
import com.daftar.app.ui.common.ToastCenter
import com.daftar.app.ui.common.ToastIcon
import com.daftar.app.ui.common.PartnerBadge
import com.daftar.app.ui.theme.DaftarColors
import com.daftar.app.ui.theme.Fraunces
import com.daftar.app.ui.theme.Inter
import com.daftar.app.ui.theme.JetBrainsMono
import com.daftar.app.ui.theme.NotoNaskhArabic
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettleFormState(
    val settleCurrency: String = "USD",
    /** Manual conversion sheet, editable by the saraf. Keys: USD_AFN, PKR_AFN, USD_PKR. */
    val rateTexts: Map<String, String> = emptyMap(),
)

data class SettleUiState(
    val partner: Counterparty? = null,
    val position: MoneyByCurrency = MoneyByCurrency(),
    val form: SettleFormState = SettleFormState(),
    val convertedPerCurrency: Map<String, Double> = emptyMap(),
    val netInSettleCurrency: Double = 0.0,
) {
    val hasOpenPosition: Boolean get() = !position.isFlat()
}

@HiltViewModel
class SettleViewModel @Inject constructor(
    partnerRepository: PartnerRepository,
    private val ratesRepository: RatesRepository,
    private val positionCalculator: PositionCalculator,
    private val converter: CurrencyConverter,
    private val settlePartner: SettlePartnerUseCase,
    private val toastCenter: ToastCenter,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val partnerId: String = checkNotNull(savedStateHandle["partnerId"])
    private val form = MutableStateFlow(SettleFormState(rateTexts = defaultRateTexts()))

    private fun defaultRateTexts(): Map<String, String> {
        val book = ratesRepository.rateBook.value
        return mapOf(
            "USD_AFN" to Formatters.rate(book.pairs[RatePair.USD_AFN]?.sell ?: 0.0, 2),
            "PKR_AFN" to Formatters.rate(book.pairs[RatePair.PKR_AFN]?.sell ?: 0.0, 3),
            "USD_PKR" to Formatters.rate(book.pairs[RatePair.USD_PKR]?.sell ?: 0.0, 2),
        )
    }

    val uiState = combine(
        partnerRepository.partners,
        form,
    ) { partners, form ->
        val partner = partners.firstOrNull { it.id == partnerId }
        val position = partner?.let(positionCalculator::partnerPosition) ?: MoneyByCurrency()
        val manualRates = form.rateTexts.mapValues { (_, text) -> text.toDoubleOrNull() ?: 0.0 }
        val converted = AssetCatalog.LEDGER_CURRENCIES.associateWith { cur ->
            converter.convertWithManualRates(position[cur], cur, form.settleCurrency, manualRates)
        }
        SettleUiState(
            partner = partner,
            position = position,
            form = form,
            convertedPerCurrency = converted,
            netInSettleCurrency = converted.values.sum(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettleUiState())

    fun setSettleCurrency(currency: String) {
        form.value = form.value.copy(settleCurrency = currency)
    }

    fun setRateText(key: String, text: String) {
        form.value = form.value.copy(rateTexts = form.value.rateTexts + (key to text))
    }

    fun resetRates() {
        form.value = form.value.copy(rateTexts = defaultRateTexts())
        toastCenter.show("Rates reset to today", ToastIcon.REFRESH)
    }

    fun confirm(onSettled: () -> Unit) {
        viewModelScope.launch {
            val partner = uiState.value.partner ?: return@launch
            if (settlePartner(partner.id, uiState.value.form.settleCurrency)) {
                toastCenter.show("${partner.shortName} · settlement recorded", ToastIcon.SCALE)
                onSettled()
            }
        }
    }
}

/** End-of-period settlement: net the partner's position into one currency. */
@Composable
fun SettleScreen(
    navController: NavController,
    viewModel: SettleViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val partner = state.partner ?: return

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DaftarColors.Ink)
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconSquareButton(Icons.AutoMirrored.Rounded.ArrowBack, { navController.popBackStack() }, onDark = true)
            Column {
                Text(
                    "Settlement",
                    style = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 18.sp, color = DaftarColors.Paper),
                )
                Text(
                    "د حساب تصفیه · End of period",
                    style = TextStyle(fontFamily = JetBrainsMono, fontSize = 10.sp, color = DaftarColors.GoldSoft),
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            // Partner card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(DaftarColors.PaperSoft)
                    .border(1.dp, DaftarColors.Line, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PartnerBadge(partner, 44.dp)
                Column {
                    Text(
                        partner.name,
                        style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = DaftarColors.Ink),
                    )
                    Text(
                        "${partner.city.displayName} · ${partner.tier.label.uppercase()} tier",
                        style = TextStyle(fontFamily = JetBrainsMono, fontSize = 10.sp, color = DaftarColors.Muted),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            SettleSectionTitle("Current position")
            Spacer(Modifier.height(8.dp))

            if (!state.hasOpenPosition) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(DaftarColors.PaperSoft)
                        .border(1.dp, DaftarColors.Line, RoundedCornerShape(14.dp))
                        .padding(vertical = 30.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "All balances are zero · nothing to settle",
                        style = TextStyle(fontFamily = Inter, fontSize = 13.sp, color = DaftarColors.Muted),
                    )
                }
            } else {
                AssetCatalog.LEDGER_CURRENCIES.forEach { cur ->
                    val amount = state.position[cur]
                    val open = abs(amount) >= 0.5
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(DaftarColors.PaperSoft)
                            .border(1.dp, DaftarColors.Line, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (open) DaftarColors.Ink else DaftarColors.PaperDeep),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                cur,
                                style = TextStyle(
                                    fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 9.sp,
                                    color = if (open) DaftarColors.GoldSoft else DaftarColors.Muted,
                                ),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (!open) "No exposure" else if (amount > 0) "They owe you" else "You owe them",
                                style = TextStyle(fontFamily = Inter, fontSize = 11.sp, color = DaftarColors.Muted),
                            )
                            Text(
                                text = if (!open) "— flat"
                                else (if (amount > 0) "+" else "−") + Formatters.number(abs(amount)),
                                style = TextStyle(
                                    fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 17.sp,
                                    color = when {
                                        !open -> DaftarColors.Muted
                                        amount > 0 -> DaftarColors.Green
                                        else -> DaftarColors.Red
                                    },
                                ),
                            )
                        }
                        if (open) {
                            Column(horizontalAlignment = Alignment.End) {
                                if (cur == state.form.settleCurrency) {
                                    MonoLabel("base", fontSize = 9)
                                } else {
                                    MonoLabel("≈ in ${state.form.settleCurrency}", fontSize = 9, letterSpacing = 0.05)
                                    Text(
                                        text = (if (amount > 0) "+" else "−") + Formatters.amount(
                                            abs(state.convertedPerCurrency[cur] ?: 0.0),
                                            state.form.settleCurrency,
                                        ),
                                        style = TextStyle(
                                            fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                                            color = if (amount > 0) DaftarColors.Green else DaftarColors.Red,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                SettleSectionTitle("Settle in")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("USD" to "US Dollar", "AFN" to "Afghani", "PKR" to "Pak Rupee").forEach { (cur, sub) ->
                        val on = state.form.settleCurrency == cur
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (on) DaftarColors.Ink else DaftarColors.PaperSoft)
                                .border(
                                    1.5.dp,
                                    if (on) DaftarColors.Ink else DaftarColors.LineStrong,
                                    RoundedCornerShape(12.dp),
                                )
                                .clickable { viewModel.setSettleCurrency(cur) }
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                cur,
                                style = TextStyle(
                                    fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                                    color = if (on) DaftarColors.GoldSoft else DaftarColors.Ink,
                                ),
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                sub,
                                style = TextStyle(
                                    fontFamily = Inter, fontSize = 10.sp,
                                    color = if (on) DaftarColors.MutedLight else DaftarColors.Muted,
                                ),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(DaftarColors.PaperSoft)
                        .border(1.dp, DaftarColors.LineStrong, RoundedCornerShape(14.dp))
                        .padding(14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MonoLabel("Conversion rates · today")
                        Text(
                            "RESET",
                            style = TextStyle(
                                fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold,
                                fontSize = 9.sp, letterSpacing = 0.1.em, color = DaftarColors.Copper,
                            ),
                            modifier = Modifier.clickable(onClick = viewModel::resetRates),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    // Only the pairs relevant to converting into the chosen settlement currency.
                    val visiblePairs = when (state.form.settleCurrency) {
                        "USD" -> listOf("USD_AFN" to ("1 USD" to "AFN"), "USD_PKR" to ("1 USD" to "PKR"))
                        "AFN" -> listOf("USD_AFN" to ("1 USD" to "AFN"), "PKR_AFN" to ("1 PKR" to "AFN"))
                        else -> listOf("USD_PKR" to ("1 USD" to "PKR"), "PKR_AFN" to ("1 PKR" to "AFN"))
                    }
                    visiblePairs.forEach { (key, labels) ->
                        val (left, right) = labels
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "$left =",
                                style = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = DaftarColors.InkSoft),
                                modifier = Modifier.width(72.dp),
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DaftarColors.Paper)
                                    .border(1.dp, DaftarColors.LineStrong, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                            ) {
                                BasicTextField(
                                    value = state.form.rateTexts[key] ?: "",
                                    onValueChange = { text ->
                                        viewModel.setRateText(key, text.filter { it.isDigit() || it == '.' })
                                    },
                                    textStyle = TextStyle(
                                        fontFamily = Fraunces, fontWeight = FontWeight.Medium,
                                        fontSize = 16.sp, color = DaftarColors.Ink,
                                    ),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            Text(
                                " $right",
                                style = TextStyle(fontFamily = JetBrainsMono, fontSize = 12.sp, color = DaftarColors.Muted),
                                modifier = Modifier.width(44.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                // Net summary
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(DaftarColors.Ink)
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    MonoLabel("Net to settle", color = DaftarColors.GoldSoft, fontSize = 9, letterSpacing = 0.25)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = when {
                            abs(state.netInSettleCurrency) < 0.5 -> "Already settled"
                            state.netInSettleCurrency > 0 -> "${partner.shortName} pays you"
                            else -> "You pay ${partner.shortName}"
                        },
                        style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = DaftarColors.Paper),
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = if (abs(state.netInSettleCurrency) < 0.5) "0"
                            else Formatters.amount(abs(state.netInSettleCurrency), state.form.settleCurrency),
                            style = TextStyle(
                                fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 34.sp,
                                color = when {
                                    abs(state.netInSettleCurrency) < 0.5 -> DaftarColors.MutedLight
                                    state.netInSettleCurrency > 0 -> DaftarColors.LongGreen
                                    else -> DaftarColors.ShortRed
                                },
                            ),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            state.form.settleCurrency,
                            style = TextStyle(
                                fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                                letterSpacing = 0.1.em, color = DaftarColors.GoldSoft,
                            ),
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = when {
                            abs(state.netInSettleCurrency) < 0.5 -> "حساب صفر دی"
                            state.netInSettleCurrency > 0 -> "هغوی پیسې درکوي"
                            else -> "تاسو ورکوئ"
                        },
                        style = TextStyle(
                            fontFamily = NotoNaskhArabic, fontSize = 12.sp,
                            color = DaftarColors.MutedLight, textDirection = TextDirection.Rtl,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        // Bottom bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DaftarColors.Paper)
                .padding(horizontal = 18.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.5.dp, DaftarColors.Ink, RoundedCornerShape(14.dp))
                    .clickable { navController.popBackStack() }
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    if (state.hasOpenPosition) "CANCEL" else "CLOSE",
                    style = TextStyle(
                        fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold,
                        fontSize = 12.sp, letterSpacing = 0.08.em, color = DaftarColors.Ink,
                    ),
                )
            }
            if (state.hasOpenPosition) {
                Row(
                    modifier = Modifier
                        .weight(2f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(DaftarColors.Green)
                        .clickable { viewModel.confirm { navController.popBackStack() } }
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Check, null, tint = DaftarColors.Paper, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "CONFIRM SETTLEMENT",
                        style = TextStyle(
                            fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold,
                            fontSize = 12.sp, letterSpacing = 0.08.em, color = DaftarColors.Paper,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun SettleSectionTitle(title: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MonoLabel(title)
        HorizontalDivider(Modifier.weight(1f), color = DaftarColors.Line)
    }
}

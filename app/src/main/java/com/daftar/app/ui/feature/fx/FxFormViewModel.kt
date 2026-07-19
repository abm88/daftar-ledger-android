package com.daftar.app.ui.feature.fx

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daftar.app.core.format.Formatters
import com.daftar.app.domain.repository.CashRepository
import com.daftar.app.domain.repository.RatesRepository
import com.daftar.app.domain.repository.SettingsRepository
import com.daftar.app.domain.usecase.CurrencyConverter
import com.daftar.app.domain.usecase.FxTradeDraft
import com.daftar.app.domain.usecase.RecordFxResult
import com.daftar.app.domain.usecase.RecordFxTradeUseCase
import com.daftar.app.ui.common.ToastCenter
import com.daftar.app.ui.common.ToastIcon
import com.daftar.app.ui.common.sanitizeAmountInput
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class FxLeg { NONE, FROM, TO }

data class FxFormState(
    val fromCurrency: String = "USD",
    val toCurrency: String = "AFN",
    val fromAmountText: String = "",
    val rateText: String = "",
    val note: String = "",
    val picker: FxLeg = FxLeg.NONE,
    val confirming: Boolean = false,
)

data class FxFormUiState(
    val form: FxFormState = FxFormState(),
    val availableCash: Double = 0.0,
    val drawerBalances: Map<String, Double> = emptyMap(),
    val pairBase: String = "USD",
    val pairQuote: String = "AFN",
    val marketRate: Double? = null,
    val computedToAmount: Double = 0.0,
    val projectedPnl: Double? = null,
) {
    val amount: Double get() = form.fromAmountText.toDoubleOrNull() ?: 0.0
    val rate: Double get() = form.rateText.toDoubleOrNull() ?: 0.0
    // v18's form gate is strict — any overshoot of the drawer blocks review.
    val sufficientCash: Boolean get() = amount <= availableCash
    val canSubmit: Boolean get() = amount > 0 && rate > 0 && sufficientCash
    val isBuy: Boolean get() = form.fromCurrency == "AFN"
    val marketRateText: String
        get() = marketRate?.let { Formatters.rate(it, if (pairQuote == "AFN" && pairBase == "PKR") 3 else 2) } ?: ""
    val formulaText: String?
        get() = if (computedToAmount <= 0) null else {
            "${Formatters.amount(amount, form.fromCurrency)} ${form.fromCurrency} " +
                (if (form.fromCurrency == pairBase) "×" else "÷") +
                // v18 formats the parsed rate to the pair's decimals here.
                " ${Formatters.rate(rate, 2)} = ${Formatters.amount(computedToAmount, form.toCurrency)} ${form.toCurrency}"
        }
}

@HiltViewModel
class FxFormViewModel @Inject constructor(
    cashRepository: CashRepository,
    ratesRepository: RatesRepository,
    settingsRepository: SettingsRepository,
    private val converter: CurrencyConverter,
    private val recordTrade: RecordFxTradeUseCase,
    private val toastCenter: ToastCenter,
) : ViewModel() {

    private val form: MutableStateFlow<FxFormState>

    init {
        val trade = settingsRepository.settings.value.tradeCurrency
        form = MutableStateFlow(
            FxFormState(
                fromCurrency = trade,
                toCurrency = if (trade == "AFN") "USD" else "AFN",
            ),
        )
    }

    val uiState = combine(
        form,
        cashRepository.drawer,
        ratesRepository.rateBook,
    ) { form, drawer, rates ->
        val base = converter.canonicalBase(form.fromCurrency, form.toCurrency)
        val quote = if (base == form.fromCurrency) form.toCurrency else form.fromCurrency
        val amount = form.fromAmountText.toDoubleOrNull() ?: 0.0
        val rate = form.rateText.toDoubleOrNull() ?: 0.0
        val draft = FxTradeDraft(form.fromCurrency, form.toCurrency, amount, rate, form.note)
        FxFormUiState(
            form = form,
            availableCash = drawer.balanceOf(form.fromCurrency),
            drawerBalances = drawer.balances,
            pairBase = base,
            pairQuote = quote,
            marketRate = converter.marketRate(base, quote, rates),
            computedToAmount = if (amount > 0 && rate > 0) recordTrade.computeToAmount(draft) else 0.0,
            projectedPnl = if (amount > 0 && rate > 0) recordTrade.projectedRealizedPnl(draft) else null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FxFormUiState())

    fun update(transform: (FxFormState) -> FxFormState) {
        form.value = transform(form.value)
    }

    fun setAmount(text: String) = update { it.copy(fromAmountText = sanitizeAmountInput(text)) }

    fun setRate(text: String) {
        val filtered = text.filter { it.isDigit() || it == '.' }
        val firstDot = filtered.indexOf('.')
        val cleaned = if (firstDot == -1) filtered
        else filtered.substring(0, firstDot + 1) + filtered.substring(firstDot + 1).replace(".", "")
        update { it.copy(rateText = cleaned) }
    }

    fun openPicker(leg: FxLeg) = update { it.copy(picker = leg) }
    fun closePicker() = update { it.copy(picker = FxLeg.NONE) }

    fun pickCurrency(code: String) {
        val current = form.value
        val next = if (current.picker == FxLeg.FROM) {
            current.copy(
                fromCurrency = code,
                toCurrency = if (current.toCurrency == code) (if (code == "AFN") "USD" else "AFN") else current.toCurrency,
            )
        } else {
            current.copy(
                toCurrency = code,
                fromCurrency = if (current.fromCurrency == code) (if (code == "AFN") "USD" else "AFN") else current.fromCurrency,
            )
        }
        // Rate is stale once the pair changes.
        form.value = next.copy(rateText = "", picker = FxLeg.NONE)
    }

    /** The rate is quoted canonically, so it survives a swap unchanged. */
    fun swapPair() = update {
        it.copy(fromCurrency = it.toCurrency, toCurrency = it.fromCurrency)
    }

    fun useMarketRate() {
        val market = uiState.value.marketRate ?: return
        val decimals = if (uiState.value.pairBase == "PKR") 3 else 2
        update { it.copy(rateText = Formatters.rate(market, decimals)) }
    }

    fun review() {
        val state = uiState.value
        when {
            state.amount <= 0 -> toastCenter.show("Enter an amount", ToastIcon.CROSS)
            state.rate <= 0 -> toastCenter.show("Enter a rate", ToastIcon.CROSS)
            state.form.fromCurrency == state.form.toCurrency ->
                toastCenter.show("Currencies must differ", ToastIcon.CROSS)
            !state.sufficientCash ->
                toastCenter.show("Insufficient ${state.form.fromCurrency} cash", ToastIcon.CROSS)
            else -> update { it.copy(confirming = true) }
        }
    }

    fun backToForm() = update { it.copy(confirming = false) }

    fun confirmTrade(onRecorded: () -> Unit) {
        val state = uiState.value
        viewModelScope.launch {
            val result = recordTrade(
                FxTradeDraft(
                    fromCurrency = state.form.fromCurrency,
                    toCurrency = state.form.toCurrency,
                    fromAmount = state.amount,
                    rate = state.rate,
                    note = state.form.note,
                ),
            )
            when (result) {
                is RecordFxResult.Recorded -> {
                    val trade = result.trade
                    val realized = trade.realizedPnlAfn
                    val message = when {
                        realized != null && realized >= 0 ->
                            "Trade done · +${Formatters.number(abs(realized))} AFN profit"
                        realized != null ->
                            "Trade done · −${Formatters.number(abs(realized))} AFN loss"
                        else ->
                            "Bought ${Formatters.amount(trade.toAmount, trade.toCurrency)} ${trade.toCurrency}"
                    }
                    toastCenter.show(
                        message,
                        if (realized != null && realized < 0) ToastIcon.TREND_DOWN else ToastIcon.TREND_UP,
                    )
                    onRecorded()
                }
                RecordFxResult.Error.INSUFFICIENT_CASH ->
                    toastCenter.show("Insufficient ${state.form.fromCurrency} cash", ToastIcon.CROSS)
                is RecordFxResult.Failure -> toastCenter.show(result.message, ToastIcon.CROSS)
                else -> toastCenter.show("Check the trade and try again", ToastIcon.CROSS)
            }
        }
    }
}

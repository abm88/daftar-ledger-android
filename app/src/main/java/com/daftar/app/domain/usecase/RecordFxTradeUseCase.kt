package com.daftar.app.domain.usecase

import com.daftar.app.core.format.Formatters
import com.daftar.app.core.time.TimeProvider
import com.daftar.app.domain.model.FxSide
import com.daftar.app.domain.model.FxTrade
import com.daftar.app.domain.repository.CashRepository
import com.daftar.app.domain.repository.FxRepository
import com.daftar.app.domain.repository.LedgerMutationRepository
import com.daftar.app.domain.repository.RatesRepository
import javax.inject.Inject

data class FxTradeDraft(
    val fromCurrency: String,
    val toCurrency: String,
    val fromAmount: Double,
    val rate: Double,
    val note: String,
)

sealed interface RecordFxResult {
    data class Recorded(val trade: FxTrade) : RecordFxResult
    data class Failure(val message: String) : RecordFxResult
    enum class Error : RecordFxResult { INVALID_AMOUNT, INVALID_RATE, SAME_CURRENCY, INSUFFICIENT_CASH }
}

/**
 * Books an FX trade: derives the traded amount from the canonically-quoted rate,
 * computes realized P&L against the weighted-average cost basis (sell side only),
 * moves both cash legs through the drawer, and appends to the trade log.
 */
class RecordFxTradeUseCase @Inject constructor(
    private val fxRepository: FxRepository,
    private val cashRepository: CashRepository,
    private val ratesRepository: RatesRepository,
    private val mutations: LedgerMutationRepository,
    private val fxAnalytics: FxAnalytics,
    private val currencyConverter: CurrencyConverter,
    private val timeProvider: TimeProvider,
) {
    fun computeToAmount(draft: FxTradeDraft): Double {
        val base = currencyConverter.canonicalBase(draft.fromCurrency, draft.toCurrency)
        return if (draft.fromCurrency == base) draft.fromAmount * draft.rate
        else draft.fromAmount / draft.rate
    }

    /** Projected realized P&L (AFN) if this sell executes, or null when not applicable. */
    fun projectedRealizedPnl(draft: FxTradeDraft): Double? {
        if (draft.fromCurrency == "AFN") return null
        if (draft.fromAmount <= 0 || draft.rate <= 0) return null
        val rates = ratesRepository.rateBook.value
        val costPerUnit = fxAnalytics.averageCostAfn(fxRepository.trades.value, draft.fromCurrency, rates)
        if (costPerUnit <= 0) return null
        val toAmount = computeToAmount(draft)
        val proceedsAfn =
            if (draft.toCurrency == "AFN") toAmount else toAmount * rates.sellRateToAfn(draft.toCurrency)
        return proceedsAfn - costPerUnit * draft.fromAmount
    }

    suspend operator fun invoke(draft: FxTradeDraft): RecordFxResult {
        if (draft.fromAmount <= 0) return RecordFxResult.Error.INVALID_AMOUNT
        if (draft.rate <= 0) return RecordFxResult.Error.INVALID_RATE
        if (draft.fromCurrency == draft.toCurrency) return RecordFxResult.Error.SAME_CURRENCY
        val available = cashRepository.drawer.value.balanceOf(draft.fromCurrency)
        if (draft.fromAmount > available + 0.5) return RecordFxResult.Error.INSUFFICIENT_CASH

        val toAmount = computeToAmount(draft)
        // Giving up AFN acquires foreign inventory (buy); giving up anything else disposes of it (sell).
        val side = if (draft.fromCurrency == "AFN") FxSide.BUY else FxSide.SELL
        val realized = if (side == FxSide.SELL) {
            val rates = ratesRepository.rateBook.value
            val costPerUnit =
                fxAnalytics.averageCostAfn(fxRepository.trades.value, draft.fromCurrency, rates)
            val proceedsAfn =
                if (draft.toCurrency == "AFN") toAmount else toAmount * rates.sellRateToAfn(draft.toCurrency)
            proceedsAfn - costPerUnit * draft.fromAmount
        } else null

        val now = timeProvider.nowMillis()
        val trade = FxTrade(
            id = "fx_$now",
            side = side,
            fromCurrency = draft.fromCurrency,
            toCurrency = draft.toCurrency,
            fromAmount = draft.fromAmount,
            toAmount = toAmount,
            rate = draft.rate,
            realizedPnlAfn = realized,
            timestampMillis = now,
            dateLabel = Formatters.nowLabel(now),
            note = draft.note.trim().ifEmpty { null },
        )
        return try {
            RecordFxResult.Recorded(mutations.createFxTrade(trade))
        } catch (error: Exception) {
            RecordFxResult.Failure(error.message ?: "Unable to record trade")
        }
    }
}

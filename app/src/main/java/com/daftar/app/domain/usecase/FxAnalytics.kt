package com.daftar.app.domain.usecase

import com.daftar.app.core.time.TimeProvider
import com.daftar.app.domain.model.FxPosition
import com.daftar.app.domain.model.FxTrade
import com.daftar.app.domain.model.RateBook
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Inventory analytics over the FX trade log: weighted-average cost basis,
 * open positions, and realized P&L windows. All values in AFN.
 */
@Singleton
class FxAnalytics @Inject constructor(
    private val timeProvider: TimeProvider,
) {

    /**
     * Weighted-average AFN cost of one unit of [currency] currently held.
     * Buys add quantity at cost; sells release quantity at the running average.
     * Cross-currency acquisitions are valued at today's market rate of the
     * currency given up (a simplification carried over from the prototype).
     */
    fun averageCostAfn(trades: List<FxTrade>, currency: String, rates: RateBook): Double {
        if (currency == "AFN") return 1.0
        var qty = 0.0
        var totalCostAfn = 0.0
        trades.sortedBy { it.timestampMillis }.forEach { t ->
            when {
                t.toCurrency == currency -> {
                    val costAfn =
                        if (t.fromCurrency == "AFN") t.fromAmount
                        else t.fromAmount * rates.sellRateToAfn(t.fromCurrency)
                    qty += t.toAmount
                    totalCostAfn += costAfn
                }
                t.fromCurrency == currency -> {
                    val avg = if (qty > 0) totalCostAfn / qty else 0.0
                    totalCostAfn -= avg * t.fromAmount
                    qty -= t.fromAmount
                }
            }
        }
        return if (qty > 0.001) totalCostAfn / qty else 0.0
    }

    /** Open positions for the non-base ledger currencies (USD, PKR). */
    fun openPositions(trades: List<FxTrade>, rates: RateBook): List<FxPosition> =
        listOf("USD", "PKR").map { cur ->
            var qty = 0.0
            var totalCost = 0.0
            trades.sortedBy { it.timestampMillis }.forEach { t ->
                when {
                    t.toCurrency == cur -> {
                        val costAfn =
                            if (t.fromCurrency == "AFN") t.fromAmount
                            else t.fromAmount * rates.sellRateToAfn(t.fromCurrency)
                        qty += t.toAmount
                        totalCost += costAfn
                    }
                    t.fromCurrency == cur -> {
                        val avg = if (qty > 0) totalCost / qty else 0.0
                        totalCost -= avg * t.fromAmount
                        qty -= t.fromAmount
                    }
                }
            }
            val marketRate = rates.sellRateToAfn(cur)
            val marketValue = qty * marketRate
            FxPosition(
                currency = cur,
                quantity = qty,
                averageCostAfn = if (qty > 0.001) totalCost / qty else 0.0,
                marketRateAfn = marketRate,
                marketValueAfn = marketValue,
                unrealizedPnlAfn = marketValue - totalCost,
            )
        }

    fun realizedPnlInWindow(trades: List<FxTrade>, fromMillis: Long, toMillis: Long): Double =
        trades
            .filter { it.timestampMillis in fromMillis until toMillis }
            .sumOf { it.realizedPnlAfn ?: 0.0 }

    fun todayRealizedPnl(trades: List<FxTrade>): Double =
        realizedPnlInWindow(trades, timeProvider.startOfTodayMillis(), timeProvider.nowMillis() + 1)
}

package com.daftar.app.domain.model

enum class FxSide { BUY, SELL }

/**
 * A currency-exchange trade. Stored symmetrically: [fromCurrency] left the drawer,
 * [toCurrency] entered. [rate] is quoted canonically ("1 big-currency = N small").
 */
data class FxTrade(
    val id: String,
    val side: FxSide,
    val fromCurrency: String,
    val toCurrency: String,
    val fromAmount: Double,
    val toAmount: Double,
    val rate: Double,
    /** Realized P&L in AFN; null on acquisitions (no basis released). */
    val realizedPnlAfn: Double? = null,
    val timestampMillis: Long,
    val dateLabel: String,
    val note: String? = null,
)

/** Open inventory position in one currency, valued against AFN. */
data class FxPosition(
    val currency: String,
    val quantity: Double,
    val averageCostAfn: Double,
    val marketRateAfn: Double,
    val marketValueAfn: Double,
    val unrealizedPnlAfn: Double,
) {
    val isFlat: Boolean get() = quantity < 0.01
}

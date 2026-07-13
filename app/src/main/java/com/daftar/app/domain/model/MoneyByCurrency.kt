package com.daftar.app.domain.model

/**
 * Per-currency signed totals. Always carries the three ledger currencies (USD / AFN / PKR)
 * and, per prototype v18, dynamically tracks any other currency an entry was booked in
 * (EUR, GBP, GOLD, …) instead of silently dropping it.
 */
data class MoneyByCurrency(
    val amounts: Map<String, Double> = AssetCatalog.LEDGER_CURRENCIES.associateWith { 0.0 },
) {
    operator fun get(currency: String): Double = amounts[currency] ?: 0.0

    fun plus(currency: String, delta: Double): MoneyByCurrency =
        MoneyByCurrency(amounts + (currency to (this[currency] + delta)))

    /** v18 treats |amount| > 0.5 as an open position, anything else as flat. */
    fun isFlat(threshold: Double = 0.5): Boolean =
        amounts.values.all { kotlin.math.abs(it) <= threshold }

    fun openCurrencies(threshold: Double = 0.5): List<String> =
        orderedCurrencies().filter { kotlin.math.abs(this[it]) > threshold }

    /**
     * The base three currencies plus any extra currency with a meaningful balance —
     * mirrors v18 customerActiveCurrencies() which drives the extra balance pills
     * on the customer detail screen.
     */
    fun activeCurrencies(): List<String> =
        AssetCatalog.LEDGER_CURRENCIES +
            (amounts.keys - AssetCatalog.LEDGER_CURRENCIES.toSet())
                .filter { kotlin.math.abs(this[it]) > 0.0001 }

    /** Ledger currencies first (fixed order), then any extras in insertion order. */
    private fun orderedCurrencies(): List<String> =
        AssetCatalog.LEDGER_CURRENCIES + (amounts.keys - AssetCatalog.LEDGER_CURRENCIES.toSet())
}

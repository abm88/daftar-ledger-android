package com.daftar.app.domain.model

/** Per-currency signed totals over the three ledger currencies (USD / AFN / PKR). */
data class MoneyByCurrency(
    val amounts: Map<String, Double> = AssetCatalog.LEDGER_CURRENCIES.associateWith { 0.0 },
) {
    operator fun get(currency: String): Double = amounts[currency] ?: 0.0

    fun plus(currency: String, delta: Double): MoneyByCurrency {
        if (currency !in amounts) return this
        return MoneyByCurrency(amounts + (currency to (amounts.getValue(currency) + delta)))
    }

    fun isFlat(threshold: Double = 0.5): Boolean =
        amounts.values.all { kotlin.math.abs(it) < threshold }

    fun openCurrencies(threshold: Double = 0.5): List<String> =
        AssetCatalog.LEDGER_CURRENCIES.filter { kotlin.math.abs(this[it]) >= threshold }
}

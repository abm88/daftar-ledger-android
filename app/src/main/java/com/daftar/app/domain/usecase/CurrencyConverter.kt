package com.daftar.app.domain.usecase

import com.daftar.app.domain.model.LedgerSettings
import com.daftar.app.domain.model.RateBook
import com.daftar.app.domain.model.RatePair
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Currency math shared by every screen. All cross-currency conversion bridges
 * through AFN, the base currency of the rate book.
 */
@Singleton
class CurrencyConverter @Inject constructor() {

    fun toAfn(code: String, amount: Double, rates: RateBook): Double =
        rates.toAfn(code, amount)

    /** Convert an amount of any asset into the reporting currency. */
    fun toReporting(code: String, amount: Double, rates: RateBook, settings: LedgerSettings): Double {
        val reporting = settings.reportingCurrency
        if (code == reporting) return amount
        val afn = toAfn(code, amount, rates)
        if (reporting == "AFN") return afn
        val reportingRate = rates.sellRateToAfn(reporting)
        return if (reportingRate > 0) afn / reportingRate else afn
    }

    /**
     * Canonical quoting order for a currency pair — the "big" currency is always
     * the base, matching how sarafs actually quote ("1 USD = 72 AFN", never
     * "1 AFN = 0.0139 USD"). Priority: USD > AFN > PKR.
     */
    fun canonicalBase(a: String, b: String): String {
        val order = mapOf("USD" to 0, "AFN" to 1, "PKR" to 2)
        return if ((order[a] ?: 9) < (order[b] ?: 9)) a else b
    }

    /** Today's market sell rate for the canonical pair (base, quote). */
    fun marketRate(base: String, quote: String, rates: RateBook): Double? = when {
        base == "USD" && quote == "AFN" -> rates.pairs[RatePair.USD_AFN]?.sell
        base == "USD" && quote == "PKR" -> rates.pairs[RatePair.USD_PKR]?.sell
        base == "AFN" && quote == "PKR" -> {
            val usdPkr = rates.pairs[RatePair.USD_PKR]?.sell ?: return null
            val usdAfn = rates.pairs[RatePair.USD_AFN]?.sell ?: return null
            if (usdAfn > 0) usdPkr / usdAfn else null
        }
        else -> null
    }

    /**
     * Suggested manual conversion rate for a customer-entry conversion
     * ("1 [from] = N [to]"), using buy/sell sides the way the prototype does.
     */
    fun suggestedConversionRate(from: String, to: String, rates: RateBook): Double? {
        val usdAfn = rates.pairs[RatePair.USD_AFN] ?: return null
        val pkrAfn = rates.pairs[RatePair.PKR_AFN] ?: return null
        val usdPkr = rates.pairs[RatePair.USD_PKR] ?: return null
        return when (from to to) {
            "USD" to "AFN" -> usdAfn.sell
            "AFN" to "USD" -> if (usdAfn.buy > 0) 1 / usdAfn.buy else null
            "USD" to "PKR" -> usdPkr.sell
            "PKR" to "USD" -> if (usdPkr.buy > 0) 1 / usdPkr.buy else null
            "PKR" to "AFN" -> pkrAfn.sell
            "AFN" to "PKR" -> if (pkrAfn.buy > 0) 1 / pkrAfn.buy else null
            else -> null
        }
    }

    /**
     * Convert between the three ledger currencies using a manually-entered rate
     * sheet (settlement flow). Keys: "USD_AFN", "PKR_AFN", "USD_PKR".
     */
    fun convertWithManualRates(
        amount: Double,
        from: String,
        to: String,
        manualRates: Map<String, Double>,
    ): Double {
        if (from == to) return amount
        fun rate(key: String, fallback: Double) = manualRates[key]?.takeIf { it > 0 } ?: fallback
        return when (to) {
            "AFN" -> when (from) {
                "USD" -> amount * rate("USD_AFN", 0.0)
                "PKR" -> amount * rate("PKR_AFN", 0.0)
                else -> 0.0
            }
            "USD" -> when (from) {
                "AFN" -> amount / rate("USD_AFN", 1.0)
                "PKR" -> amount / rate("USD_PKR", 1.0)
                else -> 0.0
            }
            "PKR" -> when (from) {
                "USD" -> amount * rate("USD_PKR", 0.0)
                "AFN" -> amount / rate("PKR_AFN", 1.0)
                else -> 0.0
            }
            else -> 0.0
        }
    }
}

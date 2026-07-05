package com.daftar.app.domain.model

/** Buy/sell quote for one asset against AFN ("1 asset = N AFN"). */
data class Rate(
    val buy: Double,
    val sell: Double,
    val previousSell: Double,
    val deltaPercent: Double,
)

/** Legacy currency-pair keys kept in sync for the FX flows and rate sheet. */
enum class RatePair(val base: String, val quote: String) {
    USD_AFN("USD", "AFN"),
    PKR_AFN("PKR", "AFN"),
    USD_PKR("USD", "PKR");

    val label: String get() = "$base / $quote"
}

/**
 * All quotes known to the shop. [perAsset] is the source of truth for valuation;
 * [pairs] mirrors it for the classic three-pair rate sheet.
 */
data class RateBook(
    val perAsset: Map<String, Rate>,
    val pairs: Map<RatePair, Rate>,
) {
    fun assetRate(code: String): Rate? = perAsset[code]

    /** AFN value of one unit of [code]; AFN itself is 1. */
    fun sellRateToAfn(code: String): Double =
        if (code == "AFN") 1.0 else perAsset[code]?.sell ?: 0.0

    fun toAfn(code: String, amount: Double): Double = amount * sellRateToAfn(code)
}

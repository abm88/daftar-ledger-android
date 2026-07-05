package com.daftar.app.domain.model

/** Physical cash (and metal) actually sitting in the drawer, per asset code. */
data class CashDrawer(
    val balances: Map<String, Double>,
    val lastCountLabel: String,
) {
    fun balanceOf(code: String): Double = balances[code] ?: 0.0
}

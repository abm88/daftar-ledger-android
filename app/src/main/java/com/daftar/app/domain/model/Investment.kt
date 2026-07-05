package com.daftar.app.domain.model

enum class InvestmentType(val label: String, val pashtoLabel: String) {
    OPENING("Opening", "پرانیستل"),
    ADDITION("Addition", "زیاتول"),
    WITHDRAWAL("Withdrawal", "وتل");

    val isOutflow: Boolean get() = this == WITHDRAWAL
}

/** Owner equity movement: capital injected into or drawn out of the shop. */
data class Investment(
    val id: String,
    val timestampMillis: Long,
    val dateLabel: String,
    val assetCode: String,
    val amount: Double,
    val type: InvestmentType,
    val note: String? = null,
) {
    /** Signed effect on invested capital. */
    val signedAmount: Double get() = if (type.isOutflow) -amount else amount
}

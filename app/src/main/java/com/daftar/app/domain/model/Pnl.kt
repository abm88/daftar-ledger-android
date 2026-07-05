package com.daftar.app.domain.model

enum class LedgerPeriod(val label: String, val pashtoLabel: String) {
    TODAY("Today", "نن"),
    YESTERDAY("Yesterday", "پرون"),
    WEEK("Last 7 days", "وروستي ۷ ورځې"),
    MONTH("This month", "دا میاشت"),
    ALL("All time", "ټول وخت"),
}

enum class PnlSource { FX_TRADE, HAWALA_COMMISSION, REVALUATION }

/** One contributing line of the P&L report. All amounts are AFN. */
data class PnlItem(
    val timestampMillis: Long,
    val dateLabel: String,
    val label: String,
    val amountAfn: Double,
    val source: PnlSource,
    val partnerName: String? = null,
)

/** Aggregated profit & loss for a period. All amounts are AFN. */
data class PnlReport(
    val period: LedgerPeriod,
    val fxRealizedAfn: Double,
    val fxTradeCount: Int,
    val hawalaCommissionAfn: Double,
    val hawalaCount: Int,
    /** Unrealized rate-move P&L on drawer holdings; only computed for TODAY / ALL. */
    val unrealizedRevaluationAfn: Double,
    val includesRevaluation: Boolean,
    val items: List<PnlItem>,
) {
    val realizedTotalAfn: Double get() = fxRealizedAfn + hawalaCommissionAfn
    val grandTotalAfn: Double get() = realizedTotalAfn + unrealizedRevaluationAfn
}

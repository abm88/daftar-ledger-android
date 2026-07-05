package com.daftar.app.domain.model

enum class HawalaType {
    /** We sent money through the partner → partner owes us. */
    SEND,

    /** We paid out on the partner's behalf → we owe the partner. */
    RECEIVE,

    /** A reconciliation entry that offsets the open position; [Hawala.amount] holds the signed delta. */
    SETTLEMENT,
}

enum class HawalaStatus { PENDING, PAID }

enum class CommissionMode { PERCENT, FIXED }

/** Sentinel code carried by synthetic entries (opening balances, settlements). */
const val SYNTHETIC_CODE = "000000"

/**
 * A single hawala (money transfer) entry in a partner's ledger.
 *
 * Position convention (paid entries only):
 *  SEND → +amount, RECEIVE → −amount, SETTLEMENT → +amount (already signed).
 */
data class Hawala(
    val id: String,
    val type: HawalaType,
    val fromCity: City,
    val toCity: City,
    val senderName: String,
    val receiverName: String,
    val amount: Double,
    val currency: String,
    val commissionPercent: Double,
    val commissionMode: CommissionMode = CommissionMode.PERCENT,
    /** Absolute commission; for PERCENT-mode legacy entries this may be derived. */
    val commissionAmount: Double? = null,
    val pickupCode: String,
    val status: HawalaStatus,
    val timestampMillis: Long,
    val dateLabel: String,
    val note: String? = null,
    /** Set when the sender paid from their customer account rather than cash. */
    val senderCustomerId: String? = null,
) {
    val isSynthetic: Boolean get() = pickupCode == SYNTHETIC_CODE

    /** Signed effect on the partner position, or zero while pending. */
    val positionDelta: Double
        get() = if (status != HawalaStatus.PAID) 0.0 else when (type) {
            HawalaType.SEND -> amount
            HawalaType.RECEIVE -> -amount
            HawalaType.SETTLEMENT -> amount
        }

    val resolvedCommissionAmount: Double
        get() = commissionAmount ?: (amount * commissionPercent / 100.0)
}

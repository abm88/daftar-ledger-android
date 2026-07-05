package com.daftar.app.domain.model

enum class LedgerEntryKind { HAWALA, SETTLEMENT, CUSTOMER_TX, FX }

/**
 * One row of the unified activity feed (the "General Ledger"): every event across
 * the business, regardless of which sub-ledger it lives in.
 */
sealed interface LedgerEntry {
    val timestampMillis: Long
    val amount: Double
    val currency: String
    val kind: LedgerEntryKind

    /** +1 credit (in), −1 debit (out), 0 neutral. */
    val direction: Int

    data class HawalaEntry(val hawala: Hawala, val partner: Counterparty) : LedgerEntry {
        override val timestampMillis get() = hawala.timestampMillis
        override val amount get() = hawala.amount
        override val currency get() = hawala.currency
        override val kind get() = LedgerEntryKind.HAWALA
        override val direction get() = 0
        val isPending get() = hawala.status == HawalaStatus.PENDING
    }

    data class SettlementEntry(val hawala: Hawala, val partner: Counterparty) : LedgerEntry {
        override val timestampMillis get() = hawala.timestampMillis
        override val amount get() = kotlin.math.abs(hawala.amount)
        override val currency get() = hawala.currency
        override val kind get() = LedgerEntryKind.SETTLEMENT
        override val direction get() = if (hawala.amount >= 0) 1 else -1
    }

    data class CustomerTxEntry(val tx: CustomerTransaction, val customer: Customer) : LedgerEntry {
        override val timestampMillis get() = tx.timestampMillis
        override val amount get() = tx.amount
        override val currency get() = tx.currency
        override val kind get() = LedgerEntryKind.CUSTOMER_TX
        override val direction get() = if (tx.isCredit) 1 else -1
        val isHawalaLinked get() = tx.hawalaId != null

        private val CustomerTransaction.isCredit
            get() = type == CustomerTxType.DEPOSIT || type == CustomerTxType.CREDIT
    }

    data class FxEntry(val trade: FxTrade) : LedgerEntry {
        override val timestampMillis get() = trade.timestampMillis
        override val amount get() = trade.toAmount
        override val currency get() = trade.toCurrency
        override val kind get() = LedgerEntryKind.FX
        override val direction get() = 0
    }
}

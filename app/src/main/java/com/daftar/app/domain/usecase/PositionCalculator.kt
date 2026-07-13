package com.daftar.app.domain.usecase

import com.daftar.app.domain.model.Counterparty
import com.daftar.app.domain.model.Customer
import com.daftar.app.domain.model.CustomerTransaction
import com.daftar.app.domain.model.MoneyByCurrency
import javax.inject.Inject
import javax.inject.Singleton

/** A transaction paired with the account's running balance after it was applied. */
data class TxWithRunningBalance(
    val tx: CustomerTransaction,
    val balanceBefore: Double,
    val balanceAfter: Double,
)

/** Net position / balance arithmetic for partners and customers. */
@Singleton
class PositionCalculator @Inject constructor() {

    /**
     * Partner exposure per currency from paid entries.
     * Positive = they owe us (long), negative = we owe them (short).
     */
    fun partnerPosition(partner: Counterparty): MoneyByCurrency =
        partner.hawalas.fold(MoneyByCurrency()) { acc, h ->
            acc.plus(h.currency, h.positionDelta)
        }

    fun globalPosition(partners: List<Counterparty>): MoneyByCurrency =
        partners.fold(MoneyByCurrency()) { acc, p ->
            partnerPosition(p).amounts.entries.fold(acc) { a, (cur, amt) -> a.plus(cur, amt) }
        }

    /**
     * Customer balance per currency. Positive = funds on deposit (saraf owes
     * the customer), negative = customer owes the saraf.
     */
    fun customerBalance(customer: Customer): MoneyByCurrency =
        customer.transactions.fold(MoneyByCurrency()) { acc, tx ->
            acc.plus(tx.currency, tx.balanceDelta)
        }

    /**
     * Chronological running balances in each transaction's own currency.
     * v18 sorts by timestamp before accumulating, so backdated inserts still
     * produce correct balances regardless of storage order.
     */
    fun runningBalances(customer: Customer): List<TxWithRunningBalance> {
        val running = mutableMapOf<String, Double>()
        return customer.transactions.sortedBy { it.timestampMillis }.map { tx ->
            val before = running[tx.currency] ?: 0.0
            val after = before + tx.balanceDelta
            running[tx.currency] = after
            TxWithRunningBalance(tx, before, after)
        }
    }

    fun runningBalanceFor(customer: Customer, txId: String): TxWithRunningBalance? =
        runningBalances(customer).firstOrNull { it.tx.id == txId }
}

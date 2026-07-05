package com.daftar.app.domain.usecase

import com.daftar.app.domain.model.CustomerTxType
import com.daftar.app.domain.model.HawalaType
import com.daftar.app.domain.model.LedgerEntry
import com.daftar.app.domain.repository.CustomerRepository
import com.daftar.app.domain.repository.FxRepository
import com.daftar.app.domain.repository.PartnerRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * The unified activity feed ("General Ledger"): hawalas, settlements, customer
 * entries, and FX trades merged chronologically, newest first.
 */
@Singleton
class ActivityFeedBuilder @Inject constructor(
    private val partnerRepository: PartnerRepository,
    private val customerRepository: CustomerRepository,
    private val fxRepository: FxRepository,
) {
    fun observe(): Flow<List<LedgerEntry>> = combine(
        partnerRepository.partners,
        customerRepository.customers,
        fxRepository.trades,
    ) { partners, customers, trades ->
        val entries = mutableListOf<LedgerEntry>()

        partners.forEach { partner ->
            partner.hawalas.forEach { h ->
                entries += if (h.type == HawalaType.SETTLEMENT) {
                    LedgerEntry.SettlementEntry(h, partner)
                } else {
                    LedgerEntry.HawalaEntry(h, partner)
                }
            }
        }

        customers.forEach { customer ->
            customer.transactions
                .filter { it.type != CustomerTxType.OPENING } // opening balances are sentinels
                .forEach { entries += LedgerEntry.CustomerTxEntry(it, customer) }
        }

        trades.forEach { entries += LedgerEntry.FxEntry(it) }

        entries.sortedByDescending { it.timestampMillis }
    }
}

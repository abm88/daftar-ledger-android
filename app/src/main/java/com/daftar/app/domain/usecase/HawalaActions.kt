package com.daftar.app.domain.usecase

import com.daftar.app.domain.repository.CustomerRepository
import com.daftar.app.domain.repository.PartnerRepository
import com.daftar.app.domain.repository.RatesRepository
import javax.inject.Inject

/** Marks a pending hawala as paid out to the receiver. */
class MarkHawalaPaidUseCase @Inject constructor(
    private val partnerRepository: PartnerRepository,
) {
    suspend operator fun invoke(hawalaId: String) {
        partnerRepository.markHawalaPaid(hawalaId, "Just paid")
    }
}

/** Removes a customer ledger entry (used from the entry detail screen). */
class DeleteCustomerTransactionUseCase @Inject constructor(
    private val customerRepository: CustomerRepository,
) {
    suspend operator fun invoke(transactionId: String) {
        customerRepository.deleteTransaction(transactionId)
    }
}

/** Persists today's buy/sell quotes; the repository re-derives deltas and pair rates. */
class UpdateRatesUseCase @Inject constructor(
    private val ratesRepository: RatesRepository,
) {
    suspend operator fun invoke(quotes: Map<String, Pair<Double, Double>>) {
        val valid = quotes.filterValues { (buy, sell) -> buy > 0 && sell > 0 }
        if (valid.isNotEmpty()) ratesRepository.updateAssetRates(valid)
    }
}

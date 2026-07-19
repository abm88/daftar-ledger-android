package com.daftar.app.domain.usecase

import com.daftar.app.domain.repository.LedgerMutationRepository
import com.daftar.app.domain.repository.PartnerRepository
import javax.inject.Inject

/**
 * Zeros out a partner's open position by posting one signed SETTLEMENT entry per
 * open currency (delta = −position). The chosen settlement currency and manual
 * rates only affect what is physically handed over; the ledger records offsets
 * in each original currency.
 */
class SettlePartnerUseCase @Inject constructor(
    private val partnerRepository: PartnerRepository,
    private val positionCalculator: PositionCalculator,
    private val mutations: LedgerMutationRepository,
) {
    suspend operator fun invoke(partnerId: String, settleCurrency: String): Boolean {
        val partner = partnerRepository.partnerById(partnerId) ?: return false
        val position = positionCalculator.partnerPosition(partner)
        val open = position.openCurrencies()
        if (open.isEmpty()) return false

        return runCatching {
            mutations.settleCounterparty(partnerId, settleCurrency, "Settled in $settleCurrency @ rate sheet")
        }.isSuccess
    }
}

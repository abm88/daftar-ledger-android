package com.daftar.app.domain.usecase

import com.daftar.app.domain.repository.CashRepository
import com.daftar.app.domain.repository.CustomerRepository
import com.daftar.app.domain.repository.FxRepository
import com.daftar.app.domain.repository.InvestmentRepository
import com.daftar.app.domain.repository.PartnerRepository
import javax.inject.Inject

/**
 * Empties the daftar into a blank shop — no cash, no customers, no partners,
 * no trades, no investments. Rates and settings keep their defaults so a new
 * saraf can start trading right after Initial Setup.
 */
class ClearShopDataUseCase @Inject constructor(
    private val cashRepository: CashRepository,
    private val partnerRepository: PartnerRepository,
    private val customerRepository: CustomerRepository,
    private val fxRepository: FxRepository,
    private val investmentRepository: InvestmentRepository,
) {
    suspend operator fun invoke() {
        cashRepository.clearAll()
        partnerRepository.clearAll()
        customerRepository.clearAll()
        fxRepository.clearAll()
        investmentRepository.clearAll()
    }
}

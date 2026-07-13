package com.daftar.app.domain.usecase

import com.daftar.app.data.seed.SeedData
import com.daftar.app.domain.model.AssetCatalog
import com.daftar.app.domain.model.CashDrawer
import com.daftar.app.domain.model.LedgerSettings
import com.daftar.app.domain.repository.CashRepository
import com.daftar.app.domain.repository.CustomerRepository
import com.daftar.app.domain.repository.FxRepository
import com.daftar.app.domain.repository.InvestmentRepository
import com.daftar.app.domain.repository.PartnerRepository
import com.daftar.app.domain.repository.RatesRepository
import com.daftar.app.domain.repository.SettingsRepository
import javax.inject.Inject

/**
 * Empties the daftar into a blank shop — no cash, no customers, no partners,
 * no trades, no investments. Per v18 createBlankUserData this also resets the
 * rate book to factory defaults and the settings to only-default assets with
 * AFN reporting / USD trade currency, so nothing from a previous account
 * bleeds into a new one.
 */
class ClearShopDataUseCase @Inject constructor(
    private val cashRepository: CashRepository,
    private val partnerRepository: PartnerRepository,
    private val customerRepository: CustomerRepository,
    private val fxRepository: FxRepository,
    private val investmentRepository: InvestmentRepository,
    private val ratesRepository: RatesRepository,
    private val settingsRepository: SettingsRepository,
    private val seed: SeedData,
) {
    suspend operator fun invoke() {
        cashRepository.replaceAll(
            CashDrawer(
                balances = AssetCatalog.ALL.associate { it.code to 0.0 },
                lastCountLabel = "Not yet counted",
            ),
        )
        partnerRepository.clearAll()
        customerRepository.clearAll()
        fxRepository.clearAll()
        investmentRepository.clearAll()
        ratesRepository.replaceAll(seed.rateBook)
        settingsRepository.replaceSettings(LedgerSettings())
    }
}

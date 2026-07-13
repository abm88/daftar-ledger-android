package com.daftar.app.domain.usecase

import com.daftar.app.core.format.Formatters
import com.daftar.app.core.time.TimeProvider
import com.daftar.app.domain.model.AssetCatalog
import com.daftar.app.domain.model.Investment
import com.daftar.app.domain.model.InvestmentType
import com.daftar.app.domain.repository.CashRepository
import com.daftar.app.domain.repository.InvestmentRepository
import com.daftar.app.domain.repository.SettingsRepository
import javax.inject.Inject

/**
 * Commits the v18 setup wizard. Besides the opening amounts (drawer balance +
 * OPENING investment entry per asset), the wizard also chose which assets are
 * active and the default reporting/trade currencies — all three are saved
 * together, exactly like the prototype's save-initial-setup handler.
 */
class SaveInitialSetupUseCase @Inject constructor(
    private val cashRepository: CashRepository,
    private val investmentRepository: InvestmentRepository,
    private val settingsRepository: SettingsRepository,
    private val timeProvider: TimeProvider,
) {
    suspend operator fun invoke(
        startingAmounts: Map<String, Double>,
        enabledAssets: Map<String, Boolean> = emptyMap(),
        reportingCurrency: String? = null,
        tradeCurrency: String? = null,
    ): Boolean {
        val entered = startingAmounts.filterValues { it > 0.0 }
        if (entered.isEmpty()) return false

        // Wizard step 1: per-asset activation for every asset in the catalog.
        enabledAssets.forEach { (code, active) ->
            settingsRepository.setAssetActive(code, active)
        }
        // Wizard step 2: default currencies.
        reportingCurrency?.let { settingsRepository.setReportingCurrency(it) }
        tradeCurrency?.let { settingsRepository.setTradeCurrency(it) }

        val now = timeProvider.nowMillis()
        val dateLabel = Formatters.nowLabel(now)
        cashRepository.setBalances(entered, dateLabel)
        entered.forEach { (code, amount) ->
            investmentRepository.addInvestment(
                Investment(
                    id = "inv_${now}_$code",
                    timestampMillis = now,
                    dateLabel = dateLabel,
                    assetCode = code,
                    amount = amount,
                    type = InvestmentType.OPENING,
                    note = "Initial setup · ${AssetCatalog.byCode(code)?.name ?: code}",
                ),
            )
        }
        return true
    }
}

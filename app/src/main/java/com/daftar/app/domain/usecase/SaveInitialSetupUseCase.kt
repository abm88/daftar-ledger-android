package com.daftar.app.domain.usecase

import com.daftar.app.domain.repository.LedgerMutationRepository
import com.daftar.app.domain.repository.SettingsRepository
import javax.inject.Inject

/**
 * Commits all three setup steps through the backend's atomic setup transaction.
 * The server creates opening investments and drawer balances together.
 */
class SaveInitialSetupUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val mutations: LedgerMutationRepository,
) {
    suspend operator fun invoke(
        startingAmounts: Map<String, Double>,
        enabledAssets: Map<String, Boolean> = emptyMap(),
        reportingCurrency: String? = null,
        tradeCurrency: String? = null,
    ): Boolean {
        val entered = startingAmounts.filterValues { it > 0.0 }
        if (entered.isEmpty()) return false

        val current = settingsRepository.settings.value
        val active = if (enabledAssets.isEmpty()) {
            current.activeAssets().map { it.code }
        } else {
            enabledAssets.filterValues { it }.keys.toList()
        }
        return runCatching {
            mutations.completeSetup(
                activeAssets = active,
                reportingCurrency = reportingCurrency ?: current.reportingCurrency,
                tradeCurrency = tradeCurrency ?: current.tradeCurrency,
                amounts = entered,
            )
        }.isSuccess
    }
}

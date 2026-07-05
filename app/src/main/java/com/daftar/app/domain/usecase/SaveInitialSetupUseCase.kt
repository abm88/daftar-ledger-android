package com.daftar.app.domain.usecase

import com.daftar.app.core.format.Formatters
import com.daftar.app.core.time.TimeProvider
import com.daftar.app.domain.model.AssetCatalog
import com.daftar.app.domain.model.Investment
import com.daftar.app.domain.model.InvestmentType
import com.daftar.app.domain.repository.CashRepository
import com.daftar.app.domain.repository.InvestmentRepository
import javax.inject.Inject

/**
 * First-run (or fiscal reset) setup: each entered amount becomes both the
 * drawer balance and an OPENING investment entry, so starting capital is
 * visible in the equity log from day one.
 */
class SaveInitialSetupUseCase @Inject constructor(
    private val cashRepository: CashRepository,
    private val investmentRepository: InvestmentRepository,
    private val timeProvider: TimeProvider,
) {
    suspend operator fun invoke(startingAmounts: Map<String, Double>): Boolean {
        val entered = startingAmounts.filterValues { it > 0.0 }
        if (entered.isEmpty()) return false

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

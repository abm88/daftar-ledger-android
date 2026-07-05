package com.daftar.app.domain.usecase

import com.daftar.app.core.format.Formatters
import com.daftar.app.core.time.TimeProvider
import com.daftar.app.domain.repository.CashRepository
import javax.inject.Inject

/**
 * Applies a physical drawer count. Only the assets actually counted are
 * overwritten — blanks mean "not counted this time".
 */
class SaveCashCountUseCase @Inject constructor(
    private val cashRepository: CashRepository,
    private val timeProvider: TimeProvider,
) {
    suspend operator fun invoke(countedAmounts: Map<String, Double>): Boolean {
        val valid = countedAmounts.filterValues { it >= 0.0 }
        if (valid.isEmpty()) return false
        cashRepository.setBalances(valid, Formatters.nowLabel(timeProvider.nowMillis()))
        return true
    }
}

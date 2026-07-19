package com.daftar.app.domain.usecase

import com.daftar.app.domain.repository.LedgerMutationRepository
import javax.inject.Inject

/**
 * Applies a physical drawer count. Only the assets actually counted are
 * overwritten — blanks mean "not counted this time".
 */
class SaveCashCountUseCase @Inject constructor(
    private val mutations: LedgerMutationRepository,
) {
    suspend operator fun invoke(countedAmounts: Map<String, Double>): Boolean {
        val valid = countedAmounts.filterValues { it >= 0.0 }
        if (valid.isEmpty()) return false
        return runCatching { mutations.recordCashCount(valid) }.isSuccess
    }
}

package com.daftar.app.domain.usecase

import com.daftar.app.core.format.Formatters
import com.daftar.app.core.time.TimeProvider
import com.daftar.app.domain.model.Investment
import com.daftar.app.domain.model.InvestmentType
import com.daftar.app.domain.repository.LedgerMutationRepository
import javax.inject.Inject

/**
 * Records an owner equity movement. The physical money enters or leaves the
 * drawer at the same time, so the cash counter moves with it.
 */
class RecordInvestmentUseCase @Inject constructor(
    private val mutations: LedgerMutationRepository,
    private val timeProvider: TimeProvider,
) {
    suspend operator fun invoke(
        type: InvestmentType,
        assetCode: String,
        amount: Double,
        note: String,
    ): Investment? {
        if (amount <= 0) return null
        val now = timeProvider.nowMillis()
        val investment = Investment(
            id = "inv_$now",
            timestampMillis = now,
            dateLabel = Formatters.nowLabel(now),
            assetCode = assetCode,
            amount = amount,
            type = type,
            note = note.trim().ifEmpty { null },
        )
        return runCatching { mutations.createInvestment(investment) }.getOrNull()
    }
}

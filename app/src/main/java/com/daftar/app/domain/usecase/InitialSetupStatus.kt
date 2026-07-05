package com.daftar.app.domain.usecase

import com.daftar.app.domain.repository.CashRepository
import com.daftar.app.domain.repository.InvestmentRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Whether the shop is brand-new (no balances and no investments recorded). */
@Singleton
class InitialSetupStatus @Inject constructor(
    private val cashRepository: CashRepository,
    private val investmentRepository: InvestmentRepository,
) {
    fun isNeeded(): Flow<Boolean> = combine(
        cashRepository.drawer,
        investmentRepository.investments,
    ) { drawer, investments ->
        drawer.balances.values.all { it <= 0.0 } && investments.isEmpty()
    }
}

package com.daftar.app.data.repository

import com.daftar.app.data.seed.SeedData
import com.daftar.app.domain.model.Investment
import com.daftar.app.domain.repository.InvestmentRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Singleton
class InMemoryInvestmentRepository @Inject constructor(seed: SeedData) : InvestmentRepository {

    private val state = MutableStateFlow(seed.investments)
    override val investments: StateFlow<List<Investment>> = state.asStateFlow()

    override suspend fun addInvestment(investment: Investment) {
        state.update { it + investment }
    }

    override suspend fun clearAll() {
        state.value = emptyList()
    }
}

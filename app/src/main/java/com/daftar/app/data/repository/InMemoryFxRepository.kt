package com.daftar.app.data.repository

import com.daftar.app.data.seed.SeedData
import com.daftar.app.domain.model.FxTrade
import com.daftar.app.domain.repository.FxRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Singleton
class InMemoryFxRepository @Inject constructor(seed: SeedData) : FxRepository {

    private val state = MutableStateFlow(seed.fxTrades)
    override val trades: StateFlow<List<FxTrade>> = state.asStateFlow()

    override suspend fun addTrade(trade: FxTrade) {
        state.update { it + trade }
    }
}

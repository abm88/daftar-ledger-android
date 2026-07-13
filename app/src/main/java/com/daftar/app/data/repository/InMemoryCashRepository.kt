package com.daftar.app.data.repository

import com.daftar.app.data.seed.SeedData
import com.daftar.app.domain.model.CashDrawer
import com.daftar.app.domain.repository.CashRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Singleton
class InMemoryCashRepository @Inject constructor(seed: SeedData) : CashRepository {

    private val state = MutableStateFlow(seed.cashDrawer)
    override val drawer: StateFlow<CashDrawer> = state.asStateFlow()

    override suspend fun setBalances(balances: Map<String, Double>, lastCountLabel: String?) {
        state.update { drawer ->
            drawer.copy(
                balances = drawer.balances + balances,
                lastCountLabel = lastCountLabel ?: drawer.lastCountLabel,
            )
        }
    }

    override suspend fun adjustBalance(assetCode: String, delta: Double) {
        state.update { drawer ->
            val current = drawer.balances[assetCode] ?: 0.0
            drawer.copy(balances = drawer.balances + (assetCode to current + delta))
        }
    }

    override suspend fun clearAll() {
        // v18 createBlankUserData labels an untouched drawer "Not yet counted".
        state.update { drawer ->
            drawer.copy(balances = drawer.balances.mapValues { 0.0 }, lastCountLabel = "Not yet counted")
        }
    }

    override suspend fun replaceAll(drawer: CashDrawer) {
        state.value = drawer
    }
}

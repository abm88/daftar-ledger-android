package com.daftar.app.domain.repository

import com.daftar.app.domain.model.CashDrawer
import kotlinx.coroutines.flow.StateFlow

/** The physical cash drawer. */
interface CashRepository {
    val drawer: StateFlow<CashDrawer>

    /** Overwrite the given asset balances (used by cash count and initial setup). */
    suspend fun setBalances(balances: Map<String, Double>, lastCountLabel: String? = null)

    /** Apply a signed delta to one asset (FX legs, investments). */
    suspend fun adjustBalance(assetCode: String, delta: Double)
}

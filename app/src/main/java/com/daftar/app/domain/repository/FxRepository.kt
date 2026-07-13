package com.daftar.app.domain.repository

import com.daftar.app.domain.model.FxTrade
import kotlinx.coroutines.flow.StateFlow

/** Currency-exchange trade log. */
interface FxRepository {
    val trades: StateFlow<List<FxTrade>>

    suspend fun addTrade(trade: FxTrade)

    /** Remove all trades — a fresh account starts with a blank shop. */
    suspend fun clearAll()

    /** Swap in another account's trade log (per-user persistence restore). */
    suspend fun replaceAll(trades: List<FxTrade>)
}

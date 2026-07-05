package com.daftar.app.domain.repository

import com.daftar.app.domain.model.FxTrade
import kotlinx.coroutines.flow.StateFlow

/** Currency-exchange trade log. */
interface FxRepository {
    val trades: StateFlow<List<FxTrade>>

    suspend fun addTrade(trade: FxTrade)
}

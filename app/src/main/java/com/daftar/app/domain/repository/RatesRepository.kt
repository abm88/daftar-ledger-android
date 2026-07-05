package com.daftar.app.domain.repository

import com.daftar.app.domain.model.RateBook
import kotlinx.coroutines.flow.StateFlow

/** Buy/sell quotes for all assets against AFN, plus the legacy pair sheet. */
interface RatesRepository {
    val rateBook: StateFlow<RateBook>

    /** Replace buy/sell quotes per asset code; derived pairs are re-synced by the impl. */
    suspend fun updateAssetRates(quotes: Map<String, Pair<Double, Double>>)
}

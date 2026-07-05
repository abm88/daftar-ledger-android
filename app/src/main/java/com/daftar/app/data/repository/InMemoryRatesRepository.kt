package com.daftar.app.data.repository

import com.daftar.app.data.seed.SeedData
import com.daftar.app.domain.model.Rate
import com.daftar.app.domain.model.RateBook
import com.daftar.app.domain.model.RatePair
import com.daftar.app.domain.repository.RatesRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Singleton
class InMemoryRatesRepository @Inject constructor(seed: SeedData) : RatesRepository {

    private val state = MutableStateFlow(seed.rateBook)
    override val rateBook: StateFlow<RateBook> = state.asStateFlow()

    override suspend fun updateAssetRates(quotes: Map<String, Pair<Double, Double>>) {
        state.update { book ->
            val perAsset = book.perAsset.toMutableMap()
            quotes.forEach { (code, quote) ->
                val (buy, sell) = quote
                val previous = perAsset[code]?.sell ?: sell
                perAsset[code] = Rate(
                    buy = buy,
                    sell = sell,
                    previousSell = previous,
                    deltaPercent = if (previous > 0) (sell - previous) / previous * 100 else 0.0,
                )
            }

            // Keep the legacy pair sheet in sync with the per-asset quotes.
            val pairs = book.pairs.toMutableMap()
            perAsset["USD"]?.let { usd ->
                val prev = pairs[RatePair.USD_AFN]?.sell ?: usd.sell
                pairs[RatePair.USD_AFN] = usd.copy(previousSell = prev)
            }
            perAsset["PKR"]?.let { pkr ->
                val prev = pairs[RatePair.PKR_AFN]?.sell ?: pkr.sell
                pairs[RatePair.PKR_AFN] = pkr.copy(previousSell = prev)
            }
            val usd = perAsset["USD"]
            val pkr = perAsset["PKR"]
            if (usd != null && pkr != null && pkr.sell > 0 && pkr.buy > 0) {
                val prevCross = pairs[RatePair.USD_PKR]?.sell ?: (usd.sell / pkr.sell)
                val crossSell = usd.sell / pkr.sell
                pairs[RatePair.USD_PKR] = Rate(
                    buy = usd.buy / pkr.buy,
                    sell = crossSell,
                    previousSell = prevCross,
                    deltaPercent = if (prevCross > 0) (crossSell - prevCross) / prevCross * 100 else 0.0,
                )
            }
            RateBook(perAsset = perAsset, pairs = pairs)
        }
    }
}

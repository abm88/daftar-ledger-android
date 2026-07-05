package com.daftar.app.domain.usecase

import com.daftar.app.core.time.TimeProvider
import com.daftar.app.domain.model.FxSide
import com.daftar.app.domain.model.FxTrade
import com.daftar.app.domain.model.Rate
import com.daftar.app.domain.model.RateBook
import com.daftar.app.domain.model.RatePair
import org.junit.Assert.assertEquals
import org.junit.Test

class FxAnalyticsTest {

    private class FixedTime(private val now: Long) : TimeProvider {
        override fun nowMillis(): Long = now
    }

    private val analytics = FxAnalytics(FixedTime(1_000_000L))

    private val rates = RateBook(
        perAsset = mapOf(
            "USD" to Rate(71.2, 71.8, 71.5, 0.1),
            "PKR" to Rate(0.245, 0.252, 0.250, -0.5),
        ),
        pairs = mapOf(
            RatePair.USD_AFN to Rate(71.2, 71.8, 71.5, 0.1),
            RatePair.PKR_AFN to Rate(0.245, 0.252, 0.250, -0.5),
            RatePair.USD_PKR to Rate(283.5, 285.2, 283.0, 0.6),
        ),
    )

    private fun buy(usd: Double, afnCost: Double, ts: Long) = FxTrade(
        id = "b$ts", side = FxSide.BUY, fromCurrency = "AFN", toCurrency = "USD",
        fromAmount = afnCost, toAmount = usd, rate = afnCost / usd,
        timestampMillis = ts, dateLabel = "-",
    )

    @Test
    fun `average cost is the weighted mean of acquisitions`() {
        val trades = listOf(
            buy(usd = 1_000.0, afnCost = 70_000.0, ts = 1), // 70 AFN each
            buy(usd = 1_000.0, afnCost = 74_000.0, ts = 2), // 74 AFN each
        )
        assertEquals(72.0, analytics.averageCostAfn(trades, "USD", rates), 0.001)
    }

    @Test
    fun `sells release quantity at the running average without changing it`() {
        val trades = listOf(
            buy(usd = 2_000.0, afnCost = 140_000.0, ts = 1), // avg 70
            FxTrade(
                id = "s1", side = FxSide.SELL, fromCurrency = "USD", toCurrency = "AFN",
                fromAmount = 500.0, toAmount = 36_000.0, rate = 72.0,
                timestampMillis = 2, dateLabel = "-",
            ),
        )
        assertEquals(70.0, analytics.averageCostAfn(trades, "USD", rates), 0.001)
        val position = analytics.openPositions(trades, rates).first { it.currency == "USD" }
        assertEquals(1_500.0, position.quantity, 0.001)
        // Market value 1500 × 71.8 vs remaining cost 105,000
        assertEquals(1_500 * 71.8 - 105_000, position.unrealizedPnlAfn, 0.001)
    }

    @Test
    fun `realized pnl window sums only trades inside the window`() {
        val trades = listOf(
            FxTrade(
                id = "s1", side = FxSide.SELL, fromCurrency = "USD", toCurrency = "AFN",
                fromAmount = 1.0, toAmount = 72.0, rate = 72.0, realizedPnlAfn = 100.0,
                timestampMillis = 10, dateLabel = "-",
            ),
            FxTrade(
                id = "s2", side = FxSide.SELL, fromCurrency = "USD", toCurrency = "AFN",
                fromAmount = 1.0, toAmount = 72.0, rate = 72.0, realizedPnlAfn = 50.0,
                timestampMillis = 100, dateLabel = "-",
            ),
        )
        assertEquals(100.0, analytics.realizedPnlInWindow(trades, 0, 50), 0.001)
        assertEquals(150.0, analytics.realizedPnlInWindow(trades, 0, 101), 0.001)
    }
}

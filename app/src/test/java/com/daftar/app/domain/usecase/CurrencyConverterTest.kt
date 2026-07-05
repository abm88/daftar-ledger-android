package com.daftar.app.domain.usecase

import com.daftar.app.domain.model.LedgerSettings
import com.daftar.app.domain.model.Rate
import com.daftar.app.domain.model.RateBook
import com.daftar.app.domain.model.RatePair
import org.junit.Assert.assertEquals
import org.junit.Test

class CurrencyConverterTest {

    private val converter = CurrencyConverter()

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

    @Test
    fun `reporting conversion bridges through AFN`() {
        val settings = LedgerSettings(reportingCurrency = "USD")
        // 71,800 AFN at 71.8 sell = 1,000 USD
        assertEquals(1_000.0, converter.toReporting("AFN", 71_800.0, rates, settings), 0.001)
    }

    @Test
    fun `canonical base always quotes the bigger currency`() {
        assertEquals("USD", converter.canonicalBase("AFN", "USD"))
        assertEquals("USD", converter.canonicalBase("USD", "PKR"))
        assertEquals("AFN", converter.canonicalBase("PKR", "AFN"))
    }

    @Test
    fun `afn to pkr cross rate derives from the usd pairs`() {
        val cross = converter.marketRate("AFN", "PKR", rates)!!
        assertEquals(285.2 / 71.8, cross, 0.0001)
    }

    @Test
    fun `manual-rate settlement conversion matches the sheet`() {
        val manual = mapOf("USD_AFN" to 71.8, "PKR_AFN" to 0.252, "USD_PKR" to 285.2)
        assertEquals(71_800.0, converter.convertWithManualRates(1_000.0, "USD", "AFN", manual), 0.001)
        assertEquals(1_000.0, converter.convertWithManualRates(71_800.0, "AFN", "USD", manual), 0.001)
        assertEquals(285_200.0, converter.convertWithManualRates(1_000.0, "USD", "PKR", manual), 0.001)
    }
}

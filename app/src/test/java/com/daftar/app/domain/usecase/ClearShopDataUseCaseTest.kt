package com.daftar.app.domain.usecase

import com.daftar.app.core.time.TimeProvider
import com.daftar.app.data.repository.InMemoryCashRepository
import com.daftar.app.data.repository.InMemoryCustomerRepository
import com.daftar.app.data.repository.InMemoryFxRepository
import com.daftar.app.data.repository.InMemoryInvestmentRepository
import com.daftar.app.data.repository.InMemoryPartnerRepository
import com.daftar.app.data.repository.InMemoryRatesRepository
import com.daftar.app.data.repository.InMemorySettingsRepository
import com.daftar.app.data.repository.InMemoryTeamRepository
import com.daftar.app.data.seed.SeedData
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClearShopDataUseCaseTest {

    private val seed = SeedData(object : TimeProvider {
        override fun nowMillis(): Long = 0L
    })

    @Test
    fun `clearing empties every ledger into a blank shop`() = runTest {
        val partners = InMemoryPartnerRepository(seed)
        val customers = InMemoryCustomerRepository(seed)
        val fx = InMemoryFxRepository(seed)
        val investments = InMemoryInvestmentRepository(seed)
        val cash = InMemoryCashRepository(seed)
        val rates = InMemoryRatesRepository(seed)
        val settings = InMemorySettingsRepository()
        val team = InMemoryTeamRepository(seed)

        // Precondition: the seeded demo shop is not empty; dirty the settings/rates too.
        assertTrue(partners.partners.value.isNotEmpty())
        settings.setReportingCurrency("USD")
        settings.setAssetActive("EUR", true)
        rates.updateAssetRates(mapOf("USD" to (80.0 to 81.0)))

        ClearShopDataUseCase(cash, partners, customers, fx, investments, rates, settings, team, seed).invoke()

        assertTrue(partners.partners.value.isEmpty())
        assertTrue(customers.customers.value.isEmpty())
        assertTrue(fx.trades.value.isEmpty())
        assertTrue(investments.investments.value.isEmpty())
        assertTrue(team.members.value.isEmpty())
        assertTrue(team.expenses.value.isEmpty())
        assertTrue(cash.drawer.value.balances.values.all { it == 0.0 })
        // v18 createBlankUserData: drawer label, factory rates, default settings all reset.
        assertEquals("Not yet counted", cash.drawer.value.lastCountLabel)
        assertEquals(seed.rateBook, rates.rateBook.value)
        assertEquals("AFN", settings.settings.value.reportingCurrency)
        assertTrue(settings.settings.value.activeAssetOverrides.isEmpty())
    }
}

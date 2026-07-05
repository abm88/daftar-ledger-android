package com.daftar.app.di

import com.daftar.app.core.time.SystemTimeProvider
import com.daftar.app.core.time.TimeProvider
import com.daftar.app.data.repository.InMemoryCashRepository
import com.daftar.app.data.repository.InMemoryCustomerRepository
import com.daftar.app.data.repository.InMemoryFxRepository
import com.daftar.app.data.repository.InMemoryInvestmentRepository
import com.daftar.app.data.repository.InMemoryPartnerRepository
import com.daftar.app.data.repository.InMemoryRatesRepository
import com.daftar.app.data.repository.InMemorySettingsRepository
import com.daftar.app.data.seed.SeedData
import com.daftar.app.domain.repository.CashRepository
import com.daftar.app.domain.repository.CustomerRepository
import com.daftar.app.domain.repository.FxRepository
import com.daftar.app.domain.repository.InvestmentRepository
import com.daftar.app.domain.repository.PartnerRepository
import com.daftar.app.domain.repository.RatesRepository
import com.daftar.app.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Composition root. The in-memory repositories are bound behind the domain
 * interfaces so the future API-backed implementations swap in without touching
 * ViewModels or use cases.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds abstract fun partnerRepository(impl: InMemoryPartnerRepository): PartnerRepository
    @Binds abstract fun customerRepository(impl: InMemoryCustomerRepository): CustomerRepository
    @Binds abstract fun fxRepository(impl: InMemoryFxRepository): FxRepository
    @Binds abstract fun investmentRepository(impl: InMemoryInvestmentRepository): InvestmentRepository
    @Binds abstract fun cashRepository(impl: InMemoryCashRepository): CashRepository
    @Binds abstract fun ratesRepository(impl: InMemoryRatesRepository): RatesRepository
    @Binds abstract fun settingsRepository(impl: InMemorySettingsRepository): SettingsRepository
}

@Module
@InstallIn(SingletonComponent::class)
object CoreModule {

    @Provides
    @Singleton
    fun timeProvider(): TimeProvider = SystemTimeProvider()

    @Provides
    @Singleton
    fun seedData(timeProvider: TimeProvider): SeedData = SeedData(timeProvider)
}

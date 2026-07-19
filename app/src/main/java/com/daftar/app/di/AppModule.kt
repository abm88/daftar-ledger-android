package com.daftar.app.di

import android.content.Context
import android.content.ContentResolver
import android.content.SharedPreferences
import com.daftar.app.BuildConfig
import com.daftar.app.core.time.SystemTimeProvider
import com.daftar.app.core.time.TimeProvider
import com.daftar.app.data.repository.InMemoryCashRepository
import com.daftar.app.data.repository.InMemoryCustomerRepository
import com.daftar.app.data.repository.InMemoryFxRepository
import com.daftar.app.data.repository.InMemoryInvestmentRepository
import com.daftar.app.data.repository.InMemoryPartnerRepository
import com.daftar.app.data.repository.InMemoryRatesRepository
import com.daftar.app.data.repository.InMemorySettingsRepository
import com.daftar.app.data.repository.InMemoryTeamRepository
import com.daftar.app.data.remote.ApiClient
import com.daftar.app.data.remote.ApiLedgerMutationRepository
import com.daftar.app.data.remote.ApiDataSynchronizer
import com.daftar.app.data.remote.DaftarApi
import com.daftar.app.data.remote.RemoteAuthRepository
import com.daftar.app.data.remote.SharedPreferencesTokenStore
import com.daftar.app.data.seed.SeedData
import com.daftar.app.domain.repository.AuthRepository
import com.daftar.app.domain.repository.CashRepository
import com.daftar.app.domain.repository.CustomerRepository
import com.daftar.app.domain.repository.FxRepository
import com.daftar.app.domain.repository.InvestmentRepository
import com.daftar.app.domain.repository.LedgerMutationRepository
import com.daftar.app.domain.repository.LedgerRefreshRepository
import com.daftar.app.domain.repository.PartnerRepository
import com.daftar.app.domain.repository.RatesRepository
import com.daftar.app.domain.repository.SettingsRepository
import com.daftar.app.domain.repository.TeamRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Composition root. StateFlow repositories form the server-hydrated read cache;
 * authentication and writes use API-backed domain ports.
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
    @Binds abstract fun teamRepository(impl: InMemoryTeamRepository): TeamRepository
    @Binds abstract fun authRepository(impl: RemoteAuthRepository): AuthRepository
    @Binds abstract fun ledgerMutations(impl: ApiLedgerMutationRepository): LedgerMutationRepository
    @Binds abstract fun ledgerRefresh(impl: ApiDataSynchronizer): LedgerRefreshRepository
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

    /** Private prefs file holding only the bearer token and cached account identity. */
    @Provides
    @Singleton
    fun authPreferences(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences("daftar_auth", Context.MODE_PRIVATE)

    @Provides
    @Singleton
    fun contentResolver(@ApplicationContext context: Context): ContentResolver = context.contentResolver

    @Provides
    @Singleton
    fun apiClient(tokenStore: SharedPreferencesTokenStore): ApiClient =
        ApiClient(BuildConfig.DAFTAR_API_BASE_URL, tokenStore)

    @Provides
    @Singleton
    fun daftarApi(client: ApiClient): DaftarApi = DaftarApi(client)
}

package com.daftar.app.data.remote

import com.daftar.app.data.repository.InMemoryCashRepository
import com.daftar.app.data.repository.InMemoryCustomerRepository
import com.daftar.app.data.repository.InMemoryFxRepository
import com.daftar.app.data.repository.InMemoryInvestmentRepository
import com.daftar.app.data.repository.InMemoryPartnerRepository
import com.daftar.app.data.repository.InMemoryRatesRepository
import com.daftar.app.data.repository.InMemorySettingsRepository
import com.daftar.app.data.repository.InMemoryTeamRepository
import com.daftar.app.data.seed.SeedData
import com.daftar.app.domain.model.AssetCatalog
import com.daftar.app.domain.model.CashDrawer
import com.daftar.app.domain.model.LedgerSettings
import com.daftar.app.domain.repository.LedgerRefreshRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Hydrates the observable read cache. The backend owns persisted rows and
 * computed balances; repositories only adapt that state for the current UI.
 */
@Singleton
class ApiDataSynchronizer @Inject constructor(
    private val api: DaftarApi,
    private val cash: InMemoryCashRepository,
    private val partners: InMemoryPartnerRepository,
    private val customers: InMemoryCustomerRepository,
    private val fx: InMemoryFxRepository,
    private val investments: InMemoryInvestmentRepository,
    private val rates: InMemoryRatesRepository,
    private val settings: InMemorySettingsRepository,
    private val team: InMemoryTeamRepository,
    private val seed: SeedData,
) : LedgerRefreshRepository {
    override suspend fun refresh() = refreshAll()

    suspend fun refreshAll() = coroutineScope {
        val userCall = async { api.me() }
        val assetsCall = async { api.assets() }
        val ratesCall = async { api.rates() }
        val drawerCall = async { api.cashDrawer() }
        val counterpartiesCall = async { api.counterparties() }
        val customersCall = async { api.customers() }
        val tradesCall = async { api.fxTrades(limit = 200) }
        val investmentsCall = async { api.investments() }
        val settingsCall = async { api.settings() }
        val teamCall = async { api.teamMembers() }
        val expensesCall = async { api.expenses() }

        val user = userCall.await().user
        val remoteAssets = assetsCall.await().assets
        val remoteRates = ratesCall.await()
        val drawer = drawerCall.await()
        val remoteCounterparties = counterpartiesCall.await().counterparties
        val remoteCustomers = customersCall.await().customers
        val trades = tradesCall.await().items
        val investmentEntries = investmentsCall.await().entries
        val remoteSettings = settingsCall.await().settings
        val members = teamCall.await().members
        val expenses = expensesCall.await().expenses

        // Child ledgers are fetched only after their parents are known. Each
        // aggregate is independent, so those calls can still run concurrently.
        val partnerBooks = remoteCounterparties.map { value ->
            async {
                val book = api.counterpartyHawalas(value.id)
                value.toDomain(book.hawalas.map(HawalaDto::toDomain))
            }
        }.awaitAll().sortedBy { it.name.lowercase() }

        val customerBooks = remoteCustomers.mapIndexed { index, value ->
            async {
                val book = api.customerTransactions(value.id)
                value.toDomain(book.transactions.map(CustomerTransactionDto::toDomain), index)
            }
        }.awaitAll().sortedBy { it.name.lowercase() }

        partners.replaceAll(partnerBooks)
        customers.replaceAll(customerBooks)
        fx.replaceAll(trades.map(FxTradeDto::toDomain))
        investments.replaceAll(investmentEntries.map(InvestmentDto::toDomain))
        cash.replaceAll(drawer.toDomain())
        rates.replaceAll(remoteRates.toDomain())
        team.replaceAll(members.map(TeamMemberDto::toDomain), expenses.map(ExpenseDto::toDomain))
        settings.replaceSettings(
            remoteSettings.toDomain(
                activeAssets = remoteAssets.associate { it.code to it.active },
                ledgerTableView = settings.settings.value.ledgerTableView,
            ),
        )
        settings.replaceShopProfile(user.toShopProfile())
    }

    suspend fun clearLocal() {
        cash.replaceAll(
            CashDrawer(
                balances = AssetCatalog.ALL.associate { it.code to 0.0 },
                lastCountLabel = "Not yet counted",
            ),
        )
        partners.clearAll()
        customers.clearAll()
        fx.clearAll()
        investments.clearAll()
        rates.replaceAll(seed.rateBook)
        settings.replaceSettings(LedgerSettings())
        team.clearAll()
    }
}

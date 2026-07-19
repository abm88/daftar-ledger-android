package com.daftar.app.domain.repository

import com.daftar.app.domain.model.Counterparty
import com.daftar.app.domain.model.Customer
import com.daftar.app.domain.model.CustomerTransaction
import com.daftar.app.domain.model.Expense
import com.daftar.app.domain.model.FxTrade
import com.daftar.app.domain.model.Hawala
import com.daftar.app.domain.model.Investment
import com.daftar.app.domain.model.TeamMember

/**
 * Business-write port. The data layer implements it with transactional backend
 * endpoints and refreshes the read repositories after each successful mutation.
 */
interface LedgerMutationRepository {
    suspend fun completeSetup(
        activeAssets: List<String>,
        reportingCurrency: String,
        tradeCurrency: String,
        amounts: Map<String, Double>,
    )

    suspend fun createCounterparty(
        counterparty: Counterparty,
        openingBalances: Map<String, Double>,
    ): Counterparty

    suspend fun createCustomer(
        customer: Customer,
        openingBalances: Map<String, Double>,
    ): Customer

    suspend fun issueHawala(
        counterpartyId: String,
        hawala: Hawala,
        senderMode: String?,
        commissionFixed: Double?,
    ): Hawala

    suspend fun markHawalaPaid(id: String, payoutCustomerId: String? = null)
    suspend fun cancelHawala(id: String)
    suspend fun settleCounterparty(id: String, currency: String, note: String? = null)

    suspend fun createCustomerTransaction(customerId: String, transaction: CustomerTransaction): CustomerTransaction
    suspend fun deleteCustomerTransaction(id: String)
    suspend fun createFxTrade(trade: FxTrade): FxTrade
    suspend fun createInvestment(investment: Investment): Investment
    suspend fun recordCashCount(counts: Map<String, Double>)
    suspend fun updateRates(quotes: Map<String, Pair<Double, Double>>)
    suspend fun setAssetActive(code: String, active: Boolean)
    suspend fun updateSettings(reportingCurrency: String? = null, tradeCurrency: String? = null)
    suspend fun createTeamMember(member: TeamMember): TeamMember
    suspend fun createExpense(expense: Expense): Expense
}

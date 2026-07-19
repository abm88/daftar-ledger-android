package com.daftar.app.data.remote

import com.daftar.app.domain.model.CommissionMode
import com.daftar.app.domain.model.Counterparty
import com.daftar.app.domain.model.Customer
import com.daftar.app.domain.model.CustomerTransaction
import com.daftar.app.domain.model.Expense
import com.daftar.app.domain.model.FxTrade
import com.daftar.app.domain.model.Hawala
import com.daftar.app.domain.model.HawalaType
import com.daftar.app.domain.model.Investment
import com.daftar.app.domain.model.TeamMember
import com.daftar.app.domain.repository.LedgerMutationRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiLedgerMutationRepository @Inject constructor(
    private val api: DaftarApi,
    private val sync: ApiDataSynchronizer,
    private val photos: PhotoPayloadEncoder,
) : LedgerMutationRepository {
    override suspend fun completeSetup(
        activeAssets: List<String>,
        reportingCurrency: String,
        tradeCurrency: String,
        amounts: Map<String, Double>,
    ) {
        api.completeSetup(CompleteSetupRequest(activeAssets, reportingCurrency, tradeCurrency, amounts))
        sync.refreshAll()
    }

    override suspend fun createCounterparty(
        counterparty: Counterparty,
        openingBalances: Map<String, Double>,
    ): Counterparty {
        val response = api.createCounterparty(
            CreateCounterpartyRequest(
                name = counterparty.name,
                shortName = counterparty.shortName,
                initial = counterparty.initial,
                phone = counterparty.phone.takeUnless { it == "—" },
                cityCode = counterparty.city.code,
                tier = counterparty.tier.name.lowercase(),
                openingBalances = openingBalances.filterValues { it != 0.0 }.ifEmpty { null },
            ),
        )
        sync.refreshAll()
        return response.counterparty.toDomain()
    }

    override suspend fun createCustomer(
        customer: Customer,
        openingBalances: Map<String, Double>,
    ): Customer {
        val response = api.createCustomer(
            CreateCustomerRequest(
                name = customer.name,
                shortName = customer.shortName,
                initial = customer.initial,
                phone = customer.phone.takeUnless { it == "—" },
                cityCode = customer.city.code,
                notes = customer.notes,
                openingBalances = openingBalances.filterValues { it > 0.0 }.ifEmpty { null },
            ),
        )
        sync.refreshAll()
        return response.customer.toDomain(fallbackColorIndex = customer.colorIndex)
    }

    override suspend fun issueHawala(
        counterpartyId: String,
        hawala: Hawala,
        senderMode: String?,
        commissionFixed: Double?,
    ): Hawala {
        val isReceive = hawala.type == HawalaType.RECEIVE
        val response = api.issueHawala(
            IssueHawalaRequest(
                type = when (hawala.type) {
                    HawalaType.SEND -> "send"
                    HawalaType.RECEIVE -> "recv"
                    HawalaType.SETTLEMENT -> error("Settlement uses the settlement endpoint")
                },
                counterpartyId = counterpartyId,
                fromCity = hawala.fromCity.code,
                toCity = hawala.toCity.code,
                amount = hawala.amount,
                currency = hawala.currency,
                code = hawala.pickupCode.takeIf { isReceive },
                receiverName = hawala.receiverName,
                senderMode = senderMode.takeUnless { isReceive },
                senderName = hawala.senderName.takeUnless { senderMode == "account" },
                senderCustomerId = hawala.senderCustomerId,
                commissionMode = hawala.commissionMode.name.lowercase(),
                commissionPct = hawala.commissionPercent.takeIf { hawala.commissionMode == CommissionMode.PERCENT },
                commissionFixed = commissionFixed.takeIf { hawala.commissionMode == CommissionMode.FIXED },
                note = hawala.note,
            ),
        )
        sync.refreshAll()
        return response.hawala.toDomain()
    }

    override suspend fun markHawalaPaid(id: String, payoutCustomerId: String?) {
        api.markHawalaPaid(
            id = id,
            method = if (payoutCustomerId == null) "cash" else "account",
            payoutCustomerId = payoutCustomerId,
        )
        sync.refreshAll()
    }

    override suspend fun cancelHawala(id: String) {
        api.cancelHawala(id)
        sync.refreshAll()
    }

    override suspend fun settleCounterparty(id: String, currency: String, note: String?) {
        api.settleCounterparty(id, currency, note)
        sync.refreshAll()
    }

    override suspend fun createCustomerTransaction(
        customerId: String,
        transaction: CustomerTransaction,
    ): CustomerTransaction {
        val conversion = transaction.conversion
        val response = api.createCustomerTransaction(
            customerId,
            CreateTransactionRequest(
                type = transaction.type.name.lowercase(),
                amount = conversion?.receivedAmount ?: transaction.amount,
                currency = conversion?.receivedCurrency ?: transaction.currency,
                note = transaction.note,
                conversion = conversion?.let { TransactionConversionRequest(it.creditedCurrency, it.rate) },
                photos = photos.encode(transaction.photoUris).ifEmpty { null },
            ),
        )
        sync.refreshAll()
        return response.transaction.toDomain()
    }

    override suspend fun deleteCustomerTransaction(id: String) {
        api.deleteTransaction(id)
        sync.refreshAll()
    }

    override suspend fun createFxTrade(trade: FxTrade): FxTrade {
        val response = api.createFxTrade(
            CreateFxTradeRequest(
                fromCurrency = trade.fromCurrency,
                toCurrency = trade.toCurrency,
                fromAmount = trade.fromAmount,
                rate = trade.rate,
                note = trade.note,
            ),
        )
        sync.refreshAll()
        return response.trade.toDomain()
    }

    override suspend fun createInvestment(investment: Investment): Investment {
        val response = api.createInvestment(
            CreateInvestmentRequest(
                assetCode = investment.assetCode,
                amount = investment.amount,
                type = investment.type.name.lowercase(),
                note = investment.note,
            ),
        )
        sync.refreshAll()
        return response.investment.toDomain()
    }

    override suspend fun recordCashCount(counts: Map<String, Double>) {
        api.recordCashCount(counts)
        sync.refreshAll()
    }

    override suspend fun updateRates(quotes: Map<String, Pair<Double, Double>>) {
        api.updateRates(quotes.mapValues { (_, value) -> RateValueRequest(value.first, value.second) })
        sync.refreshAll()
    }

    override suspend fun setAssetActive(code: String, active: Boolean) {
        api.setAssetActivation(code, active)
        sync.refreshAll()
    }

    override suspend fun updateSettings(reportingCurrency: String?, tradeCurrency: String?) {
        api.updateSettings(reportingCurrency, tradeCurrency)
        sync.refreshAll()
    }

    override suspend fun createTeamMember(member: TeamMember): TeamMember {
        val response = api.createTeamMember(
            CreateTeamMemberRequest(member.name, member.role, member.phone, member.initial),
        )
        sync.refreshAll()
        return response.member.toDomain()
    }

    override suspend fun createExpense(expense: Expense): Expense {
        val response = api.createExpense(
            CreateExpenseRequest(
                teamMemberId = expense.teamMemberId,
                amount = expense.amount,
                currency = expense.currency,
                note = expense.note,
            ),
        )
        sync.refreshAll()
        return response.expense.toDomain()
    }
}

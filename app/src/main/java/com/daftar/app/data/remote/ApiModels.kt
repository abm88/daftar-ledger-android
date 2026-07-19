package com.daftar.app.data.remote

/** Wire-only models matching the backend README. Domain models stay HTTP-agnostic. */
data class ValidationDetailDto(val path: String, val message: String)
data class ErrorPayloadDto(val message: String, val details: List<ValidationDetailDto>? = null)
data class ErrorEnvelopeDto(val error: ErrorPayloadDto)

data class HealthDto(val status: String, val database: String)
data class MessageResponse(val message: String)
data class PaginationDto(
    val total: Int,
    val limit: Int,
    val offset: Int,
    val hasMore: Boolean,
)

data class UserDto(
    val id: String,
    val email: String,
    val phone: String? = null,
    val name: String,
    val shopName: String? = null,
    val cityCode: String? = null,
    val registrationNo: String? = null,
    val createdAt: String,
    val updatedAt: String? = null,
)
data class AuthResponse(val user: UserDto, val token: String)
data class UserResponse(val user: UserDto)

data class CityDto(val code: String, val name: String, val color: String, val sortOrder: Int)
data class CitiesResponse(val cities: List<CityDto>)

data class AssetDto(
    val code: String,
    val type: String,
    val name: String,
    val pashtoName: String,
    val symbol: String,
    val decimals: Int,
    val emoji: String,
    val isBase: Boolean,
    val isDefault: Boolean,
    val sortOrder: Int,
    val active: Boolean,
    val tolaGrams: Double? = null,
)
data class AssetsResponse(val assets: List<AssetDto>)
data class AssetResponse(val asset: AssetDto)

data class RateDto(
    val assetCode: String,
    val buy: Double,
    val sell: Double,
    val prevSell: Double,
    val deltaPct: Double,
    val updatedAt: String? = null,
)
data class CrossRateDto(val pair: String, val buy: Double, val sell: Double)
data class RatesResponse(val rates: List<RateDto>, val crosses: List<CrossRateDto>? = null)
data class RateHistoryDto(val assetCode: String, val buy: Double, val sell: Double, val recordedAt: String)
data class RateHistoryResponse(val history: List<RateHistoryDto>)

data class CashDrawerItemDto(
    val assetCode: String,
    val type: String,
    val name: String,
    val symbol: String,
    val decimals: Int,
    val balance: Double,
    val tola: Double? = null,
    val afnValue: Double,
    val reportingValue: Double,
    val revaluationAfn: Double,
)
data class CashDrawerTotalsDto(
    val afn: Double,
    val reporting: Double,
    val reportingCurrency: String,
    val revaluationAfn: Double,
)
data class CashDrawerResponse(
    val items: List<CashDrawerItemDto>,
    val totals: CashDrawerTotalsDto,
    val lastCountAt: String? = null,
)
data class CashMovementDto(val assetCode: String, val inflow: Double, val outflow: Double, val net: Double)
data class CashMovementResponse(val movement: List<CashMovementDto>)

data class SettingsDto(
    val userId: String? = null,
    val reportingCurrency: String,
    val tradeCurrency: String,
    val lastCashCountAt: String? = null,
    val updatedAt: String? = null,
)
data class SettingsResponse(val settings: SettingsDto)
data class SetupStatusResponse(val setupNeeded: Boolean)
data class CompleteSetupResponse(
    val setupNeeded: Boolean,
    val settings: SettingsDto,
    val assets: List<AssetDto>,
    val drawer: CashDrawerResponse,
)

data class CounterpartyDto(
    val id: String,
    val userId: String? = null,
    val name: String,
    val shortName: String? = null,
    val initial: String? = null,
    val phone: String? = null,
    val cityCode: String,
    val tier: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val positions: Map<String, Double>? = null,
    val hawalaCount: Int? = null,
)
data class CounterpartiesResponse(val counterparties: List<CounterpartyDto>)
data class CounterpartyResponse(val counterparty: CounterpartyDto)

data class HawalaDto(
    val id: String,
    val userId: String? = null,
    val counterpartyId: String,
    val type: String,
    val fromCity: String,
    val toCity: String,
    val senderName: String,
    val receiverName: String,
    val amount: Double,
    val currency: String,
    val commissionMode: String,
    val commissionPct: Double,
    val commissionAmount: Double,
    val code: String,
    val status: String,
    val senderCustomerId: String? = null,
    val isOpening: Boolean,
    val note: String? = null,
    val payoutMethod: String? = null,
    val payoutCustomerId: String? = null,
    val createdAt: String,
    val paidAt: String? = null,
    val counterpartyName: String? = null,
    val counterpartyShortName: String? = null,
    val counterpartyCity: String? = null,
)
data class HawalasResponse(val hawalas: List<HawalaDto>)
data class PaginatedHawalasResponse(val items: List<HawalaDto>, val pagination: PaginationDto)
data class HawalaResponse(val hawala: HawalaDto)
data class NextCodeResponse(val code: String)
data class CancelHawalaResponse(val cancelled: Boolean, val id: String, val reversedTransactions: Int)
data class SettledPositionDto(val currency: String, val clearedPosition: Double)
data class SettlementResponse(val counterpartyId: String, val settled: List<SettledPositionDto>)
data class CounterpartyStatementResponse(
    val counterparty: CounterpartyDto,
    val entries: List<HawalaDto>,
    val positions: Map<String, Double>,
    val generatedAt: String,
)

data class ConversionDto(
    val receivedAmount: Double,
    val receivedCurrency: String,
    val rate: Double,
    val creditedAmount: Double,
    val creditedCurrency: String,
)
data class CustomerTransactionDto(
    val id: String,
    val customerId: String,
    val type: String,
    val amount: Double,
    val currency: String,
    val note: String? = null,
    val hawalaId: String? = null,
    val conversion: ConversionDto? = null,
    val photos: List<String>? = null,
    val createdAt: String,
    val customerName: String? = null,
    val customerShortName: String? = null,
    val customerCity: String? = null,
    val balanceBefore: Double? = null,
    val balanceAfter: Double? = null,
)
data class CustomerDto(
    val id: String,
    val userId: String? = null,
    val name: String,
    val shortName: String? = null,
    val initial: String? = null,
    val phone: String? = null,
    val cityCode: String,
    val colorIdx: Int? = null,
    val notes: String? = null,
    val openedAt: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val balances: Map<String, Double>? = null,
    val transactionCount: Int? = null,
)
data class CustomerHoldingDto(val deposits: Double, val advances: Double, val net: Double)
data class CustomerStatusCountsDto(val withDeposits: Int, val withAdvances: Int, val settled: Int)
data class CustomersSummaryDto(
    val holdings: Map<String, CustomerHoldingDto>,
    val statusCounts: CustomerStatusCountsDto,
)
data class CustomersResponse(
    val customers: List<CustomerDto>,
    val summary: CustomersSummaryDto? = null,
    val total: Int? = null,
)
data class CustomerResponse(val customer: CustomerDto)
data class TransactionsResponse(val transactions: List<CustomerTransactionDto>)
data class TransactionResponse(val transaction: CustomerTransactionDto)
data class DeletedTransactionResponse(val deleted: Boolean, val customerId: String)
data class ReceiptResponse(val receipt: String, val transaction: CustomerTransactionDto)
data class CustomerStatementTotalsDto(val credits: Double, val debits: Double)
data class CustomerStatementResponse(
    val customer: CustomerDto,
    val entries: List<CustomerTransactionDto>,
    val closingBalances: Map<String, Double>,
    val totals: Map<String, CustomerStatementTotalsDto>,
    val generatedAt: String,
)

data class TeamMemberDto(
    val id: String,
    val name: String,
    val role: String,
    val phone: String? = null,
    val initial: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val expenses: List<ExpenseDto>? = null,
    val expenseCount: Int? = null,
    val expenseTotalReporting: Double? = null,
    val reportingCurrency: String? = null,
)
data class TeamMembersResponse(
    val members: List<TeamMemberDto>,
    val reportingCurrency: String? = null,
    val total: Int? = null,
)
data class TeamMemberResponse(val member: TeamMemberDto)
data class ExpenseDto(
    val id: String,
    val teamMemberId: String,
    val teamMemberName: String? = null,
    val teamMemberRole: String? = null,
    val amount: Double,
    val currency: String,
    val note: String? = null,
    val createdAt: String,
    val ts: Double? = null,
    val date: String? = null,
)
data class ExpensesResponse(val expenses: List<ExpenseDto>)
data class ExpenseResponse(val expense: ExpenseDto)

data class FxTradeDto(
    val id: String,
    val userId: String? = null,
    val side: String,
    val fromCurrency: String,
    val toCurrency: String,
    val fromAmount: Double,
    val toAmount: Double,
    val rate: Double,
    val fromAfnValue: Double? = null,
    val toAfnValue: Double? = null,
    val realizedPl: Double? = null,
    val note: String? = null,
    val createdAt: String,
    val pairBase: String? = null,
)
data class FxTradeResponse(val trade: FxTradeDto)
data class FxTradesResponse(val items: List<FxTradeDto>, val pagination: PaginationDto)
data class FxPositionDto(
    val currency: String,
    val qty: Double,
    val avgCostAfn: Double,
    val marketRateAfn: Double,
    val marketValueAfn: Double,
    val unrealizedPlAfn: Double,
    val totalCostAfn: Double,
)
data class FxPositionsResponse(val positions: List<FxPositionDto>)

data class InvestmentDto(
    val id: String,
    val userId: String? = null,
    val assetCode: String,
    val amount: Double,
    val type: String,
    val note: String? = null,
    val createdAt: String,
)
data class InvestmentResponse(val investment: InvestmentDto)
data class InvestmentAssetSummaryDto(val invested: Double, val withdrawn: Double, val net: Double, val count: Int)
data class InvestmentTotalsDto(val netReporting: Double, val reportingCurrency: String)
data class InvestmentEquityDto(
    val currentReporting: Double,
    val netReturnReporting: Double,
    val netReturnPct: Double,
    val reportingCurrency: String,
)
data class InvestmentsResponse(
    val entries: List<InvestmentDto>,
    val perAsset: Map<String, InvestmentAssetSummaryDto>,
    val totals: InvestmentTotalsDto,
    val equity: InvestmentEquityDto? = null,
)

// Request bodies. Nullable fields are omitted by Gson, matching partial-update semantics.
data class RegisterRequest(
    val email: String,
    val password: String,
    val name: String,
    val phone: String? = null,
    val shopName: String? = null,
    val cityCode: String? = null,
    val registrationNo: String? = null,
)
data class LoginRequest(val email: String, val password: String)
data class UpdateProfileRequest(
    val name: String? = null,
    val shopName: String? = null,
    val cityCode: String? = null,
    val registrationNo: String? = null,
    val email: String? = null,
    val phone: String? = null,
)
data class ChangePasswordRequest(val currentPassword: String, val newPassword: String)
data class AssetActivationRequest(val active: Boolean)
data class RateValueRequest(val buy: Double, val sell: Double)
data class UpdateRatesRequest(val rates: Map<String, RateValueRequest>)
data class CashCountRequest(val counts: Map<String, Double>)
data class InitialCashSetupRequest(val amounts: Map<String, Double>)
data class CompleteSetupRequest(
    val activeAssets: List<String>,
    val reportingCurrency: String,
    val tradeCurrency: String,
    val amounts: Map<String, Double>,
)
data class CreateCounterpartyRequest(
    val name: String,
    val shortName: String?,
    val initial: String?,
    val phone: String?,
    val cityCode: String,
    val tier: String?,
    val openingBalances: Map<String, Double>?,
)
data class UpdateCounterpartyRequest(
    val name: String? = null,
    val shortName: String? = null,
    val initial: String? = null,
    val phone: String? = null,
    val cityCode: String? = null,
    val tier: String? = null,
)
data class SettleRequest(val settleCurrency: String? = null, val note: String? = null)
data class IssueHawalaRequest(
    val type: String,
    val counterpartyId: String,
    val fromCity: String? = null,
    val toCity: String? = null,
    val amount: Double,
    val currency: String,
    val code: String? = null,
    val receiverName: String,
    val senderMode: String? = null,
    val senderName: String? = null,
    val senderCustomerId: String? = null,
    val commissionMode: String = "percent",
    val commissionPct: Double? = null,
    val commissionFixed: Double? = null,
    val note: String? = null,
)
data class MarkHawalaPaidRequest(val method: String? = null, val payoutCustomerId: String? = null)
data class CreateCustomerRequest(
    val name: String,
    val shortName: String?,
    val initial: String?,
    val phone: String?,
    val cityCode: String,
    val notes: String?,
    val openingBalances: Map<String, Double>?,
)
data class UpdateCustomerRequest(
    val name: String? = null,
    val shortName: String? = null,
    val initial: String? = null,
    val phone: String? = null,
    val cityCode: String? = null,
    val notes: String? = null,
)
data class TransactionConversionRequest(val toCurrency: String, val rate: Double)
data class CreateTransactionRequest(
    val type: String,
    val amount: Double,
    val currency: String,
    val note: String? = null,
    val conversion: TransactionConversionRequest? = null,
    val photos: List<String>? = null,
    val photo: String? = null,
)
data class CreateTeamMemberRequest(val name: String, val role: String?, val phone: String?, val initial: String?)
data class UpdateTeamMemberRequest(
    val name: String? = null,
    val role: String? = null,
    val phone: String? = null,
    val initial: String? = null,
)
data class CreateExpenseRequest(val teamMemberId: String, val amount: Double, val currency: String, val note: String?)
data class CreateFxTradeRequest(
    val fromCurrency: String,
    val toCurrency: String,
    val fromAmount: Double,
    val rate: Double,
    val note: String?,
)
data class CreateInvestmentRequest(val assetCode: String, val amount: Double, val type: String, val note: String?)
data class UpdateSettingsRequest(val reportingCurrency: String? = null, val tradeCurrency: String? = null)

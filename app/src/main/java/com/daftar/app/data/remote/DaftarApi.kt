package com.daftar.app.data.remote

import com.google.gson.JsonObject

/** Typed facade for every endpoint in the backend README. Paths never leak into UI code. */
class DaftarApi(private val client: ApiClient) {
    private val prefix = "api/v1"

    // Health and authentication
    suspend fun health(): HealthDto = get("health", HealthDto::class.java, authenticated = false, versioned = false)
    suspend fun register(body: RegisterRequest): AuthResponse =
        sendBody(HttpMethod.POST, "auth/register", body, AuthResponse::class.java, authenticated = false)
    suspend fun login(body: LoginRequest): AuthResponse =
        sendBody(HttpMethod.POST, "auth/login", body, AuthResponse::class.java, authenticated = false)
    suspend fun logout(): MessageResponse = sendBody(HttpMethod.POST, "auth/logout", null, MessageResponse::class.java)
    suspend fun me(): UserResponse = get("auth/me", UserResponse::class.java)
    suspend fun updateProfile(body: UpdateProfileRequest): UserResponse =
        sendBody(HttpMethod.PUT, "auth/me", body, UserResponse::class.java)
    suspend fun changePassword(body: ChangePasswordRequest): MessageResponse =
        sendBody(HttpMethod.PUT, "auth/me/password", body, MessageResponse::class.java)

    // Setup, reference data, assets, and rates
    suspend fun setupStatus(): SetupStatusResponse = get("setup/status", SetupStatusResponse::class.java)
    suspend fun completeSetup(body: CompleteSetupRequest): CompleteSetupResponse =
        sendBody(HttpMethod.POST, "setup", body, CompleteSetupResponse::class.java)
    suspend fun cities(): CitiesResponse = get("cities", CitiesResponse::class.java, authenticated = false)
    suspend fun assets(): AssetsResponse = get("assets", AssetsResponse::class.java)
    suspend fun setAssetActivation(code: String, active: Boolean): AssetResponse =
        sendBody(HttpMethod.PATCH, "assets/$code/activation", AssetActivationRequest(active), AssetResponse::class.java)
    suspend fun rates(): RatesResponse = get("rates", RatesResponse::class.java)
    suspend fun updateRates(rates: Map<String, RateValueRequest>): RatesResponse =
        sendBody(HttpMethod.PUT, "rates", UpdateRatesRequest(rates), RatesResponse::class.java)
    suspend fun rateHistory(asset: String? = null, limit: Int? = null): RateHistoryResponse =
        get("rates/history", RateHistoryResponse::class.java, query = query("asset" to asset, "limit" to limit))

    // Cash drawer
    suspend fun cashDrawer(): CashDrawerResponse = get("cash-drawer", CashDrawerResponse::class.java)
    suspend fun recordCashCount(counts: Map<String, Double>): CashDrawerResponse =
        sendBody(HttpMethod.PUT, "cash-drawer/count", CashCountRequest(counts), CashDrawerResponse::class.java)
    suspend fun initialCashSetup(amounts: Map<String, Double>): CashDrawerResponse =
        sendBody(HttpMethod.POST, "cash-drawer/initial-setup", InitialCashSetupRequest(amounts), CashDrawerResponse::class.java)
    suspend fun todayCashMovement(): CashMovementResponse =
        get("cash-drawer/today-movement", CashMovementResponse::class.java)

    // Counterparties
    suspend fun counterparties(search: String? = null): CounterpartiesResponse =
        get("counterparties", CounterpartiesResponse::class.java, query = query("search" to search))
    suspend fun createCounterparty(body: CreateCounterpartyRequest): CounterpartyResponse =
        sendBody(HttpMethod.POST, "counterparties", body, CounterpartyResponse::class.java)
    suspend fun counterparty(id: String): CounterpartyResponse =
        get("counterparties/$id", CounterpartyResponse::class.java)
    suspend fun updateCounterparty(id: String, body: UpdateCounterpartyRequest): CounterpartyResponse =
        sendBody(HttpMethod.PUT, "counterparties/$id", body, CounterpartyResponse::class.java)
    suspend fun deleteCounterparty(id: String) = delete("counterparties/$id")
    suspend fun counterpartyHawalas(id: String): HawalasResponse =
        get("counterparties/$id/hawalas", HawalasResponse::class.java)
    suspend fun settleCounterparty(id: String, currency: String? = null, note: String? = null): SettlementResponse =
        sendBody(HttpMethod.POST, "counterparties/$id/settle", SettleRequest(currency, note), SettlementResponse::class.java)
    suspend fun counterpartyStatement(
        id: String,
        from: String? = null,
        to: String? = null,
    ): CounterpartyStatementResponse = get(
        "counterparties/$id/statement",
        CounterpartyStatementResponse::class.java,
        query = query("from" to from, "to" to to),
    )

    // Hawalas
    suspend fun hawalas(
        status: String? = null,
        currency: String? = null,
        counterpartyId: String? = null,
        search: String? = null,
        includeOpening: Boolean? = null,
        limit: Int? = null,
        offset: Int? = null,
    ): PaginatedHawalasResponse = get(
        "hawalas",
        PaginatedHawalasResponse::class.java,
        query = query(
            "status" to status,
            "currency" to currency,
            "counterpartyId" to counterpartyId,
            "search" to search,
            "includeOpening" to includeOpening,
            "limit" to limit,
            "offset" to offset,
        ),
    )
    suspend fun pendingHawalas(): HawalasResponse = get("hawalas/pending", HawalasResponse::class.java)
    suspend fun nextHawalaCode(): NextCodeResponse = get("hawalas/next-code", NextCodeResponse::class.java)
    suspend fun issueHawala(body: IssueHawalaRequest): HawalaResponse =
        sendBody(HttpMethod.POST, "hawalas", body, HawalaResponse::class.java)
    suspend fun hawala(id: String): HawalaResponse = get("hawalas/$id", HawalaResponse::class.java)
    suspend fun markHawalaPaid(
        id: String,
        method: String? = null,
        payoutCustomerId: String? = null,
    ): HawalaResponse = sendBody(
        HttpMethod.POST,
        "hawalas/$id/mark-paid",
        MarkHawalaPaidRequest(method, payoutCustomerId),
        HawalaResponse::class.java,
    )
    suspend fun cancelHawala(id: String): CancelHawalaResponse =
        client.send(HttpMethod.DELETE, path("hawalas/$id"), CancelHawalaResponse::class.java)

    // Customers and transactions
    suspend fun customers(
        search: String? = null,
        city: String? = null,
        status: String? = null,
    ): CustomersResponse = get(
        "customers",
        CustomersResponse::class.java,
        query = query("search" to search, "city" to city, "status" to status),
    )
    suspend fun createCustomer(body: CreateCustomerRequest): CustomerResponse =
        sendBody(HttpMethod.POST, "customers", body, CustomerResponse::class.java)
    suspend fun customer(id: String): CustomerResponse = get("customers/$id", CustomerResponse::class.java)
    suspend fun updateCustomer(id: String, body: UpdateCustomerRequest): CustomerResponse =
        sendBody(HttpMethod.PUT, "customers/$id", body, CustomerResponse::class.java)
    suspend fun deleteCustomer(id: String) = delete("customers/$id")
    suspend fun customerTransactions(id: String): TransactionsResponse =
        get("customers/$id/transactions", TransactionsResponse::class.java)
    suspend fun createCustomerTransaction(id: String, body: CreateTransactionRequest): TransactionResponse =
        sendBody(HttpMethod.POST, "customers/$id/transactions", body, TransactionResponse::class.java)
    suspend fun customerStatement(
        id: String,
        from: String? = null,
        to: String? = null,
    ): CustomerStatementResponse = get(
        "customers/$id/statement",
        CustomerStatementResponse::class.java,
        query = query("from" to from, "to" to to),
    )
    suspend fun transaction(id: String): TransactionResponse =
        get("transactions/$id", TransactionResponse::class.java)
    suspend fun deleteTransaction(id: String): DeletedTransactionResponse =
        client.send(HttpMethod.DELETE, path("transactions/$id"), DeletedTransactionResponse::class.java)
    suspend fun transactionReceipt(id: String): ReceiptResponse =
        get("transactions/$id/receipt", ReceiptResponse::class.java)

    // Team and expenses
    suspend fun teamMembers(): TeamMembersResponse = get("team", TeamMembersResponse::class.java)
    suspend fun createTeamMember(body: CreateTeamMemberRequest): TeamMemberResponse =
        sendBody(HttpMethod.POST, "team", body, TeamMemberResponse::class.java)
    suspend fun teamMember(id: String): TeamMemberResponse = get("team/$id", TeamMemberResponse::class.java)
    suspend fun updateTeamMember(id: String, body: UpdateTeamMemberRequest): TeamMemberResponse =
        sendBody(HttpMethod.PUT, "team/$id", body, TeamMemberResponse::class.java)
    suspend fun deleteTeamMember(id: String) = delete("team/$id")
    suspend fun expenses(teamMemberId: String? = null): ExpensesResponse =
        get("expenses", ExpensesResponse::class.java, query = query("teamMemberId" to teamMemberId))
    suspend fun createExpense(body: CreateExpenseRequest): ExpenseResponse =
        sendBody(HttpMethod.POST, "expenses", body, ExpenseResponse::class.java)
    suspend fun expense(id: String): ExpenseResponse = get("expenses/$id", ExpenseResponse::class.java)
    suspend fun deleteExpense(id: String) = delete("expenses/$id")

    // FX, investments, and settings
    suspend fun fxTrades(limit: Int? = null, offset: Int? = null): FxTradesResponse =
        get("fx/trades", FxTradesResponse::class.java, query = query("limit" to limit, "offset" to offset))
    suspend fun createFxTrade(body: CreateFxTradeRequest): FxTradeResponse =
        sendBody(HttpMethod.POST, "fx/trades", body, FxTradeResponse::class.java)
    suspend fun fxPositions(): FxPositionsResponse = get("fx/positions", FxPositionsResponse::class.java)
    suspend fun investments(): InvestmentsResponse = get("investments", InvestmentsResponse::class.java)
    suspend fun createInvestment(body: CreateInvestmentRequest): InvestmentResponse =
        sendBody(HttpMethod.POST, "investments", body, InvestmentResponse::class.java)
    suspend fun settings(): SettingsResponse = get("settings", SettingsResponse::class.java)
    suspend fun updateSettings(
        reportingCurrency: String? = null,
        tradeCurrency: String? = null,
    ): SettingsResponse = sendBody(
        HttpMethod.PUT,
        "settings",
        UpdateSettingsRequest(reportingCurrency, tradeCurrency),
        SettingsResponse::class.java,
    )

    // Report schemas intentionally remain lossless because these aggregates evolve independently.
    suspend fun dashboardReport(): JsonObject = get("reports/dashboard", JsonObject::class.java)
    suspend fun pnlReport(period: String = "all"): JsonObject =
        get("reports/pnl", JsonObject::class.java, query = query("period" to period))
    suspend fun activityReport(
        kind: String? = null,
        search: String? = null,
        from: String? = null,
        to: String? = null,
        limit: Int? = null,
        offset: Int? = null,
    ): JsonObject = get(
        "reports/activity",
        JsonObject::class.java,
        query = query(
            "kind" to kind,
            "search" to search,
            "from" to from,
            "to" to to,
            "limit" to limit,
            "offset" to offset,
        ),
    )
    suspend fun ledgerStatement(
        period: String = "all",
        kind: String? = null,
        from: String? = null,
        to: String? = null,
    ): JsonObject = get(
        "reports/ledger-statement",
        JsonObject::class.java,
        query = query("period" to period, "kind" to kind, "from" to from, "to" to to),
    )

    private suspend fun <Response : Any> get(
        resource: String,
        type: Class<Response>,
        authenticated: Boolean = true,
        versioned: Boolean = true,
        query: Map<String, String> = emptyMap(),
    ): Response = client.send(
        method = HttpMethod.GET,
        path = if (versioned) path(resource) else resource,
        responseType = type,
        query = query,
        authenticated = authenticated,
    )

    private suspend fun <Response : Any> sendBody(
        method: HttpMethod,
        resource: String,
        request: Any?,
        type: Class<Response>,
        authenticated: Boolean = true,
    ): Response = client.send(method, path(resource), type, body = request, authenticated = authenticated)

    private suspend fun delete(resource: String) {
        client.sendWithoutResponse(HttpMethod.DELETE, path(resource))
    }

    private fun path(resource: String) = "$prefix/$resource"

    private fun query(vararg values: Pair<String, Any?>): Map<String, String> = buildMap {
        values.forEach { (key, value) -> value?.let { put(key, it.toString()) } }
    }
}

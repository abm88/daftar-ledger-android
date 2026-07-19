package com.daftar.app.data.remote

import java.net.URI
import java.net.URLDecoder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Verifies that every README endpoint is represented with its exact verb and path. */
class DaftarApiContractTest {
    private lateinit var transport: RejectingTransport
    private lateinit var api: DaftarApi

    @Before
    fun setUp() {
        transport = RejectingTransport()
        api = DaftarApi(ApiClient("https://api.example.af/", MemoryTokenStore("jwt"), transport))
    }

    @Test
    fun `health auth setup and reference endpoint contracts`() = runTest {
        assertCall(HttpMethod.GET, "/health", authenticated = false) { api.health() }
        assertCall(HttpMethod.POST, "/api/v1/auth/register", authenticated = false) {
            api.register(RegisterRequest("a@b.af", "secret1", "A"))
        }
        assertCall(HttpMethod.POST, "/api/v1/auth/login", authenticated = false) {
            api.login(LoginRequest("a@b.af", "secret1"))
        }
        assertCall(HttpMethod.POST, "/api/v1/auth/logout") { api.logout() }
        assertCall(HttpMethod.GET, "/api/v1/auth/me") { api.me() }
        assertCall(HttpMethod.PUT, "/api/v1/auth/me") { api.updateProfile(UpdateProfileRequest(name = "A")) }
        assertCall(HttpMethod.PUT, "/api/v1/auth/me/password") {
            api.changePassword(ChangePasswordRequest("secret1", "secret2"))
        }
        assertCall(HttpMethod.GET, "/api/v1/setup/status") { api.setupStatus() }
        assertCall(HttpMethod.POST, "/api/v1/setup") {
            api.completeSetup(CompleteSetupRequest(listOf("AFN"), "AFN", "AFN", mapOf("AFN" to 1.0)))
        }
        assertCall(HttpMethod.GET, "/api/v1/cities", authenticated = false) { api.cities() }
        assertCall(HttpMethod.GET, "/api/v1/assets") { api.assets() }
        assertCall(HttpMethod.PATCH, "/api/v1/assets/GOLD/activation") { api.setAssetActivation("GOLD", true) }
        assertCall(HttpMethod.GET, "/api/v1/rates") { api.rates() }
        assertCall(HttpMethod.PUT, "/api/v1/rates") {
            api.updateRates(mapOf("USD" to RateValueRequest(71.0, 72.0)))
        }
        assertCall(HttpMethod.GET, "/api/v1/rates/history", mapOf("asset" to "USD", "limit" to "100")) {
            api.rateHistory("USD", 100)
        }
    }

    @Test
    fun `cash counterparty and hawala endpoint contracts`() = runTest {
        assertCall(HttpMethod.GET, "/api/v1/cash-drawer") { api.cashDrawer() }
        assertCall(HttpMethod.PUT, "/api/v1/cash-drawer/count") { api.recordCashCount(mapOf("AFN" to 1.0)) }
        assertCall(HttpMethod.POST, "/api/v1/cash-drawer/initial-setup") { api.initialCashSetup(mapOf("AFN" to 1.0)) }
        assertCall(HttpMethod.GET, "/api/v1/cash-drawer/today-movement") { api.todayCashMovement() }

        assertCall(HttpMethod.GET, "/api/v1/counterparties", mapOf("search" to "Naseem")) {
            api.counterparties("Naseem")
        }
        assertCall(HttpMethod.POST, "/api/v1/counterparties") {
            api.createCounterparty(CreateCounterpartyRequest("N", null, null, null, "KBL", null, null))
        }
        assertCall(HttpMethod.GET, "/api/v1/counterparties/c1") { api.counterparty("c1") }
        assertCall(HttpMethod.PUT, "/api/v1/counterparties/c1") {
            api.updateCounterparty("c1", UpdateCounterpartyRequest(phone = "1"))
        }
        assertCall(HttpMethod.DELETE, "/api/v1/counterparties/c1") { api.deleteCounterparty("c1") }
        assertCall(HttpMethod.GET, "/api/v1/counterparties/c1/hawalas") { api.counterpartyHawalas("c1") }
        assertCall(HttpMethod.POST, "/api/v1/counterparties/c1/settle") {
            api.settleCounterparty("c1", "USD", "Monthly")
        }
        assertCall(
            HttpMethod.GET,
            "/api/v1/counterparties/c1/statement",
            mapOf("from" to "2026-01-01", "to" to "2026-02-01"),
        ) { api.counterpartyStatement("c1", "2026-01-01", "2026-02-01") }

        assertCall(
            HttpMethod.GET,
            "/api/v1/hawalas",
            mapOf("status" to "pending", "currency" to "USD", "limit" to "25", "offset" to "50"),
        ) { api.hawalas(status = "pending", currency = "USD", limit = 25, offset = 50) }
        assertCall(HttpMethod.GET, "/api/v1/hawalas/pending") { api.pendingHawalas() }
        assertCall(HttpMethod.GET, "/api/v1/hawalas/next-code") { api.nextHawalaCode() }
        assertCall(HttpMethod.POST, "/api/v1/hawalas") {
            api.issueHawala(IssueHawalaRequest("send", "c1", amount = 1.0, currency = "USD", receiverName = "R"))
        }
        assertCall(HttpMethod.GET, "/api/v1/hawalas/h1") { api.hawala("h1") }
        assertCall(HttpMethod.POST, "/api/v1/hawalas/h1/mark-paid") { api.markHawalaPaid("h1") }
        assertCall(HttpMethod.DELETE, "/api/v1/hawalas/h1") { api.cancelHawala("h1") }
    }

    @Test
    fun `customer transaction team and expense endpoint contracts`() = runTest {
        assertCall(
            HttpMethod.GET,
            "/api/v1/customers",
            mapOf("search" to "Dawood", "city" to "KBL", "status" to "deposits"),
        ) { api.customers("Dawood", "KBL", "deposits") }
        assertCall(HttpMethod.POST, "/api/v1/customers") {
            api.createCustomer(CreateCustomerRequest("D", null, null, null, "KBL", null, null))
        }
        assertCall(HttpMethod.GET, "/api/v1/customers/u1") { api.customer("u1") }
        assertCall(HttpMethod.PUT, "/api/v1/customers/u1") {
            api.updateCustomer("u1", UpdateCustomerRequest(notes = "note"))
        }
        assertCall(HttpMethod.DELETE, "/api/v1/customers/u1") { api.deleteCustomer("u1") }
        assertCall(HttpMethod.GET, "/api/v1/customers/u1/transactions") { api.customerTransactions("u1") }
        assertCall(HttpMethod.POST, "/api/v1/customers/u1/transactions") {
            api.createCustomerTransaction("u1", CreateTransactionRequest("deposit", 1.0, "USD"))
        }
        assertCall(
            HttpMethod.GET,
            "/api/v1/customers/u1/statement",
            mapOf("from" to "2026-01-01", "to" to "2026-02-01"),
        ) { api.customerStatement("u1", "2026-01-01", "2026-02-01") }
        assertCall(HttpMethod.GET, "/api/v1/transactions/t1") { api.transaction("t1") }
        assertCall(HttpMethod.DELETE, "/api/v1/transactions/t1") { api.deleteTransaction("t1") }
        assertCall(HttpMethod.GET, "/api/v1/transactions/t1/receipt") { api.transactionReceipt("t1") }

        assertCall(HttpMethod.GET, "/api/v1/team") { api.teamMembers() }
        assertCall(HttpMethod.POST, "/api/v1/team") {
            api.createTeamMember(CreateTeamMemberRequest("Wali", "Partner", null, null))
        }
        assertCall(HttpMethod.GET, "/api/v1/team/m1") { api.teamMember("m1") }
        assertCall(HttpMethod.PUT, "/api/v1/team/m1") {
            api.updateTeamMember("m1", UpdateTeamMemberRequest(role = "Owner"))
        }
        assertCall(HttpMethod.DELETE, "/api/v1/team/m1") { api.deleteTeamMember("m1") }
        assertCall(HttpMethod.GET, "/api/v1/expenses", mapOf("teamMemberId" to "m1")) { api.expenses("m1") }
        assertCall(HttpMethod.POST, "/api/v1/expenses") {
            api.createExpense(CreateExpenseRequest("m1", 1.0, "AFN", "Rent"))
        }
        assertCall(HttpMethod.GET, "/api/v1/expenses/e1") { api.expense("e1") }
        assertCall(HttpMethod.DELETE, "/api/v1/expenses/e1") { api.deleteExpense("e1") }
    }

    @Test
    fun `fx investment settings and report endpoint contracts`() = runTest {
        assertCall(HttpMethod.GET, "/api/v1/fx/trades", mapOf("limit" to "200", "offset" to "0")) {
            api.fxTrades(200, 0)
        }
        assertCall(HttpMethod.POST, "/api/v1/fx/trades") {
            api.createFxTrade(CreateFxTradeRequest("USD", "AFN", 1.0, 72.0, null))
        }
        assertCall(HttpMethod.GET, "/api/v1/fx/positions") { api.fxPositions() }
        assertCall(HttpMethod.GET, "/api/v1/investments") { api.investments() }
        assertCall(HttpMethod.POST, "/api/v1/investments") {
            api.createInvestment(CreateInvestmentRequest("USD", 1.0, "addition", null))
        }
        assertCall(HttpMethod.GET, "/api/v1/settings") { api.settings() }
        assertCall(HttpMethod.PUT, "/api/v1/settings") { api.updateSettings("AFN", "USD") }
        assertCall(HttpMethod.GET, "/api/v1/reports/dashboard") { api.dashboardReport() }
        assertCall(HttpMethod.GET, "/api/v1/reports/pnl", mapOf("period" to "month")) { api.pnlReport("month") }
        assertCall(
            HttpMethod.GET,
            "/api/v1/reports/activity",
            mapOf("kind" to "fx", "search" to "walk in", "limit" to "100", "offset" to "0"),
        ) { api.activityReport(kind = "fx", search = "walk in", limit = 100, offset = 0) }
        assertCall(
            HttpMethod.GET,
            "/api/v1/reports/ledger-statement",
            mapOf("period" to "all", "kind" to "expense"),
        ) { api.ledgerStatement(kind = "expense") }
    }

    private suspend fun assertCall(
        method: HttpMethod,
        path: String,
        query: Map<String, String> = emptyMap(),
        authenticated: Boolean = true,
        call: suspend () -> Any?,
    ) {
        val before = transport.requests.size
        val error = runCatching { call() }.exceptionOrNull()
        assertTrue("Contract probe should stop at the fake server", error is ApiException.Server)
        val request = transport.requests[before]
        val uri = URI(request.url)
        assertEquals(method, request.method)
        assertEquals(path, uri.path)
        assertEquals(query, uri.rawQuery.toQueryMap())
        if (authenticated) assertEquals("Bearer jwt", request.headers["Authorization"])
        else assertFalse(request.headers.containsKey("Authorization"))
    }

    private fun String?.toQueryMap(): Map<String, String> = this
        ?.split('&')
        ?.filter(String::isNotEmpty)
        ?.associate { item ->
            val pieces = item.split('=', limit = 2)
            URLDecoder.decode(pieces[0], Charsets.UTF_8.name()) to
                URLDecoder.decode(pieces.getOrElse(1) { "" }, Charsets.UTF_8.name())
        }
        .orEmpty()
}

private class RejectingTransport : HttpTransport {
    val requests = mutableListOf<HttpRequest>()

    override suspend fun execute(request: HttpRequest): HttpResponse {
        requests += request
        return HttpResponse(418, """{"error":{"message":"contract probe"}}""")
    }
}

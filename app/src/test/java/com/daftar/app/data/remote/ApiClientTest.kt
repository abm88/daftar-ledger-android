package com.daftar.app.data.remote

import com.google.gson.JsonParser
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiClientTest {
    @Test
    fun `authenticated request adds bearer token and versioned path`() = runTest {
        val transport = QueueTransport(HttpResponse(200, """{"assets":[]}"""))
        val client = ApiClient("https://api.example.af/", MemoryTokenStore("test-jwt"), transport)
        val api = DaftarApi(client)

        assertTrue(api.assets().assets.isEmpty())
        val request = transport.requests.single()
        assertEquals(HttpMethod.GET, request.method)
        assertEquals("https://api.example.af/api/v1/assets", request.url)
        assertEquals("Bearer test-jwt", request.headers["Authorization"])
        assertEquals("application/json", request.headers["Accept"])
    }

    @Test
    fun `login is public and sends documented JSON`() = runTest {
        val response = """{"user":{"id":"u1","email":"demo@daftar.af","name":"Demo","createdAt":"2026-07-08T14:25:49.122Z"},"token":"jwt"}"""
        val transport = QueueTransport(HttpResponse(200, response))
        val api = DaftarApi(ApiClient("https://api.example.af", MemoryTokenStore("old"), transport))

        val auth = api.login(LoginRequest("demo@daftar.af", "daftar123"))

        assertEquals("u1", auth.user.id)
        assertEquals("jwt", auth.token)
        val request = transport.requests.single()
        assertEquals(HttpMethod.POST, request.method)
        assertEquals("https://api.example.af/api/v1/auth/login", request.url)
        assertFalse(request.headers.containsKey("Authorization"))
        assertEquals("application/json", request.headers["Content-Type"])
        val body = JsonParser.parseString(request.body).asJsonObject
        assertEquals("demo@daftar.af", body["email"].asString)
        assertEquals("daftar123", body["password"].asString)
    }

    @Test
    fun `filters are URL encoded and pagination is preserved`() = runTest {
        val response = """{"items":[],"pagination":{"total":0,"limit":25,"offset":50,"hasMore":false}}"""
        val transport = QueueTransport(HttpResponse(200, response))
        val api = DaftarApi(ApiClient("https://api.example.af", MemoryTokenStore("jwt"), transport))

        val result = api.hawalas(
            status = "pending",
            currency = "USD",
            search = "Rahmat & Sons",
            includeOpening = true,
            limit = 25,
            offset = 50,
        )

        assertEquals(50, result.pagination.offset)
        val url = transport.requests.single().url
        assertTrue(url.contains("search=Rahmat+%26+Sons"))
        assertTrue(url.contains("includeOpening=true"))
        assertTrue(url.contains("offset=50"))
    }

    @Test
    fun `no-content delete succeeds without decoding`() = runTest {
        val transport = QueueTransport(HttpResponse(204, ""))
        val api = DaftarApi(ApiClient("https://api.example.af", MemoryTokenStore("jwt"), transport))

        api.deleteCustomer("customer-id")

        assertEquals(HttpMethod.DELETE, transport.requests.single().method)
        assertEquals("https://api.example.af/api/v1/customers/customer-id", transport.requests.single().url)
    }

    @Test
    fun `validation envelope keeps status message and details`() = runTest {
        val transport = QueueTransport(
            HttpResponse(
                422,
                """{"error":{"message":"Validation failed","details":[{"path":"amount","message":"must be positive"}]}}""",
            ),
        )
        val api = DaftarApi(ApiClient("https://api.example.af", MemoryTokenStore("jwt"), transport))

        val error = runCatching { api.initialCashSetup(mapOf("USD" to -1.0)) }.exceptionOrNull()

        assertTrue(error is ApiException.Server)
        error as ApiException.Server
        assertEquals(422, error.statusCode)
        assertEquals("Validation failed", error.message)
        assertEquals("amount", error.details.single().path)
        assertEquals("must be positive", error.details.single().messageText)
    }

    @Test
    fun `unauthorized response clears revoked token`() = runTest {
        val tokens = MemoryTokenStore("revoked")
        val transport = QueueTransport(HttpResponse(401, """{"error":{"message":"Session revoked"}}"""))
        val api = DaftarApi(ApiClient("https://api.example.af", tokens, transport))

        val error = runCatching { api.me() }.exceptionOrNull()

        assertTrue(error is ApiException.Unauthorized)
        assertEquals("Session revoked", error?.message)
        assertNull(tokens.token)
    }
}

internal class MemoryTokenStore(override var token: String? = null) : AuthTokenStore {
    override fun clear() { token = null }
}

private class QueueTransport(vararg responses: HttpResponse) : HttpTransport {
    val requests = mutableListOf<HttpRequest>()
    private val queue = ArrayDeque(responses.toList())

    override suspend fun execute(request: HttpRequest): HttpResponse {
        requests += request
        return queue.removeFirst()
    }
}

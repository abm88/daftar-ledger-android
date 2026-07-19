package com.daftar.app.data.remote

import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Optional real-backend smoke test. Normal unit runs skip it; CI/local runs can
 * set DAFTAR_INTEGRATION_BASE_URL=http://localhost:3000/ to verify live decoding.
 */
class LiveApiIntegrationTest {
    @Test
    fun `fresh account initial API snapshot decodes`() = runTest {
        val baseUrl = System.getenv("DAFTAR_INTEGRATION_BASE_URL")
        assumeTrue("Set DAFTAR_INTEGRATION_BASE_URL to run live API verification", !baseUrl.isNullOrBlank())

        val tokens = MemoryTokenStore()
        val api = DaftarApi(ApiClient(baseUrl!!, tokens))
        val email = "android-smoke-${UUID.randomUUID().toString().lowercase()}@example.af"

        api.health()
        api.cities()
        val auth = api.register(RegisterRequest(email, "daftar123", "Android Smoke Test"))
        tokens.token = auth.token

        coroutineScope {
            listOf(
                async { api.me() },
                async { api.setupStatus() },
                async { api.assets() },
                async { api.rates() },
                async { api.cashDrawer() },
                async { api.todayCashMovement() },
                async { api.counterparties() },
                async { api.hawalas(limit = 200) },
                async { api.pendingHawalas() },
                async { api.nextHawalaCode() },
                async { api.customers() },
                async { api.teamMembers() },
                async { api.expenses() },
                async { api.fxTrades(limit = 200) },
                async { api.fxPositions() },
                async { api.investments() },
                async { api.settings() },
                async { api.dashboardReport() },
                async { api.pnlReport() },
                async { api.activityReport() },
                async { api.ledgerStatement() },
            ).awaitAll()
        }

        api.logout()
    }
}

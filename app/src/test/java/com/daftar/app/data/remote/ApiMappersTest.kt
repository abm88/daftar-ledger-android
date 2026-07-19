package com.daftar.app.data.remote

import com.daftar.app.domain.model.CommissionMode
import com.daftar.app.domain.model.CustomerTxType
import com.daftar.app.domain.model.HawalaStatus
import com.daftar.app.domain.model.HawalaType
import com.daftar.app.domain.model.RatePair
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiMappersTest {
    @Test
    fun `received hawala keeps server lifecycle and fixed commission`() {
        val domain = HawalaDto(
            id = "h1",
            counterpartyId = "c1",
            type = "recv",
            fromCity = "HRT",
            toCity = "KBL",
            senderName = "Origin",
            receiverName = "Receiver",
            amount = 800.0,
            currency = "USD",
            commissionMode = "fixed",
            commissionPct = 0.0,
            commissionAmount = 10.0,
            code = "100777",
            status = "paid",
            isOpening = false,
            createdAt = "2026-07-19T09:00:00.000Z",
            paidAt = "2026-07-19T10:00:00.000Z",
        ).toDomain()

        assertEquals(HawalaType.RECEIVE, domain.type)
        assertEquals(HawalaStatus.PAID, domain.status)
        assertEquals(CommissionMode.FIXED, domain.commissionMode)
        assertEquals(10.0, domain.resolvedCommissionAmount, 0.0)
        assertEquals("100777", domain.pickupCode)
    }

    @Test
    fun `converted customer entry keeps original intake and portable photos`() {
        val domain = CustomerTransactionDto(
            id = "t1",
            customerId = "u1",
            type = "deposit",
            amount = 71_900.0,
            currency = "AFN",
            conversion = ConversionDto(1_000.0, "USD", 71.9, 71_900.0, "AFN"),
            photos = listOf("data:image/jpeg;base64,abc"),
            createdAt = "2026-07-19T09:00:00Z",
        ).toDomain()

        assertEquals(CustomerTxType.DEPOSIT, domain.type)
        assertEquals("USD", domain.conversion?.receivedCurrency)
        assertEquals(1_000.0, domain.conversion?.receivedAmount ?: 0.0, 0.0)
        assertEquals(listOf("data:image/jpeg;base64,abc"), domain.photoUris)
    }

    @Test
    fun `rate mapper derives legacy pair without making it source of truth`() {
        val book = RatesResponse(
            rates = listOf(
                RateDto("USD", 71.0, 72.0, 71.5, 0.7),
                RateDto("PKR", 0.25, 0.3, 0.29, 3.4),
            ),
        ).toDomain()

        assertEquals(240.0, book.pairs.getValue(RatePair.USD_PKR).sell, 0.0001)
        assertTrue(book.perAsset.containsKey("USD"))
    }
}

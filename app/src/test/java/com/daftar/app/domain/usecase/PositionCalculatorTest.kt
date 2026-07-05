package com.daftar.app.domain.usecase

import com.daftar.app.domain.model.City
import com.daftar.app.domain.model.Counterparty
import com.daftar.app.domain.model.Customer
import com.daftar.app.domain.model.CustomerTransaction
import com.daftar.app.domain.model.CustomerTxType
import com.daftar.app.domain.model.Hawala
import com.daftar.app.domain.model.HawalaStatus
import com.daftar.app.domain.model.HawalaType
import com.daftar.app.domain.model.PartnerTier
import org.junit.Assert.assertEquals
import org.junit.Test

class PositionCalculatorTest {

    private val calculator = PositionCalculator()

    private fun hawala(
        type: HawalaType,
        amount: Double,
        currency: String = "USD",
        status: HawalaStatus = HawalaStatus.PAID,
    ) = Hawala(
        id = "h_$amount$type", type = type, fromCity = City.KBL, toCity = City.HRT,
        senderName = "A", receiverName = "B", amount = amount, currency = currency,
        commissionPercent = 1.0, pickupCode = "101246", status = status,
        timestampMillis = 0L, dateLabel = "Today",
    )

    private fun partner(vararg hawalas: Hawala) = Counterparty(
        id = "cp", name = "Test", shortName = "T", phone = "-", city = City.KBL,
        initial = "ت", tier = PartnerTier.CORE, hawalas = hawalas.toList(),
    )

    @Test
    fun `send adds, receive subtracts, pending ignored`() {
        val position = calculator.partnerPosition(
            partner(
                hawala(HawalaType.SEND, 5_000.0),
                hawala(HawalaType.RECEIVE, 2_000.0),
                hawala(HawalaType.SEND, 999.0, status = HawalaStatus.PENDING),
            ),
        )
        assertEquals(3_000.0, position["USD"], 0.001)
    }

    @Test
    fun `settlement entries carry their signed delta`() {
        val position = calculator.partnerPosition(
            partner(
                hawala(HawalaType.SEND, 5_000.0),
                hawala(HawalaType.SETTLEMENT, -5_000.0),
            ),
        )
        assertEquals(0.0, position["USD"], 0.001)
    }

    @Test
    fun `customer balance treats deposits as positive and debits as negative`() {
        val customer = Customer(
            id = "c", name = "N", shortName = "N", phone = "-", city = City.KBL,
            colorIndex = 0, initial = "ن", accountOpenedLabel = "Jan 2026",
            transactions = listOf(
                tx(CustomerTxType.OPENING, 8_500.0),
                tx(CustomerTxType.DEPOSIT, 1_500.0),
                tx(CustomerTxType.WITHDRAWAL, 2_000.0),
                tx(CustomerTxType.CREDIT, 500.0),
            ),
        )
        assertEquals(7_500.0, calculator.customerBalance(customer)["USD"], 0.001)
    }

    @Test
    fun `running balances expose before and after per entry`() {
        val customer = Customer(
            id = "c", name = "N", shortName = "N", phone = "-", city = City.KBL,
            colorIndex = 0, initial = "ن", accountOpenedLabel = "Jan 2026",
            transactions = listOf(
                tx(CustomerTxType.OPENING, 1_000.0, id = "t1"),
                tx(CustomerTxType.WITHDRAWAL, 400.0, id = "t2"),
            ),
        )
        val second = calculator.runningBalanceFor(customer, "t2")!!
        assertEquals(1_000.0, second.balanceBefore, 0.001)
        assertEquals(600.0, second.balanceAfter, 0.001)
    }

    private fun tx(type: CustomerTxType, amount: Double, id: String = "t_$amount$type") =
        CustomerTransaction(
            id = id, type = type, amount = amount, currency = "USD",
            dateLabel = "01 Feb", timestampMillis = 0L,
        )
}

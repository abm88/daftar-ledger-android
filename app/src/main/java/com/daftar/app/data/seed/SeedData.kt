package com.daftar.app.data.seed

import com.daftar.app.core.time.TimeProvider
import com.daftar.app.domain.model.CashDrawer
import com.daftar.app.domain.model.City
import com.daftar.app.domain.model.Counterparty
import com.daftar.app.domain.model.Customer
import com.daftar.app.domain.model.CustomerTransaction
import com.daftar.app.domain.model.CustomerTxType
import com.daftar.app.domain.model.Expense
import com.daftar.app.domain.model.FxSide
import com.daftar.app.domain.model.FxTrade
import com.daftar.app.domain.model.Hawala
import com.daftar.app.domain.model.HawalaStatus
import com.daftar.app.domain.model.HawalaType
import com.daftar.app.domain.model.Investment
import com.daftar.app.domain.model.InvestmentType
import com.daftar.app.domain.model.PartnerTier
import com.daftar.app.domain.model.Rate
import com.daftar.app.domain.model.RateBook
import com.daftar.app.domain.model.RatePair
import com.daftar.app.domain.model.TeamMember

/**
 * Demo dataset mirroring the design prototype. Replaced by the API layer later —
 * nothing outside `data` knows this exists.
 */
class SeedData(timeProvider: TimeProvider) {

    private val now = timeProvider.nowMillis()
    private fun daysAgo(days: Int): Long = now - days * 86_400_000L
    private fun hoursAgo(hours: Int): Long = now - hours * 3_600_000L

    val cashDrawer = CashDrawer(
        balances = mapOf(
            "USD" to 12_450.0,
            "AFN" to 1_850_000.0,
            "PKR" to 425_000.0,
            "EUR" to 0.0, "GBP" to 0.0, "SAR" to 0.0, "AED" to 0.0,
            "GOLD" to 0.0, "SILVER" to 0.0,
        ),
        lastCountLabel = "Today, 09:00",
    )

    val rateBook = RateBook(
        perAsset = mapOf(
            "USD" to Rate(buy = 71.20, sell = 71.80, previousSell = 71.50, deltaPercent = 0.1),
            "PKR" to Rate(buy = 0.245, sell = 0.252, previousSell = 0.250, deltaPercent = -0.5),
            "EUR" to Rate(buy = 76.50, sell = 77.30, previousSell = 77.00, deltaPercent = 0.4),
            "GBP" to Rate(buy = 89.20, sell = 90.10, previousSell = 89.80, deltaPercent = 0.3),
            "SAR" to Rate(buy = 18.95, sell = 19.20, previousSell = 19.10, deltaPercent = 0.5),
            "AED" to Rate(buy = 19.40, sell = 19.65, previousSell = 19.55, deltaPercent = 0.5),
            "GOLD" to Rate(buy = 5_680.0, sell = 5_750.0, previousSell = 5_720.0, deltaPercent = 0.5),
            "SILVER" to Rate(buy = 65.0, sell = 68.0, previousSell = 67.0, deltaPercent = 1.5),
        ),
        pairs = mapOf(
            RatePair.USD_AFN to Rate(buy = 71.20, sell = 71.80, previousSell = 71.50, deltaPercent = 0.1),
            RatePair.PKR_AFN to Rate(buy = 0.245, sell = 0.252, previousSell = 0.250, deltaPercent = -0.5),
            RatePair.USD_PKR to Rate(buy = 283.50, sell = 285.20, previousSell = 283.00, deltaPercent = 0.6),
        ),
    )

    val fxTrades = listOf(
        FxTrade("fx1", FxSide.BUY, "PKR", "USD", 1_410_000.0, 5_000.0, 282.0, null, daysAgo(3), "3 days ago", "Opening USD stock"),
        FxTrade("fx2", FxSide.BUY, "AFN", "PKR", 84_000.0, 300_000.0, 0.28, null, daysAgo(2), "2 days ago", "PKR replenish"),
        FxTrade("fx3", FxSide.SELL, "USD", "AFN", 2_000.0, 144_000.0, 72.0, 1_000.0, daysAgo(1), "Yesterday", "Walk-in exchange"),
        FxTrade("fx4", FxSide.SELL, "PKR", "AFN", 100_000.0, 28_100.0, 0.281, 100.0, hoursAgo(3), "Today, 10:30", "Morning exchange"),
    )

    val investments = listOf(
        Investment("inv1", daysAgo(90), "90 days ago", "USD", 8_000.0, InvestmentType.OPENING, "Shop opening — USD stock"),
        Investment("inv2", daysAgo(90), "90 days ago", "AFN", 1_500_000.0, InvestmentType.OPENING, "Shop opening — AFN cash"),
        Investment("inv3", daysAgo(90), "90 days ago", "PKR", 200_000.0, InvestmentType.OPENING, "Shop opening — PKR float"),
        Investment("inv4", daysAgo(60), "60 days ago", "USD", 3_000.0, InvestmentType.ADDITION, "Top-up from personal savings"),
        Investment("inv5", daysAgo(30), "30 days ago", "AFN", 250_000.0, InvestmentType.ADDITION, "Reinvested first month profits"),
        Investment("inv6", daysAgo(14), "14 days ago", "PKR", 150_000.0, InvestmentType.ADDITION, "PKR liquidity injection"),
        Investment("inv7", daysAgo(7), "7 days ago", "AFN", 50_000.0, InvestmentType.WITHDRAWAL, "Personal expenses"),
    )

    val counterparties = listOf(
        Counterparty(
            id = "cp1", name = "Sarai Shahzada — Haji Yusuf", shortName = "H. Yusuf",
            phone = "+93 70 000 1234", city = City.KBL, initial = "ي", tier = PartnerTier.CORE,
            hawalas = listOf(
                hawala("h1", HawalaType.SEND, City.KBL, City.HRT, "Mirwais Khan", "Abdul Rahman", 5_000.0, "USD", 1.0, "101246", HawalaStatus.PENDING, hoursAgo(2), "Today, 11:04"),
                hawala("h2", HawalaType.RECEIVE, City.HRT, City.KBL, "Mohammad Ali", "Karim Shah", 240_000.0, "AFN", 0.8, "101243", HawalaStatus.PAID, daysAgo(1), "Yesterday"),
                hawala("h3", HawalaType.SEND, City.KBL, City.MZR, "Zia Walid", "Haji Baba", 850_000.0, "PKR", 1.2, "101242", HawalaStatus.PAID, daysAgo(2), "2 days ago"),
            ),
        ),
        Counterparty(
            id = "cp2", name = "Sarai Qandahari — Agha Naseem", shortName = "A. Naseem",
            phone = "+93 70 000 5678", city = City.HRT, initial = "ن", tier = PartnerTier.CORE,
            hawalas = listOf(
                hawala("h4", HawalaType.SEND, City.HRT, City.KBL, "Ahmad Zia", "Sultan Mohammad", 3_200.0, "USD", 1.0, "101244", HawalaStatus.PENDING, hoursAgo(4), "Today, 07:22"),
                hawala("h5", HawalaType.RECEIVE, City.KBL, City.HRT, "Fawad Ahmad", "Haji Noor", 180_000.0, "AFN", 0.8, "101241", HawalaStatus.PAID, daysAgo(3), "3 days ago"),
            ),
        ),
        Counterparty(
            id = "cp3", name = "Shahr-e-Naw Saraf — Khalid", shortName = "Khalid",
            phone = "+93 70 000 9012", city = City.KBL, initial = "خ", tier = PartnerTier.REGULAR,
            hawalas = listOf(
                hawala("h6", HawalaType.SEND, City.KBL, City.JAL, "Bilal Khan", "Amir Khan", 120_000.0, "PKR", 1.5, "101245", HawalaStatus.PENDING, hoursAgo(1), "Today, 09:41"),
            ),
        ),
        Counterparty(
            id = "cp4", name = "Sarai Mazar — Haji Qasim", shortName = "H. Qasim",
            phone = "+93 70 000 3456", city = City.MZR, initial = "ق", tier = PartnerTier.CORE,
            hawalas = listOf(
                hawala("h7", HawalaType.RECEIVE, City.MZR, City.KBL, "Reza Khan", "Mustafa Ali", 2_100.0, "USD", 1.0, "101240", HawalaStatus.PAID, daysAgo(4), "4 days ago"),
            ),
        ),
    )

    val customers = listOf(
        Customer(
            id = "cust1", name = "Haji Dawood", shortName = "Dawood",
            phone = "+93 70 100 2345", city = City.KBL, colorIndex = 0, initial = "د",
            accountOpenedLabel = "Feb 2026", notes = "Timber importer, monthly account",
            transactions = listOf(
                tx("ct1", CustomerTxType.OPENING, 8_500.0, "USD", "01 Feb 2026", daysAgo(80), "Opening deposit"),
                tx("ct2", CustomerTxType.DEPOSIT, 3_200.0, "USD", "14 Feb 2026", daysAgo(68), "Cash deposit"),
                tx("ct3", CustomerTxType.WITHDRAWAL, 450_000.0, "AFN", "20 Feb 2026", daysAgo(62), "Cash withdrawal for timber purchase"),
                tx("ct4", CustomerTxType.CHARGE, 180_000.0, "PKR", "02 Mar 2026", daysAgo(52), "Paid to Peshawar supplier on behalf"),
                tx("ct5", CustomerTxType.DEPOSIT, 1_800.0, "USD", "15 Mar 2026", daysAgo(39), "Weekly deposit"),
                tx("ct6", CustomerTxType.WITHDRAWAL, 2_500.0, "USD", "02 Apr 2026", daysAgo(21), "Cash withdrawal"),
                tx("ct7", CustomerTxType.CREDIT, 600_000.0, "AFN", "10 Apr 2026", daysAgo(13), "Short-term advance (to be settled)"),
                tx("ct8", CustomerTxType.DEPOSIT, 2_100.0, "USD", "18 Apr 2026", daysAgo(5), "Cash deposit"),
            ),
        ),
        Customer(
            id = "cust2", name = "Mohammad Karim", shortName = "M. Karim",
            phone = "+93 70 100 6789", city = City.KBL, colorIndex = 1, initial = "م",
            accountOpenedLabel = "Jan 2026", notes = "Dry fruit exporter",
            transactions = listOf(
                tx("ct9", CustomerTxType.OPENING, 4_200.0, "USD", "12 Jan 2026", daysAgo(101), "Opening deposit"),
                tx("ct10", CustomerTxType.DEPOSIT, 1_500.0, "USD", "25 Feb 2026", daysAgo(57), "Cash deposit"),
                tx("ct11", CustomerTxType.WITHDRAWAL, 280_000.0, "AFN", "15 Mar 2026", daysAgo(39), "Market expenses"),
            ),
        ),
        Customer(
            id = "cust3", name = "Sultan Aziz", shortName = "S. Aziz",
            phone = "+93 70 100 1122", city = City.HRT, colorIndex = 2, initial = "س",
            accountOpenedLabel = "Mar 2026", notes = "Carpet merchant",
            transactions = listOf(
                tx("ct12", CustomerTxType.OPENING, 1_200.0, "USD", "05 Mar 2026", daysAgo(49), "Opening deposit"),
                tx("ct13", CustomerTxType.DEPOSIT, 900.0, "USD", "22 Mar 2026", daysAgo(32), "Weekly deposit"),
            ),
        ),
    )

    // v20: team members and the expenses booked against each of them.
    val teamMembers = listOf(
        TeamMember(id = "tm1", name = "Azam", role = "Partner", phone = "+93 70 111 2222"),
        TeamMember(id = "tm2", name = "Daud Sarafi", role = "Owner", phone = "+93 70 333 4444"),
    )

    val expenses = listOf(
        Expense(
            id = "exp1", amount = 3_000.0, currency = "AFN", teamMemberId = "tm1",
            note = "Shop rent share", timestampMillis = daysAgo(2), dateLabel = "2 days ago",
        ),
        Expense(
            id = "exp2", amount = 450.0, currency = "AFN", teamMemberId = "tm2",
            note = "Tea & lunch", timestampMillis = hoursAgo(1), dateLabel = "Today",
        ),
    )

    private fun hawala(
        id: String, type: HawalaType, from: City, to: City,
        sender: String, receiver: String, amount: Double, currency: String,
        commissionPercent: Double, code: String, status: HawalaStatus,
        ts: Long, dateLabel: String,
    ) = Hawala(
        id = id, type = type, fromCity = from, toCity = to,
        senderName = sender, receiverName = receiver,
        amount = amount, currency = currency,
        commissionPercent = commissionPercent, pickupCode = code,
        status = status, timestampMillis = ts, dateLabel = dateLabel,
    )

    private fun tx(
        id: String, type: CustomerTxType, amount: Double, currency: String,
        dateLabel: String, ts: Long, note: String,
    ) = CustomerTransaction(
        id = id, type = type, amount = amount, currency = currency,
        dateLabel = dateLabel, timestampMillis = ts, note = note,
    )
}

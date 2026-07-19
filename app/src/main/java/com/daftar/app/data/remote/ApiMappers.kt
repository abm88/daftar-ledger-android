package com.daftar.app.data.remote

import com.daftar.app.core.format.Formatters
import com.daftar.app.domain.model.CashDrawer
import com.daftar.app.domain.model.City
import com.daftar.app.domain.model.CommissionMode
import com.daftar.app.domain.model.Counterparty
import com.daftar.app.domain.model.CurrencyConversion
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
import com.daftar.app.domain.model.LedgerSettings
import com.daftar.app.domain.model.PartnerTier
import com.daftar.app.domain.model.Rate
import com.daftar.app.domain.model.RateBook
import com.daftar.app.domain.model.RatePair
import com.daftar.app.domain.model.ShopProfile
import com.daftar.app.domain.model.TeamMember
import com.daftar.app.domain.model.UserAccount
import java.time.Instant

internal fun String.toEpochMillis(): Long = Instant.parse(this).toEpochMilli()

internal fun UserDto.toAccount(): UserAccount = UserAccount(id, name, email, createdAt.toEpochMillis())

internal fun UserDto.toShopProfile(): ShopProfile = ShopProfile(
    ownerName = name,
    shopName = shopName ?: "Daftar",
    city = cityCode?.let(City::fromCode) ?: City.KBL,
    phone = phone.orEmpty(),
    registration = registrationNo.orEmpty(),
)

internal fun HawalaDto.toDomain(): Hawala {
    val millis = createdAt.toEpochMillis()
    return Hawala(
        id = id,
        type = when (type) {
            "send" -> HawalaType.SEND
            "recv" -> HawalaType.RECEIVE
            "settle" -> HawalaType.SETTLEMENT
            else -> error("Unsupported hawala type: $type")
        },
        fromCity = City.fromCode(fromCity),
        toCity = City.fromCode(toCity),
        senderName = senderName,
        receiverName = receiverName,
        amount = amount,
        currency = currency,
        commissionPercent = commissionPct,
        commissionMode = when (commissionMode) {
            "percent" -> CommissionMode.PERCENT
            "fixed" -> CommissionMode.FIXED
            else -> error("Unsupported commission mode: $commissionMode")
        },
        commissionAmount = commissionAmount,
        pickupCode = code,
        status = when (status) {
            "pending" -> HawalaStatus.PENDING
            "paid" -> HawalaStatus.PAID
            else -> error("Unsupported hawala status: $status")
        },
        timestampMillis = millis,
        dateLabel = Formatters.fullDateLabel(millis),
        note = note,
        senderCustomerId = senderCustomerId,
    )
}

internal fun CounterpartyDto.toDomain(hawalas: List<Hawala> = emptyList()): Counterparty = Counterparty(
    id = id,
    name = name,
    shortName = shortName ?: name,
    phone = phone.orEmpty(),
    city = City.fromCode(cityCode),
    initial = initial ?: name.take(1),
    tier = tier?.let { PartnerTier.valueOf(it.uppercase()) } ?: PartnerTier.REGULAR,
    hawalas = hawalas,
)

internal fun CustomerTransactionDto.toDomain(): CustomerTransaction {
    val millis = createdAt.toEpochMillis()
    return CustomerTransaction(
        id = id,
        type = CustomerTxType.valueOf(type.uppercase()),
        amount = amount,
        currency = currency,
        dateLabel = Formatters.fullDateLabel(millis),
        timestampMillis = millis,
        note = note,
        hawalaId = hawalaId,
        conversion = conversion?.let {
            CurrencyConversion(
                receivedAmount = it.receivedAmount,
                receivedCurrency = it.receivedCurrency,
                rate = it.rate,
                creditedAmount = it.creditedAmount,
                creditedCurrency = it.creditedCurrency,
            )
        },
        photoUris = photos.orEmpty(),
    )
}

internal fun CustomerDto.toDomain(
    transactions: List<CustomerTransaction> = emptyList(),
    fallbackColorIndex: Int = 0,
): Customer {
    val openedMillis = (openedAt ?: createdAt)?.toEpochMillis()
    return Customer(
        id = id,
        name = name,
        shortName = shortName ?: name,
        phone = phone.orEmpty(),
        city = City.fromCode(cityCode),
        colorIndex = colorIdx ?: fallbackColorIndex,
        initial = initial ?: name.take(1),
        accountOpenedLabel = openedMillis?.let(Formatters::monthYearLabel).orEmpty(),
        notes = notes,
        transactions = transactions,
    )
}

internal fun FxTradeDto.toDomain(): FxTrade {
    val millis = createdAt.toEpochMillis()
    return FxTrade(
        id = id,
        side = FxSide.valueOf(side.uppercase()),
        fromCurrency = fromCurrency,
        toCurrency = toCurrency,
        fromAmount = fromAmount,
        toAmount = toAmount,
        rate = rate,
        realizedPnlAfn = realizedPl,
        timestampMillis = millis,
        dateLabel = Formatters.nowLabel(millis),
        note = note,
    )
}

internal fun InvestmentDto.toDomain(): Investment {
    val millis = createdAt.toEpochMillis()
    return Investment(
        id = id,
        timestampMillis = millis,
        dateLabel = Formatters.nowLabel(millis),
        assetCode = assetCode,
        amount = amount,
        type = InvestmentType.valueOf(type.uppercase()),
        note = note,
    )
}

internal fun TeamMemberDto.toDomain(): TeamMember = TeamMember(
    id = id,
    name = name,
    role = role,
    phone = phone,
    initial = initial ?: name.take(1),
)

internal fun ExpenseDto.toDomain(): Expense {
    val millis = createdAt.toEpochMillis()
    return Expense(
        id = id,
        amount = amount,
        currency = currency,
        teamMemberId = teamMemberId,
        note = note,
        timestampMillis = millis,
        dateLabel = Formatters.fullDateLabel(millis),
    )
}

internal fun CashDrawerResponse.toDomain(): CashDrawer = CashDrawer(
    balances = items.associate { it.assetCode to it.balance },
    lastCountLabel = lastCountAt?.toEpochMillis()?.let(Formatters::nowLabel) ?: "Not yet counted",
)

internal fun RatesResponse.toDomain(): RateBook {
    val perAsset = rates.associate { dto ->
        dto.assetCode to Rate(dto.buy, dto.sell, dto.prevSell, dto.deltaPct)
    }
    val usd = perAsset["USD"] ?: Rate(1.0, 1.0, 1.0, 0.0)
    val pkr = perAsset["PKR"] ?: Rate(1.0, 1.0, 1.0, 0.0)
    val cross = crosses.orEmpty().firstOrNull { it.pair == "USD_PKR" }
    val usdPkr = Rate(
        buy = cross?.buy ?: usd.buy / pkr.buy,
        sell = cross?.sell ?: usd.sell / pkr.sell,
        previousSell = cross?.sell ?: usd.sell / pkr.sell,
        deltaPercent = 0.0,
    )
    return RateBook(
        perAsset = perAsset,
        pairs = mapOf(
            RatePair.USD_AFN to usd,
            RatePair.PKR_AFN to pkr,
            RatePair.USD_PKR to usdPkr,
        ),
    )
}

internal fun SettingsDto.toDomain(
    activeAssets: Map<String, Boolean>,
    ledgerTableView: Boolean,
): LedgerSettings = LedgerSettings(
    reportingCurrency = reportingCurrency,
    tradeCurrency = tradeCurrency,
    activeAssetOverrides = activeAssets,
    ledgerTableView = ledgerTableView,
)

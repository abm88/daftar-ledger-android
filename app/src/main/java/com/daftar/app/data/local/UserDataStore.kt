package com.daftar.app.data.local

import android.content.Context
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
import com.daftar.app.domain.model.TeamMember
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Everything the prototype persists per user (v18 PERSISTED_FIELDS):
 * cash drawer, partner ledgers, customer accounts, FX trades, investments,
 * the rate book, active-asset toggles, and default currencies.
 */
data class UserDataSnapshot(
    val cashDrawer: CashDrawer,
    val counterparties: List<Counterparty>,
    val customers: List<Customer>,
    val fxTrades: List<FxTrade>,
    val investments: List<Investment>,
    val rateBook: RateBook,
    val settings: LedgerSettings,
    val teamMembers: List<TeamMember> = emptyList(),
    val expenses: List<Expense> = emptyList(),
)

/**
 * Device-local per-user shop data — the Android analog of the prototype's
 * `daftar_data_<userId>` localStorage blobs. Each account's ledger is stored
 * as one JSON document in a private SharedPreferences file, so a returning
 * saraf gets their own daftar back and accounts never see each other's data.
 *
 * TODO(backend): replace with a server-backed store (plus offline cache) once
 * the sync API exists; the JSON shape below is intentionally close to the
 * prototype's persisted state to ease that migration.
 */
@Singleton
class UserDataStore @Inject constructor(@ApplicationContext context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    fun load(userId: String): UserDataSnapshot? {
        val raw = prefs.getString(KEY_PREFIX + userId, null) ?: return null
        return try {
            decode(JSONObject(raw))
        } catch (_: JSONException) {
            null // Corrupt blob — treat as absent, same as the prototype's try/catch.
        }
    }

    fun save(userId: String, snapshot: UserDataSnapshot) {
        prefs.edit().putString(KEY_PREFIX + userId, encode(snapshot).toString()).apply()
    }

    // ---- JSON codec ----

    private fun encode(s: UserDataSnapshot): JSONObject = JSONObject()
        .put(
            "cashDrawer",
            JSONObject()
                .put("balances", JSONObject(s.cashDrawer.balances.mapValues { it.value as Any }))
                .put("lastCount", s.cashDrawer.lastCountLabel),
        )
        .put("counterparties", JSONArray(s.counterparties.map(::encodePartner)))
        .put("customers", JSONArray(s.customers.map(::encodeCustomer)))
        .put("fxTrades", JSONArray(s.fxTrades.map(::encodeFxTrade)))
        .put("investments", JSONArray(s.investments.map(::encodeInvestment)))
        .put("rates", encodeRateBook(s.rateBook))
        .put(
            "defaults",
            JSONObject()
                .put("reportingCurrency", s.settings.reportingCurrency)
                .put("tradeCurrency", s.settings.tradeCurrency)
                .put("ledgerTableView", s.settings.ledgerTableView),
        )
        .put("activeAssets", JSONObject(s.settings.activeAssetOverrides.mapValues { it.value as Any }))
        .put("teamMembers", JSONArray(s.teamMembers.map(::encodeTeamMember)))
        .put("expenses", JSONArray(s.expenses.map(::encodeExpense)))

    private fun decode(json: JSONObject): UserDataSnapshot {
        val drawerJson = json.getJSONObject("cashDrawer")
        val balancesJson = drawerJson.getJSONObject("balances")
        val balances = balancesJson.keys().asSequence().associateWith { balancesJson.getDouble(it) }
        val defaults = json.getJSONObject("defaults")
        val overridesJson = json.getJSONObject("activeAssets")
        val overrides = overridesJson.keys().asSequence().associateWith { overridesJson.getBoolean(it) }
        return UserDataSnapshot(
            cashDrawer = CashDrawer(balances, drawerJson.getString("lastCount")),
            counterparties = json.getJSONArray("counterparties").mapObjects(::decodePartner),
            customers = json.getJSONArray("customers").mapObjects(::decodeCustomer),
            fxTrades = json.getJSONArray("fxTrades").mapObjects(::decodeFxTrade),
            investments = json.getJSONArray("investments").mapObjects(::decodeInvestment),
            rateBook = decodeRateBook(json.getJSONObject("rates")),
            settings = LedgerSettings(
                reportingCurrency = defaults.getString("reportingCurrency"),
                tradeCurrency = defaults.getString("tradeCurrency"),
                activeAssetOverrides = overrides,
                // optBoolean keeps blobs written before v20 (no key) decoding cleanly.
                ledgerTableView = defaults.optBoolean("ledgerTableView", false),
            ),
            // teamMembers/expenses are v20 additions — absent in older blobs.
            teamMembers = json.optJSONArray("teamMembers")?.mapObjects(::decodeTeamMember) ?: emptyList(),
            expenses = json.optJSONArray("expenses")?.mapObjects(::decodeExpense) ?: emptyList(),
        )
    }

    private fun encodeTeamMember(m: TeamMember): JSONObject = JSONObject()
        .put("id", m.id)
        .put("name", m.name)
        .put("role", m.role)
        .put("phone", m.phone ?: JSONObject.NULL)
        .put("initial", m.initial)

    private fun decodeTeamMember(o: JSONObject): TeamMember = TeamMember(
        id = o.getString("id"),
        name = o.getString("name"),
        role = o.getString("role"),
        phone = o.optStringOrNull("phone"),
        initial = o.getString("initial"),
    )

    private fun encodeExpense(e: Expense): JSONObject = JSONObject()
        .put("id", e.id)
        .put("amount", e.amount)
        .put("currency", e.currency)
        .put("teamMemberId", e.teamMemberId)
        .put("note", e.note ?: JSONObject.NULL)
        .put("ts", e.timestampMillis)
        .put("dateLabel", e.dateLabel)

    private fun decodeExpense(o: JSONObject): Expense = Expense(
        id = o.getString("id"),
        amount = o.getDouble("amount"),
        currency = o.getString("currency"),
        teamMemberId = o.getString("teamMemberId"),
        note = o.optStringOrNull("note"),
        timestampMillis = o.getLong("ts"),
        dateLabel = o.getString("dateLabel"),
    )

    private fun encodePartner(p: Counterparty): JSONObject = JSONObject()
        .put("id", p.id)
        .put("name", p.name)
        .put("shortName", p.shortName)
        .put("phone", p.phone)
        .put("city", p.city.code)
        .put("initial", p.initial)
        .put("tier", p.tier.name)
        .put("hawalas", JSONArray(p.hawalas.map(::encodeHawala)))

    private fun decodePartner(o: JSONObject): Counterparty = Counterparty(
        id = o.getString("id"),
        name = o.getString("name"),
        shortName = o.getString("shortName"),
        phone = o.getString("phone"),
        city = City.fromCode(o.getString("city")),
        initial = o.getString("initial"),
        tier = PartnerTier.valueOf(o.getString("tier")),
        hawalas = o.getJSONArray("hawalas").mapObjects(::decodeHawala),
    )

    private fun encodeHawala(h: Hawala): JSONObject = JSONObject()
        .put("id", h.id)
        .put("type", h.type.name)
        .put("from", h.fromCity.code)
        .put("to", h.toCity.code)
        .put("sender", h.senderName)
        .put("receiver", h.receiverName)
        .put("amount", h.amount)
        .put("currency", h.currency)
        .put("commissionPercent", h.commissionPercent)
        .put("commissionMode", h.commissionMode.name)
        .put("commissionAmount", h.commissionAmount ?: JSONObject.NULL)
        .put("code", h.pickupCode)
        .put("status", h.status.name)
        .put("ts", h.timestampMillis)
        .put("dateLabel", h.dateLabel)
        .put("note", h.note ?: JSONObject.NULL)
        .put("senderCustomerId", h.senderCustomerId ?: JSONObject.NULL)

    private fun decodeHawala(o: JSONObject): Hawala = Hawala(
        id = o.getString("id"),
        type = HawalaType.valueOf(o.getString("type")),
        fromCity = City.fromCode(o.getString("from")),
        toCity = City.fromCode(o.getString("to")),
        senderName = o.getString("sender"),
        receiverName = o.getString("receiver"),
        amount = o.getDouble("amount"),
        currency = o.getString("currency"),
        commissionPercent = o.getDouble("commissionPercent"),
        commissionMode = CommissionMode.valueOf(o.getString("commissionMode")),
        commissionAmount = if (o.isNull("commissionAmount")) null else o.getDouble("commissionAmount"),
        pickupCode = o.getString("code"),
        status = HawalaStatus.valueOf(o.getString("status")),
        timestampMillis = o.getLong("ts"),
        dateLabel = o.getString("dateLabel"),
        note = o.optStringOrNull("note"),
        senderCustomerId = o.optStringOrNull("senderCustomerId"),
    )

    private fun encodeCustomer(c: Customer): JSONObject = JSONObject()
        .put("id", c.id)
        .put("name", c.name)
        .put("shortName", c.shortName)
        .put("phone", c.phone)
        .put("city", c.city.code)
        .put("colorIndex", c.colorIndex)
        .put("initial", c.initial)
        .put("accountOpened", c.accountOpenedLabel)
        .put("notes", c.notes ?: JSONObject.NULL)
        .put("transactions", JSONArray(c.transactions.map(::encodeCustomerTx)))

    private fun decodeCustomer(o: JSONObject): Customer = Customer(
        id = o.getString("id"),
        name = o.getString("name"),
        shortName = o.getString("shortName"),
        phone = o.getString("phone"),
        city = City.fromCode(o.getString("city")),
        colorIndex = o.getInt("colorIndex"),
        initial = o.getString("initial"),
        accountOpenedLabel = o.getString("accountOpened"),
        notes = o.optStringOrNull("notes"),
        transactions = o.getJSONArray("transactions").mapObjects(::decodeCustomerTx),
    )

    private fun encodeCustomerTx(t: CustomerTransaction): JSONObject = JSONObject()
        .put("id", t.id)
        .put("type", t.type.name)
        .put("amount", t.amount)
        .put("currency", t.currency)
        .put("dateLabel", t.dateLabel)
        .put("ts", t.timestampMillis)
        .put("note", t.note ?: JSONObject.NULL)
        .put("hawalaId", t.hawalaId ?: JSONObject.NULL)
        .put("photoUris", JSONArray(t.photoUris))
        .put(
            "conversion",
            t.conversion?.let {
                JSONObject()
                    .put("receivedAmount", it.receivedAmount)
                    .put("receivedCurrency", it.receivedCurrency)
                    .put("rate", it.rate)
                    .put("creditedAmount", it.creditedAmount)
                    .put("creditedCurrency", it.creditedCurrency)
            } ?: JSONObject.NULL,
        )

    private fun decodeCustomerTx(o: JSONObject): CustomerTransaction = CustomerTransaction(
        id = o.getString("id"),
        type = CustomerTxType.valueOf(o.getString("type")),
        amount = o.getDouble("amount"),
        currency = o.getString("currency"),
        dateLabel = o.getString("dateLabel"),
        timestampMillis = o.getLong("ts"),
        note = o.optStringOrNull("note"),
        hawalaId = o.optStringOrNull("hawalaId"),
        photoUris = o.optJSONArray("photoUris")?.let { arr ->
            (0 until arr.length()).map { arr.getString(it) }
        } ?: emptyList(),
        conversion = if (o.isNull("conversion")) null else o.getJSONObject("conversion").let {
            CurrencyConversion(
                receivedAmount = it.getDouble("receivedAmount"),
                receivedCurrency = it.getString("receivedCurrency"),
                rate = it.getDouble("rate"),
                creditedAmount = it.getDouble("creditedAmount"),
                creditedCurrency = it.getString("creditedCurrency"),
            )
        },
    )

    private fun encodeFxTrade(t: FxTrade): JSONObject = JSONObject()
        .put("id", t.id)
        .put("side", t.side.name)
        .put("fromCurrency", t.fromCurrency)
        .put("toCurrency", t.toCurrency)
        .put("fromAmount", t.fromAmount)
        .put("toAmount", t.toAmount)
        .put("rate", t.rate)
        .put("realized", t.realizedPnlAfn ?: JSONObject.NULL)
        .put("ts", t.timestampMillis)
        .put("dateLabel", t.dateLabel)
        .put("note", t.note ?: JSONObject.NULL)

    private fun decodeFxTrade(o: JSONObject): FxTrade = FxTrade(
        id = o.getString("id"),
        side = FxSide.valueOf(o.getString("side")),
        fromCurrency = o.getString("fromCurrency"),
        toCurrency = o.getString("toCurrency"),
        fromAmount = o.getDouble("fromAmount"),
        toAmount = o.getDouble("toAmount"),
        rate = o.getDouble("rate"),
        realizedPnlAfn = if (o.isNull("realized")) null else o.getDouble("realized"),
        timestampMillis = o.getLong("ts"),
        dateLabel = o.getString("dateLabel"),
        note = o.optStringOrNull("note"),
    )

    private fun encodeInvestment(i: Investment): JSONObject = JSONObject()
        .put("id", i.id)
        .put("ts", i.timestampMillis)
        .put("dateLabel", i.dateLabel)
        .put("asset", i.assetCode)
        .put("amount", i.amount)
        .put("type", i.type.name)
        .put("note", i.note ?: JSONObject.NULL)

    private fun decodeInvestment(o: JSONObject): Investment = Investment(
        id = o.getString("id"),
        timestampMillis = o.getLong("ts"),
        dateLabel = o.getString("dateLabel"),
        assetCode = o.getString("asset"),
        amount = o.getDouble("amount"),
        type = InvestmentType.valueOf(o.getString("type")),
        note = o.optStringOrNull("note"),
    )

    private fun encodeRate(r: Rate): JSONObject = JSONObject()
        .put("buy", r.buy)
        .put("sell", r.sell)
        .put("prev", r.previousSell)
        .put("delta", r.deltaPercent)

    private fun decodeRate(o: JSONObject): Rate = Rate(
        buy = o.getDouble("buy"),
        sell = o.getDouble("sell"),
        previousSell = o.getDouble("prev"),
        deltaPercent = o.getDouble("delta"),
    )

    private fun encodeRateBook(book: RateBook): JSONObject = JSONObject()
        .put("perAsset", JSONObject(book.perAsset.mapValues { encodeRate(it.value) as Any }))
        .put("pairs", JSONObject(book.pairs.entries.associate { it.key.name to encodeRate(it.value) as Any }))

    private fun decodeRateBook(o: JSONObject): RateBook {
        val perAssetJson = o.getJSONObject("perAsset")
        val pairsJson = o.getJSONObject("pairs")
        return RateBook(
            perAsset = perAssetJson.keys().asSequence()
                .associateWith { decodeRate(perAssetJson.getJSONObject(it)) },
            pairs = pairsJson.keys().asSequence()
                .associate { RatePair.valueOf(it) to decodeRate(pairsJson.getJSONObject(it)) },
        )
    }

    private fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
        (0 until length()).map { transform(getJSONObject(it)) }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else getString(key)

    companion object {
        private const val PREFS_FILE = "daftar_user_data"
        private const val KEY_PREFIX = "daftar_data_"
    }
}

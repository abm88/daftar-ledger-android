package com.daftar.app.domain.model

enum class AssetType { CURRENCY, METAL }

/**
 * An asset the saraf can hold in the drawer: fiat currency (whole units) or a
 * precious metal (tracked in grams, displayed also in tola).
 *
 * Rates are always quoted canonically as "1 unit of asset = N AFN"; AFN is the base.
 */
data class Asset(
    val code: String,
    val type: AssetType,
    val name: String,
    val pashtoName: String,
    val symbol: String,
    val decimals: Int,
    /** Default assets (USD, AFN, PKR) cannot be deactivated. */
    val isDefault: Boolean,
    /** Whether the asset is tracked when the saraf has expressed no preference. */
    val activeByDefault: Boolean,
    val emoji: String,
    val isBase: Boolean = false,
)

object AssetCatalog {

    /** 1 tola = 11.6638 g — the standard South Asian / Afghan jeweler unit. */
    const val TOLA_GRAMS = 11.6638

    val USD = Asset("USD", AssetType.CURRENCY, "US Dollar", "ډالر", "$", 2, isDefault = true, activeByDefault = true, emoji = "🇺🇸")
    val AFN = Asset("AFN", AssetType.CURRENCY, "Afghani", "افغانۍ", "؋", 0, isDefault = true, activeByDefault = true, emoji = "🇦🇫", isBase = true)
    val PKR = Asset("PKR", AssetType.CURRENCY, "Pakistani Rupee", "روپۍ", "₨", 0, isDefault = true, activeByDefault = true, emoji = "🇵🇰")
    val EUR = Asset("EUR", AssetType.CURRENCY, "Euro", "یورو", "€", 2, isDefault = false, activeByDefault = false, emoji = "🇪🇺")
    val GBP = Asset("GBP", AssetType.CURRENCY, "British Pound", "پاؤنډ", "£", 2, isDefault = false, activeByDefault = false, emoji = "🇬🇧")
    val SAR = Asset("SAR", AssetType.CURRENCY, "Saudi Riyal", "ریال", "﷼", 2, isDefault = false, activeByDefault = false, emoji = "🇸🇦")
    val AED = Asset("AED", AssetType.CURRENCY, "UAE Dirham", "درهم", "د.إ", 2, isDefault = false, activeByDefault = false, emoji = "🇦🇪")
    val GOLD = Asset("GOLD", AssetType.METAL, "Gold", "طلا", "g", 2, isDefault = false, activeByDefault = false, emoji = "🟡")
    val SILVER = Asset("SILVER", AssetType.METAL, "Silver", "سپین زر", "g", 1, isDefault = false, activeByDefault = false, emoji = "⚪")

    val ALL: List<Asset> = listOf(USD, AFN, PKR, EUR, GBP, SAR, AED, GOLD, SILVER)

    /** The three ledger currencies every balance sheet in the app is broken down by. */
    val LEDGER_CURRENCIES: List<String> = listOf("USD", "AFN", "PKR")

    fun byCode(code: String): Asset? = ALL.firstOrNull { it.code == code }

    fun gramsToTola(grams: Double): Double = grams / TOLA_GRAMS

    fun decimalsFor(code: String): Int = byCode(code)?.decimals ?: 0

    fun symbolFor(code: String): String = byCode(code)?.symbol ?: code
}

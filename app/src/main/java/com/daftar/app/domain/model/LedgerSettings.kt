package com.daftar.app.domain.model

/**
 * Saraf-configurable defaults.
 *
 * @property reportingCurrency drives drawer totals, P&L, ROI, and "≈" equivalents.
 * @property tradeCurrency pre-selected on new FX trades, hawalas, and customer entries.
 * @property activeAssetOverrides per-asset activation choices layered over [Asset.activeByDefault].
 */
data class LedgerSettings(
    val reportingCurrency: String = "AFN",
    val tradeCurrency: String = "USD",
    val activeAssetOverrides: Map<String, Boolean> = emptyMap(),
) {
    fun isAssetActive(asset: Asset): Boolean =
        activeAssetOverrides[asset.code] ?: asset.activeByDefault

    fun activeAssets(): List<Asset> = AssetCatalog.ALL.filter(::isAssetActive)

    fun activeCurrencies(): List<Asset> =
        activeAssets().filter { it.type == AssetType.CURRENCY }
}

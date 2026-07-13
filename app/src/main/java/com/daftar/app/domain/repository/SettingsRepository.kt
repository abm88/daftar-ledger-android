package com.daftar.app.domain.repository

import com.daftar.app.domain.model.LedgerSettings
import com.daftar.app.domain.model.ShopProfile
import kotlinx.coroutines.flow.StateFlow

/** Saraf preferences and shop identity. */
interface SettingsRepository {
    val settings: StateFlow<LedgerSettings>
    val shopProfile: StateFlow<ShopProfile>

    suspend fun setReportingCurrency(code: String)
    suspend fun setTradeCurrency(code: String)
    suspend fun setAssetActive(code: String, active: Boolean)

    /** Swap in another account's preferences (per-user persistence restore / blank-shop reset). */
    suspend fun replaceSettings(settings: LedgerSettings)
}

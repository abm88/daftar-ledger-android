package com.daftar.app.data.repository

import com.daftar.app.domain.model.LedgerSettings
import com.daftar.app.domain.model.ShopProfile
import com.daftar.app.domain.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Singleton
class InMemorySettingsRepository @Inject constructor() : SettingsRepository {

    private val state = MutableStateFlow(LedgerSettings())
    override val settings: StateFlow<LedgerSettings> = state.asStateFlow()

    private val profile = MutableStateFlow(ShopProfile())
    override val shopProfile: StateFlow<ShopProfile> = profile.asStateFlow()

    override suspend fun setReportingCurrency(code: String) {
        state.update { it.copy(reportingCurrency = code) }
    }

    override suspend fun setTradeCurrency(code: String) {
        state.update { it.copy(tradeCurrency = code) }
    }

    override suspend fun setAssetActive(code: String, active: Boolean) {
        state.update { it.copy(activeAssetOverrides = it.activeAssetOverrides + (code to active)) }
    }

    override suspend fun replaceSettings(settings: LedgerSettings) {
        state.value = settings
    }
}

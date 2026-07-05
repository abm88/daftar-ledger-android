package com.daftar.app.ui.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daftar.app.core.format.Formatters
import com.daftar.app.core.time.TimeProvider
import com.daftar.app.domain.model.AssetCatalog
import com.daftar.app.domain.model.AssetType
import com.daftar.app.domain.model.CashDrawer
import com.daftar.app.domain.model.LedgerEntry
import com.daftar.app.domain.model.LedgerSettings
import com.daftar.app.domain.model.RateBook
import com.daftar.app.domain.model.ShopProfile
import com.daftar.app.domain.repository.CashRepository
import com.daftar.app.domain.repository.RatesRepository
import com.daftar.app.domain.repository.SettingsRepository
import com.daftar.app.domain.usecase.ActivityFeedBuilder
import com.daftar.app.domain.usecase.CurrencyConverter
import com.daftar.app.domain.usecase.InitialSetupStatus
import com.daftar.app.ui.common.ToastCenter
import com.daftar.app.ui.common.ToastIcon
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CashCellUi(
    val code: String,
    val symbol: String,
    val isMetal: Boolean,
    val amountText: String,
    val unitSuffix: String?,
    val amountFullText: String,
    val sublineTola: String?,
    val equivalentText: String,
    val revalText: String?,
    val revalPositive: Boolean,
)

data class HomeUiState(
    val profile: ShopProfile = ShopProfile(),
    val setupNeeded: Boolean = false,
    val syncing: Boolean = false,
    val lastCountLabel: String = "",
    val assetCount: Int = 0,
    val cells: List<CashCellUi> = emptyList(),
    val reportingCurrency: String = "AFN",
    val drawerTotalText: String = "0",
    val totalRevalText: String? = null,
    val totalRevalPositive: Boolean = false,
    val feedPreview: List<LedgerEntry> = emptyList(),
    val feedTotal: Int = 0,
    val todayStartMillis: Long = 0,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    cashRepository: CashRepository,
    ratesRepository: RatesRepository,
    settingsRepository: SettingsRepository,
    activityFeedBuilder: ActivityFeedBuilder,
    initialSetupStatus: InitialSetupStatus,
    private val converter: CurrencyConverter,
    private val timeProvider: TimeProvider,
    private val toastCenter: ToastCenter,
) : ViewModel() {

    private val syncing = MutableStateFlow(false)

    val uiState = combine(
        cashRepository.drawer,
        ratesRepository.rateBook,
        settingsRepository.settings,
        activityFeedBuilder.observe(),
        combine(initialSetupStatus.isNeeded(), syncing, settingsRepository.shopProfile) { a, b, c -> Triple(a, b, c) },
    ) { drawer, rates, settings, feed, (setupNeeded, isSyncing, profile) ->
        buildState(drawer, rates, settings, feed, setupNeeded, isSyncing, profile)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    fun sync() {
        if (syncing.value) return
        viewModelScope.launch {
            syncing.value = true
            delay(1100)
            syncing.value = false
            toastCenter.show("Ledger synced", ToastIcon.CHECK)
        }
    }

    private fun buildState(
        drawer: CashDrawer,
        rates: RateBook,
        settings: LedgerSettings,
        feed: List<LedgerEntry>,
        setupNeeded: Boolean,
        isSyncing: Boolean,
        profile: ShopProfile,
    ): HomeUiState {
        val reporting = settings.reportingCurrency
        val repDecimals = AssetCatalog.decimalsFor(reporting)
        val repSymbol = AssetCatalog.symbolFor(reporting)
        val activeAssets = settings.activeAssets()

        var totalRep = 0.0
        var totalRepPrev = 0.0

        val cells = activeAssets.map { asset ->
            val amount = drawer.balanceOf(asset.code)
            val inReporting = converter.toReporting(asset.code, amount, rates, settings)
            if (amount != 0.0) {
                totalRep += inReporting
                totalRepPrev += when (asset.code) {
                    reporting -> amount
                    "AFN" -> inReporting
                    else -> {
                        val rate = rates.assetRate(asset.code)
                        val prevAfn = amount * (rate?.previousSell ?: rate?.sell ?: 0.0)
                        if (reporting == "AFN") prevAfn
                        else rates.sellRateToAfn(reporting).let { r -> if (r > 0) prevAfn / r else prevAfn }
                    }
                }
            }

            // Rate-move P&L for non-reporting, non-AFN assets with holdings
            var revalText: String? = null
            var revalPositive = false
            val equivalent: String = when {
                asset.code == reporting -> "BASE"
                amount <= 0.0 -> "—"
                else -> {
                    if (asset.code != "AFN") {
                        val rate = rates.assetRate(asset.code)
                        if (rate != null) {
                            val prevAfn = amount * rate.previousSell
                            val prevRep = if (reporting == "AFN") prevAfn
                            else rates.sellRateToAfn(reporting).let { r -> if (r > 0) prevAfn / r else prevAfn }
                            val reval = inReporting - prevRep
                            if (abs(reval) >= 0.5) {
                                revalPositive = reval > 0
                                revalText = (if (reval > 0) "+" else "−") +
                                    Formatters.compact(reval, repDecimals) + " " + repSymbol
                            }
                        }
                    }
                    "≈ " + Formatters.compact(inReporting, repDecimals) + " " + repSymbol
                }
            }

            val isMetal = asset.type == AssetType.METAL
            val (amountText, unitSuffix) = when {
                isMetal && amount < 1000 -> Formatters.number(amount, asset.decimals) to "g"
                isMetal -> Formatters.number(amount / 1000, 2) to "kg"
                else -> Formatters.compact(amount, asset.decimals) to null
            }

            CashCellUi(
                code = asset.code,
                symbol = asset.symbol,
                isMetal = isMetal,
                amountText = amountText,
                unitSuffix = unitSuffix,
                amountFullText = Formatters.number(amount, asset.decimals) + " " + asset.code,
                sublineTola = if (isMetal) {
                    if (amount > 0) Formatters.number(AssetCatalog.gramsToTola(amount), 2) + " tola" else "0 tola"
                } else null,
                equivalentText = equivalent,
                revalText = revalText,
                revalPositive = revalPositive,
            )
        }

        val totalReval = totalRep - totalRepPrev

        return HomeUiState(
            profile = profile,
            setupNeeded = setupNeeded,
            syncing = isSyncing,
            lastCountLabel = drawer.lastCountLabel,
            assetCount = activeAssets.size,
            cells = cells,
            reportingCurrency = reporting,
            drawerTotalText = Formatters.number(totalRep, repDecimals),
            totalRevalText = if (abs(totalReval) >= 0.5) {
                (if (totalReval > 0) "+" else "−") + Formatters.number(totalReval, repDecimals) + " from rate move"
            } else null,
            totalRevalPositive = totalReval > 0,
            feedPreview = feed.take(FEED_PREVIEW_LIMIT),
            feedTotal = feed.size,
            todayStartMillis = timeProvider.startOfTodayMillis(),
        )
    }

    private companion object {
        const val FEED_PREVIEW_LIMIT = 15
    }
}

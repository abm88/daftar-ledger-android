package com.daftar.app.domain.usecase

import com.daftar.app.core.format.Formatters
import com.daftar.app.core.time.TimeProvider
import com.daftar.app.domain.model.AssetType
import com.daftar.app.domain.model.CashDrawer
import com.daftar.app.domain.model.CommissionMode
import com.daftar.app.domain.model.Counterparty
import com.daftar.app.domain.model.FxTrade
import com.daftar.app.domain.model.HawalaStatus
import com.daftar.app.domain.model.HawalaType
import com.daftar.app.domain.model.LedgerPeriod
import com.daftar.app.domain.model.LedgerSettings
import com.daftar.app.domain.model.PnlItem
import com.daftar.app.domain.model.PnlReport
import com.daftar.app.domain.model.PnlSource
import com.daftar.app.domain.model.RateBook
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Profit & loss aggregation across the three sources:
 *  - realized FX trade P&L,
 *  - hawala commissions on paid hawalas (converted to AFN),
 *  - unrealized revaluation of drawer holdings from the day's rate move
 *    (snapshot — only included for TODAY / ALL periods).
 */
@Singleton
class PnlCalculator @Inject constructor(
    private val timeProvider: TimeProvider,
) {

    fun compute(
        period: LedgerPeriod,
        trades: List<FxTrade>,
        partners: List<Counterparty>,
        drawer: CashDrawer,
        rates: RateBook,
        settings: LedgerSettings,
    ): PnlReport {
        val now = timeProvider.nowMillis()
        val from = when (period) {
            LedgerPeriod.TODAY -> timeProvider.startOfTodayMillis()
            LedgerPeriod.YESTERDAY -> timeProvider.startOfTodayMillis() - DAY_MS
            LedgerPeriod.WEEK -> timeProvider.startOfTodayMillis() - 6 * DAY_MS
            LedgerPeriod.MONTH -> timeProvider.startOfMonthMillis()
            LedgerPeriod.ALL -> 0L
        }
        val to = if (period == LedgerPeriod.YESTERDAY) timeProvider.startOfTodayMillis() else now + 1

        val items = mutableListOf<PnlItem>()

        // ---- Realized: FX trades ----
        var fxRealized = 0.0
        var fxCount = 0
        trades.forEach { t ->
            val realized = t.realizedPnlAfn ?: return@forEach
            if (t.timestampMillis < from || t.timestampMillis >= to) return@forEach
            fxRealized += realized
            fxCount++
            items += PnlItem(
                timestampMillis = t.timestampMillis,
                dateLabel = t.dateLabel,
                label = "Sold ${Formatters.amount(t.fromAmount, t.fromCurrency)} ${t.fromCurrency} → ${t.toCurrency} @ ${Formatters.ratePlain(t.rate)}",
                amountAfn = realized,
                source = PnlSource.FX_TRADE,
            )
        }

        // ---- Realized: hawala commissions on paid hawalas ----
        var commissionTotal = 0.0
        var hawalaCount = 0
        partners.forEach { partner ->
            partner.hawalas.forEach h@{ h ->
                if (h.type == HawalaType.SETTLEMENT) return@h
                if (h.status != HawalaStatus.PAID) return@h
                if (h.timestampMillis < from || h.timestampMillis >= to) return@h
                // Deliberate deviation from v18: the prototype computes commission
                // from the percent field only, so fixed-mode commissions contribute 0
                // to P&L. Dropping real income is a prototype gap, not a spec — we
                // count the resolved amount for both modes.
                val commissionInOriginal = h.resolvedCommissionAmount
                if (commissionInOriginal <= 0.0) return@h
                val commissionAfn = rates.toAfn(h.currency, commissionInOriginal)
                commissionTotal += commissionAfn
                hawalaCount++
                val rateLabel = if (h.commissionMode == CommissionMode.FIXED) {
                    "fixed ${Formatters.amount(commissionInOriginal, h.currency)} ${h.currency}"
                } else {
                    "${Formatters.rate(h.commissionPercent, 1)}% on ${Formatters.number(h.amount)} ${h.currency}"
                }
                items += PnlItem(
                    timestampMillis = h.timestampMillis,
                    dateLabel = h.dateLabel,
                    label = "${if (h.type == HawalaType.SEND) "Sent" else "Received"} hawala · " +
                        "${h.fromCity.code}→${h.toCity.code} · $rateLabel",
                    amountAfn = commissionAfn,
                    source = PnlSource.HAWALA_COMMISSION,
                    // v18 falls back to the full name when shortName is blank.
                    partnerName = partner.shortName.ifBlank { partner.name },
                )
            }
        }

        // ---- Unrealized: revaluation snapshot ----
        val includesReval = period == LedgerPeriod.TODAY || period == LedgerPeriod.ALL
        var reval = 0.0
        if (includesReval) {
            settings.activeAssets().forEach { asset ->
                if (asset.code == "AFN") return@forEach
                val amt = drawer.balanceOf(asset.code)
                if (amt <= 0) return@forEach
                val rate = rates.assetRate(asset.code) ?: return@forEach
                val diff = (rate.sell - rate.previousSell) * amt
                if (abs(diff) < 0.5) return@forEach
                reval += diff
                val decimals = if (rate.sell < 1) 3 else 2
                items += PnlItem(
                    timestampMillis = now,
                    dateLabel = "snapshot",
                    label = "Reval ${asset.code} · ${Formatters.number(amt, asset.decimals)}" +
                        (if (asset.type == AssetType.METAL) "g" else "") +
                        " · rate ${Formatters.rate(rate.previousSell, decimals)} → ${Formatters.rate(rate.sell, decimals)}",
                    amountAfn = diff,
                    source = PnlSource.REVALUATION,
                )
            }
        }

        return PnlReport(
            period = period,
            fxRealizedAfn = fxRealized,
            fxTradeCount = fxCount,
            hawalaCommissionAfn = commissionTotal,
            hawalaCount = hawalaCount,
            unrealizedRevaluationAfn = reval,
            includesRevaluation = includesReval,
            items = items.sortedByDescending { it.timestampMillis },
        )
    }

    private companion object {
        const val DAY_MS = 86_400_000L
    }
}

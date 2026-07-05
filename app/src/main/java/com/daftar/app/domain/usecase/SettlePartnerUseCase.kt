package com.daftar.app.domain.usecase

import com.daftar.app.core.format.Formatters
import com.daftar.app.core.time.TimeProvider
import com.daftar.app.domain.model.Hawala
import com.daftar.app.domain.model.HawalaStatus
import com.daftar.app.domain.model.HawalaType
import com.daftar.app.domain.model.SYNTHETIC_CODE
import com.daftar.app.domain.repository.PartnerRepository
import javax.inject.Inject

/**
 * Zeros out a partner's open position by posting one signed SETTLEMENT entry per
 * open currency (delta = −position). The chosen settlement currency and manual
 * rates only affect what is physically handed over; the ledger records offsets
 * in each original currency.
 */
class SettlePartnerUseCase @Inject constructor(
    private val partnerRepository: PartnerRepository,
    private val positionCalculator: PositionCalculator,
    private val timeProvider: TimeProvider,
) {
    suspend operator fun invoke(partnerId: String, settleCurrency: String): Boolean {
        val partner = partnerRepository.partnerById(partnerId) ?: return false
        val position = positionCalculator.partnerPosition(partner)
        val open = position.openCurrencies()
        if (open.isEmpty()) return false

        val now = timeProvider.nowMillis()
        val dateLabel = Formatters.fullDateLabel(now)
        val entries = open.map { currency ->
            Hawala(
                id = "h_settle_${now}_$currency",
                type = HawalaType.SETTLEMENT,
                fromCity = partner.city,
                toCity = partner.city,
                senderName = "—",
                receiverName = "—",
                amount = -position[currency],
                currency = currency,
                commissionPercent = 0.0,
                pickupCode = SYNTHETIC_CODE,
                status = HawalaStatus.PAID,
                timestampMillis = now,
                dateLabel = dateLabel,
                note = "Settled in $settleCurrency @ rate sheet",
            )
        }
        partnerRepository.addHawalas(partnerId, entries)
        return true
    }
}

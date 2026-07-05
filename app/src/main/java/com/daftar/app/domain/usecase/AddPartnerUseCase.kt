package com.daftar.app.domain.usecase

import com.daftar.app.core.time.TimeProvider
import com.daftar.app.domain.model.City
import com.daftar.app.domain.model.Counterparty
import com.daftar.app.domain.model.Hawala
import com.daftar.app.domain.model.HawalaStatus
import com.daftar.app.domain.model.HawalaType
import com.daftar.app.domain.model.PartnerTier
import com.daftar.app.domain.model.SYNTHETIC_CODE
import com.daftar.app.domain.repository.PartnerRepository
import javax.inject.Inject

data class NewPartnerDraft(
    val name: String,
    val shortName: String,
    val initial: String,
    val phone: String,
    val city: City,
    val tier: PartnerTier,
    /** Per-currency opening balance; positive = they owe you, negative = you owe. */
    val openingBalances: Map<String, Double>,
)

/** Registers a partner; opening balances become synthetic paid entries (code 000000). */
class AddPartnerUseCase @Inject constructor(
    private val partnerRepository: PartnerRepository,
    private val timeProvider: TimeProvider,
) {
    suspend operator fun invoke(draft: NewPartnerDraft): Counterparty? {
        val name = draft.name.trim()
        if (name.isEmpty()) return null
        val now = timeProvider.nowMillis()

        val openingEntries = draft.openingBalances
            .filterValues { it != 0.0 }
            .map { (currency, value) ->
                Hawala(
                    id = "h_${now}_$currency",
                    type = if (value > 0) HawalaType.SEND else HawalaType.RECEIVE,
                    fromCity = draft.city,
                    toCity = draft.city,
                    senderName = "Opening balance",
                    receiverName = "Opening balance",
                    amount = kotlin.math.abs(value),
                    currency = currency,
                    commissionPercent = 0.0,
                    pickupCode = SYNTHETIC_CODE,
                    status = HawalaStatus.PAID,
                    timestampMillis = now - 1000,
                    dateLabel = "Opening",
                )
            }

        val partner = Counterparty(
            id = "cp_$now",
            name = name,
            shortName = draft.shortName.trim().ifEmpty { name.split(" ").first() },
            phone = draft.phone.trim().ifEmpty { "—" },
            city = draft.city,
            initial = draft.initial.trim().ifEmpty { name.take(1) },
            tier = draft.tier,
            hawalas = openingEntries,
        )
        partnerRepository.addPartner(partner)
        return partner
    }
}

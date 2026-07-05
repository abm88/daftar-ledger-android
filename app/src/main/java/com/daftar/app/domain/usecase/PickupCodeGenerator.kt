package com.daftar.app.domain.usecase

import com.daftar.app.domain.model.HawalaType
import com.daftar.app.domain.repository.PartnerRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sequential 6-digit pickup codes. Scans every partner's history for the highest
 * numeric code and returns the next one; settlement sentinels are ignored.
 */
@Singleton
class PickupCodeGenerator @Inject constructor(
    private val partnerRepository: PartnerRepository,
) {
    fun next(): String {
        var maxSeen = 100_000L
        partnerRepository.partners.value.forEach { partner ->
            partner.hawalas.forEach { h ->
                if (h.type == HawalaType.SETTLEMENT) return@forEach
                val numeric = h.pickupCode.toLongOrNull() ?: return@forEach
                if (numeric > maxSeen) maxSeen = numeric
            }
        }
        return (maxSeen + 1).toString().padStart(6, '0')
    }
}

package com.daftar.app.data.repository

import com.daftar.app.data.seed.SeedData
import com.daftar.app.domain.model.Counterparty
import com.daftar.app.domain.model.Hawala
import com.daftar.app.domain.model.HawalaStatus
import com.daftar.app.domain.repository.PartnerRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Singleton
class InMemoryPartnerRepository @Inject constructor(seed: SeedData) : PartnerRepository {

    private val state = MutableStateFlow(seed.counterparties)
    override val partners: StateFlow<List<Counterparty>> = state.asStateFlow()

    override suspend fun addPartner(partner: Counterparty) {
        state.update { listOf(partner) + it }
    }

    override suspend fun addHawala(partnerId: String, hawala: Hawala) {
        addHawalas(partnerId, listOf(hawala))
    }

    override suspend fun addHawalas(partnerId: String, hawalas: List<Hawala>) {
        state.update { partners ->
            partners.map { p ->
                if (p.id == partnerId) p.copy(hawalas = p.hawalas + hawalas) else p
            }
        }
    }

    override suspend fun markHawalaPaid(hawalaId: String, dateLabel: String) {
        state.update { partners ->
            partners.map { p ->
                p.copy(
                    hawalas = p.hawalas.map { h ->
                        if (h.id == hawalaId) h.copy(status = HawalaStatus.PAID, dateLabel = dateLabel) else h
                    },
                )
            }
        }
    }

    override suspend fun clearAll() {
        state.value = emptyList()
    }

    override fun partnerById(id: String): Counterparty? =
        state.value.firstOrNull { it.id == id }

    override fun findHawala(hawalaId: String): Pair<Hawala, Counterparty>? {
        state.value.forEach { partner ->
            partner.hawalas.firstOrNull { it.id == hawalaId }?.let { return it to partner }
        }
        return null
    }
}

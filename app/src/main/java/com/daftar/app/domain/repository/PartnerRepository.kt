package com.daftar.app.domain.repository

import com.daftar.app.domain.model.Counterparty
import com.daftar.app.domain.model.Hawala
import kotlinx.coroutines.flow.StateFlow

/** Counterparty sarafs and their hawala ledgers. Backed in-memory for now; API later. */
interface PartnerRepository {
    val partners: StateFlow<List<Counterparty>>

    suspend fun addPartner(partner: Counterparty)
    suspend fun addHawala(partnerId: String, hawala: Hawala)
    suspend fun addHawalas(partnerId: String, hawalas: List<Hawala>)
    suspend fun markHawalaPaid(hawalaId: String, dateLabel: String)

    fun partnerById(id: String): Counterparty?
    fun findHawala(hawalaId: String): Pair<Hawala, Counterparty>?
}

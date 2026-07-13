package com.daftar.app.domain.repository

import com.daftar.app.domain.model.Investment
import kotlinx.coroutines.flow.StateFlow

/** Owner equity contributions and withdrawals. */
interface InvestmentRepository {
    val investments: StateFlow<List<Investment>>

    suspend fun addInvestment(investment: Investment)

    /** Remove all entries — a fresh account starts with a blank shop. */
    suspend fun clearAll()

    /** Swap in another account's investment log (per-user persistence restore). */
    suspend fun replaceAll(investments: List<Investment>)
}

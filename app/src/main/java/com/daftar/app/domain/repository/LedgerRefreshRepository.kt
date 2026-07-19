package com.daftar.app.domain.repository

/** Explicit pull-to-refresh port for server-owned ledger state. */
fun interface LedgerRefreshRepository {
    suspend fun refresh()
}

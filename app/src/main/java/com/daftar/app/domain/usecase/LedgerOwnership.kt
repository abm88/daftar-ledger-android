package com.daftar.app.domain.usecase

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks which account the in-memory ledger belongs to. The seeded demo
 * ledger is unowned and is adopted by the first sign-in of the process; a
 * later sign-in by a different account must never see the previous owner's
 * entries. Goes away once ledger data is persisted per user.
 */
@Singleton
class LedgerOwnership @Inject constructor() {

    private var ownerId: String? = null

    /**
     * Claim the ledger for [userId]. Returns false when it belonged to a
     * different account, in which case the caller must wipe it before use.
     */
    @Synchronized
    fun claim(userId: String): Boolean {
        val keep = ownerId == null || ownerId == userId
        ownerId = userId
        return keep
    }

    @Synchronized
    fun assign(userId: String) {
        ownerId = userId
    }
}

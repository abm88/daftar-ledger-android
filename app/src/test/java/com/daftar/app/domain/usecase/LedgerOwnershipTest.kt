package com.daftar.app.domain.usecase

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerOwnershipTest {

    @Test
    fun `first claim keeps the existing in-memory ledger`() {
        val ownership = LedgerOwnership()
        assertTrue(ownership.claim("usr_a"))
    }

    @Test
    fun `re-claim by the same user keeps the ledger`() {
        val ownership = LedgerOwnership()
        ownership.claim("usr_a")
        assertTrue(ownership.claim("usr_a"))
    }

    @Test
    fun `claim by a different user does not keep the previous ledger`() {
        val ownership = LedgerOwnership()
        ownership.claim("usr_a")
        assertFalse(ownership.claim("usr_b"))
    }

    @Test
    fun `assign then claim by the same user keeps the ledger`() {
        val ownership = LedgerOwnership()
        ownership.assign("usr_a")
        assertTrue(ownership.claim("usr_a"))
        assertFalse(ownership.claim("usr_b"))
    }
}

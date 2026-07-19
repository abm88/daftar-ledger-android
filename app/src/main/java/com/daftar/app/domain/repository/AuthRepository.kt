package com.daftar.app.domain.repository

import com.daftar.app.domain.model.UserAccount
import kotlinx.coroutines.flow.StateFlow

/** Outcome of a register/login attempt, with a user-facing message on failure. */
sealed interface AuthResult {
    data class Success(val user: UserAccount) : AuthResult
    data class Failure(val message: String) : AuthResult
}

/**
 * Accounts and the active server session. Presentation depends only on this
 * port; bearer-token persistence and HTTP remain data-layer concerns.
 */
interface AuthRepository {
    /** The signed-in saraf, or null when the auth gate should show. Restored across launches. */
    val sessionUser: StateFlow<UserAccount?>

    /** Create an account and start a session for it. Validates name, email, and password. */
    suspend fun register(name: String, email: String, password: String): AuthResult

    /** Start a session with existing credentials. */
    suspend fun login(email: String, password: String): AuthResult

    /** End the session but keep the account. */
    suspend fun signOut()
}

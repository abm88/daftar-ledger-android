package com.daftar.app.domain.usecase

import com.daftar.app.data.session.UserDataSession
import com.daftar.app.domain.repository.AuthRepository
import com.daftar.app.domain.repository.AuthResult
import javax.inject.Inject

/**
 * Signs a saraf in and restores their own shop data (v18): a returning user
 * gets their saved daftar back, and a user with no saved data starts from a
 * blank shop — the demo ledger is never shown to a signed-in account.
 */
class LoginUserUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val userDataSession: UserDataSession,
) {
    suspend operator fun invoke(email: String, password: String): AuthResult {
        val result = authRepository.login(email, password)
        if (result is AuthResult.Success) {
            userDataSession.beginSession(result.user.id)
        }
        return result
    }
}

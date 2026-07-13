package com.daftar.app.domain.usecase

import com.daftar.app.data.session.UserDataSession
import com.daftar.app.domain.repository.AuthRepository
import com.daftar.app.domain.repository.AuthResult
import javax.inject.Inject

/**
 * Signs up a new saraf. A fresh account opens a blank daftar which is
 * persisted immediately (v18 signup applies createBlankUserData and saves it)
 * — the demo ledger or a previous user's entries never leak into a new account.
 */
class RegisterUserUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val userDataSession: UserDataSession,
) {
    suspend operator fun invoke(name: String, email: String, password: String): AuthResult {
        val result = authRepository.register(name, email, password)
        if (result is AuthResult.Success) {
            userDataSession.beginSession(result.user.id)
        }
        return result
    }
}

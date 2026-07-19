package com.daftar.app.domain.usecase

import com.daftar.app.domain.repository.AuthRepository
import com.daftar.app.domain.repository.AuthResult
import javax.inject.Inject

/**
 * Signs a saraf in. The API-backed auth repository verifies the credentials,
 * restores the server session, and hydrates that saraf's isolated ledger.
 */
class LoginUserUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(email: String, password: String): AuthResult =
        authRepository.login(email, password)
}

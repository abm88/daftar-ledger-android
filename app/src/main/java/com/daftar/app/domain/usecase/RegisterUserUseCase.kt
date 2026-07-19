package com.daftar.app.domain.usecase

import com.daftar.app.domain.repository.AuthRepository
import com.daftar.app.domain.repository.AuthResult
import javax.inject.Inject

/**
 * Signs up a new saraf. The backend provisions the blank, user-scoped shop and
 * the auth adapter hydrates its initial assets, rates, drawer, and settings.
 */
class RegisterUserUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(name: String, email: String, password: String): AuthResult =
        authRepository.register(name, email, password)
}

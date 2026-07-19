package com.daftar.app.domain.usecase

import com.daftar.app.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * Revokes the server-side session and clears the local token/read cache.
 */
class SignOutUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke() {
        authRepository.signOut()
    }
}

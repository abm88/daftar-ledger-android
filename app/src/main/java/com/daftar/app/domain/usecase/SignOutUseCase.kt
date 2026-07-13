package com.daftar.app.domain.usecase

import com.daftar.app.data.session.UserDataSession
import com.daftar.app.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * Signs the saraf out. Like the prototype's logout handler, the shop data is
 * saved one final time before the session is cleared, so nothing typed in the
 * last moments is lost.
 */
class SignOutUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val userDataSession: UserDataSession,
) {
    suspend operator fun invoke() {
        userDataSession.endSession()
        authRepository.signOut()
    }
}

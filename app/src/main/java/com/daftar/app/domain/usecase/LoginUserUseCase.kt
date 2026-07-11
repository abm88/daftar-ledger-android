package com.daftar.app.domain.usecase

import com.daftar.app.domain.repository.AuthRepository
import com.daftar.app.domain.repository.AuthResult
import javax.inject.Inject

/**
 * Signs a saraf in. Until per-user persistence exists the ledger lives in
 * process memory, so login guarantees isolation rather than restoration: an
 * in-memory ledger owned by a different account is wiped, never shown to the
 * newly signed-in user.
 */
class LoginUserUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val clearShopData: ClearShopDataUseCase,
    private val ledgerOwnership: LedgerOwnership,
) {
    suspend operator fun invoke(email: String, password: String): AuthResult {
        val result = authRepository.login(email, password)
        if (result is AuthResult.Success && !ledgerOwnership.claim(result.user.id)) {
            clearShopData()
        }
        return result
    }
}

package com.daftar.app.domain.usecase

import com.daftar.app.domain.repository.AuthRepository
import com.daftar.app.domain.repository.AuthResult
import javax.inject.Inject

/**
 * Signs up a new saraf. A fresh account opens a blank daftar — the demo
 * ledger (or a previous user's entries) never leaks into a new account.
 */
class RegisterUserUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val clearShopData: ClearShopDataUseCase,
    private val ledgerOwnership: LedgerOwnership,
) {
    suspend operator fun invoke(name: String, email: String, password: String): AuthResult {
        val result = authRepository.register(name, email, password)
        if (result is AuthResult.Success) {
            clearShopData()
            ledgerOwnership.assign(result.user.id)
        }
        return result
    }
}

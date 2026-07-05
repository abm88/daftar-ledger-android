package com.daftar.app.domain.usecase

import com.daftar.app.domain.model.CommissionMode
import javax.inject.Inject
import javax.inject.Singleton

/** Commission math for hawala issuance — percent presets or a fixed amount. */
@Singleton
class CommissionCalculator @Inject constructor() {

    fun commissionAmount(
        amount: Double,
        mode: CommissionMode,
        percent: Double?,
        fixed: Double?,
    ): Double = when (mode) {
        CommissionMode.FIXED -> fixed ?: 0.0
        CommissionMode.PERCENT -> amount * (percent ?: DEFAULT_PERCENT) / 100.0
    }

    companion object {
        const val DEFAULT_PERCENT = 1.0
        val PERCENT_PRESETS = listOf(0.5, 0.8, 1.0, 1.2, 1.5, 2.0)
    }
}

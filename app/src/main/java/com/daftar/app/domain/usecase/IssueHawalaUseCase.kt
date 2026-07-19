package com.daftar.app.domain.usecase

import com.daftar.app.core.format.Formatters
import com.daftar.app.core.time.TimeProvider
import com.daftar.app.domain.model.City
import com.daftar.app.domain.model.CommissionMode
import com.daftar.app.domain.model.Hawala
import com.daftar.app.domain.model.HawalaStatus
import com.daftar.app.domain.model.HawalaType
import com.daftar.app.domain.repository.CustomerRepository
import com.daftar.app.domain.repository.LedgerMutationRepository
import com.daftar.app.domain.repository.PartnerRepository
import javax.inject.Inject

/** How the sending customer pays for a new hawala. */
enum class SenderMode { CASH, ACCOUNT }

data class HawalaDraft(
    val currency: String,
    val amount: Double,
    val fromCity: City,
    val toCity: City,
    val senderMode: SenderMode,
    val senderName: String,
    val receiverName: String,
    val senderCustomerId: String?,
    val partnerId: String,
    val commissionMode: CommissionMode,
    val commissionPercent: Double,
    val commissionFixed: Double,
    val pickupCode: String,
)

sealed interface IssueHawalaResult {
    data class Issued(val hawalaId: String, val debitedCustomerName: String?) : IssueHawalaResult
    data class Failure(val message: String) : IssueHawalaResult
    enum class Error : IssueHawalaResult {
        INVALID_AMOUNT, MISSING_RECEIVER, MISSING_SENDER,
        MISSING_SENDER_ACCOUNT, UNKNOWN_PARTNER,
    }
}

/**
 * Validates and issues a hawala through the backend mutation port. Account-mode
 * debits may create an advance: a negative customer balance is money owed to
 * the saraf, not a rejected insufficient-funds condition.
 */
class IssueHawalaUseCase @Inject constructor(
    private val partnerRepository: PartnerRepository,
    private val customerRepository: CustomerRepository,
    private val commissionCalculator: CommissionCalculator,
    private val mutations: LedgerMutationRepository,
    private val timeProvider: TimeProvider,
) {
    suspend operator fun invoke(draft: HawalaDraft): IssueHawalaResult {
        if (draft.amount <= 0) return IssueHawalaResult.Error.INVALID_AMOUNT
        if (draft.receiverName.isBlank()) return IssueHawalaResult.Error.MISSING_RECEIVER
        partnerRepository.partnerById(draft.partnerId)
            ?: return IssueHawalaResult.Error.UNKNOWN_PARTNER

        val commission = commissionCalculator.commissionAmount(
            draft.amount, draft.commissionMode, draft.commissionPercent, draft.commissionFixed,
        )

        val senderCustomer = if (draft.senderMode == SenderMode.ACCOUNT) {
            val customer = draft.senderCustomerId?.let(customerRepository::customerById)
                ?: return IssueHawalaResult.Error.MISSING_SENDER_ACCOUNT
            customer
        } else {
            if (draft.senderName.isBlank()) return IssueHawalaResult.Error.MISSING_SENDER
            null
        }

        val now = timeProvider.nowMillis()
        val hawalaId = "h_$now"
        val senderName = senderCustomer?.name ?: draft.senderName.trim()

        val created = try {
            mutations.issueHawala(
                counterpartyId = draft.partnerId,
                hawala = Hawala(
                    id = hawalaId,
                    type = HawalaType.SEND,
                    fromCity = draft.fromCity,
                    toCity = draft.toCity,
                    senderName = senderName,
                    receiverName = draft.receiverName.trim(),
                    amount = draft.amount,
                    currency = draft.currency,
                    commissionPercent = if (draft.commissionMode == CommissionMode.PERCENT) draft.commissionPercent else 0.0,
                    commissionMode = draft.commissionMode,
                    commissionAmount = commission,
                    pickupCode = draft.pickupCode,
                    status = HawalaStatus.PENDING,
                    timestampMillis = now,
                    dateLabel = "Just now",
                    senderCustomerId = senderCustomer?.id,
                ),
                senderMode = draft.senderMode.name.lowercase(),
                commissionFixed = draft.commissionFixed.takeIf { draft.commissionMode == CommissionMode.FIXED },
            )
        } catch (error: Exception) {
            return IssueHawalaResult.Failure(error.message ?: "Unable to issue hawala")
        }
        return IssueHawalaResult.Issued(created.id, senderCustomer?.name)
    }
}

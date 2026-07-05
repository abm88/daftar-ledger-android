package com.daftar.app.domain.usecase

import com.daftar.app.core.format.Formatters
import com.daftar.app.core.time.TimeProvider
import com.daftar.app.domain.model.City
import com.daftar.app.domain.model.CommissionMode
import com.daftar.app.domain.model.CustomerTransaction
import com.daftar.app.domain.model.CustomerTxType
import com.daftar.app.domain.model.Hawala
import com.daftar.app.domain.model.HawalaStatus
import com.daftar.app.domain.model.HawalaType
import com.daftar.app.domain.repository.CustomerRepository
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
    enum class Error : IssueHawalaResult {
        INVALID_AMOUNT, MISSING_RECEIVER, MISSING_SENDER,
        MISSING_SENDER_ACCOUNT, INSUFFICIENT_BALANCE, UNKNOWN_PARTNER,
    }
}

/**
 * Issues a hawala: validates the draft, appends the pending entry to the
 * partner ledger, and — in account mode — debits the sender's customer account
 * for amount + commission with a linked withdrawal entry.
 */
class IssueHawalaUseCase @Inject constructor(
    private val partnerRepository: PartnerRepository,
    private val customerRepository: CustomerRepository,
    private val positionCalculator: PositionCalculator,
    private val commissionCalculator: CommissionCalculator,
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
            val available = positionCalculator.customerBalance(customer)[draft.currency]
            if (available < draft.amount + commission - 0.5) {
                return IssueHawalaResult.Error.INSUFFICIENT_BALANCE
            }
            customer
        } else {
            if (draft.senderName.isBlank()) return IssueHawalaResult.Error.MISSING_SENDER
            null
        }

        val now = timeProvider.nowMillis()
        val hawalaId = "h_$now"
        val senderName = senderCustomer?.name ?: draft.senderName.trim()

        partnerRepository.addHawala(
            draft.partnerId,
            Hawala(
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
        )

        if (senderCustomer != null) {
            val commissionNote = if (draft.commissionMode == CommissionMode.FIXED) {
                "incl. ${Formatters.amount(commission, draft.currency)} ${draft.currency} commission"
            } else {
                "incl. ${Formatters.rate(draft.commissionPercent, 1)}% commission"
            }
            customerRepository.addTransaction(
                senderCustomer.id,
                CustomerTransaction(
                    id = "ct_haw_$now",
                    type = CustomerTxType.WITHDRAWAL,
                    amount = draft.amount + commission,
                    currency = draft.currency,
                    dateLabel = Formatters.fullDateLabel(now),
                    timestampMillis = now,
                    note = "Hawala to ${draft.receiverName.trim()} · ${draft.fromCity.code} → " +
                        "${draft.toCity.code} · code ${draft.pickupCode} ($commissionNote)",
                    hawalaId = hawalaId,
                ),
            )
        }

        return IssueHawalaResult.Issued(hawalaId, senderCustomer?.name)
    }
}

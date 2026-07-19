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

/** Marks a pending hawala as paid out to the receiver. */
class MarkHawalaPaidUseCase @Inject constructor(
    private val mutations: LedgerMutationRepository,
) {
    suspend operator fun invoke(hawalaId: String) {
        mutations.markHawalaPaid(hawalaId)
    }
}

/**
 * Cancels a hawala: removes it from the partner ledger and reverses any linked
 * account debit (a send funded from a customer account). v20 Cancel Hawala.
 */
class CancelHawalaUseCase @Inject constructor(
    private val mutations: LedgerMutationRepository,
) {
    suspend operator fun invoke(hawalaId: String) {
        mutations.cancelHawala(hawalaId)
    }
}

/**
 * Phase two of a received hawala: pays it out as cash, or credits the net amount
 * to a customer account. Either way the hawala moves from pending → paid.
 */
class PayOutHawalaUseCase @Inject constructor(
    private val partnerRepository: PartnerRepository,
    private val mutations: LedgerMutationRepository,
) {
    /** @param creditCustomerId non-null pays into that account; null pays cash. */
    suspend operator fun invoke(hawalaId: String, creditCustomerId: String?) {
        val found = partnerRepository.findHawala(hawalaId) ?: return
        mutations.markHawalaPaid(hawalaId, creditCustomerId)
    }
}

/** Fields captured by the two-phase Receive form (phase one records it as pending). */
data class ReceiveHawalaDraft(
    val partnerId: String?,
    val pickupCode: String,
    val currency: String,
    val amount: Double,
    val senderName: String,
    val receiverName: String,
)

/**
 * Phase one of receiving a hawala: records the incoming transfer as pending
 * against its origin branch. No cash/account movement happens until payout.
 */
class RecordReceiveHawalaUseCase @Inject constructor(
    private val partnerRepository: PartnerRepository,
    private val codeGenerator: PickupCodeGenerator,
    private val mutations: LedgerMutationRepository,
    private val timeProvider: TimeProvider,
) {
    /** @return the new hawala id, or null when the draft is incomplete. */
    suspend operator fun invoke(draft: ReceiveHawalaDraft): String? {
        val partnerId = draft.partnerId ?: return null
        if (draft.amount <= 0) return null
        val partner = partnerRepository.partnerById(partnerId) ?: return null
        val now = timeProvider.nowMillis()
        val id = "hw_recv_$now"
        val hawala = Hawala(
            id = id,
            type = HawalaType.RECEIVE,
            // Cities are vestigial in v20 (the form dropped them); keep sensible defaults.
            fromCity = partner.city,
            toCity = City.KBL,
            senderName = draft.senderName.trim(),
            receiverName = draft.receiverName.trim(),
            amount = draft.amount,
            currency = draft.currency,
            commissionPercent = 0.0,
            commissionMode = CommissionMode.PERCENT,
            commissionAmount = 0.0,
            pickupCode = draft.pickupCode.trim().ifEmpty { codeGenerator.next() },
            status = HawalaStatus.PENDING,
            timestampMillis = now,
            dateLabel = Formatters.nowLabel(now),
        )
        return mutations.issueHawala(
            counterpartyId = partnerId,
            hawala = hawala,
            senderMode = null,
            commissionFixed = null,
        ).id
    }
}

/** Removes a customer ledger entry (used from the entry detail screen). */
class DeleteCustomerTransactionUseCase @Inject constructor(
    private val mutations: LedgerMutationRepository,
) {
    suspend operator fun invoke(transactionId: String) {
        mutations.deleteCustomerTransaction(transactionId)
    }
}

/** Persists today's buy/sell quotes; the repository re-derives deltas and pair rates. */
class UpdateRatesUseCase @Inject constructor(
    private val mutations: LedgerMutationRepository,
) {
    suspend operator fun invoke(quotes: Map<String, Pair<Double, Double>>): Boolean {
        val valid = quotes.filterValues { (buy, sell) -> buy > 0 && sell > 0 }
        if (valid.isEmpty()) return false
        return runCatching { mutations.updateRates(valid) }.isSuccess
    }
}

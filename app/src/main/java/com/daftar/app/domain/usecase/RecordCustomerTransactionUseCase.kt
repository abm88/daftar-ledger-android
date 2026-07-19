package com.daftar.app.domain.usecase

import com.daftar.app.core.format.Formatters
import com.daftar.app.core.time.TimeProvider
import com.daftar.app.domain.model.CurrencyConversion
import com.daftar.app.domain.model.CustomerTransaction
import com.daftar.app.domain.model.CustomerTxType
import com.daftar.app.domain.repository.CustomerRepository
import javax.inject.Inject

data class CustomerTxDraft(
    val customerId: String?,
    val type: CustomerTxType,
    val currency: String,
    val amount: Double,
    val note: String,
    /** When set, cash was taken in [currency] but the account is booked in [convertToCurrency]. */
    val convert: Boolean = false,
    val convertToCurrency: String = "AFN",
    val conversionRate: Double = 0.0,
    /** v20 attached receipt photos (content:// URIs). */
    val photoUris: List<String> = emptyList(),
)

sealed interface RecordCustomerTxResult {
    data class Recorded(val tx: CustomerTransaction, val customerName: String) : RecordCustomerTxResult
    enum class Error : RecordCustomerTxResult { MISSING_CUSTOMER, INVALID_AMOUNT, INVALID_RATE }
}

/** Books a customer account entry, optionally converting across currencies at a manual rate. */
class RecordCustomerTransactionUseCase @Inject constructor(
    private val customerRepository: CustomerRepository,
    private val timeProvider: TimeProvider,
) {
    suspend operator fun invoke(draft: CustomerTxDraft): RecordCustomerTxResult {
        val customerId = draft.customerId ?: return RecordCustomerTxResult.Error.MISSING_CUSTOMER
        val customer = customerRepository.customerById(customerId)
            ?: return RecordCustomerTxResult.Error.MISSING_CUSTOMER
        if (draft.amount <= 0) return RecordCustomerTxResult.Error.INVALID_AMOUNT
        if (draft.convert && draft.conversionRate <= 0) return RecordCustomerTxResult.Error.INVALID_RATE

        val creditedAmount = if (draft.convert) draft.amount * draft.conversionRate else draft.amount
        val creditedCurrency = if (draft.convert) draft.convertToCurrency else draft.currency

        val defaultNote = when (draft.type) {
            CustomerTxType.DEPOSIT -> "Cash deposit"
            CustomerTxType.WITHDRAWAL -> "Cash withdrawal"
            CustomerTxType.CHARGE -> "Paid on behalf"
            CustomerTxType.CREDIT -> "Advance credit"
            CustomerTxType.OPENING -> "Opening deposit"
        }
        val conversionDescription =
            "Received ${Formatters.amount(draft.amount, draft.currency)} ${draft.currency} " +
                "@ ${draft.conversionRate} → $creditedCurrency"
        val note = when {
            draft.convert && draft.note.isNotBlank() -> "${draft.note.trim()} ($conversionDescription)"
            draft.convert -> conversionDescription
            draft.note.isNotBlank() -> draft.note.trim()
            else -> defaultNote
        }

        val now = timeProvider.nowMillis()
        val tx = CustomerTransaction(
            id = "ct_$now",
            type = draft.type,
            amount = creditedAmount,
            currency = creditedCurrency,
            dateLabel = Formatters.fullDateLabel(now),
            timestampMillis = now,
            note = note,
            conversion = if (draft.convert) {
                CurrencyConversion(
                    receivedAmount = draft.amount,
                    receivedCurrency = draft.currency,
                    rate = draft.conversionRate,
                    creditedAmount = creditedAmount,
                    creditedCurrency = creditedCurrency,
                )
            } else null,
            photoUris = draft.photoUris,
        )
        customerRepository.addTransaction(customerId, tx)
        return RecordCustomerTxResult.Recorded(tx, customer.name)
    }
}

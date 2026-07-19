package com.daftar.app.domain.usecase

import com.daftar.app.core.format.Formatters
import com.daftar.app.core.time.TimeProvider
import com.daftar.app.domain.model.City
import com.daftar.app.domain.model.Customer
import com.daftar.app.domain.model.CustomerTransaction
import com.daftar.app.domain.model.CustomerTxType
import com.daftar.app.domain.repository.CustomerRepository
import com.daftar.app.domain.repository.LedgerMutationRepository
import javax.inject.Inject

data class NewCustomerDraft(
    val name: String,
    val shortName: String,
    val initial: String,
    val phone: String,
    val city: City,
    val notes: String,
    /** Per-currency opening deposits (only positive amounts are recorded). */
    val openingDeposits: Map<String, Double>,
)

/** Opens a customer account; positive opening deposits become OPENING entries. */
class AddCustomerUseCase @Inject constructor(
    private val customerRepository: CustomerRepository,
    private val mutations: LedgerMutationRepository,
    private val timeProvider: TimeProvider,
) {
    suspend operator fun invoke(draft: NewCustomerDraft): Customer? {
        val name = draft.name.trim()
        if (name.isEmpty()) return null
        val now = timeProvider.nowMillis()
        val existingCount = customerRepository.customers.value.size

        val openingTxs = draft.openingDeposits
            .filterValues { it > 0.0 }
            .map { (currency, value) ->
                CustomerTransaction(
                    id = "ct_${now}_$currency",
                    type = CustomerTxType.OPENING,
                    amount = value,
                    currency = currency,
                    dateLabel = Formatters.fullDateLabel(now),
                    timestampMillis = now - 1000,
                    note = "Opening deposit",
                )
            }

        val customer = Customer(
            id = "cust_$now",
            name = name,
            shortName = draft.shortName.trim().ifEmpty { name.split(" ").first() },
            phone = draft.phone.trim().ifEmpty { "—" },
            city = draft.city,
            colorIndex = existingCount,
            initial = draft.initial.trim().ifEmpty { name.take(1) },
            accountOpenedLabel = Formatters.monthYearLabel(now),
            notes = draft.notes.trim().ifEmpty { null },
            transactions = openingTxs,
        )
        return runCatching { mutations.createCustomer(customer, draft.openingDeposits) }.getOrNull()
    }
}

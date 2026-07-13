package com.daftar.app.data.repository

import com.daftar.app.data.seed.SeedData
import com.daftar.app.domain.model.Customer
import com.daftar.app.domain.model.CustomerTransaction
import com.daftar.app.domain.repository.CustomerRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Singleton
class InMemoryCustomerRepository @Inject constructor(seed: SeedData) : CustomerRepository {

    private val state = MutableStateFlow(seed.customers)
    override val customers: StateFlow<List<Customer>> = state.asStateFlow()

    override suspend fun addCustomer(customer: Customer) {
        state.update { listOf(customer) + it }
    }

    override suspend fun addTransaction(customerId: String, transaction: CustomerTransaction) {
        state.update { customers ->
            customers.map { c ->
                if (c.id == customerId) c.copy(transactions = c.transactions + transaction) else c
            }
        }
    }

    override suspend fun deleteTransaction(transactionId: String) {
        state.update { customers ->
            customers.map { c ->
                c.copy(transactions = c.transactions.filterNot { it.id == transactionId })
            }
        }
    }

    override suspend fun clearAll() {
        state.value = emptyList()
    }

    override suspend fun replaceAll(customers: List<Customer>) {
        state.value = customers
    }

    override fun customerById(id: String): Customer? =
        state.value.firstOrNull { it.id == id }

    override fun findTransaction(transactionId: String): Pair<CustomerTransaction, Customer>? {
        state.value.forEach { customer ->
            customer.transactions.firstOrNull { it.id == transactionId }?.let { return it to customer }
        }
        return null
    }
}

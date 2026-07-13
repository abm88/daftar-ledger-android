package com.daftar.app.domain.repository

import com.daftar.app.domain.model.Customer
import com.daftar.app.domain.model.CustomerTransaction
import kotlinx.coroutines.flow.StateFlow

/** Account-holding customers and their transaction histories. */
interface CustomerRepository {
    val customers: StateFlow<List<Customer>>

    suspend fun addCustomer(customer: Customer)
    suspend fun addTransaction(customerId: String, transaction: CustomerTransaction)
    suspend fun deleteTransaction(transactionId: String)

    /** Remove all customers and their transactions — a fresh account starts with a blank shop. */
    suspend fun clearAll()

    /** Swap in another account's customer book (per-user persistence restore). */
    suspend fun replaceAll(customers: List<Customer>)

    fun customerById(id: String): Customer?
    fun findTransaction(transactionId: String): Pair<CustomerTransaction, Customer>?
}

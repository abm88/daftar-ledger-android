package com.daftar.app.ui.feature.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daftar.app.domain.model.AssetCatalog
import com.daftar.app.domain.model.City
import com.daftar.app.domain.model.Customer
import com.daftar.app.domain.model.MoneyByCurrency
import com.daftar.app.domain.repository.CustomerRepository
import com.daftar.app.domain.repository.LedgerRefreshRepository
import com.daftar.app.domain.usecase.AddCustomerUseCase
import com.daftar.app.domain.usecase.NewCustomerDraft
import com.daftar.app.domain.usecase.PositionCalculator
import com.daftar.app.ui.common.ToastCenter
import com.daftar.app.ui.common.ToastIcon
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CustomerRowUi(val customer: Customer, val balance: MoneyByCurrency)

// v18 made the Accounts tab customers-only; partner sarafs moved to
// Daftar -> Branches (see ui/feature/branches).
data class AccountsUiState(
    val search: String = "",
    val customerRows: List<CustomerRowUi> = emptyList(),
    val customerTotal: Int = 0,
    val custodialNet: MoneyByCurrency = MoneyByCurrency(),
    val withDeposits: Int = 0,
    val withAdvances: Int = 0,
    val settled: Int = 0,
    val syncing: Boolean = false,
)

@HiltViewModel
class AccountsViewModel @Inject constructor(
    customerRepository: CustomerRepository,
    private val positionCalculator: PositionCalculator,
    private val addCustomerUseCase: AddCustomerUseCase,
    private val ledgerRefresh: LedgerRefreshRepository,
    private val toastCenter: ToastCenter,
) : ViewModel() {

    private val search = MutableStateFlow("")
    private val syncing = MutableStateFlow(false)

    val uiState = combine(
        customerRepository.customers,
        search,
        syncing,
    ) { customers, query, sync ->
        val q = query.trim().lowercase()

        val customerRows = customers
            .map { CustomerRowUi(it, positionCalculator.customerBalance(it)) }
        var withDeposits = 0
        var withAdvances = 0
        var settledCount = 0
        var custodial = MoneyByCurrency()
        customerRows.forEach { row ->
            val hasDeposit = AssetCatalog.LEDGER_CURRENCIES.any { row.balance[it] > 0.5 }
            val hasAdvance = AssetCatalog.LEDGER_CURRENCIES.any { row.balance[it] < -0.5 }
            if (!hasDeposit && !hasAdvance) settledCount++ else {
                if (hasDeposit) withDeposits++
                if (hasAdvance) withAdvances++
            }
            AssetCatalog.LEDGER_CURRENCIES.forEach { cur ->
                custodial = custodial.plus(cur, row.balance[cur])
            }
        }

        AccountsUiState(
            search = query,
            customerRows = customerRows.filter { row ->
                q.isEmpty() || row.customer.name.lowercase().contains(q) ||
                    row.customer.phone.contains(query.trim())
            },
            customerTotal = customers.size,
            custodialNet = custodial,
            withDeposits = withDeposits,
            withAdvances = withAdvances,
            settled = settledCount,
            syncing = sync,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AccountsUiState())

    fun setSearch(value: String) { search.value = value }

    /** Clears the search when the bottom tab is re-entered (v18 nav taps reset it). */
    fun resetSearch() { search.value = "" }

    fun sync() {
        if (syncing.value) return
        viewModelScope.launch {
            syncing.value = true
            val result = runCatching { ledgerRefresh.refresh() }
            syncing.value = false
            toastCenter.show(
                if (result.isSuccess) "Ledger synced" else result.exceptionOrNull()?.message ?: "Unable to sync ledger",
                if (result.isSuccess) ToastIcon.REFRESH else ToastIcon.CROSS,
            )
        }
    }

    fun addCustomer(
        name: String, shortName: String, initial: String, phone: String,
        city: City, notes: String, openings: Map<String, Double>,
        onDone: () -> Unit,
    ) {
        viewModelScope.launch {
            val customer = addCustomerUseCase(
                NewCustomerDraft(name, shortName, initial, phone, city, notes, openings),
            )
            if (customer != null) {
                toastCenter.show("Account opened · ${customer.name}", ToastIcon.PERSON_ADD)
                onDone()
            }
        }
    }
}

package com.daftar.app.ui.feature.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daftar.app.domain.model.AssetCatalog
import com.daftar.app.domain.model.City
import com.daftar.app.domain.model.Counterparty
import com.daftar.app.domain.model.Customer
import com.daftar.app.domain.model.MoneyByCurrency
import com.daftar.app.domain.model.PartnerTier
import com.daftar.app.domain.repository.CustomerRepository
import com.daftar.app.domain.repository.PartnerRepository
import com.daftar.app.domain.usecase.AddCustomerUseCase
import com.daftar.app.domain.usecase.AddPartnerUseCase
import com.daftar.app.domain.usecase.NewCustomerDraft
import com.daftar.app.domain.usecase.NewPartnerDraft
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

enum class AccountsSubTab { CUSTOMERS, PARTNERS }

data class CustomerRowUi(val customer: Customer, val balance: MoneyByCurrency)
data class PartnerRowUi(val partner: Counterparty, val position: MoneyByCurrency)

data class AccountsUiState(
    val subTab: AccountsSubTab = AccountsSubTab.CUSTOMERS,
    val search: String = "",
    // Customers view
    val customerRows: List<CustomerRowUi> = emptyList(),
    val customerTotal: Int = 0,
    val custodialNet: MoneyByCurrency = MoneyByCurrency(),
    val withDeposits: Int = 0,
    val withAdvances: Int = 0,
    val settled: Int = 0,
    // Partners view
    val partnerRows: List<PartnerRowUi> = emptyList(),
    val partnerTotal: Int = 0,
    val globalPosition: MoneyByCurrency = MoneyByCurrency(),
)

@HiltViewModel
class AccountsViewModel @Inject constructor(
    customerRepository: CustomerRepository,
    partnerRepository: PartnerRepository,
    private val positionCalculator: PositionCalculator,
    private val addPartnerUseCase: AddPartnerUseCase,
    private val addCustomerUseCase: AddCustomerUseCase,
    private val toastCenter: ToastCenter,
) : ViewModel() {

    private val subTab = MutableStateFlow(AccountsSubTab.CUSTOMERS)
    private val search = MutableStateFlow("")

    val uiState = combine(
        customerRepository.customers,
        partnerRepository.partners,
        subTab,
        search,
    ) { customers, partners, tab, query ->
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
            subTab = tab,
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
            partnerRows = partners
                .map { PartnerRowUi(it, positionCalculator.partnerPosition(it)) }
                .filter { row ->
                    q.isEmpty() || row.partner.name.lowercase().contains(q) ||
                        row.partner.phone.contains(query.trim())
                },
            partnerTotal = partners.size,
            globalPosition = positionCalculator.globalPosition(partners),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AccountsUiState())

    fun setSubTab(tab: AccountsSubTab) { subTab.value = tab; search.value = "" }
    fun setSearch(value: String) { search.value = value }

    fun addPartner(
        name: String, shortName: String, initial: String, phone: String,
        city: City, tier: PartnerTier, openings: Map<String, Double>,
        onDone: () -> Unit,
    ) {
        viewModelScope.launch {
            val partner = addPartnerUseCase(
                NewPartnerDraft(name, shortName, initial, phone, city, tier, openings),
            )
            if (partner != null) {
                toastCenter.show("${partner.name} added", ToastIcon.PERSON_ADD)
                onDone()
            }
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

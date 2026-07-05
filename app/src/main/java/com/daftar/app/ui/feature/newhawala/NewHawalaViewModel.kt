package com.daftar.app.ui.feature.newhawala

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daftar.app.domain.model.City
import com.daftar.app.domain.model.CommissionMode
import com.daftar.app.domain.model.Counterparty
import com.daftar.app.domain.model.Customer
import com.daftar.app.domain.repository.CustomerRepository
import com.daftar.app.domain.repository.PartnerRepository
import com.daftar.app.domain.repository.SettingsRepository
import com.daftar.app.domain.usecase.CommissionCalculator
import com.daftar.app.domain.usecase.HawalaDraft
import com.daftar.app.domain.usecase.IssueHawalaResult
import com.daftar.app.domain.usecase.IssueHawalaUseCase
import com.daftar.app.domain.usecase.PickupCodeGenerator
import com.daftar.app.domain.usecase.PositionCalculator
import com.daftar.app.domain.usecase.SenderMode
import com.daftar.app.ui.common.ToastCenter
import com.daftar.app.ui.common.ToastIcon
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class HawalaPicker { NONE, FROM_CITY, TO_CITY, PARTNER, SENDER_ACCOUNT }

data class NewHawalaFormState(
    val currency: String = "USD",
    val amountText: String = "",
    val fromCity: City = City.KBL,
    val toCity: City = City.HRT,
    val senderMode: SenderMode = SenderMode.CASH,
    val senderName: String = "",
    val receiverName: String = "",
    val senderCustomerId: String? = null,
    val partnerId: String? = null,
    val commissionMode: CommissionMode = CommissionMode.PERCENT,
    val commissionPercent: Double = CommissionCalculator.DEFAULT_PERCENT,
    val commissionFixedText: String = "",
    val pickupCode: String = "",
    val picker: HawalaPicker = HawalaPicker.NONE,
    val confirming: Boolean = false,
)

/** Everything the form needs that is derived from repositories + draft. */
data class NewHawalaUiState(
    val form: NewHawalaFormState = NewHawalaFormState(),
    val partner: Counterparty? = null,
    val senderCustomer: Customer? = null,
    val partners: List<Counterparty> = emptyList(),
    val customers: List<Customer> = emptyList(),
    val commissionAmount: Double = 0.0,
    val senderBalance: Double = 0.0,
    val customerBalances: Map<String, Double> = emptyMap(),
) {
    val amount: Double get() = form.amountText.toDoubleOrNull() ?: 0.0
    val totalDebit: Double get() = amount + commissionAmount
    val balanceAfter: Double get() = senderBalance - totalDebit
    val insufficientAccountFunds: Boolean
        get() = form.senderMode == SenderMode.ACCOUNT && senderCustomer != null &&
            amount > 0 && balanceAfter < -0.5
}

@HiltViewModel
class NewHawalaViewModel @Inject constructor(
    partnerRepository: PartnerRepository,
    customerRepository: CustomerRepository,
    settingsRepository: SettingsRepository,
    codeGenerator: PickupCodeGenerator,
    private val positionCalculator: PositionCalculator,
    private val commissionCalculator: CommissionCalculator,
    private val issueHawala: IssueHawalaUseCase,
    private val toastCenter: ToastCenter,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val form: MutableStateFlow<NewHawalaFormState>

    init {
        val prefillPartnerId: String? = savedStateHandle["partnerId"]
        val prefillPartner = prefillPartnerId?.let(partnerRepository::partnerById)
        val defaultPartner = prefillPartner ?: partnerRepository.partners.value.firstOrNull()
        form = MutableStateFlow(
            NewHawalaFormState(
                currency = settingsRepository.settings.value.tradeCurrency,
                partnerId = defaultPartner?.id,
                toCity = when {
                    prefillPartner != null && prefillPartner.city != City.KBL -> prefillPartner.city
                    else -> City.HRT
                },
                pickupCode = codeGenerator.next(),
            ),
        )
    }

    val uiState = combine(
        form.asStateFlow(),
        partnerRepository.partners,
        customerRepository.customers,
    ) { form, partners, customers ->
        val senderCustomer = form.senderCustomerId?.let { id -> customers.firstOrNull { it.id == id } }
        val amount = form.amountText.toDoubleOrNull() ?: 0.0
        NewHawalaUiState(
            form = form,
            partner = partners.firstOrNull { it.id == form.partnerId },
            senderCustomer = senderCustomer,
            partners = partners,
            customers = customers,
            commissionAmount = commissionCalculator.commissionAmount(
                amount, form.commissionMode, form.commissionPercent,
                form.commissionFixedText.toDoubleOrNull(),
            ),
            senderBalance = senderCustomer
                ?.let { positionCalculator.customerBalance(it)[form.currency] } ?: 0.0,
            customerBalances = customers.associate { c ->
                c.id to positionCalculator.customerBalance(c)[form.currency]
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NewHawalaUiState())

    fun update(transform: (NewHawalaFormState) -> NewHawalaFormState) {
        form.value = transform(form.value)
    }

    fun setSenderMode(mode: SenderMode) {
        form.value = form.value.copy(
            senderMode = mode,
            // Leaving account mode clears the customer prefill.
            senderName = if (mode == SenderMode.CASH && form.value.senderCustomerId != null) "" else form.value.senderName,
            senderCustomerId = if (mode == SenderMode.CASH) null else form.value.senderCustomerId,
        )
    }

    fun setCommissionMode(mode: CommissionMode) {
        val current = form.value
        var fixedText = current.commissionFixedText
        // Seed the fixed field from the current percent so switching feels continuous.
        if (mode == CommissionMode.FIXED && fixedText.isBlank()) {
            val amount = current.amountText.toDoubleOrNull() ?: 0.0
            if (amount > 0) {
                val seeded = amount * current.commissionPercent / 100
                fixedText = if (current.currency == "USD") String.format(java.util.Locale.US, "%.2f", seeded)
                else seeded.toLong().toString()
            }
        }
        form.value = current.copy(commissionMode = mode, commissionFixedText = fixedText)
    }

    fun pickSenderCustomer(customer: Customer) {
        form.value = form.value.copy(
            senderCustomerId = customer.id,
            senderName = customer.name,
            picker = HawalaPicker.NONE,
        )
    }

    fun pickPartner(partner: Counterparty) {
        val current = form.value
        form.value = current.copy(
            partnerId = partner.id,
            // Convenience from the prototype: aim the corridor at the partner's city.
            toCity = if (current.fromCity != partner.city) partner.city else current.toCity,
            picker = HawalaPicker.NONE,
        )
    }

    /** Validates and opens the confirmation sheet. */
    fun review() {
        val state = uiState.value
        val form = state.form
        when {
            state.amount <= 0 || form.receiverName.isBlank() ->
                toastCenter.show("Fill amount and receiver", ToastIcon.CROSS)
            form.senderMode == SenderMode.ACCOUNT && form.senderCustomerId == null ->
                toastCenter.show("Choose sender account", ToastIcon.CROSS)
            form.senderMode == SenderMode.ACCOUNT && state.insufficientAccountFunds ->
                toastCenter.show("Insufficient ${form.currency} balance", ToastIcon.CROSS)
            form.senderMode == SenderMode.CASH && form.senderName.isBlank() ->
                toastCenter.show("Enter sender name", ToastIcon.CROSS)
            else -> update { it.copy(confirming = true) }
        }
    }

    fun confirmIssue(onIssued: (String) -> Unit) {
        val state = uiState.value
        val form = state.form
        val partnerId = form.partnerId ?: return
        viewModelScope.launch {
            val result = issueHawala(
                HawalaDraft(
                    currency = form.currency,
                    amount = state.amount,
                    fromCity = form.fromCity,
                    toCity = form.toCity,
                    senderMode = form.senderMode,
                    senderName = form.senderName,
                    receiverName = form.receiverName,
                    senderCustomerId = form.senderCustomerId,
                    partnerId = partnerId,
                    commissionMode = form.commissionMode,
                    commissionPercent = form.commissionPercent,
                    commissionFixed = form.commissionFixedText.toDoubleOrNull() ?: 0.0,
                    pickupCode = form.pickupCode,
                ),
            )
            when (result) {
                is IssueHawalaResult.Issued -> {
                    val message = result.debitedCustomerName
                        ?.let { "Hawala issued · ${it.split(" ").first()}'s account debited" }
                        ?: "Hawala issued · code ${form.pickupCode}"
                    toastCenter.show(message, ToastIcon.CHECK)
                    onIssued(result.hawalaId)
                }
                IssueHawalaResult.Error.INSUFFICIENT_BALANCE ->
                    toastCenter.show("Insufficient ${form.currency} balance", ToastIcon.CROSS)
                else -> toastCenter.show("Check the form and try again", ToastIcon.CROSS)
            }
        }
    }
}

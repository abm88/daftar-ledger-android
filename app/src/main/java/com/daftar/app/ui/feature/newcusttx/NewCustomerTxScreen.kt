package com.daftar.app.ui.feature.newcusttx

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.daftar.app.core.format.Formatters
import androidx.compose.material.icons.rounded.PersonAdd
import com.daftar.app.domain.model.AssetCatalog
import com.daftar.app.domain.model.Customer
import com.daftar.app.domain.model.CustomerTxType
import com.daftar.app.domain.repository.CustomerRepository
import com.daftar.app.domain.repository.RatesRepository
import com.daftar.app.domain.repository.SettingsRepository
import com.daftar.app.domain.usecase.CurrencyConverter
import com.daftar.app.domain.usecase.CustomerTxDraft
import com.daftar.app.domain.usecase.RecordCustomerTransactionUseCase
import com.daftar.app.domain.usecase.RecordCustomerTxResult
import com.daftar.app.ui.common.BigAmountInput
import com.daftar.app.ui.common.dashedBorder
import com.daftar.app.ui.common.FieldBox
import com.daftar.app.ui.common.FieldTextInput
import com.daftar.app.ui.common.IconSquareButton
import com.daftar.app.ui.common.MonoLabel
import com.daftar.app.ui.common.SheetHandle
import com.daftar.app.ui.common.FullScreenPhotoViewer
import com.daftar.app.ui.common.MAX_TX_PHOTOS
import com.daftar.app.ui.common.PhotoAttachmentSection
import com.daftar.app.ui.common.SubmitButton
import com.daftar.app.ui.common.ToastCenter
import com.daftar.app.ui.common.ToastIcon
import com.daftar.app.ui.common.sanitizeAmountInput
import com.daftar.app.ui.common.CustomerBadge
import com.daftar.app.ui.components.EmptyState
import com.daftar.app.ui.components.EmptyStateTone
import com.daftar.app.ui.feature.accounts.AddCustomerSheet
import com.daftar.app.ui.theme.DaftarColors
import com.daftar.app.ui.theme.Fraunces
import com.daftar.app.ui.theme.Inter
import com.daftar.app.ui.theme.JetBrainsMono
import com.daftar.app.ui.theme.NotoNaskhArabic
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** gave = red quick flow, received = green quick flow, full = type grid. */
enum class CustTxMode { FULL, GAVE, RECEIVED }

data class NewCustTxFormState(
    val mode: CustTxMode = CustTxMode.FULL,
    val customerId: String? = null,
    /** v18 lockedCustomer: opened from the customer's own page — picker hidden. */
    val locked: Boolean = false,
    val type: CustomerTxType = CustomerTxType.DEPOSIT,
    val currency: String = "USD",
    val amountText: String = "",
    val note: String = "",
    val convert: Boolean = false,
    val convertTo: String = "AFN",
    val rateText: String = "",
    val pickerOpen: Boolean = false,
    val addCustomerOpen: Boolean = false,
    /** Red error styling on the amount after a failed save attempt (v18 shake). */
    val amountError: Boolean = false,
    /** v20 attached receipt photos (content:// URIs), up to MAX_TX_PHOTOS. */
    val photoUris: List<String> = emptyList(),
)

data class NewCustTxUiState(
    val form: NewCustTxFormState = NewCustTxFormState(),
    val customer: Customer? = null,
    val customers: List<Customer> = emptyList(),
    val suggestedRate: Double? = null,
    /** Enabled currency codes (v18 activeCurrencies — metals excluded by design). */
    val activeCurrencies: List<String> = listOf("USD", "AFN", "PKR"),
) {
    val amount: Double get() = form.amountText.toDoubleOrNull() ?: 0.0
    val rate: Double get() = form.rateText.toDoubleOrNull() ?: 0.0
    val creditedAmount: Double get() = if (amount > 0 && rate > 0) amount * rate else 0.0
}

@HiltViewModel
class NewCustomerTxViewModel @Inject constructor(
    customerRepository: CustomerRepository,
    ratesRepository: RatesRepository,
    settingsRepository: SettingsRepository,
    private val converter: CurrencyConverter,
    private val recordTransaction: RecordCustomerTransactionUseCase,
    private val addCustomerUseCase: com.daftar.app.domain.usecase.AddCustomerUseCase,
    private val toastCenter: ToastCenter,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val form: MutableStateFlow<NewCustTxFormState>

    init {
        val mode = when (savedStateHandle.get<String>("mode")) {
            "gave" -> CustTxMode.GAVE
            "received" -> CustTxMode.RECEIVED
            else -> CustTxMode.FULL
        }
        val tradeCurrency = settingsRepository.settings.value.tradeCurrency
        form = MutableStateFlow(
            NewCustTxFormState(
                mode = mode,
                customerId = savedStateHandle.get<String>("customerId")
                    ?: customerRepository.customers.value.firstOrNull()?.id,
                locked = savedStateHandle.get<String>("locked") == "true",
                type = if (mode == CustTxMode.GAVE) CustomerTxType.WITHDRAWAL else CustomerTxType.DEPOSIT,
                currency = tradeCurrency,
                convertTo = if (tradeCurrency == "AFN") "USD" else "AFN",
            ),
        )
    }

    val uiState = combine(
        form,
        customerRepository.customers,
        ratesRepository.rateBook,
        settingsRepository.settings,
    ) { form, customers, rates, settings ->
        // v18: every enabled currency gets a pill (metals excluded); the selected
        // one stays listed even if it was deactivated mid-edit.
        val active = settings.activeCurrencies().map { it.code }
        NewCustTxUiState(
            form = form,
            customer = customers.firstOrNull { it.id == form.customerId },
            customers = customers,
            suggestedRate = if (form.convert) {
                converter.suggestedConversionRate(form.currency, form.convertTo, rates)
            } else null,
            activeCurrencies = if (form.currency in active) active else active + form.currency,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NewCustTxUiState())

    fun update(transform: (NewCustTxFormState) -> NewCustTxFormState) {
        form.value = transform(form.value)
    }

    fun setCurrency(currency: String) {
        form.value = form.value.copy(
            currency = currency,
            // v18 re-aims the destination only while the convert toggle is on.
            convertTo = if (form.value.convert && form.value.convertTo == currency) {
                if (currency == "USD") "AFN" else "USD"
            } else form.value.convertTo,
        )
    }

    /** v18 add-customer-from-picker: the new account is auto-selected. */
    fun addCustomerAndSelect(
        name: String, shortName: String, initial: String, phone: String,
        city: com.daftar.app.domain.model.City, notes: String, openings: Map<String, Double>,
    ) {
        viewModelScope.launch {
            val customer = addCustomerUseCase(
                com.daftar.app.domain.usecase.NewCustomerDraft(name, shortName, initial, phone, city, notes, openings),
            )
            if (customer != null) {
                toastCenter.show("Account opened · ${customer.name}", ToastIcon.PERSON_ADD)
                form.value = form.value.copy(
                    customerId = customer.id,
                    addCustomerOpen = false,
                    pickerOpen = false,
                )
            }
        }
    }

    fun toggleConvert() {
        val current = form.value
        val next = !current.convert
        form.value = current.copy(
            convert = next,
            convertTo = if (next && current.convertTo == current.currency) {
                if (current.currency == "USD") "AFN" else "USD"
            } else current.convertTo,
        )
    }

    fun save(onSaved: () -> Unit) {
        val state = uiState.value
        val form = state.form
        if (state.amount <= 0 || form.customerId == null) {
            // v18 also shakes the amount box; we flag it red until the next edit.
            this.form.value = form.copy(amountError = state.amount <= 0)
            toastCenter.show("Choose account, enter amount", ToastIcon.CROSS)
            return
        }
        if (form.convert && state.rate <= 0) {
            toastCenter.show("Enter conversion rate", ToastIcon.CROSS)
            return
        }
        viewModelScope.launch {
            val result = recordTransaction(
                CustomerTxDraft(
                    customerId = form.customerId,
                    type = form.type,
                    currency = form.currency,
                    amount = state.amount,
                    note = form.note,
                    convert = form.convert,
                    convertToCurrency = form.convertTo,
                    conversionRate = state.rate,
                    photoUris = form.photoUris,
                ),
            )
            if (result is RecordCustomerTxResult.Recorded) {
                // v18 toast uses the plain accounting word ("Deposit", "Credit"…).
                val label = result.tx.type.plainLabel
                val message = if (form.convert) {
                    "$label · ${Formatters.amount(result.tx.amount, result.tx.currency)} ${result.tx.currency} " +
                        "(from ${Formatters.amount(state.amount, form.currency)} ${form.currency})"
                } else {
                    "$label · ${Formatters.number(state.amount)} ${form.currency}"
                }
                toastCenter.show(message, ToastIcon.CHECK)
                onSaved()
            } else if (result is RecordCustomerTxResult.Failure) {
                toastCenter.show(result.message, ToastIcon.CROSS)
            } else toastCenter.show("Check the form and try again", ToastIcon.CROSS)
        }
    }
}

/** Customer account entry form (full type grid or quick gave/received modes). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewCustomerTxScreen(
    navController: NavController,
    viewModel: NewCustomerTxViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val form = state.form

    // v20 receipt-photo attachments — native picker (gallery multi-select) + viewer.
    val context = LocalContext.current
    var viewerUri by remember { mutableStateOf<String?>(null) }
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_TX_PHOTOS),
    ) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                // Persist read access so the photos survive app restarts.
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
            }
            viewModel.update { f ->
                f.copy(photoUris = (f.photoUris + uris.map { it.toString() }).distinct().take(MAX_TX_PHOTOS))
            }
        }
    }
    val launchPicker = {
        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    val (headerColor, headerTitle, headerPashto, headerIcon) = when (form.mode) {
        CustTxMode.GAVE -> listOf(DaftarColors.Red, "You Gave", "تاسو ورکړل", Icons.Rounded.ArrowUpward)
        CustTxMode.RECEIVED -> listOf(DaftarColors.Green, "You Received", "تاسو ترلاسه کړل", Icons.Rounded.ArrowDownward)
        CustTxMode.FULL -> listOf(DaftarColors.Green, "New Entry · Account", "د حساب نوې ننوتنه", null)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerColor as Color)
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (headerIcon != null) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = headerIcon as androidx.compose.ui.graphics.vector.ImageVector,
                            contentDescription = null,
                            tint = DaftarColors.Paper,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Column {
                    Text(
                        text = headerTitle as String,
                        style = TextStyle(
                            fontFamily = Fraunces, fontWeight = FontWeight.Medium,
                            fontSize = 18.sp, color = DaftarColors.Paper,
                        ),
                    )
                    if (form.locked && state.customer != null) {
                        // v18 locked mode shows who the entry is pinned to.
                        Row(
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White.copy(alpha = 0.16f))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(
                                Icons.Rounded.Person, null,
                                tint = DaftarColors.Paper,
                                modifier = Modifier.size(10.dp),
                            )
                            Text(
                                text = state.customer!!.name,
                                style = TextStyle(
                                    fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold,
                                    fontSize = 10.sp, color = DaftarColors.Paper,
                                ),
                            )
                        }
                    } else {
                        Text(
                            text = headerPashto as String,
                            style = TextStyle(
                                fontFamily = NotoNaskhArabic, fontSize = 12.sp,
                                color = DaftarColors.Paper.copy(alpha = 0.85f),
                                textDirection = TextDirection.Rtl,
                            ),
                        )
                    }
                }
            }
            IconSquareButton(Icons.Rounded.Close, { navController.popBackStack() }, onDark = true)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            // Account picker — hidden entirely when locked to one customer (v18).
            if (!form.locked) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DaftarColors.PaperSoft)
                    .border(1.dp, DaftarColors.LineStrong, RoundedCornerShape(12.dp))
                    .clickable { viewModel.update { it.copy(pickerOpen = true) } }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.customer != null) {
                    CustomerBadge(state.customer!!, 40.dp)
                } else {
                    Icon(Icons.Rounded.Person, null, tint = DaftarColors.InkSoft, modifier = Modifier.size(18.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    MonoLabel("Account", fontSize = 9)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = state.customer?.name ?: "Choose customer",
                        style = TextStyle(
                            fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                            color = if (state.customer != null) DaftarColors.Ink else DaftarColors.Muted,
                        ),
                    )
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = DaftarColors.Muted, modifier = Modifier.size(16.dp))
            }
            }

            // Type grid — full mode only
            if (form.mode == CustTxMode.FULL) {
                Spacer(Modifier.height(14.dp))
                val types = listOf(
                    Triple(CustomerTxType.DEPOSIT, "Cash in · holder deposits", Icons.Rounded.ArrowDownward),
                    Triple(CustomerTxType.WITHDRAWAL, "Cash out · holder withdraws", Icons.Rounded.ArrowUpward),
                    Triple(CustomerTxType.CHARGE, "Paid on behalf · e.g. supplier", Icons.Rounded.ArrowUpward),
                    Triple(CustomerTxType.CREDIT, "Short-term credit given", Icons.Rounded.ArrowUpward),
                )
                types.chunked(2).forEach { rowTypes ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowTypes.forEach { (type, sub, icon) ->
                            val on = form.type == type
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (on) DaftarColors.Green.copy(alpha = 0.08f) else DaftarColors.PaperSoft)
                                    .border(
                                        1.5.dp,
                                        if (on) DaftarColors.Green else DaftarColors.LineStrong,
                                        RoundedCornerShape(12.dp),
                                    )
                                    .clickable { viewModel.update { it.copy(type = type) } }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (type == CustomerTxType.DEPOSIT) DaftarColors.Green.copy(alpha = 0.12f)
                                            else DaftarColors.Red.copy(alpha = 0.12f),
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        icon, null,
                                        tint = if (type == CustomerTxType.DEPOSIT) DaftarColors.Green else DaftarColors.Red,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                                Column {
                                    Text(
                                        // v18 tile titles: "You Received" / "You Gave" / "Charge" / "Advance".
                                        text = when (type) {
                                            CustomerTxType.CHARGE -> "Charge"
                                            CustomerTxType.CREDIT -> "Advance"
                                            else -> type.label
                                        },
                                        style = TextStyle(
                                            fontFamily = Inter, fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp, color = DaftarColors.Ink,
                                        ),
                                    )
                                    Text(
                                        text = sub,
                                        style = TextStyle(fontFamily = Inter, fontSize = 9.sp, color = DaftarColors.Muted),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            // v18 renders a scrollable pill per enabled currency (symbol + code)
            // instead of the fixed three-segment switcher.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                state.activeCurrencies.forEach { code ->
                    val on = form.currency == code
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (on) DaftarColors.Ink else DaftarColors.PaperSoft)
                            .border(
                                1.dp,
                                if (on) DaftarColors.Ink else DaftarColors.LineStrong,
                                RoundedCornerShape(20.dp),
                            )
                            .clickable { viewModel.setCurrency(code) }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Text(
                            text = AssetCatalog.symbolFor(code),
                            style = TextStyle(
                                fontFamily = Inter, fontSize = 11.sp,
                                color = if (on) DaftarColors.GoldSoft else DaftarColors.Muted,
                            ),
                        )
                        Text(
                            text = code,
                            style = TextStyle(
                                fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold,
                                fontSize = 11.sp, letterSpacing = 0.05.em,
                                color = if (on) DaftarColors.Paper else DaftarColors.InkSoft,
                            ),
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            BigAmountInput(
                value = form.amountText,
                onValueChange = { text -> viewModel.update { it.copy(amountText = text, amountError = false) } },
                currency = form.currency,
                label = if (form.convert) "Cash received · type amount" else "Amount · type amount",
                error = form.amountError,
            )

            // v18 field order: note sits between the amount and the convert toggle.
            Spacer(Modifier.height(12.dp))
            FieldBox("Note · description", modifier = Modifier.fillMaxWidth()) {
                FieldTextInput(
                    form.note, { text -> viewModel.update { it.copy(note = text) } },
                    "e.g. Cash deposit, Market expenses, Paid supplier",
                )
            }

            // v20 receipt photos — Add photo CTA / thumbnail grid.
            Spacer(Modifier.height(12.dp))
            PhotoAttachmentSection(
                uris = form.photoUris,
                editable = true,
                onOpen = { viewerUri = it },
                onAdd = launchPicker,
                onRemove = { uri -> viewModel.update { it.copy(photoUris = it.photoUris - uri) } },
            )

            Spacer(Modifier.height(12.dp))
            // Convert toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (form.convert) DaftarColors.Gold.copy(alpha = 0.1f) else DaftarColors.PaperSoft)
                    .border(
                        1.dp,
                        if (form.convert) DaftarColors.Gold else DaftarColors.LineStrong,
                        RoundedCornerShape(12.dp),
                    )
                    .clickable(onClick = viewModel::toggleConvert)
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (form.convert) DaftarColors.Copper else DaftarColors.PaperDeep),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Refresh, null,
                        tint = if (form.convert) DaftarColors.Paper else DaftarColors.InkSoft,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Convert to other currency",
                        style = TextStyle(
                            fontFamily = Inter, fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp, color = DaftarColors.Ink,
                        ),
                    )
                    Text(
                        text = if (form.convert) "Manual rate · auto-calculated" else "e.g. cash USD → credit AFN",
                        style = TextStyle(fontFamily = JetBrainsMono, fontSize = 10.sp, color = DaftarColors.Muted),
                    )
                }
                // Switch track
                Box(
                    modifier = Modifier
                        .size(width = 38.dp, height = 22.dp)
                        .clip(CircleShape)
                        .background(if (form.convert) DaftarColors.Copper else DaftarColors.LineStrong),
                    contentAlignment = if (form.convert) Alignment.CenterEnd else Alignment.CenterStart,
                ) {
                    Box(
                        modifier = Modifier
                            .padding(2.dp)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                    )
                }
            }

            if (form.convert) {
                Spacer(Modifier.height(12.dp))
                ConversionBlock(state, viewModel)
            }

            Spacer(Modifier.height(20.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(DaftarColors.Paper)
                .padding(horizontal = 18.dp, vertical = 12.dp)
                .navigationBarsPadding(),
        ) {
            SubmitButton(
                label = when (form.mode) {
                    CustTxMode.GAVE -> "Confirm · You Gave"
                    CustTxMode.RECEIVED -> "Confirm · You Received"
                    CustTxMode.FULL -> "Save Entry · خوندي کړه"
                },
                icon = Icons.Rounded.Check,
                container = if (form.mode == CustTxMode.GAVE) DaftarColors.Red else DaftarColors.Green,
                onClick = { viewModel.save { navController.popBackStack() } },
            )
        }
    }

    if (form.pickerOpen) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.update { it.copy(pickerOpen = false) } },
            containerColor = DaftarColors.Paper,
            dragHandle = { SheetHandle() },
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                Text(
                    "Choose account",
                    style = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 18.sp, color = DaftarColors.Ink),
                )
                Spacer(Modifier.height(12.dp))
                if (state.customers.isEmpty()) {
                    // v18: empty picker offers to create the first account inline.
                    EmptyState(
                        icon = Icons.Rounded.Person,
                        title = "No accounts yet",
                        sub = "You haven't added any customer accounts. Create one to record this entry against it.",
                        tone = EmptyStateTone.COPPER,
                        ctaLabel = "Create account · نوی حساب",
                        ctaIcon = Icons.Rounded.PersonAdd,
                        onCta = { viewModel.update { it.copy(addCustomerOpen = true) } },
                    )
                }
                state.customers.forEach { customer ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                viewModel.update { it.copy(customerId = customer.id, pickerOpen = false) }
                            }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CustomerBadge(customer, 40.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                customer.name,
                                style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = DaftarColors.Ink),
                            )
                            Text(
                                "${customer.city.displayName} · ${customer.phone}",
                                style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp, color = DaftarColors.Muted),
                            )
                        }
                        if (form.customerId == customer.id) {
                            Icon(Icons.Rounded.Check, null, tint = DaftarColors.Green, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                if (state.customers.isNotEmpty()) {
                    // v18 footer row: create an account without leaving the flow.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .dashedBorder(DaftarColors.LineDashed, 1.5.dp, 12.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { viewModel.update { it.copy(addCustomerOpen = true) } }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.PersonAdd, null, tint = DaftarColors.InkSoft, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Add new account · نوی حساب",
                            style = TextStyle(
                                fontFamily = Inter, fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp, color = DaftarColors.InkSoft,
                            ),
                        )
                    }
                }
            }
        }
    }

    if (form.addCustomerOpen) {
        AddCustomerSheet(
            onDismiss = { viewModel.update { it.copy(addCustomerOpen = false) } },
            onSave = { name, shortName, initial, phone, city, notes, openings ->
                viewModel.addCustomerAndSelect(name, shortName, initial, phone, city, notes, openings)
            },
        )
    }

    viewerUri?.let { uri ->
        FullScreenPhotoViewer(uri, onDismiss = { viewerUri = null })
    }
}

@Composable
private fun ConversionBlock(state: NewCustTxUiState, viewModel: NewCustomerTxViewModel) {
    val form = state.form
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DaftarColors.Ink)
            .padding(14.dp),
    ) {
        // Received row
        ConvertRow(
            chip = { ConvertChip(form.currency, DaftarColors.Paper.copy(alpha = 0.1f), DaftarColors.Paper) },
            label = "Received from holder",
        ) {
            Text(
                text = (if (state.amount > 0) Formatters.amount(state.amount, form.currency) else "0") + " ",
                style = ConvertValueStyle,
            )
            SmallCur(form.currency)
        }

        ConvertArrow()

        // Manual rate input row
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(DaftarColors.Gold.copy(alpha = 0.15f))
                .border(1.dp, DaftarColors.GoldSoft.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            MonoLabel("Conversion rate · نرخ", color = DaftarColors.GoldSoft, fontSize = 9)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) {
                    if (form.rateText.isEmpty()) {
                        Text("0.00", style = ConvertValueStyle.copy(color = DaftarColors.MutedLight))
                    }
                    BasicTextField(
                        value = form.rateText,
                        onValueChange = { text ->
                            viewModel.update { it.copy(rateText = sanitizeRate(text)) }
                        },
                        textStyle = ConvertValueStyle,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    text = "${form.currency} → ${form.convertTo}",
                    style = TextStyle(fontFamily = JetBrainsMono, fontSize = 10.sp, color = DaftarColors.GoldSoft),
                )
            }
        }

        // Suggested market rate — v18 formats 2 dp except PKR→AFN which needs 4.
        val suggested = state.suggestedRate
        if (suggested != null && kotlin.math.abs(state.rate - suggested) > 0.0001) {
            val rateDecimals = if (form.currency == "PKR" && form.convertTo == "AFN") 4 else 2
            val suggestedText = Formatters.rate(suggested, rateDecimals)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Today's rate: 1 ${form.currency} = $suggestedText ${form.convertTo}",
                    style = TextStyle(fontFamily = JetBrainsMono, fontSize = 10.sp, color = DaftarColors.MutedLight),
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(DaftarColors.Gold.copy(alpha = 0.25f))
                        .clickable {
                            viewModel.update { it.copy(rateText = suggestedText) }
                        }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        "Use",
                        style = TextStyle(
                            fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold,
                            fontSize = 9.sp, letterSpacing = 0.1.em, color = DaftarColors.GoldSoft,
                        ),
                    )
                }
            }
        }

        ConvertArrow()

        // Credited row with destination picker
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(DaftarColors.Green.copy(alpha = 0.15f))
                .border(1.dp, DaftarColors.LongGreen.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MonoLabel(
                    if (form.type.isDebit) "Debit from account" else "Credit to account",
                    color = DaftarColors.LongGreen, fontSize = 9,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // v18 offers every enabled currency except the source.
                    state.activeCurrencies.filter { it != form.currency }.forEach { cur ->
                        val on = form.convertTo == cur
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(if (on) DaftarColors.LongGreen else DaftarColors.Paper.copy(alpha = 0.1f))
                                .clickable { viewModel.update { it.copy(convertTo = cur) } }
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text(
                                cur,
                                style = TextStyle(
                                    fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 9.sp,
                                    color = if (on) DaftarColors.Ink else DaftarColors.MutedLight,
                                ),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = if (state.creditedAmount > 0) Formatters.amount(state.creditedAmount, form.convertTo) else "0",
                    style = ConvertValueStyle.copy(fontSize = 22.sp),
                )
                Spacer(Modifier.width(6.dp))
                SmallCur(form.convertTo)
            }
        }
    }
}

private val ConvertValueStyle = TextStyle(
    fontFamily = Fraunces,
    fontWeight = FontWeight.Medium,
    fontSize = 17.sp,
    color = DaftarColors.Paper,
)

private fun sanitizeRate(raw: String): String = sanitizeRateText(raw)

private fun sanitizeRateText(raw: String): String {
    val filtered = raw.filter { it.isDigit() || it == '.' }
    val firstDot = filtered.indexOf('.')
    return if (firstDot == -1) filtered
    else filtered.substring(0, firstDot + 1) + filtered.substring(firstDot + 1).replace(".", "")
}

@Composable
private fun ConvertRow(
    chip: @Composable () -> Unit,
    label: String,
    value: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(DaftarColors.Paper.copy(alpha = 0.05f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        chip()
        Column {
            MonoLabel(label, color = DaftarColors.MutedLight, fontSize = 9)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.Bottom) { value() }
        }
    }
}

@Composable
private fun ConvertChip(text: String, background: Color, contentColor: Color) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text.take(3),
            style = TextStyle(
                fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold,
                fontSize = 8.sp, color = contentColor,
            ),
        )
    }
}

@Composable
private fun SmallCur(currency: String) {
    Text(
        text = currency,
        style = TextStyle(
            fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold,
            fontSize = 10.sp, letterSpacing = 0.1.em, color = DaftarColors.MutedLight,
        ),
        modifier = Modifier.padding(bottom = 2.dp),
    )
}

@Composable
private fun ConvertArrow() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.ArrowDownward, null,
            tint = DaftarColors.GoldSoft.copy(alpha = 0.6f),
            modifier = Modifier.size(14.dp),
        )
    }
}

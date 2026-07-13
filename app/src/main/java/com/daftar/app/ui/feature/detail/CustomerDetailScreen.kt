package com.daftar.app.ui.feature.detail

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
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
import com.daftar.app.domain.model.AssetCatalog
import com.daftar.app.domain.model.Customer
import com.daftar.app.domain.model.MoneyByCurrency
import com.daftar.app.domain.repository.CustomerRepository
import com.daftar.app.domain.repository.RatesRepository
import com.daftar.app.domain.repository.SettingsRepository
import com.daftar.app.domain.usecase.CurrencyConverter
import com.daftar.app.domain.usecase.PositionCalculator
import com.daftar.app.domain.usecase.TxWithRunningBalance
import com.daftar.app.ui.common.IconSquareButton
import com.daftar.app.ui.common.LocalToaster
import com.daftar.app.ui.common.MonoLabel
import com.daftar.app.ui.common.ToastIcon
import com.daftar.app.ui.common.dashedBorder
import com.daftar.app.ui.common.CustomerBadge
import com.daftar.app.ui.components.DarkBalanceGrid
import com.daftar.app.ui.components.EmptyState
import com.daftar.app.ui.components.EmptyStateTone
import com.daftar.app.ui.navigation.DaftarDestinations
import com.daftar.app.ui.theme.DaftarColors
import com.daftar.app.ui.theme.Fraunces
import com.daftar.app.ui.theme.Inter
import com.daftar.app.ui.theme.JetBrainsMono
import com.daftar.app.ui.theme.NotoNaskhArabic
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class CustomerDetailUiState(
    val customer: Customer? = null,
    val balance: MoneyByCurrency = MoneyByCurrency(),
    /** Statement-style rows, newest first, each carrying its running balance (v18). */
    val rows: List<TxWithRunningBalance> = emptyList(),
    /** Net across all currencies converted to the reporting currency (v18 net chip). */
    val netReporting: Double = 0.0,
    val reportingCurrency: String = "AFN",
    val reportingDecimals: Int = 0,
)

@HiltViewModel
class CustomerDetailViewModel @Inject constructor(
    customerRepository: CustomerRepository,
    ratesRepository: RatesRepository,
    settingsRepository: SettingsRepository,
    positionCalculator: PositionCalculator,
    converter: CurrencyConverter,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val customerId: String = checkNotNull(savedStateHandle["customerId"])

    val uiState = combine(
        customerRepository.customers,
        ratesRepository.rateBook,
        settingsRepository.settings,
    ) { customers, rates, settings ->
        val customer = customers.firstOrNull { it.id == customerId }
            ?: return@combine CustomerDetailUiState()
        val balance = positionCalculator.customerBalance(customer)
        // v18: sum every currency's balance converted to the reporting currency,
        // rounded to that currency's decimals before classifying the direction.
        val repDecimals = AssetCatalog.decimalsFor(settings.reportingCurrency)
        val net = balance.amounts.entries.sumOf { (cur, amt) ->
            converter.toReporting(cur, amt, rates, settings)
        }
        val netRounded = String.format(java.util.Locale.US, "%.${repDecimals}f", net).toDouble()
        CustomerDetailUiState(
            customer = customer,
            balance = balance,
            rows = positionCalculator.runningBalances(customer).reversed(),
            netReporting = netRounded,
            reportingCurrency = settings.reportingCurrency,
            reportingDecimals = repDecimals,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CustomerDetailUiState())
}

/**
 * Customer account detail, v18 layout: dark header with net-position chip and
 * dynamic per-currency balances, notes, a statement-style running-balance
 * history, and the dual "You Gave / You Received" quick FABs.
 */
@Composable
fun CustomerDetailScreen(
    navController: NavController,
    viewModel: CustomerDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val customer = state.customer ?: return
    val toaster = LocalToaster.current

    Box(modifier = Modifier.fillMaxWidth()) {
        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 110.dp),
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DaftarColors.Ink)
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp)
                        .padding(top = 8.dp, bottom = 18.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        IconSquareButton(Icons.AutoMirrored.Rounded.ArrowBack, { navController.popBackStack() }, onDark = true)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // TODO(backend): fire an ACTION_DIAL intent; toast-only in the prototype too.
                            IconSquareButton(Icons.Rounded.Phone, { toaster("Dialling ${customer.phone}…", ToastIcon.PHONE) }, onDark = true)
                            IconSquareButton(
                                Icons.Rounded.Description,
                                { navController.navigate(DaftarDestinations.customerStatement(customer.id)) },
                                onDark = true,
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        CustomerBadge(customer, 56.dp)
                        Column {
                            Text(
                                text = customer.name,
                                style = TextStyle(
                                    fontFamily = Fraunces,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 22.sp,
                                    letterSpacing = (-0.02).em,
                                    color = DaftarColors.Paper,
                                ),
                            )
                            Spacer(Modifier.height(6.dp))
                            NetPositionChip(
                                net = state.netReporting,
                                currency = state.reportingCurrency,
                                decimals = state.reportingDecimals,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "${customer.city.displayName.uppercase()} · ${customer.phone}",
                                style = TextStyle(
                                    fontFamily = JetBrainsMono,
                                    fontSize = 11.sp,
                                    letterSpacing = 0.1.em,
                                    color = DaftarColors.GoldSoft,
                                ),
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "Account since ${customer.accountOpenedLabel}",
                                style = TextStyle(
                                    fontFamily = JetBrainsMono,
                                    fontSize = 10.sp,
                                    color = DaftarColors.MutedLight,
                                ),
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = DaftarColors.Paper.copy(alpha = 0.15f))
                    Spacer(Modifier.height(14.dp))
                    // v18 lists USD/AFN/PKR plus any other currency with a balance.
                    DarkBalanceGrid(
                        position = state.balance,
                        statusFor = { amt -> if (amt > 0) "ON DEPOSIT" else if (amt < 0) "OWES YOU" else "SETTLED" },
                        currencies = state.balance.activeCurrencies(),
                    )
                }
            }

            if (customer.notes != null) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(top = 14.dp)
                            .dashedBorder(DaftarColors.Gold.copy(alpha = 0.4f), 1.dp, 10.dp)
                            .background(DaftarColors.Gold.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        MonoLabel("Notes", color = DaftarColors.CopperDeep, fontSize = 9, letterSpacing = 0.15)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = customer.notes,
                            style = TextStyle(fontFamily = Inter, fontSize = 12.sp, color = DaftarColors.InkSoft),
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 16.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MonoLabel("Statement · د حساب کتاب")
                    HorizontalDivider(Modifier.weight(1f), color = DaftarColors.Line)
                    MonoLabel("${customer.transactions.size} entries · running balance")
                }
            }

            if (state.rows.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.AutoMirrored.Rounded.MenuBook,
                        title = "No entries yet",
                        pashto = "تر اوسه هیڅ لیکنه نشته",
                        sub = "Use the You Gave / You Received buttons below to record this account's first entry.",
                        tone = EmptyStateTone.MUTED,
                    )
                }
            } else {
                items(count = state.rows.size, key = { i -> state.rows[i].tx.id }) { i ->
                    StatementHistoryRow(state.rows[i]) {
                        navController.navigate(DaftarDestinations.customerTxDetail(state.rows[i].tx.id))
                    }
                }
            }
        }

        // v18 dual quick-entry FABs: red "You Gave" / green "You Received",
        // both opening the entry form locked to this customer.
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CustomerQuickFab(
                label = "You Gave",
                pashto = "ورکړل",
                icon = Icons.Rounded.ArrowUpward,
                gradient = listOf(DaftarColors.RedDeep, DaftarColors.Red),
                modifier = Modifier.weight(1f),
            ) {
                navController.navigate(
                    DaftarDestinations.newCustomerTx(mode = "gave", customerId = customer.id, locked = true),
                )
            }
            CustomerQuickFab(
                label = "You Received",
                pashto = "ترلاسه کړل",
                icon = Icons.Rounded.ArrowDownward,
                gradient = listOf(DaftarColors.GreenDeep, DaftarColors.Green),
                modifier = Modifier.weight(1f),
            ) {
                navController.navigate(
                    DaftarDestinations.newCustomerTx(mode = "received", customerId = customer.id, locked = true),
                )
            }
        }
    }
}

/** Glowing-dot pill summarising the whole account in the reporting currency. */
@Composable
private fun NetPositionChip(net: Double, currency: String, decimals: Int) {
    val (dotColor, label) = when {
        net > 0 -> DaftarColors.LongGreen to "YOU OWE"
        net < 0 -> DaftarColors.ShortRed to "OWES YOU"
        else -> DaftarColors.GoldSoft to "SETTLED"
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, dotColor.copy(alpha = 0.4f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Text(
            text = label,
            style = TextStyle(
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Bold,
                fontSize = 8.sp,
                letterSpacing = 0.12.em,
                color = dotColor,
            ),
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = Formatters.number(abs(net), decimals),
                style = TextStyle(
                    fontFamily = Fraunces,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = DaftarColors.Paper,
                ),
            )
            Text(
                text = " $currency",
                style = TextStyle(
                    fontFamily = JetBrainsMono,
                    fontSize = 8.sp,
                    letterSpacing = 0.08.em,
                    color = DaftarColors.MutedLight,
                ),
            )
        }
    }
}

/**
 * v18 statement-card history row: date · type · DR/CR chip, note, then the
 * running balance on the left and the signed amount on the right.
 */
@Composable
private fun StatementHistoryRow(row: TxWithRunningBalance, onClick: () -> Unit) {
    val tx = row.tx
    val isDebit = tx.type.isDebit
    val decimals = AssetCatalog.decimalsFor(tx.currency)
    val amountColor = if (isDebit) DaftarColors.Red else DaftarColors.Green
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(DaftarColors.PaperSoft)
            .border(1.dp, DaftarColors.Line, RoundedCornerShape(13.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = tx.dateLabel,
                style = TextStyle(fontFamily = JetBrainsMono, fontSize = 10.sp, color = DaftarColors.Muted),
            )
            Text(
                text = tx.type.statementLabel,
                style = TextStyle(
                    fontFamily = Fraunces,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = DaftarColors.Ink,
                ),
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background((if (isDebit) DaftarColors.Red else DaftarColors.Green).copy(alpha = 0.12f))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            ) {
                Text(
                    text = if (isDebit) "DR" else "CR",
                    style = TextStyle(
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Bold,
                        fontSize = 8.sp,
                        letterSpacing = 0.08.em,
                        color = amountColor,
                    ),
                )
            }
        }
        if (tx.note != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = tx.note,
                style = TextStyle(fontFamily = Inter, fontSize = 12.sp, color = DaftarColors.InkSoft),
            )
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = DaftarColors.Line)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Balance: " + (if (row.balanceAfter >= 0) "+" else "−") +
                    Formatters.number(abs(row.balanceAfter), decimals) + " " + tx.currency,
                style = TextStyle(fontFamily = JetBrainsMono, fontSize = 10.sp, color = DaftarColors.Muted),
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = (if (isDebit) "−" else "+") + Formatters.number(tx.amount, decimals),
                    style = TextStyle(
                        fontFamily = Fraunces,
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp,
                        color = amountColor,
                    ),
                )
                Text(
                    text = " ${tx.currency}",
                    style = TextStyle(
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 0.08.em,
                        color = DaftarColors.Muted,
                    ),
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
        }
    }
}

/** One of the paired bottom FABs: colored gradient, icon disc, label + Pashto. */
@Composable
private fun CustomerQuickFab(
    label: String,
    pashto: String,
    icon: ImageVector,
    gradient: List<Color>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(gradient))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = DaftarColors.Paper,
                modifier = Modifier.size(16.dp),
            )
        }
        Column {
            Text(
                text = label,
                style = TextStyle(
                    fontFamily = Fraunces,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = DaftarColors.Paper,
                ),
            )
            Text(
                text = pashto,
                style = TextStyle(
                    fontFamily = NotoNaskhArabic,
                    fontSize = 10.sp,
                    color = DaftarColors.Paper.copy(alpha = 0.8f),
                    textDirection = TextDirection.Rtl,
                ),
            )
        }
    }
}

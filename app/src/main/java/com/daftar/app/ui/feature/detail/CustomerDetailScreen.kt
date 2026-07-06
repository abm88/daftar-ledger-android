package com.daftar.app.ui.feature.detail

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ChatBubble
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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
import com.daftar.app.domain.model.Customer
import com.daftar.app.domain.model.CustomerTransaction
import com.daftar.app.domain.model.MoneyByCurrency
import com.daftar.app.domain.repository.CustomerRepository
import com.daftar.app.domain.usecase.PositionCalculator
import com.daftar.app.ui.common.IconSquareButton
import com.daftar.app.ui.common.LocalToaster
import com.daftar.app.ui.common.MonoLabel
import com.daftar.app.ui.common.ToastIcon
import com.daftar.app.ui.common.dashedBorder
import com.daftar.app.ui.common.CustomerBadge
import com.daftar.app.ui.components.DarkBalanceGrid
import com.daftar.app.ui.navigation.DaftarDestinations
import com.daftar.app.ui.theme.DaftarColors
import com.daftar.app.ui.theme.Fraunces
import com.daftar.app.ui.theme.Inter
import com.daftar.app.ui.theme.JetBrainsMono
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class CustomerDetailUiState(
    val customer: Customer? = null,
    val balance: MoneyByCurrency = MoneyByCurrency(),
)

@HiltViewModel
class CustomerDetailViewModel @Inject constructor(
    customerRepository: CustomerRepository,
    positionCalculator: PositionCalculator,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val customerId: String = checkNotNull(savedStateHandle["customerId"])

    val uiState = customerRepository.customers
        .map { customers ->
            val customer = customers.firstOrNull { it.id == customerId }
            CustomerDetailUiState(
                customer = customer,
                balance = customer?.let(positionCalculator::customerBalance) ?: MoneyByCurrency(),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CustomerDetailUiState())
}

/** Customer account detail: balances, actions, notes, full history. */
@Composable
fun CustomerDetailScreen(
    navController: NavController,
    viewModel: CustomerDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val customer = state.customer ?: return
    val toaster = LocalToaster.current

    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 40.dp),
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
                        Spacer(Modifier.height(4.dp))
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
                DarkBalanceGrid(
                    position = state.balance,
                    statusFor = { amt -> if (amt > 0.5) "ON DEPOSIT" else if (amt < -0.5) "OWES YOU" else "SETTLED" },
                )
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DaftarColors.PaperDeep)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CustomerAction(
                    icon = Icons.Rounded.Add, label = "New entry", primary = true,
                    modifier = Modifier.weight(1f),
                ) { navController.navigate(DaftarDestinations.newCustomerTx(customerId = customer.id)) }
                CustomerAction(
                    icon = Icons.Rounded.Description, label = "Statement",
                    modifier = Modifier.weight(1f),
                ) { navController.navigate(DaftarDestinations.customerStatement(customer.id)) }
                CustomerAction(
                    icon = Icons.Rounded.ChatBubble, label = "WhatsApp",
                    modifier = Modifier.weight(1f),
                ) { toaster("Opening WhatsApp to ${customer.shortName}", ToastIcon.MESSAGE) }
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
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MonoLabel("Account history")
                HorizontalDivider(Modifier.weight(1f), color = DaftarColors.Line)
                MonoLabel("${customer.transactions.size} entries")
            }
        }

        val txs = customer.transactions.reversed()
        if (txs.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = 30.dp), contentAlignment = Alignment.Center) {
                    Text("No transactions yet", style = TextStyle(fontFamily = Inter, fontSize = 13.sp, color = DaftarColors.Muted))
                }
            }
        } else {
            items(count = txs.size, key = { i -> txs[i].id }) { i ->
                CustomerTxEntryRow(txs[i]) {
                    navController.navigate(DaftarDestinations.customerTxDetail(txs[i].id))
                }
            }
        }
    }
}

@Composable
private fun CustomerAction(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (primary) DaftarColors.Green else DaftarColors.Paper)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (primary) DaftarColors.Paper else DaftarColors.InkSoft,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = label.uppercase(),
            style = TextStyle(
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 0.08.em,
                color = if (primary) DaftarColors.Paper else DaftarColors.InkSoft,
            ),
        )
    }
}

@Composable
private fun CustomerTxEntryRow(tx: CustomerTransaction, onClick: () -> Unit) {
    val isDebit = tx.type.isDebit
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    imageVector = if (isDebit) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                    contentDescription = null,
                    tint = if (isDebit) DaftarColors.Red else DaftarColors.Green,
                    modifier = Modifier.size(11.dp),
                )
                Text(
                    text = tx.type.label.uppercase(),
                    style = TextStyle(
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 0.15.em,
                        color = if (isDebit) DaftarColors.Red else DaftarColors.Green,
                    ),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = tx.dateLabel,
                    style = TextStyle(fontFamily = JetBrainsMono, fontSize = 10.sp, color = DaftarColors.Muted),
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = DaftarColors.MutedLight,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = (if (isDebit) "−" else "+") + Formatters.number(tx.amount),
                style = TextStyle(
                    fontFamily = Fraunces,
                    fontWeight = FontWeight.Medium,
                    fontSize = 20.sp,
                    color = if (isDebit) DaftarColors.Red else DaftarColors.Green,
                ),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = tx.currency,
                style = TextStyle(
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 0.1.em,
                    color = DaftarColors.Muted,
                ),
                modifier = Modifier.padding(bottom = 3.dp),
            )
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
    }
}

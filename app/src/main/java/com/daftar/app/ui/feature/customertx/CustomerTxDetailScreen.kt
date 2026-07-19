package com.daftar.app.ui.feature.customertx

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
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
import com.daftar.app.domain.model.CustomerTransaction
import com.daftar.app.domain.repository.CustomerRepository
import com.daftar.app.domain.usecase.DeleteCustomerTransactionUseCase
import com.daftar.app.domain.usecase.PositionCalculator
import com.daftar.app.ui.common.IconSquareButton
import com.daftar.app.ui.common.LocalToaster
import com.daftar.app.ui.common.FullScreenPhotoViewer
import com.daftar.app.ui.common.MonoLabel
import com.daftar.app.ui.common.PhotoAttachmentSection
import com.daftar.app.ui.common.ToastCenter
import com.daftar.app.ui.common.ToastIcon
import com.daftar.app.ui.common.CustomerBadge
import com.daftar.app.ui.components.DetailCard
import com.daftar.app.ui.components.DetailRow
import com.daftar.app.ui.components.DetailSectionTitle
import com.daftar.app.ui.navigation.DaftarDestinations
import com.daftar.app.ui.theme.DaftarColors
import com.daftar.app.ui.theme.Fraunces
import com.daftar.app.ui.theme.JetBrainsMono
import com.daftar.app.ui.theme.NotoNaskhArabic
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CustomerTxDetailUiState(
    val tx: CustomerTransaction? = null,
    val customer: Customer? = null,
    val balanceBefore: Double = 0.0,
    val balanceAfter: Double = 0.0,
)

@HiltViewModel
class CustomerTxDetailViewModel @Inject constructor(
    private val customerRepository: CustomerRepository,
    private val positionCalculator: PositionCalculator,
    private val deleteTransaction: DeleteCustomerTransactionUseCase,
    private val toastCenter: ToastCenter,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val txId: String = checkNotNull(savedStateHandle["txId"])

    val uiState = customerRepository.customers
        .map { customers ->
            val owner = customers.firstOrNull { c -> c.transactions.any { it.id == txId } }
                ?: return@map CustomerTxDetailUiState()
            val running = positionCalculator.runningBalanceFor(owner, txId)
                ?: return@map CustomerTxDetailUiState()
            CustomerTxDetailUiState(
                tx = running.tx,
                customer = owner,
                balanceBefore = running.balanceBefore,
                balanceAfter = running.balanceAfter,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CustomerTxDetailUiState())

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            deleteTransaction(txId)
            toastCenter.show("Entry deleted", ToastIcon.CROSS)
            onDeleted()
        }
    }

    fun buildReceiptText(state: CustomerTxDetailUiState): String? {
        val tx = state.tx ?: return null
        val customer = state.customer ?: return null
        val gave = tx.type.isDebit
        return buildString {
            appendLine("Daftar — ${if (gave) "You Gave" else "You Received"}")
            appendLine(customer.name)
            appendLine((if (gave) "−" else "+") + Formatters.number(tx.amount) + " " + tx.currency)
            appendLine(tx.dateLabel)
            tx.note?.let(::appendLine)
            append(
                "New balance: " + (if (state.balanceAfter >= 0) "+" else "−") +
                    Formatters.number(abs(state.balanceAfter)) + " " + tx.currency,
            )
        }
    }
}

/** One customer ledger entry, with its balance impact and receipt actions. */
@Composable
fun CustomerTxDetailScreen(
    navController: NavController,
    viewModel: CustomerTxDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val tx = state.tx ?: return
    val customer = state.customer ?: return
    val toaster = LocalToaster.current
    val printContext = androidx.compose.ui.platform.LocalContext.current
    val clipboard = LocalClipboardManager.current
    var viewerUri by remember { mutableStateOf<String?>(null) }

    val isDebit = tx.type.isDebit
    val directionLabel = if (isDebit) "You Gave" else "You Received"
    val directionColor = if (isDebit) DaftarColors.Red else DaftarColors.Green
    val headerBrush = if (isDebit) {
        Brush.linearGradient(listOf(DaftarColors.RedDeep, DaftarColors.Red))
    } else {
        Brush.linearGradient(listOf(DaftarColors.GreenDeep, DaftarColors.Green))
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 20.dp),
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(headerBrush)
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp)
                        .padding(top = 8.dp, bottom = 20.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // v18 back-from-tx always returns to the owning customer's page.
                        IconSquareButton(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            {
                                navController.popBackStack(DaftarDestinations.MAIN, inclusive = false)
                                navController.navigate(DaftarDestinations.customerDetail(customer.id))
                            },
                            onDark = true,
                        )
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White.copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = if (isDebit) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                                contentDescription = null,
                                tint = DaftarColors.Paper,
                                modifier = Modifier.size(10.dp),
                            )
                            Text(
                                directionLabel.uppercase(),
                                style = TextStyle(
                                    fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp, letterSpacing = 0.1.em, color = DaftarColors.Paper,
                                ),
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        MonoLabel(tx.type.label, color = Color.White.copy(alpha = 0.7f), fontSize = 9, letterSpacing = 0.3)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = tx.type.pashtoLabel,
                            style = TextStyle(
                                fontFamily = NotoNaskhArabic,
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.85f),
                                textDirection = TextDirection.Rtl,
                            ),
                        )
                        Spacer(Modifier.height(14.dp))
                        MonoLabel("Amount", color = Color.White.copy(alpha = 0.7f), fontSize = 9, letterSpacing = 0.25)
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                text = (if (isDebit) "−" else "+") + Formatters.number(tx.amount),
                                style = TextStyle(
                                    fontFamily = Fraunces,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 38.sp,
                                    letterSpacing = (-0.02).em,
                                    color = DaftarColors.Paper,
                                ),
                            )
                            Text(
                                // v18 shows "USD $" — code plus symbol.
                                text = " ${tx.currency} ${AssetCatalog.symbolFor(tx.currency)}",
                                style = TextStyle(
                                    fontFamily = JetBrainsMono,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = Color.White.copy(alpha = 0.75f),
                                ),
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        }
                    }
                }
            }

            item {
                DetailSectionTitle("Account holder")
                DetailCard {
                    DetailRow(
                        label = "Account holder",
                        value = customer.name,
                        // v18 folds city and phone into one sub-line.
                        sub = "${customer.city.displayName} · ${customer.phone}",
                        leading = { CustomerBadge(customer, 36.dp) },
                        trailing = {
                            Icon(
                                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = null,
                                tint = DaftarColors.Muted,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        showDivider = false,
                        modifier = Modifier.clickable {
                            navController.navigate(DaftarDestinations.customerDetail(customer.id))
                        },
                    )
                }
            }

            item {
                DetailSectionTitle("Balance impact · ${tx.currency}")
                DetailCard {
                    DetailRow(
                        label = "Before this entry",
                        value = Formatters.signPrefix(state.balanceBefore) +
                            Formatters.number(abs(state.balanceBefore)) + " " + tx.currency,
                        valueColor = when {
                            state.balanceBefore > 0 -> DaftarColors.Green
                            state.balanceBefore < 0 -> DaftarColors.Red
                            else -> DaftarColors.Muted
                        },
                        sub = when {
                            state.balanceBefore > 0 -> "On deposit"
                            state.balanceBefore < 0 -> "Owed you"
                            else -> "Settled"
                        },
                        icon = Icons.Rounded.Schedule,
                    )
                    DetailRow(
                        label = "This entry",
                        value = (if (isDebit) "−" else "+") + Formatters.number(tx.amount) + " " + tx.currency,
                        valueColor = directionColor,
                        sub = directionLabel,
                        icon = if (isDebit) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                        iconTint = DaftarColors.Paper,
                        iconBackground = directionColor,
                        background = DaftarColors.Copper.copy(alpha = 0.06f),
                    )
                    DetailRow(
                        label = "After this entry",
                        value = Formatters.signPrefix(state.balanceAfter) +
                            Formatters.number(abs(state.balanceAfter)) + " " + tx.currency,
                        valueColor = when {
                            state.balanceAfter > 0 -> DaftarColors.LongGreen
                            state.balanceAfter < 0 -> DaftarColors.ShortRed
                            else -> DaftarColors.Paper
                        },
                        sub = when {
                            state.balanceAfter > 0 -> "On deposit · امانت"
                            state.balanceAfter < 0 -> "Owes you · پور"
                            else -> "Settled · صفر"
                        },
                        icon = Icons.Rounded.Check,
                        iconTint = DaftarColors.Paper,
                        iconBackground = DaftarColors.Paper.copy(alpha = 0.15f),
                        background = DaftarColors.Ink,
                        labelColor = DaftarColors.GoldSoft,
                        subColor = DaftarColors.GoldSoft,
                        showDivider = false,
                    )
                }
            }

            if (tx.conversion != null) {
                item {
                    val conv = tx.conversion
                    DetailSectionTitle("Currency conversion · تبادله")
                    DetailCard {
                        DetailRow(
                            label = "Cash received",
                            value = Formatters.amount(conv.receivedAmount, conv.receivedCurrency) + " " + conv.receivedCurrency,
                            icon = Icons.Rounded.ArrowDownward,
                            iconTint = DaftarColors.CopperDeep,
                            iconBackground = DaftarColors.Copper.copy(alpha = 0.18f),
                        )
                        DetailRow(
                            label = "Conversion rate",
                            value = "${conv.rate} ${conv.receivedCurrency} → ${conv.creditedCurrency}",
                            aside = "manual", // v18 keeps this lowercase
                            icon = Icons.Rounded.Tag,
                            iconTint = DaftarColors.CopperDeep,
                            iconBackground = DaftarColors.Gold.copy(alpha = 0.2f),
                        )
                        DetailRow(
                            label = "Credited to account",
                            value = Formatters.amount(conv.creditedAmount, conv.creditedCurrency) + " " + conv.creditedCurrency,
                            valueColor = DaftarColors.Green,
                            icon = Icons.Rounded.Check,
                            iconTint = DaftarColors.Paper,
                            iconBackground = DaftarColors.Green,
                            background = DaftarColors.Green.copy(alpha = 0.08f),
                            showDivider = false,
                        )
                    }
                }
            }

            item {
                DetailSectionTitle("Details")
                DetailCard {
                    DetailRow(
                        label = "Recorded on",
                        value = tx.dateLabel,
                        aside = "#" + tx.id.takeLast(6).uppercase(),
                        icon = Icons.Rounded.Schedule,
                    )
                    DetailRow(
                        label = "Type",
                        value = tx.type.label,
                        icon = Icons.Rounded.Tag,
                        showDivider = tx.note != null,
                    )
                    if (tx.note != null) {
                        DetailRow(
                            label = "Note · یادښت",
                            value = tx.note,
                            icon = Icons.Rounded.Description,
                            showDivider = false,
                        )
                    }
                }
            }

            // v20: attached receipt photos (same 5-per-row grid, tap to enlarge).
            if (tx.photoUris.isNotEmpty()) {
                item {
                    DetailSectionTitle("Photos · انځورونه")
                    PhotoAttachmentSection(
                        uris = tx.photoUris,
                        editable = false,
                        onOpen = { viewerUri = it },
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }

            item {
                DetailSectionTitle("Share with account holder")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ShareAction(Icons.Rounded.ChatBubble, "WhatsApp", Modifier.weight(1f)) {
                        toaster("Receipt sent via WhatsApp", ToastIcon.MESSAGE)
                    }
                    ShareAction(Icons.Rounded.Print, "Print", Modifier.weight(1f)) {
                        // v18 tx-print-receipt: toast, then a real print dialog.
                        toaster("Print dialog opened", ToastIcon.PRINTER)
                        com.daftar.app.core.print.StatementPrinter.print(
                            printContext,
                            com.daftar.app.core.print.StatementPrintSpec(
                                jobName = "receipt-${tx.id}",
                                docTitle = "Receipt",
                                pashtoTitle = "رسید",
                                metaLeftLabel = "Account holder",
                                metaLeftValue = customer.name,
                                metaLeftSub = "${customer.city.displayName} · ${customer.phone}",
                                metaRightLabel = "Date",
                                metaRightValue = tx.dateLabel,
                                metaRightSub = tx.type.label,
                                summary = emptyList(),
                                columns = listOf("Type", "Description", "Cur", "Amount", "New balance"),
                                rows = listOf(
                                    listOf(
                                        tx.type.label,
                                        tx.note ?: "—",
                                        tx.currency,
                                        (if (tx.type.isDebit) "−" else "+") + Formatters.number(tx.amount),
                                        Formatters.signPrefix(state.balanceAfter).ifEmpty { "+" } +
                                            Formatters.number(kotlin.math.abs(state.balanceAfter)),
                                    ),
                                ),
                                profile = com.daftar.app.domain.model.ShopProfile(),
                                issuedLabel = tx.dateLabel,
                            ),
                        )
                    }
                    ShareAction(Icons.Rounded.ContentCopy, "Copy", Modifier.weight(1f)) {
                        viewModel.buildReceiptText(state)?.let { receipt ->
                            clipboard.setText(AnnotatedString(receipt))
                        }
                        toaster("Receipt copied", ToastIcon.COPY)
                    }
                }
            }
        }

        // Bottom actions — Edit (stub) and Delete
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DaftarColors.Paper)
                .padding(horizontal = 18.dp, vertical = 14.dp)
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.5.dp, DaftarColors.Ink, RoundedCornerShape(14.dp))
                    .clickable { toaster("Edit flow coming next", ToastIcon.HASH) }
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Tag, null, tint = DaftarColors.Ink, modifier = Modifier.size(14.dp))
                Spacer(Modifier.size(8.dp))
                Text(
                    "EDIT ENTRY",
                    style = TextStyle(
                        fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold,
                        fontSize = 12.sp, letterSpacing = 0.08.em, color = DaftarColors.Ink,
                    ),
                )
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(DaftarColors.Red)
                    .clickable {
                        viewModel.delete {
                            // v18 routes to the customer's page after deleting.
                            navController.popBackStack(DaftarDestinations.MAIN, inclusive = false)
                            navController.navigate(DaftarDestinations.customerDetail(customer.id))
                        }
                    }
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Close, null, tint = DaftarColors.Paper, modifier = Modifier.size(14.dp))
                Spacer(Modifier.size(8.dp))
                Text(
                    "DELETE",
                    style = TextStyle(
                        fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold,
                        fontSize = 12.sp, letterSpacing = 0.08.em, color = DaftarColors.Paper,
                    ),
                )
            }
        }
    }

    viewerUri?.let { uri ->
        FullScreenPhotoViewer(uri, onDismiss = { viewerUri = null })
    }
}

@Composable
private fun ShareAction(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(DaftarColors.Paper)
            .border(1.dp, DaftarColors.Line, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = DaftarColors.InkSoft, modifier = Modifier.size(14.dp))
        Text(
            text = label.uppercase(),
            style = TextStyle(
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 0.08.em,
                color = DaftarColors.InkSoft,
            ),
        )
    }
}

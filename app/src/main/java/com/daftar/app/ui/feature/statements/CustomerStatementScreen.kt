package com.daftar.app.ui.feature.statements

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.daftar.app.core.time.TimeProvider
import com.daftar.app.domain.model.AssetCatalog
import com.daftar.app.domain.model.Customer
import com.daftar.app.domain.model.MoneyByCurrency
import com.daftar.app.domain.model.ShopProfile
import com.daftar.app.domain.repository.CustomerRepository
import com.daftar.app.domain.repository.SettingsRepository
import com.daftar.app.domain.usecase.PositionCalculator
import com.daftar.app.domain.usecase.TxWithRunningBalance
import com.daftar.app.ui.common.DaftarFilterChip
import com.daftar.app.ui.common.IconSquareButton
import com.daftar.app.ui.common.MonoLabel
import com.daftar.app.ui.theme.DaftarColors
import com.daftar.app.ui.theme.Fraunces
import com.daftar.app.ui.theme.Inter
import com.daftar.app.ui.theme.JetBrainsMono
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** v18 statement period chips: All / 30 days / 90 days / 1 year / Custom. */
enum class StatementPeriod(val chipLabel: String) {
    ALL("All"),
    D30("30 days"),
    D90("90 days"),
    Y1("1 year"),
    CUSTOM("Custom"),
}

data class CustomerStatementUiState(
    val customer: Customer? = null,
    val balance: MoneyByCurrency = MoneyByCurrency(),
    /** Rows inside the selected period; running balances carry forward from before it. */
    val rows: List<TxWithRunningBalance> = emptyList(),
    val totalCount: Int = 0,
    val profile: ShopProfile = ShopProfile(),
    val issuedLabel: String = "",
    val period: StatementPeriod = StatementPeriod.ALL,
    val customStart: String = "",
    val customEnd: String = "",
    val periodLabel: String = "All time",
)

@HiltViewModel
class CustomerStatementViewModel @Inject constructor(
    customerRepository: CustomerRepository,
    settingsRepository: SettingsRepository,
    positionCalculator: PositionCalculator,
    private val timeProvider: TimeProvider,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val customerId: String = checkNotNull(savedStateHandle["customerId"])

    private val period = kotlinx.coroutines.flow.MutableStateFlow(StatementPeriod.ALL)
    private val customStart = kotlinx.coroutines.flow.MutableStateFlow("")
    private val customEnd = kotlinx.coroutines.flow.MutableStateFlow("")

    fun setPeriod(value: StatementPeriod) { period.value = value }
    fun setCustomStart(value: String) { customStart.value = value }
    fun setCustomEnd(value: String) { customEnd.value = value }

    val uiState = combine(
        customerRepository.customers,
        settingsRepository.shopProfile,
        period,
        customStart,
        customEnd,
    ) { customers, profile, period, startText, endText ->
        val customer = customers.firstOrNull { it.id == customerId }
        val now = timeProvider.nowMillis()

        // v18 period bounds: N × 86,400,000 ms back from now; custom runs from
        // start-of-day to end-of-day of the typed dates.
        val startMillis = when (period) {
            StatementPeriod.ALL -> 0L
            StatementPeriod.D30 -> now - 30L * DAY_MS
            StatementPeriod.D90 -> now - 90L * DAY_MS
            StatementPeriod.Y1 -> now - 365L * DAY_MS
            StatementPeriod.CUSTOM -> parseDate(startText)?.let { it } ?: 0L
        }
        val endMillis = when (period) {
            StatementPeriod.CUSTOM -> parseDate(endText)?.let { it + DAY_MS - 1 } ?: Long.MAX_VALUE
            else -> Long.MAX_VALUE
        }

        // Running balance is computed over ALL transactions, then only rows in
        // range are shown — so each row's balance is carried forward (v18).
        val allRows = customer?.let(positionCalculator::runningBalances) ?: emptyList()
        val visible = allRows.filter { it.tx.timestampMillis in startMillis..endMillis }

        val label = when (period) {
            StatementPeriod.ALL -> "All time"
            StatementPeriod.D30 -> "Last 30 days"
            StatementPeriod.D90 -> "Last 90 days"
            StatementPeriod.Y1 -> "Last 1 year"
            StatementPeriod.CUSTOM ->
                (startText.ifBlank { "…" }) + " → " + (endText.ifBlank { "today" })
        }

        CustomerStatementUiState(
            customer = customer,
            balance = customer?.let(positionCalculator::customerBalance) ?: MoneyByCurrency(),
            rows = visible,
            totalCount = allRows.size,
            profile = profile,
            issuedLabel = Formatters.fullDateLabel(now),
            period = period,
            customStart = startText,
            customEnd = endText,
            periodLabel = label,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CustomerStatementUiState())

    private fun parseDate(text: String): Long? = try {
        if (text.isBlank()) null
        else java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .apply { isLenient = false }
            .parse(text)?.time
    } catch (_: java.text.ParseException) {
        null
    }

    companion object {
        private const val DAY_MS = 86_400_000L
    }
}

/** Printable account statement for one customer with running balances. */
@Composable
fun CustomerStatementScreen(
    navController: NavController,
    viewModel: CustomerStatementViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val customer = state.customer ?: return

    // v18 buildStatementPrintHtml: same rows/filter as the screen, per-asset
    // decimals, "statement-{name}" file name.
    val printSpec = {
        com.daftar.app.core.print.StatementPrintSpec(
            jobName = "statement-" + customer.name.lowercase().replace(Regex("[^a-z0-9]+"), "-"),
            docTitle = "Account Statement",
            pashtoTitle = "د حساب راپور",
            metaLeftLabel = "Account holder",
            metaLeftValue = customer.name,
            metaLeftSub = customer.phone,
            metaRightLabel = "Issued",
            metaRightValue = state.issuedLabel,
            metaRightSub = "Period · ${state.periodLabel}",
            summary = AssetCatalog.LEDGER_CURRENCIES.map { cur ->
                val amt = state.balance[cur]
                com.daftar.app.core.print.PrintSummaryCell(
                    heading = cur,
                    amount = Formatters.signPrefix(amt) + Formatters.number(abs(amt)),
                    status = if (amt > 0) "ON DEPOSIT" else if (amt < 0) "OWES" else "SETTLED",
                )
            },
            columns = listOf("Date", "Type", "Description", "Cur", "Debit", "Credit", "Balance"),
            rows = state.rows.map { row ->
                val tx = row.tx
                val decimals = AssetCatalog.decimalsFor(tx.currency)
                val amountText = Formatters.number(tx.amount, decimals)
                listOf(
                    tx.dateLabel,
                    tx.type.statementLabel,
                    tx.note ?: "—",
                    tx.currency,
                    if (tx.type.isDebit) amountText else "—",
                    if (tx.type.isDebit) "—" else amountText,
                    (if (row.balanceAfter >= 0) "+" else "−") +
                        Formatters.number(abs(row.balanceAfter), decimals),
                )
            },
            profile = state.profile,
            issuedLabel = state.issuedLabel,
        )
    }
    val printContext = androidx.compose.ui.platform.LocalContext.current

    Column(modifier = Modifier.fillMaxWidth()) {
        StatementHeaderBar(
            title = "Account Statement",
            subtitle = customer.name,
            onBack = { navController.popBackStack() },
            trailing = {
                // v18 header download icon triggers the same PDF flow.
                IconSquareButton(
                    androidx.compose.material.icons.Icons.Rounded.Download,
                    { com.daftar.app.core.print.StatementPrinter.print(printContext, printSpec()) },
                    onDark = true,
                )
            },
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(DaftarColors.PaperSoft)
                .border(1.dp, DaftarColors.LineStrong, RoundedCornerShape(8.dp))
                .padding(18.dp),
        ) {
            StatementMasthead(state.profile, "Account Statement", "د حساب راپور")
            StatementMetaRow(
                leftLabel = "Account holder", leftValue = customer.name, leftSub = customer.phone,
                rightLabel = "Issued", rightValue = state.issuedLabel,
                // v18 shows the active period instead of the account-open date.
                rightSub = "Period · ${state.periodLabel}",
            )

            Spacer(Modifier.height(12.dp))
            // v18 period chips with optional custom date range.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StatementPeriod.entries.forEach { p ->
                    DaftarFilterChip(
                        label = p.chipLabel,
                        selected = state.period == p,
                        onClick = { viewModel.setPeriod(p) },
                    )
                }
            }
            if (state.period == StatementPeriod.CUSTOM) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // v18 uses native date inputs; a mono YYYY-MM-DD field is the
                    // lightweight Android stand-in.
                    StatementDateField(
                        label = "From · له",
                        value = state.customStart,
                        onChange = viewModel::setCustomStart,
                        modifier = Modifier.weight(1f),
                    )
                    StatementDateField(
                        label = "To · تر",
                        value = state.customEnd,
                        onChange = viewModel::setCustomEnd,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            StatementSummary(
                AssetCatalog.LEDGER_CURRENCIES.map { cur ->
                    val amt = state.balance[cur]
                    // v18 signs/labels on any non-zero balance.
                    StatementSummaryCell(
                        heading = cur,
                        amount = Formatters.signPrefix(amt) + Formatters.number(abs(amt)),
                        status = if (amt > 0) "ON DEPOSIT" else if (amt < 0) "OWES" else "SETTLED",
                        color = when {
                            amt > 0 -> DaftarColors.Green
                            amt < 0 -> DaftarColors.Red
                            else -> DaftarColors.Muted
                        },
                    )
                },
            )

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                MonoLabel("Transactions · تراکنشونه", fontSize = 9)
                MonoLabel("${state.rows.size} in period", fontSize = 9)
            }
            Spacer(Modifier.height(8.dp))

            if (state.rows.isEmpty()) {
                MonoLabel(
                    if (state.totalCount == 0) "No transactions yet" else "No transactions in this period",
                    fontSize = 10,
                )
            } else {
                state.rows.forEach { row ->
                    StatementTxCard(row)
                }
            }

            StatementFooter(state.issuedLabel.uppercase())
        }

        StatementActions(modifier = Modifier.navigationBarsPadding(), printSpec = printSpec)
    }
}

@Composable
private fun StatementDateField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(DaftarColors.Paper)
            .border(1.dp, DaftarColors.LineStrong, RoundedCornerShape(9.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        MonoLabel(label, fontSize = 9, letterSpacing = 0.15)
        Spacer(Modifier.height(2.dp))
        androidx.compose.foundation.layout.Box {
            if (value.isEmpty()) {
                Text(
                    "YYYY-MM-DD",
                    style = TextStyle(fontFamily = JetBrainsMono, fontSize = 12.sp, color = DaftarColors.MutedLight),
                )
            }
            androidx.compose.foundation.text.BasicTextField(
                value = value,
                onValueChange = { text -> onChange(text.filter { it.isDigit() || it == '-' }.take(10)) },
                textStyle = TextStyle(fontFamily = JetBrainsMono, fontSize = 12.sp, color = DaftarColors.Ink),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun StatementHeaderBar(title: String, subtitle: String, onBack: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DaftarColors.Ink)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconSquareButton(Icons.AutoMirrored.Rounded.ArrowBack, onBack, onDark = true)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 18.sp, color = DaftarColors.Paper),
            )
            Text(
                subtitle,
                style = TextStyle(fontFamily = JetBrainsMono, fontSize = 10.sp, color = DaftarColors.GoldSoft),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        trailing?.invoke()
    }
}

@Composable
private fun StatementTxCard(row: TxWithRunningBalance) {
    val tx = row.tx
    val isDebit = tx.type.isDebit
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(DaftarColors.Paper)
            .border(1.dp, DaftarColors.Line, RoundedCornerShape(8.dp))
            .padding(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                tx.dateLabel,
                style = TextStyle(fontFamily = JetBrainsMono, fontSize = 9.sp, color = DaftarColors.Muted),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                tx.type.statementLabel,
                style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = DaftarColors.Ink),
                modifier = Modifier.weight(1f),
            )
            Text(
                if (isDebit) "DR" else "CR",
                style = TextStyle(
                    fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 9.sp,
                    letterSpacing = 0.1.em,
                    color = if (isDebit) DaftarColors.Red else DaftarColors.Green,
                ),
            )
        }
        if (tx.note != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                tx.note,
                style = TextStyle(fontFamily = Inter, fontSize = 10.sp, color = DaftarColors.InkSoft),
            )
        }
        Spacer(Modifier.height(6.dp))
        HorizontalDivider(color = DaftarColors.Line)
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "Balance: " + Formatters.signPrefix(row.balanceAfter).ifEmpty { "+" } +
                    Formatters.number(abs(row.balanceAfter)) + " " + tx.currency,
                style = TextStyle(fontFamily = JetBrainsMono, fontSize = 9.sp, color = DaftarColors.Muted),
            )
            Text(
                (if (isDebit) "−" else "+") + Formatters.amount(tx.amount, tx.currency) + " " + tx.currency,
                style = TextStyle(
                    fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 13.sp,
                    color = if (isDebit) DaftarColors.Red else DaftarColors.Green,
                ),
            )
        }
    }
}

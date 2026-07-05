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

data class CustomerStatementUiState(
    val customer: Customer? = null,
    val balance: MoneyByCurrency = MoneyByCurrency(),
    val rows: List<TxWithRunningBalance> = emptyList(),
    val profile: ShopProfile = ShopProfile(),
    val issuedLabel: String = "",
)

@HiltViewModel
class CustomerStatementViewModel @Inject constructor(
    customerRepository: CustomerRepository,
    settingsRepository: SettingsRepository,
    positionCalculator: PositionCalculator,
    timeProvider: TimeProvider,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val customerId: String = checkNotNull(savedStateHandle["customerId"])

    val uiState = combine(
        customerRepository.customers,
        settingsRepository.shopProfile,
    ) { customers, profile ->
        val customer = customers.firstOrNull { it.id == customerId }
        CustomerStatementUiState(
            customer = customer,
            balance = customer?.let(positionCalculator::customerBalance) ?: MoneyByCurrency(),
            rows = customer?.let(positionCalculator::runningBalances) ?: emptyList(),
            profile = profile,
            issuedLabel = Formatters.fullDateLabel(timeProvider.nowMillis()),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CustomerStatementUiState())
}

/** Printable account statement for one customer with running balances. */
@Composable
fun CustomerStatementScreen(
    navController: NavController,
    viewModel: CustomerStatementViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val customer = state.customer ?: return

    Column(modifier = Modifier.fillMaxWidth()) {
        StatementHeaderBar(
            title = "Account Statement",
            subtitle = customer.name,
            onBack = { navController.popBackStack() },
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
                rightLabel = "Issued", rightValue = state.issuedLabel, rightSub = "Since ${customer.accountOpenedLabel}",
            )
            StatementSummary(
                AssetCatalog.LEDGER_CURRENCIES.map { cur ->
                    val amt = state.balance[cur]
                    StatementSummaryCell(
                        heading = cur,
                        amount = Formatters.signPrefix(amt, 0.5) + Formatters.number(abs(amt)),
                        status = if (amt > 0.5) "ON DEPOSIT" else if (amt < -0.5) "OWES" else "SETTLED",
                        color = when {
                            amt > 0.5 -> DaftarColors.Green
                            amt < -0.5 -> DaftarColors.Red
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
                MonoLabel("${state.rows.size} entries", fontSize = 9)
            }
            Spacer(Modifier.height(8.dp))

            if (state.rows.isEmpty()) {
                MonoLabel("No transactions yet", fontSize = 10)
            } else {
                state.rows.forEach { row ->
                    StatementTxCard(row)
                }
            }

            StatementFooter(state.issuedLabel.uppercase())
        }

        StatementActions(modifier = Modifier.navigationBarsPadding())
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
                tx.type.label,
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

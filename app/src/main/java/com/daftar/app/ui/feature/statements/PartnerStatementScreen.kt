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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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
import com.daftar.app.core.time.TimeProvider
import com.daftar.app.domain.model.AssetCatalog
import com.daftar.app.domain.model.Counterparty
import com.daftar.app.domain.model.Hawala
import com.daftar.app.domain.model.HawalaStatus
import com.daftar.app.domain.model.HawalaType
import com.daftar.app.domain.model.MoneyByCurrency
import com.daftar.app.domain.model.SYNTHETIC_CODE
import com.daftar.app.domain.model.ShopProfile
import com.daftar.app.domain.repository.PartnerRepository
import com.daftar.app.domain.repository.SettingsRepository
import com.daftar.app.domain.usecase.PositionCalculator
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

/** One statement line: the entry plus the running balance in its currency. */
data class HawalaStatementRow(val hawala: Hawala, val runningBalance: Double)

data class PartnerStatementUiState(
    val partner: Counterparty? = null,
    val position: MoneyByCurrency = MoneyByCurrency(),
    val rows: List<HawalaStatementRow> = emptyList(),
    val profile: ShopProfile = ShopProfile(),
    val issuedLabel: String = "",
)

@HiltViewModel
class PartnerStatementViewModel @Inject constructor(
    partnerRepository: PartnerRepository,
    settingsRepository: SettingsRepository,
    positionCalculator: PositionCalculator,
    timeProvider: TimeProvider,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val partnerId: String = checkNotNull(savedStateHandle["partnerId"])

    val uiState = combine(
        partnerRepository.partners,
        settingsRepository.shopProfile,
    ) { partners, profile ->
        val partner = partners.firstOrNull { it.id == partnerId }
        val running = mutableMapOf<String, Double>()
        val rows = partner?.hawalas
            ?.sortedBy { it.timestampMillis }
            ?.map { h ->
                if (h.status == HawalaStatus.PAID) {
                    running[h.currency] = (running[h.currency] ?: 0.0) + h.positionDelta
                }
                HawalaStatementRow(h, running[h.currency] ?: 0.0)
            }
            ?: emptyList()
        PartnerStatementUiState(
            partner = partner,
            position = partner?.let(positionCalculator::partnerPosition) ?: MoneyByCurrency(),
            rows = rows,
            profile = profile,
            issuedLabel = Formatters.fullDateLabel(timeProvider.nowMillis()),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PartnerStatementUiState())
}

/** Printable running-balance statement for one counterparty saraf. */
@Composable
fun PartnerStatementScreen(
    navController: NavController,
    viewModel: PartnerStatementViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val partner = state.partner ?: return

    // v18 cp-statement print doc ("cp-statement-{shortname}").
    val printSpec = {
        com.daftar.app.core.print.StatementPrintSpec(
            jobName = "cp-statement-" + partner.shortName.lowercase().replace(Regex("[^a-z0-9]+"), "-"),
            docTitle = "Partner Statement",
            pashtoTitle = "د همکار صرافي حساب",
            metaLeftLabel = "Partner",
            metaLeftValue = partner.name,
            metaLeftSub = "${partner.city.displayName} · ${partner.phone}",
            metaRightLabel = "Issued",
            metaRightValue = state.issuedLabel,
            metaRightSub = partner.tier.label.replace("-", "").uppercase() + " tier",
            summary = AssetCatalog.LEDGER_CURRENCIES.map { cur ->
                val amt = state.position[cur]
                com.daftar.app.core.print.PrintSummaryCell(
                    heading = cur,
                    amount = Formatters.signPrefix(amt) + Formatters.number(abs(amt)),
                    status = if (amt > 0) "LONG" else if (amt < 0) "SHORT" else "FLAT",
                )
            },
            columns = listOf("Date", "Entry", "Detail", "Cur", "Debit", "Credit", "Balance"),
            rows = state.rows.map { row ->
                val h = row.hawala
                val pending = h.status == com.daftar.app.domain.model.HawalaStatus.PENDING
                val amountText = Formatters.number(h.amount)
                val debit = if (!pending && h.positionDelta < 0) Formatters.number(abs(h.positionDelta)) else "—"
                val credit = if (!pending && h.positionDelta > 0) Formatters.number(h.positionDelta) else "—"
                listOf(
                    h.dateLabel + if (pending) " (pending)" else "",
                    when (h.type) {
                        com.daftar.app.domain.model.HawalaType.SETTLEMENT -> "Settlement"
                        com.daftar.app.domain.model.HawalaType.SEND -> "Sent hawala"
                        com.daftar.app.domain.model.HawalaType.RECEIVE -> "Received hawala"
                    },
                    if (h.type == com.daftar.app.domain.model.HawalaType.SETTLEMENT) (h.note ?: "Settled")
                    else "${h.senderName} → ${h.receiverName}" +
                        (if (h.pickupCode != com.daftar.app.domain.model.SYNTHETIC_CODE) " · ${h.pickupCode}" else ""),
                    h.currency,
                    debit,
                    credit,
                    Formatters.signPrefix(row.runningBalance).ifEmpty { "+" } + Formatters.number(abs(row.runningBalance)),
                )
            },
            profile = state.profile,
            issuedLabel = state.issuedLabel,
        )
    }
    val printContext = androidx.compose.ui.platform.LocalContext.current

    Column(modifier = Modifier.fillMaxWidth()) {
        StatementHeaderBar(
            title = "Partner Statement",
            subtitle = partner.name,
            onBack = { navController.popBackStack() },
            trailing = {
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
            StatementMasthead(state.profile, "Partner Statement", "د همکار صرافي حساب")
            StatementMetaRow(
                leftLabel = "Partner", leftValue = partner.name,
                leftSub = "${partner.city.displayName} · ${partner.phone}",
                rightLabel = "Issued", rightValue = state.issuedLabel,
                rightSub = partner.tier.label.replace("-", "").uppercase() + " tier", // v18: "ADHOC tier"
            )
            StatementSummary(
                AssetCatalog.LEDGER_CURRENCIES.map { cur ->
                    val amt = state.position[cur]
                    // v18 signs/labels on any non-zero position.
                    StatementSummaryCell(
                        heading = cur,
                        amount = Formatters.signPrefix(amt) + Formatters.number(abs(amt)),
                        status = if (amt > 0) "OWES YOU" else if (amt < 0) "YOU OWE" else "SETTLED",
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
                MonoLabel("Entries", fontSize = 9)
                MonoLabel("Debit · Credit · Balance", fontSize = 9)
            }
            Spacer(Modifier.height(8.dp))

            state.rows.forEach { row -> PartnerStatementCard(row) }

            StatementFooter(state.issuedLabel.uppercase())
        }

        StatementActions(modifier = Modifier.navigationBarsPadding(), printSpec = printSpec)
    }
}

@Composable
private fun PartnerStatementCard(row: HawalaStatementRow) {
    val h = row.hawala
    // A SEND (they owe us) reads as a credit line; RECEIVE as a debit; settlements by sign.
    val isCredit = h.type == HawalaType.SEND || (h.type == HawalaType.SETTLEMENT && h.amount > 0)
    val paid = h.status == HawalaStatus.PAID
    val description = when (h.type) {
        HawalaType.SEND -> "Sent hawala"
        HawalaType.RECEIVE -> "Received hawala"
        HawalaType.SETTLEMENT -> "Settlement"
    }
    val subDescription = when (h.type) {
        HawalaType.SETTLEMENT -> h.note ?: "Settlement entry"
        else -> "${h.fromCity.code} → ${h.toCity.code} · ${h.senderName} to ${h.receiverName}" +
            (if (h.pickupCode != SYNTHETIC_CODE) " · code ${h.pickupCode}" else "")
    }
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
            Text(h.dateLabel, style = TextStyle(fontFamily = JetBrainsMono, fontSize = 9.sp, color = DaftarColors.Muted))
            Spacer(Modifier.width(10.dp))
            Text(
                text = description + if (!paid) " (pending)" else "",
                style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = DaftarColors.Ink),
                modifier = Modifier.weight(1f),
            )
            Text(
                h.currency,
                style = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = 0.1.em, color = DaftarColors.Muted),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            subDescription,
            style = TextStyle(fontFamily = Inter, fontSize = 10.sp, color = DaftarColors.InkSoft),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        HorizontalDivider(color = DaftarColors.Line)
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Debit " + (if (!isCredit && paid) Formatters.number(abs(h.amount)) else "—") +
                    "  ·  Credit " + (if (isCredit && paid) Formatters.number(abs(h.amount)) else "—"),
                style = TextStyle(fontFamily = JetBrainsMono, fontSize = 9.sp, color = DaftarColors.Muted),
            )
            Text(
                text = "Bal " + if (paid) {
                    (if (row.runningBalance >= 0) "" else "−") + Formatters.number(abs(row.runningBalance))
                } else "—",
                style = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 9.sp, color = DaftarColors.Ink),
            )
        }
    }
}

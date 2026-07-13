package com.daftar.app.ui.feature.investments

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.BusinessCenter
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.daftar.app.core.format.Formatters
import com.daftar.app.domain.model.Asset
import com.daftar.app.domain.model.AssetCatalog
import com.daftar.app.domain.model.AssetType
import com.daftar.app.domain.model.Investment
import com.daftar.app.domain.model.InvestmentType
import com.daftar.app.domain.repository.CashRepository
import com.daftar.app.domain.repository.InvestmentRepository
import com.daftar.app.domain.repository.RatesRepository
import com.daftar.app.domain.repository.SettingsRepository
import com.daftar.app.domain.usecase.CurrencyConverter
import com.daftar.app.domain.usecase.RecordInvestmentUseCase
import com.daftar.app.ui.common.FieldBox
import com.daftar.app.ui.common.FieldTextInput
import com.daftar.app.ui.common.IconSquareButton
import com.daftar.app.ui.common.MonoLabel
import com.daftar.app.ui.common.SheetHandle
import com.daftar.app.ui.common.SubmitButton
import com.daftar.app.ui.common.ToastCenter
import com.daftar.app.ui.common.ToastIcon
import com.daftar.app.ui.common.sanitizeAmountInput
import com.daftar.app.ui.feature.statements.StatementHeaderBar
import com.daftar.app.ui.components.EmptyState
import com.daftar.app.ui.components.EmptyStateTone
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
import kotlinx.coroutines.launch

data class AssetInvestmentTotals(
    val asset: Asset,
    val invested: Double,
    val withdrawn: Double,
    val net: Double,
    val count: Int,
)

data class InvestmentsUiState(
    val totalInvestedRep: Double = 0.0,
    val currentEquityRep: Double = 0.0,
    val reportingCurrency: String = "AFN",
    val reportingDecimals: Int = 0,
    val perAsset: List<AssetInvestmentTotals> = emptyList(),
    val entries: List<Investment> = emptyList(),
    val activeAssets: List<Asset> = emptyList(),
) {
    val roiAbsolute: Double get() = currentEquityRep - totalInvestedRep
    val roiPercent: Double get() = if (totalInvestedRep > 0) roiAbsolute / totalInvestedRep * 100 else 0.0
}

@HiltViewModel
class InvestmentsViewModel @Inject constructor(
    investmentRepository: InvestmentRepository,
    cashRepository: CashRepository,
    ratesRepository: RatesRepository,
    settingsRepository: SettingsRepository,
    converter: CurrencyConverter,
    private val recordInvestment: RecordInvestmentUseCase,
    private val toastCenter: ToastCenter,
) : ViewModel() {

    val uiState = combine(
        investmentRepository.investments,
        cashRepository.drawer,
        ratesRepository.rateBook,
        settingsRepository.settings,
    ) { investments, drawer, rates, settings ->
        // v18 builds assetTotals while walking entries newest-first, so assets
        // appear in most-recently-touched order.
        val perAsset = investments
            .sortedByDescending { it.timestampMillis }
            .groupBy { it.assetCode }
            .mapNotNull { (code, list) ->
                val asset = AssetCatalog.byCode(code) ?: return@mapNotNull null
                val invested = list.filter { !it.type.isOutflow }.sumOf { it.amount }
                val withdrawn = list.filter { it.type.isOutflow }.sumOf { it.amount }
                AssetInvestmentTotals(asset, invested, withdrawn, invested - withdrawn, list.size)
            }

        val totalInvestedRep = perAsset.sumOf { totals ->
            converter.toReporting(totals.asset.code, totals.net, rates, settings)
        }
        val currentEquityRep = settings.activeAssets().sumOf { asset ->
            converter.toReporting(asset.code, drawer.balanceOf(asset.code), rates, settings)
        }

        InvestmentsUiState(
            totalInvestedRep = totalInvestedRep,
            currentEquityRep = currentEquityRep,
            reportingCurrency = settings.reportingCurrency,
            reportingDecimals = AssetCatalog.decimalsFor(settings.reportingCurrency),
            perAsset = perAsset,
            entries = investments.sortedByDescending { it.timestampMillis },
            activeAssets = settings.activeAssets(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), InvestmentsUiState())

    fun save(type: InvestmentType, assetCode: String, amountText: String, note: String, onSaved: () -> Unit) {
        val amount = amountText.toDoubleOrNull()
        if (amount == null || amount <= 0) {
            toastCenter.show("Enter a valid amount and asset", ToastIcon.CROSS)
            return
        }
        viewModelScope.launch {
            val investment = recordInvestment(type, assetCode, amount, note)
            if (investment != null) {
                toastCenter.show(
                    (if (type.isOutflow) "Withdrawal recorded" else "Investment recorded") +
                        " · ${Formatters.number(amount)} $assetCode",
                    if (type.isOutflow) ToastIcon.ARROW_UP else ToastIcon.ARROW_DOWN,
                )
                onSaved()
            }
        }
    }
}

/** Owner equity tracker: invested capital, current equity, ROI, entry log. */
@Composable
fun InvestmentsScreen(
    navController: NavController,
    viewModel: InvestmentsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var addOpen by rememberSaveable { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        StatementHeaderBar(
            title = "Investments",
            subtitle = "د پانګې اچونه · capital injected · in ${state.reportingCurrency}",
            onBack = { navController.popBackStack() },
            trailing = {
                IconSquareButton(Icons.Rounded.Add, { addOpen = true }, onDark = true)
            },
        )

        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
            item {
                // Headline: invested vs equity + ROI
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(DaftarColors.Ink)
                        .padding(18.dp),
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            MonoLabel("Total invested", color = DaftarColors.GoldSoft, fontSize = 9)
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    Formatters.compact(state.totalInvestedRep, state.reportingDecimals),
                                    style = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 24.sp, color = DaftarColors.Paper),
                                )
                                Text(
                                    " " + state.reportingCurrency,
                                    style = TextStyle(fontFamily = JetBrainsMono, fontSize = 10.sp, color = DaftarColors.GoldSoft),
                                    modifier = Modifier.padding(bottom = 4.dp),
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            MonoLabel("Current equity", color = DaftarColors.GoldSoft, fontSize = 9)
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    Formatters.compact(state.currentEquityRep, state.reportingDecimals),
                                    style = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 24.sp, color = DaftarColors.Paper),
                                )
                                Text(
                                    " " + state.reportingCurrency,
                                    style = TextStyle(fontFamily = JetBrainsMono, fontSize = 10.sp, color = DaftarColors.GoldSoft),
                                    modifier = Modifier.padding(bottom = 4.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                when {
                                    state.roiAbsolute > 0.5 -> DaftarColors.Green.copy(alpha = 0.25f)
                                    state.roiAbsolute < -0.5 -> DaftarColors.Red.copy(alpha = 0.25f)
                                    else -> DaftarColors.Paper.copy(alpha = 0.08f)
                                },
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(
                                imageVector = if (state.roiAbsolute >= 0) Icons.AutoMirrored.Rounded.TrendingUp else Icons.AutoMirrored.Rounded.TrendingDown,
                                contentDescription = null,
                                tint = if (state.roiAbsolute >= 0) DaftarColors.LongGreen else DaftarColors.ShortRed,
                                modifier = Modifier.size(11.dp),
                            )
                            MonoLabel("Net return", color = DaftarColors.Paper.copy(alpha = 0.8f), fontSize = 9)
                        }
                        Text(
                            // v18 signs any non-zero return; near-zero renders flat.
                            text = Formatters.signPrefix(state.roiAbsolute) +
                                Formatters.compact(abs(state.roiAbsolute), state.reportingDecimals) +
                                " ${state.reportingCurrency} · " +
                                Formatters.signPrefix(state.roiAbsolute) +
                                Formatters.rate(abs(state.roiPercent), 1) + "%",
                            style = TextStyle(
                                fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 11.sp,
                                color = when {
                                    abs(state.roiAbsolute) <= 0.5 -> DaftarColors.GoldSoft
                                    state.roiAbsolute > 0 -> DaftarColors.LongGreen
                                    else -> DaftarColors.ShortRed
                                },
                            ),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Equity = current cash holdings (excludes receivables/payables)",
                        style = TextStyle(fontFamily = JetBrainsMono, fontSize = 9.sp, color = DaftarColors.MutedLight),
                    )
                }
                Spacer(Modifier.height(16.dp))
                MonoLabel("Capital by asset · د دارایو پانګه")
                Spacer(Modifier.height(8.dp))
            }

            items(count = state.perAsset.size, key = { i -> "inv_asset_" + state.perAsset[i].asset.code }) { i ->
                val totals = state.perAsset[i]
                val unit = if (totals.asset.type == AssetType.METAL) "g" else ""
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(DaftarColors.PaperSoft)
                        .border(1.dp, DaftarColors.Line, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (totals.asset.type == AssetType.METAL) DaftarColors.Gold else DaftarColors.Copper),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            totals.asset.code.take(4),
                            style = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 8.sp, color = DaftarColors.Paper),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            totals.asset.name,
                            style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = DaftarColors.Ink),
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Invested " + Formatters.compact(totals.invested) + unit +
                                (if (totals.withdrawn > 0) " · withdrew " + Formatters.compact(totals.withdrawn) + unit else "") +
                                " · ${totals.count} entries",
                            style = TextStyle(fontFamily = JetBrainsMono, fontSize = 9.sp, color = DaftarColors.Muted),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            Formatters.compact(totals.net, totals.asset.decimals) + unit,
                            style = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = DaftarColors.Ink),
                        )
                        MonoLabel("net invested", fontSize = 8, letterSpacing = 0.1)
                    }
                }
            }

            item {
                Spacer(Modifier.height(10.dp))
                MonoLabel("Activity log · ${state.entries.size} entries")
                Spacer(Modifier.height(8.dp))
            }

            if (state.entries.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.BusinessCenter,
                        title = "No investments yet",
                        pashto = "تر اوسه هیڅ پانګه نشته",
                        sub = "Record your opening capital and any top-ups or withdrawals to track your equity and ROI.",
                        tone = EmptyStateTone.COPPER,
                        ctaLabel = "Add entry · نوې لیکنه",
                        ctaIcon = Icons.Rounded.Add,
                        onCta = { addOpen = true },
                    )
                }
            } else {
                items(count = state.entries.size, key = { i -> state.entries[i].id }) { i ->
                    InvestmentEntryRow(state.entries[i])
                }
            }
        }
    }

    if (addOpen) {
        AddInvestmentSheet(
            activeAssets = state.activeAssets,
            onDismiss = { addOpen = false },
            onSave = { type, asset, amount, note ->
                viewModel.save(type, asset, amount, note) { addOpen = false }
            },
        )
    }
}

@Composable
private fun InvestmentEntryRow(entry: Investment) {
    val asset = AssetCatalog.byCode(entry.assetCode)
    val meta = when (entry.type) {
        InvestmentType.OPENING -> DaftarColors.Copper
        InvestmentType.ADDITION -> DaftarColors.Green
        InvestmentType.WITHDRAWAL -> DaftarColors.Red
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(DaftarColors.PaperSoft)
            .border(1.dp, DaftarColors.Line, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(meta.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (entry.type.isOutflow) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                contentDescription = null,
                tint = meta,
                modifier = Modifier.size(13.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.note ?: "${asset?.name ?: entry.assetCode} · ${entry.type.label.lowercase()}",
                style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = DaftarColors.Ink),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(meta.copy(alpha = 0.12f))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                ) {
                    Text(
                        entry.type.label.uppercase(),
                        style = TextStyle(
                            fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold,
                            fontSize = 7.sp, letterSpacing = 0.08.em, color = meta,
                        ),
                    )
                }
                Text(
                    entry.dateLabel,
                    style = TextStyle(fontFamily = JetBrainsMono, fontSize = 9.sp, color = DaftarColors.Muted),
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = (if (entry.type.isOutflow) "−" else "+") +
                    Formatters.compact(entry.amount, asset?.decimals ?: 0),
                style = TextStyle(
                    fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 15.sp,
                    color = if (entry.type.isOutflow) DaftarColors.Red else DaftarColors.Green,
                ),
            )
            MonoLabel(
                entry.assetCode + if (asset?.type == AssetType.METAL) "g" else "",
                fontSize = 8, letterSpacing = 0.1,
            )
        }
    }
}

/** "Record investment" sheet: type, asset, amount, note. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddInvestmentSheet(
    activeAssets: List<Asset>,
    onDismiss: () -> Unit,
    onSave: (InvestmentType, String, String, String) -> Unit,
) {
    var type by rememberSaveable { mutableStateOf(InvestmentType.ADDITION) }
    var assetCode by rememberSaveable { mutableStateOf("USD") }
    var amountText by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DaftarColors.Paper,
        dragHandle = { SheetHandle() },
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text("Record investment", style = MaterialTheme.typography.headlineMedium, color = DaftarColors.Ink)
            Text("د پانګې اچونه", style = MaterialTheme.typography.bodyMedium, color = DaftarColors.Muted)
            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InvestmentType.entries.forEach { option ->
                    val on = type == option
                    val color = when (option) {
                        InvestmentType.OPENING -> DaftarColors.Copper
                        InvestmentType.ADDITION -> DaftarColors.Green
                        InvestmentType.WITHDRAWAL -> DaftarColors.Red
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (on) color else DaftarColors.PaperSoft)
                            .border(1.5.dp, color, RoundedCornerShape(10.dp))
                            .clickable { type = option }
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            option.label.uppercase(),
                            style = TextStyle(
                                fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold,
                                fontSize = 10.sp, letterSpacing = 0.08.em,
                                color = if (on) DaftarColors.Paper else color,
                            ),
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            option.pashtoLabel,
                            style = TextStyle(
                                fontFamily = NotoNaskhArabic, fontSize = 10.sp,
                                color = if (on) DaftarColors.Paper.copy(alpha = 0.85f) else DaftarColors.Muted,
                                textDirection = TextDirection.Rtl,
                            ),
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            MonoLabel("Asset · دارایۍ", fontSize = 9)
            Spacer(Modifier.height(6.dp))
            activeAssets.chunked(4).forEach { rowAssets ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    rowAssets.forEach { asset ->
                        val on = assetCode == asset.code
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (on) DaftarColors.Ink else DaftarColors.PaperSoft)
                                .border(
                                    1.dp,
                                    if (on) DaftarColors.Ink else DaftarColors.LineStrong,
                                    RoundedCornerShape(10.dp),
                                )
                                .clickable { assetCode = asset.code }
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(asset.emoji, style = TextStyle(fontSize = 16.sp))
                            Spacer(Modifier.height(2.dp))
                            Text(
                                asset.code,
                                style = TextStyle(
                                    fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 9.sp,
                                    color = if (on) DaftarColors.GoldSoft else DaftarColors.InkSoft,
                                ),
                            )
                        }
                    }
                    repeat(4 - rowAssets.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            Spacer(Modifier.height(10.dp))
            MonoLabel("Amount · مقدار", fontSize = 9)
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DaftarColors.PaperSoft)
                    .border(1.dp, DaftarColors.LineStrong, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (amountText.isEmpty()) {
                        Text(
                            "0",
                            style = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 26.sp, color = DaftarColors.MutedLight),
                        )
                    }
                    BasicTextField(
                        value = amountText,
                        onValueChange = { amountText = sanitizeAmountInput(it) },
                        textStyle = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 26.sp, color = DaftarColors.Ink),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                MonoLabel(assetCode, fontSize = 12, letterSpacing = 0.1)
            }
            val asset = AssetCatalog.byCode(assetCode)
            if (asset?.type == AssetType.METAL) {
                val grams = amountText.toDoubleOrNull() ?: 0.0
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "amount in grams" +
                        if (grams > 0) " · ${Formatters.rate(AssetCatalog.gramsToTola(grams), 2)} tola" else "",
                    style = TextStyle(fontFamily = JetBrainsMono, fontSize = 10.sp, color = DaftarColors.Muted),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
            }

            Spacer(Modifier.height(14.dp))
            FieldBox("Note · یادداشت (optional)", modifier = Modifier.fillMaxWidth()) {
                FieldTextInput(note, { note = it }, "e.g. Q2 capital top-up · partner contribution")
            }

            Spacer(Modifier.height(18.dp))
            SubmitButton(
                label = "Save investment · خوندي کړه",
                icon = Icons.Rounded.Check,
                enabled = (amountText.toDoubleOrNull() ?: 0.0) > 0,
                onClick = { onSave(type, assetCode, amountText, note) },
            )
        }
    }
}

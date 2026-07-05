package com.daftar.app.ui.feature.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
import com.daftar.app.domain.repository.CashRepository
import com.daftar.app.domain.repository.RatesRepository
import com.daftar.app.domain.repository.SettingsRepository
import com.daftar.app.domain.usecase.CurrencyConverter
import com.daftar.app.domain.usecase.InitialSetupStatus
import com.daftar.app.domain.usecase.SaveInitialSetupUseCase
import com.daftar.app.ui.common.MonoLabel
import com.daftar.app.ui.common.SubmitButton
import com.daftar.app.ui.common.ToastCenter
import com.daftar.app.ui.common.ToastIcon
import com.daftar.app.ui.common.dashedBorder
import com.daftar.app.ui.common.sanitizeAmountInput
import com.daftar.app.ui.feature.statements.StatementHeaderBar
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

data class SetupRow(
    val asset: Asset,
    val enteredText: String,
    val existingBalance: Double,
    val isTradeCurrency: Boolean,
    val equivalentText: String?,
    val tolaText: String?,
)

data class InitialSetupUiState(
    val firstRun: Boolean = true,
    val rows: List<SetupRow> = emptyList(),
    val totalRepText: String = "",
    val reportingCurrency: String = "AFN",
    val anyEntered: Boolean = false,
    val tradeCurrency: String = "USD",
)

@HiltViewModel
class InitialSetupViewModel @Inject constructor(
    cashRepository: CashRepository,
    ratesRepository: RatesRepository,
    settingsRepository: SettingsRepository,
    initialSetupStatus: InitialSetupStatus,
    converter: CurrencyConverter,
    private val saveInitialSetup: SaveInitialSetupUseCase,
    private val toastCenter: ToastCenter,
) : ViewModel() {

    private val entered = MutableStateFlow<Map<String, String>>(emptyMap())

    val uiState = combine(
        cashRepository.drawer,
        ratesRepository.rateBook,
        settingsRepository.settings,
        initialSetupStatus.isNeeded(),
        entered,
    ) { drawer, rates, settings, firstRun, entered ->
        val trade = settings.tradeCurrency
        val reporting = settings.reportingCurrency
        val repDecimals = AssetCatalog.decimalsFor(reporting)

        // Trade currency first, then other currencies, then metals.
        val sortedAssets = settings.activeAssets().sortedWith(
            compareBy(
                { it.code != trade },
                { it.type != AssetType.CURRENCY },
                { AssetCatalog.ALL.indexOf(it) },
            ),
        )

        var total = 0.0
        var any = false
        val rows = sortedAssets.map { asset ->
            val text = entered[asset.code] ?: ""
            val value = text.toDoubleOrNull() ?: 0.0
            if (value > 0) {
                any = true
                total += converter.toReporting(asset.code, value, rates, settings)
            }
            SetupRow(
                asset = asset,
                enteredText = text,
                existingBalance = drawer.balanceOf(asset.code),
                isTradeCurrency = asset.code == trade,
                equivalentText = if (value > 0 && asset.code != reporting) {
                    "≈ " + Formatters.compact(converter.toReporting(asset.code, value, rates, settings), repDecimals) +
                        " " + reporting
                } else null,
                tolaText = if (asset.type == AssetType.METAL && value > 0) {
                    Formatters.number(AssetCatalog.gramsToTola(value), 2) + " tola"
                } else null,
            )
        }

        InitialSetupUiState(
            firstRun = firstRun,
            rows = rows,
            totalRepText = Formatters.compact(total, repDecimals),
            reportingCurrency = reporting,
            anyEntered = any,
            tradeCurrency = trade,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), InitialSetupUiState())

    fun setEntered(code: String, text: String) {
        entered.value = entered.value + (code to sanitizeAmountInput(text))
    }

    fun save(onSaved: () -> Unit) {
        viewModelScope.launch {
            val amounts = entered.value
                .mapNotNull { (code, text) -> text.toDoubleOrNull()?.takeIf { it > 0 }?.let { code to it } }
                .toMap()
            if (saveInitialSetup(amounts)) {
                toastCenter.show("Shop ready · start trading", ToastIcon.CHECK)
                onSaved()
            } else {
                toastCenter.show("Enter at least one amount", ToastIcon.CROSS)
            }
        }
    }
}

/** First-run / fiscal-reset flow: enter drawer holdings as opening capital. */
@Composable
fun InitialSetupScreen(
    navController: NavController,
    viewModel: InitialSetupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxWidth()) {
        StatementHeaderBar(
            title = if (state.firstRun) "Welcome to Daftar" else "Initial Setup",
            subtitle = if (state.firstRun) "Let's set up your shop · ښه راغلاست" else "Reset opening balances · پيل تنظيم",
            onBack = { navController.popBackStack() },
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            if (state.firstRun) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(DaftarColors.Ink)
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(DaftarColors.Paper),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "د",
                            style = TextStyle(fontFamily = NotoNaskhArabic, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, color = DaftarColors.Ink),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "First, count what you have",
                        style = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 18.sp, color = DaftarColors.Paper),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Enter the amount of cash and metals in your drawer right now. These become " +
                            "your opening balance and are recorded as your initial investment. " +
                            "You can start trading once you save.",
                        style = TextStyle(fontFamily = Inter, fontSize = 12.sp, color = DaftarColors.MutedLight),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Your trade currency is ${state.tradeCurrency}. Skip any asset you don't hold — leave it blank.",
                        style = TextStyle(fontFamily = JetBrainsMono, fontSize = 10.sp, color = DaftarColors.GoldSoft),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .dashedBorder(DaftarColors.Red.copy(alpha = 0.4f), 1.dp, 12.dp)
                        .background(DaftarColors.Red.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Rounded.Shield, null, tint = DaftarColors.Red, modifier = Modifier.size(16.dp))
                    Column {
                        Text(
                            "Reset opening balances",
                            style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = DaftarColors.Ink),
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "This replaces your current drawer amounts and adds opening entries to " +
                                "the investment log. Use this when starting a new fiscal period or if " +
                                "your initial setup was wrong.",
                            style = TextStyle(fontFamily = Inter, fontSize = 11.sp, color = DaftarColors.Muted),
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            state.rows.forEach { row -> SetupRowView(row, viewModel, !state.firstRun) }
        }

        // Sticky total + save
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DaftarColors.Paper)
                .padding(horizontal = 18.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (state.anyEntered) {
                    MonoLabel("Total starting capital", fontSize = 9)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            state.totalRepText,
                            style = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 18.sp, color = DaftarColors.Ink),
                        )
                        Text(
                            " " + state.reportingCurrency,
                            style = TextStyle(fontFamily = JetBrainsMono, fontSize = 10.sp, color = DaftarColors.Muted),
                            modifier = Modifier.padding(bottom = 2.dp),
                        )
                    }
                } else {
                    MonoLabel("Enter at least one starting amount", fontSize = 9, letterSpacing = 0.08)
                }
            }
            Box(modifier = Modifier.weight(1f)) {
                SubmitButton(
                    label = if (state.firstRun) "Start trading" else "Save balances",
                    icon = Icons.Rounded.Check,
                    enabled = state.anyEntered,
                    onClick = { viewModel.save { navController.popBackStack() } },
                )
            }
        }
    }
}

@Composable
private fun SetupRowView(row: SetupRow, viewModel: InitialSetupViewModel, showExisting: Boolean) {
    val isMetal = row.asset.type == AssetType.METAL
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(DaftarColors.PaperSoft)
            .border(
                1.5.dp,
                if (row.isTradeCurrency) DaftarColors.Copper else DaftarColors.LineStrong,
                RoundedCornerShape(13.dp),
            )
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isMetal) DaftarColors.Gold else DaftarColors.Copper),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                row.asset.code.take(4),
                style = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 9.sp, color = DaftarColors.Paper),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "${row.asset.name} · ${row.asset.pashtoName}",
                    style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = DaftarColors.Ink),
                )
                if (row.isTradeCurrency) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(DaftarColors.Copper)
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    ) {
                        Text(
                            "YOUR TRADE CURRENCY",
                            style = TextStyle(
                                fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold,
                                fontSize = 7.sp, letterSpacing = 0.08.em, color = DaftarColors.Paper,
                            ),
                        )
                    }
                }
            }
            if (showExisting && row.existingBalance > 0) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "Currently: " + Formatters.number(row.existingBalance, row.asset.decimals) +
                        " " + (if (isMetal) "g" else row.asset.code),
                    style = TextStyle(fontFamily = JetBrainsMono, fontSize = 10.sp, color = DaftarColors.Muted),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(9.dp))
                    .background(DaftarColors.Paper)
                    .border(1.dp, DaftarColors.LineStrong, RoundedCornerShape(9.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (row.enteredText.isEmpty()) {
                        Text(
                            "0",
                            style = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 17.sp, color = DaftarColors.MutedLight),
                        )
                    }
                    BasicTextField(
                        value = row.enteredText,
                        onValueChange = { viewModel.setEntered(row.asset.code, it) },
                        textStyle = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 17.sp, color = DaftarColors.Ink),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                MonoLabel(if (isMetal) "grams" else row.asset.symbol, fontSize = 10, letterSpacing = 0.05)
            }
            if (row.tolaText != null || row.equivalentText != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = listOfNotNull(row.tolaText, row.equivalentText).joinToString(" · "),
                    style = TextStyle(fontFamily = JetBrainsMono, fontSize = 10.sp, color = DaftarColors.Muted),
                )
            }
        }
    }
}

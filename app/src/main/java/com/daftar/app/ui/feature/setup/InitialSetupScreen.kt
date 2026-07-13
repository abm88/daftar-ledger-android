package com.daftar.app.ui.feature.setup

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.daftar.app.domain.repository.RatesRepository
import com.daftar.app.domain.repository.SettingsRepository
import com.daftar.app.domain.usecase.CurrencyConverter
import com.daftar.app.domain.usecase.InitialSetupStatus
import com.daftar.app.domain.usecase.SaveInitialSetupUseCase
import com.daftar.app.ui.common.MonoLabel
import com.daftar.app.ui.common.ToastCenter
import com.daftar.app.ui.common.ToastIcon
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
    val isTradeCurrency: Boolean,
    val equivalentText: String?,
    val tolaText: String?,
)

data class InitialSetupUiState(
    val step: Int = 1,
    val firstRun: Boolean = true,
    /** Wizard buffer: per-asset enablement (step 1). */
    val enabled: Map<String, Boolean> = emptyMap(),
    /** Currencies enabled in the buffer — the step-2 option list. */
    val enabledCurrencies: List<Asset> = emptyList(),
    val reportingCurrency: String = "AFN",
    val tradeCurrency: String = "USD",
    /** Step 3 rows over the wizard-enabled assets. */
    val rows: List<SetupRow> = emptyList(),
    val totalRepText: String = "",
    val anyEntered: Boolean = false,
)

/**
 * v18 turned Initial Setup into a 3-step wizard:
 *  1. toggle the assets the daftar deals in (AFN locked as base),
 *  2. pick the reporting + trade currency from the enabled set,
 *  3. enter opening amounts.
 * Saving commits all three (active assets, defaults, drawer + opening
 * investments) in one go.
 */
@HiltViewModel
class InitialSetupViewModel @Inject constructor(
    ratesRepository: RatesRepository,
    settingsRepository: SettingsRepository,
    initialSetupStatus: InitialSetupStatus,
    converter: CurrencyConverter,
    private val saveInitialSetup: SaveInitialSetupUseCase,
    private val toastCenter: ToastCenter,
) : ViewModel() {

    private val step = MutableStateFlow(1)
    private val enabled = MutableStateFlow(
        // Pre-check the currently active assets (v18 open-initial-setup).
        AssetCatalog.ALL.associate { asset ->
            asset.code to settingsRepository.settings.value.isAssetActive(asset)
        },
    )
    private val reporting = MutableStateFlow(settingsRepository.settings.value.reportingCurrency)
    private val trade = MutableStateFlow(settingsRepository.settings.value.tradeCurrency)
    private val entered = MutableStateFlow<Map<String, String>>(emptyMap())

    val uiState = combine(
        combine(step, enabled, reporting, trade) { s, e, r, t -> WizardBuffer(s, e, r, t) },
        entered,
        ratesRepository.rateBook,
        settingsRepository.settings,
        initialSetupStatus.isNeeded(),
    ) { buffer, entered, rates, settings, firstRun ->
        val enabledAssets = AssetCatalog.ALL.filter { buffer.enabled[it.code] == true }
        val enabledCurrencies = enabledAssets.filter { it.type == AssetType.CURRENCY }
        val repDecimals = AssetCatalog.decimalsFor(buffer.reporting)
        // Equivalents convert through the wizard's chosen reporting currency,
        // not the already-saved settings (v18).
        val wizardSettings = settings.copy(reportingCurrency = buffer.reporting)

        // Trade currency first, then currencies, then metals in catalog order.
        val sortedAssets = enabledAssets.sortedWith(
            compareBy(
                { it.code != buffer.trade },
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
                total += converter.toReporting(asset.code, value, rates, wizardSettings)
            }
            SetupRow(
                asset = asset,
                enteredText = text,
                isTradeCurrency = asset.code == buffer.trade,
                equivalentText = if (value > 0 && asset.code != buffer.reporting) {
                    "≈ " + Formatters.compact(
                        converter.toReporting(asset.code, value, rates, wizardSettings),
                        repDecimals,
                    ) + " " + buffer.reporting
                } else null,
                tolaText = if (asset.type == AssetType.METAL && value > 0) {
                    Formatters.number(AssetCatalog.gramsToTola(value), 2) + " tola"
                } else null,
            )
        }

        InitialSetupUiState(
            step = buffer.step,
            firstRun = firstRun,
            enabled = buffer.enabled,
            enabledCurrencies = enabledCurrencies,
            reportingCurrency = buffer.reporting,
            tradeCurrency = buffer.trade,
            rows = rows,
            totalRepText = Formatters.compact(total, repDecimals),
            anyEntered = any,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), InitialSetupUiState())

    private data class WizardBuffer(
        val step: Int,
        val enabled: Map<String, Boolean>,
        val reporting: String,
        val trade: String,
    )

    fun toggleAsset(code: String) {
        if (code == "AFN") {
            // v18: the base currency can never be turned off.
            toastCenter.show("AFN is required as the base currency", ToastIcon.SHIELD)
            return
        }
        enabled.value = enabled.value + (code to !(enabled.value[code] ?: false))
    }

    fun setReporting(code: String) { reporting.value = code }
    fun setTrade(code: String) { trade.value = code }
    fun setEntered(code: String, text: String) {
        entered.value = entered.value + (code to sanitizeAmountInput(text))
    }

    /** Continue button: step-1 validation + coercion, then advance. */
    fun next() {
        if (step.value == 1) {
            val enabledCurrencies = AssetCatalog.ALL
                .filter { it.type == AssetType.CURRENCY && enabled.value[it.code] == true }
            if (enabledCurrencies.isEmpty()) {
                toastCenter.show("Enable at least one currency", ToastIcon.CROSS)
                return
            }
            // v18 repairs the defaults if their currency was just disabled.
            if (enabled.value[reporting.value] != true) reporting.value = enabledCurrencies.first().code
            if (enabled.value[trade.value] != true) trade.value = enabledCurrencies.first().code
            step.value = 2
        } else if (step.value == 2) {
            step.value = 3
        }
    }

    /** Back arrow: steps 2–3 go back a step; step 1 exits (return true = handled). */
    fun back(): Boolean {
        if (step.value > 1) {
            step.value -= 1
            return true
        }
        return false
    }

    fun save(onSaved: () -> Unit) {
        viewModelScope.launch {
            val amounts = entered.value
                .filterKeys { enabled.value[it] == true } // only wizard-enabled assets count
                .mapNotNull { (code, text) -> text.toDoubleOrNull()?.takeIf { it > 0 }?.let { code to it } }
                .toMap()
            val saved = saveInitialSetup(
                startingAmounts = amounts,
                enabledAssets = enabled.value,
                reportingCurrency = reporting.value,
                tradeCurrency = trade.value,
            )
            if (saved) {
                toastCenter.show("Daftar ready · start trading", ToastIcon.CHECK)
                onSaved()
            } else {
                toastCenter.show("Enter at least one amount", ToastIcon.CROSS)
            }
        }
    }
}

/** 3-step initial setup wizard (v18): Assets → Currency → Amounts. */
@Composable
fun InitialSetupScreen(
    navController: NavController,
    viewModel: InitialSetupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxWidth()) {
        StatementHeaderBar(
            title = if (state.firstRun) "Welcome to Daftar" else "Initial Setup",
            subtitle = when (state.step) {
                1 -> "Step 1 · Choose your assets"
                2 -> "Step 2 · Default currency"
                else -> "Step 3 · Opening amounts"
            },
            onBack = { if (!viewModel.back()) navController.popBackStack() },
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            StepDots(state.step)
            Spacer(Modifier.height(16.dp))

            when (state.step) {
                1 -> StepAssets(state, viewModel)
                2 -> StepCurrency(state, viewModel)
                else -> StepAmounts(state, viewModel)
            }
        }

        // Footer: Continue on steps 1–2, summary + save on step 3.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DaftarColors.Paper)
                .padding(horizontal = 18.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.step < 3) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(DaftarColors.Ink)
                        .clickable(onClick = viewModel::next)
                        .padding(vertical = 15.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "CONTINUE · دوام ورکړه",
                        style = TextStyle(
                            fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold,
                            fontSize = 12.sp, letterSpacing = 0.08.em, color = DaftarColors.Paper,
                        ),
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowForward, null,
                        tint = DaftarColors.Paper, modifier = Modifier.size(14.dp),
                    )
                }
            } else {
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
                        MonoLabel("Enter at least one amount", fontSize = 9, letterSpacing = 0.08)
                    }
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (state.anyEntered) DaftarColors.Ink else DaftarColors.Ink.copy(alpha = 0.45f))
                        .clickable(enabled = state.anyEntered) { viewModel.save { navController.popBackStack() } }
                        .padding(vertical = 15.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Check, null, tint = DaftarColors.Paper, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (state.firstRun) "START TRADING · پیل وکړه" else "SAVE · خوندي کړه",
                        style = TextStyle(
                            fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold,
                            fontSize = 11.sp, letterSpacing = 0.06.em, color = DaftarColors.Paper,
                        ),
                    )
                }
            }
        }
    }
}

/** "Assets — Currency — Amounts" progress dots with connector lines. */
@Composable
private fun StepDots(step: Int) {
    val labels = listOf("Assets", "Currency", "Amounts")
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { index, label ->
            val n = index + 1
            val done = n < step
            val on = n == step
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                done -> DaftarColors.Green
                                on -> DaftarColors.Copper
                                else -> DaftarColors.PaperDeep
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (done) {
                        Icon(Icons.Rounded.Check, null, tint = DaftarColors.Paper, modifier = Modifier.size(11.dp))
                    } else {
                        Text(
                            n.toString(),
                            style = TextStyle(
                                fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 11.sp,
                                color = if (on) DaftarColors.Paper else DaftarColors.Muted,
                            ),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                MonoLabel(label, fontSize = 8, letterSpacing = 0.1, color = if (on) DaftarColors.Ink else DaftarColors.Muted)
            }
            if (index < labels.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    color = DaftarColors.LineStrong,
                )
            }
        }
    }
}

@Composable
private fun StepAssets(state: InitialSetupUiState, viewModel: InitialSetupViewModel) {
    Text(
        text = if (state.firstRun) {
            "Which currencies and metals do you deal in? Turn on the ones you hold. " +
                "You can change these later in Daftar settings."
        } else "Adjust which assets are active. AFN stays on as the base currency.",
        style = TextStyle(fontFamily = Inter, fontSize = 12.5.sp, lineHeight = 19.sp, color = DaftarColors.Muted),
    )
    Spacer(Modifier.height(14.dp))
    MonoLabel("Currencies · اسعار", fontSize = 9)
    Spacer(Modifier.height(8.dp))
    AssetCatalog.ALL.filter { it.type == AssetType.CURRENCY }.forEach { asset ->
        SetupAssetToggle(asset, state.enabled[asset.code] == true) { viewModel.toggleAsset(asset.code) }
    }
    Spacer(Modifier.height(16.dp))
    MonoLabel("Precious metals · قیمتي فلزات", fontSize = 9)
    Spacer(Modifier.height(8.dp))
    AssetCatalog.ALL.filter { it.type == AssetType.METAL }.forEach { asset ->
        SetupAssetToggle(asset, state.enabled[asset.code] == true) { viewModel.toggleAsset(asset.code) }
    }
}

@Composable
private fun SetupAssetToggle(asset: Asset, on: Boolean, onToggle: () -> Unit) {
    val locked = asset.code == "AFN"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (on) DaftarColors.PaperSoft else DaftarColors.PaperDeep.copy(alpha = 0.5f))
            .border(
                1.5.dp,
                if (on) DaftarColors.Copper.copy(alpha = 0.5f) else DaftarColors.Line,
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(asset.emoji, style = TextStyle(fontSize = 20.sp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    asset.name,
                    style = TextStyle(
                        fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                        color = if (on) DaftarColors.Ink else DaftarColors.Muted,
                    ),
                )
                if (locked) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(DaftarColors.Copper.copy(alpha = 0.12f))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    ) {
                        Text(
                            "REQUIRED",
                            style = TextStyle(
                                fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold,
                                fontSize = 7.sp, letterSpacing = 0.1.em, color = DaftarColors.CopperDeep,
                            ),
                        )
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "${asset.code} · ${asset.pashtoName}",
                style = TextStyle(fontFamily = JetBrainsMono, fontSize = 10.sp, color = DaftarColors.Muted),
            )
        }
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(if (on) DaftarColors.Green else DaftarColors.Paper)
                .border(
                    1.5.dp,
                    if (on) DaftarColors.Green else DaftarColors.LineStrong,
                    RoundedCornerShape(7.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (on) {
                Icon(Icons.Rounded.Check, null, tint = DaftarColors.Paper, modifier = Modifier.size(13.dp))
            }
        }
    }
}

@Composable
private fun StepCurrency(state: InitialSetupUiState, viewModel: InitialSetupViewModel) {
    CurrencySection(
        icon = Icons.Rounded.BarChart,
        iconTint = DaftarColors.Copper,
        title = "Reporting Currency",
        description = "Your totals, P&L, and drawer value are shown in this currency.",
        options = state.enabledCurrencies,
        selected = state.reportingCurrency,
        onSelect = viewModel::setReporting,
    )
    Spacer(Modifier.height(18.dp))
    CurrencySection(
        icon = Icons.Rounded.Refresh,
        iconTint = DaftarColors.Green,
        title = "Trade Currency",
        description = "Pre-selected when you start a new trade, hawala, or customer entry.",
        options = state.enabledCurrencies,
        selected = state.tradeCurrency,
        onSelect = viewModel::setTrade,
    )
}

@Composable
private fun CurrencySection(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    description: String,
    options: List<Asset>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(iconTint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(14.dp))
        }
        Text(
            title,
            style = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = DaftarColors.Ink),
        )
    }
    Spacer(Modifier.height(4.dp))
    Text(
        description,
        style = TextStyle(fontFamily = Inter, fontSize = 11.5.sp, color = DaftarColors.Muted),
    )
    Spacer(Modifier.height(10.dp))
    options.forEach { asset ->
        val on = asset.code == selected
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (on) DaftarColors.Copper.copy(alpha = 0.08f) else DaftarColors.PaperSoft)
                .border(
                    1.5.dp,
                    if (on) DaftarColors.Copper else DaftarColors.LineStrong,
                    RoundedCornerShape(12.dp),
                )
                .clickable { onSelect(asset.code) }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(asset.emoji, style = TextStyle(fontSize = 18.sp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    asset.code,
                    style = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = DaftarColors.Ink),
                )
                Text(
                    asset.name,
                    style = TextStyle(fontFamily = Inter, fontSize = 11.sp, color = DaftarColors.Muted),
                )
            }
            if (on) {
                Icon(Icons.Rounded.Check, null, tint = DaftarColors.Copper, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun StepAmounts(state: InitialSetupUiState, viewModel: InitialSetupViewModel) {
    Text(
        "Enter what you have in your drawer right now. Leave blank any asset you don't hold yet.",
        style = TextStyle(fontFamily = Inter, fontSize = 12.5.sp, lineHeight = 19.sp, color = DaftarColors.Muted),
    )
    Spacer(Modifier.height(14.dp))
    state.rows.forEach { row -> SetupRowView(row, viewModel) }
}

@Composable
private fun SetupRowView(row: SetupRow, viewModel: InitialSetupViewModel) {
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
                            "TRADE", // v18 badge text
                            style = TextStyle(
                                fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold,
                                fontSize = 7.sp, letterSpacing = 0.08.em, color = DaftarColors.Paper,
                            ),
                        )
                    }
                }
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

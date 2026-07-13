package com.daftar.app.ui.feature.cashcount

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
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Check
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
import com.daftar.app.domain.model.AssetType
import com.daftar.app.domain.repository.CashRepository
import com.daftar.app.domain.repository.RatesRepository
import com.daftar.app.domain.repository.SettingsRepository
import com.daftar.app.domain.usecase.SaveCashCountUseCase
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
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CashCountRow(
    val asset: Asset,
    val recorded: Double,
    val enteredText: String,
) {
    val entered: Double? get() = enteredText.toDoubleOrNull()
    val variance: Double? get() = entered?.let { it - recorded }
}

data class CashCountUiState(
    val rows: List<CashCountRow> = emptyList(),
    val netVarianceAfn: Double = 0.0,
    val anyEntered: Boolean = false,
)

@HiltViewModel
class CashCountViewModel @Inject constructor(
    cashRepository: CashRepository,
    ratesRepository: RatesRepository,
    settingsRepository: SettingsRepository,
    private val saveCashCount: SaveCashCountUseCase,
    private val toastCenter: ToastCenter,
) : ViewModel() {

    private val entered = MutableStateFlow<Map<String, String>>(emptyMap())

    val uiState = combine(
        cashRepository.drawer,
        ratesRepository.rateBook,
        settingsRepository.settings,
        entered,
    ) { drawer, rates, settings, entered ->
        val rows = settings.activeAssets().map { asset ->
            CashCountRow(
                asset = asset,
                recorded = drawer.balanceOf(asset.code),
                enteredText = entered[asset.code] ?: "",
            )
        }
        val netVariance = rows.sumOf { row ->
            val diff = row.variance ?: return@sumOf 0.0
            if (row.asset.code == "AFN") diff else diff * rates.sellRateToAfn(row.asset.code)
        }
        CashCountUiState(
            rows = rows,
            netVarianceAfn = netVariance,
            anyEntered = rows.any { row ->
                val variance = row.variance
                variance != null && kotlin.math.abs(variance) > 0.001
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CashCountUiState())

    fun setEntered(code: String, text: String) {
        entered.value = entered.value + (code to sanitizeAmountInput(text))
    }

    fun save(onSaved: () -> Unit) {
        viewModelScope.launch {
            val counted = entered.value
                .mapNotNull { (code, text) -> text.toDoubleOrNull()?.let { code to it } }
                .toMap()
            if (saveCashCount(counted)) {
                toastCenter.show("Cash counter updated", ToastIcon.CHECK)
                onSaved()
            } else {
                toastCenter.show("Enter at least one count to save", ToastIcon.CROSS)
            }
        }
    }
}

/** Physical drawer count with live variance against recorded balances. */
@Composable
fun CashCountScreen(
    navController: NavController,
    viewModel: CashCountViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxWidth()) {
        StatementHeaderBar(
            title = "Count Cash",
            subtitle = "د صندوق شمېرنه · ${state.rows.size} assets",
            onBack = { navController.popBackStack() },
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .dashedBorder(DaftarColors.Copper.copy(alpha = 0.3f), 1.dp, 12.dp)
                    .background(DaftarColors.Copper.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                MonoLabel("Manual count", color = DaftarColors.CopperDeep, fontSize = 9)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Physically count your drawer and enter actual amounts. Variances are " +
                        "highlighted so you can spot discrepancies. Saving overwrites the recorded balance.",
                    style = TextStyle(fontFamily = Inter, fontSize = 11.sp, color = DaftarColors.CopperDeep),
                )
            }
            Spacer(Modifier.height(14.dp))
            state.rows.forEach { row -> CountRow(row, viewModel) }
        }

        // Sticky summary + save
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
                    MonoLabel("Net variance", fontSize = 9)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = (if (state.netVarianceAfn >= 0) "+" else "−") +
                                Formatters.compact(abs(state.netVarianceAfn)),
                            style = TextStyle(
                                fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 18.sp,
                                color = if (state.netVarianceAfn >= 0) DaftarColors.Green else DaftarColors.Red,
                            ),
                        )
                        Text(
                            " AFN",
                            style = TextStyle(fontFamily = JetBrainsMono, fontSize = 10.sp, color = DaftarColors.Muted),
                            modifier = Modifier.padding(bottom = 2.dp),
                        )
                    }
                } else {
                    MonoLabel("Enter counted amounts above", fontSize = 9, letterSpacing = 0.1)
                }
            }
            Box(modifier = Modifier.weight(1f)) {
                SubmitButton(
                    label = "Save count · خوندي کړه",
                    icon = Icons.Rounded.Check,
                    enabled = state.anyEntered,
                    onClick = { viewModel.save { navController.popBackStack() } },
                )
            }
        }
    }
}

@Composable
private fun CountRow(row: CashCountRow, viewModel: CashCountViewModel) {
    val isMetal = row.asset.type == AssetType.METAL
    val recordedDisplay = Formatters.number(row.recorded, row.asset.decimals) + if (isMetal) " g" else " ${row.asset.code}"
    // v18 placeholder omits the currency code ("12,450", metals keep the g).
    val placeholderDisplay = Formatters.number(row.recorded, row.asset.decimals) + if (isMetal) " g" else ""
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(DaftarColors.PaperSoft)
            .border(1.dp, DaftarColors.LineStrong, RoundedCornerShape(13.dp))
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
            Text(
                "${row.asset.name} · ${row.asset.pashtoName}",
                style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = DaftarColors.Ink),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Recorded: $recordedDisplay",
                style = TextStyle(fontFamily = JetBrainsMono, fontSize = 10.sp, color = DaftarColors.Muted),
            )
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
                            placeholderDisplay,
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
            val variance = row.variance
            if (variance != null && row.enteredText.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                if (abs(variance) > 0.001) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(
                            imageVector = if (variance > 0) Icons.AutoMirrored.Rounded.TrendingUp else Icons.AutoMirrored.Rounded.TrendingDown,
                            contentDescription = null,
                            tint = if (variance > 0) DaftarColors.Green else DaftarColors.Red,
                            modifier = Modifier.size(10.dp),
                        )
                        Text(
                            text = (if (variance > 0) "+" else "−") +
                                Formatters.number(abs(variance), row.asset.decimals) +
                                " ${if (isMetal) "g" else row.asset.code} " +
                                (if (variance > 0) "overage" else "short"),
                            style = TextStyle(
                                fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 10.sp,
                                color = if (variance > 0) DaftarColors.Green else DaftarColors.Red,
                            ),
                        )
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Rounded.Check, null, tint = DaftarColors.Green, modifier = Modifier.size(10.dp))
                        Text(
                            "Matches recorded",
                            style = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = DaftarColors.Green),
                        )
                    }
                }
            }
        }
    }
}

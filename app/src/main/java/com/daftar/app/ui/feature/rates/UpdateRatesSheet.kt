package com.daftar.app.ui.feature.rates

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.Alignment
import com.daftar.app.core.format.Formatters
import com.daftar.app.domain.model.Asset
import com.daftar.app.domain.model.AssetType
import com.daftar.app.domain.repository.RatesRepository
import com.daftar.app.domain.repository.SettingsRepository
import com.daftar.app.domain.usecase.UpdateRatesUseCase
import com.daftar.app.ui.common.MonoLabel
import com.daftar.app.ui.common.SheetHandle
import com.daftar.app.ui.common.SubmitButton
import com.daftar.app.ui.common.ToastCenter
import com.daftar.app.ui.common.ToastIcon
import com.daftar.app.ui.common.dashedBorder
import com.daftar.app.ui.theme.DaftarColors
import com.daftar.app.ui.theme.Fraunces
import com.daftar.app.ui.theme.Inter
import com.daftar.app.ui.theme.JetBrainsMono
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RateEditRow(
    val asset: Asset,
    val buyText: String,
    val sellText: String,
)

@HiltViewModel
class UpdateRatesViewModel @Inject constructor(
    ratesRepository: RatesRepository,
    settingsRepository: SettingsRepository,
    private val updateRates: UpdateRatesUseCase,
    private val toastCenter: ToastCenter,
) : ViewModel() {

    private val edits = MutableStateFlow<Map<String, Pair<String, String>>>(
        run {
            val book = ratesRepository.rateBook.value
            settingsRepository.settings.value.activeAssets()
                .filter { it.code != "AFN" }
                .associate { asset ->
                    val rate = book.assetRate(asset.code)
                    val decimals = if ((rate?.sell ?: 1.0) < 1) 3 else 2
                    asset.code to (
                        Formatters.rate(rate?.buy ?: 0.0, decimals) to
                            Formatters.rate(rate?.sell ?: 0.0, decimals)
                        )
                }
        },
    )

    val rows = combine(
        settingsRepository.settings,
        edits,
    ) { settings, edits ->
        settings.activeAssets()
            .filter { it.code != "AFN" }
            .map { asset ->
                val (buy, sell) = edits[asset.code] ?: ("" to "")
                RateEditRow(asset, buy, sell)
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setBuy(code: String, text: String) {
        val current = edits.value[code] ?: ("" to "")
        edits.value = edits.value + (code to (filterRate(text) to current.second))
    }

    fun setSell(code: String, text: String) {
        val current = edits.value[code] ?: ("" to "")
        edits.value = edits.value + (code to (current.first to filterRate(text)))
    }

    private fun filterRate(text: String) = text.filter { it.isDigit() || it == '.' }

    fun save(onSaved: () -> Unit) {
        viewModelScope.launch {
            val quotes = edits.value.mapNotNull { (code, texts) ->
                val buy = texts.first.toDoubleOrNull() ?: return@mapNotNull null
                val sell = texts.second.toDoubleOrNull() ?: return@mapNotNull null
                if (buy <= 0 || sell <= 0) return@mapNotNull null
                code to (buy to sell)
            }.toMap()
            updateRates(quotes)
            toastCenter.show("Rates updated", ToastIcon.SCALE)
            onSaved()
        }
    }
}

/** "Update rates" sheet: buy/sell per active asset against AFN. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateRatesSheet(
    onDismiss: () -> Unit,
    viewModel: UpdateRatesViewModel = hiltViewModel(),
) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()

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
            Text("Update rates", style = MaterialTheme.typography.headlineMedium, color = DaftarColors.Ink)
            Text(
                "د اسعارو نرخ سمول · base: AFN",
                style = MaterialTheme.typography.bodyMedium,
                color = DaftarColors.Muted,
            )
            Spacer(Modifier.height(14.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .dashedBorder(DaftarColors.Copper.copy(alpha = 0.3f), 1.dp, 12.dp)
                    .background(DaftarColors.Copper.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                MonoLabel("Today's rates", color = DaftarColors.CopperDeep, fontSize = 9)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Update buy/sell rates per asset against AFN. Metals are quoted per gram. " +
                        "To count cash, go to Shop · Count Cash.",
                    style = TextStyle(fontFamily = Inter, fontSize = 11.sp, color = DaftarColors.CopperDeep),
                )
            }

            Spacer(Modifier.height(14.dp))
            rows.forEach { row ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(DaftarColors.PaperSoft)
                        .border(1.dp, DaftarColors.LineStrong, RoundedCornerShape(13.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = row.asset.code + " / AFN" +
                                if (row.asset.type == AssetType.METAL) " (per gram)" else "",
                            style = TextStyle(
                                fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold,
                                fontSize = 11.sp, color = DaftarColors.Ink,
                            ),
                        )
                        Text(
                            text = "1 ${if (row.asset.type == AssetType.METAL) "gram of ${row.asset.code}" else row.asset.code} = ? AFN",
                            style = TextStyle(fontFamily = JetBrainsMono, fontSize = 9.sp, color = DaftarColors.Muted),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RateInputBox("Buy", row.buyText, DaftarColors.Green, Modifier.weight(1f)) {
                            viewModel.setBuy(row.asset.code, it)
                        }
                        RateInputBox("Sell", row.sellText, DaftarColors.Red, Modifier.weight(1f)) {
                            viewModel.setSell(row.asset.code, it)
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            SubmitButton(
                label = "Save rates · نرخ خوندي کړه",
                icon = Icons.Rounded.Check,
                onClick = { viewModel.save(onDismiss) },
            )
        }
    }
}

@Composable
private fun RateInputBox(
    label: String,
    value: String,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier,
    onChange: (String) -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(DaftarColors.Paper)
            .border(1.dp, DaftarColors.Line, RoundedCornerShape(9.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        MonoLabel(label, color = accent, fontSize = 9, letterSpacing = 0.15)
        Spacer(Modifier.height(2.dp))
        androidx.compose.foundation.layout.Box {
            if (value.isEmpty()) {
                Text(
                    "0",
                    style = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 20.sp, color = DaftarColors.MutedLight),
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                textStyle = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 20.sp, color = accent),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

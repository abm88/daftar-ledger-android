package com.daftar.app.ui.feature.defaults

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.daftar.app.domain.model.Asset
import com.daftar.app.domain.model.LedgerSettings
import com.daftar.app.domain.repository.SettingsRepository
import com.daftar.app.domain.repository.LedgerMutationRepository
import com.daftar.app.ui.common.MonoLabel
import com.daftar.app.ui.common.ToastCenter
import com.daftar.app.ui.common.ToastIcon
import com.daftar.app.ui.feature.statements.StatementHeaderBar
import com.daftar.app.ui.theme.DaftarColors
import com.daftar.app.ui.theme.Inter
import com.daftar.app.ui.theme.JetBrainsMono
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class DefaultsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val mutations: LedgerMutationRepository,
    private val toastCenter: ToastCenter,
) : ViewModel() {

    val settings = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LedgerSettings())

    fun setReporting(code: String) {
        viewModelScope.launch {
            val result = runCatching { mutations.updateSettings(reportingCurrency = code) }
            toastCenter.show(
                if (result.isSuccess) "Reporting currency: $code" else result.exceptionOrNull()?.message ?: "Unable to update settings",
                if (result.isSuccess) ToastIcon.CHECK else ToastIcon.CROSS,
            )
        }
    }

    fun setTrade(code: String) {
        viewModelScope.launch {
            val result = runCatching { mutations.updateSettings(tradeCurrency = code) }
            toastCenter.show(
                if (result.isSuccess) "Trade default: $code" else result.exceptionOrNull()?.message ?: "Unable to update settings",
                if (result.isSuccess) ToastIcon.CHECK else ToastIcon.CROSS,
            )
        }
    }
}

/** Default currency preferences: reporting currency and trade currency. */
@Composable
fun DefaultsScreen(
    navController: NavController,
    viewModel: DefaultsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val currencies = settings.activeCurrencies()

    Column(modifier = Modifier.fillMaxWidth()) {
        StatementHeaderBar(
            title = "Default Currency",
            subtitle = "د اسعارو پیش‌فرض",
            onBack = { navController.popBackStack() },
        )
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            DefaultsSection(
                icon = Icons.Rounded.BarChart,
                tint = DaftarColors.Copper,
                title = "Reporting Currency",
                description = "Used for drawer total, P&L, ROI, and all \"≈\" equivalent displays. " +
                    "Switching does not change underlying balances — only how totals are presented.",
                options = currencies,
                selected = settings.reportingCurrency,
                onSelect = viewModel::setReporting,
            )
            Spacer(Modifier.height(20.dp))
            DefaultsSection(
                icon = Icons.Rounded.Refresh,
                tint = DaftarColors.Green,
                title = "Trade Currency",
                description = "Pre-selected when you start a new FX trade, hawala, or customer " +
                    "transaction. Only currencies are eligible (not metals).",
                options = currencies,
                selected = settings.tradeCurrency,
                onSelect = viewModel::setTrade,
            )
        }
    }
}

@Composable
private fun DefaultsSection(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    title: String,
    description: String,
    options: List<Asset>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
            }
            Text(
                title,
                style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = DaftarColors.Ink),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            description,
            style = TextStyle(fontFamily = Inter, fontSize = 11.sp, color = DaftarColors.Muted),
        )
        Spacer(Modifier.height(10.dp))
        options.forEach { asset ->
            val on = asset.code == selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) tint.copy(alpha = 0.08f) else DaftarColors.PaperSoft)
                    .border(
                        1.5.dp,
                        if (on) tint else DaftarColors.LineStrong,
                        RoundedCornerShape(12.dp),
                    )
                    .clickable { onSelect(asset.code) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(asset.emoji, style = TextStyle(fontSize = 20.sp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        asset.code,
                        style = TextStyle(
                            fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold,
                            fontSize = 13.sp, color = DaftarColors.Ink,
                        ),
                    )
                    Text(
                        asset.name,
                        style = TextStyle(fontFamily = Inter, fontSize = 11.sp, color = DaftarColors.Muted),
                    )
                }
                if (on) {
                    Icon(Icons.Rounded.Check, null, tint = tint, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

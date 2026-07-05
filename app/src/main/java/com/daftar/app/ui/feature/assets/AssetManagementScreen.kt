package com.daftar.app.ui.feature.assets

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.daftar.app.domain.model.Asset
import com.daftar.app.domain.model.AssetCatalog
import com.daftar.app.domain.model.AssetType
import com.daftar.app.domain.repository.SettingsRepository
import com.daftar.app.domain.model.LedgerSettings
import com.daftar.app.ui.common.MonoLabel
import com.daftar.app.ui.common.ToastCenter
import com.daftar.app.ui.common.ToastIcon
import com.daftar.app.ui.common.dashedBorder
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
class AssetManagementViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val toastCenter: ToastCenter,
) : ViewModel() {

    val settings = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LedgerSettings())

    fun toggle(asset: Asset) {
        if (asset.isDefault) return
        viewModelScope.launch {
            val wasActive = settings.value.isAssetActive(asset)
            settingsRepository.setAssetActive(asset.code, !wasActive)
            toastCenter.show(
                if (wasActive) "${asset.code} deactivated" else "${asset.code} activated · ${asset.name}",
                if (wasActive) ToastIcon.CROSS else ToastIcon.CHECK,
            )
        }
    }
}

/** Toggle which currencies and metals the shop tracks; defaults are locked on. */
@Composable
fun AssetManagementScreen(
    navController: NavController,
    viewModel: AssetManagementViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxWidth()) {
        StatementHeaderBar(
            title = "Assets",
            subtitle = "د دارایو لیست · activate what you trade",
            onBack = { navController.popBackStack() },
        )
        Column(
            modifier = Modifier
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
                MonoLabel("How this works", color = DaftarColors.CopperDeep, fontSize = 9)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Toggle on the currencies and metals your shop deals in. Active assets appear " +
                        "on your Cash card, in the FX form, and in customer transactions. " +
                        "USD, AFN, and PKR are always on.",
                    style = TextStyle(fontFamily = Inter, fontSize = 11.sp, color = DaftarColors.CopperDeep),
                )
            }

            Spacer(Modifier.height(16.dp))
            MonoLabel("Currencies · اسعار")
            Spacer(Modifier.height(8.dp))
            AssetCatalog.ALL.filter { it.type == AssetType.CURRENCY }.forEach { asset ->
                AssetToggleRow(asset, settings.isAssetActive(asset)) { viewModel.toggle(asset) }
            }

            Spacer(Modifier.height(16.dp))
            MonoLabel("Precious Metals · قیمتي فلزات")
            Spacer(Modifier.height(8.dp))
            AssetCatalog.ALL.filter { it.type == AssetType.METAL }.forEach { asset ->
                AssetToggleRow(asset, settings.isAssetActive(asset)) { viewModel.toggle(asset) }
            }
        }
    }
}

@Composable
private fun AssetToggleRow(asset: Asset, active: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) DaftarColors.PaperSoft else DaftarColors.PaperDeep.copy(alpha = 0.5f))
            .border(
                1.dp,
                if (active) DaftarColors.LineStrong else DaftarColors.Line,
                RoundedCornerShape(12.dp),
            )
            .clickable(enabled = !asset.isDefault, onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = asset.emoji, style = TextStyle(fontSize = 20.sp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    asset.name,
                    style = TextStyle(
                        fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                        color = if (active) DaftarColors.Ink else DaftarColors.Muted,
                    ),
                )
                if (asset.isDefault) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(DaftarColors.Ink.copy(alpha = 0.08f))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    ) {
                        Text(
                            "DEFAULT",
                            style = TextStyle(
                                fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold,
                                fontSize = 7.sp, letterSpacing = 0.1.em, color = DaftarColors.Muted,
                            ),
                        )
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = asset.code + " · " + asset.pashtoName +
                    if (asset.type == AssetType.METAL) " · per gram (also tola)" else "",
                style = TextStyle(fontFamily = JetBrainsMono, fontSize = 10.sp, color = DaftarColors.Muted),
            )
        }
        // Toggle track
        Box(
            modifier = Modifier
                .size(width = 40.dp, height = 24.dp)
                .clip(CircleShape)
                .background(if (active) DaftarColors.Green else DaftarColors.LineStrong),
            contentAlignment = if (active) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .padding(3.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(androidx.compose.ui.graphics.Color.White),
            )
        }
    }
}

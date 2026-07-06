package com.daftar.app.ui.feature.detail

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.daftar.app.domain.model.CommissionMode
import com.daftar.app.domain.model.Counterparty
import com.daftar.app.domain.model.Hawala
import com.daftar.app.domain.model.HawalaStatus
import com.daftar.app.domain.model.HawalaType
import com.daftar.app.domain.model.MoneyByCurrency
import com.daftar.app.domain.repository.PartnerRepository
import com.daftar.app.domain.usecase.PositionCalculator
import com.daftar.app.ui.common.IconSquareButton
import com.daftar.app.ui.common.LocalToaster
import com.daftar.app.ui.common.MonoLabel
import com.daftar.app.ui.common.ToastIcon
import com.daftar.app.ui.components.DarkBalanceGrid
import com.daftar.app.ui.common.PartnerBadge
import com.daftar.app.ui.navigation.DaftarDestinations
import com.daftar.app.ui.theme.DaftarColors
import com.daftar.app.ui.theme.Fraunces
import com.daftar.app.ui.theme.Inter
import com.daftar.app.ui.theme.JetBrainsMono
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class PartnerDetailUiState(
    val partner: Counterparty? = null,
    val position: MoneyByCurrency = MoneyByCurrency(),
)

@HiltViewModel
class PartnerDetailViewModel @Inject constructor(
    partnerRepository: PartnerRepository,
    positionCalculator: PositionCalculator,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val partnerId: String = checkNotNull(savedStateHandle["partnerId"])

    val uiState = partnerRepository.partners
        .map { partners ->
            val partner = partners.firstOrNull { it.id == partnerId }
            PartnerDetailUiState(
                partner = partner,
                position = partner?.let(positionCalculator::partnerPosition) ?: MoneyByCurrency(),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PartnerDetailUiState())
}

/** Partner (counterparty saraf) detail: exposure, actions, hawala history. */
@Composable
fun PartnerDetailScreen(
    navController: NavController,
    viewModel: PartnerDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val partner = state.partner ?: return
    val toaster = LocalToaster.current

    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 40.dp),
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DaftarColors.Ink)
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp, bottom = 18.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    IconSquareButton(Icons.AutoMirrored.Rounded.ArrowBack, { navController.popBackStack() }, onDark = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconSquareButton(Icons.Rounded.Phone, { toaster("Dialling ${partner.phone}…", ToastIcon.PHONE) }, onDark = true)
                        IconSquareButton(Icons.Rounded.ChatBubble, { toaster("Opening WhatsApp to ${partner.shortName}", ToastIcon.MESSAGE) }, onDark = true)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    PartnerBadge(partner, 56.dp)
                    Column {
                        Text(
                            text = partner.name,
                            style = TextStyle(
                                fontFamily = Fraunces,
                                fontWeight = FontWeight.Medium,
                                fontSize = 22.sp,
                                letterSpacing = (-0.02).em,
                                color = DaftarColors.Paper,
                            ),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${partner.city.displayName.uppercase()} · ${partner.phone}",
                            style = TextStyle(
                                fontFamily = JetBrainsMono,
                                fontSize = 11.sp,
                                letterSpacing = 0.1.em,
                                color = DaftarColors.GoldSoft,
                            ),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = DaftarColors.Paper.copy(alpha = 0.15f))
                Spacer(Modifier.height(14.dp))
                DarkBalanceGrid(
                    position = state.position,
                    statusFor = { amt -> if (amt > 0.5) "OWES YOU" else if (amt < -0.5) "YOU OWE" else "SETTLED" },
                )
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DaftarColors.PaperDeep)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PartnerAction(
                    icon = Icons.AutoMirrored.Rounded.Send, label = "Send hawala", primary = true,
                    modifier = Modifier.weight(1f),
                ) { navController.navigate(DaftarDestinations.newHawala(partner.id)) }
                PartnerAction(
                    icon = Icons.Rounded.Balance, label = "Settle",
                    modifier = Modifier.weight(1f),
                ) { navController.navigate(DaftarDestinations.settle(partner.id)) }
                PartnerAction(
                    icon = Icons.Rounded.Description, label = "Statement",
                    modifier = Modifier.weight(1f),
                ) { navController.navigate(DaftarDestinations.partnerStatement(partner.id)) }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MonoLabel("Transactions")
                HorizontalDivider(Modifier.weight(1f), color = DaftarColors.Line)
                MonoLabel("${partner.hawalas.size} entries")
            }
        }

        val hawalas = partner.hawalas.reversed()
        if (hawalas.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = 30.dp), contentAlignment = Alignment.Center) {
                    Text("No transactions yet", style = TextStyle(fontFamily = Inter, fontSize = 13.sp, color = DaftarColors.Muted))
                }
            }
        } else {
            items(count = hawalas.size, key = { i -> hawalas[i].id }) { i ->
                PartnerTxEntry(hawalas[i]) {
                    if (hawalas[i].type != HawalaType.SETTLEMENT && !hawalas[i].isSynthetic) {
                        navController.navigate(DaftarDestinations.hawalaDetail(hawalas[i].id))
                    }
                }
            }
        }
    }
}

@Composable
private fun PartnerAction(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (primary) DaftarColors.Copper else DaftarColors.Paper)
            .border(
                1.dp,
                if (primary) DaftarColors.Copper else DaftarColors.Line,
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (primary) DaftarColors.Paper else DaftarColors.InkSoft,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = label.uppercase(),
            style = TextStyle(
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 0.08.em,
                color = if (primary) DaftarColors.Paper else DaftarColors.InkSoft,
            ),
        )
    }
}

@Composable
private fun PartnerTxEntry(h: Hawala, onClick: () -> Unit) {
    val isSettle = h.type == HawalaType.SETTLEMENT
    val paid = h.status == HawalaStatus.PAID
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (isSettle) DaftarColors.Gold.copy(alpha = 0.05f) else Color.Transparent)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isSettle) {
                    Icon(Icons.Rounded.Balance, null, tint = DaftarColors.CopperDeep, modifier = Modifier.size(11.dp))
                }
                Text(
                    text = when (h.type) {
                        HawalaType.SEND -> "SENT HAWALA"
                        HawalaType.RECEIVE -> "RECEIVED HAWALA"
                        HawalaType.SETTLEMENT -> "SETTLEMENT"
                    },
                    style = TextStyle(
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 0.15.em,
                        color = when (h.type) {
                            HawalaType.SEND -> DaftarColors.Red
                            HawalaType.RECEIVE -> DaftarColors.Green
                            HawalaType.SETTLEMENT -> DaftarColors.CopperDeep
                        },
                    ),
                )
                if (!paid) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(DaftarColors.Copper)
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(Icons.Rounded.Schedule, null, tint = DaftarColors.Paper, modifier = Modifier.size(10.dp))
                        Text(
                            "PENDING",
                            style = TextStyle(
                                fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold,
                                fontSize = 8.sp, letterSpacing = 0.1.em, color = DaftarColors.Paper,
                            ),
                        )
                    }
                }
            }
            Text(
                text = h.dateLabel,
                style = TextStyle(fontFamily = JetBrainsMono, fontSize = 10.sp, color = DaftarColors.Muted),
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = (if (isSettle) (if (h.amount >= 0) "+" else "−") else "") +
                    Formatters.number(abs(h.amount)),
                style = TextStyle(
                    fontFamily = Fraunces,
                    fontWeight = FontWeight.Medium,
                    fontSize = 20.sp,
                    color = if (isSettle) DaftarColors.CopperDeep else DaftarColors.Ink,
                ),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = h.currency,
                style = TextStyle(
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 0.1.em,
                    color = DaftarColors.Muted,
                ),
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (isSettle) (h.note ?: "Settled")
            else "${h.fromCity.code} → ${h.toCity.code} · from ${h.senderName} · to ${h.receiverName}",
            style = TextStyle(fontFamily = Inter, fontSize = 12.sp, color = DaftarColors.InkSoft),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (isSettle) {
                TxChip(Icons.Rounded.Check, "Position offset", DaftarColors.Gold.copy(alpha = 0.18f), DaftarColors.CopperDeep)
            } else {
                TxChip(Icons.Rounded.Key, h.pickupCode, DaftarColors.Ink, DaftarColors.Paper)
                TxChip(
                    Icons.Rounded.Tag,
                    if (h.commissionMode == CommissionMode.FIXED) {
                        Formatters.amount(h.resolvedCommissionAmount, h.currency) + " " + h.currency
                    } else {
                        Formatters.rate(h.commissionPercent, 1) + "%"
                    },
                    DaftarColors.Gold.copy(alpha = 0.18f),
                    DaftarColors.CopperDeep,
                )
                if (paid) {
                    TxChip(Icons.Rounded.Check, "Paid out", DaftarColors.Green.copy(alpha = 0.1f), DaftarColors.Green)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = DaftarColors.Line)
    }
}

@Composable
fun TxChip(icon: ImageVector?, label: String, background: Color, contentColor: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(background)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(10.dp))
        }
        Text(
            text = label,
            style = TextStyle(
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp,
                letterSpacing = 0.05.em,
                color = contentColor,
            ),
        )
    }
}

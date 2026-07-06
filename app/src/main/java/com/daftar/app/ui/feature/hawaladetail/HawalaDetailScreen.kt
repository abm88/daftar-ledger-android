package com.daftar.app.ui.feature.hawaladetail

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
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
import com.daftar.app.domain.repository.PartnerRepository
import com.daftar.app.domain.usecase.MarkHawalaPaidUseCase
import com.daftar.app.ui.common.IconSquareButton
import com.daftar.app.ui.common.LocalToaster
import com.daftar.app.ui.common.MonoLabel
import com.daftar.app.ui.common.ToastCenter
import com.daftar.app.ui.common.ToastIcon
import com.daftar.app.ui.common.dashedBorder
import com.daftar.app.ui.components.DetailCard
import com.daftar.app.ui.components.DetailRow
import com.daftar.app.ui.components.DetailSectionTitle
import com.daftar.app.ui.common.PartnerBadge
import com.daftar.app.ui.navigation.DaftarDestinations
import com.daftar.app.ui.theme.DaftarColors
import com.daftar.app.ui.theme.Fraunces
import com.daftar.app.ui.theme.JetBrainsMono
import com.daftar.app.ui.theme.NotoNaskhArabic
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HawalaDetailUiState(
    val hawala: Hawala? = null,
    val partner: Counterparty? = null,
)

@HiltViewModel
class HawalaDetailViewModel @Inject constructor(
    partnerRepository: PartnerRepository,
    private val markHawalaPaid: MarkHawalaPaidUseCase,
    private val toastCenter: ToastCenter,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val hawalaId: String = checkNotNull(savedStateHandle["hawalaId"])

    val uiState = partnerRepository.partners
        .map { partners ->
            partners.forEach { partner ->
                partner.hawalas.firstOrNull { it.id == hawalaId }?.let {
                    return@map HawalaDetailUiState(it, partner)
                }
            }
            HawalaDetailUiState()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HawalaDetailUiState())

    fun markPaid() {
        viewModelScope.launch {
            markHawalaPaid(hawalaId)
            toastCenter.show("Hawala marked paid out", ToastIcon.CHECK)
        }
    }
}

/** Full hawala record: route, pickup code, parties, financials, timeline. */
@Composable
fun HawalaDetailScreen(
    navController: NavController,
    viewModel: HawalaDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val hawala = state.hawala ?: return
    val partner = state.partner ?: return
    val toaster = LocalToaster.current
    val clipboard = LocalClipboardManager.current

    val pending = hawala.status == HawalaStatus.PENDING
    val commissionAmount = hawala.resolvedCommissionAmount
    val commissionLabel = if (hawala.commissionMode == CommissionMode.FIXED) "Fixed"
    else Formatters.rate(hawala.commissionPercent, 1) + "%"

    Column(modifier = Modifier.fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 20.dp),
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (pending) DaftarColors.Ink else DaftarColors.GreenDeep)
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp)
                        .padding(top = 8.dp, bottom = 20.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconSquareButton(Icons.AutoMirrored.Rounded.ArrowBack, { navController.popBackStack() }, onDark = true)
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (pending) DaftarColors.Copper else DaftarColors.Green,
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = if (pending) Icons.Rounded.Schedule else Icons.Rounded.Check,
                                contentDescription = null,
                                tint = DaftarColors.Paper,
                                modifier = Modifier.size(10.dp),
                            )
                            Text(
                                text = if (pending) "PENDING PICKUP" else "SETTLED",
                                style = TextStyle(
                                    fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp, letterSpacing = 0.1.em, color = DaftarColors.Paper,
                                ),
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RouteCity(hawala.fromCity.code, hawala.fromCity.displayName)
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            HorizontalDivider(
                                color = DaftarColors.GoldSoft.copy(alpha = 0.5f),
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                            Box(
                                modifier = Modifier
                                    .background(if (pending) DaftarColors.Ink else DaftarColors.GreenDeep)
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                    contentDescription = null,
                                    tint = DaftarColors.Gold,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        RouteCity(hawala.toCity.code, hawala.toCity.displayName)
                    }
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = DaftarColors.Paper.copy(alpha = 0.18f))
                    Spacer(Modifier.height(10.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        MonoLabel(
                            if (hawala.type == HawalaType.SEND) "Amount sent" else "Amount received",
                            color = DaftarColors.GoldSoft, fontSize = 9, letterSpacing = 0.25,
                        )
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                text = Formatters.number(hawala.amount),
                                style = TextStyle(
                                    fontFamily = Fraunces,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 38.sp,
                                    letterSpacing = (-0.02).em,
                                    color = DaftarColors.Paper,
                                ),
                            )
                            Text(
                                text = " ${hawala.currency}",
                                style = TextStyle(
                                    fontFamily = JetBrainsMono,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    letterSpacing = 0.1.em,
                                    color = DaftarColors.GoldSoft,
                                ),
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        }
                    }
                }
            }

            item {
                // Pickup code block
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 16.dp)
                        .dashedBorder(DaftarColors.Copper, 2.dp, 14.dp)
                        .background(Color.White, RoundedCornerShape(14.dp))
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    MonoLabel("Pickup Code", color = DaftarColors.CopperDeep, fontSize = 9, letterSpacing = 0.3)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "د پیسو کوډ",
                        style = TextStyle(
                            fontFamily = NotoNaskhArabic,
                            fontSize = 12.sp,
                            color = DaftarColors.Muted,
                            textDirection = TextDirection.Rtl,
                        ),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = hawala.pickupCode.toCharArray().joinToString(" "),
                        style = TextStyle(
                            fontFamily = JetBrainsMono,
                            fontWeight = FontWeight.Bold,
                            fontSize = 34.sp,
                            color = DaftarColors.Ink,
                        ),
                    )
                    Spacer(Modifier.height(6.dp))
                    MonoLabel("Recipient presents this code with ID", fontSize = 9, letterSpacing = 0.1)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CodeAction("Copy", Icons.Rounded.Tag, Modifier.weight(1f)) {
                            clipboard.setText(AnnotatedString(hawala.pickupCode))
                            toaster("Code ${hawala.pickupCode} copied", ToastIcon.HASH)
                        }
                        CodeAction("WhatsApp", Icons.Rounded.Share, Modifier.weight(1f)) {
                            toaster("Code sent via WhatsApp", ToastIcon.SEND)
                        }
                    }
                }
            }

            item {
                DetailSectionTitle("Parties")
                DetailCard {
                    DetailRow(
                        label = "Sender · لیږونکی",
                        value = hawala.senderName,
                        sub = "${hawala.fromCity.displayName} · ${hawala.fromCity.code}",
                        icon = Icons.Rounded.ArrowUpward,
                        iconTint = DaftarColors.Red,
                        iconBackground = DaftarColors.Red.copy(alpha = 0.12f),
                    )
                    DetailRow(
                        label = "Receiver · ترلاسه کوونکی",
                        value = hawala.receiverName,
                        sub = "${hawala.toCity.displayName} · ${hawala.toCity.code}",
                        icon = Icons.Rounded.ArrowDownward,
                        iconTint = DaftarColors.Green,
                        iconBackground = DaftarColors.Green.copy(alpha = 0.12f),
                        showDivider = false,
                    )
                }
            }

            item {
                DetailSectionTitle("Partner")
                DetailCard {
                    DetailRow(
                        label = "${partner.city.displayName} · ${partner.tier.label} tier",
                        value = partner.name,
                        sub = partner.phone,
                        leading = { PartnerBadge(partner, 36.dp) },
                        trailing = {
                            Icon(
                                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = null,
                                tint = DaftarColors.Muted,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        showDivider = false,
                        modifier = Modifier.clickable {
                            navController.navigate(DaftarDestinations.partnerDetail(partner.id))
                        },
                    )
                }
            }

            item {
                DetailSectionTitle("Financials")
                DetailCard {
                    DetailRow(
                        label = "Commission",
                        value = commissionLabel,
                        aside = Formatters.amount(commissionAmount, hawala.currency) + " " + hawala.currency,
                        icon = Icons.Rounded.Tag,
                    )
                    DetailRow(
                        label = "Net to " + (if (hawala.type == HawalaType.SEND) "receiver" else "you"),
                        value = Formatters.amount(hawala.amount - commissionAmount, hawala.currency) +
                            " " + hawala.currency,
                        icon = Icons.Rounded.Balance,
                        showDivider = false,
                    )
                }
            }

            item {
                DetailSectionTitle("Status timeline")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(DaftarColors.PaperSoft)
                        .border(1.dp, DaftarColors.Line, RoundedCornerShape(14.dp))
                        .padding(14.dp),
                ) {
                    TimelineStep(
                        icon = Icons.Rounded.Check,
                        state = TimelineState.DONE,
                        label = "Hawala issued",
                        time = Formatters.dayMonthLabel(hawala.timestampMillis) + ", " +
                            Formatters.timeLabel(hawala.timestampMillis),
                    )
                    TimelineStep(
                        icon = Icons.Rounded.Share,
                        state = TimelineState.DONE,
                        label = "Code shared with partner",
                        time = "Auto-sent via WhatsApp",
                    )
                    TimelineStep(
                        icon = if (pending) Icons.Rounded.Schedule else Icons.Rounded.Check,
                        state = if (pending) TimelineState.CURRENT else TimelineState.DONE,
                        label = if (pending) "Awaiting pickup" else "Paid out to receiver",
                        time = if (pending) "Receiver presents code at ${hawala.toCity.displayName}" else hawala.dateLabel,
                        isLast = pending,
                    )
                    if (!pending) {
                        TimelineStep(
                            icon = Icons.Rounded.Balance,
                            state = TimelineState.UPCOMING,
                            label = "Pending settlement with ${partner.shortName}",
                            time = "Will be netted at next reconciliation",
                            isLast = true,
                        )
                    }
                }
            }
        }

        // Bottom actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DaftarColors.Paper)
                .padding(horizontal = 18.dp, vertical = 14.dp)
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (pending) {
                BottomAction(
                    label = "Mark Paid Out", icon = Icons.Rounded.Check,
                    background = DaftarColors.Green, modifier = Modifier.weight(1f),
                ) { viewModel.markPaid() }
                BottomAction(
                    label = "Share Code", icon = Icons.Rounded.ChatBubble,
                    background = DaftarColors.WhatsApp, modifier = Modifier.weight(1f),
                ) { toaster("Code sent via WhatsApp", ToastIcon.SEND) }
            } else {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.5.dp, DaftarColors.Ink, RoundedCornerShape(14.dp))
                        .clickable { navController.popBackStack() }
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = DaftarColors.Ink, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "BACK TO ALL HAWALAS",
                        style = TextStyle(
                            fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold,
                            fontSize = 12.sp, letterSpacing = 0.08.em, color = DaftarColors.Ink,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun RouteCity(code: String, name: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = code,
            style = TextStyle(
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                letterSpacing = 0.1.em,
                color = DaftarColors.Paper,
            ),
        )
        Spacer(Modifier.height(2.dp))
        MonoLabel(name, color = DaftarColors.GoldSoft, fontSize = 9, letterSpacing = 0.15)
    }
}

@Composable
private fun CodeAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(DaftarColors.PaperSoft)
            .border(1.dp, DaftarColors.LineStrong, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = DaftarColors.InkSoft, modifier = Modifier.size(11.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text = label.uppercase(),
            style = TextStyle(
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 0.1.em,
                color = DaftarColors.InkSoft,
            ),
        )
    }
}

private enum class TimelineState { DONE, CURRENT, UPCOMING }

@Composable
private fun TimelineStep(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    state: TimelineState,
    label: String,
    time: String,
    isLast: Boolean = false,
) {
    Row(modifier = Modifier.padding(bottom = if (isLast) 4.dp else 14.dp)) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(
                    when (state) {
                        TimelineState.DONE -> DaftarColors.Green
                        TimelineState.CURRENT -> DaftarColors.Copper
                        TimelineState.UPCOMING -> DaftarColors.PaperDeep
                    },
                )
                .border(
                    1.5.dp,
                    when (state) {
                        TimelineState.DONE -> DaftarColors.Green
                        TimelineState.CURRENT -> DaftarColors.Copper
                        TimelineState.UPCOMING -> DaftarColors.GoldSoft
                    },
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (state == TimelineState.UPCOMING) DaftarColors.Gold else DaftarColors.Paper,
                modifier = Modifier.size(11.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = label,
                style = TextStyle(
                    fontFamily = com.daftar.app.ui.theme.Inter,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = if (state == TimelineState.UPCOMING) DaftarColors.Muted else DaftarColors.Ink,
                ),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = time,
                style = TextStyle(fontFamily = JetBrainsMono, fontSize = 10.sp, color = DaftarColors.Muted),
            )
        }
    }
}

@Composable
private fun BottomAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    background: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = DaftarColors.Paper, modifier = Modifier.size(14.dp))
        Spacer(Modifier.size(8.dp))
        Text(
            text = label.uppercase(),
            style = TextStyle(
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 0.08.em,
                color = DaftarColors.Paper,
            ),
        )
    }
}

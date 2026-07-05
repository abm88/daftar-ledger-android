package com.daftar.app.ui.feature.newhawala

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.BusinessCenter
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.daftar.app.core.format.Formatters
import com.daftar.app.domain.model.AssetCatalog
import com.daftar.app.domain.model.City
import com.daftar.app.domain.model.CommissionMode
import com.daftar.app.domain.usecase.CommissionCalculator
import com.daftar.app.domain.usecase.SenderMode
import com.daftar.app.ui.common.BigAmountInput
import com.daftar.app.ui.common.CurrencySwitcher
import com.daftar.app.ui.common.FieldBox
import com.daftar.app.ui.common.FieldTextInput
import com.daftar.app.ui.common.IconSquareButton
import com.daftar.app.ui.common.MonoLabel
import com.daftar.app.ui.common.SegmentButton
import com.daftar.app.ui.common.SegmentedSwitcher
import com.daftar.app.ui.common.SheetHandle
import com.daftar.app.ui.common.SubmitButton
import com.daftar.app.ui.common.dashedBorder
import com.daftar.app.ui.common.sanitizeAmountInput
import com.daftar.app.ui.components.CustomerBadge
import com.daftar.app.ui.components.PartnerBadge
import com.daftar.app.ui.components.badgeColor
import com.daftar.app.ui.navigation.DaftarDestinations
import com.daftar.app.ui.theme.DaftarColors
import com.daftar.app.ui.theme.Fraunces
import com.daftar.app.ui.theme.Inter
import com.daftar.app.ui.theme.JetBrainsMono
import com.daftar.app.ui.theme.NotoNaskhArabic
import kotlin.math.abs

/** New hawala form: sender mode, amount, corridor, commission, parties, code. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewHawalaScreen(
    navController: NavController,
    viewModel: NewHawalaViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val form = state.form

    Column(modifier = Modifier.fillMaxWidth()) {
        // Copper header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DaftarColors.Copper)
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "New Hawala · Send",
                    style = TextStyle(
                        fontFamily = Fraunces,
                        fontWeight = FontWeight.Medium,
                        fontSize = 18.sp,
                        color = DaftarColors.Paper,
                    ),
                )
                Text(
                    text = "د پیسو لیږل",
                    style = TextStyle(
                        fontFamily = NotoNaskhArabic,
                        fontSize = 12.sp,
                        color = DaftarColors.Paper.copy(alpha = 0.85f),
                        textDirection = TextDirection.Rtl,
                    ),
                )
            }
            IconSquareButton(Icons.Rounded.Close, { navController.popBackStack() }, onDark = true)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            // Sender mode
            MonoLabel("How is sender paying?", fontSize = 9)
            Spacer(Modifier.height(8.dp))
            SegmentedSwitcher {
                SegmentButton(form.senderMode == SenderMode.CASH, { viewModel.setSenderMode(SenderMode.CASH) }) {
                    SenderModeLabel(Icons.Rounded.BusinessCenter, "Cash", "Walk-in", form.senderMode == SenderMode.CASH)
                }
                SegmentButton(form.senderMode == SenderMode.ACCOUNT, { viewModel.setSenderMode(SenderMode.ACCOUNT) }) {
                    SenderModeLabel(Icons.Rounded.Person, "Account", "From holder", form.senderMode == SenderMode.ACCOUNT)
                }
            }

            Spacer(Modifier.height(14.dp))
            CurrencySwitcher(form.currency, { cur -> viewModel.update { it.copy(currency = cur) } })

            Spacer(Modifier.height(14.dp))
            BigAmountInput(
                value = form.amountText,
                onValueChange = { text -> viewModel.update { it.copy(amountText = text) } },
                currency = form.currency,
            )

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CityField("From", form.fromCity, Modifier.weight(1f)) {
                    viewModel.update { it.copy(picker = HawalaPicker.FROM_CITY) }
                }
                CityField("To", form.toCity, Modifier.weight(1f)) {
                    viewModel.update { it.copy(picker = HawalaPicker.TO_CITY) }
                }
            }

            Spacer(Modifier.height(12.dp))
            CommissionBlock(state, viewModel)

            Spacer(Modifier.height(12.dp))
            if (form.senderMode == SenderMode.CASH) {
                FieldBox("Sender · لیږونکی", modifier = Modifier.fillMaxWidth()) {
                    FieldTextInput(form.senderName, { text -> viewModel.update { it.copy(senderName = text) } }, "Sender full name")
                }
                Spacer(Modifier.height(10.dp))
                FieldBox("Receiver · ترلاسه کوونکی", modifier = Modifier.fillMaxWidth()) {
                    FieldTextInput(form.receiverName, { text -> viewModel.update { it.copy(receiverName = text) } }, "Receiver full name")
                }
            } else {
                // Sender account picker
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DaftarColors.Green.copy(alpha = 0.05f))
                        .border(1.dp, DaftarColors.Green, RoundedCornerShape(12.dp))
                        .clickable { viewModel.update { it.copy(picker = HawalaPicker.SENDER_ACCOUNT) } }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.senderCustomer != null) {
                        CustomerBadge(state.senderCustomer!!, 38.dp)
                    } else {
                        Icon(Icons.Rounded.Person, null, tint = DaftarColors.Green, modifier = Modifier.size(18.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        MonoLabel("Sender account · د لیږونکي حساب", color = DaftarColors.Green, fontSize = 9)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = state.senderCustomer?.name ?: "Choose account",
                            style = TextStyle(
                                fontFamily = Inter,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = if (state.senderCustomer != null) DaftarColors.Ink else DaftarColors.Muted,
                            ),
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
                        tint = DaftarColors.Muted, modifier = Modifier.size(16.dp),
                    )
                }

                if (state.senderCustomer != null && state.amount > 0) {
                    Spacer(Modifier.height(12.dp))
                    BalanceImpactCard(state)
                }

                Spacer(Modifier.height(12.dp))
                FieldBox("Receiver · ترلاسه کوونکی", modifier = Modifier.fillMaxWidth()) {
                    FieldTextInput(form.receiverName, { text -> viewModel.update { it.copy(receiverName = text) } }, "Receiver full name")
                }
            }

            Spacer(Modifier.height(12.dp))
            // Partner picker
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DaftarColors.PaperSoft)
                    .border(1.dp, DaftarColors.LineStrong, RoundedCornerShape(12.dp))
                    .clickable { viewModel.update { it.copy(picker = HawalaPicker.PARTNER) } }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Rounded.Group, null, tint = DaftarColors.InkSoft, modifier = Modifier.size(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    MonoLabel("Partner", fontSize = 9)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = state.partner?.let { "${it.name} · ${it.city.displayName}" } ?: "Choose partner",
                        style = TextStyle(
                            fontFamily = Inter,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = if (state.partner != null) DaftarColors.Ink else DaftarColors.Muted,
                        ),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
                    tint = DaftarColors.Muted, modifier = Modifier.size(16.dp),
                )
            }

            Spacer(Modifier.height(12.dp))
            // Pickup code preview
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(DaftarColors.Ink)
                    .padding(3.dp)
                    .dashedBorder(DaftarColors.GoldSoft.copy(alpha = 0.35f), 1.5.dp, 11.dp)
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MonoLabel("Pickup Code", color = DaftarColors.GoldSoft, fontSize = 9, letterSpacing = 0.3)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = form.pickupCode.toCharArray().joinToString(" "),
                    style = TextStyle(
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Bold,
                        fontSize = 26.sp,
                        color = DaftarColors.Paper,
                    ),
                )
            }
            Spacer(Modifier.height(20.dp))
        }

        // Submit bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(DaftarColors.Paper)
                .padding(horizontal = 18.dp, vertical = 12.dp)
                .navigationBarsPadding(),
        ) {
            SubmitButton("Issue Hawala", viewModel::review, icon = Icons.Rounded.Check, container = DaftarColors.Copper)
        }
    }

    // ---- Pickers & confirm sheet ----
    when (form.picker) {
        HawalaPicker.FROM_CITY, HawalaPicker.TO_CITY -> CityPickerSheet(
            title = if (form.picker == HawalaPicker.FROM_CITY) "From city" else "To city",
            selected = if (form.picker == HawalaPicker.FROM_CITY) form.fromCity else form.toCity,
            onDismiss = { viewModel.update { it.copy(picker = HawalaPicker.NONE) } },
            onPick = { city ->
                viewModel.update {
                    if (it.picker == HawalaPicker.FROM_CITY) it.copy(fromCity = city, picker = HawalaPicker.NONE)
                    else it.copy(toCity = city, picker = HawalaPicker.NONE)
                }
            },
        )
        HawalaPicker.PARTNER -> PartnerPickerSheet(
            state = state,
            onDismiss = { viewModel.update { it.copy(picker = HawalaPicker.NONE) } },
            onPick = viewModel::pickPartner,
        )
        HawalaPicker.SENDER_ACCOUNT -> SenderAccountPickerSheet(
            state = state,
            onDismiss = { viewModel.update { it.copy(picker = HawalaPicker.NONE) } },
            onPick = viewModel::pickSenderCustomer,
        )
        HawalaPicker.NONE -> Unit
    }

    if (form.confirming) {
        ConfirmHawalaSheet(
            state = state,
            onDismiss = { viewModel.update { it.copy(confirming = false) } },
            onConfirm = {
                viewModel.confirmIssue { hawalaId ->
                    navController.popBackStack()
                    navController.navigate(DaftarDestinations.hawalaDetail(hawalaId))
                }
            },
        )
    }
}

@Composable
private fun SenderModeLabel(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, sub: String, selected: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) DaftarColors.Paper else DaftarColors.Muted,
            modifier = Modifier.size(14.dp),
        )
        Column {
            Text(
                text = label.uppercase(),
                style = TextStyle(
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 0.05.em,
                    color = if (selected) DaftarColors.Paper else DaftarColors.Muted,
                ),
            )
            Text(
                text = sub,
                style = TextStyle(
                    fontFamily = Inter,
                    fontSize = 8.sp,
                    color = (if (selected) DaftarColors.Paper else DaftarColors.Muted).copy(alpha = 0.7f),
                ),
            )
        }
    }
}

@Composable
private fun CityField(label: String, city: City, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(DaftarColors.PaperSoft)
            .border(1.dp, DaftarColors.LineStrong, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Rounded.Place, null, tint = DaftarColors.InkSoft, modifier = Modifier.size(16.dp))
        Column {
            MonoLabel(label, fontSize = 9)
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${city.displayName} · ${city.code}",
                style = TextStyle(
                    fontFamily = Inter,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = DaftarColors.Ink,
                ),
            )
        }
    }
}

@Composable
private fun CommissionBlock(state: NewHawalaUiState, viewModel: NewHawalaViewModel) {
    val form = state.form
    val isPercent = form.commissionMode == CommissionMode.PERCENT
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DaftarColors.Gold.copy(alpha = 0.08f))
            .border(1.dp, DaftarColors.Gold.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Rounded.Tag, null, tint = DaftarColors.CopperDeep, modifier = Modifier.size(11.dp))
                MonoLabel("Commission · کمیشن", color = DaftarColors.CopperDeep)
            }
            if (state.amount > 0 || !isPercent) {
                Text(
                    text = Formatters.amount(state.commissionAmount, form.currency) + " " + form.currency +
                        (if (isPercent && state.commissionAmount > 0) " · ${Formatters.rate(form.commissionPercent, 1)}%" else ""),
                    style = TextStyle(
                        fontFamily = Fraunces,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = DaftarColors.Ink,
                    ),
                )
            } else {
                MonoLabel("Enter amount first", fontSize = 9, letterSpacing = 0.05)
            }
        }
        Spacer(Modifier.height(10.dp))
        SegmentedSwitcher {
            SegmentButton(isPercent, { viewModel.setCommissionMode(CommissionMode.PERCENT) }, selectedColor = DaftarColors.Copper) {
                Text(
                    "PERCENT",
                    style = TextStyle(
                        fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 10.sp,
                        letterSpacing = 0.1.em,
                        color = if (isPercent) DaftarColors.Paper else DaftarColors.Muted,
                    ),
                )
            }
            SegmentButton(!isPercent, { viewModel.setCommissionMode(CommissionMode.FIXED) }, selectedColor = DaftarColors.Copper) {
                Text(
                    "FIXED ${form.currency}",
                    style = TextStyle(
                        fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 10.sp,
                        letterSpacing = 0.1.em,
                        color = if (!isPercent) DaftarColors.Paper else DaftarColors.Muted,
                    ),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        if (isPercent) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                CommissionCalculator.PERCENT_PRESETS.forEach { preset ->
                    val on = abs(form.commissionPercent - preset) < 0.01
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(7.dp))
                            .background(if (on) DaftarColors.CopperDeep else DaftarColors.Paper)
                            .border(
                                1.dp,
                                if (on) DaftarColors.CopperDeep else DaftarColors.LineStrong,
                                RoundedCornerShape(7.dp),
                            )
                            .clickable { viewModel.update { it.copy(commissionPercent = preset) } }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "${Formatters.rate(preset, 1)}%",
                            style = TextStyle(
                                fontFamily = JetBrainsMono,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = if (on) DaftarColors.Paper else DaftarColors.InkSoft,
                            ),
                        )
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(DaftarColors.Paper)
                    .border(1.dp, DaftarColors.LineStrong, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DaftarColors.Copper),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = AssetCatalog.symbolFor(form.currency),
                        style = TextStyle(
                            fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold,
                            fontSize = 13.sp, color = DaftarColors.Paper,
                        ),
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    if (form.commissionFixedText.isEmpty()) {
                        Text(
                            "0",
                            style = TextStyle(
                                fontFamily = Fraunces, fontWeight = FontWeight.Medium,
                                fontSize = 18.sp, color = DaftarColors.MutedLight,
                            ),
                        )
                    }
                    androidx.compose.foundation.text.BasicTextField(
                        value = form.commissionFixedText,
                        onValueChange = { text ->
                            viewModel.update { it.copy(commissionFixedText = sanitizeAmountInput(text)) }
                        },
                        textStyle = TextStyle(
                            fontFamily = Fraunces, fontWeight = FontWeight.Medium,
                            fontSize = 18.sp, color = DaftarColors.Ink,
                        ),
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                MonoLabel(form.currency, fontSize = 11, letterSpacing = 0.1)
            }
        }
    }
}

@Composable
private fun BalanceImpactCard(state: NewHawalaUiState) {
    val insufficient = state.insufficientAccountFunds
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (insufficient) DaftarColors.Red.copy(alpha = 0.06f) else DaftarColors.Ink)
            .then(
                if (insufficient) Modifier.dashedBorder(DaftarColors.Red, 1.5.dp, 14.dp) else Modifier,
            )
            .padding(14.dp),
    ) {
        MonoLabel(
            "Balance impact · ${state.form.currency}",
            color = if (insufficient) DaftarColors.Red else DaftarColors.GoldSoft,
            fontSize = 9,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                MonoLabel("Now", color = if (insufficient) DaftarColors.Muted else DaftarColors.MutedLight, fontSize = 9, letterSpacing = 0.1)
                Text(
                    text = Formatters.signPrefix(state.senderBalance).ifEmpty { "+" } +
                        Formatters.number(abs(state.senderBalance)),
                    style = TextStyle(
                        fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 18.sp,
                        color = when {
                            state.senderBalance > 0 -> DaftarColors.LongGreen
                            state.senderBalance < 0 -> DaftarColors.ShortRed
                            insufficient -> DaftarColors.Ink
                            else -> DaftarColors.MutedLight
                        },
                    ),
                )
            }
            Icon(
                Icons.AutoMirrored.Rounded.ArrowForward, null,
                tint = (if (insufficient) DaftarColors.Muted else DaftarColors.GoldSoft).copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp),
            )
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                MonoLabel("After", color = if (insufficient) DaftarColors.Muted else DaftarColors.MutedLight, fontSize = 9, letterSpacing = 0.1)
                Text(
                    text = Formatters.signPrefix(state.balanceAfter).ifEmpty { "+" } +
                        Formatters.number(abs(state.balanceAfter)),
                    style = TextStyle(
                        fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 18.sp,
                        color = when {
                            state.balanceAfter > 0 -> DaftarColors.LongGreen
                            state.balanceAfter < 0 && insufficient -> DaftarColors.Red
                            state.balanceAfter < 0 -> DaftarColors.ShortRed
                            else -> DaftarColors.MutedLight
                        },
                    ),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(
            color = if (insufficient) DaftarColors.Red.copy(alpha = 0.25f) else DaftarColors.GoldSoft.copy(alpha = 0.18f),
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Debit: ${Formatters.amount(state.amount, state.form.currency)} + " +
                    if (state.form.commissionMode == CommissionMode.FIXED) {
                        "${Formatters.amount(state.commissionAmount, state.form.currency)} ${state.form.currency} fixed"
                    } else {
                        "${Formatters.rate(state.form.commissionPercent, 1)}% commission"
                    },
                style = TextStyle(
                    fontFamily = JetBrainsMono, fontSize = 10.sp,
                    color = if (insufficient) DaftarColors.Red else DaftarColors.GoldSoft,
                ),
            )
            Text(
                text = if (insufficient) "⚠ INSUFFICIENT"
                else "TOTAL " + Formatters.amount(state.totalDebit, state.form.currency),
                style = TextStyle(
                    fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 10.sp,
                    color = if (insufficient) DaftarColors.Red else DaftarColors.GoldSoft,
                ),
            )
        }
    }
}

// ---- Sheets ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CityPickerSheet(
    title: String,
    selected: City,
    onDismiss: () -> Unit,
    onPick: (City) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = DaftarColors.Paper, dragHandle = { SheetHandle() }) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(title, style = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 18.sp, color = DaftarColors.Ink))
            Spacer(Modifier.height(12.dp))
            City.entries.forEach { city ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onPick(city) }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(city.badgeColor),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            city.code,
                            style = TextStyle(
                                fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold,
                                fontSize = 11.sp, letterSpacing = 0.1.em, color = DaftarColors.Paper,
                            ),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            city.displayName,
                            style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = DaftarColors.Ink),
                        )
                        Text(
                            "${city.code} · Afghanistan",
                            style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp, color = DaftarColors.Muted),
                        )
                    }
                    if (selected == city) {
                        Icon(Icons.Rounded.Check, null, tint = DaftarColors.Copper, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PartnerPickerSheet(
    state: NewHawalaUiState,
    onDismiss: () -> Unit,
    onPick: (com.daftar.app.domain.model.Counterparty) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = DaftarColors.Paper, dragHandle = { SheetHandle() }) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("Choose partner", style = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 18.sp, color = DaftarColors.Ink))
            Spacer(Modifier.height(12.dp))
            state.partners.forEach { partner ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onPick(partner) }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PartnerBadge(partner, 40.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            partner.name,
                            style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = DaftarColors.Ink),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                        Text(
                            "${partner.city.displayName} · ${partner.phone}",
                            style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp, color = DaftarColors.Muted),
                        )
                    }
                    if (state.form.partnerId == partner.id) {
                        Icon(Icons.Rounded.Check, null, tint = DaftarColors.Copper, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SenderAccountPickerSheet(
    state: NewHawalaUiState,
    onDismiss: () -> Unit,
    onPick: (com.daftar.app.domain.model.Customer) -> Unit,
) {
    // Funded accounts first, matching the prototype ordering.
    val sorted = state.customers.sortedWith(
        compareByDescending<com.daftar.app.domain.model.Customer> { (state.customerBalances[it.id] ?: 0.0) > 0 }
            .thenByDescending { state.customerBalances[it.id] ?: 0.0 },
    )
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = DaftarColors.Paper, dragHandle = { SheetHandle() }) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("Sender account", style = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 18.sp, color = DaftarColors.Ink))
            Spacer(Modifier.height(4.dp))
            Text(
                "Showing ${state.form.currency} balance · کافي پیسې لرونکي",
                style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp, color = DaftarColors.Muted),
            )
            Spacer(Modifier.height(12.dp))
            sorted.forEach { customer ->
                val balance = state.customerBalances[customer.id] ?: 0.0
                val hasFunds = balance > 0
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onPick(customer) }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(modifier = Modifier.size(40.dp)) {
                        CustomerBadge(customer, 40.dp)
                        if (!hasFunds) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(DaftarColors.Paper.copy(alpha = 0.45f)),
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            customer.name,
                            style = TextStyle(
                                fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                                color = if (hasFunds) DaftarColors.Ink else DaftarColors.Muted,
                            ),
                        )
                        Text(
                            "${customer.city.displayName} · ${customer.phone}",
                            style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp, color = DaftarColors.Muted),
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = Formatters.signPrefix(balance) + Formatters.number(abs(balance)),
                            style = TextStyle(
                                fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 14.sp,
                                color = when {
                                    balance > 0 -> DaftarColors.Green
                                    balance < 0 -> DaftarColors.Red
                                    else -> DaftarColors.Muted
                                },
                            ),
                        )
                        MonoLabel(state.form.currency, fontSize = 9, letterSpacing = 0.1)
                    }
                    if (state.form.senderCustomerId == customer.id) {
                        Icon(Icons.Rounded.Check, null, tint = DaftarColors.Green, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfirmHawalaSheet(
    state: NewHawalaUiState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val form = state.form
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = DaftarColors.Paper, dragHandle = { SheetHandle() }) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text("Review hawala", style = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 20.sp, color = DaftarColors.Ink))
            Text("د حوالې بیاکتنه", style = TextStyle(fontFamily = NotoNaskhArabic, fontSize = 12.sp, color = DaftarColors.Muted, textDirection = TextDirection.Rtl))
            Spacer(Modifier.height(14.dp))

            // Route + amount summary
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(DaftarColors.Ink)
                    .padding(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ConfirmCity(form.fromCity.code, form.fromCity.displayName)
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = DaftarColors.Gold, modifier = Modifier.size(18.dp))
                    }
                    ConfirmCity(form.toCity.code, form.toCity.displayName)
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = DaftarColors.Paper.copy(alpha = 0.15f))
                Spacer(Modifier.height(10.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    MonoLabel("Amount", color = DaftarColors.GoldSoft, fontSize = 9, letterSpacing = 0.25)
                    Text(
                        text = Formatters.amount(state.amount, form.currency) + " " + form.currency,
                        style = TextStyle(
                            fontFamily = Fraunces, fontWeight = FontWeight.Medium,
                            fontSize = 30.sp, color = DaftarColors.Paper,
                        ),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            ConfirmRow(
                "Sender · لیږونکی",
                state.senderCustomer?.name ?: form.senderName,
                if (form.senderMode == SenderMode.ACCOUNT) "FROM ACCOUNT" else "CASH",
            )
            ConfirmRow("Receiver · ترلاسه کوونکی", form.receiverName, form.toCity.code)
            ConfirmRow("Partner", state.partner?.shortName ?: "—", state.partner?.city?.displayName ?: "—")
            ConfirmRow(
                "Commission",
                if (form.commissionMode == CommissionMode.FIXED) "Fixed" else "${Formatters.rate(form.commissionPercent, 1)}%",
                Formatters.amount(state.commissionAmount, form.currency) + " " + form.currency,
            )
            if (form.senderMode == SenderMode.ACCOUNT && state.senderCustomer != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(DaftarColors.Ink)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        MonoLabel("Total debit from account", color = DaftarColors.GoldSoft, fontSize = 9)
                        Text(
                            text = Formatters.amount(state.totalDebit, form.currency) + " " + form.currency,
                            style = TextStyle(
                                fontFamily = Fraunces, fontWeight = FontWeight.Medium,
                                fontSize = 16.sp, color = DaftarColors.Paper,
                            ),
                        )
                    }
                    MonoLabel("Auto", color = DaftarColors.GoldSoft, fontSize = 9)
                }
            }

            Spacer(Modifier.height(14.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .dashedBorder(DaftarColors.Copper, 1.5.dp, 12.dp)
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MonoLabel("Pickup code · د پیسو کوډ", color = DaftarColors.CopperDeep, fontSize = 9)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = form.pickupCode.toCharArray().joinToString(" "),
                    style = TextStyle(
                        fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold,
                        fontSize = 24.sp, color = DaftarColors.Ink,
                    ),
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.5.dp, DaftarColors.Ink, RoundedCornerShape(14.dp))
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        "EDIT",
                        style = TextStyle(
                            fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold,
                            fontSize = 12.sp, letterSpacing = 0.08.em, color = DaftarColors.Ink,
                        ),
                    )
                }
                Row(
                    modifier = Modifier
                        .weight(2f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(DaftarColors.Green)
                        .clickable(onClick = onConfirm)
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Check, null, tint = DaftarColors.Paper, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "CONFIRM & ISSUE",
                        style = TextStyle(
                            fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold,
                            fontSize = 12.sp, letterSpacing = 0.08.em, color = DaftarColors.Paper,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfirmCity(code: String, name: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            code,
            style = TextStyle(
                fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold,
                fontSize = 18.sp, letterSpacing = 0.1.em, color = DaftarColors.Paper,
            ),
        )
        MonoLabel(name, color = DaftarColors.GoldSoft, fontSize = 9, letterSpacing = 0.1)
    }
}

@Composable
fun ConfirmRow(label: String, value: String, aside: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(DaftarColors.PaperSoft)
            .border(1.dp, DaftarColors.Line, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            MonoLabel(label, fontSize = 9)
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                style = TextStyle(
                    fontFamily = Inter, fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp, color = DaftarColors.Ink,
                ),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        Text(
            text = aside.uppercase(),
            style = TextStyle(fontFamily = JetBrainsMono, fontSize = 10.sp, color = DaftarColors.Muted),
        )
    }
}

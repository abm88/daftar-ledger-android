package com.daftar.app.ui.feature.fx

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SwapHoriz
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.daftar.app.core.format.Formatters
import com.daftar.app.domain.model.AssetCatalog
import com.daftar.app.ui.common.BigAmountInput
import com.daftar.app.ui.common.FieldBox
import com.daftar.app.ui.common.FieldTextInput
import com.daftar.app.ui.common.IconSquareButton
import com.daftar.app.ui.common.MonoLabel
import com.daftar.app.ui.common.SheetHandle
import com.daftar.app.ui.common.SubmitButton
import com.daftar.app.ui.common.sanitizeAmountInput
import com.daftar.app.ui.navigation.DaftarDestinations
import com.daftar.app.ui.theme.DaftarColors
import com.daftar.app.ui.theme.Fraunces
import com.daftar.app.ui.theme.Inter
import com.daftar.app.ui.theme.JetBrainsMono
import com.daftar.app.ui.theme.NotoNaskhArabic
import kotlin.math.abs

private val CurrencyNames = mapOf("USD" to "Dollar", "AFN" to "Afghani", "PKR" to "Rupee")

/** Currency exchange form: pair, amount, canonical rate, result, P&L preview. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FxFormScreen(
    navController: NavController,
    viewModel: FxFormViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val form = state.form

    Column(modifier = Modifier.fillMaxWidth()) {
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
                    "Currency Exchange",
                    style = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 18.sp, color = DaftarColors.Paper),
                )
                Text(
                    "د اسعارو تبادله",
                    style = TextStyle(
                        fontFamily = NotoNaskhArabic, fontSize = 12.sp,
                        color = DaftarColors.Paper.copy(alpha = 0.85f), textDirection = TextDirection.Rtl,
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(
                    Icons.Rounded.Refresh, null,
                    tint = DaftarColors.Muted,
                    modifier = Modifier.size(11.dp),
                )
                MonoLabel("You give → customer gets · د تبادلې اسعار", fontSize = 9)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FxCurrencyCard(
                    code = form.fromCurrency,
                    name = CurrencyNames[form.fromCurrency] ?: form.fromCurrency,
                    // v18 shows the currency symbol with the drawer amount.
                    detail = Formatters.amount(state.availableCash, form.fromCurrency) + " " +
                        AssetCatalog.symbolFor(form.fromCurrency) + " in drawer",
                    highlight = true,
                    modifier = Modifier.weight(1f),
                ) { viewModel.openPicker(FxLeg.FROM) }
                Box(
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(DaftarColors.Ink)
                        .clickable(onClick = viewModel::swapPair),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.SwapHoriz, "Swap currencies", tint = DaftarColors.GoldSoft, modifier = Modifier.size(20.dp))
                }
                FxCurrencyCard(
                    code = form.toCurrency,
                    name = CurrencyNames[form.toCurrency] ?: form.toCurrency,
                    // v18 shows the destination currency's symbol here.
                    detail = AssetCatalog.symbolFor(form.toCurrency),
                    highlight = false,
                    modifier = Modifier.weight(1f),
                ) { viewModel.openPicker(FxLeg.TO) }
            }

            Spacer(Modifier.height(18.dp))
            BigAmountInput(
                prefixSymbol = AssetCatalog.symbolFor(form.fromCurrency),
                value = form.fromAmountText,
                onValueChange = viewModel::setAmount,
                currency = form.fromCurrency,
                label = "Amount · څومره",
                error = !state.sufficientCash,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (state.sufficientCash) {
                    "Available: ${Formatters.amount(state.availableCash, form.fromCurrency)} ${form.fromCurrency}"
                } else {
                    "⚠ Only ${Formatters.amount(state.availableCash, form.fromCurrency)} ${form.fromCurrency} available"
                },
                style = TextStyle(
                    fontFamily = JetBrainsMono,
                    fontSize = 10.sp,
                    color = if (state.sufficientCash) DaftarColors.Muted else DaftarColors.Red,
                ),
            )

            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(
                    Icons.Rounded.Balance, null,
                    tint = DaftarColors.Muted,
                    modifier = Modifier.size(11.dp),
                )
                MonoLabel("Today's rate · د نرخ", fontSize = 9)
            }
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(DaftarColors.PaperSoft)
                    .border(1.dp, DaftarColors.LineStrong, RoundedCornerShape(14.dp))
                    .padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "1 ${state.pairBase} =",
                        style = TextStyle(
                            fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold,
                            fontSize = 14.sp, color = DaftarColors.InkSoft,
                        ),
                    )
                    Spacer(Modifier.width(10.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (form.rateText.isEmpty()) {
                            Text(
                                "0",
                                style = TextStyle(
                                    fontFamily = Fraunces, fontWeight = FontWeight.Medium,
                                    fontSize = 24.sp, color = DaftarColors.MutedLight,
                                ),
                            )
                        }
                        BasicTextField(
                            value = form.rateText,
                            onValueChange = viewModel::setRate,
                            textStyle = TextStyle(
                                fontFamily = Fraunces, fontWeight = FontWeight.Medium,
                                fontSize = 24.sp, color = DaftarColors.Ink,
                            ),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = state.pairQuote,
                        style = TextStyle(
                            fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold,
                            fontSize = 14.sp, color = DaftarColors.InkSoft,
                        ),
                    )
                }
                if (state.marketRate != null) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(DaftarColors.Green.copy(alpha = 0.1f))
                            .clickable { viewModel.useMarketRate() }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(Icons.Rounded.Refresh, null, tint = DaftarColors.Green, modifier = Modifier.size(10.dp))
                        Text(
                            text = "Use market rate: ${state.marketRateText}",
                            style = TextStyle(
                                fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp, color = DaftarColors.Green,
                            ),
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            // Result — what the customer gets
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (state.computedToAmount > 0) DaftarColors.Ink else DaftarColors.PaperDeep)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MonoLabel(
                    "Customer gets · ترلاسه کوي",
                    color = if (state.computedToAmount > 0) DaftarColors.GoldSoft else DaftarColors.Muted,
                    fontSize = 9,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = if (state.computedToAmount > 0) Formatters.amount(state.computedToAmount, form.toCurrency) else "—",
                        style = TextStyle(
                            fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 30.sp,
                            color = if (state.computedToAmount > 0) DaftarColors.Paper else DaftarColors.MutedLight,
                        ),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = form.toCurrency,
                        style = TextStyle(
                            fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                            letterSpacing = 0.1.em,
                            color = if (state.computedToAmount > 0) DaftarColors.GoldSoft else DaftarColors.Muted,
                        ),
                        modifier = Modifier.padding(bottom = 5.dp),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = state.formulaText ?: "Enter amount and rate to see result",
                    style = TextStyle(
                        fontFamily = JetBrainsMono, fontSize = 10.sp,
                        color = if (state.computedToAmount > 0) DaftarColors.MutedLight else DaftarColors.Muted,
                    ),
                )
            }

            // P&L preview
            val pnl = state.projectedPnl
            if (pnl != null) {
                Spacer(Modifier.height(12.dp))
                val profit = pnl > 0.5
                val loss = pnl < -0.5
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            when {
                                profit -> DaftarColors.Green.copy(alpha = 0.1f)
                                loss -> DaftarColors.Red.copy(alpha = 0.1f)
                                else -> DaftarColors.PaperDeep
                            },
                        )
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = when {
                                profit -> "You make profit"
                                loss -> "You take a loss"
                                else -> "Break-even"
                            },
                            style = TextStyle(
                                fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                                color = when {
                                    profit -> DaftarColors.Green
                                    loss -> DaftarColors.Red
                                    else -> DaftarColors.Muted
                                },
                            ),
                        )
                        Text(
                            text = when {
                                profit -> "ګټه کوئ"
                                loss -> "زیان کوئ"
                                else -> "مساوي"
                            },
                            style = TextStyle(
                                fontFamily = NotoNaskhArabic, fontSize = 11.sp,
                                color = DaftarColors.Muted, textDirection = TextDirection.Rtl,
                            ),
                        )
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = (if (pnl >= 0) "+" else "−") + Formatters.number(abs(pnl)),
                            style = TextStyle(
                                fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 20.sp,
                                color = when {
                                    profit -> DaftarColors.Green
                                    loss -> DaftarColors.Red
                                    else -> DaftarColors.Muted
                                },
                            ),
                        )
                        Text(
                            text = " AFN",
                            style = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, color = DaftarColors.Muted),
                            modifier = Modifier.padding(bottom = 3.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            FieldBox("Note · یادداشت (optional)", modifier = Modifier.fillMaxWidth()) {
                FieldTextInput(
                    form.note, { text -> viewModel.update { it.copy(note = text) } },
                    "e.g. Walk-in customer · morning batch",
                )
            }
            Spacer(Modifier.height(20.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(DaftarColors.Paper)
                .padding(horizontal = 18.dp, vertical = 12.dp)
                .navigationBarsPadding(),
        ) {
            SubmitButton(
                label = "Review Trade · کتنه",
                icon = Icons.Rounded.Check,
                container = DaftarColors.Copper,
                enabled = state.canSubmit,
                onClick = viewModel::review,
            )
        }
    }

    // Currency picker sheet
    if (form.picker != FxLeg.NONE) {
        ModalBottomSheet(
            onDismissRequest = viewModel::closePicker,
            containerColor = DaftarColors.Paper,
            dragHandle = { SheetHandle() },
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                Text(
                    "Choose currency",
                    style = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 18.sp, color = DaftarColors.Ink),
                )
                Spacer(Modifier.height(12.dp))
                listOf("USD" to "US Dollar", "AFN" to "Afghan Afghani", "PKR" to "Pakistan Rupee").forEach { (code, name) ->
                    val selected = if (form.picker == FxLeg.FROM) form.fromCurrency == code else form.toCurrency == code
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { viewModel.pickCurrency(code) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(DaftarColors.Ink),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                code,
                                style = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = DaftarColors.Paper),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(name, style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = DaftarColors.Ink))
                            Text(
                                "Cash on hand: ${Formatters.amount(state.drawerBalances[code] ?: 0.0, code)}",
                                style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp, color = DaftarColors.Muted),
                            )
                        }
                        if (selected) {
                            Icon(Icons.Rounded.Check, null, tint = DaftarColors.Green, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }

    // Confirm trade sheet
    if (form.confirming) {
        ModalBottomSheet(
            onDismissRequest = viewModel::backToForm,
            containerColor = DaftarColors.Paper,
            dragHandle = { SheetHandle() },
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
            ) {
                Text("Review trade", style = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 20.sp, color = DaftarColors.Ink))
                Text(
                    "د معاملې بیاکتنه",
                    style = TextStyle(fontFamily = NotoNaskhArabic, fontSize = 12.sp, color = DaftarColors.Muted, textDirection = TextDirection.Rtl),
                )
                Spacer(Modifier.height(14.dp))

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
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            MonoLabel("Give", color = DaftarColors.ShortRed, fontSize = 9, letterSpacing = 0.15)
                            Text(
                                "${Formatters.amount(state.amount, form.fromCurrency)} ${form.fromCurrency}",
                                style = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = DaftarColors.Paper),
                            )
                        }
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = DaftarColors.Gold, modifier = Modifier.size(18.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            MonoLabel("Get", color = DaftarColors.LongGreen, fontSize = 9, letterSpacing = 0.15)
                            Text(
                                "${Formatters.amount(state.computedToAmount, form.toCurrency)} ${form.toCurrency}",
                                style = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = DaftarColors.Paper),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = DaftarColors.Paper.copy(alpha = 0.15f))
                    Spacer(Modifier.height(10.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        MonoLabel("Rate", color = DaftarColors.GoldSoft, fontSize = 9, letterSpacing = 0.25)
                        Text(
                            "1 ${state.pairBase} = ${form.rateText} ${state.pairQuote}",
                            style = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 24.sp, color = DaftarColors.Paper),
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                FxConfirmRow(
                    icon = if (state.isBuy) Icons.AutoMirrored.Rounded.TrendingUp else Icons.AutoMirrored.Rounded.TrendingDown,
                    tint = if (state.isBuy) DaftarColors.Green else DaftarColors.Red,
                    label = "Trade direction",
                    value = if (state.isBuy) "Buy ${form.toCurrency}" else "Sell ${form.fromCurrency}",
                    aside = if (state.isBuy) "BUY" else "SELL",
                )
                FxConfirmRow(
                    icon = Icons.Rounded.ArrowUpward,
                    tint = DaftarColors.Red,
                    label = "Cash out (${form.fromCurrency})",
                    value = Formatters.amount(state.amount, form.fromCurrency),
                    aside = "AUTO",
                )
                FxConfirmRow(
                    icon = Icons.Rounded.ArrowDownward,
                    tint = DaftarColors.Green,
                    label = "Cash in (${form.toCurrency})",
                    value = Formatters.amount(state.computedToAmount, form.toCurrency),
                    aside = "AUTO",
                )
                val pnl2 = state.projectedPnl
                if (pnl2 != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (pnl2 >= 0) DaftarColors.Green else DaftarColors.Red)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            MonoLabel("Realized P&L (AFN)", color = Color.White.copy(alpha = 0.75f), fontSize = 9)
                            Text(
                                (if (pnl2 >= 0) "+" else "−") + Formatters.number(abs(pnl2)) + " AFN",
                                style = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = DaftarColors.Paper),
                            )
                        }
                        MonoLabel(if (pnl2 >= 0) "Profit" else "Loss", color = Color.White.copy(alpha = 0.75f), fontSize = 9)
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // v18 gives Edit a back arrow and both buttons equal widths.
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.5.dp, DaftarColors.Ink, RoundedCornerShape(14.dp))
                            .clickable(onClick = viewModel::backToForm)
                            .padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack, null,
                            tint = DaftarColors.Ink,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "EDIT",
                            style = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.08.em, color = DaftarColors.Ink),
                        )
                    }
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(DaftarColors.Green)
                            .clickable {
                                viewModel.confirmTrade {
                                    navController.popBackStack()
                                    navController.navigate(DaftarDestinations.FX_LEDGER)
                                }
                            }
                            .padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Check, null, tint = DaftarColors.Paper, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "CONFIRM TRADE",
                            style = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.08.em, color = DaftarColors.Paper),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FxCurrencyCard(
    code: String,
    name: String,
    detail: String,
    highlight: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (highlight) DaftarColors.PaperSoft else DaftarColors.PaperDeep)
            .border(
                1.5.dp,
                if (highlight) DaftarColors.Copper else DaftarColors.LineStrong,
                RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = code,
            style = TextStyle(
                fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold,
                fontSize = 18.sp, letterSpacing = 0.05.em, color = DaftarColors.Ink,
            ),
        )
        Spacer(Modifier.height(2.dp))
        Text(text = name, style = TextStyle(fontFamily = Inter, fontSize = 11.sp, color = DaftarColors.Muted))
        Spacer(Modifier.height(4.dp))
        Text(
            text = detail,
            style = TextStyle(fontFamily = JetBrainsMono, fontSize = 9.sp, color = DaftarColors.Muted),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FxConfirmRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    label: String,
    value: String,
    aside: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(DaftarColors.PaperSoft)
            .border(1.dp, DaftarColors.Line, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(13.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            MonoLabel(label, fontSize = 9)
            Spacer(Modifier.height(2.dp))
            Text(
                value,
                style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = DaftarColors.Ink),
            )
        }
        MonoLabel(aside, fontSize = 10, letterSpacing = 0.1)
    }
}

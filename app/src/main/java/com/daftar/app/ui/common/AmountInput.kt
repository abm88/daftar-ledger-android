package com.daftar.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.daftar.app.ui.theme.DaftarColors
import com.daftar.app.ui.theme.Fraunces
import com.daftar.app.ui.theme.JetBrainsMono

/** Keeps only digits and at most one decimal point with two decimals. */
fun sanitizeAmountInput(raw: String): String {
    val filtered = raw.filter { it.isDigit() || it == '.' }
    val firstDot = filtered.indexOf('.')
    val cleaned = if (firstDot == -1) filtered
    else filtered.substring(0, firstDot + 1) + filtered.substring(firstDot + 1).replace(".", "")
    return if (cleaned.contains('.')) {
        val (whole, decimals) = cleaned.split('.', limit = 2)
        "$whole.${decimals.take(2)}"
    } else cleaned
}

/** The big serif amount entry block used by all money forms. */
@Composable
fun BigAmountInput(
    value: String,
    onValueChange: (String) -> Unit,
    currency: String,
    modifier: Modifier = Modifier,
    label: String = "Amount · type amount",
    error: Boolean = false,
    prefixSymbol: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            // v18 tints the whole box red on error/insufficient states.
            .background(if (error) DaftarColors.Red.copy(alpha = 0.06f) else DaftarColors.PaperSoft)
            .border(
                1.5.dp,
                if (error) DaftarColors.Red else DaftarColors.LineStrong,
                RoundedCornerShape(16.dp),
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        MonoLabel(label, fontSize = 9, letterSpacing = 0.15)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (prefixSymbol != null) {
                Text(
                    text = prefixSymbol,
                    style = TextStyle(
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = DaftarColors.Muted,
                    ),
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(
                        text = "0",
                        style = TextStyle(
                            fontFamily = Fraunces,
                            fontWeight = FontWeight.Medium,
                            fontSize = 32.sp,
                            color = DaftarColors.MutedLight,
                        ),
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = { onValueChange(sanitizeAmountInput(it)) },
                    textStyle = TextStyle(
                        fontFamily = Fraunces,
                        fontWeight = FontWeight.Medium,
                        fontSize = 32.sp,
                        letterSpacing = (-0.01).em,
                        color = if (error) DaftarColors.Red else DaftarColors.Ink,
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                text = currency,
                style = TextStyle(
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 0.1.em,
                    color = DaftarColors.Muted,
                ),
            )
        }
    }
}

/** Three-way USD / AFN / PKR selector used by the money forms. */
@Composable
fun CurrencySwitcher(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    selectedColor: Color = DaftarColors.Ink,
) {
    SegmentedSwitcher(modifier = modifier) {
        listOf("USD" to "$", "AFN" to "؋", "PKR" to "₨").forEach { (code, symbol) ->
            SegmentButton(
                selected = selected == code,
                onClick = { onSelect(code) },
                selectedColor = selectedColor,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        text = code,
                        style = TextStyle(
                            fontFamily = JetBrainsMono,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 0.05.em,
                            color = if (selected == code) DaftarColors.Paper else DaftarColors.Muted,
                        ),
                    )
                    Text(
                        text = symbol,
                        style = TextStyle(
                            fontSize = 12.sp,
                            color = if (selected == code) DaftarColors.GoldSoft else DaftarColors.MutedLight,
                        ),
                    )
                }
            }
        }
    }
}

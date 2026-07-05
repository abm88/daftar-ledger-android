package com.daftar.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.daftar.app.core.format.Formatters
import com.daftar.app.domain.model.AssetCatalog
import com.daftar.app.domain.model.MoneyByCurrency
import com.daftar.app.ui.theme.DaftarColors
import com.daftar.app.ui.theme.Fraunces
import com.daftar.app.ui.theme.JetBrainsMono
import kotlin.math.abs

/** Right-aligned stack of signed per-currency amounts (list rows). */
@Composable
fun PositionLines(position: MoneyByCurrency, flatLabel: String = "FLAT") {
    Column(horizontalAlignment = Alignment.End) {
        val open = position.openCurrencies()
        if (open.isEmpty()) {
            Text(
                text = flatLabel,
                style = TextStyle(
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = DaftarColors.Muted,
                ),
            )
        } else {
            open.forEach { cur ->
                val amt = position[cur]
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = (if (amt > 0) "+" else "−") + Formatters.number(amt),
                        style = TextStyle(
                            fontFamily = JetBrainsMono,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            color = if (amt > 0) DaftarColors.Green else DaftarColors.Red,
                        ),
                    )
                    Text(
                        text = " $cur",
                        style = TextStyle(
                            fontFamily = JetBrainsMono,
                            fontSize = 9.sp,
                            letterSpacing = 0.05.em,
                            color = DaftarColors.Muted,
                        ),
                    )
                }
            }
        }
    }
}

/**
 * A three-cell balance strip on a dark card: per-currency signed amount with a
 * status caption below (LONG/SHORT, OWES YOU/YOU OWE, ON DEPOSIT…).
 */
@Composable
fun DarkBalanceGrid(
    position: MoneyByCurrency,
    statusFor: (Double) -> String,
    modifier: Modifier = Modifier,
    accent: androidx.compose.ui.graphics.Color = DaftarColors.GoldSoft,
) {
    Row(modifier = modifier) {
        AssetCatalog.LEDGER_CURRENCIES.forEach { cur ->
            val amt = position[cur]
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = cur,
                    style = TextStyle(
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 9.sp,
                        letterSpacing = 0.2.em,
                        color = accent,
                    ),
                )
                Text(
                    text = Formatters.signPrefix(amt, 0.5) + Formatters.number(abs(amt)),
                    style = TextStyle(
                        fontFamily = Fraunces,
                        fontWeight = FontWeight.Medium,
                        fontSize = 17.sp,
                        color = when {
                            amt > 0.5 -> DaftarColors.LongGreen
                            amt < -0.5 -> DaftarColors.ShortRed
                            else -> DaftarColors.MutedLight
                        },
                    ),
                    modifier = Modifier.padding(top = 3.dp),
                )
                Text(
                    text = statusFor(amt).uppercase(),
                    style = TextStyle(
                        fontFamily = JetBrainsMono,
                        fontSize = 8.sp,
                        letterSpacing = 0.15.em,
                        color = DaftarColors.MutedLight,
                    ),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

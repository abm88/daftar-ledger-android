package com.daftar.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.daftar.app.core.format.Formatters
import com.daftar.app.domain.model.CustomerTxType
import com.daftar.app.domain.model.FxSide
import com.daftar.app.domain.model.HawalaStatus
import com.daftar.app.domain.model.HawalaType
import com.daftar.app.domain.model.LedgerEntry
import com.daftar.app.ui.common.MonoLabel
import com.daftar.app.ui.theme.DaftarColors
import com.daftar.app.ui.theme.Fraunces
import com.daftar.app.ui.theme.Inter
import com.daftar.app.ui.theme.JetBrainsMono
import kotlin.math.abs

/** Presentation payload derived from a domain ledger entry. */
private data class LedgerRowStyle(
    val icon: ImageVector,
    val tint: Color,
    val background: Color,
    val title: String,
    val subtitle: String,
    val amountPrefix: String,
    val amountColor: Color,
    val tag: String?,
    val tagColor: Color?,
    val pending: Boolean,
)

private fun styleFor(entry: LedgerEntry): LedgerRowStyle = when (entry) {
    is LedgerEntry.SettlementEntry -> LedgerRowStyle(
        icon = Icons.Rounded.Balance,
        tint = DaftarColors.CopperDeep,
        background = DaftarColors.Gold.copy(alpha = 0.12f),
        title = "Settlement · ${entry.partner.shortName}",
        subtitle = entry.hawala.note ?: "Position offset",
        amountPrefix = if (entry.hawala.amount >= 0) "+" else "−",
        amountColor = DaftarColors.CopperDeep,
        tag = null,
        tagColor = null,
        pending = false,
    )
    is LedgerEntry.HawalaEntry -> {
        val send = entry.hawala.type == HawalaType.SEND
        LedgerRowStyle(
            icon = if (send) Icons.AutoMirrored.Rounded.TrendingUp else Icons.AutoMirrored.Rounded.TrendingDown,
            tint = if (send) DaftarColors.Red else DaftarColors.Green,
            background = (if (send) DaftarColors.Red else DaftarColors.Green).copy(alpha = 0.1f),
            title = (if (send) "Sent hawala · " else "Received hawala · ") + entry.partner.shortName,
            subtitle = buildString {
                append("${entry.hawala.fromCity.code} → ${entry.hawala.toCity.code}")
                append(" · ${entry.hawala.senderName} → ${entry.hawala.receiverName}")
                if (entry.hawala.status == HawalaStatus.PENDING) append(" · pending")
            },
            amountPrefix = "",
            amountColor = DaftarColors.Ink,
            tag = "HAWALA",
            tagColor = DaftarColors.Blue,
            pending = entry.isPending,
        )
    }
    is LedgerEntry.CustomerTxEntry -> {
        val credit = entry.direction > 0
        val linked = entry.isHawalaLinked
        val label = when (entry.tx.type) {
            CustomerTxType.DEPOSIT -> "Deposit"
            CustomerTxType.WITHDRAWAL -> "Withdrawal"
            CustomerTxType.CHARGE -> "Charge"
            CustomerTxType.CREDIT -> "Credit advance"
            CustomerTxType.OPENING -> "Opening"
        }
        LedgerRowStyle(
            icon = when {
                linked -> Icons.AutoMirrored.Rounded.Send
                credit -> Icons.Rounded.ArrowDownward
                else -> Icons.Rounded.ArrowUpward
            },
            tint = when {
                linked -> DaftarColors.CopperDeep
                credit -> DaftarColors.Green
                else -> DaftarColors.Red
            },
            background = when {
                linked -> DaftarColors.Gold.copy(alpha = 0.1f)
                credit -> DaftarColors.Green.copy(alpha = 0.1f)
                else -> DaftarColors.Red.copy(alpha = 0.1f)
            },
            title = (if (linked) "Hawala debit · " else "$label · ") + entry.customer.name,
            subtitle = entry.tx.note
                ?: "Customer account · ${entry.customer.city.displayName}",
            amountPrefix = if (credit) "+" else "−",
            amountColor = if (credit) DaftarColors.Green else DaftarColors.Red,
            tag = if (credit) "CR" else "DR",
            tagColor = if (credit) DaftarColors.Green else DaftarColors.Red,
            pending = false,
        )
    }
    is LedgerEntry.FxEntry -> {
        val trade = entry.trade
        val sell = trade.side == FxSide.SELL
        val realized = trade.realizedPnlAfn
        val plSuffix = if (realized != null && abs(realized) >= 0.5) {
            " · " + (if (realized >= 0) "+" else "−") + Formatters.number(realized) +
                " AFN " + (if (realized >= 0) "profit" else "loss")
        } else ""
        LedgerRowStyle(
            icon = Icons.Rounded.Refresh,
            tint = if (sell) DaftarColors.Red else DaftarColors.Green,
            background = (if (sell) DaftarColors.Red else DaftarColors.Green).copy(alpha = 0.1f),
            title = (if (sell) "Sold " else "Bought ") + "${trade.fromCurrency} → ${trade.toCurrency}",
            subtitle = "${Formatters.amount(trade.fromAmount, trade.fromCurrency)} " +
                "${trade.fromCurrency} @ ${trade.rate}$plSuffix",
            amountPrefix = "",
            amountColor = when {
                realized == null -> DaftarColors.Ink
                realized >= 0 -> DaftarColors.Green
                else -> DaftarColors.Red
            },
            tag = "EXCHANGE",
            tagColor = DaftarColors.Copper,
            pending = false,
        )
    }
}

@Composable
fun LedgerEntryRow(entry: LedgerEntry, onOpen: (LedgerEntry) -> Unit) {
    val style = styleFor(entry)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen(entry) }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(style.background),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = style.icon,
                contentDescription = null,
                tint = style.tint,
                modifier = Modifier.size(16.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = style.title,
                    style = TextStyle(
                        fontFamily = Inter,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = DaftarColors.Ink,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (style.pending) {
                    Box(
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(DaftarColors.Copper)
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    ) {
                        Text(
                            text = "PENDING",
                            style = TextStyle(
                                fontFamily = JetBrainsMono,
                                fontWeight = FontWeight.Bold,
                                fontSize = 7.sp,
                                letterSpacing = 0.1.em,
                                color = DaftarColors.Paper,
                            ),
                        )
                    }
                }
            }
            Text(
                text = style.subtitle,
                style = TextStyle(fontFamily = Inter, fontSize = 11.sp, color = DaftarColors.Muted),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = style.amountPrefix + Formatters.amount(entry.amount, entry.currency),
                style = TextStyle(
                    fontFamily = Fraunces,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    color = style.amountColor,
                ),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                MonoLabel(entry.currency, fontSize = 9, letterSpacing = 0.1)
                if (style.tag != null && style.tagColor != null) {
                    Text(
                        text = style.tag,
                        style = TextStyle(
                            fontFamily = JetBrainsMono,
                            fontWeight = FontWeight.Bold,
                            fontSize = 7.sp,
                            letterSpacing = 0.08.em,
                            color = style.tagColor,
                        ),
                    )
                }
            }
            Text(
                text = Formatters.timeLabel(entry.timestampMillis),
                style = TextStyle(
                    fontFamily = JetBrainsMono,
                    fontSize = 9.sp,
                    color = DaftarColors.MutedLight,
                ),
            )
        }
    }
}

@Composable
fun LedgerDayHeader(label: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MonoLabel(label, fontSize = 9)
        HorizontalDivider(modifier = Modifier.weight(1f), color = DaftarColors.Line)
        MonoLabel(count.toString(), fontSize = 9, color = DaftarColors.CopperDeep)
    }
}

/** Renders entries grouped by day into an existing LazyColumn. */
fun LazyListScope.ledgerFeedItems(
    entries: List<LedgerEntry>,
    todayStartMillis: Long,
    onOpen: (LedgerEntry) -> Unit,
) {
    val groups = entries.groupBy { entry ->
        val delta = entry.timestampMillis - todayStartMillis
        // Bucket by day offset relative to today's midnight.
        Math.floorDiv(delta, 86_400_000L)
    }
    groups.keys.sortedDescending().forEach { dayOffset ->
        val group = groups.getValue(dayOffset)
        val dayStart = todayStartMillis + dayOffset * 86_400_000L
        item(key = "day_$dayOffset") {
            LedgerDayHeader(
                label = Formatters.relativeDayLabel(dayStart, todayStartMillis),
                count = group.size,
            )
        }
        items(count = group.size, key = { idx -> feedKey(group[idx]) }) { idx ->
            LedgerEntryRow(group[idx], onOpen)
        }
    }
}

private fun feedKey(entry: LedgerEntry): String = when (entry) {
    is LedgerEntry.HawalaEntry -> "h_${entry.hawala.id}"
    is LedgerEntry.SettlementEntry -> "s_${entry.hawala.id}"
    is LedgerEntry.CustomerTxEntry -> "c_${entry.tx.id}"
    is LedgerEntry.FxEntry -> "f_${entry.trade.id}"
}

package com.daftar.app.ui.feature.statements

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.daftar.app.domain.model.ShopProfile
import com.daftar.app.ui.common.LocalToaster
import com.daftar.app.ui.common.MonoLabel
import com.daftar.app.ui.common.ToastIcon
import com.daftar.app.ui.theme.DaftarColors
import com.daftar.app.ui.theme.Fraunces
import com.daftar.app.ui.theme.Inter
import com.daftar.app.ui.theme.JetBrainsMono
import com.daftar.app.ui.theme.NotoNaskhArabic

/** Ledger-paper masthead: brand mark, shop identity, document title. */
@Composable
fun StatementMasthead(profile: ShopProfile, docTitle: String, pashtoLine: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(DaftarColors.Ink),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "د",
                    style = TextStyle(fontFamily = NotoNaskhArabic, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = DaftarColors.Paper),
                )
            }
            Column {
                Text(
                    "${profile.shopName} · ${profile.ownerName}",
                    style = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = DaftarColors.Ink),
                )
                MonoLabel(
                    "Currency & Hawala · ${profile.city.displayName} · ${profile.registration}",
                    fontSize = 8, letterSpacing = 0.15,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        MonoLabel(docTitle, fontSize = 11, letterSpacing = 0.15)
        Spacer(Modifier.height(3.dp))
        Text(
            pashtoLine,
            style = TextStyle(
                fontFamily = NotoNaskhArabic, fontSize = 13.sp,
                color = DaftarColors.InkSoft, textDirection = TextDirection.Rtl,
            ),
        )
        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = DaftarColors.Ink, thickness = 2.dp)
    }
}

@Composable
fun StatementMetaRow(
    leftLabel: String, leftValue: String, leftSub: String,
    rightLabel: String, rightValue: String, rightSub: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            MonoLabel(leftLabel, fontSize = 9)
            Spacer(Modifier.height(3.dp))
            Text(leftValue, style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = DaftarColors.Ink))
            Spacer(Modifier.height(2.dp))
            Text(leftSub, style = TextStyle(fontFamily = JetBrainsMono, fontSize = 10.sp, color = DaftarColors.Muted))
        }
        Column(horizontalAlignment = Alignment.End) {
            MonoLabel(rightLabel, fontSize = 9)
            Spacer(Modifier.height(3.dp))
            Text(rightValue, style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = DaftarColors.Ink))
            Spacer(Modifier.height(2.dp))
            Text(rightSub, style = TextStyle(fontFamily = JetBrainsMono, fontSize = 10.sp, color = DaftarColors.Muted))
        }
    }
}

data class StatementSummaryCell(
    val heading: String,
    val amount: String,
    val status: String,
    val color: Color,
)

@Composable
fun StatementSummary(cells: List<StatementSummaryCell>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DaftarColors.PaperDeep.copy(alpha = 0.5f))
            .border(1.dp, DaftarColors.LineStrong, RoundedCornerShape(8.dp))
            .padding(vertical = 12.dp),
    ) {
        cells.forEach { cell ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MonoLabel(cell.heading, fontSize = 9, letterSpacing = 0.2)
                Spacer(Modifier.height(4.dp))
                Text(
                    cell.amount,
                    style = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = cell.color),
                )
                Spacer(Modifier.height(3.dp))
                MonoLabel(cell.status, fontSize = 8, letterSpacing = 0.15)
            }
        }
    }
}

@Composable
fun StatementFooter(issuedLabel: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(20.dp))
        HorizontalDivider(color = DaftarColors.Ink, thickness = 2.dp)
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MonoLabel("Page 1 of 1", fontSize = 9)
            MonoLabel("Generated $issuedLabel", fontSize = 9)
        }
        Spacer(Modifier.height(16.dp))
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .rotate(-12f)
                    .border(2.5.dp, DaftarColors.Copper, androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    // v18 stamp uses Western digits for the year.
                    "مهر صرافي\nKBL · 2026",
                    style = TextStyle(
                        fontFamily = NotoNaskhArabic, fontWeight = FontWeight.Bold,
                        fontSize = 11.sp, color = DaftarColors.Copper, textAlign = TextAlign.Center,
                    ),
                )
            }
        }
    }
}

/**
 * PDF / Print / WhatsApp action bar shared by all statements. When a
 * [printSpec] is supplied, PDF and Print open the system print dialog with
 * the v18-style A4 document ("Save as PDF" lives there, matching the
 * prototype's window.print flow).
 */
@Composable
fun StatementActions(
    modifier: Modifier = Modifier,
    printSpec: (() -> com.daftar.app.core.print.StatementPrintSpec)? = null,
) {
    val toaster = LocalToaster.current
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(DaftarColors.Paper)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatementActionButton("PDF", Icons.Rounded.Download, DaftarColors.Ink, Modifier.weight(1f)) {
            toaster("Choose \"Save as PDF\" in print dialog", ToastIcon.DOWNLOAD)
            printSpec?.let { com.daftar.app.core.print.StatementPrinter.print(context, it()) }
        }
        StatementActionButton("Print", Icons.Rounded.Print, DaftarColors.InkSoft, Modifier.weight(1f)) {
            toaster("Print dialog opened", ToastIcon.PRINTER)
            printSpec?.let { com.daftar.app.core.print.StatementPrinter.print(context, it()) }
        }
        StatementActionButton("WhatsApp", Icons.Rounded.ChatBubble, DaftarColors.WhatsApp, Modifier.weight(1f)) {
            // TODO(backend): real WhatsApp share needs a share intent + rendered
            // document; toast-only in the prototype as well.
            toaster("Statement shared via WhatsApp", ToastIcon.MESSAGE)
        }
    }
}

@Composable
private fun StatementActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    background: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = DaftarColors.Paper, modifier = Modifier.size(14.dp))
        Spacer(Modifier.size(6.dp))
        Text(
            label.uppercase(),
            style = TextStyle(
                fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold,
                fontSize = 11.sp, letterSpacing = 0.05.em, color = DaftarColors.Paper,
            ),
        )
    }
}

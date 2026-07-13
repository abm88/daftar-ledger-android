package com.daftar.app.ui.feature.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.daftar.app.ui.common.SheetHandle
import com.daftar.app.ui.theme.DaftarColors
import com.daftar.app.ui.theme.Fraunces
import com.daftar.app.ui.theme.JetBrainsMono
import com.daftar.app.ui.theme.NotoNaskhArabic

enum class NewEntryOption(
    val label: String,
    val pashto: String,
    val sub: String,
    val icon: ImageVector,
    val background: Color,
) {
    RECEIVED("You Received", "ترلاسه کړل", "Customer paid you", Icons.Rounded.ArrowDownward, DaftarColors.Green),
    GAVE("You Gave", "ورکړل", "You handed cash", Icons.Rounded.ArrowUpward, DaftarColors.Red),
    FX("Currency Exchange", "اسعارو تبادله", "Buy or sell FX", Icons.Rounded.Refresh, DaftarColors.Copper),
    HAWALA("Send Hawala", "حواله ولېږه", "Money transfer", Icons.AutoMirrored.Rounded.Send, DaftarColors.Ink),
}

/** "What do you want to log?" — the unified entry point for all four flows. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewEntryChooserSheet(
    onDismiss: () -> Unit,
    onChoose: (NewEntryOption) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DaftarColors.Paper,
        dragHandle = { SheetHandle() },
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
            Text(
                text = "New Entry",
                style = MaterialTheme.typography.headlineMedium,
                color = DaftarColors.Ink,
            )
            Text(
                text = "نوې لیکنه · what do you want to log?",
                style = MaterialTheme.typography.bodyMedium,
                color = DaftarColors.Muted,
            )
            Spacer(modifier = Modifier.height(14.dp))
            val options = NewEntryOption.entries
            options.chunked(2).forEach { rowOptions ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    rowOptions.forEach { option ->
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 120.dp)
                                .shadow(6.dp, RoundedCornerShape(14.dp))
                                .clip(RoundedCornerShape(14.dp))
                                .background(option.background)
                                // v18 decorates each tile with a soft radial
                                // highlight in the top-right corner.
                                .drawBehind {
                                    drawCircle(
                                        brush = Brush.radialGradient(
                                            colors = listOf(Color.White.copy(alpha = 0.18f), Color.Transparent),
                                            center = Offset(size.width, 0f),
                                            radius = size.width * 0.75f,
                                        ),
                                        center = Offset(size.width, 0f),
                                        radius = size.width * 0.75f,
                                    )
                                }
                                .clickable { onChoose(option) }
                                .padding(14.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (option == NewEntryOption.HAWALA) {
                                            DaftarColors.GoldSoft.copy(alpha = 0.2f)
                                        } else {
                                            Color.White.copy(alpha = 0.2f)
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = option.icon,
                                    contentDescription = null,
                                    tint = DaftarColors.Paper,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Column {
                                Text(
                                    text = option.label,
                                    style = TextStyle(
                                        fontFamily = Fraunces,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 16.sp,
                                        color = DaftarColors.Paper,
                                    ),
                                )
                                Text(
                                    text = option.pashto,
                                    style = TextStyle(
                                        fontFamily = NotoNaskhArabic,
                                        fontSize = 11.sp,
                                        color = DaftarColors.Paper.copy(alpha = 0.85f),
                                        textDirection = TextDirection.Rtl,
                                    ),
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = option.sub.uppercase(),
                                    style = TextStyle(
                                        fontFamily = JetBrainsMono,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 9.sp,
                                        letterSpacing = 0.08.em,
                                        color = DaftarColors.Paper.copy(alpha = 0.7f),
                                    ),
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

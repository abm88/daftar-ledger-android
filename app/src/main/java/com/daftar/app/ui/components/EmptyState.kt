package com.daftar.app.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daftar.app.ui.theme.DaftarColors
import com.daftar.app.ui.theme.Fraunces
import com.daftar.app.ui.theme.Inter
import com.daftar.app.ui.theme.NotoNaskhArabic

/** Tint of the icon tile — mirrors the prototype's `tone` option. */
enum class EmptyStateTone(val background: Color, val icon: Color) {
    COPPER(DaftarColors.Copper.copy(alpha = 0.1f), DaftarColors.Copper),
    GREEN(DaftarColors.Green.copy(alpha = 0.1f), DaftarColors.Green),
    MUTED(DaftarColors.PaperDeep, DaftarColors.Muted),
}

/**
 * Rich zero-data state, ported from the prototype's emptyStateHtml (v18):
 * icon in a soft 68dp tile, serif title, RTL Pashto line, muted subtext,
 * and an optional ink CTA button.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    pashto: String? = null,
    sub: String? = null,
    tone: EmptyStateTone = EmptyStateTone.COPPER,
    ctaLabel: String? = null,
    ctaIcon: ImageVector? = null,
    onCta: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(tone.background),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tone.icon,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = TextStyle(
                fontFamily = Fraunces,
                fontWeight = FontWeight.Medium,
                fontSize = 19.sp,
                color = DaftarColors.Ink,
            ),
            textAlign = TextAlign.Center,
        )
        if (pashto != null) {
            Text(
                text = pashto,
                style = TextStyle(
                    fontFamily = NotoNaskhArabic,
                    fontSize = 13.sp,
                    color = DaftarColors.Muted,
                    textDirection = TextDirection.Rtl,
                ),
                modifier = Modifier.padding(top = 4.dp),
                textAlign = TextAlign.Center,
            )
        }
        if (sub != null) {
            Text(
                text = sub,
                style = TextStyle(
                    fontFamily = Inter,
                    fontSize = 12.5.sp,
                    lineHeight = 19.sp,
                    color = DaftarColors.Muted,
                ),
                modifier = Modifier
                    .padding(top = 8.dp)
                    .widthIn(max = 260.dp),
                textAlign = TextAlign.Center,
            )
        }
        if (ctaLabel != null && onCta != null) {
            Row(
                modifier = Modifier
                    .padding(top = 18.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DaftarColors.Ink)
                    .clickable(onClick = onCta)
                    .padding(horizontal = 20.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                if (ctaIcon != null) {
                    Icon(
                        imageVector = ctaIcon,
                        contentDescription = null,
                        tint = DaftarColors.Paper,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Text(
                    text = ctaLabel,
                    style = TextStyle(
                        fontFamily = Fraunces,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = DaftarColors.Paper,
                    ),
                )
            }
        }
    }
}

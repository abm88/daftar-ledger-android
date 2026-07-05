package com.daftar.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daftar.app.ui.common.MonoLabel
import com.daftar.app.ui.theme.DaftarColors
import com.daftar.app.ui.theme.Inter
import com.daftar.app.ui.theme.JetBrainsMono

/** Section title with hairline, used inside detail screens. */
@Composable
fun DetailSectionTitle(title: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 18.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MonoLabel(title)
        HorizontalDivider(modifier = Modifier.weight(1f), color = DaftarColors.Line)
    }
}

/** Card grouping several [DetailRow]s, matching the prototype's detail grid. */
@Composable
fun DetailCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(DaftarColors.PaperSoft)
            .border(1.dp, DaftarColors.Line, RoundedCornerShape(14.dp)),
    ) {
        content()
    }
}

@Composable
fun DetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = DaftarColors.InkSoft,
    iconBackground: Color = DaftarColors.PaperDeep,
    sub: String? = null,
    aside: String? = null,
    valueColor: Color = DaftarColors.Ink,
    background: Color = Color.Transparent,
    labelColor: Color = DaftarColors.Muted,
    subColor: Color = DaftarColors.Muted,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    showDivider: Boolean = true,
) {
    Column(modifier = modifier.background(background)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                leading != null -> leading()
                icon != null -> Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(iconBackground),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(14.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                MonoLabel(label, fontSize = 9, color = labelColor)
                Text(
                    text = value,
                    style = TextStyle(
                        fontFamily = Inter,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = valueColor,
                    ),
                    modifier = Modifier.padding(top = 2.dp),
                )
                if (sub != null) {
                    Text(
                        text = sub,
                        style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp, color = subColor),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            when {
                trailing != null -> trailing()
                aside != null -> Text(
                    text = aside,
                    style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp, color = subColor),
                )
            }
        }
        if (showDivider) HorizontalDivider(color = DaftarColors.Line)
    }
}

package com.daftar.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daftar.app.ui.common.ToastData
import com.daftar.app.ui.theme.DaftarColors
import com.daftar.app.ui.theme.Inter

/** The ink toast pill with a gold keyline. */
@Composable
fun ToastPill(toast: ToastData) {
    Row(
        modifier = Modifier
            .shadow(10.dp, RoundedCornerShape(14.dp)) // v18 gives the pill a soft drop shadow
            .background(DaftarColors.Ink, RoundedCornerShape(14.dp))
            .border(1.dp, DaftarColors.Gold.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = toast.icon.vector,
            contentDescription = null,
            tint = DaftarColors.Paper,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = toast.message,
            style = TextStyle(
                fontFamily = Inter,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                color = DaftarColors.Paper,
            ),
            maxLines = 1, // v18 keeps the pill to a single line
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}

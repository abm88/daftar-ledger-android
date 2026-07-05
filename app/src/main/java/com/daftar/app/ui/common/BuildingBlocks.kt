package com.daftar.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.daftar.app.ui.theme.DaftarColors
import com.daftar.app.ui.theme.Inter
import com.daftar.app.ui.theme.JetBrainsMono

/** Uppercase mono micro-label, the ledger's ubiquitous eyebrow text. */
@Composable
fun MonoLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = DaftarColors.Muted,
    fontSize: Int = 10,
    letterSpacing: Double = 0.2,
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = TextStyle(
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.Bold,
            fontSize = fontSize.sp,
            letterSpacing = letterSpacing.em,
            color = color,
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** "SECTION ---- 12" header row with hairline and optional trailing content. */
@Composable
fun SectionHead(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MonoLabel(title)
        HorizontalDivider(modifier = Modifier.weight(1f), color = DaftarColors.Line)
        trailing?.invoke()
    }
}

@Composable
fun CountPill(count: Int, container: Color = DaftarColors.Copper) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(container)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = count.toString(),
            style = TextStyle(
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                color = DaftarColors.Paper,
            ),
        )
    }
}

/** Rounded filter chip with optional count badge. */
@Composable
fun DaftarFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectedColor: Color = DaftarColors.Ink,
    count: Int? = null,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) selectedColor else DaftarColors.PaperSoft)
            .border(
                1.dp,
                if (selected) selectedColor else DaftarColors.LineStrong,
                RoundedCornerShape(20.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label.uppercase(),
            style = TextStyle(
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 0.08.em,
                color = if (selected) DaftarColors.Paper else DaftarColors.InkSoft,
            ),
        )
        if (count != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (selected) DaftarColors.GoldSoft.copy(alpha = 0.3f)
                        else DaftarColors.PaperDeep,
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = count.toString(),
                    style = TextStyle(
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 9.sp,
                        color = if (selected) DaftarColors.GoldSoft else DaftarColors.Muted,
                    ),
                )
            }
        }
    }
}

/** Search input styled like the prototype's rounded field. */
@Composable
fun DaftarSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(DaftarColors.PaperSoft)
            .border(1.dp, DaftarColors.LineStrong, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            tint = DaftarColors.Muted,
            modifier = Modifier.size(16.dp),
        )
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = TextStyle(fontFamily = Inter, fontSize = 13.sp, color = DaftarColors.Muted),
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(fontFamily = Inter, fontSize = 13.sp, color = DaftarColors.Ink),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Small square icon button on paper (the header buttons). */
@Composable
fun IconSquareButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onDark: Boolean = false,
    showDot: Boolean = false,
    size: Dp = 36.dp,
) {
    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (onDark) DaftarColors.Paper.copy(alpha = 0.1f) else DaftarColors.PaperDeep,
                )
                .then(
                    if (onDark) Modifier
                    else Modifier.border(1.dp, DaftarColors.Line, RoundedCornerShape(10.dp)),
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (onDark) DaftarColors.Paper else DaftarColors.InkSoft,
                modifier = Modifier.size(17.dp),
            )
        }
        if (showDot) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .align(Alignment.TopEnd)
                    .padding(0.dp)
                    .clip(CircleShape)
                    .background(DaftarColors.Red),
            )
        }
    }
}

/** Full-width ink submit button used at the bottom of forms. */
@Composable
fun SubmitButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    container: Color = DaftarColors.Ink,
    icon: ImageVector? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) container else container.copy(alpha = 0.45f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 15.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = DaftarColors.Paper,
                modifier = Modifier.size(15.dp),
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(8.dp))
        }
        Text(
            text = label.uppercase(),
            style = TextStyle(
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 0.08.em,
                color = DaftarColors.Paper,
            ),
        )
    }
}

/** Sheet drag handle bar. */
@Composable
fun SheetHandle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(top = 12.dp, bottom = 16.dp)
            .size(width = 40.dp, height = 4.dp)
            .clip(CircleShape)
            .background(DaftarColors.LineStrong),
    )
}

/** Label + value column used inside field boxes. */
@Composable
fun FieldBox(
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(DaftarColors.PaperSoft)
            .border(1.dp, DaftarColors.LineStrong, RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        MonoLabel(label, fontSize = 9, letterSpacing = 0.15)
        content()
    }
}

/** Plain single-line text input matching the field box styles. */
@Composable
fun FieldTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = TextStyle(
        fontFamily = Inter,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = DaftarColors.Ink,
    ),
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions =
        androidx.compose.foundation.text.KeyboardOptions.Default,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        if (value.isEmpty()) {
            Text(text = placeholder, style = textStyle.copy(color = DaftarColors.MutedLight))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = textStyle,
            singleLine = true,
            keyboardOptions = keyboardOptions,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Segmented switcher (currency picker, sender mode, sub-tabs). */
@Composable
fun SegmentedSwitcher(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DaftarColors.PaperDeep)
            .border(1.dp, DaftarColors.Line, RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

@Composable
fun RowScope.SegmentButton(
    selected: Boolean,
    onClick: () -> Unit,
    selectedColor: Color = DaftarColors.Ink,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) selectedColor else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

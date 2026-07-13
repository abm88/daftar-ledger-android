package com.daftar.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.daftar.app.ui.theme.DaftarColors
import com.daftar.app.ui.theme.Fraunces
import com.daftar.app.ui.theme.JetBrainsMono
import com.daftar.app.ui.theme.NotoNaskhArabic

/** Ink splash with the "د" brand mark, matching the prototype's opening. */
@Composable
fun SplashOverlay() {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val markScale by animateFloatAsState(
        targetValue = if (started) 1f else 0.7f,
        animationSpec = tween(durationMillis = 600),
        label = "splashMark",
    )
    val textAlpha by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = 600, delayMillis = 200),
        label = "splashText",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DaftarColors.Ink),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .scale(markScale)
                .background(DaftarColors.Paper, RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .padding(5.dp)
                    .fillMaxSize()
                    .border(1.5.dp, DaftarColors.Gold, RoundedCornerShape(17.dp)),
            )
            Text(
                text = "د",
                style = TextStyle(
                    fontFamily = NotoNaskhArabic,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 54.sp,
                    color = DaftarColors.Ink,
                ),
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Daftar",
            style = TextStyle(
                fontFamily = Fraunces,
                fontWeight = FontWeight.Medium,
                fontSize = 36.sp,
                letterSpacing = (-0.02).em,
                color = DaftarColors.Paper,
            ),
            modifier = Modifier.graphicsLayer { alpha = textAlpha },
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "FOR SARAFS",
            style = TextStyle(
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                letterSpacing = 0.3.em,
                color = DaftarColors.GoldSoft,
            ),
            modifier = Modifier.graphicsLayer { alpha = textAlpha },
        )
    }
}

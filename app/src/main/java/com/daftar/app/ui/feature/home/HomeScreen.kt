package com.daftar.app.ui.feature.home

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.BusinessCenter
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.daftar.app.domain.model.LedgerEntry
import com.daftar.app.ui.common.IconSquareButton
import com.daftar.app.ui.common.MonoLabel
import com.daftar.app.ui.components.ledgerFeedItems
import com.daftar.app.ui.feature.rates.UpdateRatesSheet
import com.daftar.app.ui.navigation.DaftarDestinations
import com.daftar.app.ui.theme.DaftarColors
import com.daftar.app.ui.theme.Fraunces
import com.daftar.app.ui.theme.Inter
import com.daftar.app.ui.theme.JetBrainsMono
import com.daftar.app.ui.theme.NotoNaskhArabic

/** Home tab: greeting, cash-on-hand card, ledger preview, New Entry FAB. */
@Composable
fun HomeScreen(
    navController: NavController,
    onOpenNewEntry: () -> Unit,
    onViewAllLedger: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var ratesSheetOpen by rememberSaveable { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier.statusBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 120.dp),
        ) {
            item { HomeHeader(state, onSync = viewModel::sync) }

            if (state.setupNeeded) {
                item {
                    SetupWelcomeCard(
                        onClick = { navController.navigate(DaftarDestinations.INITIAL_SETUP) },
                    )
                }
            }

            item {
                CashCard(
                    state = state,
                    onEditRates = { ratesSheetOpen = true },
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                        contentDescription = null,
                        tint = DaftarColors.Muted,
                        modifier = Modifier.size(12.dp),
                    )
                    MonoLabel("General Ledger · عمومي دفتر")
                    HorizontalDivider(modifier = Modifier.weight(1f), color = DaftarColors.Line)
                    if (state.feedTotal > state.feedPreview.size) {
                        Text(
                            text = "View all ${state.feedTotal} →",
                            style = TextStyle(
                                fontFamily = JetBrainsMono,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = DaftarColors.Copper,
                            ),
                            modifier = Modifier.clickable(onClick = onViewAllLedger),
                        )
                    }
                }
            }

            ledgerFeedItems(
                entries = state.feedPreview,
                todayStartMillis = state.todayStartMillis,
                onOpen = { entry -> openLedgerEntry(entry, navController) },
            )

            if (state.feedTotal > state.feedPreview.size) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, DaftarColors.LineStrong, RoundedCornerShape(12.dp))
                            .clickable(onClick = onViewAllLedger)
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                            contentDescription = null,
                            tint = DaftarColors.InkSoft,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = "View full ledger · ${state.feedTotal} entries",
                            style = TextStyle(
                                fontFamily = Inter,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = DaftarColors.InkSoft,
                            ),
                        )
                    }
                }
            }
        }

        NewEntryFab(
            onClick = onOpenNewEntry,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 20.dp),
        )
    }

    if (ratesSheetOpen) {
        UpdateRatesSheet(onDismiss = { ratesSheetOpen = false })
    }
}

fun openLedgerEntry(entry: LedgerEntry, navController: NavController) {
    when (entry) {
        is LedgerEntry.HawalaEntry ->
            navController.navigate(DaftarDestinations.hawalaDetail(entry.hawala.id))
        is LedgerEntry.SettlementEntry ->
            navController.navigate(DaftarDestinations.partnerDetail(entry.partner.id))
        is LedgerEntry.CustomerTxEntry ->
            navController.navigate(DaftarDestinations.customerTxDetail(entry.tx.id))
        is LedgerEntry.FxEntry ->
            navController.navigate(DaftarDestinations.FX_LEDGER)
    }
}

@Composable
private fun HomeHeader(state: HomeUiState, onSync: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "السلام علیکم",
                    style = TextStyle(
                        fontFamily = NotoNaskhArabic,
                        fontSize = 13.sp,
                        color = DaftarColors.Muted,
                        textDirection = TextDirection.Rtl,
                    ),
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(DaftarColors.Ink)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "${state.profile.city.code} · ${state.profile.shopName.uppercase()}",
                        style = TextStyle(
                            fontFamily = JetBrainsMono,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 9.sp,
                            letterSpacing = 0.1.em,
                            color = DaftarColors.Paper,
                        ),
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = state.profile.ownerName,
                style = MaterialTheme.typography.headlineMedium,
                color = DaftarColors.Ink,
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "${state.profile.tagline} · ${state.profile.registration}",
                style = TextStyle(fontFamily = Inter, fontSize = 12.sp, color = DaftarColors.Muted),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconSquareButton(icon = Icons.Rounded.Refresh, onClick = onSync)
            IconSquareButton(icon = Icons.Rounded.Notifications, onClick = {}, showDot = true)
        }
    }
}

@Composable
private fun SetupWelcomeCard(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 14.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(DaftarColors.Ink)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(DaftarColors.Copper),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.BusinessCenter,
                contentDescription = null,
                tint = DaftarColors.Paper,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            MonoLabel("Get started", color = DaftarColors.GoldSoft, fontSize = 9)
            Text(
                text = "Set up your shop",
                style = TextStyle(
                    fontFamily = Fraunces,
                    fontWeight = FontWeight.Medium,
                    fontSize = 17.sp,
                    color = DaftarColors.Paper,
                ),
            )
            Text(
                text = "Enter the cash and metals in your drawer to start trading.",
                style = TextStyle(fontFamily = Inter, fontSize = 11.sp, color = DaftarColors.MutedLight),
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = DaftarColors.GoldSoft,
        )
    }
}

@Composable
private fun CashCard(state: HomeUiState, onEditRates: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(DaftarColors.PaperSoft)
            .border(1.5.dp, DaftarColors.Copper, RoundedCornerShape(20.dp)),
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.BusinessCenter,
                        contentDescription = null,
                        tint = DaftarColors.CopperDeep,
                        modifier = Modifier.size(11.dp),
                    )
                    MonoLabel("Cash on Hand · صندوق", color = DaftarColors.CopperDeep)
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "Last counted ${state.lastCountLabel} · ${state.assetCount} assets",
                    style = TextStyle(fontFamily = JetBrainsMono, fontSize = 9.sp, color = DaftarColors.Muted),
                )
            }
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(DaftarColors.Ink)
                    .clickable(onClick = onEditRates)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Balance,
                    contentDescription = null,
                    tint = DaftarColors.Paper,
                    modifier = Modifier.size(10.dp),
                )
                Text(
                    text = "RATES",
                    style = TextStyle(
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        letterSpacing = 0.15.em,
                        color = DaftarColors.Paper,
                    ),
                )
            }
        }
        HorizontalDivider(color = DaftarColors.Copper.copy(alpha = 0.18f))

        // Asset grid — 3 columns
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(DaftarColors.PaperDeep)
                .padding(horizontal = 8.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            state.cells.chunked(3).forEach { rowCells ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    rowCells.forEach { cell -> CashCellView(cell, Modifier.weight(1f)) }
                    repeat(3 - rowCells.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
        }

        // Drawer total strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DaftarColors.Ink)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                MonoLabel("Drawer total", color = DaftarColors.GoldSoft, fontSize = 9)
                if (state.totalRevalText != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = if (state.totalRevalPositive) {
                                Icons.AutoMirrored.Rounded.TrendingUp
                            } else Icons.AutoMirrored.Rounded.TrendingDown,
                            contentDescription = null,
                            tint = if (state.totalRevalPositive) DaftarColors.LongGreen else DaftarColors.ShortRed,
                            modifier = Modifier.size(9.dp),
                        )
                        Text(
                            text = state.totalRevalText,
                            style = TextStyle(
                                fontFamily = JetBrainsMono,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                color = if (state.totalRevalPositive) DaftarColors.LongGreen else DaftarColors.ShortRed,
                            ),
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = state.drawerTotalText,
                    style = TextStyle(
                        fontFamily = Fraunces,
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp,
                        color = DaftarColors.Paper,
                    ),
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    text = state.reportingCurrency,
                    style = TextStyle(
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp,
                        letterSpacing = 0.1.em,
                        color = DaftarColors.GoldSoft,
                    ),
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun CashCellView(cell: CashCellUi, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = cell.code,
                style = TextStyle(
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 0.15.em,
                    color = if (cell.isMetal) DaftarColors.Gold else DaftarColors.Copper,
                ),
            )
            Text(
                text = cell.symbol,
                style = TextStyle(
                    fontFamily = JetBrainsMono,
                    fontSize = 11.sp,
                    color = if (cell.isMetal) DaftarColors.Gold else DaftarColors.CopperDeep.copy(alpha = 0.8f),
                ),
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = cell.amountText,
                style = TextStyle(
                    fontFamily = Fraunces,
                    fontWeight = FontWeight.Medium,
                    fontSize = 20.sp,
                    color = DaftarColors.Ink,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (cell.unitSuffix != null) {
                Text(
                    text = cell.unitSuffix,
                    style = TextStyle(
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp,
                        color = DaftarColors.Muted,
                    ),
                    modifier = Modifier.padding(start = 2.dp, top = 2.dp),
                )
            }
        }
        if (cell.sublineTola != null) {
            Text(
                text = cell.sublineTola,
                style = TextStyle(
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 9.sp,
                    color = DaftarColors.Gold,
                ),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Text(
            text = cell.equivalentText,
            style = TextStyle(
                fontFamily = JetBrainsMono,
                fontSize = 9.sp,
                color = DaftarColors.Muted,
            ),
            modifier = Modifier.padding(top = 3.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (cell.revalText != null) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        (if (cell.revalPositive) DaftarColors.Green else DaftarColors.Red).copy(alpha = 0.12f),
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = cell.revalText,
                    style = TextStyle(
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = if (cell.revalPositive) DaftarColors.Green else DaftarColors.Red,
                    ),
                )
            }
        }
    }
}

/** Ink pill FAB with copper plus — "New Entry · نوې لیکنه". */
@Composable
fun NewEntryFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(DaftarColors.Ink)
            .border(1.5.dp, DaftarColors.Copper.copy(alpha = 0.35f), RoundedCornerShape(28.dp))
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(DaftarColors.Copper),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = "New entry",
                tint = DaftarColors.Paper,
                modifier = Modifier.size(18.dp),
            )
        }
        Column {
            Text(
                text = "New Entry",
                style = TextStyle(
                    fontFamily = Fraunces,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    color = DaftarColors.Paper,
                ),
            )
            Text(
                text = "نوې لیکنه",
                style = TextStyle(
                    fontFamily = NotoNaskhArabic,
                    fontSize = 11.sp,
                    color = DaftarColors.GoldSoft,
                    textDirection = TextDirection.Rtl,
                ),
            )
        }
        Icon(
            imageVector = Icons.Rounded.KeyboardArrowUp,
            contentDescription = null,
            tint = DaftarColors.GoldSoft.copy(alpha = 0.65f),
            modifier = Modifier.size(14.dp),
        )
    }
}

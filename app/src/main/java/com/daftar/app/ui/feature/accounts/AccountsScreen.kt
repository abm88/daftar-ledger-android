package com.daftar.app.ui.feature.accounts

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BusinessCenter
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.daftar.app.core.format.Formatters
import com.daftar.app.domain.model.AssetCatalog
import com.daftar.app.ui.common.DaftarSearchField
import com.daftar.app.ui.common.MonoLabel
import com.daftar.app.ui.common.SyncIconButton
import com.daftar.app.ui.common.CustomerBadge
import com.daftar.app.ui.components.DarkBalanceGrid
import com.daftar.app.ui.components.EmptyState
import com.daftar.app.ui.components.EmptyStateTone
import com.daftar.app.ui.components.PositionLines
import com.daftar.app.ui.navigation.DaftarDestinations
import com.daftar.app.ui.theme.DaftarColors
import com.daftar.app.ui.theme.Fraunces
import com.daftar.app.ui.theme.Inter
import com.daftar.app.ui.theme.JetBrainsMono
import com.daftar.app.ui.theme.NotoNaskhArabic

/**
 * Accounts tab — customers only (v18: "Accounts screen is now customers-only.
 * Partners moved to Daftar -> Branches", see ui/feature/branches).
 */
@Composable
fun AccountsScreen(
    navController: NavController,
    viewModel: AccountsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var addCustomerOpen by rememberSaveable { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier.statusBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 140.dp),
        ) {
            item {
                Header(
                    pashto = "د حسابونو دفتر",
                    badge = "ACCOUNT HOLDERS",
                    badgeColor = DaftarColors.Green,
                    title = "Accounts",
                    subtitle = "${state.customerTotal} active accounts",
                    syncing = state.syncing,
                    onSync = viewModel::sync,
                )
            }

            if (state.customerTotal == 0) {
                // v18 first-run empty state with a create-account CTA.
                item {
                    EmptyState(
                        icon = Icons.Rounded.BusinessCenter,
                        title = "No accounts yet",
                        pashto = "تر اوسه هیڅ حساب نشته",
                        sub = "Open an account for a customer to start tracking their deposits, withdrawals, and balance.",
                        tone = EmptyStateTone.COPPER,
                        ctaLabel = "Add account · نوی حساب",
                        ctaIcon = Icons.Rounded.PersonAdd,
                        onCta = { addCustomerOpen = true },
                    )
                }
            } else {
                item {
                    HoldingsCard(state)
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StatBox("On deposit", state.withDeposits, DaftarColors.Green, Modifier.weight(1f))
                        StatBox("Owes you", state.withAdvances, DaftarColors.Red, Modifier.weight(1f))
                        StatBox("Settled", state.settled, DaftarColors.Muted, Modifier.weight(1f))
                    }
                }
                item {
                    DaftarSearchField(state.search, viewModel::setSearch, "Search account or phone…")
                    Spacer(modifier = Modifier.height(8.dp))
                }
                item {
                    ListHeader("Showing ${state.customerRows.size} of ${state.customerTotal}", "Balance")
                }
                if (state.customerRows.isEmpty()) {
                    item { EmptyNote("No accounts match your search") }
                } else {
                    items(count = state.customerRows.size, key = { i -> state.customerRows[i].customer.id }) { i ->
                        val row = state.customerRows[i]
                        PartyListRow(
                            badge = { CustomerBadge(row.customer) },
                            name = row.customer.name,
                            sub = "${row.customer.city.displayName} · ${row.customer.transactions.size} entries · Since ${row.customer.accountOpenedLabel}",
                            onClick = { navController.navigate(DaftarDestinations.customerDetail(row.customer.id)) },
                        ) {
                            PositionLines(row.balance, flatLabel = "SETTLED")
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DualCtaButton(
                label = "Create Account",
                pashto = "نوی حساب",
                icon = Icons.Rounded.PersonAdd,
                background = DaftarColors.Ink,
                modifier = Modifier.weight(1f),
                onClick = { addCustomerOpen = true },
            )
            DualCtaButton(
                label = "New Entry",
                pashto = "نوې لیکنه",
                icon = Icons.Rounded.Add,
                background = DaftarColors.Copper,
                modifier = Modifier.weight(1f),
                onClick = { navController.navigate(DaftarDestinations.newCustomerTx()) },
            )
        }
    }

    if (addCustomerOpen) {
        AddCustomerSheet(
            onDismiss = { addCustomerOpen = false },
            onSave = { name, shortName, initial, phone, city, notes, openings ->
                viewModel.addCustomer(name, shortName, initial, phone, city, notes, openings) {
                    addCustomerOpen = false
                }
            },
        )
    }
}

@Composable
private fun Header(
    pashto: String,
    badge: String,
    badgeColor: Color,
    title: String,
    subtitle: String,
    syncing: Boolean,
    onSync: () -> Unit,
) {
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
                    text = pashto,
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
                        .background(badgeColor)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    MonoLabel(badge, color = DaftarColors.Paper, fontSize = 9, letterSpacing = 0.1)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = title, style = MaterialTheme.typography.headlineMedium, color = DaftarColors.Ink)
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = subtitle,
                style = TextStyle(fontFamily = Inter, fontSize = 12.sp, color = DaftarColors.Muted),
            )
        }
        SyncIconButton(syncing = syncing, onClick = onSync)
    }
}

@Composable
private fun HoldingsCard(state: AccountsUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 14.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(listOf(DaftarColors.GreenDeep, DaftarColors.Green)),
            )
            .padding(bottom = 14.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.BusinessCenter,
                    contentDescription = null,
                    tint = Color(0xFFA8D5BA),
                    modifier = Modifier.size(11.dp),
                )
                MonoLabel("Account Holdings · امانت", color = Color(0xFFA8D5BA))
            }
            Spacer(modifier = Modifier.height(3.dp))
            // v18 stamps this card with an as-of date (the prototype hardcodes a
            // fake one); we show today's real date instead.
            MonoLabel(
                "Funds held on behalf · " + remember { Formatters.fullDateLabel(System.currentTimeMillis()) },
                color = DaftarColors.MutedLight,
                fontSize = 9,
                letterSpacing = 0.1,
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        DarkBalanceGrid(
            position = state.custodialNet,
            statusFor = { amt -> if (amt > 0) "ON DEPOSIT" else if (amt < 0) "ADVANCED" else "NIL" },
            accent = Color(0xFFA8D5BA),
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun StatBox(label: String, value: Int, color: Color, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(DaftarColors.PaperSoft)
            .border(1.dp, DaftarColors.Line, RoundedCornerShape(12.dp))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value.toString(),
            style = TextStyle(
                fontFamily = Fraunces,
                fontWeight = FontWeight.Medium,
                fontSize = 20.sp,
                color = color,
            ),
        )
        Spacer(modifier = Modifier.height(2.dp))
        MonoLabel(label, fontSize = 9, letterSpacing = 0.15)
    }
}

@Composable
fun ListHeader(left: String, right: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        MonoLabel(left)
        MonoLabel(right)
    }
}

@Composable
fun EmptyNote(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TextStyle(fontFamily = Inter, fontSize = 13.sp, color = DaftarColors.Muted),
        )
    }
}

@Composable
fun PartyListRow(
    badge: @Composable () -> Unit,
    name: String,
    sub: String,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            badge()
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = TextStyle(
                        fontFamily = Inter,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = DaftarColors.Ink,
                    ),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = sub,
                    style = TextStyle(fontFamily = Inter, fontSize = 11.sp, color = DaftarColors.Muted),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            trailing()
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp),
            color = DaftarColors.Line,
        )
    }
}

@Composable
private fun DualCtaButton(
    label: String,
    pashto: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    background: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = DaftarColors.Paper,
                modifier = Modifier.size(14.dp),
            )
        }
        Spacer(modifier = Modifier.size(8.dp))
        Column {
            Text(
                text = label.uppercase(),
                style = TextStyle(
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 0.1.em,
                    color = DaftarColors.Paper,
                ),
            )
            Text(
                text = pashto,
                style = TextStyle(
                    fontFamily = NotoNaskhArabic,
                    fontSize = 10.sp,
                    color = DaftarColors.Paper.copy(alpha = 0.85f),
                    textDirection = TextDirection.Rtl,
                ),
            )
        }
    }
}

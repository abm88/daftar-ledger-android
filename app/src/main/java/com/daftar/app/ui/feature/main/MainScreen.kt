package com.daftar.app.ui.feature.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.BusinessCenter
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.daftar.app.domain.model.HawalaStatus
import com.daftar.app.domain.model.HawalaType
import com.daftar.app.domain.repository.PartnerRepository
import com.daftar.app.ui.feature.accounts.AccountsScreen
import com.daftar.app.ui.feature.hawalas.HawalasScreen
import com.daftar.app.ui.feature.home.HomeScreen
import com.daftar.app.ui.feature.ledger.GeneralLedgerScreen
import com.daftar.app.ui.feature.shop.ShopScreen
import com.daftar.app.ui.navigation.DaftarDestinations
import com.daftar.app.ui.theme.DaftarColors
import com.daftar.app.ui.theme.JetBrainsMono
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class MainTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Rounded.Home),
    ACCOUNTS("Accounts", Icons.Rounded.BusinessCenter),
    HAWALAS("Hawalas", Icons.AutoMirrored.Rounded.Send),
    LEDGER("Ledger", Icons.AutoMirrored.Rounded.MenuBook),
    SHOP("Shop", Icons.Rounded.Person),
}

@HiltViewModel
class MainViewModel @Inject constructor(
    partnerRepository: PartnerRepository,
) : ViewModel() {
    val pendingCount = partnerRepository.partners
        .map { partners ->
            partners.sumOf { p ->
                p.hawalas.count { it.status == HawalaStatus.PENDING && it.type != HawalaType.SETTLEMENT }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
}

/** Bottom-nav shell hosting the five tabs; also owns the New Entry chooser sheet. */
@Composable
fun MainScreen(
    navController: NavController,
    viewModel: MainViewModel = hiltViewModel(),
) {
    var currentTab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    var chooserOpen by rememberSaveable { mutableStateOf(false) }
    val pendingCount by viewModel.pendingCount.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = DaftarColors.Paper,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Column(modifier = Modifier.background(DaftarColors.Paper)) {
                HorizontalDivider(color = DaftarColors.Line)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                ) {
                    MainTab.entries.forEach { tab ->
                        val selected = tab == currentTab
                        Column(
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { currentTab = tab }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Box {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.label,
                                    tint = if (selected) DaftarColors.Copper else DaftarColors.Muted,
                                    modifier = Modifier.size(20.dp),
                                )
                                if (tab == MainTab.HAWALAS && pendingCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = 8.dp, y = (-4).dp)
                                            .clip(CircleShape)
                                            .background(DaftarColors.Copper)
                                            .padding(horizontal = 5.dp, vertical = 1.dp),
                                    ) {
                                        Text(
                                            text = pendingCount.toString(),
                                            style = TextStyle(
                                                fontFamily = JetBrainsMono,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 8.sp,
                                                color = DaftarColors.Paper,
                                            ),
                                        )
                                    }
                                }
                            }
                            Text(
                                text = tab.label.uppercase(),
                                style = TextStyle(
                                    fontFamily = JetBrainsMono,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp,
                                    letterSpacing = 0.1.em,
                                    color = if (selected) DaftarColors.Copper else DaftarColors.Muted,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (currentTab) {
                MainTab.HOME -> HomeScreen(
                    navController = navController,
                    onOpenNewEntry = { chooserOpen = true },
                    onViewAllLedger = { currentTab = MainTab.LEDGER },
                )
                MainTab.ACCOUNTS -> AccountsScreen(navController)
                MainTab.HAWALAS -> HawalasScreen(navController)
                MainTab.LEDGER -> GeneralLedgerScreen(
                    navController = navController,
                    onOpenNewEntry = { chooserOpen = true },
                )
                MainTab.SHOP -> ShopScreen(navController)
            }
        }
    }

    if (chooserOpen) {
        NewEntryChooserSheet(
            onDismiss = { chooserOpen = false },
            onChoose = { option ->
                chooserOpen = false
                when (option) {
                    NewEntryOption.RECEIVED ->
                        navController.navigate(DaftarDestinations.newCustomerTx(mode = "received"))
                    NewEntryOption.GAVE ->
                        navController.navigate(DaftarDestinations.newCustomerTx(mode = "gave"))
                    NewEntryOption.FX -> navController.navigate(DaftarDestinations.NEW_FX)
                    NewEntryOption.HAWALA -> navController.navigate(DaftarDestinations.newHawala())
                }
            },
        )
    }
}

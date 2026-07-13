package com.daftar.app.ui.feature.shop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.BusinessCenter
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.daftar.app.core.format.Formatters
import com.daftar.app.domain.model.AssetCatalog
import com.daftar.app.domain.model.LedgerPeriod
import com.daftar.app.domain.model.ShopProfile
import com.daftar.app.domain.model.UserAccount
import com.daftar.app.domain.repository.AuthRepository
import com.daftar.app.domain.repository.CashRepository
import com.daftar.app.domain.repository.CustomerRepository
import com.daftar.app.domain.repository.FxRepository
import com.daftar.app.domain.repository.InvestmentRepository
import com.daftar.app.domain.repository.PartnerRepository
import com.daftar.app.domain.repository.RatesRepository
import com.daftar.app.domain.repository.SettingsRepository
import com.daftar.app.domain.usecase.CurrencyConverter
import com.daftar.app.domain.usecase.PnlCalculator
import com.daftar.app.domain.usecase.SignOutUseCase
import com.daftar.app.ui.common.LocalToaster
import com.daftar.app.ui.common.ToastIcon
import com.daftar.app.ui.navigation.DaftarDestinations
import com.daftar.app.ui.theme.DaftarColors
import com.daftar.app.ui.theme.Fraunces
import com.daftar.app.ui.theme.Inter
import com.daftar.app.ui.theme.JetBrainsMono
import com.daftar.app.ui.theme.NotoNaskhArabic
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ShopUiState(
    val profile: ShopProfile = ShopProfile(),
    val user: UserAccount? = null,
    val contactCount: Int = 0,
    val entryCount: Int = 0,
    val cityCount: Int = 4,
    val partnerCount: Int = 0,
    val lastCountLabel: String = "",
    val pnlSubtitle: String = "",
    val investmentsSubtitle: String = "",
    val activeAssetCount: Int = 0,
    val reportingCurrency: String = "AFN",
    val tradeCurrency: String = "USD",
    val setupNeeded: Boolean = false,
)

@HiltViewModel
class ShopViewModel @Inject constructor(
    partnerRepository: PartnerRepository,
    customerRepository: CustomerRepository,
    fxRepository: FxRepository,
    investmentRepository: InvestmentRepository,
    cashRepository: CashRepository,
    ratesRepository: RatesRepository,
    settingsRepository: SettingsRepository,
    pnlCalculator: PnlCalculator,
    converter: CurrencyConverter,
    authRepository: AuthRepository,
    private val signOutUseCase: SignOutUseCase,
) : ViewModel() {

    fun signOut() {
        // Saves the shop one final time before the session ends (v18 logout).
        viewModelScope.launch { signOutUseCase() }
    }

    val uiState = combine(
        combine(partnerRepository.partners, customerRepository.customers) { p, c -> p to c },
        combine(fxRepository.trades, investmentRepository.investments) { t, i -> t to i },
        cashRepository.drawer,
        ratesRepository.rateBook,
        combine(
            settingsRepository.settings,
            settingsRepository.shopProfile,
            authRepository.sessionUser,
        ) { s, pr, u -> Triple(s, pr, u) },
    ) { (partners, customers), (trades, investments), drawer, rates, (settings, profile, user) ->
        val hawalaCount = partners.sumOf { it.hawalas.size }
        val txCount = customers.sumOf { it.transactions.size }

        val allTime = pnlCalculator.compute(LedgerPeriod.ALL, trades, partners, drawer, rates, settings)
        val reporting = settings.reportingCurrency
        val repDecimals = AssetCatalog.decimalsFor(reporting)
        val grandRep = converter.toReporting("AFN", allTime.grandTotalAfn, rates, settings)

        val investedRep = investments.sumOf { inv ->
            converter.toReporting(inv.assetCode, inv.signedAmount, rates, settings)
        }

        val hasBalances = drawer.balances.values.any { it > 0.0 }

        ShopUiState(
            profile = profile,
            user = user,
            contactCount = partners.size + customers.size,
            entryCount = hawalaCount + txCount,
            partnerCount = partners.size,
            lastCountLabel = drawer.lastCountLabel,
            pnlSubtitle = (if (grandRep >= 0) "+" else "−") +
                Formatters.compact(grandRep, repDecimals) + " " + reporting +
                " all-time · ${allTime.fxTradeCount} trades · ${allTime.hawalaCount} hawalas",
            investmentsSubtitle = Formatters.compact(investedRep, repDecimals) + " " + reporting +
                " invested · ${investments.size} entries",
            activeAssetCount = settings.activeAssets().size,
            reportingCurrency = reporting,
            tradeCurrency = settings.tradeCurrency,
            setupNeeded = !hasBalances && investments.isEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ShopUiState())
}

private data class ShopItem(
    val icon: ImageVector,
    val label: String,
    val sub: String,
    val route: String? = null,
    val stubToast: String? = null,
    val danger: Boolean = false,
    val onClick: (() -> Unit)? = null,
)

/** Shop tab: saraf profile, quick stats, and the settings hub. */
@Composable
fun ShopScreen(
    navController: NavController,
    viewModel: ShopViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val toaster = LocalToaster.current

    val items = listOf(
        ShopItem(
            Icons.Rounded.Tag, "Count Cash · صندوق شمېرنه",
            "Recount drawer · last ${state.lastCountLabel}", DaftarDestinations.CASH_COUNT,
        ),
        ShopItem(Icons.Rounded.BarChart, "Profit & Loss · ګټه او زیان", state.pnlSubtitle, DaftarDestinations.PNL),
        ShopItem(Icons.Rounded.BusinessCenter, "Investments · پانګه اچونه", state.investmentsSubtitle, DaftarDestinations.INVESTMENTS),
        ShopItem(
            Icons.Rounded.Balance, "Live Rates",
            "USD/AFN · PKR/AFN · USD/PKR — view & broadcast", DaftarDestinations.RATES,
        ),
        // v18: partner sarafs moved from the Accounts tab to Daftar → Branches.
        ShopItem(
            Icons.Rounded.Group, "Branches · څانګې",
            "${state.partnerCount} partner sarafs · net positions & settlements",
            DaftarDestinations.BRANCHES,
        ),
        ShopItem(
            Icons.Rounded.BusinessCenter, "Assets · Currencies & Metals",
            "${state.activeAssetCount} active · gold, silver, EUR, GBP, SAR, AED",
            DaftarDestinations.ASSET_MANAGEMENT,
        ),
        ShopItem(
            Icons.Rounded.Public, "Default Currency · اسعار پیش‌فرض",
            "Reporting: ${state.reportingCurrency} · Trade: ${state.tradeCurrency}",
            DaftarDestinations.DEFAULTS,
        ),
        ShopItem(
            Icons.Rounded.BusinessCenter, "Initial Setup · پيل تنظيم",
            if (state.setupNeeded) "Set opening balances · required to start" else "Reset opening balances",
            DaftarDestinations.INITIAL_SETUP,
        ),
        ShopItem(Icons.Rounded.Person, "Saraf profile", "Shop details, license", stubToast = "Profile editing comes with the API"),
        ShopItem(Icons.Rounded.Public, "Languages", "Pashto · Dari · English", stubToast = "Language switching coming soon"),
        ShopItem(Icons.Rounded.Tag, "Commission defaults", "0.8% · 1.0% · 1.5% tiers", stubToast = "Commission tiers coming soon"),
        ShopItem(Icons.Rounded.ChatBubble, "WhatsApp templates", "Code · Rate sheet · Statement", stubToast = "Templates coming soon"),
        ShopItem(Icons.Rounded.Lock, "PIN & biometric", "Face ID enabled", stubToast = "Security settings coming soon"),
        ShopItem(Icons.Rounded.Download, "Export daftar", "Daily · Weekly · Monthly", stubToast = "Export coming soon"),
        ShopItem(Icons.Rounded.Shield, "KYC & record keeping", "AFG Central Bank compliance", stubToast = "Compliance tools coming soon"),
        ShopItem(
            Icons.AutoMirrored.Rounded.Logout, "Sign out · وتل", "End session · لاسلیک ختم",
            danger = true,
            onClick = {
                viewModel.signOut()
                toaster("Signed out", ToastIcon.LOGOUT)
            },
        ),
    )

    LazyColumn(
        modifier = Modifier.statusBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 110.dp),
    ) {
        item {
            val user = state.user
            val displayName = user?.name ?: state.profile.ownerName
            val initials = displayName.split(' ')
                .mapNotNull { it.firstOrNull()?.toString() }
                .joinToString("")
                .take(2)
                .uppercase()
                .ifEmpty { "S" }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // v18 profile avatar: 86dp rounded square, inset gold keyline, and a
                // large low-opacity Naskh watermark of the first letter behind the
                // Latin initials.
                Box(
                    modifier = Modifier
                        .size(86.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(DaftarColors.Ink),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(3.dp)
                            .border(1.dp, DaftarColors.Gold.copy(alpha = 0.35f), RoundedCornerShape(19.dp)),
                    )
                    Text(
                        text = displayName.firstOrNull()?.toString() ?: "س",
                        style = TextStyle(
                            fontFamily = NotoNaskhArabic,
                            fontSize = 52.sp,
                            color = DaftarColors.GoldSoft.copy(alpha = 0.18f),
                        ),
                    )
                    Text(
                        text = initials,
                        style = TextStyle(
                            fontFamily = Fraunces,
                            fontWeight = FontWeight.Medium,
                            fontSize = 26.sp,
                            color = DaftarColors.GoldSoft,
                        ),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = displayName,
                    style = TextStyle(
                        fontFamily = Fraunces,
                        fontWeight = FontWeight.Medium,
                        fontSize = 20.sp,
                        color = DaftarColors.Ink,
                    ),
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = user?.email ?: "${state.profile.shopName} · ${state.profile.city.displayName}",
                    style = TextStyle(fontFamily = Inter, fontSize = 12.sp, color = DaftarColors.Muted),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "SIGNED IN · حساب فعال",
                    style = TextStyle(
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Medium,
                        fontSize = 9.sp,
                        letterSpacing = 0.15.em,
                        color = DaftarColors.MutedLight,
                    ),
                )
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MiniStat(state.contactCount.toString(), "Contacts", Modifier.weight(1f))
                MiniStat(state.entryCount.toString(), "Entries", Modifier.weight(1f))
                MiniStat(state.cityCount.toString(), "Cities", Modifier.weight(1f))
            }
        }

        items(count = items.size) { index ->
            val item = items[index]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DaftarColors.PaperSoft)
                    .border(1.dp, DaftarColors.Line, RoundedCornerShape(12.dp))
                    .clickable {
                        when {
                            item.onClick != null -> item.onClick.invoke()
                            item.route != null -> navController.navigate(item.route)
                            else -> toaster(item.stubToast ?: "Coming soon", ToastIcon.CHECK)
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (item.danger) DaftarColors.Red.copy(alpha = 0.1f) else DaftarColors.PaperDeep),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        item.icon,
                        contentDescription = null,
                        tint = if (item.danger) DaftarColors.Red else DaftarColors.InkSoft,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.label,
                        style = TextStyle(
                            fontFamily = Inter,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = if (item.danger) DaftarColors.Red else DaftarColors.Ink,
                        ),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = item.sub,
                        style = TextStyle(fontFamily = Inter, fontSize = 11.sp, color = DaftarColors.Muted),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = DaftarColors.Muted,
                    modifier = Modifier.size(17.dp),
                )
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(DaftarColors.Ink)
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Daftar · for sarafs",
                    style = TextStyle(
                        fontFamily = Fraunces,
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp,
                        color = DaftarColors.Paper,
                    ),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "A disciplined ledger for Afghanistan's money-changing trade.",
                    style = TextStyle(fontFamily = Inter, fontSize = 12.sp, color = DaftarColors.MutedLight),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "د افغان صرافانو لپاره ډیجیټل دفتر",
                    style = TextStyle(
                        fontFamily = NotoNaskhArabic,
                        fontSize = 12.sp,
                        color = DaftarColors.GoldSoft,
                        textDirection = TextDirection.Rtl,
                    ),
                )
            }
        }
    }
}

@Composable
private fun MiniStat(value: String, label: String, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(DaftarColors.PaperSoft)
            .border(1.dp, DaftarColors.Line, RoundedCornerShape(12.dp))
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = TextStyle(
                fontFamily = Fraunces,
                fontWeight = FontWeight.Medium,
                fontSize = 20.sp,
                color = DaftarColors.Ink,
            ),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label.uppercase(),
            style = TextStyle(
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                letterSpacing = 0.15.em,
                color = DaftarColors.Muted,
            ),
        )
    }
}

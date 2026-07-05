package com.daftar.app.ui.feature.shop

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
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.BusinessCenter
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Download
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
import com.daftar.app.domain.repository.CashRepository
import com.daftar.app.domain.repository.CustomerRepository
import com.daftar.app.domain.repository.FxRepository
import com.daftar.app.domain.repository.InvestmentRepository
import com.daftar.app.domain.repository.PartnerRepository
import com.daftar.app.domain.repository.RatesRepository
import com.daftar.app.domain.repository.SettingsRepository
import com.daftar.app.domain.usecase.CurrencyConverter
import com.daftar.app.domain.usecase.PnlCalculator
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

data class ShopUiState(
    val profile: ShopProfile = ShopProfile(),
    val contactCount: Int = 0,
    val entryCount: Int = 0,
    val cityCount: Int = 4,
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
) : ViewModel() {

    val uiState = combine(
        combine(partnerRepository.partners, customerRepository.customers) { p, c -> p to c },
        combine(fxRepository.trades, investmentRepository.investments) { t, i -> t to i },
        cashRepository.drawer,
        ratesRepository.rateBook,
        combine(settingsRepository.settings, settingsRepository.shopProfile) { s, pr -> s to pr },
    ) { (partners, customers), (trades, investments), drawer, rates, (settings, profile) ->
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
            contactCount = partners.size + customers.size,
            entryCount = hawalaCount + txCount,
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
    )

    LazyColumn(
        modifier = Modifier.statusBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 110.dp),
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(DaftarColors.Ink),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "ر",
                        style = TextStyle(
                            fontFamily = NotoNaskhArabic,
                            fontWeight = FontWeight.Medium,
                            fontSize = 30.sp,
                            color = DaftarColors.GoldSoft,
                        ),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = state.profile.ownerName,
                    style = TextStyle(
                        fontFamily = Fraunces,
                        fontWeight = FontWeight.Medium,
                        fontSize = 20.sp,
                        color = DaftarColors.Ink,
                    ),
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "${state.profile.shopName} · ${state.profile.city.displayName}",
                    style = TextStyle(fontFamily = Inter, fontSize = 12.sp, color = DaftarColors.Muted),
                )
                Text(
                    text = "${state.profile.phone} · ${state.profile.registration}",
                    style = TextStyle(fontFamily = Inter, fontSize = 12.sp, color = DaftarColors.Muted),
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
                        if (item.route != null) navController.navigate(item.route)
                        else toaster(item.stubToast ?: "Coming soon", ToastIcon.CHECK)
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(DaftarColors.PaperDeep),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(item.icon, contentDescription = null, tint = DaftarColors.InkSoft, modifier = Modifier.size(16.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.label,
                        style = TextStyle(
                            fontFamily = Inter,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = DaftarColors.Ink,
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

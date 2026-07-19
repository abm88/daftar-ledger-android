package com.daftar.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.daftar.app.domain.model.UserAccount
import com.daftar.app.domain.repository.AuthRepository
import com.daftar.app.ui.common.LocalToaster
import com.daftar.app.ui.common.ToastCenter
import com.daftar.app.ui.common.ToastIcon
import com.daftar.app.ui.components.SplashOverlay
import com.daftar.app.ui.components.ToastPill
import com.daftar.app.ui.feature.assets.AssetManagementScreen
import com.daftar.app.ui.feature.branches.BranchesScreen
import com.daftar.app.ui.feature.cashcount.CashCountScreen
import com.daftar.app.ui.feature.customertx.CustomerTxDetailScreen
import com.daftar.app.ui.feature.defaults.DefaultsScreen
import com.daftar.app.ui.feature.detail.CustomerDetailScreen
import com.daftar.app.ui.feature.detail.PartnerDetailScreen
import com.daftar.app.ui.feature.expense.NewExpenseScreen
import com.daftar.app.ui.feature.fx.FxFormScreen
import com.daftar.app.ui.feature.fx.FxLedgerScreen
import com.daftar.app.ui.feature.hawaladetail.HawalaDetailScreen
import com.daftar.app.ui.feature.investments.InvestmentsScreen
import com.daftar.app.ui.feature.main.MainScreen
import com.daftar.app.ui.feature.newcusttx.NewCustomerTxScreen
import com.daftar.app.ui.feature.newhawala.NewHawalaScreen
import com.daftar.app.ui.feature.pnl.PnlScreen
import com.daftar.app.ui.feature.auth.AuthScreen
import com.daftar.app.ui.feature.rates.RatesScreen
import com.daftar.app.ui.feature.settle.SettleScreen
import com.daftar.app.ui.feature.setup.InitialSetupScreen
import com.daftar.app.ui.feature.statements.BusinessStatementScreen
import com.daftar.app.ui.feature.statements.CustomerStatementScreen
import com.daftar.app.ui.feature.statements.PartnerStatementScreen
import com.daftar.app.ui.feature.team.TeamMemberDetailScreen
import com.daftar.app.ui.feature.team.TeamScreen
import com.daftar.app.ui.navigation.DaftarDestinations
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class AppViewModel @Inject constructor(
    val toastCenter: ToastCenter,
    authRepository: AuthRepository,
) : ViewModel() {
    /** Drives the auth gate: null shows the login/signup flow. */
    val sessionUser: StateFlow<UserAccount?> = authRepository.sessionUser
}

/** Root of the UI: auth gate, navigation graph, splash overlay, and the global toast pill. */
@Composable
fun DaftarApp(appViewModel: AppViewModel = hiltViewModel()) {
    val toast by appViewModel.toastCenter.current.collectAsStateWithLifecycle()
    val sessionUser by appViewModel.sessionUser.collectAsStateWithLifecycle()
    var splashVisible by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        // v18 keeps the splash for 2 seconds before it fades out.
        delay(2000)
        splashVisible = false
    }

    CompositionLocalProvider(
        LocalToaster provides { message: String, icon: ToastIcon ->
            appViewModel.toastCenter.show(message, icon)
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            if (sessionUser == null) {
                // Auth gate — no session, no app. Signing out lands back here
                // and discards the previous navigation stack, like the prototype.
                AuthScreen()
            } else {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = DaftarDestinations.MAIN,
                ) {
                    composable(DaftarDestinations.MAIN) { MainScreen(navController) }

                    composable(
                        DaftarDestinations.PARTNER_DETAIL,
                        arguments = listOf(navArgument("partnerId") { type = NavType.StringType }),
                    ) { PartnerDetailScreen(navController) }

                    composable(
                        DaftarDestinations.CUSTOMER_DETAIL,
                        arguments = listOf(navArgument("customerId") { type = NavType.StringType }),
                    ) { CustomerDetailScreen(navController) }

                    composable(
                        DaftarDestinations.CUSTOMER_TX_DETAIL,
                        arguments = listOf(navArgument("txId") { type = NavType.StringType }),
                    ) { CustomerTxDetailScreen(navController) }

                    composable(
                        DaftarDestinations.HAWALA_DETAIL,
                        arguments = listOf(navArgument("hawalaId") { type = NavType.StringType }),
                    ) { HawalaDetailScreen(navController) }

                    composable(
                        DaftarDestinations.NEW_HAWALA,
                        arguments = listOf(
                            navArgument("partnerId") {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            },
                        ),
                    ) { NewHawalaScreen(navController) }

                    composable(
                        DaftarDestinations.NEW_CUSTOMER_TX,
                        arguments = listOf(
                            navArgument("mode") {
                                type = NavType.StringType
                                defaultValue = "full"
                            },
                            navArgument("customerId") {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            },
                            navArgument("locked") {
                                type = NavType.StringType
                                defaultValue = "false"
                            },
                        ),
                    ) { NewCustomerTxScreen(navController) }

                    composable(DaftarDestinations.NEW_FX) { FxFormScreen(navController) }
                    composable(DaftarDestinations.FX_LEDGER) { FxLedgerScreen(navController) }

                    composable(
                        DaftarDestinations.NEW_EXPENSE,
                        arguments = listOf(
                            navArgument("teamMemberId") {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            },
                        ),
                    ) { NewExpenseScreen(navController) }

                    composable(DaftarDestinations.TEAM) { TeamScreen(navController) }
                    composable(
                        DaftarDestinations.TEAM_MEMBER_DETAIL,
                        arguments = listOf(navArgument("memberId") { type = NavType.StringType }),
                    ) { entry ->
                        TeamMemberDetailScreen(
                            navController = navController,
                            memberId = entry.arguments?.getString("memberId").orEmpty(),
                        )
                    }

                    composable(
                        DaftarDestinations.SETTLE,
                        arguments = listOf(navArgument("partnerId") { type = NavType.StringType }),
                    ) { SettleScreen(navController) }

                    composable(
                        DaftarDestinations.CUSTOMER_STATEMENT,
                        arguments = listOf(navArgument("customerId") { type = NavType.StringType }),
                    ) { CustomerStatementScreen(navController) }

                    composable(
                        DaftarDestinations.PARTNER_STATEMENT,
                        arguments = listOf(navArgument("partnerId") { type = NavType.StringType }),
                    ) { PartnerStatementScreen(navController) }

                    composable(
                        DaftarDestinations.BUSINESS_STATEMENT,
                        arguments = listOf(
                            navArgument("filter") {
                                type = NavType.StringType
                                defaultValue = "all"
                            },
                        ),
                    ) { BusinessStatementScreen(navController) }

                    composable(DaftarDestinations.RATES) { RatesScreen(navController) }
                    composable(DaftarDestinations.BRANCHES) { BranchesScreen(navController) }
                    composable(DaftarDestinations.ASSET_MANAGEMENT) { AssetManagementScreen(navController) }
                    composable(DaftarDestinations.CASH_COUNT) { CashCountScreen(navController) }
                    composable(DaftarDestinations.PNL) { PnlScreen(navController) }
                    composable(DaftarDestinations.INVESTMENTS) { InvestmentsScreen(navController) }
                    composable(DaftarDestinations.DEFAULTS) { DefaultsScreen(navController) }
                    composable(DaftarDestinations.INITIAL_SETUP) { InitialSetupScreen(navController) }
                }
            }

            AnimatedVisibility(
                visible = toast != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 60.dp),
            ) {
                toast?.let { ToastPill(it) }
            }

            AnimatedVisibility(visible = splashVisible, exit = fadeOut()) {
                SplashOverlay()
            }
        }
    }
}

package com.daftar.app.data.session

import com.daftar.app.data.local.UserDataSnapshot
import com.daftar.app.data.local.UserDataStore
import com.daftar.app.data.seed.SeedData
import com.daftar.app.domain.model.AssetCatalog
import com.daftar.app.domain.model.CashDrawer
import com.daftar.app.domain.model.LedgerSettings
import com.daftar.app.domain.repository.CashRepository
import com.daftar.app.domain.repository.CustomerRepository
import com.daftar.app.domain.repository.FxRepository
import com.daftar.app.domain.repository.InvestmentRepository
import com.daftar.app.domain.repository.PartnerRepository
import com.daftar.app.domain.repository.RatesRepository
import com.daftar.app.domain.repository.SettingsRepository
import com.daftar.app.domain.repository.TeamRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/**
 * Binds the signed-in account to its own shop data — the Android port of the
 * prototype's per-user localStorage persistence (v18):
 *
 *  - login / signup / session-restore loads `daftar_data_<userId>` (or seeds a
 *    blank shop and saves it right away, exactly like `createBlankUserData()`),
 *  - every repository change schedules a throttled (400 ms) auto-save,
 *  - sign-out performs one final save before the session ends.
 *
 * With this in place accounts are fully isolated: the seeded demo ledger is
 * never shown to a signed-in user, and a returning saraf gets their own
 * daftar back after a process restart.
 *
 * TODO(backend): when a sync API exists, this class is the seam — replace the
 * local store round-trips with server pulls/pushes and conflict handling.
 */
@Singleton
class UserDataSession @Inject constructor(
    private val store: UserDataStore,
    private val seed: SeedData,
    private val cashRepository: CashRepository,
    private val partnerRepository: PartnerRepository,
    private val customerRepository: CustomerRepository,
    private val fxRepository: FxRepository,
    private val investmentRepository: InvestmentRepository,
    private val ratesRepository: RatesRepository,
    private val settingsRepository: SettingsRepository,
    private val teamRepository: TeamRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var autosaveJob: Job? = null

    @Volatile
    private var activeUserId: String? = null

    /** Load [userId]'s saved shop (or a blank one) into the repositories and start auto-saving. */
    suspend fun beginSession(userId: String) {
        stopAutosave()
        val saved = store.load(userId)
        val snapshot = saved ?: blankSnapshot()
        applySnapshot(snapshot)
        activeUserId = userId
        // v18 signup persists the blank shop immediately, before any edits.
        if (saved == null) store.save(userId, snapshot)
        startAutosave()
    }

    /** Final save + stop auto-saving. Call before the auth session is cleared. */
    fun endSession() {
        saveNow()
        activeUserId = null
        stopAutosave()
    }

    /** Synchronous snapshot save for the active user (v18 saves once more on logout). */
    fun saveNow() {
        val userId = activeUserId ?: return
        store.save(userId, currentSnapshot())
    }

    @OptIn(FlowPreview::class)
    private fun startAutosave() {
        autosaveJob = scope.launch {
            merge(
                cashRepository.drawer,
                partnerRepository.partners,
                customerRepository.customers,
                fxRepository.trades,
                investmentRepository.investments,
                ratesRepository.rateBook,
                settingsRepository.settings,
                teamRepository.members,
                teamRepository.expenses,
            )
                .debounce(AUTOSAVE_DEBOUNCE_MS) // v18 throttles saves at 400 ms after a render
                .collect { saveNow() }
        }
    }

    private fun stopAutosave() {
        autosaveJob?.cancel()
        autosaveJob = null
    }

    private suspend fun applySnapshot(snapshot: UserDataSnapshot) {
        cashRepository.replaceAll(snapshot.cashDrawer)
        partnerRepository.replaceAll(snapshot.counterparties)
        customerRepository.replaceAll(snapshot.customers)
        fxRepository.replaceAll(snapshot.fxTrades)
        investmentRepository.replaceAll(snapshot.investments)
        ratesRepository.replaceAll(snapshot.rateBook)
        settingsRepository.replaceSettings(snapshot.settings)
        teamRepository.replaceAll(snapshot.teamMembers, snapshot.expenses)
    }

    private fun currentSnapshot(): UserDataSnapshot = UserDataSnapshot(
        cashDrawer = cashRepository.drawer.value,
        counterparties = partnerRepository.partners.value,
        customers = customerRepository.customers.value,
        fxTrades = fxRepository.trades.value,
        investments = investmentRepository.investments.value,
        rateBook = ratesRepository.rateBook.value,
        settings = settingsRepository.settings.value,
        teamMembers = teamRepository.members.value,
        expenses = teamRepository.expenses.value,
    )

    /**
     * v18 createBlankUserData(): zero drawer ("Not yet counted"), empty ledgers,
     * factory-default rates, only the default assets active, AFN/USD defaults.
     */
    private fun blankSnapshot(): UserDataSnapshot = UserDataSnapshot(
        cashDrawer = CashDrawer(
            balances = AssetCatalog.ALL.associate { it.code to 0.0 },
            lastCountLabel = "Not yet counted",
        ),
        counterparties = emptyList(),
        customers = emptyList(),
        fxTrades = emptyList(),
        investments = emptyList(),
        rateBook = seed.rateBook,
        settings = LedgerSettings(),
    )

    companion object {
        private const val AUTOSAVE_DEBOUNCE_MS = 400L
    }
}

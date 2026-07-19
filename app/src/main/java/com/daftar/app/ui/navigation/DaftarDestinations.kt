package com.daftar.app.ui.navigation

/** Route definitions for every screen in the app. */
object DaftarDestinations {
    const val MAIN = "main"

    const val PARTNER_DETAIL = "partner/{partnerId}"
    fun partnerDetail(partnerId: String) = "partner/$partnerId"

    const val CUSTOMER_DETAIL = "customer/{customerId}"
    fun customerDetail(customerId: String) = "customer/$customerId"

    const val CUSTOMER_TX_DETAIL = "customerTx/{txId}"
    fun customerTxDetail(txId: String) = "customerTx/$txId"

    const val HAWALA_DETAIL = "hawala/{hawalaId}"
    fun hawalaDetail(hawalaId: String) = "hawala/$hawalaId"

    const val NEW_HAWALA = "newHawala?partnerId={partnerId}"
    fun newHawala(partnerId: String? = null) =
        if (partnerId == null) "newHawala" else "newHawala?partnerId=$partnerId"

    /** v20 two-phase receive: records an incoming hawala as pending. */
    const val RECEIVE_HAWALA = "receiveHawala"

    /** mode: full | gave | received */
    /** locked pins the entry to the given customer (v18 detail-page quick FABs). */
    const val NEW_CUSTOMER_TX = "newCustomerTx?mode={mode}&customerId={customerId}&locked={locked}"
    fun newCustomerTx(mode: String = "full", customerId: String? = null, locked: Boolean = false) =
        "newCustomerTx?mode=$mode" +
            (customerId?.let { "&customerId=$it" } ?: "") +
            (if (locked) "&locked=true" else "")

    const val NEW_FX = "newFx"
    const val FX_LEDGER = "fxLedger"

    /** Expense entry form. optional teamMemberId pre-selects the member (v20 member detail CTA). */
    const val NEW_EXPENSE = "newExpense?teamMemberId={teamMemberId}"
    fun newExpense(teamMemberId: String? = null) =
        if (teamMemberId == null) "newExpense" else "newExpense?teamMemberId=$teamMemberId"

    // v20: Team members moved into the Daftar section (expenses tracked per person).
    const val TEAM = "team"
    const val TEAM_MEMBER_DETAIL = "team/{memberId}"
    fun teamMemberDetail(memberId: String) = "team/$memberId"

    const val SETTLE = "settle/{partnerId}"
    fun settle(partnerId: String) = "settle/$partnerId"

    const val CUSTOMER_STATEMENT = "statement/customer/{customerId}"
    fun customerStatement(customerId: String) = "statement/customer/$customerId"

    const val PARTNER_STATEMENT = "statement/partner/{partnerId}"
    fun partnerStatement(partnerId: String) = "statement/partner/$partnerId"

    const val BUSINESS_STATEMENT = "statement/business?filter={filter}"
    fun businessStatement(filter: String = "all") = "statement/business?filter=$filter"

    const val RATES = "rates"
    // v18 moved the partner-saraf list from the Accounts tab to Daftar → Branches.
    const val BRANCHES = "branches"
    const val ASSET_MANAGEMENT = "assets"
    const val CASH_COUNT = "cashCount"
    const val PNL = "pnl"
    const val INVESTMENTS = "investments"
    const val DEFAULTS = "defaults"
    const val INITIAL_SETUP = "initialSetup"
}

package com.daftar.app.domain.model

enum class PartnerTier(val label: String, val description: String) {
    CORE("Core", "Daily settlement"),
    REGULAR("Regular", "Weekly"),
    ADHOC("Ad-hoc", "One-off"),
}

/** A counterparty saraf (trading partner) and their hawala ledger. */
data class Counterparty(
    val id: String,
    val name: String,
    val shortName: String,
    val phone: String,
    val city: City,
    /** Single Arabic-script letter shown on the badge. */
    val initial: String,
    val tier: PartnerTier,
    val hawalas: List<Hawala> = emptyList(),
)

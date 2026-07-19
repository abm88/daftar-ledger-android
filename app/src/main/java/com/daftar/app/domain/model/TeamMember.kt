package com.daftar.app.domain.model

/**
 * A staff member or partner of the daftar. v20 tracks business expenses per
 * person, so every [Expense] is recorded against one team member.
 */
data class TeamMember(
    val id: String,
    val name: String,
    val role: String = "Member",
    val phone: String? = null,
    val initial: String = name.trim().firstOrNull()?.uppercase() ?: "?",
)

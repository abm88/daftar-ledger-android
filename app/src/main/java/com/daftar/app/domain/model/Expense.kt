package com.daftar.app.domain.model

/**
 * A business cost recorded against a [TeamMember].
 *
 * v20 removed the expense-category grid: an expense now carries only its
 * amount/currency, the team member it is booked against, and a free-text note.
 * Feed and detail rows render it as "Expense · <member>" with the note.
 */
data class Expense(
    val id: String,
    val amount: Double,
    val currency: String,
    val teamMemberId: String,
    val note: String? = null,
    val timestampMillis: Long,
    val dateLabel: String,
)

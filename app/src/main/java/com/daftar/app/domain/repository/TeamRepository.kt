package com.daftar.app.domain.repository

import com.daftar.app.domain.model.Expense
import com.daftar.app.domain.model.TeamMember
import kotlinx.coroutines.flow.StateFlow

/**
 * Team members (staff/partners) and the expenses booked against them. v20 tracks
 * business costs per person, so members and expenses live together here.
 */
interface TeamRepository {
    val members: StateFlow<List<TeamMember>>
    val expenses: StateFlow<List<Expense>>

    suspend fun addMember(member: TeamMember)
    suspend fun addExpense(expense: Expense)

    /** Blank-shop reset — a fresh account starts with no team and no expenses. */
    suspend fun clearAll()

    /** Swap in another account's team + expenses (per-user persistence restore). */
    suspend fun replaceAll(members: List<TeamMember>, expenses: List<Expense>)

    fun memberById(id: String): TeamMember?
    fun expensesFor(memberId: String): List<Expense>
}

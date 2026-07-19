package com.daftar.app.data.repository

import com.daftar.app.data.seed.SeedData
import com.daftar.app.domain.model.Expense
import com.daftar.app.domain.model.TeamMember
import com.daftar.app.domain.repository.TeamRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Singleton
class InMemoryTeamRepository @Inject constructor(seed: SeedData) : TeamRepository {

    private val membersState = MutableStateFlow(seed.teamMembers)
    override val members: StateFlow<List<TeamMember>> = membersState.asStateFlow()

    private val expensesState = MutableStateFlow(seed.expenses)
    override val expenses: StateFlow<List<Expense>> = expensesState.asStateFlow()

    override suspend fun addMember(member: TeamMember) {
        membersState.update { it + member }
    }

    override suspend fun addExpense(expense: Expense) {
        expensesState.update { listOf(expense) + it }
    }

    override suspend fun clearAll() {
        membersState.value = emptyList()
        expensesState.value = emptyList()
    }

    override suspend fun replaceAll(members: List<TeamMember>, expenses: List<Expense>) {
        membersState.value = members
        expensesState.value = expenses
    }

    override fun memberById(id: String): TeamMember? =
        membersState.value.firstOrNull { it.id == id }

    override fun expensesFor(memberId: String): List<Expense> =
        expensesState.value.filter { it.teamMemberId == memberId }.sortedByDescending { it.timestampMillis }
}

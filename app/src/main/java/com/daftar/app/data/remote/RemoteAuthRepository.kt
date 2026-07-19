package com.daftar.app.data.remote

import android.content.SharedPreferences
import com.daftar.app.domain.model.UserAccount
import com.daftar.app.domain.repository.AuthRepository
import com.daftar.app.domain.repository.AuthResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.json.JSONObject

/** API-backed authentication with a restorable bearer session. */
@Singleton
class RemoteAuthRepository @Inject constructor(
    private val api: DaftarApi,
    private val tokenStore: SharedPreferencesTokenStore,
    private val preferences: SharedPreferences,
    private val sync: ApiDataSynchronizer,
) : AuthRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val session = MutableStateFlow(loadCachedUser().takeIf { tokenStore.token != null })
    override val sessionUser: StateFlow<UserAccount?> = session.asStateFlow()

    init {
        scope.launch {
            tokenStore.tokenChanges.drop(1).collect { token ->
                if (token == null) {
                    preferences.edit().remove(KEY_CACHED_USER).apply()
                    session.value = null
                }
            }
        }
        if (session.value != null) {
            scope.launch {
                runCatching {
                    val user = api.me().user.toAccount()
                    sync.refreshAll()
                    cache(user)
                    session.value = user
                }.onFailure {
                    if (it is ApiException.Unauthorized) clearLocalSession()
                }
            }
        }
    }

    override suspend fun register(name: String, email: String, password: String): AuthResult = authenticate {
        api.register(RegisterRequest(email.trim(), password, name.trim()))
    }

    override suspend fun login(email: String, password: String): AuthResult = authenticate {
        api.login(LoginRequest(email.trim(), password))
    }

    override suspend fun signOut() {
        // Revoke while the bearer token is still attached; local cleanup is unconditional.
        runCatching { api.logout() }
        clearLocalSession()
        sync.clearLocal()
    }

    private suspend fun authenticate(call: suspend () -> AuthResponse): AuthResult = try {
        val response = call()
        tokenStore.token = response.token
        sync.refreshAll()
        val user = response.user.toAccount()
        cache(user)
        session.value = user
        AuthResult.Success(user)
    } catch (error: ApiException) {
        tokenStore.clear()
        AuthResult.Failure(error.message ?: "Unable to sign in")
    } catch (error: Exception) {
        tokenStore.clear()
        AuthResult.Failure(error.message ?: "Unable to sign in")
    }

    private fun cache(user: UserAccount) {
        val value = JSONObject()
            .put("id", user.id)
            .put("name", user.name)
            .put("email", user.email)
            .put("createdAt", user.createdAtMillis)
        preferences.edit().putString(KEY_CACHED_USER, value.toString()).apply()
    }

    private fun loadCachedUser(): UserAccount? = runCatching {
        val value = JSONObject(preferences.getString(KEY_CACHED_USER, null) ?: return null)
        UserAccount(
            id = value.getString("id"),
            name = value.getString("name"),
            email = value.getString("email"),
            createdAtMillis = value.getLong("createdAt"),
        )
    }.getOrNull()

    private fun clearLocalSession() {
        tokenStore.clear()
        preferences.edit().remove(KEY_CACHED_USER).apply()
        session.value = null
    }

    private companion object {
        const val KEY_CACHED_USER = "api_cached_user"
    }
}

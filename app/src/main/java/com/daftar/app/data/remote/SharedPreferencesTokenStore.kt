package com.daftar.app.data.remote

import android.content.SharedPreferences
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class SharedPreferencesTokenStore @Inject constructor(
    private val preferences: SharedPreferences,
) : AuthTokenStore {
    private val state = MutableStateFlow(preferences.getString(KEY_TOKEN, null))
    val tokenChanges: StateFlow<String?> = state.asStateFlow()

    override var token: String?
        get() = state.value
        set(value) {
            preferences.edit().apply {
                if (value == null) remove(KEY_TOKEN) else putString(KEY_TOKEN, value)
            }.apply()
            state.value = value
        }

    override fun clear() {
        token = null
    }

    private companion object {
        const val KEY_TOKEN = "api_session_token"
    }
}

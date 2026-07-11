package com.daftar.app.data.repository

import android.content.SharedPreferences
import com.daftar.app.core.time.TimeProvider
import com.daftar.app.domain.model.UserAccount
import com.daftar.app.domain.repository.AuthRepository
import com.daftar.app.domain.repository.AuthResult
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Device-local accounts — the Android analog of the prototype's localStorage
 * auth store. Accounts (salted PBKDF2 password hashes, never plaintext) and
 * the active session id live in a private SharedPreferences file, so the
 * signed-in saraf survives app restarts. There is no backend and no account
 * recovery; replacing this class with an API-backed implementation is the
 * only change needed to move auth server-side.
 */
@Singleton
class LocalAuthRepository @Inject constructor(
    private val prefs: SharedPreferences,
    private val timeProvider: TimeProvider,
) : AuthRepository {

    private data class StoredAccount(
        val id: String,
        val name: String,
        val email: String,
        val passwordHash: String,
        val salt: String,
        val createdAtMillis: Long,
    ) {
        fun toUser() = UserAccount(id, name, email, createdAtMillis)
    }

    private val mutex = Mutex()
    private var accounts: List<StoredAccount> = emptyList()

    private val session = MutableStateFlow<UserAccount?>(null)
    override val sessionUser: StateFlow<UserAccount?> = session.asStateFlow()

    init {
        restore()
    }

    override suspend fun register(name: String, email: String, password: String): AuthResult =
        withContext(Dispatchers.Default) {
            val normalizedEmail = email.trim().lowercase()
            val trimmedName = name.trim()
            when {
                !EMAIL_REGEX.matches(normalizedEmail) ->
                    AuthResult.Failure("Please enter a valid email address")
                password.length < MIN_PASSWORD_LENGTH ->
                    AuthResult.Failure("Password must be at least $MIN_PASSWORD_LENGTH characters")
                trimmedName.isEmpty() ->
                    AuthResult.Failure("Please enter your name")
                else -> mutex.withLock {
                    if (accounts.any { it.email == normalizedEmail }) {
                        AuthResult.Failure("An account with this email already exists")
                    } else {
                        val now = timeProvider.nowMillis()
                        val salt = randomHex(SALT_BYTES)
                        val account = StoredAccount(
                            id = "usr_${now}_${randomHex(3)}",
                            name = trimmedName,
                            email = normalizedEmail,
                            passwordHash = hashPassword(password, salt),
                            salt = salt,
                            createdAtMillis = now,
                        )
                        accounts = accounts + account
                        session.value = account.toUser()
                        persist()
                        AuthResult.Success(account.toUser())
                    }
                }
            }
        }

    override suspend fun login(email: String, password: String): AuthResult =
        withContext(Dispatchers.Default) {
            val normalizedEmail = email.trim().lowercase()
            mutex.withLock {
                val account = accounts.firstOrNull { it.email == normalizedEmail }
                val matches = account != null && MessageDigest.isEqual(
                    hashPassword(password, account.salt).toByteArray(),
                    account.passwordHash.toByteArray(),
                )
                if (account == null || !matches) {
                    // Deliberately vague — don't reveal whether the email exists.
                    AuthResult.Failure("Email or password is incorrect")
                } else {
                    session.value = account.toUser()
                    persist()
                    AuthResult.Success(account.toUser())
                }
            }
        }

    override suspend fun signOut() {
        mutex.withLock {
            session.value = null
            persist()
        }
    }

    private fun restore() {
        val raw = prefs.getString(KEY_STORE, null) ?: return
        try {
            val json = JSONObject(raw)
            val list = json.optJSONArray("accounts") ?: JSONArray()
            accounts = (0 until list.length()).map { index ->
                val entry = list.getJSONObject(index)
                StoredAccount(
                    id = entry.getString("id"),
                    name = entry.getString("name"),
                    email = entry.getString("email"),
                    passwordHash = entry.getString("passwordHash"),
                    salt = entry.getString("salt"),
                    createdAtMillis = entry.getLong("createdAt"),
                )
            }
            val sessionId = if (json.isNull("sessionUserId")) null else json.getString("sessionUserId")
            session.value = accounts.firstOrNull { it.id == sessionId }?.toUser()
        } catch (_: JSONException) {
            // Corrupt store — start fresh rather than crash, same as the prototype.
            accounts = emptyList()
            session.value = null
        }
    }

    private fun persist() {
        val json = JSONObject()
            .put("sessionUserId", session.value?.id ?: JSONObject.NULL)
            .put(
                "accounts",
                JSONArray().also { array ->
                    accounts.forEach { account ->
                        array.put(
                            JSONObject()
                                .put("id", account.id)
                                .put("name", account.name)
                                .put("email", account.email)
                                .put("passwordHash", account.passwordHash)
                                .put("salt", account.salt)
                                .put("createdAt", account.createdAtMillis),
                        )
                    }
                },
            )
        prefs.edit().putString(KEY_STORE, json.toString()).apply()
    }

    private fun hashPassword(password: String, salt: String): String {
        val spec = PBEKeySpec(password.toCharArray(), salt.toByteArray(), PBKDF2_ITERATIONS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded.joinToString("") { "%02x".format(it) }
    }

    private fun randomHex(byteCount: Int): String {
        val bytes = ByteArray(byteCount)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val KEY_STORE = "daftar_auth_store"
        const val MIN_PASSWORD_LENGTH = 6

        private val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

        // Kept moderate so sign-in stays fast on low-end devices; the busy
        // state in the auth form covers the delay.
        private const val PBKDF2_ITERATIONS = 120_000
        private const val KEY_BITS = 256
        private const val SALT_BYTES = 16
    }
}

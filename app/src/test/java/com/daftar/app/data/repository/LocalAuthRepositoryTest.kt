package com.daftar.app.data.repository

import com.daftar.app.core.time.TimeProvider
import com.daftar.app.domain.repository.AuthResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LocalAuthRepositoryTest {

    private class FixedTimeProvider(var now: Long = 1_000L) : TimeProvider {
        override fun nowMillis(): Long = now
    }

    private lateinit var prefs: FakeSharedPreferences
    private lateinit var time: FixedTimeProvider
    private lateinit var repo: LocalAuthRepository

    @Before
    fun setUp() {
        prefs = FakeSharedPreferences()
        time = FixedTimeProvider()
        repo = LocalAuthRepository(prefs, time)
    }

    @Test
    fun `register starts a session for the new account`() = runTest {
        val result = repo.register("Haji Rahmat", "Haji@Example.com ", "secret123")

        assertTrue(result is AuthResult.Success)
        val user = (result as AuthResult.Success).user
        assertEquals("Haji Rahmat", user.name)
        // Email is normalized to lowercase / trimmed.
        assertEquals("haji@example.com", user.email)
        assertEquals("Haji", user.firstName)
        assertEquals(user, repo.sessionUser.value)
    }

    @Test
    fun `register rejects an invalid email`() = runTest {
        val result = repo.register("Rahmat", "not-an-email", "secret123")
        assertTrue(result is AuthResult.Failure)
        assertNull(repo.sessionUser.value)
    }

    @Test
    fun `register rejects a short password`() = runTest {
        val result = repo.register("Rahmat", "a@b.co", "12345")
        assertTrue(result is AuthResult.Failure)
        assertEquals("Password must be at least 6 characters", (result as AuthResult.Failure).message)
    }

    @Test
    fun `register rejects a blank name`() = runTest {
        val result = repo.register("   ", "a@b.co", "secret123")
        assertTrue(result is AuthResult.Failure)
        assertNull(repo.sessionUser.value)
    }

    @Test
    fun `register rejects a duplicate email regardless of case`() = runTest {
        repo.register("First", "dup@example.com", "secret123")
        val result = repo.register("Second", "DUP@example.com", "another123")

        assertTrue(result is AuthResult.Failure)
        assertEquals("An account with this email already exists", (result as AuthResult.Failure).message)
    }

    @Test
    fun `login succeeds with correct credentials`() = runTest {
        repo.register("Rahmat", "a@b.co", "secret123")
        repo.signOut()

        val result = repo.login("a@b.co", "secret123")
        assertTrue(result is AuthResult.Success)
        assertNotNull(repo.sessionUser.value)
    }

    @Test
    fun `login fails with a vague message on wrong password`() = runTest {
        repo.register("Rahmat", "a@b.co", "secret123")
        repo.signOut()

        val result = repo.login("a@b.co", "wrongpass")
        assertTrue(result is AuthResult.Failure)
        assertEquals("Email or password is incorrect", (result as AuthResult.Failure).message)
        assertNull(repo.sessionUser.value)
    }

    @Test
    fun `login fails with the same vague message for an unknown email`() = runTest {
        val result = repo.login("nobody@example.com", "secret123")
        assertTrue(result is AuthResult.Failure)
        assertEquals("Email or password is incorrect", (result as AuthResult.Failure).message)
    }

    @Test
    fun `sign out clears the session but keeps the account`() = runTest {
        repo.register("Rahmat", "a@b.co", "secret123")
        repo.signOut()

        assertNull(repo.sessionUser.value)
        // The account still exists, so signing back in works.
        assertTrue(repo.login("a@b.co", "secret123") is AuthResult.Success)
    }

    @Test
    fun `session and accounts survive a new repository instance`() = runTest {
        repo.register("Rahmat", "a@b.co", "secret123")

        // Simulate a process restart: a new repository over the same storage.
        val restored = LocalAuthRepository(prefs, time)
        assertEquals("a@b.co", restored.sessionUser.value?.email)
    }

    @Test
    fun `signed-out session does not restore on restart but the account remains`() = runTest {
        repo.register("Rahmat", "a@b.co", "secret123")
        repo.signOut()

        val restored = LocalAuthRepository(prefs, time)
        assertNull(restored.sessionUser.value)
        assertTrue(restored.login("a@b.co", "secret123") is AuthResult.Success)
    }

    @Test
    fun `stored data never contains the plaintext password`() = runTest {
        repo.register("Rahmat", "a@b.co", "secret123")

        val stored = prefs.getString(LocalAuthRepository.KEY_STORE, "") ?: ""
        assertFalse(stored.contains("secret123"))
    }
}

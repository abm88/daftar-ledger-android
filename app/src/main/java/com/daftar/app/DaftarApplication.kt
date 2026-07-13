package com.daftar.app

import android.app.Application
import com.daftar.app.data.session.UserDataSession
import com.daftar.app.domain.repository.AuthRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

@HiltAndroidApp
class DaftarApplication : Application() {

    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var userDataSession: UserDataSession

    override fun onCreate() {
        super.onCreate()
        // v18 boot: if a session survived the restart, load that user's saved
        // shop before the first frame so they see their own daftar, not the
        // seed. The blob is tiny, so the synchronous read is imperceptible.
        authRepository.sessionUser.value?.let { user ->
            runBlocking { userDataSession.beginSession(user.id) }
        }
    }
}

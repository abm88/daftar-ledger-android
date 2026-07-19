package com.daftar.app

import android.app.Application
import com.daftar.app.domain.repository.AuthRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class DaftarApplication : Application() {

    // Eager construction restores and validates any persisted API session at launch.
    @Inject lateinit var authRepository: AuthRepository
}

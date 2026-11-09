package com.silasha.diaryvault

import android.app.Application
import com.silasha.diaryvault.config.FirebaseBootstrap

class DiaryVaultApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseBootstrap.configureIfNeeded(this)
    }
}

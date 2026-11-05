package com.diaryvault.app

import android.app.Application
import com.diaryvault.app.config.FirebaseBootstrap

class DiaryVaultApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseBootstrap.configureIfNeeded(this)
    }
}

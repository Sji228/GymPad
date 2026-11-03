package com.gympad

import android.app.Application
import com.gympad.data.GymRepository
import com.gympad.data.LanguageStore
import com.gympad.data.createGymDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

class GymPadApp : Application() {
    val repository: GymRepository by lazy { GymRepository(createGymDatabase(this)) }

    val languageStore: LanguageStore by lazy {
        val preferences = getSharedPreferences("settings", MODE_PRIVATE)
        object : LanguageStore {
            override fun load() = preferences.getString("language", null)
            override fun save(code: String) = preferences.edit().putString("language", code).apply()
        }
    }

    val installEpochDay: Long by lazy {
        val installedAt = packageManager.getPackageInfo(packageName, 0).firstInstallTime
        Instant.ofEpochMilli(installedAt).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
    }

    override fun onCreate() {
        super.onCreate()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch { repository.seedIfEmpty() }
    }
}

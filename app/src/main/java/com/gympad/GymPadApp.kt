package com.gympad

import android.app.Application
import com.gympad.data.FoodCatalog
import com.gympad.data.FoodRepository
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
    private val database by lazy { createGymDatabase(this) }
    val repository: GymRepository by lazy { GymRepository(database) }

    // The catalog copy can be rebuilt from the APK at any time, so it stays out of backups.
    val foodRepository: FoodRepository by lazy { FoodRepository(database, FoodCatalog(noBackupFilesDir)) }

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

package com.gympad.data

import android.content.Context
import androidx.room.Room

fun createGymDatabase(context: Context): GymDatabase {
    val appContext = context.applicationContext
    return Room.databaseBuilder<GymDatabase>(
        context = appContext,
        name = appContext.getDatabasePath("gympad.db").absolutePath,
    ).buildGymDatabase()
}

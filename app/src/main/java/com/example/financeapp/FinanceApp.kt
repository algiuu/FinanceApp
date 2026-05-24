package com.example.financeapp

import android.app.Application
import androidx.room.Room
import com.example.financeapp.data.AppDatabase

class FinanceApp : Application() {
    val database by lazy {
        Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "finance-db"
        ).fallbackToDestructiveMigration().build()
    }
}
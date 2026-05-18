package com.example.debt_tracker.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.debt_tracker.data.dao.DebtDao
import com.example.debt_tracker.data.dao.PaymentDao
import com.example.debt_tracker.data.model.Debt
import com.example.debt_tracker.data.model.Payment

@Database(entities = [Debt::class, Payment::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun debtDao(): DebtDao
    abstract fun paymentDao(): PaymentDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "debt-tracker.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}

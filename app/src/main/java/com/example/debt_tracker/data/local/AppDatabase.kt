package com.example.debt_tracker.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.debt_tracker.data.dao.DebtDao
import com.example.debt_tracker.data.dao.PaymentDao
import com.example.debt_tracker.data.model.Debt
import com.example.debt_tracker.data.model.Payment

@Database(entities = [Debt::class, Payment::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun debtDao(): DebtDao
    abstract fun paymentDao(): PaymentDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE debts ADD COLUMN interestRate REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE debts ADD COLUMN interestType TEXT NOT NULL DEFAULT 'FIXED'")
                db.execSQL("ALTER TABLE debts ADD COLUMN paymentType TEXT NOT NULL DEFAULT 'INSTALLMENT'")
                db.execSQL("ALTER TABLE debts ADD COLUMN creditLimit REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE debts ADD COLUMN remainingBalance REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE debts ADD COLUMN lastInterestCalculationDate INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE debts ADD COLUMN minimumPaymentPercent REAL NOT NULL DEFAULT 0.0")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "debt-tracker.db"
                )
                .addMigrations(MIGRATION_1_2)
                .build().also { INSTANCE = it }
            }
        }
    }
}

package com.example.debt_tracker.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "payments",
    foreignKeys = [
        ForeignKey(
            entity = Debt::class,
            parentColumns = ["id"],
            childColumns = ["debtId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("debtId")]
)
data class Payment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val debtId: Long,
    val paymentDate: Long,
    val amount: Double
)

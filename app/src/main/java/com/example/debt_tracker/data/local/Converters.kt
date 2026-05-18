package com.example.debt_tracker.data.local

import androidx.room.TypeConverter
import com.example.debt_tracker.data.model.InterestType
import com.example.debt_tracker.data.model.PaymentType

class Converters {
    @TypeConverter
    fun fromInterestType(value: InterestType): String {
        return value.name
    }

    @TypeConverter
    fun toInterestType(value: String): InterestType {
        return try {
            InterestType.valueOf(value)
        } catch (e: Exception) {
            InterestType.FIXED
        }
    }

    @TypeConverter
    fun fromPaymentType(value: PaymentType): String {
        return value.name
    }

    @TypeConverter
    fun toPaymentType(value: String): PaymentType {
        return try {
            PaymentType.valueOf(value)
        } catch (e: Exception) {
            PaymentType.INSTALLMENT
        }
    }
}

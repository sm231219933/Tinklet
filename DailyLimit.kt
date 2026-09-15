package com.tinklet.bharatdatingapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_limits")
data class DailyLimit(
    @PrimaryKey val date: String, // Format: YYYY-MM-DD
    val count: Int
)

package com.tinklet.bharatdatingapp.data.local

import androidx.room.*

@Dao
interface DailyLimitDao {
    @Query("SELECT * FROM daily_limits WHERE date = :date")
    suspend fun getLimitForDate(date: String): DailyLimit?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateLimit(limit: DailyLimit)
}

package com.tinklet.bharatdatingapp.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles WHERE connectionStatus = 'NONE'")
    fun getDiscoveryProfiles(): Flow<List<UserProfile>>

    @Query("SELECT * FROM profiles")
    fun getAllProfilesFlow(): Flow<List<UserProfile>>

    @Query("SELECT * FROM profiles")
    suspend fun getAllProfiles(): List<UserProfile>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfiles(profiles: List<UserProfile>)

    @Query("SELECT * FROM profiles WHERE email = :email LIMIT 1")
    suspend fun getProfileByEmail(email: String): UserProfile?

    @Query("SELECT * FROM profiles WHERE phoneNumber = :phone AND phoneNumber != '' LIMIT 1")
    suspend fun getProfileByPhone(phone: String): UserProfile?

    @Query("DELETE FROM profiles WHERE isMe = 1")
    suspend fun deleteMyProfile()

    @Query("SELECT * FROM profiles WHERE isMe = 1 LIMIT 1")
    suspend fun getMyProfile(): UserProfile?

    @Update
    suspend fun updateProfile(profile: UserProfile)

    @Query("DELETE FROM profiles")
    suspend fun deleteAllProfiles()

    @Query("UPDATE profiles SET connectionStatus = 'NONE' WHERE connectionStatus = 'VIEWED' AND isMe = 0")
    suspend fun resetViewedOnly()

    @Query("SELECT * FROM profiles WHERE connectionStatus = 'ACCEPTED'")
    fun getMatches(): Flow<List<UserProfile>>

    @Query("SELECT COUNT(*) FROM profiles")
    suspend fun getProfileCount(): Int
}

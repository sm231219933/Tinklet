package com.tinklet.bharatdatingapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "profiles")
data class UserProfile(
    @PrimaryKey val email: String = "", 
    val userId: String = "", 
    val name: String = "Tinklet User",
    val age: Int = 18,
    val gender: String = "Neutral",
    val phoneNumber: String = "",
    val country: String = "",
    val state: String = "",
    val dob: String = "", 
    val bio: String = "",
    val photoUri: String = "",
    val secondaryPhotos: List<String> = emptyList(),
    val introVideoUri: String = "",
    
    val height: String = "",
    val religion: String = "",
    val education: String = "",
    val profession: String = "",
    val diet: String = "", 
    val habits: String = "", 
    val language: String = "",
    val intentions: String = "", 
    val interests: String = "", 
    
    val coins: Int = 25,
    val boostBid: Int = 0, 
    val boostUntil: Long = 0,
    val isMe: Boolean = false,
    val isPremium: Boolean = false,
    val isAdFree: Boolean = false,
    val referralCode: String = "",
    val referredBy: String = "",
    val deviceId: String = "",
    val badgeType: String = "NONE",
    val badgeExpiry: Long = 0L,
    
    val isDeactivated: Boolean = false,
    val lastGiftReceived: String = "",
    val connectionStatus: String = "NONE",
    val isBlocked: Boolean = false,
    val reportCount: Int = 0,
    val password: String = "",
    val lastActive: Long = 0,
    val deletionRequestedAt: Long = 0,
    
    val interactions: Map<String, String> = emptyMap()
)

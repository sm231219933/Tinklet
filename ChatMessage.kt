package com.tinklet.bharatdatingapp.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "chat_messages",
    indices = [Index(value = ["matchId", "senderId", "timestamp", "text"], unique = true)]
)
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val messageId: String = UUID.randomUUID().toString(),
    val status: String = "SENT", // SENT, DELIVERED, READ
    val matchId: String, // Changed to partner email for cloud sync
    val senderId: String, // "ME" or "OTHER"
    val text: String? = null,
    val imageUri: String? = null,
    val voiceUrl: String? = null,
    val videoNoteUrl: String? = null,
    val duration: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val isSafe: Boolean = true,
    val isEdited: Boolean = false,
    val isDeletedForEveryone: Boolean = false
)

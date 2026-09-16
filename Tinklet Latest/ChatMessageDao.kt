package com.tinklet.bharatdatingapp.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE matchId = :matchId ORDER BY timestamp ASC")
    fun getMessagesForMatch(matchId: String): Flow<List<ChatMessage>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessage(message: ChatMessage)

    @Update
    suspend fun updateMessage(message: ChatMessage)

    @Query("DELETE FROM chat_messages WHERE id = :id")
    suspend fun deleteMessage(id: Int)

    @Query("DELETE FROM chat_messages WHERE matchId = :matchId")
    suspend fun deleteChat(matchId: String)

    @Query("UPDATE chat_messages SET status = :status WHERE messageId = :messageId")
    suspend fun updateMessageStatus(messageId: String, status: String)

    @Query("UPDATE chat_messages SET status = 'READ' WHERE matchId = :matchId AND senderId = 'OTHER' AND status != 'READ'")
    suspend fun markAllAsRead(matchId: String)
}

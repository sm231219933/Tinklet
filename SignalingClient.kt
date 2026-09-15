package com.tinklet.bharatdatingapp.calling

import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import java.net.URISyntaxException

/**
 * Signaling server (Socket.io) client for WebRTC call setup and real-time messaging.
 */
class SignalingClient(
    private val serverUrl: String, 
    private val myUserId: String,
    private val listener: SignalingListener
) {
    private var socket: Socket? = null

    interface SignalingListener {
        fun onIncomingCall(fromUserId: String, fromUserName: String, offer: String, callType: String)
        fun onCallAnswered(answer: String)
        fun onCallRejected()
        fun onCallEnded()
        fun onIceCandidateReceived(candidate: String)
        fun onChatMessageReceived(fromUserId: String, text: String, messageId: String? = null) {}
        fun onMessageDeleted(fromUserId: String, msgTimestamp: Long) {}
        fun onMessageEdited(fromUserId: String, msgTimestamp: Long, newText: String) {}
        fun onMessageDelivered(fromUserId: String, messageId: String) {}
        fun onMessageRead(fromUserId: String, messageId: String) {}
        fun onUserBlocked(fromUserId: String) {}
        fun onCallFailed(reason: String)
        fun onConnected()
        fun onLiveCountUpdate(count: Int) {}
    }

    fun connect() {
        try {
            val opts = IO.Options().apply {
                forceNew = true
                reconnection = true
            }
            socket = IO.socket(serverUrl, opts)

            socket?.on(Socket.EVENT_CONNECT) {
                Log.d("SignalingClient", "Connected to signaling server")
                socket?.emit("register", myUserId)
                listener.onConnected()
            }

            socket?.on("call:incoming") { args ->
                val data = args[0] as JSONObject
                listener.onIncomingCall(
                    fromUserId = data.getString("fromUserId"),
                    fromUserName = data.optString("fromUserName", "Tinklet User"),
                    offer = data.getString("offer"),
                    callType = data.optString("callType", "audio")
                )
            }

            socket?.on("call:answered") { args ->
                val data = args[0] as JSONObject
                listener.onCallAnswered(data.getString("answer"))
            }

            socket?.on("call:rejected") {
                listener.onCallRejected()
            }

            socket?.on("call:ended") {
                listener.onCallEnded()
            }

            socket?.on("call:ice-candidate") { args ->
                val data = args[0] as JSONObject
                listener.onIceCandidateReceived(data.getString("candidate"))
            }

            // CHAT EVENTS
            socket?.on("chat:message") { args ->
                val data = args[0] as JSONObject
                listener.onChatMessageReceived(
                    data.getString("fromUserId"),
                    data.optString("text", data.optString("sdp", "")),
                    if (data.has("messageId")) data.getString("messageId") else null
                )
            }

            socket?.on("message:delivered") { args ->
                val data = args[0] as JSONObject
                listener.onMessageDelivered(data.getString("fromUserId"), data.getString("messageId"))
            }

            socket?.on("message:read") { args ->
                val data = args[0] as JSONObject
                listener.onMessageRead(data.getString("fromUserId"), data.getString("messageId"))
            }

            socket?.on("message:deleted") { args ->
                val data = args[0] as JSONObject
                listener.onMessageDeleted(data.getString("fromUserId"), data.getLong("timestamp"))
            }

            socket?.on("message:edited") { args ->
                val data = args[0] as JSONObject
                listener.onMessageEdited(data.getString("fromUserId"), data.getLong("timestamp"), data.getString("newText"))
            }

            socket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
                val err = args[0] as Exception
                Log.e("SignalingClient", "Connection error", err)
                listener.onCallFailed("Connection failed: ${err.message}")
            }

            socket?.connect()

        } catch (e: URISyntaxException) {
            Log.e("SignalingClient", "Invalid server URL", e)
        }
    }

    // GENERIC SIGNAL FOR COMPATIBILITY
    fun sendSignal(targetId: String, type: String, sdp: String? = null, messageId: String? = null) {
        val eventName = when(type) {
            "chat_message" -> "chat:message"
            "message_delivered" -> "message:delivered"
            "message_read" -> "message:read"
            "delete_message" -> "message:deleted"
            "edit_message" -> "message:edited"
            else -> type
        }
        val data = JSONObject().apply {
            put("toUserId", targetId)
            put("fromUserId", myUserId)
            put("text", sdp)
            put("sdp", sdp)
            put("messageId", messageId)
        }
        socket?.emit(eventName, data)
    }

    fun sendOffer(toUserId: String, fromUserName: String, offerSdp: String, callType: String) {
        val data = JSONObject().apply {
            put("toUserId", toUserId)
            put("fromUserId", myUserId)
            put("fromUserName", fromUserName)
            put("offer", offerSdp)
            put("callType", callType)
        }
        socket?.emit("call:offer", data)
    }

    fun sendAnswer(toUserId: String, answerSdp: String) {
        val data = JSONObject().apply {
            put("toUserId", toUserId)
            put("answer", answerSdp)
        }
        socket?.emit("call:answer", data)
    }

    fun sendReject(toUserId: String) {
        val data = JSONObject().apply {
            put("toUserId", toUserId)
        }
        socket?.emit("call:reject", data)
    }

    fun sendIceCandidate(toUserId: String, candidate: String) {
        val data = JSONObject().apply {
            put("toUserId", toUserId)
            put("candidate", candidate)
        }
        socket?.emit("call:ice-candidate", data)
    }

    fun sendEndCall(toUserId: String) {
        val data = JSONObject().apply {
            put("toUserId", toUserId)
        }
        socket?.emit("call:end", data)
    }

    fun sendDeliveryReceipt(toUserId: String, messageId: String) {
        val data = JSONObject().apply {
            put("toUserId", toUserId)
            put("fromUserId", myUserId)
            put("messageId", messageId)
        }
        socket?.emit("message:delivered", data)
    }

    fun sendReadReceipt(toUserId: String, messageId: String) {
        val data = JSONObject().apply {
            put("toUserId", toUserId)
            put("fromUserId", myUserId)
            put("messageId", messageId)
        }
        socket?.emit("message:read", data)
    }

    fun sendDeleteMessage(toUserId: String, timestamp: Long) {
        val data = JSONObject().apply {
            put("toUserId", toUserId)
            put("fromUserId", myUserId)
            put("timestamp", timestamp)
        }
        socket?.emit("message:deleted", data)
    }

    fun sendEditMessage(toUserId: String, timestamp: Long, newText: String) {
        val data = JSONObject().apply {
            put("toUserId", toUserId)
            put("fromUserId", myUserId)
            put("timestamp", timestamp)
            put("newText", newText)
        }
        socket?.emit("message:edited", data)
    }

    fun disconnect() {
        socket?.disconnect()
        socket?.off()
    }
}

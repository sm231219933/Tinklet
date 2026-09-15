package com.tinklet.bharatdatingapp.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.tinklet.bharatdatingapp.MainActivity
import com.tinklet.bharatdatingapp.R
import com.tinklet.bharatdatingapp.utils.NotificationHelper

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        Log.d("FCM", "From: ${remoteMessage.from}")

        // 1. Handle Data Payload (Preferred for custom logic)
        if (remoteMessage.data.isNotEmpty()) {
            val type = remoteMessage.data["type"]
            val fromUserId = remoteMessage.data["fromUserId"] ?: ""
            val title = remoteMessage.data["title"] ?: "Tinklet"
            val body = remoteMessage.data["body"] ?: ""

            when (type) {
                "chat" -> {
                    NotificationHelper.showMessageNotification(this, title, body, fromUserId)
                }
                "call" -> {
                    val callType = remoteMessage.data["callType"] ?: "audio"
                    val offerSdp = remoteMessage.data["offerSdp"] ?: ""
                    NotificationHelper.showCallNotification(this, title, callType, fromUserId, offerSdp)
                }
                else -> {
                    sendNotification(title, body)
                }
            }
            return
        }

        // 2. Handle Notification Payload (Fallback)
        remoteMessage.notification?.let {
            sendNotification(it.title ?: "Tinklet", it.body ?: "New update!")
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "Refreshed token: $token")
        // TODO: Send this token to your server to link it with the user
    }

    private fun sendNotification(title: String, messageBody: String) {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE)

        val channelId = NotificationHelper.GENERAL_CHANNEL_ID
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Permission check for Android 13+
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || 
            androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == 
            android.content.pm.PackageManager.PERMISSION_GRANTED) {
            notificationManager.notify(0, notificationBuilder.build())
        }
    }
}

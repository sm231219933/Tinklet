package com.tinklet.bharatdatingapp.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.tinklet.bharatdatingapp.MainActivity
import com.tinklet.bharatdatingapp.calling.IncomingCallActivity

object NotificationHelper {
    private const val MESSAGE_CHANNEL_ID = "tinklet_messages"
    private const val CALL_CHANNEL_ID = "tinklet_calls"
    const val GENERAL_CHANNEL_ID = "tinklet_general"
    
    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            val generalChannel = NotificationChannel(
                GENERAL_CHANNEL_ID,
                "General Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            
            val messageChannel = NotificationChannel(
                MESSAGE_CHANNEL_ID,
                "Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Chat messages from your matches"
                enableLights(true)
            }

            val callChannel = NotificationChannel(
                CALL_CHANNEL_ID,
                "Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming voice and video calls"
                enableLights(true)
                setSound(android.provider.Settings.System.DEFAULT_RINGTONE_URI, null)
            }

            notificationManager.createNotificationChannel(generalChannel)
            notificationManager.createNotificationChannel(messageChannel)
            notificationManager.createNotificationChannel(callChannel)
        }
    }

    fun showMessageNotification(context: Context, senderName: String, text: String, senderEmail: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("target_email", senderEmail)
            putExtra("open_chat", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            senderEmail.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, MESSAGE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(senderName)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(senderEmail.hashCode(), builder.build())
    }

    fun showCallNotification(context: Context, callerName: String, callType: String, callerEmail: String, offerSdp: String) {
        val intent = Intent(context, IncomingCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(IncomingCallActivity.EXTRA_FROM_USER_ID, callerEmail)
            putExtra(IncomingCallActivity.EXTRA_FROM_USER_NAME, callerName)
            putExtra(IncomingCallActivity.EXTRA_OFFER_SDP, offerSdp)
            putExtra(IncomingCallActivity.EXTRA_CALL_TYPE, callType)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CALL_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle(callerName)
            .setContentText("Incoming $callType call...")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(false)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_call, "Answer", pendingIntent)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1001, builder.build())
    }

    fun cancelCallNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(1001)
    }
}

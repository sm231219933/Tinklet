package com.tinklet.bharatdatingapp

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.tinklet.bharatdatingapp.data.remote.RetrofitClient
import com.tinklet.bharatdatingapp.utils.AdManager
import com.tinklet.bharatdatingapp.utils.NotificationHelper
import com.tinklet.bharatdatingapp.utils.SoundManager

class BharatApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Networking
        RetrofitClient.init(this)

        // Global Crash Logger to catch hidden startup errors
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("CRITICAL_CRASH", "Uncaught Exception in thread ${thread.name}", throwable)
            // Optionally, save to file or restart app
        }

        try {
            // Initialize Firebase
            FirebaseApp.initializeApp(this)
        } catch (e: Exception) { Log.e("BharatApp", "Firebase init fail", e) }
        
        try {
            // Initialize Sound Manager
            SoundManager.init(this)
            NotificationHelper.createNotificationChannels(this)
        } catch (e: Exception) { Log.e("BharatApp", "Notification/Sound init fail", e) }
        
        // Mobile Ads will be initialized in MainActivity as it needs an Activity context for loading ads
    }
}

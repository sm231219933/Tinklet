package com.tinklet.bharatdatingapp.utils

import android.annotation.SuppressLint
import android.content.Context
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager

object SimUtils {
    @SuppressLint("MissingPermission")
    fun getAvailableSimNumbers(context: Context): List<String> {
        val numbers = mutableListOf<String>()
        try {
            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            val activeSubscriptions = subscriptionManager.activeSubscriptionInfoList
            
            activeSubscriptions?.forEach { info ->
                // Note: On newer Android versions, this might return null if the app 
                // is not a default dialer or system app.
                val number = info.number
                if (!number.isNullOrBlank()) {
                    numbers.add(number)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return numbers
    }
}

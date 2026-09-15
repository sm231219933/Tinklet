package com.tinklet.bharatdatingapp.utils

import android.app.Activity
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

object AdManager {
    private var rewardedAd: RewardedAd? = null
    private const val TEST_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
    private var isLoading = false

    fun init(activity: Activity) {
        MobileAds.initialize(activity) { }
        loadRewardedAd(activity)
    }

    private fun loadRewardedAd(activity: Activity) {
        if (isLoading || rewardedAd != null) return
        
        try {
            isLoading = true
            val adRequest = AdRequest.Builder().build()
            RewardedAd.load(activity, TEST_AD_UNIT_ID, adRequest, object : RewardedAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.e("Ads", "Failed to load: ${adError.message}")
                    rewardedAd = null
                    isLoading = false
                }

                override fun onAdLoaded(ad: RewardedAd) {
                    Log.d("Ads", "Ad loaded successfully")
                    rewardedAd = ad
                    isLoading = false
                }
            })
        } catch (e: Exception) {
            Log.e("Ads", "RewardedAd.load throw", e)
            isLoading = false
        }
    }

    fun showRewardedAd(activity: Activity, onRewardEarned: () -> Unit) {
        if (rewardedAd != null) {
            rewardedAd?.show(activity) { _ ->
                onRewardEarned()
                rewardedAd = null
                loadRewardedAd(activity)
            }
        } else {
            Log.e("Ads", "Ad not ready yet")
            loadRewardedAd(activity)
        }
    }
}

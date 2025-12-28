package com.example.mqttpanelcraft.utils

import android.app.Activity
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

object AdManager {

    private const val TAG = "AdManager"

    // Real Ad Unit IDs (User Provided)
    const val BANNER_AD_ID = "ca-app-pub-4344043793626988/3938962153"
    const val INTERSTITIAL_AD_ID = "ca-app-pub-4344043793626988/5500182186"
    const val REWARDED_AD_ID = "ca-app-pub-4344043793626988/4187100512"

    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null
    
    var isAdsDisabled = false

    fun initialize(context: android.content.Context) {
        val prefs = context.getSharedPreferences("AppSettings", android.content.Context.MODE_PRIVATE)
        isAdsDisabled = prefs.getBoolean("ads_disabled", false)
        Log.d(TAG, "Ads Disabled: $isAdsDisabled")

        if (!isAdsDisabled) {
            MobileAds.initialize(context) { status ->
                Log.d(TAG, "AdMob Initialized: ${status.adapterStatusMap}")
            }
        }
    }
    
    fun setDisabled(disabled: Boolean, context: android.content.Context) {
        isAdsDisabled = disabled
        context.getSharedPreferences("AppSettings", android.content.Context.MODE_PRIVATE)
            .edit().putBoolean("ads_disabled", disabled).apply()
        
        if (!disabled) {
             // Re-init if re-enabled
             initialize(context)
        }
    }

    // --- Banner Ads ---
    
    fun loadBannerAd(activity: Activity, container: FrameLayout) {
        if (isAdsDisabled) {
            container.visibility = View.GONE
            container.removeAllViews()
            return
        }

        val adView = AdView(activity)
        adView.setAdSize(AdSize.BANNER)
        adView.adUnitId = BANNER_AD_ID

        // Create a wrapper for Ad + Close Button
        val wrapper = FrameLayout(activity)
        wrapper.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        // Ad Layout Params
        val adParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.CENTER
        }
        
        wrapper.addView(adView, adParams)

        // Close Button
        val closeBtn = ImageButton(activity)
        closeBtn.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
        closeBtn.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        val btnParams = FrameLayout.LayoutParams(
            60, 60
        ).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.END
        }
        closeBtn.setOnClickListener {
            container.visibility = View.GONE
            adView.destroy()
        }
        
        wrapper.addView(closeBtn, btnParams)

        container.removeAllViews()
        container.addView(wrapper)

        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)
        
        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
               container.visibility = View.VISIBLE
            }
            override fun onAdFailedToLoad(error: LoadAdError) {
                Log.e(TAG, "Banner failed to load: ${error.message}")
                container.visibility = View.GONE
            }
        }
    }

    // --- Interstitial Ads ---

    fun loadInterstitial(context: android.content.Context) {
        if (isAdsDisabled) {
            interstitialAd = null
            return
        }
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(context, INTERSTITIAL_AD_ID, adRequest, object : InterstitialAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.e(TAG, "Interstitial failed to load: ${adError.message}")
                interstitialAd = null
            }

            override fun onAdLoaded(ad: InterstitialAd) {
                Log.d(TAG, "Interstitial loaded")
                interstitialAd = ad
            }
        })
    }

    fun showInterstitial(activity: Activity, onAdClosed: () -> Unit = {}) {
        if (isAdsDisabled) {
             onAdClosed()
             return
        }

        if (interstitialAd != null) {
            interstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "Interstitial dismissed")
                    interstitialAd = null
                    loadInterstitial(activity)
                    onAdClosed()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.e(TAG, "Interstitial failed to show")
                    interstitialAd = null
                    onAdClosed()
                }
            }
            interstitialAd?.show(activity)
        } else {
            Log.d(TAG, "Interstitial not ready")
            loadInterstitial(activity)
            onAdClosed()
        }
    }

    // --- Rewarded Ads ---

    fun loadRewarded(context: android.content.Context) {
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(context, REWARDED_AD_ID, adRequest, object : RewardedAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.e(TAG, "Rewarded failed to load: ${adError.message}")
                rewardedAd = null
            }

            override fun onAdLoaded(ad: RewardedAd) {
                Log.d(TAG, "Rewarded loaded")
                rewardedAd = ad
            }
        })
    }

    fun isRewardedReady(): Boolean {
        return rewardedAd != null
    }

    fun showRewarded(activity: Activity, onReward: () -> Unit, onClosed: () -> Unit) {
        if (rewardedAd != null) {
            rewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    rewardedAd = null
                    loadRewarded(activity)
                    onClosed()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    rewardedAd = null
                    onClosed()
                }
            }
            rewardedAd?.show(activity) { rewardItem ->
                // User earned reward
                Log.d(TAG, "User earned reward: ${rewardItem.amount} ${rewardItem.type}")
                onReward()
            }
        } else {
            Log.d(TAG, "Rewarded ad not ready")
            loadRewarded(activity)
            // Do NOT auto-reward. Just close (which implies failure to user).
            onClosed() 
        }
    }
}

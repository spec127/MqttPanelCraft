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
    
    // NOTE: isAdsDisabled property is removed. Use PremiumManager.isPremium(context) instead.

    fun initialize(context: android.content.Context) {
        val isPremium = PremiumManager.isPremium(context)
        Log.d(TAG, "Initializing AdManager. Premium Status: $isPremium")

        // Always initialize MobileAds. 
        // Even if premium, we might need it initialized if user signs out or status changes dynamically.
        MobileAds.initialize(context) { status ->
            Log.d(TAG, "AdMob Initialized: ${status.adapterStatusMap}")
        }
    }
    
    // Deprecated: Use PremiumManager directly if possible. keeping for easy refactor.
    fun setDisabled(disabled: Boolean, context: android.content.Context) {
        PremiumManager.setPremium(context, disabled)
    }

    // --- Banner Ads ---

    private fun getAdSize(activity: Activity): AdSize {
        // Determine the screen width (less decorations) to use for the ad width.
        val display = activity.windowManager.defaultDisplay
        val outMetrics = android.util.DisplayMetrics()
        display.getMetrics(outMetrics)

        val density = outMetrics.density
        
        // Use full width of screen
        var adWidthPixels = outMetrics.widthPixels.toFloat()
        
        // If you had margins, you'd subtract them here. 
        // For now we use full width.
        if (adWidthPixels == 0f) {
            adWidthPixels = outMetrics.widthPixels.toFloat() // Fallback
        }

        val adWidth = (adWidthPixels / density).toInt()
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, adWidth)
    }
    
    fun loadBannerAd(activity: Activity, container: FrameLayout, fab: View? = null) {
        // Helper to clear and hide container
        fun hideBanner() {
            container.removeAllViews()
            container.visibility = View.GONE
            // Reset FAB position if needed
            fab?.animate()?.translationY(0f)?.setDuration(300)?.start()
        }

        // 1. Check Premium Status
        if (PremiumManager.isPremium(activity)) {
            Log.d(TAG, "Premium User: Hiding Banner Ad")
            hideBanner()
            return
        }

        // 2. Load Ad
        val adView = AdView(activity)
        
        // Use Adaptive Banner Size
        val adSize = getAdSize(activity)
        adView.setAdSize(adSize)
        
        adView.adUnitId = BANNER_AD_ID

        // Create a wrapper for Ad + Close Button
        val wrapper = FrameLayout(activity)
        // Reset layout params to wrap the adaptive height
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

        container.removeAllViews()
        container.addView(wrapper)

        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)
        
        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                // Check again in case status changed
               if (PremiumManager.isPremium(activity)) {
                   hideBanner()
                   return
               }

               container.visibility = View.VISIBLE
               // Animate FAB up
               val heightPx = adSize.getHeightInPixels(activity).toFloat()
               fab?.animate()?.translationY(-heightPx)?.setDuration(300)?.start()
            }
            override fun onAdFailedToLoad(error: LoadAdError) {
                Log.e(TAG, "Banner failed to load: ${error.message}")
                container.visibility = View.GONE
                // Reset FAB position
                fab?.animate()?.translationY(0f)?.setDuration(300)?.start()
            }
        }
    }

    // --- Interstitial Ads ---

    fun loadInterstitial(context: android.content.Context) {
        if (PremiumManager.isPremium(context)) {
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
        if (PremiumManager.isPremium(activity)) {
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
        if (PremiumManager.isPremium(context)) {
            rewardedAd = null
            return
        }
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
        if (PremiumManager.isPremium(activity)) {
             // If premium, grant reward immediately without showing ad
             onReward()
             onClosed()
             return
        }

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
            // Do NOT auto-reward. Just close.
            onClosed() 
        }
    }
    
    // Helper used by PremiumManager to refresh UI if needed
    fun refreshAdState(context: android.content.Context) {
         // Logic to refresh ads if visible could go here
         // For now, next loadBannerAd call will handle it.
    }
}

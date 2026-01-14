package com.example.mqttpanelcraft.utils

import android.content.Context

object PremiumManager {
    private const val PREFS_NAME = "AppSettings"
    // We map premium status to the legacy "ads_disabled" key to maintain current behavior,
    // as the user mentioned they are currently using SP to simulate it.
    private const val KEY_PREMIUM_STATUS = "ads_disabled"

    /**
     * Checks if the user has Premium status.
     * This is the single source of truth for ad display logic.
     */
    fun isPremium(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_PREMIUM_STATUS, false)
    }

    /**
     * Sets the Premium status.
     * To be called when a purchase is successful or for debugging/simulation.
     */
    fun setPremium(context: Context, isPremium: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_PREMIUM_STATUS, isPremium).apply()
        
        // Notify AdManager to update its state if necessary (e.g. hide existing banners)
        // Since AdManager logic will now pull from here, we might need a refresh method there 
        // if it caches anything or has visible views.
        //AdManager.refreshAdState(context)
    }
}

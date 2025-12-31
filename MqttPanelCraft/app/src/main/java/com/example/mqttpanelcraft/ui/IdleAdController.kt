package com.example.mqttpanelcraft.ui

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log

class IdleAdController(
    private val activity: Activity,
    private val onAdClosed: () -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private val intervals = listOf(30L, 60L, 180L, 300L, 600L) // Seconds
    private var currentIntervalIndex = 0
    private var isRunning = false

    private val idleRunnable = Runnable {
        showAdAndScheduleNext()
    }

    fun start() {
        if (isRunning) return
        isRunning = true
        currentIntervalIndex = 0 
        scheduleNext(intervals[0])
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        handler.removeCallbacks(idleRunnable)
    }

    fun onUserInteraction() {
        if (isRunning) {
            // Reset timer for CURRENT interval
            handler.removeCallbacks(idleRunnable)
            val delay = if (currentIntervalIndex < intervals.size) intervals[currentIntervalIndex] else 600L
            scheduleNext(delay)
        }
    }

    private fun scheduleNext(delaySeconds: Long) {
        handler.removeCallbacks(idleRunnable)
        handler.postDelayed(idleRunnable, delaySeconds * 1000)
        Log.d("IdleAd", "Scheduled ad in ${delaySeconds}s")
    }

    private fun showAdAndScheduleNext() {
        if (!isRunning) return

        com.example.mqttpanelcraft.utils.AdManager.showInterstitial(activity) {
            // On Ad Closed
            if (currentIntervalIndex < intervals.size - 1) {
                currentIntervalIndex++
            }
            val delay = if (currentIntervalIndex < intervals.size) intervals[currentIntervalIndex] else 600L
            scheduleNext(delay)

            onAdClosed()
        }
    }
}

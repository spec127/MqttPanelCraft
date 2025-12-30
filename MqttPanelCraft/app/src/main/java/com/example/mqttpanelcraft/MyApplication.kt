package com.example.mqttpanelcraft

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Date
import kotlin.system.exitProcess

class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Restore Theme Preference
        val prefs = getSharedPreferences("AppSettings", android.content.Context.MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean("dark_mode", false)
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
            else androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
        )

        // Init Data Layer
        com.example.mqttpanelcraft.data.ProjectRepository.initialize(this)

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            handleUncaughtException(thread, throwable)
        }
    }

    private fun handleUncaughtException(thread: Thread, e: Throwable) {
        e.printStackTrace()
        
        try {
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            val stackTrace = sw.toString()
            
            val logContent = "CRASH TIMESTAMP: ${Date()}\nError: ${e.message}\nStack Trace:\n$stackTrace\n--------------------------------\n"
            
            val file = File(getExternalFilesDir(null), "crash_log.txt")
            file.appendText(logContent)
            
            // Try to show a toast on UI thread if possible (best effort), mainly for dev loop
            // In a real crash, this might not show if Looper is killed.
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(this, "CRASHED! Saved to ${file.name}", Toast.LENGTH_LONG).show()
            }
            
        } catch (ioe: Exception) {
            ioe.printStackTrace()
        }
        
        // Use default handler or kill
        // android.os.Process.killProcess(android.os.Process.myPid())
        exitProcess(1)
    }
}

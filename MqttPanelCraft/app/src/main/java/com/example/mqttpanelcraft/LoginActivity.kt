package com.example.mqttpanelcraft

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import java.io.File
import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.switchmaterial.SwitchMaterial
import java.util.Locale
import com.example.mqttpanelcraft.utils.CrashLogger

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val btnLogin = findViewById<MaterialButton>(R.id.btnLogin)
        val btnGuest = findViewById<MaterialButton>(R.id.btnGuest)
        val btnGoogle = findViewById<MaterialButton>(R.id.btnGoogle)
        val tvRegister = findViewById<TextView>(R.id.tvRegister)
        val btnSettings = findViewById<MaterialButton>(R.id.btnSettings)
        val tvWelcome = findViewById<TextView>(R.id.tvWelcome) // Need to add ID to XML

        // Secret: Long click on "Welcome Back" to view logs
        tvWelcome?.setOnLongClickListener {
            showCrashLogs()
            true
        }

        // Login -> DashboardActivity (Mock flow)
        // Login -> DashboardActivity (Mock flow)
        btnLogin.setOnClickListener {
            try {
                // In a real app, perform validation here
                val intent = Intent(this, DashboardActivity::class.java)
                startActivity(intent)
                finish()
            } catch (e: Exception) {
                CrashLogger.logError(this, "Login Failed", e)
            }
        }

        // Guest -> DashboardActivity
        btnGuest.setOnClickListener {
            try {
                val intent = Intent(this, DashboardActivity::class.java)
                intent.putExtra("IS_GUEST", true)
                startActivity(intent)
                finish()
            } catch (e: Exception) {
                CrashLogger.logError(this, "Guest Login Failed", e)
            }
        }

        // Google -> Mock Toast
        btnGoogle.setOnClickListener {
            try {
                Toast.makeText(this, "Google Sign In (UI Only)", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                CrashLogger.logError(this, "Google Sign In Failed", e)
            }
        }

        // Register -> RegisterActivity
        tvRegister.setOnClickListener {
            try {
                val intent = Intent(this, RegisterActivity::class.java)
                startActivity(intent)
            } catch (e: Exception) {
                CrashLogger.logError(this, "Register Nav Failed", e)
            }
        }

        // Settings -> BottomSheet
        btnSettings.setOnClickListener {
            try {
                showSettingsBottomSheet()
            } catch (e: Exception) {
                CrashLogger.logError(this, "Settings Open Failed", e)
            }
        }
    }

    private fun showCrashLogs() {
        try {
            val file = File(getExternalFilesDir(null), "crash_log.txt")
            if (!file.exists()) {
                Toast.makeText(this, "No crash logs found.", Toast.LENGTH_SHORT).show()
                return
            }
            val content = file.readText()
            
            AlertDialog.Builder(this)
                .setTitle("Crash Logs")
                .setMessage(content.takeLast(2000)) // Show last 2000 chars
                .setPositiveButton("Close", null)
                .setNeutralButton("Copy") { _, _ ->
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Crash Log", content)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Clear") { _, _ -> 
                    file.delete()
                    Toast.makeText(this, "Logs cleared", Toast.LENGTH_SHORT).show()
                }
                .show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error reading logs: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSettingsBottomSheet() {
        val bottomSheetDialog = BottomSheetDialog(this)
        bottomSheetDialog.setContentView(R.layout.bottom_sheet_settings)
        
        val switchDarkMode = bottomSheetDialog.findViewById<SwitchMaterial>(R.id.switchDarkMode)
        val dropdownLanguage = bottomSheetDialog.findViewById<AutoCompleteTextView>(R.id.dropdownLanguage)
        
        // vFix: Hide Sort Option in Login
        val tvSortLabel = bottomSheetDialog.findViewById<TextView>(R.id.tvSortLabel)
        val radioGroupSort = bottomSheetDialog.findViewById<android.widget.RadioGroup>(R.id.radioGroupSort)
        tvSortLabel?.visibility = View.GONE
        radioGroupSort?.visibility = View.GONE
        
        // Setup Dark Mode Switch
        val currentNightMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        switchDarkMode?.isChecked = currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
        switchDarkMode?.setOnCheckedChangeListener { _, isChecked ->
            val mode = if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            AppCompatDelegate.setDefaultNightMode(mode)
        }

        // Setup Language Dropdown
        val languages = listOf("English", "繁體中文")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, languages)
        dropdownLanguage?.setAdapter(adapter)
        
        // Set current selection
        val currentLang = if (resources.configuration.locales[0].language == "zh") "繁體中文" else "English"
        dropdownLanguage?.setText(currentLang, false)
        
        dropdownLanguage?.setOnItemClickListener { _, _, position, _ ->
            val selected = languages[position]
            if (selected != currentLang) {
                val localeCode = if (selected == "繁體中文") "zh" else "en"
                val regionCode = if (selected == "繁體中文") "TW" else "US"
                setLocale(localeCode, regionCode)
                bottomSheetDialog.dismiss()
            }
        }
        
        bottomSheetDialog.show()
    }
    
    private fun setLocale(languageCode: String, countryCode: String) {
        val locale = Locale(languageCode, countryCode)
        Locale.setDefault(locale)
        val config = resources.configuration
        config.setLocale(locale)
        createConfigurationContext(config)
        resources.updateConfiguration(config, resources.displayMetrics)

        // Restart Activity
        finish()
        startActivity(intent)
    }
}

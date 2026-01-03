package com.example.mqttpanelcraft

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.mqttpanelcraft.data.ProjectRepository
import com.example.mqttpanelcraft.model.Project
import com.example.mqttpanelcraft.service.MqttService
import com.example.mqttpanelcraft.utils.HtmlTemplates
import com.google.android.material.floatingactionbutton.FloatingActionButton

class WebViewActivity : AppCompatActivity(), MqttRepository.MessageListener {

    private lateinit var webView: WebView
    private lateinit var codeEditor: EditText
    private var projectId: String? = null
    private var project: Project? = null

    @SuppressLint("SetJavaScriptEnabled", "SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Ensure Data Init
        ProjectRepository.initialize(applicationContext)
        
        // Apply Global Theme
        com.example.mqttpanelcraft.utils.ThemeManager.applyTheme(this)
        
        setContentView(R.layout.activity_webview)
        
        // Fix: Status Bar Spacing & Color
        val root = findViewById<android.view.View>(R.id.rootCoordinator)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        val isDarkTheme = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        windowInsetsController.isAppearanceLightStatusBars = !isDarkTheme
        
        // Toolbar Setup
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        
        val tvTitle = findViewById<android.widget.TextView>(R.id.tvToolbarTitle)
        
        // Views
        webView = findViewById(R.id.webView)
        codeEditor = findViewById(R.id.etCodeEditor)
        val containerCode = findViewById<android.view.View>(R.id.containerCode)
        val fabCode = findViewById<FloatingActionButton>(R.id.fabCode)
        
        projectId = intent.getStringExtra("PROJECT_ID")
        
        if (projectId != null) {
            project = ProjectRepository.getProjectById(projectId!!)
            tvTitle.text = project?.name ?: "Web Project"
        }
        
        // MQTT Service Integration
        // Ensure Service is Connected using Project Defaults
        val brokerUrl = project?.broker ?: "tcp://broker.emqx.io"
        val port = project?.port ?: 1883
        val clientId = project?.clientId ?: "webview_client_${System.currentTimeMillis()}"
        val username = project?.username ?: ""
        val password = project?.password ?: ""

        val serviceIntent = Intent(this, MqttService::class.java).apply {
            action = "CONNECT"
            putExtra("BROKER", brokerUrl)
            putExtra("PORT", port)
            putExtra("USER", username)
            putExtra("PASSWORD", password)
            putExtra("CLIENT_ID", clientId)
        }
        startService(serviceIntent)
        
        // Subscribe to Project Base Topic
        if (project != null) {
            val baseTopic = "${project!!.name}/${project!!.id}/#"
            val subIntent = Intent(this, MqttService::class.java).apply {
                action = "SUBSCRIBE"
                putExtra("TOPIC", baseTopic)
            }
            startService(subIntent)
        }

        // WebView Setup
        webView.webViewClient = WebViewClient()
        webView.settings.javaScriptEnabled = true
        webView.addJavascriptInterface(MQTTInterface(), "mqtt")

        // Load Code: Priority: ProjectRepository (JSON) -> Template
        val initialCode = if (!project?.customCode.isNullOrEmpty()) {
            project!!.customCode
        } else {
            HtmlTemplates.generateDefaultHtml(project)
        }
        
        codeEditor.setText(initialCode)
        codeEditor.hint = "Enter HTML here..."
        
        // Initial Load
        webView.loadDataWithBaseURL(null, initialCode, "text/html", "utf-8", null)

        // FAB Action: Toggle Editor & Save/Run
        fabCode.setOnClickListener {
             if (containerCode.visibility == android.view.View.VISIBLE) {
                 // Close Editor -> Run Code
                 containerCode.visibility = android.view.View.GONE
                 fabCode.setImageResource(android.R.drawable.ic_menu_edit)
                 
                 // Hide Keyboard
                 val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                 imm.hideSoftInputFromWindow(codeEditor.windowToken, 0)
                 
                 val code = codeEditor.text.toString()
                 
                 // Save to ProjectRepository (Persistence in JSON)
                 if (project != null) {
                     val updatedProject = project!!.copy(customCode = code)
                     ProjectRepository.updateProject(updatedProject)
                     project = updatedProject // Update local Ref
                     Toast.makeText(this, "Code Saved to Project", Toast.LENGTH_SHORT).show()
                 }
                 
                 webView.loadDataWithBaseURL(null, code, "text/html", "utf-8", null)
             } else {
                 // Open Editor
                 containerCode.visibility = android.view.View.VISIBLE
                 fabCode.setImageResource(android.R.drawable.ic_media_play)
             }
        }
        
        findViewById<android.view.View>(R.id.btnSettings).setOnClickListener {
             if (projectId != null) {
                 val intent = Intent(this, SetupActivity::class.java)
                 intent.putExtra("PROJECT_ID", projectId)
                 startActivity(intent)
             }
        }

        // Register Listener
        MqttRepository.registerListener(this)
        
        // Update Initial Status
        updateStatusIndicator(MqttRepository.connectionStatus.value ?: 0)
        MqttRepository.connectionStatus.observe(this) { status ->
            updateStatusIndicator(status)
        }
    }

    private fun updateStatusIndicator(status: Int) {
        val viewStatusDot = findViewById<android.view.View>(R.id.viewStatusDot)
        when(status) {
            1 -> { // Connected
                 viewStatusDot.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.GREEN)
            }
            2 -> { // Failed
                 viewStatusDot.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.RED)
            }
            else -> { // Connecting
                 viewStatusDot.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.GRAY)
            }
        }
    }

    override fun onMessageReceived(topic: String, payload: String) {
        runOnUiThread {
            // Inject into JS
            val safePayload = payload.replace("'", "\\'")
            val safeTopic = topic.replace("'", "\\'")
            webView.evaluateJavascript("if(window.mqttOnMessage) mqttOnMessage('$safeTopic', '$safePayload')", null)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        MqttRepository.unregisterListener(this)
    }

    // JS Interface
    inner class MQTTInterface {
        @JavascriptInterface
        fun publish(topic: String, message: String) {
            val intent = Intent(this@WebViewActivity, MqttService::class.java).apply {
                action = "PUBLISH"
                putExtra("TOPIC", topic)
                putExtra("PAYLOAD", message)
            }
            startService(intent)
        }
        
        @JavascriptInterface
        fun subscribe(topic: String) {
            val intent = Intent(this@WebViewActivity, MqttService::class.java).apply {
                action = "SUBSCRIBE"
                putExtra("TOPIC", topic)
            }
            startService(intent)
        }
    }
}


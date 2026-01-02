package com.example.mqttpanelcraft

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class WebViewActivity : AppCompatActivity() {

    private lateinit var mqttHandler: MqttHandler
    private lateinit var webView: WebView
    private lateinit var codeEditor: EditText

    @SuppressLint("SetJavaScriptEnabled", "SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Ensure Data Init
        com.example.mqttpanelcraft.data.ProjectRepository.initialize(applicationContext)
        
        setContentView(R.layout.activity_webview)
        
        // Fix: Status Bar Spacing & Color (Match ProjectView)
        val root = findViewById<android.view.View>(R.id.rootCoordinator)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Set Status Bar Appearance (Light/Dark)
        val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        val isDarkTheme = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        windowInsetsController.isAppearanceLightStatusBars = !isDarkTheme
        
        // Toolbar Setup
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        
        val tvTitle = findViewById<android.widget.TextView>(R.id.tvToolbarTitle)
        val viewStatusDot = findViewById<android.view.View>(R.id.viewStatusDot)
        
        // Views
        webView = findViewById(R.id.webView)
        codeEditor = findViewById(R.id.etCodeEditor)
        val containerCode = findViewById<android.view.View>(R.id.containerCode)
        val fabCode = findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabCode)
        
        // Retrieve Project Info if available
        // DashboardActivity passes "URL" but usually passes "PROJECT_ID" for other activities.
        // Let's try to get Project ID if passed, otherwise use Defaults.
        val projectId = intent.getStringExtra("PROJECT_ID")
        var project: com.example.mqttpanelcraft.model.Project? = null
        
        // This relies on Repository being initialized. It should be from Dashboard.
        if (projectId != null) {
            project = com.example.mqttpanelcraft.data.ProjectRepository.getProjectById(projectId)
            tvTitle.text = project?.name ?: "Web Project"
        }
        
        // MQTT Setup
        // Use Project Broker if available, else Intent URL, else Default
        val brokerUrl = project?.broker ?: intent.getStringExtra("URL") ?: "tcp://broker.emqx.io:1883"
        val mqttClientId = project?.clientId ?: "webview_client_${System.currentTimeMillis()}"

        mqttHandler = MqttHandler(this, 
            onMessageReceived = { topic, message ->
                runOnUiThread {
                    // Inject into JS
                    webView.evaluateJavascript("if(window.mqttOnMessage) mqttOnMessage('$topic', '$message')", null)
                }
            },
            onConnectionStatusChanged = { connected ->
                runOnUiThread {
                    if (connected) {
                        viewStatusDot.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.GREEN)
                         Toast.makeText(this, "Connected", Toast.LENGTH_SHORT).show()
                    } else {
                        viewStatusDot.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.RED)
                    }
                }
            }
        )
        mqttHandler.connect(brokerUrl, mqttClientId)
        
        if (project != null) {
            mqttHandler.subscribe("${project.name}/${project.id}/#")
        }

        // WebView Setup
        webView.webViewClient = WebViewClient()
        webView.settings.javaScriptEnabled = true
        webView.addJavascriptInterface(MQTTInterface(), "mqtt")

        // Default Code
        val savedCode = getPreferences(MODE_PRIVATE).getString("SAVED_CODE_$projectId", codeEditor.hint.toString())
        codeEditor.setText(savedCode)
        
        // Initial Load
        webView.loadDataWithBaseURL(null, savedCode ?: "", "text/html", "utf-8", null)

        // FAB Action: Toggle Editor & Save/Run
        fabCode.setOnClickListener {
             if (containerCode.visibility == android.view.View.VISIBLE) {
                 // Close Editor -> Run Code
                 containerCode.visibility = android.view.View.GONE
                 fabCode.setImageResource(android.R.drawable.ic_menu_edit)
                 
                 val code = codeEditor.text.toString()
                 getPreferences(MODE_PRIVATE).edit().putString("SAVED_CODE_$projectId", code).apply()
                 webView.loadDataWithBaseURL(null, code, "text/html", "utf-8", null)
                 Toast.makeText(this, "Code Updated", Toast.LENGTH_SHORT).show()
             } else {
                 // Open Editor
                 containerCode.visibility = android.view.View.VISIBLE
                 fabCode.setImageResource(android.R.drawable.ic_media_play)
             }
        }
        
        // Settings Button
        findViewById<android.view.View>(R.id.btnSettings).setOnClickListener {
             if (projectId != null) {
                 val intent = android.content.Intent(this, SetupActivity::class.java)
                 intent.putExtra("PROJECT_ID", projectId)
                 startActivity(intent)
             } else {
                 android.widget.Toast.makeText(this, "Settings not available (No Project ID)", android.widget.Toast.LENGTH_SHORT).show()
             }
        }
    }
    
    // JS Interface
    inner class MQTTInterface {
        @JavascriptInterface
        fun publish(topic: String, message: String) {
            mqttHandler.publish(topic, message)
        }
        
        @JavascriptInterface
        fun subscribe(topic: String) {
            mqttHandler.subscribe(topic)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        mqttHandler.disconnect()
    }
}

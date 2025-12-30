package com.example.mqttpanelcraft

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.mqttpanelcraft.data.ProjectRepository
import com.example.mqttpanelcraft.model.Project
import com.example.mqttpanelcraft.service.MqttService
import com.google.android.material.floatingactionbutton.FloatingActionButton

class WebViewActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var fabEditCode: FloatingActionButton
    private var projectId: String? = null
    private var currentProject: Project? = null
    
    // Default Template
    private val DEFAULT_HTML = """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<style>
    body { font-family: 'Segoe UI', sans-serif; padding: 20px; background-color: #f0f2f5; display: flex; flex-direction: column; align-items: center; justify-content: center; height: 100vh; margin: 0; }
    .marquee-container { 
        width: 100%; background: #222; color: #0f0; 
        padding: 20px; font-size: 24px; font-weight: bold; 
        white-space: nowrap; overflow: hidden; border-radius: 8px; margin-bottom: 30px;
        box-shadow: 0 4px 10px rgba(0,0,0,0.3);
    }
    .marquee-text { display: inline-block; padding-left: 100%; animation: marquee 10s linear infinite; }
    @keyframes marquee { 0% { transform: translate(0, 0); } 100% { transform: translate(-100%, 0); } }

    input { padding: 12px; border-radius: 4px; border: 1px solid #ccc; width: 80%; font-size: 16px; margin-bottom: 10px; }
    button { padding: 12px 24px; background: #2196F3; color: white; border: none; border-radius: 4px; font-size: 16px; cursor: pointer; }
    button:active { transform: scale(0.98); }
</style>
</head>
<body>

    <div class="marquee-container">
        <div id="marquee" class="marquee-text">Waiting for message...</div>
    </div>

    <input type="text" id="inputBox" placeholder="Enter text to broadcast...">
    <button onclick="sendText()">Broadcast Text</button>

    <script>
        // 1. Subscribe on load (optional, or via button)
        if(window.AndroidMqtt) {
            window.AndroidMqtt.subscribeDefault();
        }

        // 2. Send Text
        function sendText() {
            var text = document.getElementById("inputBox").value;
            if(text && window.AndroidMqtt) {
                window.AndroidMqtt.publishText(text);
                document.getElementById("inputBox").value = "";
            }
        }

        // 3. Receive Text (Callback from Android)
        window.setMarqueeText = function(text) {
            var el = document.getElementById("marquee");
            el.innerText = text;
            
            // Restart animation to make it noticeable
            el.style.animation = 'none';
            el.offsetHeight; /* trigger reflow */
            el.style.animation = null; 
        };
    </script>
</body>
</html>
"""

    // Toolbar Views
    private lateinit var tvToolbarTitle: android.widget.TextView
    private lateinit var viewStatusDot: android.view.View


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webview)

        projectId = intent.getStringExtra("PROJECT_ID")
        if (projectId == null) {
            finish()
            return
        }
        
        currentProject = ProjectRepository.getProjectById(projectId!!)
        if (currentProject == null) {
            finish()
            return
        }

        // Initialize MQTT Service connection for this project
        startMqttService()

        setupViews()
        setupToolbar()
        loadWebView()
        setupMqttObserver()
    }

    private fun startMqttService() {
        val project = currentProject!!
        MqttRepository.activeProjectId = project.id 

        val intent = Intent(this, MqttService::class.java)
        intent.action = "CONNECT"
        intent.putExtra("BROKER", project.broker)
        intent.putExtra("PORT", project.port)
        intent.putExtra("USER", project.username)
        intent.putExtra("PASSWORD", project.password)
        startService(intent)
    }

    private fun setupToolbar() {
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        tvToolbarTitle = findViewById(R.id.tvToolbarTitle)
        viewStatusDot = findViewById(R.id.viewStatusDot)
        
        val btnSettings = findViewById<android.widget.ImageView>(R.id.btnSettings)
        val btnApiInfo = findViewById<android.widget.ImageView>(R.id.btnApiInfo)

        tvToolbarTitle.text = currentProject?.name ?: "WebView"

        // Navigation Icon (Hamburger / Settings)
        toolbar.setNavigationIcon(R.drawable.ic_menu) // Or use a specific settings icon if preferred
        toolbar.setNavigationOnClickListener { view ->
            showSettingsMenu(view)
        }
        
        // API Info Button
        btnApiInfo.setOnClickListener {
            showApiInfoDialog()
        }
        
        // Settings Button Logic
        btnSettings.setOnClickListener {
             if (projectId != null) {
                try {
                     val intent = android.content.Intent(this, SetupActivity::class.java)
                     intent.putExtra("PROJECT_ID", projectId)
                     startActivity(intent)
                } catch (e: Exception) {
                    android.widget.Toast.makeText(this, "Setup Activity Error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showSettingsMenu(anchor: android.view.View) {
        val popup = androidx.appcompat.widget.PopupMenu(this, anchor)
        popup.menu.add("Dark Mode Toggle")

        
        popup.setOnMenuItemClickListener { item ->
            when (item.title) {
                "Dark Mode Toggle" -> {
                    val currentMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
                    val newMode = if (currentMode == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
                        androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
                    } else {
                        androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                    }
                    androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(newMode)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupViews() {
        webView = findViewById(R.id.webView)
        fabEditCode = findViewById(R.id.fabEditCode)

        webView.settings.javaScriptEnabled = true
        webView.webViewClient = WebViewClient()
        
        // Add JS Interface
        // Note: Topic logic might need to be specific. The reference used a default topic "app/marquee".
        // MqttPanelCraft uses "name/id/type/...".
        // For compatibility with the requested "copy api part", I will use a default topic derived from project ID or just a fixed one if standard.
        // Let's us "project/id/text/0/out" for publish to match MqttPanelCraft conventions if possible,
        // OR just simple topic if that's what the "Advanced" mode implies.
        // The user request said "preserve api copy part", which in the webview project was "app/marquee".
        // However, to integrate well, maybe we use "{project.name}/webview" or similar.
        // Let'sstick to the reference `DEFAULT_TOPIC` logic if generic, or better, use the project ID to avoid collisions.
        // JS Interface with Strict Topic Naming
        // Base: {name}/{id}/
        val baseTopic = "${currentProject!!.name.lowercase()}/${currentProject!!.id}"
        
        webView.addJavascriptInterface(WebAppInterface(this, baseTopic), "AndroidMqtt")

        fabEditCode.setOnClickListener {
            showEditDialog()
        }
    }

    private fun loadWebView() {
        val code = if (currentProject!!.customCode.isNotEmpty()) currentProject!!.customCode else DEFAULT_HTML
        webView.loadDataWithBaseURL(null, code, "text/html", "UTF-8", null)
    }

    private fun setupMqttObserver() {
        // 1. Connection Status
        com.example.mqttpanelcraft.MqttRepository.connectionStatus.observe(this) { status ->
             val color = when (status) {
                 com.example.mqttpanelcraft.MqttStatus.CONNECTED -> android.graphics.Color.GREEN
                 com.example.mqttpanelcraft.MqttStatus.CONNECTING -> android.graphics.Color.LTGRAY
                 com.example.mqttpanelcraft.MqttStatus.FAILED -> android.graphics.Color.RED
                 else -> android.graphics.Color.GRAY
             }
             if (::viewStatusDot.isInitialized) {
                 viewStatusDot.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
             }
        }

        // 2. Messages
        MqttRepository.latestMessage.observe(this) { message ->
             // Validate Topic: Must match {base}/webview/0/state
             // Actually, for flexibility, we might pass ALL messages matching prefix?
             // But for the specific Marquee demo, let's look for our state topic.
             val baseTopic = "${currentProject!!.name.lowercase()}/${currentProject!!.id}"
             val expectedTopic = "$baseTopic/webview/0/state"
             
             if (message.topic == expectedTopic) {
                 val payload = message.payload.replace("'", "\\'").replace("\n", " ")
                 val js = "if(typeof window.setMarqueeText === 'function') window.setMarqueeText('$payload');"
                 webView.evaluateJavascript(js, null)
             }
        }
    }

    private fun showApiInfoDialog() {
        val project = currentProject ?: return
        val baseTopic = "${project.name.lowercase()}/${project.id}"
        
        val apiText = """
// === Android WebView Integration Spec ===
// Use this reference to prompt an AI (like ChatGPT) to write your HTML/JS dashboard.

// 1. MQTT Topic Protocol
// Pattern: {ProjectName}/{ProjectID}/{ComponentType}/{Index}/{Direction}
// This WebView Identity: Type='webview', Index='0'

// - To Send Command (App -> Device):
//   Topic: $baseTopic/webview/0/cmd
//   Payload: Any String

// - To Receive State (Device -> App):
//   Topic: $baseTopic/webview/0/state
//   Payload: Any String

// 2. JavaScript Interface (AndroidMqtt)

// A. Publish Text (Sends to '.../webview/0/cmd')
window.AndroidMqtt.publishText("your_payload_here");

// B. Subscribe (Listens to '.../webview/0/state')
// Call this once at startup to enable reception.
window.AndroidMqtt.subscribeDefault();

// C. Receive Callback (Define this function to update UI)
window.setMarqueeText = function(payload) {
    console.log("Received: " + payload);
    // Update your DOM elements here
};
"""
        val context = this
        val builder = AlertDialog.Builder(context)
        builder.setTitle("API Reference (Tap to Copy)")

        val scrollView = android.widget.ScrollView(context)
        val textView = android.widget.TextView(context)
        textView.text = apiText
        textView.typeface = android.graphics.Typeface.MONOSPACE
        textView.setPadding(40, 40, 40, 40)
        textView.setTextIsSelectable(false) // Handle click manually for "Copy All"
        textView.textSize = 12f
        
        // Click to Copy Logic
        textView.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("API Reference", apiText)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "API Copied to Clipboard!", Toast.LENGTH_SHORT).show()
        }

        scrollView.addView(textView)
        builder.setView(scrollView)
        builder.setPositiveButton("Close", null)
        builder.show()
    }

    private fun showEditDialog() {
        val context = this
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Code Editor")

        val input = EditText(context)
        val currentCode = if (currentProject!!.customCode.isNotEmpty()) currentProject!!.customCode else DEFAULT_HTML
        input.setText(currentCode)
        input.isSingleLine = false
        input.minLines = 10
        input.gravity = android.view.Gravity.TOP or android.view.Gravity.START
        input.setPadding(30, 30, 30, 30) // DP conversion would be better but this is quick
        
        // Container for padding
        val container = android.widget.LinearLayout(context)
        container.orientation = android.widget.LinearLayout.VERTICAL
        container.setPadding(40, 20, 40, 0)
        container.addView(input)


        builder.setView(container)

        builder.setPositiveButton("Run & Save") { dialog, _ ->
            val newCode = input.text.toString()
            
            // Save to Project
            currentProject = currentProject!!.copy(customCode = newCode)
            ProjectRepository.updateProject(currentProject!!)
            
            // Reload WebView
            loadWebView()
        }
        builder.setNegativeButton("Cancel", null)

        builder.show()
    }

    // JS Interface
    class WebAppInterface(private val context: Context, private val baseTopic: String) {
        
        // Publish to .../webview/0/cmd
        @JavascriptInterface
        fun publishText(text: String) {
            val topic = "$baseTopic/webview/0/cmd"
            val intent = Intent(context, MqttService::class.java)
            intent.action = "PUBLISH"
            intent.putExtra("TOPIC", topic)
            intent.putExtra("PAYLOAD", text)
            context.startService(intent)
        }

        // Subscribe to .../webview/0/state
        @JavascriptInterface
        fun subscribeDefault() {
            val topic = "$baseTopic/webview/0/state"
            val intent = Intent(context, MqttService::class.java)
            intent.action = "SUBSCRIBE"
            intent.putExtra("TOPIC", topic) 
            context.startService(intent)
        }
    }
}

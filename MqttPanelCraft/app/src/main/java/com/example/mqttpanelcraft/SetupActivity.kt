package com.example.mqttpanelcraft

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.mqttpanelcraft.data.ProjectRepository
import com.example.mqttpanelcraft.model.Project
import com.example.mqttpanelcraft.model.ProjectType
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

class SetupActivity : AppCompatActivity() {

    private var selectedType: ProjectType = ProjectType.HOME
    private var projectId: String? = null
    
    // UI Elements
    private lateinit var etName: TextInputEditText
    private lateinit var etBroker: TextInputEditText
    private lateinit var etPort: TextInputEditText
    private lateinit var etUser: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnTest: MaterialButton
    private lateinit var btnSave: MaterialButton
    private lateinit var btnImport: MaterialButton
    private lateinit var btnExport: MaterialButton

    // Data State
    private var originalProject: Project? = null // For Edit Mode
    private var pendingComponents: MutableList<com.example.mqttpanelcraft.model.ComponentData>? = null
    private var pendingCustomCode: String? = null // Store imported code
    private var pendingExportJson: String? = null // Temporary hold for export


    // Theme Cards
    private lateinit var cardHome: LinearLayout
    private lateinit var ivHome: ImageView
    private lateinit var tvHome: TextView

    private lateinit var cardFactory: LinearLayout
    private lateinit var ivFactory: ImageView
    private lateinit var tvFactory: TextView

    private lateinit var cardWebview: LinearLayout
    private lateinit var ivWebview: ImageView
    private lateinit var tvWebview: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)
        ProjectRepository.initialize(this)

        setupToolbar()
        setupViews()
        
        // Check for Edit Mode
        projectId = intent.getStringExtra("PROJECT_ID")
        if (projectId != null) {
            setupEditMode(projectId!!)
        } else {
             // Create Mode: Import is visible, Export is gone (default in XML)
        }

        setupWindowInsets()
    }



    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        com.example.mqttpanelcraft.utils.AdManager.loadBannerAd(this, findViewById(R.id.bannerAdContainer))
        com.example.mqttpanelcraft.utils.AdManager.loadRewarded(this)
    }
    
    private fun setupEditMode(id: String) {
        val project = ProjectRepository.getProjectById(id) ?: return
        originalProject = project

        // Show Export
        btnExport.visibility = android.view.View.VISIBLE

        etName.setText(project.name)
        etBroker.setText(project.broker)
        etPort.setText(project.port.toString())
        etUser.setText(project.username)
        etPassword.setText(project.password)

        // v2: Show Project ID + Change Button
        val containerProjectId = findViewById<android.view.View>(R.id.containerProjectId)
        val tvProjectId = findViewById<TextView>(R.id.tvProjectId)
        val btnChangeId = findViewById<MaterialButton>(R.id.btnChangeId)

        containerProjectId.visibility = android.view.View.VISIBLE
        tvProjectId.text = id

        btnChangeId.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Change Project ID")
                .setMessage("Warning: Changing the ID will treat this as a new entry. Are you sure?")
                .setPositiveButton("Generate New ID") { _, _ ->
                     val newId = ProjectRepository.generateId()
                     tvProjectId.text = newId
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // v38: Copy ID to Clipboard
        tvProjectId.setOnClickListener {
            val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Project ID", tvProjectId.text.toString())
            clipboard.setPrimaryClip(clip)
            android.widget.Toast.makeText(this, "ID Copied", android.widget.Toast.LENGTH_SHORT).show()
        }


        findViewById<TextInputEditText>(R.id.etProjectName).setText(project.name)
        findViewById<TextInputEditText>(R.id.etBroker).setText(project.broker)
        selectType(project.type)
        btnSave.text = "Update & Start"
        findViewById<MaterialButton>(R.id.btnSaveProject).text = "Update Project"
        supportActionBar?.title = "Edit Project"
    }

    private fun setupToolbar() {
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_arrow_back) // Need this icon, or use standard back
        // For now use default if arrow back not custom created, or create one.
        // Standard Android usually has one if displayHomeAsUp is true.
        // If not creating specific drawable, system default works.
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    private val saveJsonLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null && pendingExportJson != null) {
            try {
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(pendingExportJson!!.toByteArray())
                }
                android.widget.Toast.makeText(this, "Saved to file!", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                android.widget.Toast.makeText(this, "Save Failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val openJsonLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val json = inputStream.bufferedReader().use { it.readText() }
                    processImportedJson(json)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                android.widget.Toast.makeText(this, "Read Failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupViews() {
        etName = findViewById(R.id.etProjectName)
        etBroker = findViewById(R.id.etBroker)
        etPort = findViewById(R.id.etPort)
        etUser = findViewById(R.id.etUser)
        etPassword = findViewById(R.id.etPassword)

        btnTest = findViewById(R.id.btnTestConnection)
        btnSave = findViewById(R.id.btnSaveProject)
        btnImport = findViewById(R.id.btnImportJson)
        btnExport = findViewById(R.id.btnExportJson)

        btnImport.setOnClickListener { showImportDialog() }
        btnExport.setOnClickListener { showExportDialog() }
        val etName = findViewById<TextInputEditText>(R.id.etProjectName)
        val etBroker = findViewById<TextInputEditText>(R.id.etBroker)
        val btnTest = findViewById<MaterialButton>(R.id.btnTestConnection)
        val btnSave = findViewById<MaterialButton>(R.id.btnSaveProject)

        cardHome = findViewById(R.id.cardHome)
        ivHome = findViewById(R.id.ivHome)
        tvHome = findViewById(R.id.tvHome)

        cardFactory = findViewById(R.id.cardFactory)
        ivFactory = findViewById(R.id.ivFactory)
        tvFactory = findViewById(R.id.tvFactory)

        cardWebview = findViewById(R.id.cardWebview)
        ivWebview = findViewById(R.id.ivWebview)
        tvWebview = findViewById(R.id.tvWebview)

        // Theme Selection
        cardHome.setOnClickListener { selectType(ProjectType.HOME) }
        cardFactory.setOnClickListener { selectType(ProjectType.FACTORY) }
        cardWebview.setOnClickListener { selectType(ProjectType.WEBVIEW) }

        btnTest.setOnClickListener {
           testConnection()
        }

        // Save
        btnSave.setOnClickListener {
            saveProject()
        }
    }

    private fun showImportDialog() {
        val context = this
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Import Configuration Payload")

        val input = android.widget.EditText(context)
        input.hint = "Paste JSON here..."
        input.isSingleLine = false
        input.minLines = 10
        input.gravity = android.view.Gravity.TOP or android.view.Gravity.START
        input.setPadding(40, 40, 40, 40)
        input.textSize = 12f

        builder.setView(input)

        builder.setPositiveButton("Load Text") { _, _ ->
            val json = input.text.toString()
            if (json.isNotBlank()) {
                processImportedJson(json)
            }
        }

        builder.setNeutralButton("Load File") { _, _ ->
             // MIME types: json, text, or any
             openJsonLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
        }

        builder.setNegativeButton("Cancel", null)

        val dialog = builder.create()
        // Fix: Set input mode on the dialog's window, not via view
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog.show()
    }

    private fun processImportedJson(json: String) {
        val imported = ProjectRepository.parseProjectJson(json)
        if (imported != null) {
            // Populate Fields with "Copy" suffix
            var newName = imported.name + "_copy"
            // Simple check to avoid loop, though repository check later handles strict uniqueness
            if (ProjectRepository.isProjectNameTaken(newName)) {
                 newName += "_" + System.currentTimeMillis() % 1000
            }
            etName.setText(newName)

            // Set Imported ID - DISABLED to prevent ID Collision/Duplication bugs
            // Importing configuration should NOT change the Project's Identity (ID).
            // if (imported.id.isNotEmpty()) {
            //    val containerProjectId = findViewById<android.view.View>(R.id.containerProjectId)
            //    val tvProjectId = findViewById<TextView>(R.id.tvProjectId)
            //    containerProjectId.visibility = android.view.View.VISIBLE
            //    tvProjectId.text = imported.id
            // }

            etBroker.setText(imported.broker)
            etPort.setText(imported.port.toString())
            etUser.setText(imported.username)
            // Password usually ignored
            selectType(imported.type)

            // Store Structure
            pendingComponents = imported.components
            pendingCustomCode = imported.customCode

            android.widget.Toast.makeText(this, "Loaded ${imported.components.size} components!", android.widget.Toast.LENGTH_SHORT).show()
        } else {
             android.widget.Toast.makeText(this, "Invalid JSON", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun showExportDialog() {
        if (originalProject == null) return

        val json = ProjectRepository.exportProjectToJson(originalProject!!)
        val context = this
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Export Configuration")

        val input = android.widget.EditText(context)
        input.setText(json)
        input.isSingleLine = false
        input.minLines = 10
        input.maxLines = 15
        input.isFocusable = false // Read only-ish
        input.setPadding(40, 40, 40, 40)
        input.textSize = 10f
        input.typeface = android.graphics.Typeface.MONOSPACE

        builder.setView(input)

        builder.setPositiveButton("Copy") { _, _ ->
             val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
             val clip = android.content.ClipData.newPlainText("Config JSON", json)
             clipboard.setPrimaryClip(clip)
             android.widget.Toast.makeText(context, "Copied to Clipboard", android.widget.Toast.LENGTH_SHORT).show()
        }

        builder.setNeutralButton("Save File") { _, _ ->
             pendingExportJson = json
             saveJsonLauncher.launch("${originalProject?.name ?: "config"}.json")
        }

        builder.setNegativeButton("Close", null)
        builder.show()
    }

    private fun testConnection() {
        val broker = etBroker.text.toString()
        val portStr = etPort.text.toString()
        val user = etUser.text.toString()
        val pass = etPassword.text.toString()

        if (broker.isBlank()) {
            etBroker.error = "Required"
            return
        }

        val port = portStr.toIntOrNull() ?: 1883
        val uri = "tcp://$broker:$port"

        btnTest.isEnabled = false
        btnTest.text = "Connecting..."

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val clientId = "TestClient_" + System.currentTimeMillis()
                val client = MqttClient(uri, clientId, MemoryPersistence())
                val options = MqttConnectOptions()
                options.isCleanSession = true
                options.connectionTimeout = 30
                options.keepAliveInterval = 60

                if (user.isNotEmpty()) {
                    options.userName = user
                    options.password = pass.toCharArray()
                }

                client.connect(options)

                withContext(Dispatchers.Main) {
                    btnTest.text = "Connected!"
                    btnTest.isEnabled = true
                    btnTest.setTextColor(Color.GREEN)
                    btnTest.strokeColor = ColorStateList.valueOf(Color.GREEN)

                    if (client.isConnected) {
                        try { client.disconnect() } catch (e: Exception) {}
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (!isFinishing && !isDestroyed) {
                        btnTest.text = "Test Connection"
                        btnTest.isEnabled = true
                        btnTest.setTextColor(Color.RED) // Or default
                        btnTest.strokeColor = ColorStateList.valueOf(Color.RED)

                        AlertDialog.Builder(this@SetupActivity)
                            .setTitle("Connection Failed")
                            .setMessage(e.message ?: "Unknown Error")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            }
        }
    }


    private fun saveProject() {
        val name = etName.text.toString()
        val broker = etBroker.text.toString()
        val portStr = etPort.text.toString()
        val user = etUser.text.toString()
        val pass = etPassword.text.toString()

        if (name.isBlank()) {
            etName.error = getString(R.string.error_name_required)
            return
        }

        // Regex Validation
        if (!name.matches(Regex("^[A-Za-z0-9_]+$"))) {
            etName.error = "Only letters, numbers, and underscores allowed"
            return
        }

        // Duplicate Name Check
        if (ProjectRepository.isProjectNameTaken(name, projectId)) {
            etName.error = "Project name already exists"
            return
        }

        if (broker.isBlank()) {
            etBroker.error = getString(R.string.error_broker_required)
            return
        }

        val port = portStr.toIntOrNull() ?: 1883

        // Determine ID
        var finalId = projectId ?: ProjectRepository.generateId()

        // Check if ID was changed in UI (Only in Edit Mode)
        if (projectId != null) {
             val tvProjectId = findViewById<TextView>(R.id.tvProjectId)
             val currentUiId = tvProjectId.text.toString()
             if (currentUiId != projectId) {
                 finalId = currentUiId
             }
        }

        // Determine Components & Custom Code
        val finalComponents = pendingComponents
            ?: originalProject?.components?.toMutableList() // Copy to avoid mutation issues
            ?: mutableListOf()

        val finalCustomCode = pendingCustomCode
            ?: originalProject?.customCode
            ?: ""

        val newProject = Project(
            id = finalId,
            name = name,
            broker = broker,
            port = port,
            username = user,
            password = pass,
            type = selectedType,
            isConnected = false,
            components = finalComponents,
            customCode = finalCustomCode
        )

        // Unified Flow: Always Show Rewarded
        val targetProjectId = newProject.id
        var isRewardEarned = false

        if (com.example.mqttpanelcraft.utils.AdManager.isRewardedReady()) {
            com.example.mqttpanelcraft.utils.AdManager.showRewarded(this,
                onReward = {
                    isRewardEarned = true
                },
                onClosed = {
                    if (isRewardEarned) {
                        saveAndFinish(newProject, targetProjectId)
                    } else {
                        android.widget.Toast.makeText(this, "You must watch the full ad to save/update!", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            )
        } else {
             // Fallback if ad not ready? Or force wait?
             // For user experience, let's save anyway if ad fails to load, or show toast.
             // V7 requirement says "Watch Ad to Save".
             // We can try to load again.
             android.widget.Toast.makeText(this, "Ad is loading, please wait...", android.widget.Toast.LENGTH_SHORT).show()
             com.example.mqttpanelcraft.utils.AdManager.loadRewarded(this)
        }
    }

    private fun saveAndFinish(newProject: Project, targetProjectId: String) {
        if (projectId != null) {
            if (newProject.id != projectId) {
                // ID Changed: Delete old, Add new
                ProjectRepository.deleteProject(projectId!!)
                ProjectRepository.addProject(newProject)
                
                 // Return result to Caller
                val resultIntent = android.content.Intent()
                resultIntent.putExtra("NEW_ID", newProject.id)
                setResult(RESULT_OK, resultIntent)
            } else {
                ProjectRepository.updateProject(newProject)
                setResult(RESULT_OK)
            }
        } else {
            ProjectRepository.addProject(newProject)
            setResult(RESULT_OK)
        }
        
        // If we want to open project immediately (optional, but standard flow usually returns to dashboard)
        // XML has "Save and Start" implies opening.
        // Let's open it.
        val intent = android.content.Intent(this, ProjectViewActivity::class.java)
        intent.putExtra("PROJECT_ID", targetProjectId)
        startActivity(intent)
        finish()
    }

    private fun selectType(type: ProjectType) {
        selectedType = type
        val primaryColor = ContextCompat.getColor(this, R.color.primary)
        val greyColor = Color.parseColor("#757575")

        // 1. Reset all to unselected state
        cardHome.setBackgroundResource(R.drawable.bg_card_unselected)
        ivHome.setColorFilter(greyColor)
        tvHome.setTextColor(greyColor)

        cardFactory.setBackgroundResource(R.drawable.bg_card_unselected)
        ivFactory.setColorFilter(greyColor)
        tvFactory.setTextColor(greyColor)

        cardWebview.setBackgroundResource(R.drawable.bg_card_unselected)
        ivWebview.setColorFilter(greyColor)
        tvWebview.setTextColor(greyColor)

        // 2. Highlight selected
        when (type) {
            ProjectType.HOME -> {
                cardHome.setBackgroundResource(R.drawable.bg_card_selected)
                ivHome.setColorFilter(primaryColor)
                tvHome.setTextColor(primaryColor)
            }
            ProjectType.FACTORY -> {
                cardFactory.setBackgroundResource(R.drawable.bg_card_selected)
                ivFactory.setColorFilter(primaryColor)
                tvFactory.setTextColor(primaryColor)
            }
            ProjectType.WEBVIEW -> {
                cardWebview.setBackgroundResource(R.drawable.bg_card_selected)
                ivWebview.setColorFilter(primaryColor)
                tvWebview.setTextColor(primaryColor)
            }
            else -> {} // Handle OTHER if needed
        }
    }
    }


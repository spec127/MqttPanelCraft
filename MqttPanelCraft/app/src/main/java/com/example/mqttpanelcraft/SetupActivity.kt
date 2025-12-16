package com.example.mqttpanelcraft

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
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
    
    // Theme Cards
    private lateinit var cardHome: LinearLayout
    private lateinit var ivHome: ImageView
    private lateinit var tvHome: TextView
    private lateinit var cardFactory: LinearLayout
    private lateinit var ivFactory: ImageView
    private lateinit var tvFactory: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        setupToolbar()
        setupViews()
        
        // Check for Edit Mode
        projectId = intent.getStringExtra("PROJECT_ID")
        if (projectId != null) {
            setupEditMode(projectId!!)
        }
        
        setupWindowInsets()
    }
    
    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }
    
    private fun setupEditMode(id: String) {
        val project = ProjectRepository.getProjectById(id) ?: return
        
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

        selectType(project.type)
        btnSave.text = "Update & Start"
        supportActionBar?.title = "Edit Project"
    }

    private fun setupToolbar() {
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    private fun setupViews() {
        etName = findViewById(R.id.etProjectName)
        etBroker = findViewById(R.id.etBroker)
        etPort = findViewById(R.id.etPort)
        etUser = findViewById(R.id.etUser)
        etPassword = findViewById(R.id.etPassword)
        
        btnTest = findViewById(R.id.btnTestConnection)
        btnSave = findViewById(R.id.btnSaveProject)

        cardHome = findViewById(R.id.cardHome)
        ivHome = findViewById(R.id.ivHome)
        tvHome = findViewById(R.id.tvHome)

        cardFactory = findViewById(R.id.cardFactory)
        ivFactory = findViewById(R.id.ivFactory)
        tvFactory = findViewById(R.id.tvFactory)

        // Theme Selection
        cardHome.setOnClickListener { selectType(ProjectType.HOME) }
        cardFactory.setOnClickListener { selectType(ProjectType.FACTORY) }

        // Test Connection (Real)
        btnTest.setOnClickListener {
           testConnection()
        }

        // Save
        btnSave.setOnClickListener {
            saveProject()
        }
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
                options.connectionTimeout = 5
                options.keepAliveInterval = 10
                
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
    
    private fun saveProject() {
        val name = etName.text.toString()
        val broker = etBroker.text.toString()
        val portStr = etPort.text.toString()
        
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
        val user = etUser.text.toString()
        val pass = etPassword.text.toString()

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

        val newProject = Project(
            id = finalId,
            name = name,
            broker = broker,
            port = port,
            username = user,
            password = pass,
            type = selectedType,
            isConnected = false
        )
        
        if (projectId != null) {
            if (finalId != projectId) {
                // ID Changed: Delete old, Add new
                ProjectRepository.deleteProject(projectId!!)
                ProjectRepository.addProject(newProject)
                
                // Return result to Caller
                val resultIntent = android.content.Intent()
                resultIntent.putExtra("NEW_ID", finalId)
                setResult(RESULT_OK, resultIntent)
            } else {
                ProjectRepository.updateProject(newProject)
                setResult(RESULT_OK)
            }
        } else {
            ProjectRepository.addProject(newProject)
            // For new project, we might want to return it too?
            // Caller usually reloads list.
            setResult(RESULT_OK)
        }
        
        // Finish SetupActivity to return to previous screen
        finish()
    }

    private fun selectType(type: ProjectType) {
        selectedType = type
        val primaryColor = ContextCompat.getColor(this, R.color.primary)
        val greyColor = Color.parseColor("#757575")

        if (type == ProjectType.HOME) {
            cardHome.setBackgroundResource(R.drawable.bg_card_selected)
            ivHome.setColorFilter(primaryColor)
            tvHome.setTextColor(primaryColor)

            cardFactory.setBackgroundResource(R.drawable.bg_card_unselected)
            ivFactory.setColorFilter(greyColor)
            tvFactory.setTextColor(greyColor)
        } else {
            cardHome.setBackgroundResource(R.drawable.bg_card_unselected)
            ivHome.setColorFilter(greyColor)
            tvHome.setTextColor(greyColor)

            cardFactory.setBackgroundResource(R.drawable.bg_card_selected)
            ivFactory.setColorFilter(primaryColor)
            tvFactory.setTextColor(primaryColor)
        }
    }
}

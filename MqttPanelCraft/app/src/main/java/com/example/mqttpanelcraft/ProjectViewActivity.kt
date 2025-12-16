package com.example.mqttpanelcraft

import android.content.ClipData
import android.content.ClipDescription
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Point
import android.graphics.Shader
import android.os.Bundle
import android.view.DragEvent
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.mqttpanelcraft.data.ProjectRepository
import com.example.mqttpanelcraft.ui.AlignmentOverlayView
import com.example.mqttpanelcraft.utils.CrashLogger
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.example.mqttpanelcraft.R
import kotlinx.coroutines.*


class ProjectViewActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var editorCanvas: ConstraintLayout
    private lateinit var guideOverlay: AlignmentOverlayView
    private lateinit var fabMode: FloatingActionButton
    private lateinit var logAdapter: LogAdapter
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<FrameLayout>
    
    // Bottom Sheet Containers
    private lateinit var containerLogs: LinearLayout
    private lateinit var containerProperties: ScrollView

    // Property Inputs
    private lateinit var etPropName: TextInputEditText
    private lateinit var etPropWidth: TextInputEditText
    private lateinit var etPropHeight: TextInputEditText
    private lateinit var etPropColor: TextInputEditText
    private lateinit var btnSaveProps: Button
    
    // Console Inputs
    private lateinit var etTopic: EditText
    private lateinit var etPayload: EditText
    private lateinit var btnSend: Button

    private var isEditMode = false 
    private var projectId: String? = null
    private var project: com.example.mqttpanelcraft.model.Project? = null
    
    private var selectedView: View? = null
    private val snapThreshold = 16f // dp


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_project_view)

            // Restore State
            if (savedInstanceState != null) {
                isEditMode = savedInstanceState.getBoolean("IS_EDIT_MODE", false)
            }

            setupUI()
            setupConsole()
            setupSidebarInteraction()
            setupPropertiesPanel()
            
            // Load Project Data
            projectId = intent.getStringExtra("PROJECT_ID")
            if (projectId != null) {
                loadProjectDetails(projectId!!)
                
                // v18: Start Background Service
                val project = com.example.mqttpanelcraft.data.ProjectRepository.getProjectById(projectId!!)
                if (project != null) {
                    // v19: Save Last Project ID
                    val prefs = getSharedPreferences("MqttPanelPrefs", android.content.Context.MODE_PRIVATE)
                    prefs.edit().putString("LAST_PROJECT_ID", projectId).apply()
                    
                    // Connection moved to onResume()
                }
            } else {
                finish()
            }

            checkMqttConnection()
            
            // Restore Drawer State (Must be after UI setup)
            if (savedInstanceState != null) {
                val wasDrawerOpen = savedInstanceState.getBoolean("IS_DRAWER_OPEN", false)
                if (wasDrawerOpen) {
                    drawerLayout.openDrawer(GravityCompat.START)
                }
            }
            
            // Ensure UI reflects restored mode
            updateModeUI()
            
        } catch (e: Exception) {
            CrashLogger.logError(this, "Project View Init Failed", e)
            finish()
        }
    }
    
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("IS_EDIT_MODE", isEditMode)
        if (::drawerLayout.isInitialized) {
            outState.putBoolean("IS_DRAWER_OPEN", drawerLayout.isDrawerOpen(GravityCompat.START))
        }
    }
    
    override fun onStart() {
        super.onStart()
        setupWindowInsets()

        setupDrawerListener()
    }
    
    override fun onResume() {
        super.onResume()
        if (projectId != null) {
            loadProjectDetails(projectId!!)
            connectToBroker()
            // v31: Refresh UI from Cache on Resume
            refreshUIFromCache()
        }
    }
    
    // v31: Refresh all components from cached states
    private fun refreshUIFromCache() {
        for (i in 0 until editorCanvas.childCount) {
            val view = editorCanvas.getChildAt(i)
            val topic = view.contentDescription?.toString()
            if (topic != null) {
                val repTopic = topic.replace("/cmd", "/rep")
                // Try specific rep topic first
                var cached = MqttRepository.getTopicState(repTopic)
                // Fallback to topic itself if stored that way (e.g. if we cached based on what we subscribed to?)
                // Our cache keys come from message topics (which are .../rep).
                // Our view topic is stored as .../cmd usually.
                
                if (cached != null) {
                     // Parse type from tag or topic
                     // Tag: "type:index"
                     val tag = view.tag.toString()
                     val type = if (tag.contains(":")) tag.split(":")[0] else "unknown"
                     
                     updateComponentState(view, type, cached)
                }
            }
        }
    }
    
    private fun connectToBroker() {
        if (projectId == null) return
        val project = ProjectRepository.getProjectById(projectId!!) ?: return
        
        // Avoid reconnecting if already connected to SAME broker? 
        // For simplicity and "force update" requirement, we just send CONNECT Intent.
        // MqttService.connect handles disconnect if needed.
        
        val serviceIntent = android.content.Intent(this, com.example.mqttpanelcraft.service.MqttService::class.java)
        serviceIntent.action = "CONNECT"
        serviceIntent.putExtra("BROKER", project.broker)
        serviceIntent.putExtra("PORT", project.port)
        serviceIntent.putExtra("USER", project.username)
        serviceIntent.putExtra("PASSWORD", project.password)
        serviceIntent.putExtra("CLIENT_ID", project.clientId)
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView.findViewById(android.R.id.content)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }
    
    private fun setupDrawerListener() {
        drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                // STRICT LOCKING: Disable bottom sheet dragging if drawer is moved
                if (slideOffset > 0.05f) {
                    bottomSheetBehavior.isDraggable = false
                    
                    // Auto-collapse bottom sheet if it's open
                    if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
                        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                    }
                }
            }

            override fun onDrawerOpened(drawerView: View) {
                 // STRICT LOCKING: Ensure bottom sheet cannot be dragged
                 bottomSheetBehavior.isDraggable = false
                 
                 // Ensure collapsed
                 if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
                     bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                 }
            }
            
            override fun onDrawerClosed(drawerView: View) {
                 // Unlock bottom sheet when drawer is closed
                 if (isEditMode) {
                     bottomSheetBehavior.isDraggable = true
                 }
            }
            
            override fun onDrawerStateChanged(newState: Int) {
                 if (newState == DrawerLayout.STATE_DRAGGING) {
                     bottomSheetBehavior.isDraggable = false
                 }
            }
        })
    }
    

    
    private fun loadProjectDetails(id: String) {
        projectId = id
        project = ProjectRepository.getProjectById(id)
        
        // v29: Set Active Project for Filtering
        MqttRepository.activeProjectId = id
        // v36: Clear Log on Project Switch
        MqttRepository.clear()
        
        if (project != null) {
            supportActionBar?.title = project!!.name
            findViewById<TextView>(R.id.tvToolbarTitle).text = project!!.name
        }
    }

    private fun setupUI() {
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false) // Hide default title
        
        val tvToolbarTitle = findViewById<TextView>(R.id.tvToolbarTitle)
        val viewStatusDot = findViewById<View>(R.id.viewStatusDot)
        
        if (project != null) {
            tvToolbarTitle.text = project!!.name
        }
        
        // Observe Connection Status
        MqttRepository.connectionStatus.observe(this) { status ->
             val color = when(status) {
                 MqttStatus.CONNECTED -> {
                     // v29: Trigger Full Sync on Connect
                     performFullSync()
                     0xFF00E676.toInt() // Green
                 }
                 MqttStatus.FAILED -> 0xFFFF5252.toInt()    // Red
                 else -> 0xFFAAAAAA.toInt()                 // Gray
             }
             viewStatusDot.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
        }
        
        viewStatusDot.setOnClickListener {
             // Manual Retry
             connectToBroker()
             Toast.makeText(this, "Retrying Connection...", Toast.LENGTH_SHORT).show()
        }

        // v25: Observe Incoming Messages for UI Updates
        MqttRepository.latestMessage.observe(this) { msg ->
             handleMqttMessage(msg.topic, msg.payload)
        }
    
        // Add Hamburger Menu Icon
        toolbar.setNavigationIcon(R.drawable.ic_menu)
    toolbar.setNavigationOnClickListener { 
        // Allow opening drawer even if bottom sheet is expanded
        if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
             bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }
        
        if (drawerLayout.isDrawerOpen(androidx.core.view.GravityCompat.START)) {
            drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
        } else {
            drawerLayout.openDrawer(androidx.core.view.GravityCompat.START)
        }
    }
        
        // v22: Activity Result Launcher for Setup
        val setupLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                // Check if ID was changed
                val newId = result.data?.getStringExtra("NEW_ID")
                if (newId != null) {
                    projectId = newId
                    
                    // Update Prefs
                    val prefs = getSharedPreferences("MqttPanelPrefs", android.content.Context.MODE_PRIVATE)
                    prefs.edit().putString("LAST_PROJECT_ID", projectId).apply()
                }
                
                // Reload details regardless
                if (projectId != null) {
                    loadProjectDetails(projectId!!)
                    // v27: Removed explicit force reconnect here, handled by onResume()
                }
            }
        }

        // Settings Button (Custom View)
        val btnSettings = findViewById<ImageView>(R.id.btnSettings)
        btnSettings.setOnClickListener {
             if (projectId != null) {
                 try {
                      val intent = android.content.Intent(this, SetupActivity::class.java)
                      intent.putExtra("PROJECT_ID", projectId)
                      setupLauncher.launch(intent)
                 } catch (e: Exception) {
                     android.widget.Toast.makeText(this, "Setup Activity not found", android.widget.Toast.LENGTH_SHORT).show()
                 }
            }
        }
        
        // Run Mode Sidebar Actions
        try {
            val switchDarkMode = findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchDarkMode)
            
            // Set initial state
            val currentNightMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
            switchDarkMode?.isChecked = (currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES)

            switchDarkMode?.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES)
                } else {
                    androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO)
                }
                // Do NOT close drawer; let onSaveInstanceState handle state persistence
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Trash Bin Drag Listener
        val binTrash = findViewById<ImageView>(R.id.binTrash)
        binTrash.setOnDragListener { v, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> true
                DragEvent.ACTION_DRAG_ENTERED -> {
                    (v as? ImageView)?.setColorFilter(Color.RED) // Highlight on hover
                    true
                }
                DragEvent.ACTION_DRAG_EXITED -> {
                    (v as? ImageView)?.clearColorFilter()
                    (v as? ImageView)?.setColorFilter(Color.WHITE) // Restore
                    true
                }
                DragEvent.ACTION_DROP -> {
                     (v as? ImageView)?.clearColorFilter()
                     (v as? ImageView)?.setColorFilter(Color.WHITE)
                     
                     val clipData = event.clipData
                     if (clipData != null && clipData.itemCount > 0) {
                         val idStr = clipData.getItemAt(0).text.toString()
                         // Check if it's an ID (View ID usually integer, encoded as string)
                         try {
                             val viewId = idStr.toInt()
                             val component = editorCanvas.findViewById<View>(viewId)
                             if (component != null) {
                                  AlertDialog.Builder(this)
                                      .setTitle("Delete Component")
                                      .setMessage("Are you sure you want to delete this component?")
                                      .setPositiveButton("Delete") { _, _ ->
                                          editorCanvas.removeView(component)
                                          guideOverlay.clear() // Clear lines
                                      }
                                      .setNegativeButton("Cancel", null)
                                      .show()
                             }
                         } catch (e: NumberFormatException) {
                             // Not a valid ID (maybe palette drag)
                         }
                     }
                     true
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    (v as? ImageView)?.clearColorFilter()
                    (v as? ImageView)?.setColorFilter(Color.WHITE)
                    true
                }
                else -> false
            }
        }

        drawerLayout = findViewById(R.id.drawerLayout)
        editorCanvas = findViewById(R.id.editorCanvas)
        guideOverlay = findViewById(R.id.guideOverlay)
        fabMode = findViewById(R.id.fabMode)
        
        containerLogs = findViewById(R.id.containerLogs)
        containerProperties = findViewById(R.id.containerProperties)
        
        
        val bottomSheet = findViewById<FrameLayout>(R.id.bottomSheet)
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED

        // Sidebar Actions
        try { // Wrap in try-catch in case views are missing
            val backgroundGrid = findViewById<View>(R.id.backgroundGrid)
            val switchGridToggle = findViewById<SwitchMaterial>(R.id.switchGridToggle)
            
            switchGridToggle?.setOnCheckedChangeListener { _, isChecked ->
                backgroundGrid?.visibility = if (isChecked) View.VISIBLE else View.GONE
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        
        val bottomSheetScrim = findViewById<View>(R.id.bottomSheetScrim)
        bottomSheetScrim?.setOnClickListener {
            if (bottomSheetBehavior.state != BottomSheetBehavior.STATE_COLLAPSED) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            }
        }
        
        // Update: Strict Mutual Exclusivity - Lock drawer if bottom sheet is expanded
        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_DRAGGING || newState == BottomSheetBehavior.STATE_EXPANDED) {
                     // Lock drawer closed -> REMOVED per user request
                     // drawerLayout.setDrawerLockMode(androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                     bottomSheetScrim?.visibility = View.VISIBLE
                } else if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                     // Unlock drawer (only if in Edit Mode)
                     if (isEditMode) {
                         drawerLayout.setDrawerLockMode(androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_UNLOCKED)
                     }
                     bottomSheetScrim?.visibility = View.GONE
                }
            }
            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                // If sliding up, ensure drawer is locked -> REMOVED per user request
                if (slideOffset > 0.05f) {
                    // drawerLayout.setDrawerLockMode(androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                    bottomSheetScrim?.visibility = View.VISIBLE
                    bottomSheetScrim?.alpha = slideOffset.coerceIn(0f, 1f)
                } else {
                     bottomSheetScrim?.visibility = View.GONE
                }
            }
        })

        val bottomSheetHeader = findViewById<LinearLayout>(R.id.bottomSheetHeader)
        bottomSheetHeader.setOnClickListener {
            // Check if Drawer is open before allowing toggle
            if (drawerLayout.isDrawerOpen(androidx.core.view.GravityCompat.START)) {
                return@setOnClickListener
            }
            
            if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_COLLAPSED) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            } else {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            }
        }

        fabMode.setOnClickListener { toggleMode() }
        updateModeUI()
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_project_view, menu)
        return true
    }


    private fun setupPropertiesPanel() {
        etPropName = findViewById(R.id.etPropName)
        etPropWidth = findViewById(R.id.etPropWidth)
        etPropHeight = findViewById(R.id.etPropHeight)
        etPropColor = findViewById(R.id.etPropColor)
        btnSaveProps = findViewById(R.id.btnSaveProps)
        
        etPropColor.setOnClickListener { showGradientColorPicker() } 
        etPropColor.setOnClickListener { showGradientColorPicker() } 
        etPropColor.isFocusable = false // v37: Fix for API < 26 compatibility
        etPropColor.isFocusableInTouchMode = false
        
        // v37: Remove duplicated unused import from top if present (not doing here, handled by tool)
        
        btnSaveProps.setOnClickListener {
            selectedView?.let { view ->
                try {
                    val w = etPropWidth.text.toString().toIntOrNull()
                    val h = etPropHeight.text.toString().toIntOrNull()
                    if (w != null && h != null) {
                        val params = view.layoutParams as ConstraintLayout.LayoutParams
                        val density = resources.displayMetrics.density
                        params.width = (w * density).toInt()
                        params.height = (h * density).toInt()
                        view.layoutParams = params
                    }
                    
                    val colorStr = etPropColor.text.toString()
                    if (colorStr.isNotEmpty()) {
                        try {
                             view.setBackgroundColor(Color.parseColor(colorStr))
                        } catch (e: Exception) {}
                    }
                    
                    if (view is TextView) view.text = etPropName.text.toString()
                    else if (view is Button) view.text = etPropName.text.toString()
                    else if (view is SwitchMaterial) view.text = etPropName.text.toString()
                    
                    Toast.makeText(this, "Updated", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showGradientColorPicker() {
        val width = 600
        val height = 400
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        
        val gradient = LinearGradient(0f, 0f, width.toFloat(), 0f,
            intArrayOf(Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED),
            null, Shader.TileMode.CLAMP)
        val paint = android.graphics.Paint()
        paint.shader = gradient
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        
        val darkGradient = LinearGradient(0f, 0f, 0f, height.toFloat(),
            intArrayOf(Color.TRANSPARENT, Color.BLACK),
            null, Shader.TileMode.CLAMP)
        val darkPaint = android.graphics.Paint()
        darkPaint.shader = darkGradient
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), darkPaint)

        val imageView = ImageView(this)
        imageView.setImageBitmap(bitmap)
        
        val dialog = AlertDialog.Builder(this)
            .setTitle("Select Color")
            .setView(imageView)
            .setPositiveButton("Close", null)
            .create()
            
        imageView.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
                val x = event.x.toInt().coerceIn(0, width - 1)
                val y = event.y.toInt().coerceIn(0, height - 1)
                val pixel = bitmap.getPixel(x, y)
                val hexBox = String.format("#%06X", (0xFFFFFF and pixel))
                etPropColor.setText(hexBox)
                dialog.setTitle("Color: $hexBox")
            }
            true
        }
        
        dialog.show()
    }
    
    private fun setupConsole() {
        val rvLogs = findViewById<RecyclerView>(R.id.rvConsoleLogs)
        etTopic = findViewById(R.id.etConsoleTopic)
        etPayload = findViewById(R.id.etConsolePayload)
        btnSend = findViewById(R.id.btnConsoleSend)
        val btnSubscribe = findViewById<Button>(R.id.btnConsoleSubscribe) // New v18
        
        logAdapter = LogAdapter()
        rvLogs.layoutManager = LinearLayoutManager(this)
        rvLogs.adapter = logAdapter
         MqttRepository.logs.observe(this) { logs ->
            logAdapter.submitList(logs)
            if (logs.isNotEmpty()) rvLogs.smoothScrollToPosition(logs.size - 1)
        }
        
        btnSend.setOnClickListener {
            var topic = etTopic.text.toString()
            val payload = etPayload.text.toString()
            
            // v38: Auto-Prefix for Console
            if (project != null && projectId != null) {
                val prefix = "${project!!.name.lowercase()}/$projectId/"
                if (!topic.startsWith(prefix) && topic.isNotEmpty()) {
                    topic = prefix + topic
                }
            }
            
            if (topic.isNotEmpty() && payload.isNotEmpty()) {
                val serviceIntent = android.content.Intent(this, com.example.mqttpanelcraft.service.MqttService::class.java)
                serviceIntent.action = "PUBLISH"
                serviceIntent.putExtra("TOPIC", topic)
                serviceIntent.putExtra("PAYLOAD", payload)
                startService(serviceIntent) // Send via service
            }
        }
        
        btnSubscribe.setOnClickListener {
            var topic = etTopic.text.toString()
            
            // v38: Auto-Prefix for Console
            if (project != null && projectId != null) {
                val prefix = "${project!!.name.lowercase()}/$projectId/"
                if (!topic.startsWith(prefix) && topic.isNotEmpty()) {
                    topic = prefix + topic
                }
            }
            
            if (topic.isNotEmpty()) {
                val serviceIntent = android.content.Intent(this, com.example.mqttpanelcraft.service.MqttService::class.java)
                serviceIntent.action = "SUBSCRIBE"
                serviceIntent.putExtra("TOPIC", topic)
                startService(serviceIntent)
                Toast.makeText(this, "Subscribing to $topic...", Toast.LENGTH_SHORT).show()
            } else {
                 etTopic.error = "Topic required"
            }
        }
    }
    private fun checkMqttConnection() {
        val client = MqttRepository.mqttClient
        if (client == null || !client.isConnected) {
            MqttRepository.addLog("System: Editor opened without active MQTT connection.", "")
        } else {
            MqttRepository.addLog("System: Connected to broker.", "")
        }
    }

    private fun toggleMode() {
        isEditMode = !isEditMode
        updateModeUI()
    }

    private fun updateModeUI() {
        val sidebarEditMode = findViewById<View>(R.id.sidebarEditMode)
        val sidebarRunMode = findViewById<View>(R.id.sidebarRunMode)

        if (isEditMode) {
             fabMode.setImageResource(android.R.drawable.ic_media_play) 
             editorCanvas.background = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.bg_grid_pattern)
             // Edit Mode: Drawer unlocked intentionally? Or locked closed?
             // User wants to open sidebar in Edit mode (Components). 
             drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.START)
             
             sidebarEditMode?.visibility = View.VISIBLE
             sidebarRunMode?.visibility = View.GONE
             
             containerLogs.visibility = View.GONE
             containerProperties.visibility = View.VISIBLE 
             guideOverlay.visibility = View.VISIBLE 
             guideOverlay.bringToFront()
             Toast.makeText(this, "Edit Mode", Toast.LENGTH_SHORT).show()
        } else {
             fabMode.setImageResource(android.R.drawable.ic_menu_edit)
             editorCanvas.background = null
             guideOverlay.clear()
             guideOverlay.visibility = View.GONE
             
             // Run Mode: User now wants to open sidebar for Export/Import buttons
             // So we UNLOCK it here too.
             drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.START)
             
             sidebarEditMode?.visibility = View.GONE
             sidebarRunMode?.visibility = View.VISIBLE
             
             containerLogs.visibility = View.VISIBLE
             containerProperties.visibility = View.GONE
             Toast.makeText(this, "Run Mode", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSidebarInteraction() {
        val touchListener = View.OnTouchListener { view, event ->
             if (event.action == MotionEvent.ACTION_DOWN) {
                 val item = ClipData.Item(view.tag as? CharSequence)
                 val dragData = ClipData(view.tag as? CharSequence, arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN), item)
                 val shadow = View.DragShadowBuilder(view)
                 view.startDragAndDrop(dragData, shadow, view, 0)
                 
                 drawerLayout.closeDrawer(GravityCompat.START)
                 return@OnTouchListener true
             }
             false
        }

        findViewById<View>(R.id.cardText).apply { tag="TEXT"; setOnTouchListener(touchListener) }
        findViewById<View>(R.id.cardImage).apply { tag="IMAGE"; setOnTouchListener(touchListener) }
        
        findViewById<View>(R.id.cardButton).apply { tag="BUTTON"; setOnTouchListener(touchListener) }
        findViewById<View>(R.id.cardSlider).apply { tag="SLIDER"; setOnTouchListener(touchListener) }
        
        findViewById<View>(R.id.cardLed).apply { tag="LED"; setOnTouchListener(touchListener) }
        findViewById<View>(R.id.cardThermometer).apply { tag="THERMOMETER"; setOnTouchListener(touchListener) }

        editorCanvas.setOnDragListener { v, event ->
            if (!isEditMode) return@setOnDragListener false
            when (event.action) {
                DragEvent.ACTION_DROP -> {
                     handleDrop(event)
                }
                DragEvent.ACTION_DRAG_LOCATION -> {
                     val localState = event.localState as? View
                     if (localState != null && localState.parent == editorCanvas) {
                         checkAlignment(event.x, event.y, localState)
                     }
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    guideOverlay.clear()
                    val localState = event.localState as? View
                    localState?.visibility = View.VISIBLE
                }
            }
            true
        }
    }
    
    private fun handleDrop(event: DragEvent) {
        val x = event.x
        val y = event.y
        val localView = event.localState as? View
        
        if (localView != null && localView.parent == editorCanvas) {
            guideOverlay.clear()
             var finalX = x - (localView.width/2)
             var finalY = y - (localView.height/2)
             
             val snapPos = calculateSnap(x, y, localView.width, localView.height, localView)
             if (snapPos != null) {
                 finalX = snapPos.x.toFloat()
                 finalY = snapPos.y.toFloat()
             }
             
             localView.x = finalX
             localView.y = finalY
             localView.visibility = View.VISIBLE
        } else {
            val tag = event.clipData?.getItemAt(0)?.text?.toString() ?: "TEXT"
            val newView = createComponentView(tag)
            
            // v16: Default Size 50x50 dp
            val density = resources.displayMetrics.density
            val sizePx = (50 * density).toInt()
            
            val params = ConstraintLayout.LayoutParams(sizePx, sizePx)
            newView.layoutParams = params
            newView.x = x - (sizePx / 2)
            newView.y = y - (sizePx / 2)
            
            // v16: Unique ID Fix
            newView.id = View.generateViewId()
            
            editorCanvas.addView(newView)
            makeDraggable(newView) 
        }
    }
    
    private fun createComponentView(tag: String): View {
        // v24: Calculate Index
        val type = com.example.mqttpanelcraft.utils.TopicHelper.getComponentType(tag)
        var count = 0
        for (i in 0 until editorCanvas.childCount) {
             val child = editorCanvas.getChildAt(i)
             val childTag = child.tag
             // We need to store the specific type in tag or checking class/properties
             // For now, let's assume we can check the view type or we start tagging views with "type:index"
             // But existing code uses tag logic for DnD.
             // Let's iterate and check if we can identify them.
             // Better approach: Store "componentType" in a view property (tag is used for DnD source, but destination view can use tag too)
             if (child.tag is String && (child.tag as String).startsWith(type)) {
                 count++
             }
        }
        val nextIndex = (count + 1).toString()
        
        // Generate Topic
        val topic = if (project != null && projectId != null) {
             com.example.mqttpanelcraft.utils.TopicHelper.formatTopic(
                 project!!.name,
                 projectId!!,
                 type,
                 nextIndex,
                 "cmd" // Default to cmd for buttons, others might be rep
             )
        } else {
            "unknown"
        }

        return when (tag) {
            "TEXT" -> TextView(this).apply {
                text = "Txt $nextIndex"
                setBackgroundColor(0xFFE1F5FE.toInt())
                gravity = Gravity.CENTER
                setTextColor(Color.BLACK)
                this.tag = "$type:$nextIndex" // Store type info
                // We might want to store the topic too, use setTag(R.id..., topic)? 
                // For prototype, simple tag is risky if we reuse it.
                // Let's allow DnD to work by not breaking standard tag usage if it relies on it?
                // DnD source uses tag. Destination views don't seem to use it for logic yet.
            }
            "IMAGE" -> ImageView(this).apply {
                setImageResource(android.R.drawable.ic_menu_gallery)
                setBackgroundColor(Color.LTGRAY)
                scaleType = ImageView.ScaleType.FIT_CENTER
                this.tag = "$type:$nextIndex"
            }
            "BUTTON" -> Button(this).apply {
                text = "Btn $nextIndex"
                setPadding(0,0,0,0) 
                this.tag = "$type:$nextIndex"
            }
            "SLIDER" -> com.google.android.material.slider.Slider(this).apply {
                valueFrom = 0f
                valueTo = 100f
                value = 50f
                this.tag = "$type:$nextIndex"
            }
            "LED" -> View(this).apply {
                setBackgroundResource(R.drawable.shape_circle_green) // Using existing circle shape
                this.tag = "$type:$nextIndex"
            }
            "THERMOMETER" -> android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                progress = 50
                max = 100
                this.tag = "$type:$nextIndex"
            }
            else -> TextView(this).apply { text = "?" }
        }.also { view ->
            // Store Topic in a keyed tag to avoid conflict
            // We define a resource id or just use contentDescription for now if lazy?
            // Ideally define <item name="tag_topic" type="id"/> in ids.xml
            // But I cannot edit resources easily without checking ids.xml.
            // I'll use a unique Map or just a runtime property map in Activity for this session.
            // Or just contentDescription as a hack for prototype "storage"
            view.contentDescription = topic
            
            // v29: Initialize with Cached State if available
            val cachedPayload = MqttRepository.getTopicState(topic) ?: MqttRepository.getTopicState("$topic/rep")
            // Try both specific rep topic or just topic if cache stored differently. 
            // Our cache stores topic as key. Incoming rep msg has topic .../rep
            // If we generated topic "cmd", we need to check "rep".
            
            // topic variable is ".../cmd"
            // rep topic is ".../rep"
            val repTopic = topic.replace("/cmd", "/rep")
            val cached = MqttRepository.getTopicState(repTopic)
            
            if (cached != null) {
                 updateComponentState(view, type, cached)
            }
        }
    }
    
    private fun makeDraggable(view: View) {
        view.setOnClickListener {
            if (isEditMode) {
                selectedView = view
                populateProperties(view)
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            } else {
                // v25: Run Mode - Send Command
                val topic = view.contentDescription?.toString()
                if (topic != null) {
                    val serviceIntent = android.content.Intent(this, com.example.mqttpanelcraft.service.MqttService::class.java)
                    serviceIntent.action = "PUBLISH"
                    serviceIntent.putExtra("TOPIC", topic) // Topic is already ".../cmd"
                    
                    // Payload Strategy:
                    // For Button: Toggle or Push? Standard "1" for now as per plan
                    val payload = "1" 
                    
                    serviceIntent.putExtra("PAYLOAD", payload)
                    startService(serviceIntent)
                    
                    Toast.makeText(this, "CMD: $topic -> $payload", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "No Topic Assigned", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        view.setOnLongClickListener {
            if (!isEditMode) return@setOnLongClickListener false
             val data = ClipData.newPlainText("id", view.id.toString()) 
             val shadow = View.DragShadowBuilder(view)
             view.startDragAndDrop(data, shadow, view, 0)
             view.visibility = View.INVISIBLE 
             true
        }
    }

    // v25: Subscribe to wildcard topic for all components
    // v29: Modified for Full Sync
    private fun subscribeToProjectTopics(projectName: String, projectId: String) {
        // Topic: {project}/{id}/+/+/rep
        val topicFilter = "${projectName.lowercase()}/$projectId/+/+/rep"
        val serviceIntent = android.content.Intent(this, com.example.mqttpanelcraft.service.MqttService::class.java)
        serviceIntent.action = "SUBSCRIBE"
        serviceIntent.putExtra("TOPIC", topicFilter)
        startService(serviceIntent)
    }
    
    // v29: Perform Full Sync (Subscribe All -> Wait -> Unsubscribe Inactive)
    private fun performFullSync() {
        if (projectId == null) return
        
        // v37: Use CoroutineScope instead of lifecycleScope (missing dependency)
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val allProjects = ProjectRepository.getAllProjects()
            val currentId = projectId
            
            // 1. Subscribe to ALL projects
            for (p in allProjects) {
                val pName = p.name.lowercase().replace("\\s".toRegex(), "_")
                // Subscribe to .../rep
                val topic = "$pName/${p.id}/+/+/rep"
                
                val intent = android.content.Intent(this@ProjectViewActivity, com.example.mqttpanelcraft.service.MqttService::class.java)
                intent.action = "SUBSCRIBE"
                intent.putExtra("TOPIC", topic)
                startService(intent)
            }
            
            // 2. Wait for Retained Messages
            kotlinx.coroutines.delay(2000)
            
            // 3. Unsubscribe from Inactive
            for (p in allProjects) {
                if (p.id != currentId) {
                    val pName = p.name.lowercase().replace("\\s".toRegex(), "_")
                    val topic = "$pName/${p.id}/+/+/rep"
                    
                    val intent = android.content.Intent(this@ProjectViewActivity, com.example.mqttpanelcraft.service.MqttService::class.java)
                    intent.action = "UNSUBSCRIBE"
                    intent.putExtra("TOPIC", topic)
                    startService(intent)
                }
            }
        }
    }

    // v25: Handle incoming messages to update UI
    private fun handleMqttMessage(topic: String, payload: String) {
         // Expected: {project}/{id}/{type}/{index}/rep
         val parts = topic.split("/")
         if (parts.size < 5) return // Invalid format
         
         // parts[0]=proj, parts[1]=id, parts[2]=type, parts[3]=index, parts[4]=rep
         val type = parts[2]
         val index = parts[3]
         val direction = parts[4]
         
         if (direction != "rep") return // Only process reports
         
         // Find View in editorCanvas
         // Tag format: "type:index" e.g. "button:1"
         val targetTag = "$type:$index"
         
         // Iterate to find view with this tag
         var targetView: View? = null
         for (i in 0 until editorCanvas.childCount) {
             val child = editorCanvas.getChildAt(i)
             if (child.tag == targetTag) {
                 targetView = child
                 break
             }
         }
         
         if (targetView != null) {
             updateComponentState(targetView, type, payload)
         }
    }
    
    private fun updateComponentState(view: View, type: String, payload: String) {
        try {
            when (type.lowercase()) {
                "led" -> {
                    val isOn = payload.equals("1") || payload.equals("on", true) || payload.equals("true", true)
                    
                    if (isOn) {
                        view.setBackgroundResource(R.drawable.shape_circle_green)
                        view.backgroundTintList = null // Use original color (Green)
                    } else {
                        view.setBackgroundResource(R.drawable.shape_circle_green) 
                        view.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.LTGRAY)
                    }
                }
                "text" -> {
                    if (view is TextView) {
                        view.text = payload
                    }
                }
                "button" -> {
                     // Optional
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun checkAlignment(x: Float, y: Float, currentView: View) {
        guideOverlay.clear()
        val threshold = snapThreshold * resources.displayMetrics.density
        
        for (i in 0 until editorCanvas.childCount) {
            val other = editorCanvas.getChildAt(i)
            if (other == currentView || other == guideOverlay) continue
            
            val otherCx = other.x + other.width/2
            val otherCy = other.y + other.height/2
            
            if (kotlin.math.abs(x - otherCx) < threshold) {
                guideOverlay.addLine(otherCx, 0f, otherCx, editorCanvas.height.toFloat())
            }
            if (kotlin.math.abs(y - otherCy) < threshold) {
                guideOverlay.addLine(0f, otherCy, editorCanvas.width.toFloat(), otherCy)
            }
        }
    }

    private fun calculateSnap(rawX: Float, rawY: Float, w: Int, h: Int, currentView: View): Point? {
        var bestX = rawX - w/2
        var bestY = rawY - h/2
        var snapped = false
        val threshold = snapThreshold * resources.displayMetrics.density
        
        for (i in 0 until editorCanvas.childCount) {
            val other = editorCanvas.getChildAt(i)
            if (other == currentView || other == guideOverlay) continue
            
            val otherCx = other.x + other.width/2
            val otherCy = other.y + other.height/2
            
            if (kotlin.math.abs(rawX - otherCx) < threshold) {
                bestX = otherCx - w/2
                snapped = true
            }
            if (kotlin.math.abs(rawY - otherCy) < threshold) {
                bestY = otherCy - h/2
                snapped = true
            }
        }
        return if (snapped) Point(bestX.toInt(), bestY.toInt()) else null
    }
    
     private fun populateProperties(view: View) {
         val params = view.layoutParams
        val density = resources.displayMetrics.density
        etPropWidth.setText((params.width / density).toInt().toString())
        etPropHeight.setText((params.height / density).toInt().toString())
        
        if (view is TextView) etPropName.setText(view.text)
        else if (view is Button) etPropName.setText(view.text)
        else if (view is SwitchMaterial) etPropName.setText(view.text)
        
        // v24: Show Topic in Properties (ReadOnly)
        // We need a TextView in layout for this.
        // Assuming we will add it or reuse an existing field?
        // Let's use Toast for now or check if we can add it to layout quickly.
        // Or if there is a console topic field, maybe unrelated.
        val topic = view.contentDescription?.toString() ?: "No Topic"
        // Let's Find specific view or just Toast it for verification if UI field is missing.
        // But plan said "Display in properties panel".
        // I will add a TextView to the properties layout in the next step.
        val tvPropTopic = findViewById<TextView>(R.id.tvPropTopic)
        if (tvPropTopic != null) {
            tvPropTopic.text = "Topic: $topic"
            tvPropTopic.visibility = View.VISIBLE
        }
     }
}

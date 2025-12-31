package com.example.mqttpanelcraft

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.DragEvent
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProvider
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.model.Project
import com.example.mqttpanelcraft.model.ProjectType
import com.example.mqttpanelcraft.ui.AlignmentOverlayView
import com.example.mqttpanelcraft.ui.CanvasManager
import com.example.mqttpanelcraft.ui.IdleAdController
import com.example.mqttpanelcraft.ui.PropertiesSheetManager
import com.example.mqttpanelcraft.ui.SidebarManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.slider.Slider
import java.util.Locale

class ProjectViewActivity : AppCompatActivity() {

    // View Model
    private lateinit var viewModel: ProjectViewModel

    // Managers
    private lateinit var canvasManager: CanvasManager
    private lateinit var sidebarManager: SidebarManager
    private lateinit var propertiesManager: PropertiesSheetManager
    private lateinit var mqttHandler: MqttHandler
    private lateinit var idleAdController: IdleAdController

    // UI Elements
    private lateinit var editorCanvas: FrameLayout
    private lateinit var guideOverlay: AlignmentOverlayView
    private lateinit var dropDeleteZone: View
    private lateinit var fabMode: FloatingActionButton
    private lateinit var drawerLayout: DrawerLayout
    
    // State
    private var isEditMode = false // Default to Run Mode
    private var selectedCameraComponentId: Int = -1

    // Image Picker
    private val startGalleryForResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null && selectedCameraComponentId != -1) {
                processAndSendImage(uri, selectedCameraComponentId)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            // Ensure Data/Ad Init
            com.example.mqttpanelcraft.data.ProjectRepository.initialize(applicationContext)
            com.example.mqttpanelcraft.utils.AdManager.initialize(this)
            
            setContentView(R.layout.activity_project_view)
            
            // Fix: Status Bar Spacing & Color
            val root = findViewById<View>(R.id.rootCoordinator)
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
                val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
                insets
            }
            
            // Set Status Bar Appearance (Light/Dark)
            val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            val isDarkTheme = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            windowInsetsController.isAppearanceLightStatusBars = !isDarkTheme 

        // Initialize ViewModel
        viewModel = ViewModelProvider(this)[ProjectViewModel::class.java]

        // Initialize UI References
        // Initialize UI References
        editorCanvas = findViewById<FrameLayout>(R.id.editorCanvas)
        guideOverlay = findViewById(R.id.guideOverlay)
        dropDeleteZone = findViewById(R.id.dropDeleteZone)
        fabMode = findViewById(R.id.fabMode)
        drawerLayout = findViewById(R.id.drawerLayout)
        val containerProperties = findViewById<View>(R.id.containerProperties) // View type to match XML usage (ScrollView) or parent
        val bottomSheet = findViewById<View>(R.id.bottomSheet) // Corrected ID from xml

        // Initialize Managers
        initializeManagers(bottomSheet, containerProperties)

        setupObservers()

        // Load Project
        val projectId = intent.getStringExtra("PROJECT_ID")
        if (projectId != null) {
            viewModel.loadProject(projectId)
        } else {
            Toast.makeText(this, "Error: No Project ID", Toast.LENGTH_SHORT).show()
            finish()
        }

        setupUI()
            
            // Update UI to match initial Run Mode state
            updateModeUI()
            
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error initializing Project View: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun initializeManagers(bottomSheet: View, containerProperties: View?) {
        // MqttHandler
        mqttHandler = MqttHandler(this,
            onMessageReceived = { topic, message ->
                 runOnUiThread {
                     logAdapter.addLog("Msg: $topic -> $message")
                     updateComponentFromMqtt(topic, message)
                 }
            },
            onConnectionStatusChanged = { isConnected ->
                 runOnUiThread {
                     logAdapter.addLog(if(isConnected) "Connected to Broker" else "Disconnected")
                     updateStatusIndicator(isConnected)
                 }
            }
        )

        // CanvasManager
        // CanvasManager
        canvasManager = CanvasManager(
            canvasCanvas = editorCanvas,
            guideOverlay = guideOverlay,
            dropDeleteZone = dropDeleteZone,
            onComponentDropped = { view -> 
                 // No-op or specific actions
                 componentViewCache[view.id] = view
            },
            onComponentMoved = { view -> 
                viewModel.saveProject()
            },
            onComponentDeleted = { view ->
                 viewModel.saveProject()
            },
            onCreateNewComponent = { tag, x, y ->
                // Create View
                val view = createComponentView(tag)
                view.id = View.generateViewId()
                
                // Calculate size to center it on finger
                val w = 100 * resources.displayMetrics.density.toInt() // Default estimate?
                val h = 100 * resources.displayMetrics.density.toInt()
                // Or better, let layout happen, but we need to set params.
                // createComponentView uses 8dp padding and internal content.
                // Let's rely on standard size or measure it.
                // For simplicity, we set a default size then snap.
                
                val params = FrameLayout.LayoutParams(
                     (100*resources.displayMetrics.density).toInt(), 
                     (100*resources.displayMetrics.density).toInt()
                )
                if (tag == "SLIDER") params.width = (200*resources.displayMetrics.density).toInt()
                if (tag == "TEXT") params.width = (150*resources.displayMetrics.density).toInt()
                
                view.layoutParams = params
                
                 // Snap Calculation using Manager's exposed util
                val snapped = canvasManager.getSnappedPosition(
                    x - params.width / 2f, 
                    y - params.height / 2f, 
                    params.width, 
                    params.height, 
                    null
                )
                view.x = snapped.x.toFloat()
                view.y = snapped.y.toFloat()
                
                editorCanvas.addView(view)
                makeDraggable(view)

                // Label
                val label = TextView(this).apply {
                    text = if(tag=="TEXT") "Label" else tag
                    this.tag = "LABEL_FOR_${view.id}"
                    this.x = view.x
                    this.y = view.y + params.height + 4
                }
                editorCanvas.addView(label)
                
                componentViewCache[view.id] = view
                
                // Create Data Object
                val componentData = ComponentData(
                    id = view.id,
                    type = tag,
                    topicConfig = "${viewModel.project.value?.name ?: "project"}/$tag/${view.id}",
                    x = view.x,
                    y = view.y,
                    width = params.width,
                    height = params.height,
                    label = if(tag=="TEXT") "Label" else tag,
                    props = mutableMapOf()
                )
                
                viewModel.addComponent(componentData)
                viewModel.saveProject()
            }
        )
        canvasManager.setupDragListener { isEditMode }

        // SidebarManager
        sidebarManager = SidebarManager(
            drawerLayout = drawerLayout,
            propertyContainer = containerProperties, 
            componentContainer = findViewById<View>(R.id.sidebarEditMode),
            runModeContainer = findViewById<View>(R.id.sidebarRunMode),
            onComponentDragStart = { _, _ -> }
        )

        // PropertiesManager
        propertiesManager = PropertiesSheetManager(
             rootView = window.decorView,
             propertyContainer = bottomSheet,
             onPropertyUpdated = { id, name, w, h, color, topicConfig -> 
                 val view = editorCanvas.findViewById<View>(id)
                 if (view != null) {
                     val params = view.layoutParams
                     params.width = (w * resources.displayMetrics.density).toInt()
                     params.height = (h * resources.displayMetrics.density).toInt()
                     view.layoutParams = params
                     
                     val label = editorCanvas.findViewWithTag<TextView>("LABEL_FOR_$id")
                     label?.text = name
                     
                     try {
                         if (color.isNotEmpty()) view.setBackgroundColor(Color.parseColor(color))
                     } catch(e: Exception){}
                     
                     viewModel.saveProject()
                 }
             }
        )
        
        // IdleAdController
        idleAdController = IdleAdController(this) {
            // setupWindowInsets() 
        }
    }

    private fun setupObservers() {
        viewModel.project.observe(this) { project ->
            if (project != null) {
                mqttHandler.connect(project.broker, project.clientId)
                
                if (editorCanvas.childCount == 0 && project.components.isNotEmpty()) {
                    restoreComponents(project.components)
                }
            }
        }
    }

    private fun setupUI() {
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(android.R.drawable.ic_menu_sort_by_size)
        toolbar.setNavigationOnClickListener { 
            sidebarManager.showComponentsPanel() 
        }

        findViewById<View>(R.id.btnSettings).setOnClickListener {
            val projectId = viewModel.project.value?.id
            if (projectId != null) {
                val intent = Intent(this, SetupActivity::class.java)
                intent.putExtra("PROJECT_ID", projectId)
                startActivity(intent)
            }
        }
        
        // Grid Toggle (Sidebar Switch)
        // Note: View might be in sidebar layout. 
        val switchGrid = findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchGridToggle)
        switchGrid?.setOnCheckedChangeListener { _, isChecked ->
             guideOverlay.setGridVisible(isChecked)
        }
        
        // Remove old button logic or hide it if present in XML
        findViewById<View>(R.id.btnGrid)?.visibility = View.GONE

        fabMode.setOnClickListener {
            val project = viewModel.project.value
            if (project == null) {
                Toast.makeText(this, "Project loading...", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (project.type == ProjectType.FACTORY && !isEditMode) {
                 showPasswordDialog { toggleMode() }
            } else {
                toggleMode()
            }
        }
        
        setupLogs()

        setupSidebarInteraction()
    }
    
    private fun setupSidebarInteraction() {
        val categories = listOf(
            R.id.cardText to "TEXT",
            R.id.cardImage to "IMAGE",
            R.id.cardButton to "BUTTON",
            R.id.cardSlider to "SLIDER",
            R.id.cardLed to "LED",
            R.id.cardThermometer to "THERMOMETER",
            R.id.cardCamera to "CAMERA"
        )
        
        val touchListener = View.OnTouchListener { view, event ->
             if (event.action == MotionEvent.ACTION_DOWN) {
                 val tag = view.tag as? String ?: return@OnTouchListener false
                 
                 val item = ClipData.Item(tag)
                 val dragData = ClipData(tag, arrayOf(android.content.ClipDescription.MIMETYPE_TEXT_PLAIN), item)
                 val shadow = View.DragShadowBuilder(view)
                 view.startDragAndDrop(dragData, shadow, view, 0)
                 
                 sidebarManager.closeDrawer()
                 return@OnTouchListener true
             }
             false
        }

        categories.forEach { (id, tag) ->
            findViewById<View>(id)?.apply {
                this.tag = tag
                setOnTouchListener(touchListener)
            }
        }
    }

    // Logs
    private val logAdapter = com.example.mqttpanelcraft.adapter.LogAdapter()

    private fun toggleMode() {
        try {
            isEditMode = !isEditMode
            updateModeUI()
            setComponentsInteractive(!isEditMode)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error toggling mode: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun updateModeUI() {
        if (isEditMode) {
            fabMode.setImageResource(android.R.drawable.ic_media_play)
            guideOverlay.visibility = if (guideOverlay.isGridVisible()) View.VISIBLE else View.VISIBLE // Logic complexity here
            // If grid is ON, overlay is VISIBLE. if grid is OFF, overlay might be GONE?
            // Wait, guideOverlay handles BOTH grid and alignment lines.
            // If Edit Mode, Alignment lines should be active. Grid is optional.
            // So overlay should ALWAYS be visible in Edit Mode to show alignment lines if active, or just ready for them.
            // Actually, `checkAlignment` draws lines.
            guideOverlay.visibility = View.VISIBLE
            
            sidebarManager.showComponentsPanel()
            idleAdController.stop()
        } else {
            fabMode.setImageResource(android.R.drawable.ic_menu_edit)
            guideOverlay.visibility = View.GONE
            sidebarManager.showRunModePanel()
            idleAdController.start()
            
            viewModel.project.value?.let { p ->
                mqttHandler.subscribe("${p.name.lowercase(Locale.ROOT)}/${p.id}/#")
                logAdapter.addLog("Subscribed to project topic")
            }
        }
    }
    
    private fun setupLogs() {
        val rvLogs = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvConsoleLogs)
        rvLogs.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        rvLogs.adapter = logAdapter
        
        findViewById<Button>(R.id.btnConsoleSubscribe).setOnClickListener {
             val topic = findViewById<EditText>(R.id.etConsoleTopic).text.toString()
             if (topic.isNotEmpty()) {
                 mqttHandler.subscribe(topic)
                 logAdapter.addLog("Subscribed: $topic")
             }
        }
        
        findViewById<Button>(R.id.btnConsoleSend).setOnClickListener {
             val payload = findViewById<EditText>(R.id.etConsolePayload).text.toString()
             // Topic? User usually needs a topic.
             // The UI shows Topic and Payload inputs in separate rows? 
             // Activity XML shows etConsoleTopic and btnConsoleSubscribe in one row.
             // And etConsolePayload and btnConsoleSend in another. But where is the topic for Send?
             // Maybe it uses `etConsoleTopic`?
             val topic = findViewById<EditText>(R.id.etConsoleTopic).text.toString()
             if (topic.isNotEmpty()) {
                 mqttHandler.publish(topic, payload)
                 logAdapter.addLog("Pub: $topic -> $payload")
             } else {
                 Toast.makeText(this, "Enter Topic", Toast.LENGTH_SHORT).show()
             }
        }
    }

    private fun updateStatusIndicator(isConnected: Boolean) {
        val dot = findViewById<View>(R.id.viewStatusDot)
        if (isConnected) {
            dot.setBackgroundResource(R.drawable.shape_circle_green)
            dot.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.GREEN)
        } else {
            dot.setBackgroundResource(R.drawable.shape_circle_green) // Using same shape, changing tint
            dot.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.RED)
        }
    }

    private fun setComponentsInteractive(enable: Boolean) {
        for (i in 0 until editorCanvas.childCount) {
             val child = editorCanvas.getChildAt(i)
             if (child is FrameLayout && child.childCount > 0) {
                 val inner = child.getChildAt(0)
                 if (inner is Button || inner is Slider) {
                     inner.isEnabled = enable
                     inner.isClickable = enable
                 }
                 child.findViewWithTag<View>("CLEAR_BTN")?.visibility = if (enable) View.VISIBLE else View.GONE
             }
        }
    }

    private fun restoreComponents(components: List<ComponentData>) {
        editorCanvas.removeAllViews()
        componentViewCache.clear()
        
        components.forEach { comp ->
             val view = createComponentView(comp.type)
             view.id = comp.id
             componentViewCache[comp.id] = view
             
             val params = FrameLayout.LayoutParams(comp.width, comp.height)
             view.layoutParams = params
             view.x = comp.x
             view.y = comp.y
             
             editorCanvas.addView(view)
             makeDraggable(view)

             val label = TextView(this).apply {
                 text = comp.label
                 tag = "LABEL_FOR_${view.id}"
                 x = view.x
                 y = view.y + view.height + 4
             }
             editorCanvas.addView(label)
        }
    }

    private fun createComponentView(tag: String): View {
        val container = FrameLayout(this)
        container.setBackgroundResource(R.drawable.component_border)
        container.setPadding(8,8,8,8)
        container.tag = tag
        
        val content: View = when(tag) {
            "BUTTON" -> Button(this).apply { 
                text = "BTN" 
                setOnClickListener {
                     if (!isEditMode) {
                         val topic = "${viewModel.project.value?.name}/button/${(parent as View).id}"
                         mqttHandler.publish(topic, "1")
                     }
                }
            }
            "TEXT" -> TextView(this).apply { text = "Text"; gravity = android.view.Gravity.CENTER }
            "SLIDER" -> Slider(this).apply { 
                valueFrom=0f; valueTo=100f 
                addOnChangeListener { _, value, fromUser ->
                    if (fromUser && !isEditMode) {
                        val topic = "${viewModel.project.value?.name}/slider/${(parent as View).id}"
                        mqttHandler.publish(topic, value.toInt().toString())
                    }
                }
            }
            "LED" -> View(this).apply { 
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(Color.RED)
                }
            }
            "IMAGE" -> ImageView(this).apply { 
                setImageResource(android.R.drawable.ic_menu_gallery)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            "CAMERA" -> Button(this).apply { 
                text = "CAM"
                setOnClickListener { 
                    if(!isEditMode) openGallery((parent as View).id) 
                }
            }
            else -> TextView(this).apply { text = tag }
        }
        
        container.addView(content, FrameLayout.LayoutParams(-1,-1))
        
        if (tag == "IMAGE") {
            val closeBtn = ImageButton(this).apply {
                setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                background = null
                layoutParams = FrameLayout.LayoutParams(64,64).apply { gravity = android.view.Gravity.TOP or android.view.Gravity.END }
                this.tag = "CLEAR_BTN"
                setOnClickListener { (content as? ImageView)?.setImageResource(android.R.drawable.ic_menu_gallery) }
            }
            container.addView(closeBtn)
        }

        return container
    }
    
    private fun makeDraggable(view: View) {
        view.setOnClickListener {
            if (!isEditMode) return@setOnClickListener
            Toast.makeText(this, "Editing Component...", Toast.LENGTH_SHORT).show()
            propertiesManager.showProperties(view, "Component", "")
        }
        
        view.setOnLongClickListener { v ->
             if (!isEditMode) return@setOnLongClickListener false
             
             val dragData = ClipData("MOVE", arrayOf(android.content.ClipDescription.MIMETYPE_TEXT_PLAIN), ClipData.Item("MOVE"))
             val shadow = View.DragShadowBuilder(v)
             v.startDragAndDrop(dragData, shadow, v, 0)
             v.visibility = View.INVISIBLE
             true
        }
        // Remove OnTouchListener that was consuming ACTION_DOWN
        view.setOnTouchListener(null) 
    }

    // Optimization: Cache component views by ID for fast lookup
    private val componentViewCache = mutableMapOf<Int, View>()

    private fun updateComponentFromMqtt(topic: String, message: String) {
        // Optimization: Parse topic to get ID is unsafe if format varies. 
        // Safer: Iterate map (faster than View traversal)
        componentViewCache.forEach { (id, view) ->
             if (topic.endsWith("/$id") || topic.contains("/$id/")) {
                if (view is FrameLayout) {
                    val inner = view.getChildAt(0)
                    if (inner is TextView && view.tag == "TEXT") inner.text = message
                    if (inner is View && view.tag == "LED") {
                         val color = if (message == "1" || message == "true") Color.GREEN else Color.RED
                         (inner.background as? android.graphics.drawable.GradientDrawable)?.setColor(color)
                    }
                }
             }
        }
    }

    private fun openGallery(viewId: Int) {
        selectedCameraComponentId = viewId
        val intent = Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startGalleryForResult.launch(intent)
    }

    private fun processAndSendImage(uri: Uri, viewId: Int) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val bytes = inputStream?.readBytes()
            inputStream?.close()
            if (bytes != null) {
                val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)
                val topic = "${viewModel.project.value?.name}/image/$viewId"
                mqttHandler.publish(topic, base64)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to send image", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showPasswordDialog(onSuccess: () -> Unit) {
        val input = EditText(this)
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Enter Password")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                if (input.text.toString() == "1234") onSuccess()
            }
            .show()
    }
    
    override fun onUserInteraction() {
        super.onUserInteraction()
        idleAdController.onUserInteraction()
    }
}

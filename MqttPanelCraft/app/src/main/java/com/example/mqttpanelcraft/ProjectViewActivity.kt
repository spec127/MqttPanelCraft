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
import com.example.mqttpanelcraft.ui.ComponentFactory
import com.example.mqttpanelcraft.ui.IdleAdController
import com.example.mqttpanelcraft.ui.PropertiesSheetManager
import com.example.mqttpanelcraft.ui.SidebarManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.slider.Slider
import java.util.Locale

class ProjectViewActivity : AppCompatActivity() {

    // View Model
    private lateinit var viewModel: ProjectViewModel



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

    // Managers
    private lateinit var logConsoleManager: com.example.mqttpanelcraft.ui.LogConsoleManager // New Manager
    private lateinit var canvasManager: CanvasManager
    private lateinit var sidebarManager: SidebarManager
    private lateinit var propertiesManager: PropertiesSheetManager
    private lateinit var mqttHandler: MqttHandler
    private lateinit var idleAdController: IdleAdController

    // ... (rest of vars)

    private fun initializeManagers(bottomSheet: View, containerProperties: View?) {
        // MqttHandler
        mqttHandler = MqttHandler(this,
            onMessageReceived = { topic, message ->
                 runOnUiThread {
                     logConsoleManager.addLog("Msg: $topic -> $message")
                     updateComponentFromMqtt(topic, message)
                 }
            },
            onConnectionStatusChanged = { isConnected ->
                 runOnUiThread {
                     logConsoleManager.addLog(if(isConnected) "Connected to Broker" else "Disconnected")
                     updateStatusIndicator(isConnected)
                 }
            }
        )
        
        // Log Console Manager
        logConsoleManager = com.example.mqttpanelcraft.ui.LogConsoleManager(
            window.decorView, // Pass root to find IDs
            mqttHandler
        )
        
        // CanvasManager
        canvasManager = CanvasManager(
            canvasCanvas = editorCanvas,
            guideOverlay = guideOverlay,
            dropDeleteZone = dropDeleteZone,
            onComponentDropped = { view -> 
                 componentViewCache[view.id] = view
            },
            onComponentMoved = { view -> 
                viewModel.saveProject()
            },
            onComponentDeleted = { view ->
                 viewModel.saveProject()
            },
            onCreateNewComponent = { tag, x, y ->
                // Create View using Factory
                val view = com.example.mqttpanelcraft.ui.ComponentFactory.createComponentView(this, tag, isEditMode)
                view.id = View.generateViewId()
                
                val (defW, defH) = com.example.mqttpanelcraft.ui.ComponentFactory.getDefaultSize(this, tag)
                val params = FrameLayout.LayoutParams(defW, defH)
                view.layoutParams = params
                
                 // Snap Calculation using Manager's exposed util
                val snapped = canvasManager.getSnappedPosition(
                    x, 
                    y, 
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
                
                // Retrieve initial config from View/Factory if needed?
                // For now, default ComponentData
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
                
                // Add logic for button/slider/cam to attach listeners that might be missing from Factory
                // The factory sets basic props, but we need Mqtt logic.
                // We should attach usage listeners here or in a helper
                attachComponentLogic(view, tag)
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
        
        idleAdController = IdleAdController(this) {}
    }

    private fun attachComponentLogic(view: View, tag: String) {
         // Lookup component data correctly for dynamic topic
         val component = viewModel.components.value?.find { it.id == view.id }
         val topic = component?.topicConfig ?: "${viewModel.project.value?.name}/$tag/${view.id}"

         if (view is FrameLayout && view.childCount > 0) {
             val content = view.getChildAt(0)
             if (tag == "BUTTON" && content is Button) {
                 content.setOnClickListener {
                     if (!isEditMode) {
                         // Use dynamic topic
                         mqttHandler.publish(topic, "1")
                     }
                 }
             }
             if (tag == "SLIDER" && content is Slider) {
                 content.addOnChangeListener { _, value, fromUser ->
                     if (fromUser && !isEditMode) {
                         mqttHandler.publish(topic, value.toInt().toString())
                     }
                 }
             }
             if (tag == "CAMERA" && content is Button) {
                 content.setOnClickListener { 
                    if(!isEditMode) openGallery(view.id) 
                 }
             }
             if (tag == "IMAGE") {
                 view.findViewWithTag<View>("CLEAR_BTN")?.setOnClickListener {
                      (content as? ImageView)?.setImageResource(android.R.drawable.ic_menu_gallery)
                 }
             }
         }
    }
    
    private fun restoreComponents(components: List<ComponentData>) {
        editorCanvas.removeAllViews()
        componentViewCache.clear()
        
        components.forEach { comp ->
             val view = com.example.mqttpanelcraft.ui.ComponentFactory.createComponentView(this, comp.type, isEditMode)
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
             
             attachComponentLogic(view, comp.type)
        }
    }

    // Removed createComponentView (moved to Factory) and setupLogs (moved to Manager)
    
    private fun updateModeUI() {
        if (isEditMode) {
             fabMode.setImageResource(android.R.drawable.ic_media_play)
             guideOverlay.visibility = View.VISIBLE
             
             // Edit Mode: 
             // 1. Sidebar shows Components (User wants to add components)
             // 2. Hide Logs, Show Properties Container
             sidebarManager.showComponentsPanel()
             
             findViewById<View>(R.id.containerLogs)?.visibility = View.GONE
             findViewById<View>(R.id.containerProperties)?.visibility = View.VISIBLE
             
             idleAdController.stop()
        } else {
             fabMode.setImageResource(android.R.drawable.ic_menu_edit)
             guideOverlay.visibility = View.GONE
             
             // Run Mode: 
             // 1. Sidebar shows Settings (User Request #1: "左邊的欄位應該是關於暗色設定")
             // 2. Hide Properties, Show Logs
             sidebarManager.showRunModePanel() 
             
             findViewById<View>(R.id.containerLogs)?.visibility = View.VISIBLE
             findViewById<View>(R.id.containerProperties)?.visibility = View.GONE
             
             propertiesManager.hide() // Ensure properties Sheet is collapsed
             
             idleAdController.start()
             
             viewModel.project.value?.let { p ->
                 mqttHandler.subscribe("${p.name.lowercase(Locale.ROOT)}/${p.id}/#")
                 logConsoleManager.addLog("Subscribed to project topic")
             }
        }
    }
    
    private fun setupUI() {
        // ... (standard setup)
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(android.R.drawable.ic_menu_sort_by_size)
        toolbar.setNavigationOnClickListener { sidebarManager.showComponentsPanel() }
        
        findViewById<View>(R.id.btnSettings).setOnClickListener {
            val projectId = viewModel.project.value?.id
            if (projectId != null) {
                val intent = Intent(this, SetupActivity::class.java)
                intent.putExtra("PROJECT_ID", projectId)
                startActivity(intent)
            }
        }
        
        // switchGrid removed. Using btnGrid instead.
        findViewById<View>(R.id.btnGrid).setOnClickListener {
             val isVisible = !guideOverlay.isGridVisible()
             guideOverlay.setGridVisible(isVisible)
             // Optional: Update icon tint or state if needed?
             // Since it's just an ImageView, maybe just toast or no visual feedback on button itself for now.
             // Or toggle alpha?
             it.alpha = if(isVisible) 1.0f else 0.5f 
        }

        fabMode.setOnClickListener {
            val project = viewModel.project.value
            if (project == null) return@setOnClickListener
            if (project.type == ProjectType.FACTORY && !isEditMode) {
                 showPasswordDialog { toggleMode() }
            } else { toggleMode() }
        }

        setupSidebarInteraction()
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


    
    private fun setupSidebarInteraction() {
        // ... (Existing component setup)
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
                 
                 // Generate "Real" Preview View for Shadow
                 val previewView = ComponentFactory.createComponentView(view.context, tag, true)
                 val (w, h) = ComponentFactory.getDefaultSize(view.context, tag)
                 
                 // Measure and Layout manualy for Shadow
                 val widthSpec = View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY)
                 val heightSpec = View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
                 previewView.measure(widthSpec, heightSpec)
                 previewView.layout(0, 0, w, h)
                 
                 val shadow = object : View.DragShadowBuilder(previewView) {
                     override fun onProvideShadowMetrics(outShadowSize: android.graphics.Point, outShadowTouchPoint: android.graphics.Point) {
                         outShadowSize.set(previewView.measuredWidth, previewView.measuredHeight)
                         outShadowTouchPoint.set(previewView.measuredWidth / 2, previewView.measuredHeight / 2)
                     }
                     override fun onDrawShadow(canvas: android.graphics.Canvas) {
                         previewView.draw(canvas)
                     }
                 }

                 view.startDragAndDrop(dragData, shadow, null, 0) // LocalState null because it's a new item
                 
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

        // --- Run Mode Settings Setup ---
        setupRunModeSidebar()
    }

    private fun setupRunModeSidebar() {
        val prefs = getSharedPreferences("ProjectViewPrefs", MODE_PRIVATE)

        // Orientation Lock
        val switchOrientation = findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchOrientationLock)
        if (switchOrientation != null) {
            val isLocked = prefs.getBoolean("orientation_locked", false)
            switchOrientation.isChecked = isLocked
            requestedOrientation = if (isLocked) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED 
                                   else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR

            switchOrientation.setOnCheckedChangeListener { _, isChecked ->
                requestedOrientation = if (isChecked) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED 
                                       else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR
                prefs.edit().putBoolean("orientation_locked", isChecked).apply()
            }
        }

        // Keep Screen On
        val switchScreen = findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchKeepScreenOn)
        if (switchScreen != null) {
            val isKeepOn = prefs.getBoolean("keep_screen_on", false)
            switchScreen.isChecked = isKeepOn
            if (isKeepOn) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            switchScreen.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                prefs.edit().putBoolean("keep_screen_on", isChecked).apply()
            }
        }
    }

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


    
    private fun makeDraggable(view: View) {
        view.setOnClickListener {
            if (!isEditMode) return@setOnClickListener
            // Toast.makeText(this, "Editing Component...", Toast.LENGTH_SHORT).show()
            
            val comp = viewModel.components.value?.find { it.id == view.id }
            if (comp != null) {
                propertiesManager.showProperties(view, comp.label, comp.topicConfig)
            }
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
             val comp = viewModel.components.value?.find { it.id == id }
             // Check if topic matches component's config
             // Handle wildcards if needed, but for now simple string match or suffix match
             if (comp != null && (topic == comp.topicConfig || topic.endsWith("/$id"))) {
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

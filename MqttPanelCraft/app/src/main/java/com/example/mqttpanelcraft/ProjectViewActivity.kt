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
import com.example.mqttpanelcraft.ui.LockableBottomSheetBehavior
import com.example.mqttpanelcraft.ui.InterceptableFrameLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
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

    override fun onPause() {
        super.onPause()
        viewModel.saveProject() // Force save on pause
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

            // Force Keep Screen On (User Request #4)
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Initialize ViewModel
        viewModel = ViewModelProvider(this)[ProjectViewModel::class.java]

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
        
        // --- Explicit Drop Zone Logic (Override any previous managers) ---
        // --- Explicit Drop Zone Logic (Override any previous managers) ---
        dropDeleteZone.setOnDragListener { v, event ->
             when (event.action) {
                 DragEvent.ACTION_DRAG_STARTED -> {
                      v.visibility = View.VISIBLE
                      v.animate().alpha(1.0f).setDuration(200).start()
                      true
                 }
                 DragEvent.ACTION_DRAG_ENTERED -> {
                      // v.setBackgroundColor(Color.RED) // Don't wipe background drawable
                      // Use scale or alpha for feedback
                      v.animate().scaleX(1.2f).scaleY(1.2f).setDuration(100).start()
                      true
                 }
                 DragEvent.ACTION_DRAG_EXITED -> {
                      v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                      true
                 }
                 DragEvent.ACTION_DROP -> {
                     v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                     val draggedView = event.localState as? View
                     if (draggedView != null) {
                         try {
                             // Existing Component -> Delete
                             viewModel.removeComponent(draggedView.id)
                             // Remove from UI
                             editorCanvas.removeView(draggedView)
                             val label = editorCanvas.findViewWithTag<View>("LABEL_FOR_${draggedView.id}")
                             if(label!=null) editorCanvas.removeView(label)
                             
                             // Clear properties panel if open for this component
                             propertiesManager.hide()
                             
                             viewModel.saveProject()
                             Toast.makeText(this, "Component Deleted", Toast.LENGTH_SHORT).show()
                         } catch (e: Exception) {
                            e.printStackTrace()
                         }
                     }
                     v.visibility = View.GONE
                     true
                 }
                 DragEvent.ACTION_DRAG_ENDED -> {
                     v.visibility = View.GONE
                     true
                 }
                 else -> true
             }
        }

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
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>

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
                // Update ViewModel Data
                val currentList = viewModel.components.value?.toMutableList()
                val comp = currentList?.find { it.id == view.id }
                if (comp != null) {
                    comp.x = view.x
                    comp.y = view.y
                    viewModel.updateComponent(comp) // This calls saveProject
                } else {
                    viewModel.saveProject()
                }
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
                // Name Generation Logic
                val currentComps = viewModel.components.value ?: emptyList()
                // Find all names starting with baseName
                val baseName = when(tag) {
                    "THERMOMETER" -> "Level Indicator"
                    "TEXT" -> "Label"
                    else -> tag.lowercase().replaceFirstChar { it.uppercase() }
                }
                
                // Regex to find max number. E.g. "Slider 5" -> 5.
                // Filter comps with name starting with baseName
                // Logic: Find first available gap. 1, 2, 4 -> 3.
                var nextNum = 1
                val existingNumbers = currentComps.mapNotNull { 
                    val name = it.label
                    if (name.startsWith(baseName)) {
                        val numberPart = name.removePrefix(baseName).trim()
                        numberPart.toIntOrNull()
                    } else null
                }.toSet()
                
                while (existingNumbers.contains(nextNum)) {
                    nextNum++
                }
                
                val newName = "$baseName $nextNum"

                val label = TextView(this).apply {
                    text = newName
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
                    label = newName,
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
            propertyContainer = null, // Decoupled: Properties are now in BottomSheet, not controlled by Sidebar logic
            componentContainer = findViewById<View>(R.id.sidebarEditMode),
            runModeContainer = findViewById<View>(R.id.sidebarRunMode),
            onComponentDragStart = { _, _ -> }
        )

        // PropertiesManager
        propertiesManager = PropertiesSheetManager(
            propertyContainer = containerProperties ?: window.decorView, // Pass container directly
            onPropertyUpdated = { id, name, w, h, color, topicConfig -> 
                 val view = editorCanvas.findViewById<View>(id)
                 if (view != null) {
                     val params = view.layoutParams
                     params.width = (w * resources.displayMetrics.density).toInt()
                     params.height = (h * resources.displayMetrics.density).toInt()
                     view.layoutParams = params
                     
                     val label = editorCanvas.findViewWithTag<TextView>("LABEL_FOR_$id")
                     label?.text = name
                     
                     // Update component data
                     val currentList = viewModel.components.value?.toMutableList()
                     val component = currentList?.find { it.id == id }
                     if (component != null) {
                         component.label = name
                         component.width = params.width
                         component.height = params.height
                         component.topicConfig = topicConfig
                         if (color.isNotEmpty()) {
                             component.props["color"] = color
                         }
                         viewModel.updateComponent(component)
                     }
                     
                     try {
                         if (color.isNotEmpty()) view.setBackgroundColor(Color.parseColor(color))
                     } catch(e: Exception){}
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
                 this.tag = "LABEL_FOR_${comp.id}"
                 this.x = comp.x
                 this.y = comp.y + comp.height + 4
             }
             editorCanvas.addView(label)
             
             attachComponentLogic(view, comp.type)
        }
        
        // Ensure interaction state is correct after restore
        setComponentsInteractive(!isEditMode)
    }

    // Removed createComponentView (moved to Factory) and setupLogs (moved to Manager)
    
    private fun updateModeUI() {
        if (isEditMode) {
             fabMode.setImageResource(android.R.drawable.ic_media_play)
             guideOverlay.visibility = View.VISIBLE
             
             // Edit Mode: 
             // 1. Sidebar shows Components (User wants to add components)
             // 2. Hide Logs, Show Properties Container
             // 3. Lock bottom sheet - only opens when clicking component
             sidebarManager.showComponentsPanel()
             
             findViewById<View>(R.id.containerLogs)?.visibility = View.GONE
             findViewById<View>(R.id.containerProperties)?.visibility = View.VISIBLE
             
             // Lock bottom sheet in edit mode
             // Edit Mode: HIDDEN and LOCKED -> No, User wants VISIBLE (Collapsed) and LOCKED.
             (bottomSheetBehavior as? LockableBottomSheetBehavior)?.isLocked = true
             bottomSheetBehavior.isHideable = false // Make it stick to collapsed if user wants it visible
             bottomSheetBehavior.peekHeight = (60 * resources.displayMetrics.density).toInt()
             bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
             
             idleAdController.stop()
        } else {
             fabMode.setImageResource(android.R.drawable.ic_menu_edit)
             // Run Mode: 
             sidebarManager.showRunModePanel() 

             guideOverlay.visibility = View.VISIBLE 
             
             findViewById<View>(R.id.containerLogs)?.visibility = View.VISIBLE
             findViewById<View>(R.id.containerProperties)?.visibility = View.GONE
             
             // In Run Mode, unlock so user can drag logs up
             (bottomSheetBehavior as? LockableBottomSheetBehavior)?.isLocked = false
             bottomSheetBehavior.isHideable = false // Don't let user hide it in Run Mode? Or True?
             // Usually false keeps it at least collapsed.
             
             bottomSheetBehavior.peekHeight = (60 * resources.displayMetrics.density).toInt()
             bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
             
             propertiesManager.hide() 
             
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
        toolbar.setNavigationOnClickListener { 
            if (isEditMode) sidebarManager.showComponentsPanel() 
            else sidebarManager.showRunModePanel()
            sidebarManager.openDrawer()
        }
        
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
             it.alpha = if(isVisible) 1.0f else 0.5f 
        }
        // Initialize state
        findViewById<View>(R.id.btnGrid).alpha = if(guideOverlay.isGridVisible()) 1.0f else 0.5f

        // Setup Bottom Sheet Behavior
        val bottomSheet = findViewById<View>(R.id.bottomSheet)
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
        (bottomSheetBehavior as? LockableBottomSheetBehavior)?.apply {
            headerViewId = R.id.bottomSheetHeader
            isLocked = true // Always locked to header-only drag
            isHideable = true // Ensure it can be hidden
            state = BottomSheetBehavior.STATE_HIDDEN // Start hidden
        }
        
        // Header Click to Toggle
        findViewById<View>(R.id.bottomSheetHeader).setOnClickListener {
            if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_COLLAPSED) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            } else {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            }
        }
        
        // Setup scrim to close bottom sheet on click
        val scrim = findViewById<View>(R.id.bottomSheetScrim)
        scrim.setOnClickListener {
            if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            }
        }
        
        // Show/hide scrim based on bottom sheet state
        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_EXPANDED -> {
                        scrim.visibility = View.VISIBLE
                    }
                    BottomSheetBehavior.STATE_COLLAPSED,
                    BottomSheetBehavior.STATE_HIDDEN -> {
                        scrim.visibility = View.GONE
                    }
                }
            }
            
            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                // Fade scrim based on slide offset
                scrim.alpha = slideOffset.coerceIn(0f, 1f)
            }
        })

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
                supportActionBar?.title = project.name
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

        // Orientation Control
        val switchPortrait = findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchLockPortrait)
        val switchLandscape = findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchLockLandscape)

        if (switchPortrait != null && switchLandscape != null) {
            val isPortraitLocked = prefs.getBoolean("lock_portrait", false)
            val isLandscapeLocked = prefs.getBoolean("lock_landscape", false)

            switchPortrait.isChecked = isPortraitLocked
            switchLandscape.isChecked = isLandscapeLocked

            // Apply initial state
            if (isPortraitLocked) requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else if (isLandscapeLocked) requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

            switchPortrait.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    // Turn off Landscape if Portrait is on
                    switchLandscape.isChecked = false
                    requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                } else {
                    if (!switchLandscape.isChecked) requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
                prefs.edit().putBoolean("lock_portrait", isChecked).apply()
            }

            switchLandscape.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    // Turn off Portrait if Landscape is on
                    switchPortrait.isChecked = false
                    requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                } else {
                    if (!switchPortrait.isChecked) requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
                prefs.edit().putBoolean("lock_landscape", isChecked).apply()
            }
        }
    }


    private fun toggleMode() {
        try {
            isEditMode = !isEditMode
            if (!isEditMode) viewModel.saveProject() // Save on exit edit mode
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
        val isEdit = !enable
        for (i in 0 until editorCanvas.childCount) {
             val child = editorCanvas.getChildAt(i)
             if (child is com.example.mqttpanelcraft.ui.InterceptableFrameLayout) {
                 child.isEditMode = isEdit
                 
                 // Clear Btn visibility (Edit Mode only)
                 child.findViewWithTag<View>("CLEAR_BTN")?.visibility = if (isEdit) View.VISIBLE else View.GONE
             }
        }
    }


    
    private fun makeDraggable(view: View) {
        view.setOnClickListener {
            if (!isEditMode) return@setOnClickListener
            
            // Ensure we get the FRESH data from ViewModel
            val currentList = viewModel.components.value
            val comp = currentList?.find { it.id == view.id }
            
            // Allow Label Sync from View if ViewModel has delay?
            var labelText = comp?.label ?: ""
            val labelView = editorCanvas.findViewWithTag<TextView>("LABEL_FOR_${view.id}")
            if (labelView != null) {
                labelText = labelView.text.toString()
            }
            
            android.util.Log.d("ProjectView", "Clicked View ID: ${view.id}, Found Comp: ${comp?.label} (${comp?.id})")
            
            if (comp != null) {
                propertiesManager.showProperties(view, labelText, comp.topicConfig)
            }
        }
        
        view.setOnLongClickListener { v ->
             if (!isEditMode) return@setOnLongClickListener false
             
             // VISIBILITY FIX: Explicitly show Drop Zone when drag starts
             dropDeleteZone.visibility = View.VISIBLE
             
             val dragData = ClipData("MOVE", arrayOf(android.content.ClipDescription.MIMETYPE_TEXT_PLAIN), ClipData.Item("MOVE"))
             val shadow = View.DragShadowBuilder(v)
             v.startDragAndDrop(dragData, shadow, v, 0)
             v.visibility = View.INVISIBLE
             true
        }
        
        view.setOnDragListener { v, event ->
             if (event.action == DragEvent.ACTION_DRAG_ENDED) {
                 v.visibility = View.VISIBLE
             }
             false 
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

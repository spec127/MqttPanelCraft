package com.example.mqttpanelcraft

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProvider
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.model.ProjectType
import com.example.mqttpanelcraft.ui.*
import com.google.android.material.floatingactionbutton.FloatingActionButton
import androidx.appcompat.app.AlertDialog
import java.util.Locale

class ProjectViewActivity : AppCompatActivity() {

    // ViewModel
    private lateinit var viewModel: ProjectViewModel

    // UI
    private lateinit var editorCanvas: FrameLayout
    private lateinit var guideOverlay: AlignmentOverlayView
    private lateinit var dropDeleteZone: View
    private lateinit var fabMode: FloatingActionButton
    private lateinit var drawerLayout: DrawerLayout

    // Managers (The New Trio)
    private lateinit var renderer: ComponentRenderer
    private lateinit var interactionManager: CanvasInteractionManager
    private lateinit var behaviorManager: ComponentBehaviorManager
    
    // Others
    private lateinit var sidebarManager: SidebarManager
    private lateinit var propertiesManager: PropertiesSheetManager
    private lateinit var logConsoleManager: LogConsoleManager
    private lateinit var idleAdController: com.example.mqttpanelcraft.ui.IdleAdController
    
    // Others

    private var selectedComponentId: Int? = null
    private var isEditMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            // Globals Init
            com.example.mqttpanelcraft.data.ProjectRepository.initialize(applicationContext)
            
            setContentView(R.layout.activity_project_view)
            viewModel = ViewModelProvider(this)[ProjectViewModel::class.java]

            // Bind UI
            editorCanvas = findViewById(R.id.editorCanvas)
            guideOverlay = findViewById(R.id.guideOverlay)
            dropDeleteZone = findViewById(R.id.dropDeleteZone)
            fabMode = findViewById(R.id.fabMode)
            drawerLayout = findViewById(R.id.drawerLayout)
            dropDeleteZone.visibility = View.GONE
            guideOverlay.isClickable = false

            // --- Initialize Managers ---
            initializeHelpers()
            initializeArchitecture()

            // Subscribers
            subscribeToViewModel()
            subscribeToMqtt()

            // Load
            val projectId = intent.getStringExtra("PROJECT_ID")
            if (projectId != null) {
                viewModel.loadProject(projectId)
            } else {
                finish()
            }

            setupToolbar()

            
            // Restore Preferences
            // Restore Preferences
            val prefs = getSharedPreferences("ProjectPrefs", MODE_PRIVATE)
            val gridVisible = prefs.getBoolean("GRID_VISIBLE", true)
            val guidesVisible = prefs.getBoolean("GUIDES_VISIBLE", true)
            com.example.mqttpanelcraft.utils.DebugLogger.log("ProjectActivity", "Restoring Prefs: Grid=$gridVisible, Guides=$guidesVisible")
            viewModel.setGridVisibility(gridVisible)
            viewModel.setGuidesVisibility(guidesVisible)
            
            // Status Bar Color
            val isNightMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            val wic = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
            wic.isAppearanceLightStatusBars = !isNightMode
            if (!isNightMode) {
                 window.statusBarColor = android.graphics.Color.WHITE 
            } else {
                 window.statusBarColor = android.graphics.Color.BLACK
            }
            
            updateModeUI()
            
            // vKeepScreenOn: Prevent auto-lock while in this activity
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Init Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun initializeArchitecture() {
        // 1. Renderer (Visuals)
        renderer = ComponentRenderer(editorCanvas, this)

        // 2. Behavior (Logic)
        behaviorManager = ComponentBehaviorManager(this, { viewModel.project.value?.id }) { topic, payload ->
            // Send MQTT
            val intent = Intent(this, com.example.mqttpanelcraft.service.MqttService::class.java).apply {
                action = "PUBLISH"
                putExtra("TOPIC", topic)
                putExtra("PAYLOAD", payload)
            }
            startService(intent)
        }

        // 3. Interaction (Input)
        val peekHeightPx = (50 * resources.displayMetrics.density).toInt()
        interactionManager = CanvasInteractionManager(editorCanvas, guideOverlay, peekHeightPx, object : CanvasInteractionManager.InteractionCallbacks {
            override fun onComponentClicked(id: Int) {
                if (isEditMode) {
                    val sheet = findViewById<View>(R.id.bottomSheet)
                    val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(sheet)
                    
                    if (id == -1) {
                        // Background Click -> Deselect & Collapse (Don't Hide)
                        selectedComponentId = null
                        behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED
                        // Clear visible properties or set "No Selection" state
                        propertiesManager.clear() // DOES NOT EXPAND
                    } else {
                        // Component Click -> Select & Expand
                        selectedComponentId = id
                        val comp = viewModel.components.value?.find { it.id == id }
                        if (comp != null) {
                            propertiesManager.showProperties(renderer.getView(id) ?: View(this@ProjectViewActivity), comp)
                            behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
                        }
                    }
                    // Trigger Re-render to update Selection Border
                    viewModel.components.value?.let { renderer.render(it, isEditMode, selectedComponentId) }
                    
                    updateCanvasOcclusion()
                } else {
                    // RUN MODE
                    if (id == -1) {
                         // Background Click -> Collapse Log Console (if expanded)
                         val sheet = findViewById<View>(R.id.bottomSheet)
                         val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(sheet)
                         if (behavior.state == com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED) {
                             behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED
                         }
                    }
                } 
            }

            override fun onComponentMoved(id: Int, newX: Float, newY: Float) {
                val comp = viewModel.components.value?.find { it.id == id }
                if (comp != null) {
                    viewModel.saveSnapshot()
                    val updated = comp.copy(x = newX, y = newY)
                    viewModel.updateComponent(updated)
                }
            }

            override fun onComponentResized(id: Int, newW: Int, newH: Int) {
                 val comp = viewModel.components.value?.find { it.id == id }
                 if (comp != null) {
                     viewModel.saveSnapshot()
                     val updated = comp.copy(width = newW, height = newH)
                     viewModel.updateComponent(updated)
                 }
            }
            
            override fun onComponentResizing(id: Int, newW: Int, newH: Int) {
                propertiesManager.updateDimensions(newW, newH)
            }

            override fun onComponentDeleted(id: Int) {
                viewModel.saveSnapshot()
                viewModel.removeComponent(id)
                if (selectedComponentId == id) {
                    selectedComponentId = null
                    propertiesManager.hide()
                }
            }
            
            override fun onDeleteZoneHover(isHovered: Boolean) {
                val header = findViewById<View>(R.id.bottomSheetHeader) ?: return
                val handle = findViewById<View>(R.id.ivHeaderHandle)
                val trash = findViewById<View>(R.id.ivHeaderTrash)
                val text = findViewById<View>(R.id.tvHeaderDelete) // Keep ref but don't show
                
                if (isHovered) {
                     header.setBackgroundResource(R.drawable.bg_delete_header)
                     handle?.visibility = View.GONE
                     trash?.visibility = View.VISIBLE
                     text?.visibility = View.GONE
                } else {
                     header.background = null 
                     handle?.visibility = View.VISIBLE
                     trash?.visibility = View.GONE
                     text?.visibility = View.GONE
                }
            }

            override fun onNewComponent(type: String, x: Float, y: Float) {
                viewModel.saveSnapshot()
                
                // We construct data here to respect drop coordinates (ViewModel's helper uses 100,100)
                // We can use generateSmartId logic from VM if needed, but VM.addComponent(data) handles ID collision.
                // But we want smart label here?
                // ProjectViewModel.generateSmartId was a static helper.
                // Actually, let's let VM handle everything if possible, OR just create a temporary ID and let VM fix it.
                // VM.addComponent(data) re-assigns ID if exists.
                
                // Smart Label? 
                // We need to access VM's logic.
                // Since `generateSmartTopic` is public in VM, we can use it.
                // But `getNextSmartLabel` is private.
                // Wait, I updated `generateSmartTopic` to use `getNextSmartLabel`.
                // So if we just generate topic, we get the label used in topic? No, topic has underscores.
                // We want the display label "Button 1".
                // I should make `getNextSmartLabel` public or use a VM method.
                // Or just defer label generation to VM?
                // VM.addComponent(type, topic) generates label. But forces x=100.
                
                // Let's add a `addComponent(x, y, type)` to VM? 
                // Or: modify `onNewComponent` to use what we have.
                // `viewModel.generateSmartTopic` is available.
                // I will assume `ComponentData` label can be temporarily generic and updated? No.
                // I will add a `getSmartLabel(type)` public method in VM?
                // Or just copy the logic locally?
                // NO, duplication is bad.
                
                // I'll call `val newId = viewModel.addComponent(type, x, y)` -> I need to add this overload to VM.
                // For now, I will use `viewModel.addComponent(type, ...)` but it puts it at 100,100.
                // User dropped it at specific X,Y.
                
                // Let's just update `ProjectViewModel` to take X, Y in `addComponent`.
                // BUT I am editing Activity now.
                // I will use `viewModel.components.value` to find new label manually for now, or just let it be basic.
                // User said "Naming logic...".
                
                // Let's rely on `viewModel.addComponent(data)` logic.
                // I will just use `viewModel.generateSmartTopic(type)` to get topic.
                // What about Label? "Button 1"?
                // `viewModel` has private `getNextSmartLabel`.
                // I will make `getNextSmartLabel` public (implied I could have done that).
                // Since I can't change VM in THIS step, I'll do it next step if needed.
                // But wait, I JUST edited VM. I made `getNextSmartLabel` private.
                
                // OK, I will update Activity to just call `val id = viewModel.addComponent(newData)` and select it.
                // I will assume `newData` creation uses best effort label, OR I can manually call `generateSmartTopic`?
                // `viewModel.generateSmartTopic(type)` is available.
                // It returns "Project/ID/Button_1/set".
                // I can extract "Button_1" -> "Button 1".
                
                val smartTopic = viewModel.generateSmartTopic(type)
                // Extract label from topic for consistency? 
                // Topic: .../Type_N/...
                // Extract label from topic for consistency
                // Topic: Project/ID/ItemName
                val path = smartTopic.split("/")
                val labelItem = if (path.isNotEmpty()) path.last() else type.lowercase()
                val label = labelItem // Already formatted by VM

                val (w, h) = com.example.mqttpanelcraft.ui.ComponentFactory.getDefaultSize(this@ProjectViewActivity, type)
                
                // Clamping Logic
                val editorW = editorCanvas.width
                val editorH = editorCanvas.height
                val maxX = (editorW - w).toFloat().coerceAtLeast(0f)
                val maxY = (editorH - h).toFloat().coerceAtLeast(0f)
                var finalX = x.coerceIn(0f, maxX)
                var finalY = y.coerceIn(0f, maxY)
                
                val density = resources.displayMetrics.density
                val gridPx = 10 * density // 10dp Snap
                finalX = (kotlin.math.round(finalX / gridPx) * gridPx)
                finalY = (kotlin.math.round(finalY / gridPx) * gridPx)
                finalX = finalX.coerceIn(0f, maxX)
                finalY = finalY.coerceIn(0f, maxY)

                // ID will be assigned by VM
                val newData = ComponentData(
                    id = 0, // VM will fix
                    type = type,
                    x = finalX,
                    y = finalY,
                    width = w,
                    height = h,
                    label = label,
                    topicConfig = smartTopic
                )
                val newComp = viewModel.addComponent(newData)
                
                if (newComp != null) {
                    val newId = newComp.id
                    viewModel.selectComponent(newId)
                    
                    // Show Properties Immediately
                    selectedComponentId = newId
                    
                    // Force Render (Sync)
                    viewModel.project.value?.components?.let { renderer.render(it, isEditMode, newId) }
                    
                    val view = renderer.getView(newId)
                    if (view != null) {
                        propertiesManager.showProperties(view, newComp)
                    }
                }
            }
        })
        
        
        interactionManager.setup(
            isEditMode = { isEditMode },
            isGridEnabled = { viewModel.isGridVisible.value ?: true },
            isBottomSheetExpanded = {
                val sheet = findViewById<View>(R.id.bottomSheet)
                val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(sheet)
                behavior.state == com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            }
        )
    }

    private fun subscribeToViewModel() {
        viewModel.logs.observe(this) { logs ->
             logConsoleManager.updateLogs(logs)
        }

        viewModel.components.observe(this) { components ->
            // Update Console Topics
            logConsoleManager.updateTopics(components, viewModel.project.value)
            
            // RENDER: One-way data flow with Selection State
            renderer.render(components, isEditMode, selectedComponentId)
            
            // Re-attach behaviors (Simple approach)
            components.forEach { comp ->
                val view = renderer.getView(comp.id)
                if (view != null) {
                    behaviorManager.attachBehavior(view, comp)
                }
            }
        }
        
        viewModel.project.observe(this) { project ->
            if (project != null) {
                supportActionBar?.title = project.name
                viewModel.initMqtt()
            }
        }
        
        viewModel.mqttStatus.observe(this) { status ->
             val light = findViewById<View>(R.id.indicatorMqttStatus) ?: return@observe
             val bg = light.background as? android.graphics.drawable.GradientDrawable
             bg?.mutate()
             val color = when(status) {
                 ProjectViewModel.MqttStatus.CONNECTED -> android.graphics.Color.GREEN
                 ProjectViewModel.MqttStatus.FAILED -> {
                     if (light.tag != "FAILED_SHOWN") {
                          Toast.makeText(this, "無法連線，請確認Broker設定後點擊燈號重試", Toast.LENGTH_LONG).show()
                          light.tag = "FAILED_SHOWN" // Simple debit
                     }
                     android.graphics.Color.RED
                 }
                 ProjectViewModel.MqttStatus.CONNECTING -> {
                     light.tag = null
                     android.graphics.Color.GRAY
                 }
                 else -> android.graphics.Color.GRAY
             }
             bg?.setColor(color)
             
             // Log Status (User Request)
             viewModel.addLog("MQTT Status: $status")
             
             light.setOnClickListener {
                 if (status == ProjectViewModel.MqttStatus.FAILED) {
                     Toast.makeText(this, "重試連線中...", Toast.LENGTH_SHORT).show()
                     viewModel.retryMqtt()
                 } else {
                     Toast.makeText(this, "Status: ${status}", Toast.LENGTH_SHORT).show()
                 }
             }
        }

        viewModel.isGridVisible.observe(this) { visible ->
            val grid = findViewById<View>(R.id.backgroundGrid)
            val btn = findViewById<android.widget.ImageView>(R.id.btnGrid)
            grid.visibility = if (visible) View.VISIBLE else View.GONE
            // Visual Consistency: High contrast for toggle state
            btn.alpha = if (visible) 1.0f else 0.3f
            btn.setColorFilter(android.graphics.Color.WHITE)
        }
        
        viewModel.isGuidesVisible.observe(this) { visible ->
             guideOverlay.visibility = if (visible) View.VISIBLE else View.GONE
        }
        
        viewModel.canUndo.observe(this) { can ->
            val btnUndo = findViewById<android.widget.ImageView>(R.id.btnUndo)
            if (btnUndo != null) {
                btnUndo.alpha = if (can) 1.0f else 0.3f
                btnUndo.isEnabled = can
                btnUndo.setColorFilter(android.graphics.Color.WHITE)
            }
        }
    }

    private fun setupToolbar() {
         val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
         setSupportActionBar(toolbar)
         supportActionBar?.setDisplayHomeAsUpEnabled(true)
         supportActionBar?.setHomeAsUpIndicator(android.R.drawable.ic_menu_sort_by_size) // Hamburger Icon
         toolbar.setNavigationOnClickListener { sidebarManager.openDrawer() }
         
         // Fix Grid Button Logic and Logs
         val btnGrid = findViewById<View>(R.id.btnGrid)
         btnGrid.setOnClickListener {
             viewModel.toggleGrid()
         }
         
         // Settings Button
         val btnSettings = findViewById<View>(R.id.btnSettings)
         btnSettings.setOnClickListener {
             // Navigate to Setup/Settings
             val intent = Intent(this, com.example.mqttpanelcraft.SetupActivity::class.java)
             intent.putExtra("PROJECT_ID", viewModel.project.value?.id) // Pass Project ID if needed
             startActivity(intent)
         }
         
         // Long Press Settings for Logs
         btnSettings.setOnLongClickListener {
             AlertDialog.Builder(this)
                 .setTitle("Debug Logs")
                 .setMessage(logConsoleManager.getLogs())
                 .setPositiveButton("Copy") { _, _ ->
                     val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                     val clip = android.content.ClipData.newPlainText("MqttLogs", logConsoleManager.getLogs())
                     clipboard.setPrimaryClip(clip)
                     Toast.makeText(this, "Logs Copied", Toast.LENGTH_SHORT).show()
                 }
                 .setNegativeButton("Close", null)
                 .show()
             true
         }

         // Undo Button
         findViewById<View>(R.id.btnUndo).setOnClickListener {
             viewModel.undo()
         }


         fabMode.setOnClickListener {
             isEditMode = !isEditMode
             
             // Idle Ad: Runs in both modes, do not stop here.
             
             selectedComponentId = null // Clear selection on mode switch
             updateModeUI()
             // Trigger re-render
             viewModel.components.value?.let { renderer.render(it, isEditMode, selectedComponentId) }
             if (!isEditMode) viewModel.saveProject()
         }
         
         // ...
         // Bottom Sheet Callback for Parallax/Push
         val bottomSheet = findViewById<View>(R.id.bottomSheet)
         val sheetBehavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheet)
          sheetBehavior.addBottomSheetCallback(object : com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback() {
              override fun onStateChanged(bottomSheet: View, newState: Int) {
                  updateBottomInset(bottomSheet)
              }
              override fun onSlide(bottomSheet: View, slideOffset: Float) {
                  updateCanvasOcclusion(bottomSheet)
                  updateBottomInset(bottomSheet)
              }
          })
    }

    private fun updateModeUI() {
        // Toggle Logs visibility
        val bottomSheet = findViewById<View>(R.id.bottomSheet)
        val logsContainer = findViewById<View>(R.id.containerLogs) 
        val sheetBehavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheet)
        
        // Lock/Unlock Behavior based on Mode
        if (sheetBehavior is LockableBottomSheetBehavior) {
            sheetBehavior.isLocked = isEditMode
        }

        if (isEditMode) {
            fabMode.setImageResource(android.R.drawable.ic_media_play)
            guideOverlay.visibility = View.VISIBLE
            sidebarManager.showComponentsPanel()
            findViewById<View>(R.id.btnUndo).visibility = View.VISIBLE
            
            // Edit Mode: Logs OFF, Properties Visible (Collapsed)
            logsContainer.visibility = View.GONE
            findViewById<View>(R.id.containerProperties).visibility = View.VISIBLE
            
            // Allow user to drag it to Close
            sheetBehavior.isHideable = true
            // If nothing selected, maybe HIDDEN? 
            // If we just entered mode, effectively hidden until user clicks.
            // But if we toggle, we might want to keep state.
            // state = STATE_HIDDEN ?? 
            // Let's keep existing logic but allow Hide.
             if (sheetBehavior.state == com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN) {
                 sheetBehavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED
             }
            
        } else {
            fabMode.setImageResource(android.R.drawable.ic_menu_edit)
            guideOverlay.visibility = View.GONE // Guides off in run mode
            sidebarManager.showRunModePanel()
            findViewById<View>(R.id.btnUndo).visibility = View.GONE
            
            // Run Mode: Logs ON (Peek), Properties OFF
            logsContainer.visibility = View.VISIBLE
            propertiesManager.hide()
            
            // Force Peek to show Logs Header/Initial Rows
            sheetBehavior.isHideable = false // Logs usually stick around? Or allow hide?
            sheetBehavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED
        }
    }

    // --- Helpers (Sidebar, Props, Logs) ---
    private fun initializeHelpers() {
        logConsoleManager = LogConsoleManager(window.decorView)
        
        propertiesManager = PropertiesSheetManager(
            findViewById(R.id.containerProperties),
            { // On Expand
                val sheet = findViewById<View>(R.id.bottomSheet)
                com.google.android.material.bottomsheet.BottomSheetBehavior.from(sheet).state = 
                    com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            },
{ updatedData -> // On Update
                viewModel.updateComponent(updatedData)
            },
            { id -> // On Clone
                viewModel.components.value?.find { it.id == id }?.let { source ->
                    // 1. Naming Logic
                    val name = source.label
                    val regex = "(.*)_copy(\\d*)$".toRegex()
                    val match = regex.find(name)
                    val newLabel = if (match != null) {
                        val base = match.groupValues[1]
                        val numStr = match.groupValues[2]
                        val num = if (numStr.isEmpty()) 2 else numStr.toInt() + 1
                        "${base}_copy$num"
                    } else {
                        "${name}_copy"
                    }
                
                    // 2. Add Component
                    val newComp = viewModel.addComponent(source.copy(
                        x = source.x + 40f, 
                        y = source.y + 40f,
                        label = newLabel
                    )) 
                    
                    if (newComp != null) {
                         val newId = newComp.id

                        // 3. Selection & Props
                        viewModel.selectComponent(newId)
                        selectedComponentId = newId
                        
                        // Force prop update: Use the RETURNED object directly!
                        val view = renderer.getView(newId)
                        if (view != null) {
                             propertiesManager.showProperties(view, newComp)
                        } else {
                             // Fallback if renderer hasn't updated view cache yet (synchronization)
                             // Usually renderer updates via Observer.
                             // Trigger manual render pass if needed?
                             // Since we mutated the list in VM, the observer WILL fire.
                             // But if we are here before observer...
                             // We can't get the VIEW until renderer creates it.
                             // So we rely on Observer to eventually call render?
                             // NO. 'showProperties' needs the VIEW immediately for location/highlight.
                             // If Observer is async, 'getView' returns null.
                             
                             // Hack: Force Renderer Update with current (mutated) list from VM
                             // VM.components.value might be stale, BUT VM.project.value.components IS updated.
                             viewModel.project.value?.components?.let { 
                                 renderer.render(it, isEditMode, newId) 
                                 val freshView = renderer.getView(newId)
                                 if (freshView != null) propertiesManager.showProperties(freshView, newComp)
                             }
                        }
                    }
                }
            },
            { id -> // On Reset Topic
                 viewModel.components.value?.find { it.id == id }?.let { comp ->
                    val freshTopic = viewModel.generateSmartTopic(comp.type)
                    val updated = comp.copy(topicConfig = freshTopic)
                    viewModel.updateComponent(updated)
                    // Refresh UI
                    val view = renderer.getView(id)
                    if (view != null) {
                        propertiesManager.showProperties(view, updated)
                    }
                }
            }
        )
        
        logConsoleManager.setClearAction {
            viewModel.clearLogs()
        }

        sidebarManager = SidebarManager(
            drawerLayout,
            null,
            findViewById(R.id.sidebarEditMode),
            findViewById(R.id.sidebarRunMode),
            { _,_ -> }
        )
        sidebarManager.setupComponentPalette(drawerLayout)
        sidebarManager.setupRunModeSettings(findViewById(R.id.sidebarRunMode), this)
        
        sidebarManager.setupRunModeSettings(findViewById(R.id.sidebarRunMode), this)
        
        // Ensure AdManager is ready and pre-load Interstitial
        com.example.mqttpanelcraft.utils.AdManager.initialize(this)
        com.example.mqttpanelcraft.utils.AdManager.loadInterstitial(this)
        
        // Idle Ad Controller
        idleAdController = com.example.mqttpanelcraft.ui.IdleAdController(this) {
             // Ad Closed
             if (isEditMode) {
                 // Resume behavior if needed
             }
        }
    }

    private fun subscribeToMqtt() {
        // 1. Message Listener
        com.example.mqttpanelcraft.MqttRepository.registerListener(object : com.example.mqttpanelcraft.MqttRepository.MessageListener {
            override fun onMessageReceived(topic: String, payload: String) {
                 runOnUiThread {
                     // Log Logic: Filter System? (Only clean messages here)
                     // Match Component Label
                     val matched = viewModel.components.value?.find { 
                         // Exact match or wildcard match
                         it.topicConfig == topic || (it.topicConfig.endsWith("/#") && topic.startsWith(it.topicConfig.dropLast(2)))
                     }
                     
                     val logMsg = if (matched != null) {
                         "${matched.label}: $payload"
                     } else {
                         "$topic: $payload"
                     }
                     
                     // Send to VM instead of Manager
                     viewModel.addLog(logMsg)

                     // Update UI for all components that match the topic
                     viewModel.components.value?.forEach { comp ->
                         // Check for exact topic match or wildcard match
                         if (comp.topicConfig == topic || (comp.topicConfig.endsWith("/#") && topic.startsWith(comp.topicConfig.dropLast(2)))) {
                             val view = renderer.getView(comp.id)
                             if (view != null) behaviorManager.onMqttMessageReceived(view, comp, payload)
                         }
                     }
                 }
            }
        })
        
        // 2. Default Subscription on Connect
        var hasSubscribed = false
        // Observe Repository Connection Status directly or via VM
        com.example.mqttpanelcraft.MqttRepository.connectionStatus.observe(this) { status ->
             if (status == 1) { // Connected
                 if (!hasSubscribed) {
                     val proj = viewModel.project.value
                     if (proj != null) {
                         val defaultTopic = "${proj.name}/${proj.id}/#"
                         val context = applicationContext
                         val intent = android.content.Intent(context, com.example.mqttpanelcraft.service.MqttService::class.java).apply {
                             action = "SUBSCRIBE"
                             putExtra("TOPIC", defaultTopic)
                         }
                         context.startService(intent)
                         // Log Subscription
                         viewModel.addLog("Auto-Subscribing to: $defaultTopic")
                         hasSubscribed = true
                     }
                 }
             } else {
                 hasSubscribed = false
             }
        }
    }


    private fun updateCanvasOcclusion(bottomSheet: View? = null) {
        val sheet = bottomSheet ?: findViewById(R.id.bottomSheet) ?: return
        val canvas = findViewById<View>(R.id.editorCanvas) ?: return
        
        val selectedId = selectedComponentId
        if (selectedId == null) {
            canvas.translationY = 0f
            return
        }
        
        val compView = renderer.getView(selectedId)
        if (compView == null) {
             canvas.translationY = 0f
             return
        }

        val compLoc = IntArray(2)
        compView.getLocationOnScreen(compLoc) 
        val currentCompBottom = compLoc[1] + compView.height
        
        val sheetLoc = IntArray(2)
        sheet.getLocationOnScreen(sheetLoc)
        val sheetTop = sheetLoc[1]
        
        val margin = (50 * resources.displayMetrics.density)
        
        val currentTrans = canvas.translationY
        val rawBottom = currentCompBottom - currentTrans
        
        var targetTrans = (sheetTop - margin - rawBottom)
        if (targetTrans > 0f) targetTrans = 0f
        
        canvas.translationY = targetTrans
    }

    private fun updateBottomInset(bottomSheet: View) {
        // Calculate overlap between BottomSheet and Canvas to adjust Deletion Zone
        if (bottomSheet.visibility != View.VISIBLE) {
            interactionManager.updateBottomInset(0)
            return
        }
        val sheetLoc = IntArray(2)
        bottomSheet.getLocationOnScreen(sheetLoc)
        val sheetTop = sheetLoc[1]
        
        val canvasLoc = IntArray(2)
        editorCanvas.getLocationOnScreen(canvasLoc)
        val canvasBottom = canvasLoc[1] + editorCanvas.height
        
        // Inset is the amount of canvas covered by the sheet
        val overlap = (canvasBottom - sheetTop).coerceAtLeast(0)
        interactionManager.updateBottomInset(overlap)
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent?): Boolean {
        if (::idleAdController.isInitialized) {
            idleAdController.onUserInteraction()
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onResume() {
        super.onResume()
        if (::idleAdController.isInitialized) {
             idleAdController.start()
        }
        updateModeUI() // Ensure UI state is consistent
    }

    override fun onPause() {
        if (::idleAdController.isInitialized) {
            idleAdController.stop()
        }
        super.onPause()
        val prefs = getSharedPreferences("ProjectPrefs", MODE_PRIVATE)
        with(prefs.edit()) {
            putBoolean("GRID_VISIBLE", viewModel.isGridVisible.value ?: true)
            putBoolean("GUIDES_VISIBLE", viewModel.isGuidesVisible.value ?: true)
            apply()
        }
    }
}

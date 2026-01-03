package com.example.mqttpanelcraft

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProvider
import com.example.mqttpanelcraft.model.ComponentData
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

/**
 * ProjectViewActivity - 專案編輯與執行主畫面
 *
 * 職責 (Responsibilities):
 * 1. 管理編輯模式 (Edit Mode) 與執行模式 (Run Mode) 的切換。
 * 2. 協調各個管理器 (Managers) 運作：
 *    - SidebarManager: 側邊欄 (元件庫、設定)。
 *    - DragDropManager: 拖拉放邏輯。
 *    - CanvasManager: 畫布上的元件操作 (移動、對齊)。
 *    - PropertiesSheetManager: 屬性設定面板。
 *    - LogConsoleManager: 底部日誌顯示。
 * 3. 處理 MQTT 訊息接收與分發 (分發已優化為 Map 查找)。
 * 4. 處理特定元件的互動邏輯 (按鈕點擊、Slider數值變更)。
 *
 * 若未來元件大量增加，建議將 `attachComponentLogic` 與 `updateComponentFromMqtt` 
 * 拆分為獨立的 ComponentBehaviorManager。
 */
class ProjectViewActivity : AppCompatActivity() {

    // --- ViewModel ---
    // 負責持有專案資料 (Project Data) 與元件列表 (Components List)。
    // 透過 LiveData 觀察資料變更並更新 UI。
    private lateinit var viewModel: ProjectViewModel

    // --- UI Elements ---
    private lateinit var editorCanvas: FrameLayout       // 主要畫布容器
    private lateinit var guideOverlay: AlignmentOverlayView // 對齊線遮罩層
    private lateinit var dropDeleteZone: View            // 拖曳刪除區域
    private lateinit var fabMode: FloatingActionButton   // 模式切換按鈕 (Edit/Run)
    private lateinit var drawerLayout: DrawerLayout      //側邊欄容器
    
    // --- State Variables ---
    private var isEditMode = false // 當前模式狀態：false = Run Mode (執行), true = Edit Mode (編輯)
    private var selectedCameraComponentId: Int = -1 // 記錄當前正在選擇圖片的 Camera 元件 ID

    // --- Image Picker Callback ---
    // 用於處理從系統相簿選擇圖片後的結果
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
        // 離開頁面時 (包含按 Home 鍵、跳轉設定頁、關閉螢幕)，執行全量同步與儲存
        // 這符合使用者期望的「Option 1: 離開時儲存」，且效能負擔極低 (僅一次寫入)
        syncComponentsState()
        viewModel.saveProject() 
    }

    /**
     * 強制同步畫面上所有元件的最新位置回 ViewModel。
     * 確保 View 的實際座標 (x, y) 被正確寫入到資料層。
     */
    private fun syncComponentsState() {
        // 在任何同步時機 (離開、跳轉) 檢查變更
        val currentList = viewModel.components.value ?: return
        val batchUpdates = mutableListOf<ComponentData>()

        componentViewCache.forEach { (id, view) ->
             val comp = currentList.find { it.id == id }
             if (comp != null) {
                 // 檢查位移 (使用 1px 閾值)
                 if (kotlin.math.abs(comp.x - view.x) > 1f || kotlin.math.abs(comp.y - view.y) > 1f) {
                     // 建立副本以確保資料純淨度
                     val newComp = comp.copy(x = view.x, y = view.y)
                     batchUpdates.add(newComp)
                     android.util.Log.d("ProjectView", "Sync: Component $id moved to (${view.x}, ${view.y})")
                 }
             }
        }
        
        if (batchUpdates.isNotEmpty()) {
             viewModel.updateComponentsBatch(batchUpdates)
             val msg = "Saving ${batchUpdates.size} components. Ex: ${batchUpdates[0].label} -> (${batchUpdates[0].x.toInt()}, ${batchUpdates[0].y.toInt()})"
             android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
             android.util.Log.d("ProjectView", "Sync: Batch saved components.")
        } else {
             // Optional: Toast even if no changes, to prove check ran? 
             // User wants to know "When Saving". So only toast on save.
             // But if user moved and NO save happens, they need to know "Why".
             // android.widget.Toast.makeText(this, "No changes detected", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateStatusIndicator(isConnected: Boolean) {
        val dot = findViewById<View>(R.id.viewStatusDot)
        if (dot == null) return // 防呆
        if (isConnected) {
            dot.setBackgroundResource(R.drawable.shape_circle_green)
            dot.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.GREEN)
        } else {
            dot.setBackgroundResource(R.drawable.shape_circle_green) 
            dot.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.RED)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            // 1. 初始化全域依賴 (Repository, Ads, Theme)
            com.example.mqttpanelcraft.data.ProjectRepository.initialize(applicationContext)
            com.example.mqttpanelcraft.utils.AdManager.initialize(this)
            com.example.mqttpanelcraft.utils.ThemeManager.applyTheme(this) // 套用全域主題設定
            
            setContentView(R.layout.activity_project_view)
            
            // 2. 調整系統狀態列邊距 (Window Insets)，避免 UI 被狀態列遮擋
            val root = findViewById<View>(R.id.rootCoordinator)
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
                val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
                insets
            }
            
            // 設定狀態列文字顏色 (依據深色模式自動調整)
            val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            val isDarkTheme = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            windowInsetsController.isAppearanceLightStatusBars = !isDarkTheme 

            // 保持螢幕常亮 (User Request)
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            // 3. 初始化 ViewModel
            viewModel = ViewModelProvider(this)[ProjectViewModel::class.java]

            // 4. 綁定 UI 元件
            editorCanvas = findViewById<FrameLayout>(R.id.editorCanvas)
            guideOverlay = findViewById(R.id.guideOverlay)
            dropDeleteZone = findViewById(R.id.dropDeleteZone)
            fabMode = findViewById(R.id.fabMode)
            drawerLayout = findViewById(R.id.drawerLayout)
            val containerProperties = findViewById<View>(R.id.containerProperties)
            val bottomSheet = findViewById<View>(R.id.bottomSheet)

            // 5. 初始化各個 Managers (將邏輯授權給 Manager 處理)
            initializeManagers(bottomSheet, containerProperties)
            
            // 6. 設定 DragDropManager 的 Drop Zone (刪除功能)
            dragDropManager.setupDropZone(dropDeleteZone)

            // 7. 設定 LiveData 觀察者 (當資料變更時更新 UI)
            setupObservers()

            // 8. 載入傳入的 Project ID
            val projectId = intent.getStringExtra("PROJECT_ID")
            if (projectId != null) {
                viewModel.loadProject(projectId)
            } else {
                Toast.makeText(this, "Error: No Project ID", Toast.LENGTH_SHORT).show()
                finish()
            }

            // 9. 初始化其他 UI 設定 (Toolbar, Listeners)
            setupUI()
            
            // 10. 初始化模式 UI (預設為 Run Mode)
            updateModeUI()
            
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error initializing Project View: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    // --- Managers ---
    // 將複雜邏輯拆分至以下    // Managers
    private lateinit var logConsoleManager: com.example.mqttpanelcraft.ui.LogConsoleManager
    private lateinit var canvasManager: CanvasManager
    private lateinit var sidebarManager: SidebarManager
    private lateinit var dragDropManager: com.example.mqttpanelcraft.ui.DragDropManager
    private lateinit var propertiesManager: PropertiesSheetManager
    private lateinit var componentBehaviorManager: com.example.mqttpanelcraft.ui.ComponentBehaviorManager // New Manager
    private lateinit var idleAdController: IdleAdController
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>

    /**
     * 初始化所有管理器，並設定它們之間的回呼 (Callbacks)。
     * 這是一個關鍵函式，串接了 UI 事件與資料層。
     */
    private fun initializeManagers(bottomSheet: View, containerProperties: View?) {
        // Initialize ComponentBehaviorManager First
        componentBehaviorManager = com.example.mqttpanelcraft.ui.ComponentBehaviorManager(
            context = this,
            projectIdProvider = { viewModel.project.value?.id },
            projectNameProvider = { viewModel.project.value?.name },
            isEditModeProvider = { isEditMode },
            onImagePickerRequested = { id -> openGallery(id) }
        )

        // [MQTT Listener]: 當收到 MQTT 訊息時，更新 Log 並尋找對應元件更新 UI
        com.example.mqttpanelcraft.MqttRepository.registerListener(object : com.example.mqttpanelcraft.MqttRepository.MessageListener {
            override fun onMessageReceived(topic: String, payload: String) {
                 runOnUiThread {
                     logConsoleManager.addLog("Msg: $topic -> $payload")
                     // Delegate Update Logic
                     componentViewCache.forEach { (id, view) ->
                         val comp = viewModel.components.value?.find { it.id == id }
                         if (comp != null) {
                             componentBehaviorManager.updateViewFromMqtt(view, comp, topic, payload)
                         }
                     }
                 }
            }
        })
        
        // [Connection Observer]: 監聽連線狀態，更新右上角紅綠燈
        com.example.mqttpanelcraft.MqttRepository.connectionStatus.observe(this) { status ->
             val isConnected = (status == com.example.mqttpanelcraft.MqttStatus.CONNECTED)
             logConsoleManager.addLog(if(isConnected) "Connected to Broker" else "Disconnected/Connecting")
             updateStatusIndicator(isConnected)
        }
        
        // 1. LogConsoleManager
        logConsoleManager = com.example.mqttpanelcraft.ui.LogConsoleManager(window.decorView)
        
        // 2. CanvasManager: 處理畫布上的元件事件
        canvasManager = CanvasManager(
            canvasCanvas = editorCanvas,
            guideOverlay = guideOverlay,
            dropDeleteZone = dropDeleteZone,
            onComponentDropped = { view -> 
                 componentViewCache[view.id] = view // 更新快取
            },
            onComponentMoved = { view -> 
                // 當元件移動後，更新 ViewModel 中的座標資料
                val currentList = viewModel.components.value?.toMutableList()
                val comp = currentList?.find { it.id == view.id }
                if (comp != null) {
                    comp.x = view.x
                    comp.y = view.y
                    viewModel.updateComponent(comp) // 更新並儲存
                }
            },
            onComponentDeleted = { view ->
                 viewModel.saveProject()
            },
            onCreateNewComponent = { tag, x, y ->
                // [核心邏輯]: 當從側邊欄拖入新元件時觸發
                // 1. 使用 Factory 建立 View
                val view = com.example.mqttpanelcraft.ui.ComponentFactory.createComponentView(this, tag, isEditMode)
                view.id = View.generateViewId()
                
                // 2. 設定預設大小
                val (defW, defH) = com.example.mqttpanelcraft.ui.ComponentFactory.getDefaultSize(this, tag)
                val params = FrameLayout.LayoutParams(defW, defH)
                view.layoutParams = params
                
                // 3. 計算對齊位置 (Snap)
                val snapped = canvasManager.getSnappedPosition(
                    x, y, params.width, params.height, null
                )
                view.x = snapped.x.toFloat()
                view.y = snapped.y.toFloat()
                
                editorCanvas.addView(view)
                makeDraggable(view) // 賦予它可拖曳的能力

                // 4. 產生預設名稱 (Label)
                val currentComps = viewModel.components.value ?: emptyList()
                val baseName = when(tag) {
                    "THERMOMETER" -> "Level Indicator"
                    "TEXT" -> "Label"
                    else -> tag.lowercase().replaceFirstChar { it.uppercase() }
                }
                
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

                // 5. 建立標籤 View
                val label = TextView(this).apply {
                    text = newName
                    this.tag = "LABEL_FOR_${view.id}"
                    this.x = view.x
                    this.y = view.y + params.height + 4
                }
                editorCanvas.addView(label)
                
                componentViewCache[view.id] = view
                
                // 6. 建立 ComponentData 並存入 ViewModel
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
                // saveProject is handled inside ViewModel.addComponent
                
                // 7. 綁定按鈕點擊等互動邏輯
                componentBehaviorManager.attachBehavior(view, componentData)
            }
        )
        // 設定 Canvas 拖曳監聽器 (僅在 Edit Mode 有效)
        canvasManager.setupDragListener { isEditMode }

        // 3. SidebarManager
        sidebarManager = SidebarManager(
            drawerLayout = drawerLayout,
            propertyContainer = null, 
            componentContainer = findViewById<View>(R.id.sidebarEditMode),
            runModeContainer = findViewById<View>(R.id.sidebarRunMode),
            onComponentDragStart = { _, _ -> }
        )

        // 4. PropertiesManager: 處理屬性變更回呼
        propertiesManager = PropertiesSheetManager(
            propertyContainer = containerProperties ?: window.decorView,
            onPropertyUpdated = { id, name, w, h, color, topicConfig -> 
                 // 當使用者在屬性面板修改數值時，更新 View 與 Data
                 val view = editorCanvas.findViewById<View>(id)
                 if (view != null) {
                     val params = view.layoutParams
                     params.width = (w * resources.displayMetrics.density).toInt()
                     params.height = (h * resources.displayMetrics.density).toInt()
                     view.layoutParams = params
                     
                     val label = editorCanvas.findViewWithTag<TextView>("LABEL_FOR_$id")
                     label?.text = name
                     
                     // 更新 ViewModel 資料
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
                         
                         // Re-attach behavior in case topic specific logic needs refresh? 
                         // Usually not needed unless behavior depends on topic.
                     }
                     
                     try {
                         if (color.isNotEmpty()) view.setBackgroundColor(Color.parseColor(color))
                     } catch(e: Exception){}
                 }
            }
        )
        
        // 5. DragDropManager: 處理刪除元件的邏輯
        dragDropManager = com.example.mqttpanelcraft.ui.DragDropManager(
            isEditModeProvider = { isEditMode },
            onComponentDeleted = { id ->
                 viewModel.removeComponent(id) // 從資料移除
                 // 從 UI 移除 View 與 Label
                 val view = editorCanvas.findViewById<View>(id)
                 if (view != null) editorCanvas.removeView(view)
                 val label = editorCanvas.findViewWithTag<View>("LABEL_FOR_$id")
                 if (label != null) editorCanvas.removeView(label)
                 
                 propertiesManager.hide() // 關閉屬性面板
                 viewModel.saveProject()
                 Toast.makeText(this, "Component Deleted", Toast.LENGTH_SHORT).show()
            }
        )
        
        idleAdController = IdleAdController(this) {}
    }

    /**
     * 從資料庫載入元件列表並重建畫面。
     * 這通常在 `onCreate` 或 Activity 恢復時被 ViewModel 觸發。
     */
    private fun restoreComponents(components: List<ComponentData>) {
        editorCanvas.removeAllViews()
        componentViewCache.clear()
        
        components.forEach { comp ->
             val view = com.example.mqttpanelcraft.ui.ComponentFactory.createComponentView(this, comp.type, isEditMode)
             view.id = comp.id
             componentViewCache[comp.id] = view // 快取 View 以供 MQTT 更新使用
             
             val params = FrameLayout.LayoutParams(comp.width, comp.height)
             view.layoutParams = params
             view.x = comp.x
             view.y = comp.y
             
             editorCanvas.addView(view)
             makeDraggable(view) // 重新綁定拖曳功能

             // 重建標籤
             val label = TextView(this).apply {
                 text = comp.label
                 this.tag = "LABEL_FOR_${comp.id}"
                 this.x = comp.x
                 this.y = comp.y + comp.height + 4
             }
             editorCanvas.addView(label)
             
             // Delegate to Manager
             componentBehaviorManager.attachBehavior(view, comp)
             
             // Restore visual props (Color)
             comp.props["color"]?.let { c ->
                 try { view.setBackgroundColor(Color.parseColor(c)) } catch(e:Exception){}
             }
        }
        
        // 確保互動狀態正確 (依據是否為 Edit Mode)
        setComponentsInteractive(!isEditMode)
    }

    private var isFirstLoad = true

    private fun setupObservers() {
        // 觀察 Project 資料變更
        viewModel.project.observe(this) { project ->
            if (project != null) {
                supportActionBar?.title = project.name
                
                // 自動連線 MQTT Broker
                val intent = Intent(this, com.example.mqttpanelcraft.service.MqttService::class.java).apply {
                    action = "CONNECT"
                    putExtra("BROKER", project.broker)
                    putExtra("PORT", project.port)
                    putExtra("USER", project.username)
                    putExtra("PASSWORD", project.password)
                    putExtra("CLIENT_ID", project.clientId)
                }
                startService(intent)
                
                android.util.Log.d("ProjectView", "Observer: Project loaded. Components: ${project.components.size}, FirstLoad: $isFirstLoad, ChildCount: ${editorCanvas.childCount}")
                
                // 改進的還原邏輯：使用 isFirstLoad 旗標確保首次載入一定會執行
                // 且只要專案有元件，就嘗試還原
                if (isFirstLoad && project.components.isNotEmpty()) {
                    restoreComponents(project.components)
                    isFirstLoad = false
                } else if (editorCanvas.childCount == 0 && project.components.isNotEmpty()) {
                    // 防呆：如果不是首次載入但畫布空了 (例如意外清除)，也嘗試還原
                    restoreComponents(project.components)
                }
            }
        }
        
        com.example.mqttpanelcraft.data.ProjectRepository.saveStatus.observe(this) { status ->
             if (status.startsWith("Disk Write Failed")) {
                 android.widget.Toast.makeText(this, status, android.widget.Toast.LENGTH_LONG).show()
             } else {
                 // Success message - show briefly
                 android.widget.Toast.makeText(this, status, android.widget.Toast.LENGTH_SHORT).show()
             }
        }
    }
    }


    /**
     * 更新 UI 模式 (Edit Mode vs Run Mode)
     * 控制：Sidebar 顯示、Log 面板可見性、底部表單鎖定、廣告暫停/播放
     */
    private fun updateModeUI() {
        if (isEditMode) {
             fabMode.setImageResource(android.R.drawable.ic_media_play)
             guideOverlay.visibility = View.VISIBLE
             
             // 編輯模式：顯示元件庫、屬性面板
             sidebarManager.showComponentsPanel()
             
             findViewById<View>(R.id.containerLogs)?.visibility = View.GONE
             findViewById<View>(R.id.containerProperties)?.visibility = View.VISIBLE
             
             // 鎖定底部 Sheet，避免干擾編輯
             (bottomSheetBehavior as? LockableBottomSheetBehavior)?.isLocked = true
             bottomSheetBehavior.isHideable = false 
             bottomSheetBehavior.peekHeight = (60 * resources.displayMetrics.density).toInt()
             bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
             
             idleAdController.stop()
        } else {
             fabMode.setImageResource(android.R.drawable.ic_menu_edit)
             // 執行模式：顯示 Run Mode 設定、日誌面板
             sidebarManager.showRunModePanel() 

             guideOverlay.visibility = View.VISIBLE 
             
             findViewById<View>(R.id.containerLogs)?.visibility = View.VISIBLE
             findViewById<View>(R.id.containerProperties)?.visibility = View.GONE
             
             // 解鎖底部 Sheet
             (bottomSheetBehavior as? LockableBottomSheetBehavior)?.isLocked = false
             bottomSheetBehavior.isHideable = false 
             
             bottomSheetBehavior.peekHeight = (60 * resources.displayMetrics.density).toInt()
             bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
             
             propertiesManager.hide() 
             
             idleAdController.start()
             
             // 訂閱 Project Topic
             viewModel.project.value?.let { p ->
                 val topic = "${p.name.lowercase(Locale.ROOT)}/${p.id}/#"
                 val intent = Intent(this, com.example.mqttpanelcraft.service.MqttService::class.java).apply {
                     action = "SUBSCRIBE"
                     putExtra("TOPIC", topic)
                 }
                 startService(intent)
                 logConsoleManager.addLog("Subscribed to project topic")
             }
        }
    }
    
    private fun setupUI() {
        // Toolbar 設定
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(android.R.drawable.ic_menu_sort_by_size)
        
        // 側邊欄開關按鈕
        toolbar.setNavigationOnClickListener { 
            if (isEditMode) sidebarManager.showComponentsPanel() 
            else sidebarManager.showRunModePanel()
            sidebarManager.openDrawer()
        }
        
        // 設定頁面跳轉
        findViewById<View>(R.id.btnSettings).setOnClickListener {
            // 跳轉前先存檔，確保位置被記錄
            syncComponentsState()
            viewModel.saveProject()
            
            val projectId = viewModel.project.value?.id
            if (projectId != null) {
                val intent = Intent(this, SetupActivity::class.java)
                intent.putExtra("PROJECT_ID", projectId)
                startActivity(intent)
            }
        }
        
        // 格線開關控制
        findViewById<View>(R.id.btnGrid).setOnClickListener {
             val isVisible = !guideOverlay.isGridVisible()
             guideOverlay.setGridVisible(isVisible)
             it.alpha = if(isVisible) 1.0f else 0.5f 
        }
        findViewById<View>(R.id.btnGrid).alpha = if(guideOverlay.isGridVisible()) 1.0f else 0.5f

        // Bottom Sheet 初始化與 Scrim (遮罩) 設定
        val bottomSheet = findViewById<View>(R.id.bottomSheet)
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
        (bottomSheetBehavior as? LockableBottomSheetBehavior)?.apply {
            headerViewId = R.id.bottomSheetHeader
            isLocked = true
            isHideable = true
            state = BottomSheetBehavior.STATE_HIDDEN
        }
        
        findViewById<View>(R.id.bottomSheetHeader).setOnClickListener {
            if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_COLLAPSED) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            } else {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            }
        }
        
        val scrim = findViewById<View>(R.id.bottomSheetScrim)
        scrim.setOnClickListener {
            if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            }
        }
        
        // 監聽 Bottom Sheet 狀態來控制 Scrim 顯示/淡入淡出
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
                scrim.alpha = slideOffset.coerceIn(0f, 1f)
            }
        })

        // FAB 模式切換按鈕事件
        fabMode.setOnClickListener {
            val project = viewModel.project.value
            if (project == null) return@setOnClickListener
            // 若為 FACTORY 專案，切換到 Edit Mode 需要密碼
            if (project.type == ProjectType.FACTORY && !isEditMode) {
                 showPasswordDialog { toggleMode() }
            } else { toggleMode() }
        }

        // 初始化側邊欄內容
        sidebarManager.setupComponentPalette(drawerLayout)
        sidebarManager.setupRunModeSettings(drawerLayout, this)
        
    }

    private fun toggleMode() {
        try {
            isEditMode = !isEditMode
            if (!isEditMode) {
                // 觸發條件 2: 從編輯切換成使用時 -> 儲存
                Toast.makeText(this, "Mode Switch: Saving Project...", Toast.LENGTH_SHORT).show()
                syncComponentsState()
                viewModel.saveProject() 
            }
            updateModeUI()
            setComponentsInteractive(!isEditMode)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error toggling mode: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }



    /**
     * 設定畫面上所有元件的互動性。
     * - Edit Mode: 攔截點擊事件以進行選取/移動，停用元件本身互動 (intercept = true)。
     * - Run Mode: 允許元件本身互動 (intercept = false)。
     */
    private fun setComponentsInteractive(enable: Boolean) {
        val isEdit = !enable
        for (i in 0 until editorCanvas.childCount) {
             val child = editorCanvas.getChildAt(i)
             if (child is com.example.mqttpanelcraft.ui.InterceptableFrameLayout) {
                 child.isEditMode = isEdit
                 // 在編輯模式顯示清除按鈕 (如 Image 元件)
                 child.findViewWithTag<View>("CLEAR_BTN")?.visibility = if (isEdit) View.VISIBLE else View.GONE
             }
        }
    }

    /**
     * 綁定元件的編輯行為 (點擊屬性、長按拖曳)。
     */
    private fun makeDraggable(view: View) {
        view.setOnClickListener {
            if (!isEditMode) return@setOnClickListener
            
            val currentList = viewModel.components.value
            val comp = currentList?.find { it.id == view.id }
            
            var labelText = comp?.label ?: ""
            val labelView = editorCanvas.findViewWithTag<TextView>("LABEL_FOR_${view.id}")
            if (labelView != null) {
                labelText = labelView.text.toString()
            }
            
            if (comp != null) {
                propertiesManager.showProperties(view, labelText, comp.topicConfig)
            }
        }
        
        // 委派拖曳行為給 DragDropManager
        dragDropManager.attachDragBehavior(view) {
             // 當開始拖曳時，顯示刪除區
             dropDeleteZone.visibility = View.VISIBLE
        }
    }

    // 優化：使用 Map 快取元件 View，加速 MQTT 訊息更新時的查找速度
    private val componentViewCache = mutableMapOf<Int, View>()

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
                
                val intent = Intent(this, com.example.mqttpanelcraft.service.MqttService::class.java).apply {
                    action = "PUBLISH"
                    putExtra("TOPIC", topic)
                    putExtra("PAYLOAD", base64)
                }
                startService(intent)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to send image", Toast.LENGTH_SHORT).show()
        }
    }
    
    // --- Utils ---
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
    
    // 傳遞使用者互動事件給 IdleAdController 以重置廣告計時
    override fun onUserInteraction() {
        super.onUserInteraction()
        idleAdController.onUserInteraction()
    }
}

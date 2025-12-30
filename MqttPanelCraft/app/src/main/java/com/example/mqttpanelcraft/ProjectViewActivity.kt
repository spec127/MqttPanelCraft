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
import android.widget.ImageButton
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.widget.ProgressBar
import android.content.res.ColorStateList
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
import kotlinx.coroutines.*
import android.content.Intent

/**
 * 主要的專案視圖 Activity (Project Main View).
 *
 * 此 Activity 負責：
 * 1. 顯示專案的畫布與元件 (UI Canvas & Components).
 * 2. 處理編輯模式 (Edit Mode) 與運行模式 (Run Mode) 的切換.
 * 3. 管理 MQTT 連線、訂閱與訊息處理 (MQTT Logic).
 * 4. 提供屬性編輯面板 (Properties Panel) 與 Log 控制台 (Console).
 *
 * 核心功能：
 * - 拖放 (Drag & Drop) 新增與刪除元件.
 * - 點擊元件進行屬性編輯 (Topic, Color, Size).
 * - 即時接收 MQTT 訊息並更新 UI (Observer Pattern).
 */
class ProjectViewActivity : AppCompatActivity(), MqttRepository.MessageListener {

    // 宣告延遲初始化的 UI 元件變數，這些變數稍後會在 onCreate 中綁定
    private lateinit var drawerLayout: DrawerLayout // 側邊抽屜佈局
    private lateinit var editorCanvas: ConstraintLayout // 編輯區畫布，組件將被放置於此
    private lateinit var guideOverlay: AlignmentOverlayView // 用於顯示對齊輔助線的自定義 View
    private lateinit var fabMode: FloatingActionButton // 切換編輯/運行模式的浮動按鈕
    private lateinit var logAdapter: LogAdapter // 用於顯示 MQTT 日誌的適配器
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<FrameLayout> // 控制底部面板行為（展開/收起）的物件

    // vFix: Grid Sync State
    private var isGridVisible = false // Default OFF

    // 底部面板的容器
    private lateinit var containerLogs: LinearLayout // 包含日誌顯示區域的容器（運行模式用）
    private lateinit var containerProperties: ScrollView // 包含屬性編輯區域的容器（編輯模式用）

    // 屬性輸入欄位
    private lateinit var etPropName: TextInputEditText // 輸入組件名稱的編輯框
    private lateinit var etPropWidth: TextInputEditText // 輸入組件寬度的編輯框
    private lateinit var etPropHeight: TextInputEditText // 輸入組件高度的編輯框
    private lateinit var etPropColor: TextInputEditText // 輸入組件顏色的編輯框
    private lateinit var btnSaveProps: Button // 儲存屬性變更的按鈕
    private lateinit var tvPropTopic: TextView // v49

    // v65: Component Index Map (ViewID -> Index)
    // v65: Component Index Map (ViewID -> Index)
    // Maps a View's ID to its logical Type Index (e.g. Button 1, Button 2)
    // Maps a View's ID to its logical Type Index (e.g. Button 1, Button 2)
    private val componentIndices = mutableMapOf<Int, Int>()

    // v80: Full Component Data Map (ViewID -> ComponentData)
    // Persists 'topicConfig', 'props' (compression), etc. while editing
    private val componentDataMap = mutableMapOf<Int, com.example.mqttpanelcraft.model.ComponentData>()

    private lateinit var dropDeleteZone: FrameLayout

    // 控制台輸入欄位（用於手動發送 MQTT 訊息）
    private lateinit var etTopic: EditText // 輸入 MQTT 主題的編輯框
    private lateinit var etPayload: EditText // 輸入 MQTT 訊息內容的編輯框
    // v80: Image Picker
    private var selectedCameraComponentId: Int? = null
    private val pickImageLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri: android.net.Uri? ->
        if (uri != null && selectedCameraComponentId != null) {
            processAndSendImage(uri, selectedCameraComponentId!!)
        }
    }

    private fun openGallery() {
        pickImageLauncher.launch("image/*")
    }

    // v80: Process Image (Compression levels 1-5)
    // Level 1: Very Low (10%)
    // Level 2: Low (25%)
    // Level 3: Medium (50%)
    // Level 4: High (1920x1080 approx, 75%)
    // Level 5: Original (100%)
    private fun processAndSendImage(uri: android.net.Uri, viewId: Int) {
        if (project == null) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Resolve Settings
                val compData = componentDataMap[viewId]
                // Default Level 3
                val level = compData?.props?.get("compression")?.toIntOrNull() ?: 3

                // Get Topic Override (Test Topic)
                // User requirement: "Sender also sends to BOTH topics"
                // User requirement: "Test topic naming rule: name/id/test/USER_DEFINED"
                val defaultTopic = getComponentTopic("camera", viewId, isCommand = true)
                val testSuffix = compData?.topicConfig
                val testTopic = if (!testSuffix.isNullOrEmpty()) {
                    "${project!!.name.lowercase()}/${project!!.id}/test/$testSuffix"
                } else null

                // 2. Load Bitmap
                val bitmap = android.provider.MediaStore.Images.Media.getBitmap(contentResolver, uri)

                // 3. Resize/Compress (Preserving Aspect Ratio)
                val maxDim = when(level) {
                   1 -> 320
                   2 -> 640
                   3 -> 800
                   4 -> 1024 // Cap High at 1024
                   5 -> 1280 // Cap Original at 1280 (1.2MP Max)
                   else -> 800
                }

                val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
                val (w, h) = if (ratio > 1) {
                    // Landscape
                    Pair(maxDim, (maxDim / ratio).toInt())
                } else {
                    // Portrait
                    Pair((maxDim * ratio).toInt(), maxDim)
                }

                val quality = when(level) {
                   1 -> 20
                   2 -> 40
                   3 -> 60
                   4 -> 80
                   5 -> 90
                   else -> 60
                }

                val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(bitmap, w, h, true)
                val outputStream = java.io.ByteArrayOutputStream()
                scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, outputStream)
                val byteArray = outputStream.toByteArray()
                val base64 = android.util.Base64.encodeToString(byteArray, android.util.Base64.NO_WRAP)

                // 4. Send (Chunked) - DUAL SEND
                val chunkSize = 16000 // Reduced to 16KB for stability
                val totalLength = base64.length
                val totalChunks = kotlin.math.ceil(totalLength.toDouble() / chunkSize).toInt()

                for (i in 0 until totalChunks) {
                    val start = i * chunkSize
                    val end = (start + chunkSize).coerceAtMost(totalLength)
                    val chunkData = base64.substring(start, end)

                    val payload = "${i + 1}|$totalChunks|$chunkData"

                    val message = org.eclipse.paho.client.mqttv3.MqttMessage(payload.toByteArray())
                    message.qos = 0

                    // Send to Default
                    MqttRepository.mqttClient?.publish(defaultTopic, message)

                    // Send to Test (if exists)
                    if (testTopic != null) {
                         val testMsg = org.eclipse.paho.client.mqttv3.MqttMessage(payload.toByteArray())
                         testMsg.qos = 0
                         MqttRepository.mqttClient?.publish(testTopic, testMsg)
                    }

                    MqttRepository.addLog("TX Img Part ${i+1}/$totalChunks", java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date()))
                    // Throttle (Prevent Flood)
                    delay(100) // 100ms delay (More conservative)
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ProjectViewActivity, "Sent Image ($totalChunks chunks)", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ProjectViewActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private lateinit var btnSend: Button // 發送 MQTT 訊息的按鈕

    // v75: Image Buffering (Ported from helloworld)
    // Topic -> (Index -> Data)
    private val imageBuffer = java.util.concurrent.ConcurrentHashMap<String, MutableMap<Int, String>>()
    private val imageTotals = java.util.concurrent.ConcurrentHashMap<String, Int>()

    private var isEditMode = false // 標記當前是否處於編輯模式，預設為 false (運行模式)
    private var projectId: String? = null // 儲存當前專案的 ID
    private var project: com.example.mqttpanelcraft.model.Project? = null // 儲存當前專案的資料模型

    private var selectedView: View? = null // 儲存當前被選中（點擊）的組件 View
    // ConstraintLayout 編輯區畫布，組件將被放置於此

    // Activity 創建時的生命週期方法
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState) // 呼叫父類別的 onCreate
        try {
            setContentView(R.layout.activity_project_view) // 設定此 Activity 的佈局檔案

            // 恢復之前的狀態（例如螢幕旋轉後）
            if (savedInstanceState != null) {
                isEditMode = savedInstanceState.getBoolean("IS_EDIT_MODE", false) // 讀取是否為編輯模式
                isGridVisible = savedInstanceState.getBoolean("IS_GRID_VISIBLE", false) // Restore grid state
            }

            setupUI() // 初始化 UI 元件與基本事件監聽
            setupConsole() // 初始化 MQTT 控制台相關功能
            setupSidebarInteraction() // 初始化側邊欄拖曳與搜尋功能
            setupPropertiesPanel() // 初始化屬性編輯面板

            // 載入專案資料
            projectId = intent.getStringExtra("PROJECT_ID") // 從 Intent 中獲取傳遞過來的專案 ID
            if (projectId != null) {
                loadProjectDetails(projectId!!) // 如果 ID 存在，載入專案詳細資訊
                restoreProjectState() // Restore Components
            } else {
                finish() // 如果沒有 ID，結束此 Activity
            }

            checkMqttConnection() // 檢查 MQTT 連線狀態並記錄日誌

            // 恢復側邊抽屜的狀態（必須在 setupUI 之後執行）
            if (savedInstanceState != null) {
                val wasDrawerOpen = savedInstanceState.getBoolean("IS_DRAWER_OPEN", false) // 讀取抽屜是否開啟
                if (wasDrawerOpen) {
                    drawerLayout.openDrawer(GravityCompat.START) // 如果之前是開的，則打開抽屜
                }
            }
            
            // Ensure UI reflects restored mode
            updateModeUI()
            
            // Initialize Data
            ProjectRepository.initialize(this)

            // Initialize Ads
            com.example.mqttpanelcraft.utils.AdManager.initialize(this)
            com.example.mqttpanelcraft.utils.AdManager.loadBannerAd(this, findViewById(R.id.bannerAdContainer))

            // Start/Stop Idle Controller based on initial mode
            if (!isEditMode) {
                idleAdController.start()
            }

        } catch (e: Exception) {
            CrashLogger.logError(this, "Project View Init Failed", e) // 記錄錯誤到 CrashLogger
            Toast.makeText(this, "Init Error: ${e.message}", Toast.LENGTH_LONG).show() // v45: Show error to user
            finish() // 發生錯誤時結束 Activity
            CrashLogger.logError(this, "Project View Init Failed", e)
            finish()
        }
    }

    // --- Idle Ad Controller ---
    private val idleAdController = IdleAdController()

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        idleAdController.onUserInteraction()
        return super.dispatchTouchEvent(ev)
    }

    override fun onDestroy() {
        super.onDestroy()
        idleAdController.stop()
    }

    inner class IdleAdController {
        private val handler = android.os.Handler(android.os.Looper.getMainLooper())
        private val intervals = listOf(30L, 60L, 180L, 300L, 600L) // Seconds
        private var currentIntervalIndex = 0
        private var isRunning = false

        private val idleRunnable = Runnable {
            showAdAndScheduleNext()
        }

        fun start() {
            if (isRunning) return
            isRunning = true
            currentIntervalIndex = 0 // Reset sequence on entry? User said "First time is 30s...".
            // If user toggles modes, should we reset? Usually yes.
            scheduleNext(intervals[0])
        }

        fun stop() {
            if (!isRunning) return
            isRunning = false
            handler.removeCallbacks(idleRunnable)
        }

        fun onUserInteraction() {
            if (isRunning) {
                // Reset timer for CURRENT interval
                handler.removeCallbacks(idleRunnable)
                val delay = if (currentIntervalIndex < intervals.size) intervals[currentIntervalIndex] else 600L
                scheduleNext(delay)
            }
        }

        private fun scheduleNext(delaySeconds: Long) {
            handler.removeCallbacks(idleRunnable)
            handler.postDelayed(idleRunnable, delaySeconds * 1000)
            android.util.Log.d("IdleAd", "Scheduled ad in ${delaySeconds}s")
        }

        private fun showAdAndScheduleNext() {
            if (!isRunning) return

            com.example.mqttpanelcraft.utils.AdManager.showInterstitial(this@ProjectViewActivity) {
                // On Ad Closed
                // Advance to next interval
                if (currentIntervalIndex < intervals.size - 1) {
                    currentIntervalIndex++
                }
                val nextDelay = intervals[currentIntervalIndex] // or last one if maxed
                // If maxed, it stays at 60m (3600L)
                val delay = if (currentIntervalIndex < intervals.size) intervals[currentIntervalIndex] else 600L
                scheduleNext(delay)

                // vFix: Refresh Insets to prevent status bar overlap
                setupWindowInsets()
            }
        }
    }

    // 儲存 Activity 狀態的生命週期方法（例如被系統回收或旋轉螢幕前）
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState) // 呼叫父類別方法
        outState.putBoolean("IS_EDIT_MODE", isEditMode) // 儲存當前模式
        outState.putBoolean("IS_GRID_VISIBLE", isGridVisible) // Save grid state
        if (::drawerLayout.isInitialized) {
            outState.putBoolean("IS_DRAWER_OPEN", drawerLayout.isDrawerOpen(GravityCompat.START)) // 儲存抽屜開啟狀態
        }
    }
    
    // Activity 可見時的生命週期方法
    override fun onStart() {
        super.onStart()
        setupWindowInsets()

        setupDrawerListener()
    }



    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView.findViewById(android.R.id.content)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)

            // v99: Fix Status Bar for Light Mode (Black Icons)
            val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            if (!isDark) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                     window.insetsController?.setSystemBarsAppearance(
                         android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                         android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                     )
                } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                     @Suppress("DEPRECATION")
                     window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                }
            } else {
                 // Clear for Dark Mode
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                     window.insetsController?.setSystemBarsAppearance(0, android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
                } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                     @Suppress("DEPRECATION")
                     window.decorView.systemUiVisibility = window.decorView.systemUiVisibility and android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                }
            }

            WindowInsetsCompat.CONSUMED
        }
    }




    private fun loadProjectDetails(id: String) {
        // ProjectRepository is a singleton object
        val p = ProjectRepository.getProjectById(id)
        if (p != null) {
            project = p
            supportActionBar?.title = p.name
            // vFix: Set Custom Title View safely
            findViewById<android.widget.TextView>(R.id.tvToolbarTitle).text = p.name
        } else {
            android.widget.Toast.makeText(this, "Project not found!", android.widget.Toast.LENGTH_SHORT).show()
            finish()
        }
    }



    // v31: Refresh UI from cache when returning to activity
    // v31: Refresh UI logic merged into bottom onResume

    // 設定側邊抽屜的狀態監聽器
    private fun setupDrawerListener() { // 設定側邊抽屜的狀態監聽器
        drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener { // 為抽屜佈局添加一個監聽器
            // 當抽屜滑動時呼叫
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) { // 當抽屜滑動時呼叫
                // 嚴格鎖定：如果抽屜開始滑動，則禁用底部面板的拖曳功能
                if (slideOffset > 0.05f) { // 如果抽屜開始滑動（滑動偏移量大於0.05f）
                    bottomSheetBehavior.isDraggable = false // 禁用底部面板的拖曳功能

                    // 如果底部面板是展開的，則自動將其收起
                    if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) { // 如果底部面板是展開的
                        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED // 則自動將其收起
                    }
                }
            }

            // 當抽屜完全打開時呼叫
            override fun onDrawerOpened(drawerView: View) { // 當抽屜完全打開時呼叫
                 // 嚴格鎖定：確保底部面板不能被拖曳
                 bottomSheetBehavior.isDraggable = false // 確保底部面板不能被拖曳
            }

            // 當抽屜完全關閉時呼叫
            override fun onDrawerClosed(drawerView: View) { // 當抽屜完全關閉時呼叫
                // 抽屜關閉後，如果處於編輯模式，則重新啟用底部面板的拖曳功能
                if (isEditMode) { // 如果當前是編輯模式
                    bottomSheetBehavior.isDraggable = true // 重新啟用底部面板的拖曳功能
                }
            }

            // 當抽屜狀態改變時呼叫
            override fun onDrawerStateChanged(newState: Int) { // 當抽屜狀態改變時呼叫
                // 不執行任何操作
            }
        })
    }

    // 初始化 UI 元件與設定事件監聽
    private fun setupUI() {
        // Initialize Lateinit Variables
        drawerLayout = findViewById(R.id.drawerLayout)
        editorCanvas = findViewById(R.id.editorCanvas)
        guideOverlay = findViewById(R.id.guideOverlay)
        fabMode = findViewById(R.id.fabMode)
        containerLogs = findViewById(R.id.containerLogs)
        containerProperties = findViewById(R.id.containerProperties)
        bottomSheetBehavior = BottomSheetBehavior.from(findViewById(R.id.bottomSheet))
        // vFix: Configure custom LockableBottomSheetBehavior
        if (bottomSheetBehavior is com.example.mqttpanelcraft.ui.LockableBottomSheetBehavior) {
            (bottomSheetBehavior as com.example.mqttpanelcraft.ui.LockableBottomSheetBehavior<*>).headerViewId = R.id.bottomSheetHeader
        }
        bottomSheetBehavior.isDraggable = true // Must be true for custom behavior to work

        // vFix: Add callback to prevent ANY state changes except programmatic ones
        var allowStateChange = false
        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                // Only allow COLLAPSED and EXPANDED states, block DRAGGING/SETTLING from user swipes
                if (!allowStateChange && newState == BottomSheetBehavior.STATE_DRAGGING) {
                    // Force back to current state
                    bottomSheetBehavior.state = if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
                        BottomSheetBehavior.STATE_EXPANDED
                    } else {
                        BottomSheetBehavior.STATE_COLLAPSED
                    }
                }
            }
            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
        })

        // Setup Toolbar
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

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

        // v49: Ensure tvPropTopic is accessible if declared as lateinit property
        // But better to verify variable declaration first. I will assume I need to declare it.
        // Wait, I didn't see tvPropTopic declared in class vars. I should declare it.
        // Since I'm using multi-chunk, I can check later. For now, I'll localize it in setupPropertiesPanel
        // OR add it as a lateinit var if I can find the vars section.
        // Actually, viewing the lines above, lateinit vars are at top.
        // SAFE APPROACH: Use findViewById locally in populateProperties if not declared,
        // BUT layout optimization suggests keeping references.
        // I will add declaration at line 98 (approx) or wherever other lateinits are.
        // Re-reading file lines 218...
        // lines 218-226 init vars. I will assume I can just use findViewById every time in populateProperties if I don't want to mess up class structure blindly.
        // NO, inefficient. I will add lateinit var declaration in a separate chunk.

        findViewById<View>(R.id.bottomSheetHeader).setOnTouchListener { v, event ->
             // vFix: Toggle on Click / Drag Header
             if (event.action == android.view.MotionEvent.ACTION_UP) {
                 if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_COLLAPSED) {
                     // Only allow expanding if NOT in Edit Mode or if a component is selected
                     if (!isEditMode || selectedView != null) {
                         bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                     } else {
                         Toast.makeText(this@ProjectViewActivity, "Select a component to edit properties", Toast.LENGTH_SHORT).show()
                     }
                 } else {
                     bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                 }
                 v.performClick()
             }
             true
        }


        // Refinement 1: Canvas Click to Collapse and Deselect
        editorCanvas.setOnClickListener {
            if (isEditMode && selectedView != null) {
                // Deselect
                selectedView?.setBackgroundResource(R.drawable.component_border)
                selectedView = null
            }
            if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                resetCanvasPan() // Reset Pan
            }
        }

        // vFix: Disable nested scrolling on content containers to prevent BottomSheetBehavior from detecting swipes
        // This preserves internal scrolling (RecyclerView, ScrollView) but prevents drag-to-close
        ViewCompat.setNestedScrollingEnabled(containerLogs, false)
        ViewCompat.setNestedScrollingEnabled(containerProperties, false)

        // Also disable on RecyclerView specifically
        val rvLogs = findViewById<RecyclerView>(R.id.rvConsoleLogs)
        ViewCompat.setNestedScrollingEnabled(rvLogs, false)

        // Restore FAB Listener
        fabMode.setOnClickListener {
            toggleMode()
        }

        // v39: Center Project Title & v43: Click to Retry
        val tvTitle = findViewById<TextView>(R.id.tvToolbarTitle)
        // REMOVED: tvTitle.text = project!!.name (Project is set in loadProjectDetails)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        // Click Title to Reconnect
        tvTitle.setOnClickListener {
             Toast.makeText(this, "Retrying Connection...", Toast.LENGTH_SHORT).show()
             startMqttConnection() // v46: Use shared helper to restart connection properly
        }



        // Settings Button (Custom View)
        val btnSettings = findViewById<ImageView>(R.id.btnSettings)
        btnSettings.setOnClickListener {
             if (projectId != null) {
                val intent = android.content.Intent(this, SetupActivity::class.java)
                intent.putExtra("PROJECT_ID", projectId)
                startActivity(intent)
                // finish() // Fixed: Do not finish, so Back button returns here
            }
        }


        // Run Mode Sidebar Actions
        try {
            val prefs = getSharedPreferences("ProjectViewPrefs", MODE_PRIVATE)

            // Orientation Lock Switch
            val switchOrientationLock = findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchOrientationLock)

            // Restore saved state
            val isOrientationLocked = prefs.getBoolean("orientation_locked", false)
            switchOrientationLock?.isChecked = isOrientationLocked

            // Apply saved orientation setting
            if (isOrientationLocked) {
                requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED
            } else {
                requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR
            }

            switchOrientationLock?.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED
                } else {
                    requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR
                }
                // Save state
                prefs.edit().putBoolean("orientation_locked", isChecked).apply()
            }

            // Keep Screen On Switch
            val switchKeepScreenOn = findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchKeepScreenOn)

            // Restore saved state
            val isKeepScreenOn = prefs.getBoolean("keep_screen_on", false)
            switchKeepScreenOn?.isChecked = isKeepScreenOn

            // Apply saved screen-on setting
            if (isKeepScreenOn) {
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }


        // Status Dot Logic (Restored)
        val viewStatusDot = findViewById<View>(R.id.viewStatusDot)

        // v47: Global Component Update Observer
        MqttRepository.latestMessage.observe(this) { msgPair ->
             if (project != null && msgPair != null) {
                 val (topic, payload) = msgPair
                 updateComponentFromMqtt(topic, payload)
             }
        }

        MqttRepository.connectionStatus.observe(this) { status ->
             when (status) {
                 com.example.mqttpanelcraft.MqttStatus.CONNECTED -> {
                     viewStatusDot.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.GREEN)
                 }
                 com.example.mqttpanelcraft.MqttStatus.CONNECTING -> {
                     viewStatusDot.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.LTGRAY) // Gray
                 }
                 MqttStatus.FAILED -> {
                     viewStatusDot.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.RED)
                     // Removed Dialog to prevent blocking/annoyance. Red light is sufficient feedback.
                 }
                 else -> {
                     viewStatusDot.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.GRAY)
                 }
             }
        } // End of observe



        // 運行模式側邊欄動作（例如開關網格、深色模式）
        try {
            val backgroundGrid = findViewById<View>(R.id.backgroundGrid) // 獲取背景網格 View

            // 顯示網格開關（編輯模式）
            // Grid Toggle Listener (Edit Mode)
            val switchGridToggle = findViewById<SwitchMaterial>(R.id.switchGridToggle)
            switchGridToggle?.isChecked = isGridVisible // Set initial state
            switchGridToggle?.setOnCheckedChangeListener { _, isChecked ->
                isGridVisible = isChecked
                backgroundGrid?.visibility = if (isChecked) View.VISIBLE else View.GONE

                // Sync Run Mode Switch if it exists
                val switchRun = findViewById<SwitchMaterial>(R.id.switchGridToggleRunMode)
                if (switchRun != null && switchRun.isChecked != isChecked) {
                    switchRun.isChecked = isChecked
                }
            }

            // Grid Toggle (Run Mode)
            val switchGridToggleRunMode = findViewById<SwitchMaterial>(R.id.switchGridToggleRunMode) // 獲取運行模式的網格開關
            switchGridToggleRunMode?.isChecked = isGridVisible // Set initial state
            // 設定運行模式網格開關的監聽器
            switchGridToggleRunMode?.setOnCheckedChangeListener { _, isChecked ->
                isGridVisible = isChecked
                backgroundGrid?.visibility = if (isChecked) View.VISIBLE else View.GONE // 根據開關狀態顯示或隱藏網格
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

                // Sync Edit Mode Switch
                val switchEdit = findViewById<SwitchMaterial>(R.id.switchGridToggle)
                if (switchEdit != null && switchEdit.isChecked != isChecked) {
                    switchEdit.isChecked = isChecked
                }
            }

            // 深色模式開關
            val switchDarkMode = findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchDarkMode) // 獲取深色模式開關

            // 設定初始狀態（讀取當前系統深色模式設定）
            val currentNightMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK // 獲取當前夜間模式掩碼
            switchDarkMode?.isChecked = (currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES) // 如果是夜間模式，開關設為開啟

            // 設定深色模式開關的監聽器
            // 設定深色模式開關的監聽器
            switchDarkMode?.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES) // 切換到深色模式
                } else {
                    androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO) // 切換到淺色模式
                }
                // Save state - assuming preference saving is handled elsewhere or not critical for this specific logic block correction
                // If it was intended to save, it should be done here.
                // However, the original code had nested listeners which was the main bug.
                // I will assume minimal impact fix.
            }

            // Keep Screen On Switch logic
            // Note: switchKeepScreenOn was defined at line 621 but used here.
            // The previous block was completely malformed.
             val switchKeepScreenOn = findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchKeepScreenOn)

            switchKeepScreenOn?.setOnCheckedChangeListener { _, isChecked ->
                 if (isChecked) {
                     window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                 } else {
                     window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                 }
                 prefs.edit().putBoolean("keep_screen_on", isChecked).apply()
            }

        } catch (e: Exception) {
            // Log or ignore errors in dynamic UI setup
            e.printStackTrace()
        }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        // Drop Zone Listener (Delete)
        dropDeleteZone = findViewById(R.id.dropDeleteZone)
        dropDeleteZone.setOnDragListener { v, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> true
                DragEvent.ACTION_DRAG_ENTERED -> {
                    // v72: Fix "Square Background" bug by using ColorFilter on existing Shape
                    v.background.colorFilter = android.graphics.PorterDuffColorFilter(
                        android.graphics.Color.RED,
                        android.graphics.PorterDuff.Mode.SRC_ATOP
                    )
                    true
                }
                DragEvent.ACTION_DRAG_EXITED -> {
                    v.background.clearColorFilter() // Restore original circle color
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
                                  // Direct Delete without Dialog (User requested intuitive drag-to-delete)
                                  val labelView = findLabelView(component)
                                  if (labelView != null) editorCanvas.removeView(labelView)
                                  editorCanvas.removeView(component)
                                  guideOverlay.clear()

                                  // Clean up index map
                                  componentIndices.remove(viewId)

                                  Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show()
                             }
                         } catch (e: Exception) {}
                     }
                     true
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                     dropDeleteZone.visibility = View.GONE // Hide zone on end
                     true
                }
                else -> false
            }
        }


    } // End of setupUI

    // v47: Update UI Components based on MQTT Topic
    private fun updateComponentFromMqtt(topic: String, payload: String) {
        if (project == null) return
        val baseTopic = "${project!!.name.lowercase()}/${project!!.id}"

        // Optimization: Quick check if topic belongs to this project
        if (!topic.startsWith(baseTopic)) return

        for (i in 0 until editorCanvas.childCount) {
            val container = editorCanvas.getChildAt(i) as? FrameLayout ?: continue
            val type = container.tag as? String ?: continue // e.g., "BUTTON", "LED"

            // Expected Topic: project/id/type/index/state
            // Index logic reuse: Container ID
            // Refactored to use helper:
            // Expected Topic: project/id/type/index/state
            // Index logic reuse: Container ID
            // Refactored to use helper:
            // v80: Loopback Check - Test Topic Support
            // User Request: "If Test Topic is not empty, component will simultaneously subscribe to this topic"
            val expectedTopic = getComponentTopic(type, container.id, isCommand = false)
            var matches = (topic == expectedTopic)

            // Check Test Topic (Concurrent Subscription)
            if (!matches) {
                val compData = componentDataMap[container.id]
                if (compData != null && !compData.topicConfig.isNullOrEmpty()) {
                    // Treat topicConfig as "Test Topic Suffix"
                    // Protocol: "project/id/test/testTopic"
                    val testSuffix = compData.topicConfig
                    val projectBase = "${project!!.name.lowercase()}/${project!!.id}"

                    val expectedTestTopic = "$projectBase/test/$testSuffix"

                    if (topic == expectedTestTopic) {
                        matches = true
                    }
                }
            }

            if (matches) {
                val innerView = container.getChildAt(0)
                try {
                    when (type) {
                        "TEXT" -> (innerView as? TextView)?.text = payload
                        "SLIDER" -> (innerView as? com.google.android.material.slider.Slider)?.value = payload.toFloatOrNull() ?: 0f
                        "LED" -> {
                            // v48: Strict Logic
                            val p = payload.trim().lowercase()
                            val color = when (p) {
                                "1", "true", "on" -> Color.GREEN
                                "0", "false", "off" -> Color.RED
                                else -> null // Ignore
                            }
                            if (color != null) {
                                innerView.background = android.graphics.drawable.GradientDrawable().apply {
                                    shape = android.graphics.drawable.GradientDrawable.OVAL
                                    setColor(color)
                                    setStroke(2, Color.DKGRAY)
                                }
                            }
                        }
                        "THERMOMETER" -> (innerView as? ProgressBar)?.progress = payload.toIntOrNull()?.coerceIn(0, 100) ?: 0
                        "IMAGE" -> {
                             // v75: Chunk Support
                             var fullBase64: String? = null

                             // Try to parse as chunk: index|total|data
                             val parts = payload.split("|", limit = 3)
                             if (parts.size == 3) {
                                 val index = parts[0].toIntOrNull()
                                 val total = parts[1].toIntOrNull()
                                 val data = parts[2]

                                 if (index != null && total != null) {
                                     val buffer = imageBuffer.getOrPut(topic) { java.util.concurrent.ConcurrentHashMap() }

                                     // Fix: If index 1 arrives, assume new transmission (most likely) and clear potentially stale buffer
                                     if (index == 1) {
                                         buffer.clear()
                                     }

                                     buffer[index] = data
                                     imageTotals[topic] = total

                                     // Check if complete
                                     if (buffer.size == total) {
                                         val sb = StringBuilder()
                                         for (k in 1..total) {
                                             sb.append(buffer[k] ?: "")
                                         }
                                         fullBase64 = sb.toString()

                                         // Cleanup
                                         imageBuffer.remove(topic)
                                         imageTotals.remove(topic)
                                     }
                                 }
                             } else {
                                 // Not a chunk, treat as full payload
                                 fullBase64 = payload
                             }

                             if (fullBase64 != null) {
                                 try {
                                     val decodedString = android.util.Base64.decode(fullBase64, android.util.Base64.DEFAULT)
                                     val decodedByte = android.graphics.BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
                                     (innerView as? ImageView)?.setImageBitmap(decodedByte)
                                 } catch (e: Exception) {
                                     // e.printStackTrace()
                                 }
                             }
                        }
                    }
        } catch (e: Exception) { }
        }
    }
    }


    // region Topic Generation Helper

    /**
     * 產生標準化的 MQTT Topic 字串.
     *
     * 格式規則: {project_name}/{project_id}/{type}/{component_id}/{direction}
     *
     * @param type 元件類型 (e.g., "BUTTON", "LED").
     * @param id 元件的 View ID (整數).
     * @param isCommand true 為發送指令 (cmd), false 為接收狀態 (state).
     * @return 完整的 Topic 字串, 若專案為空則返回空字串.
     */
    private fun getComponentTopic(type: String, id: Int, isCommand: Boolean): String {
        if (project == null) return ""
        val direction = if (isCommand) "cmd" else "state"

        // v65: Use Smart Index if available
        val index = componentIndices[id] ?: id

        return "${project!!.name.lowercase()}/${project!!.id}/${type.lowercase()}/$index/$direction"
    }

    // v65: Smart Index Helper
    private fun getNextIndex(type: String): Int {
        // Find existing indices for this type
        val existingIndices = mutableSetOf<Int>()
        for (i in 0 until editorCanvas.childCount) {
            val child = editorCanvas.getChildAt(i)
            if (child.tag == type) {
                val idx = componentIndices[child.id]
                if (idx != null) existingIndices.add(idx)
            }
        }

        // Find first gap (starting from 1)
        var next = 1
        while (existingIndices.contains(next)) {
            next++
        }
        return next
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_project_view, menu)
        return true
    }


    // 初始化屬性編輯面板
    private fun setupPropertiesPanel() {
        etPropName = findViewById(R.id.etPropName) // 獲取名稱輸入框
        etPropWidth = findViewById(R.id.etPropWidth) // 獲取寬度輸入框
        etPropHeight = findViewById(R.id.etPropHeight) // 獲取高度輸入框
        etPropColor = findViewById(R.id.etPropColor) // 獲取顏色輸入框
        btnSaveProps = findViewById(R.id.btnSaveProps) // 獲取儲存按鈕

        // v49: Initialize Topic Display
        tvPropTopic = findViewById(R.id.tvPropTopic)
        val etPropTopicConfig = findViewById<TextInputEditText>(R.id.etPropTopicConfig)
        val containerCompression = findViewById<LinearLayout>(R.id.containerCompression)
        val sliderCompression = findViewById<com.google.android.material.slider.Slider>(R.id.sliderCompression)


        etPropColor.setOnClickListener { showGradientColorPicker() } // 設定顏色輸入框的點擊事件（顯示顏色選擇器）
        etPropColor.isFocusable = false // v41: Fix API 26 requirement (was FOCUSABLE_AUTO)
        etPropColor.isFocusableInTouchMode = false // 禁止觸控模式下取得焦點（強制使用點擊事件）

        btnSaveProps.setOnClickListener {
            selectedView?.let { view -> // 確保有選中的組件 View
                try {
                    val w = etPropWidth.text.toString().toIntOrNull()
                    val h = etPropHeight.text.toString().toIntOrNull()

                    // ... Layout Params update (w, h) omitted for brevity as it is unchanged ...
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
                    
                    val labelView = findLabelView(view)
                    if (labelView != null) {
                        labelView.text = etPropName.text.toString()
                    }

                    // v80: Save Advanced Props to Map
                    val currentData = componentDataMap.getOrPut(view.id) {
                         com.example.mqttpanelcraft.model.ComponentData(view.id, view.tag as String, 0f,0f,0,0,"")
                    }

                    // Save Topc Config
                    currentData.topicConfig = etPropTopicConfig.text.toString()

                    // Save Compression (If visible)
                    if (containerCompression.visibility == View.VISIBLE) {
                        currentData.props["compression"] = sliderCompression.value.toInt().toString()
                    }

                    Toast.makeText(this, "Updated", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }


    }

    // 顯示顏色選擇器（漸層色盤）
    private fun showGradientColorPicker() {
        val width = 600 // 色盤寬度
        val height = 400 // 色盤高度
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) // 創建 Bitmap
        val canvas = android.graphics.Canvas(bitmap) // 創建畫布

        // 繪製水平光譜漸層
        val gradient = LinearGradient(0f, 0f, width.toFloat(), 0f,
            intArrayOf(Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED),
            null, Shader.TileMode.CLAMP)
        val paint = android.graphics.Paint()
        paint.shader = gradient
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint) // 繪製矩形

        // 繪製垂直透明度/明度漸層（從透明到黑）
        val darkGradient = LinearGradient(0f, 0f, 0f, height.toFloat(),
            intArrayOf(Color.TRANSPARENT, Color.BLACK),
            null, Shader.TileMode.CLAMP)
        val darkPaint = android.graphics.Paint()
        darkPaint.shader = darkGradient
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), darkPaint)

        val imageView = ImageView(this)
        imageView.setImageBitmap(bitmap) // 將生成的 Bitmap 設定給 ImageView

        // 創建並顯示對話框
        val dialog = AlertDialog.Builder(this)
            .setTitle("Select Color") // 標題
            .setView(imageView) // 設定內容為色盤圖片
            .setPositiveButton("Close", null) // 關閉按鈕
            .create()
            
        // 設定圖片的觸控事件（選取顏色）
        imageView.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
                // 限制座標在圖片範圍內
                val x = event.x.toInt().coerceIn(0, width - 1)
                val y = event.y.toInt().coerceIn(0, height - 1)
                val pixel = bitmap.getPixel(x, y) // 獲取像素顏色
                val hexBox = String.format("#%06X", (0xFFFFFF and pixel)) // 轉換為 Hex 字串
                etPropColor.setText(hexBox) // 填入輸入框
                dialog.setTitle("Color: $hexBox") // 在標題顯示當前顏色
            }
            true // 已處理事件
        }
        
        dialog.show() // 顯示對話框
    }
    
    // 初始化 MQTT 控制台並綁定 ViewModel (MqttRepository LiveData)
    private fun setupConsole() {
        val rvLogs = findViewById<RecyclerView>(R.id.rvConsoleLogs) // 獲取 RecyclerView
        etTopic = findViewById(R.id.etConsoleTopic) // 獲取主題輸入框
        etPayload = findViewById(R.id.etConsolePayload) // 獲取訊息輸入框
        btnSend = findViewById(R.id.btnConsoleSend) // 獲取發送按鈕
        logAdapter = LogAdapter() // 創建日誌適配器
        rvLogs.layoutManager = LinearLayoutManager(this) // 設定佈局管理器
        rvLogs.adapter = logAdapter // 設定適配器

         // 觀察 MqttRepository 的 logs 變數
         MqttRepository.logs.observe(this) { logs ->
            logAdapter.submitList(logs) // 當日誌更新時，提交給適配器
            if (logs.isNotEmpty()) rvLogs.smoothScrollToPosition(logs.size - 1) // 自動捲動到底部
        }

        // 發送按鈕點擊事件
        btnSend.setOnClickListener {
             var topic = etTopic.text.toString() // 獲取主題
            val payload = etPayload.text.toString() // 獲取訊息內容

            // v38: Auto-Prefix for Console (Restore)
            if (project != null && topic.isNotEmpty()) {
                val prefix = "${project!!.name.lowercase()}/${project!!.id}/"
                if (!topic.startsWith(prefix)) {
                    topic = prefix + topic
                }
            }

            if (topic.isNotEmpty() && payload.isNotEmpty()) { // 確保不為空
                val client = MqttRepository.mqttClient // 獲取 MQTT 客戶端
                if (client != null && client.isConnected) { // 檢查是否已連線
                    try {
                        val message = org.eclipse.paho.client.mqttv3.MqttMessage(payload.toByteArray()) // 創建訊息
                        client.publish(topic, message) // 發布訊息
                        MqttRepository.addLog("TX [$topic]: $payload", java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date())) // 記錄發送日誌
                    } catch (e: Exception) {
                        Toast.makeText(this, "Publish Failed: ${e.message}", Toast.LENGTH_SHORT).show() // 發送失敗提示
                    }
                } else {
                    Toast.makeText(this, "Not Connected.", Toast.LENGTH_SHORT).show()
                }
            } else {
                 Toast.makeText(this, "Topic and Payload required", Toast.LENGTH_SHORT).show() // v39: Feedback
            }
        }

        // v39: Subscribe Button Logic (Missing previously)
        val btnSubscribe = findViewById<Button>(R.id.btnConsoleSubscribe)
        if (btnSubscribe != null) {
            btnSubscribe.setOnClickListener {
                 var topic = etTopic.text.toString()
                 if (project != null && topic.isNotEmpty()) {
                     val prefix = "${project!!.name.lowercase()}/${project!!.id}/"
                     if (!topic.startsWith(prefix)) {
                         topic = prefix + topic
                     }
                 }

                 if (topic.isNotEmpty()) {
                      val client = MqttRepository.mqttClient
                      if (client != null && client.isConnected) {
                          try {
                              // Start Service Action for Subscribe (Robustness) or Direct
                              val intent = Intent(this, com.example.mqttpanelcraft.service.MqttService::class.java)
                              intent.action = "SUBSCRIBE"
                              intent.putExtra("TOPIC", topic)
                              startService(intent) // Delegate to Service

                              MqttRepository.addLog("Subscribing to [$topic]...", java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date()))
                          } catch (e: Exception) {
                               Toast.makeText(this, "Sub Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                          }
                      } else {
                          Toast.makeText(this, "Not Connected.", Toast.LENGTH_SHORT).show()
                      }
                 } else {
                      Toast.makeText(this, "Topic required", Toast.LENGTH_SHORT).show()
                 }
            }
        } else {
            // Log warning or ignore if button missing in layout variant
            CrashLogger.logError(this, "Warning: btnConsoleSubscribe not found in layout", java.lang.Exception("View Missing"))
        }
    }

    // 檢查 MQTT 連線狀態並記錄初始日誌
    private fun checkMqttConnection() {
        val client = MqttRepository.mqttClient
        if (client == null || !client.isConnected) {
            MqttRepository.addLog("System: Starting MQTT Connection...", "")
            startMqttConnection() // v46: Trigger connection on load if not connected
        } else {
            MqttRepository.addLog("System: Connected to broker.", "")
        }
    }



    // v46: Helper to start MQTT Service with CONNECT action
    private fun startMqttConnection() {
        if (project == null) return

        val intent = Intent(this, com.example.mqttpanelcraft.service.MqttService::class.java)
        // Stop previous instance to ensure clean retry state (requested by user for retry logic)
        stopService(intent)

        intent.action = "CONNECT"
        intent.putExtra("BROKER", project!!.broker)
        intent.putExtra("PORT", project!!.port)
        intent.putExtra("USER", project!!.username)
        intent.putExtra("PASSWORD", project!!.password)
        intent.putExtra("CLIENT_ID", project!!.clientId)

        startService(intent)
    }

    // 切換編輯/運行模式
    private fun toggleMode() {
        isEditMode = !isEditMode
        updateModeUI()
    }

    // 根據目前的 isEditMode 更新 UI 介面
    private fun updateModeUI() {
        val sidebarEditMode = findViewById<View>(R.id.sidebarEditMode) // 獲取編輯模式側邊欄
        val sidebarRunMode = findViewById<View>(R.id.sidebarRunMode) // 獲取運行模式側邊欄
        val backgroundGrid = findViewById<View>(R.id.backgroundGrid) // 獲取背景網格

        if (isEditMode) { // 如果是編輯模式
             fabMode.setImageResource(android.R.drawable.ic_media_play) // 按鈕圖示改為「播放」
             // vFix: Remove hardcoded white background drawable, use GridPatternView instead
             editorCanvas.background = null

             // vFix: Ensure Grid matches state
             backgroundGrid?.visibility = if (isGridVisible) View.VISIBLE else View.GONE

             // vFix: Hide Bottom Sheet initially in Edit Mode
             bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED // Or HIDDEN? User said "retract". Collapsed is peeking.
             // If user wants "Cannot pull out", maybe HIDDEN is better?
             // But "Click header to retract" implies it's visible.
             // Let's stick to Collapsed (Header visible).
             // Wait, "Component NOT dragged out -> List cannot be pulled".
             // If Header is visible, they can click it.
             // I added logic in Header Touch Listener to block expansion if no component selected.

             // 編輯模式下，解鎖側邊欄以便使用者拖曳組件
             drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.START)
             
             sidebarEditMode?.visibility = View.VISIBLE // 顯示組件側邊欄
             sidebarRunMode?.visibility = View.GONE // 隱藏運行側邊欄

             containerLogs.visibility = View.GONE // 隱藏日誌面板
             containerProperties.visibility = View.VISIBLE // 顯示屬性面板
             guideOverlay.visibility = View.VISIBLE // 顯示對齊線
             guideOverlay.bringToFront() // 將對齊線置頂

             // 同步編輯模式的網格開關狀態
             val switchGridToggle = findViewById<SwitchMaterial>(R.id.switchGridToggle)
             switchGridToggle?.isChecked = isGridVisible

             Toast.makeText(this, "Edit Mode", Toast.LENGTH_SHORT).show()
        } else { // 如果是運行模式
             fabMode.setImageResource(android.R.drawable.ic_menu_edit) // 按鈕圖示改為「編輯」
             editorCanvas.background = null // 移除背景網格
             guideOverlay.clear() // 清除對齊線
             guideOverlay.visibility = View.GONE // 隱藏對齊線圖層

             drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.START)
             
             sidebarEditMode?.visibility = View.GONE // 隱藏組件側邊欄
             sidebarRunMode?.visibility = View.VISIBLE // 顯示運行側邊欄

             containerLogs.visibility = View.VISIBLE // 顯示日誌面板
             containerProperties.visibility = View.GONE // 隱藏屬性面板

             // 同步運行模式的網格開關狀態
             val switchGridToggleRunMode = findViewById<SwitchMaterial>(R.id.switchGridToggleRunMode)
             switchGridToggleRunMode?.isChecked = isGridVisible

             containerLogs.visibility = View.VISIBLE
             containerProperties.visibility = View.GONE
             Toast.makeText(this, "Run Mode", Toast.LENGTH_SHORT).show()
        }

        // Disable/Enable interaction with components based on mode
        setComponentsInteractive(!isEditMode)
    }

    // Helper to enable/disable interaction for components
    private fun setComponentsInteractive(enable: Boolean) {
        for (i in 0 until editorCanvas.childCount) {
             val child = editorCanvas.getChildAt(i)
             if (child is FrameLayout && child.childCount > 0) {
                 // 1. Handle Main Content (Child 0)
                 val innerView = child.getChildAt(0)
                 if (innerView is Button || innerView is com.google.android.material.slider.Slider) {
                     innerView.isEnabled = enable
                     innerView.isClickable = enable
                     innerView.isFocusable = enable
                 }

                 // 2. Handle Overlays (e.g. CLEAR_BTN)
                 for (k in 0 until child.childCount) {
                     val sub = child.getChildAt(k)
                     if (sub.tag == "CLEAR_BTN") {
                         // Show only in Run Mode (enable=true -> Run Mode)
                         // Wait, setComponentsInteractive is called with (!isEditMode).
                         // So enable == True means Run Mode.
                         sub.visibility = if (enable) View.VISIBLE else View.GONE
                     }
                 }
             }
        }
    }

    private fun setupSidebarInteraction() {
        val touchListener = View.OnTouchListener { view, event ->
             if (event.action == MotionEvent.ACTION_DOWN) {
                 val tag = view.tag as? String ?: return@OnTouchListener false

                 // Define default size
                 val density = resources.displayMetrics.density
                 var wDp = 50
                 var hDp = 50

             // Custom dimensions for specific components
             if (tag == "THERMOMETER") {
                 wDp = 120
                 hDp = 30
             } else if (tag == "SLIDER") {
                 wDp = 100
             }

             val wPx = (wDp * density).toInt()
             val hPx = (hDp * density).toInt()

             // Create a preview of the actual component
             val previewView = createComponentView(tag)

             // Measure and layout the preview (including all children)
             previewView.measure(
                 View.MeasureSpec.makeMeasureSpec(wPx, View.MeasureSpec.EXACTLY),
                 View.MeasureSpec.makeMeasureSpec(hPx, View.MeasureSpec.EXACTLY)
             )
             previewView.layout(0, 0, wPx, hPx)

             // Force layout of children
             if (previewView is FrameLayout && previewView.childCount > 0) {
                 val child = previewView.getChildAt(0)
                 val p = (4 * density).toInt() // padding
                 child.measure(
                     View.MeasureSpec.makeMeasureSpec(wPx - 2*p, View.MeasureSpec.EXACTLY),
                     View.MeasureSpec.makeMeasureSpec(hPx - 2*p, View.MeasureSpec.EXACTLY)
                 )
                 child.layout(p, p, wPx - p, hPx - p)
             }

                 // Create drag data and shadow
                 val item = ClipData.Item(tag)
                 val dragData = ClipData(tag, arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN), item)
                 val shadow = View.DragShadowBuilder(previewView)
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
        // Fix: Enable Camera Drag
        findViewById<View>(R.id.cardCamera).apply { tag="CAMERA"; setOnTouchListener(touchListener) }



        // 設定組件搜尋欄
        val etSearchComponents = findViewById<EditText>(R.id.etSearchComponents) // 獲取搜尋輸入框

        // 設定觸控監聽器，偵測清除按鈕（右側圖示）的點擊
        etSearchComponents?.setOnTouchListener { v, event -> // 為搜尋輸入框設定觸控監聽器
            if (event.action == MotionEvent.ACTION_UP) { // 當手指抬起時
                // 檢查點擊位置是否在右側圖示（drawableEnd）範圍內
                if (event.rawX >= (etSearchComponents.right - etSearchComponents.compoundDrawables[2].bounds.width())) { // 判斷點擊是否在清除圖示區域
                    etSearchComponents.text.clear() // 清空搜尋文字
                    v.performClick() // 觸發標準點擊事件（為了無障礙功能）
                    return@setOnTouchListener true
                }
            }
            false
        }

        // 設定文字變更監聽器（即時搜尋過濾）
        etSearchComponents?.addTextChangedListener(object : android.text.TextWatcher { // 為搜尋輸入框添加文字變更監聽器
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {} // 文字改變前調用
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {} // 文字改變時調用
            override fun afterTextChanged(s: android.text.Editable?) { // 文字改變後調用
                val query = s.toString().lowercase().trim() // 獲取輸入文字並轉為小寫、去除空白

                // 獲取所有組件卡片容器
                val cardsContainer = findViewById<LinearLayout>(R.id.cardsContainer) // 獲取包含所有組件卡片的 LinearLayout

                // 遍歷所有卡片 (注意：結構是 Category(TextView) -> Row(LinearLayout) -> Card(MaterialCardView))
                for (i in 0 until cardsContainer.childCount) {
                     val child = cardsContainer.getChildAt(i)

                     // 如果是水平排列的 LinearLayout，表示它是放置卡片的一行
                     if (child is LinearLayout && child.orientation == LinearLayout.HORIZONTAL) {
                         // 遍歷這一行裡面的所有卡片
                         var hasVisibleCardInRow = false
                         for (j in 0 until child.childCount) {
                             val card = child.getChildAt(j)

                             if (card is androidx.cardview.widget.CardView || card is com.google.android.material.card.MaterialCardView) {
                                 val innerLayout = (card as android.view.ViewGroup).getChildAt(0) as? android.view.ViewGroup
                                 val textView = innerLayout?.getChildAt(1) as? TextView

                                 if (textView != null) {
                                     val componentName = textView.text.toString().lowercase()
                                     if (componentName.contains(query)) {
                                         card.visibility = View.VISIBLE
                                         hasVisibleCardInRow = true
                                     } else {
                                         card.visibility = View.GONE // 這裡會有一個問題：如果隱藏了，可能會導致排版空白，但暫時先這樣
                                     }
                                 }
                             }
                         }
                         // 可以選擇是否隱藏整行，但為了保持類別標題位置，暫時保留行本身
                         child.visibility = if (hasVisibleCardInRow) View.VISIBLE else View.GONE
                     }
                }
            }
        })

        editorCanvas.setOnDragListener { v, event -> // 為編輯畫布設定拖曳監聽器
            if (!isEditMode) return@setOnDragListener false // 如果不是編輯模式，則不處理拖曳事件
            when (event.action) { // 根據拖曳事件的動作類型進行處理
                DragEvent.ACTION_DROP -> { // 當拖曳物被放下時
                     handleDrop(event) // 呼叫 handleDrop 函數處理放下事件
                }
                DragEvent.ACTION_DRAG_LOCATION -> { // 當拖曳物在移動時
                     val localState = event.localState as? View // 獲取拖曳物的本地狀態（即被拖曳的視圖）
                     if (localState != null && localState.parent == editorCanvas) { // 如果本地狀態存在且其父視圖是編輯畫布
                         checkAlignment(event.x, event.y, localState) // 檢查對齊線
                     }
                }
                DragEvent.ACTION_DRAG_ENDED -> { // 當拖曳操作結束時
                    guideOverlay.clear() // 清除對齊線
                    val localState = event.localState as? View // 獲取拖曳物的本地狀態
                    localState?.visibility = View.VISIBLE // 顯示被拖曳的視圖（因為拖曳開始時被隱藏了）

                    // Also show the label
                    if (localState != null) { // 如果本地狀態存在
                        val labelView = findLabelView(localState) // 找到對應的標籤視圖
                        labelView?.visibility = View.VISIBLE // 顯示標籤視圖
                    }
                }
            }
            true // 已處理事件
        }
    }


    private fun handleDrop(event: DragEvent) { // 處理拖曳放下事件
        val x = event.x // 獲取放下點的 X 座標
        val y = event.y // 獲取放下點的 Y 座標
        val localView = event.localState as? View // 獲取拖曳物的本地狀態（即被拖曳的視圖）

        if (localView != null && localView.parent == editorCanvas) { // 如果是從畫布上拖曳現有組件
            guideOverlay.clear() // 清除對齊線
             var finalX = x - (localView.width/2) // 計算組件最終的 X 座標（居中）
             var finalY = y - (localView.height/2) // 計算組件最終的 Y 座標（居中）

             val snapPos = calculateSnap(x, y, localView.width, localView.height, localView) // 計算是否需要吸附到網格或其它組件
             if (snapPos != null) { // 如果有吸附位置
                 finalX = snapPos.x.toFloat() // 更新最終 X 座標為吸附位置
                 finalY = snapPos.y.toFloat() // 更新最終 Y 座標為吸附位置
             }

             localView.x = finalX // 設定組件的 X 座標
             localView.y = finalY // 設定組件的 Y 座標
             localView.visibility = View.VISIBLE // 顯示組件

             // Update label position
             val labelView = findLabelView(localView) // 找到對應的標籤視圖
             if (labelView != null) { // 如果標籤視圖存在
                 labelView.x = finalX // 更新標籤的 X 座標與組件對齊
                 labelView.y = finalY + localView.height + 4 // 更新標籤的 Y 座標，位於組件下方並留有間距
             }
        } else { // 如果是從側邊欄拖曳新組件到畫布
            val tag = event.clipData?.getItemAt(0)?.text?.toString() ?: "TEXT" // 從拖曳資料中獲取組件標籤，默認為 "TEXT"
            val newView = createComponentView(tag) // 根據標籤創建新的組件視圖

            // v16: Default Size
            val density = resources.displayMetrics.density
            var wDp = 50
            var hDp = 50

            // Custom dimensions for drop
            if (tag == "THERMOMETER") {
                wDp = 120
                hDp = 30
            } else if (tag == "SLIDER") {
                wDp = 100
            }

            val wPx = (wDp * density).toInt()
            val hPx = (hDp * density).toInt()

            val params = ConstraintLayout.LayoutParams(wPx, hPx) // 創建布局參數
            newView.layoutParams = params // 設定布局參數

            var rawX = x - (wPx / 2) // 設定 X 座標（居中放下點）
            var rawY = y - (hPx / 2) // 設定 Y 座標（居中放下點）

            // vRefinement: Calculate Snap for NEW components too
            // Note: View is not added yet, passing newView is fine
            val snapPt = calculateSnap(rawX, rawY, wPx, hPx, newView)
            if (snapPt != null) {
                rawX = snapPt.x.toFloat()
                rawY = snapPt.y.toFloat()
            }

            newView.x = rawX
            newView.y = rawY

            // v16: Unique ID Fix
            newView.id = View.generateViewId() // 生成唯一的 View ID

            // v65: Smart Index Generation
            val index = getNextIndex(tag)
            componentIndices[newView.id] = index

            editorCanvas.addView(newView) // 將新組件添加到畫布
            makeDraggable(newView) // 讓新組件可拖曳

            // Fix: Ensure new component is not interactive if we are in Edit Mode
            if (isEditMode) {
                if (newView is FrameLayout && newView.childCount > 0) {
                     val inner = newView.getChildAt(0)
                     inner.isEnabled = false
                     inner.isClickable = false
                     inner.isFocusable = false
                }
            }

            // Add label below component
            // 添加位於組件下方的標籤
            val labelView = TextView(this).apply {
                id = View.generateViewId() // 標籤也需要唯一的 ID

                // vFix: Map tags to human readable names
                // v65: Append Index
                val typeName = when (tag) {
                    "THERMOMETER" -> "Level Indicator"
                    "BUTTON" -> "Button"
                    "SLIDER" -> "Slider"
                    "TEXT" -> "Text"
                    "IMAGE" -> "Image"
                    "LED" -> "LED"
                    "CAMERA" -> "Camera"
                    else -> tag
                }
                text = "$typeName $index"

                textSize = 10f // 設定文字大小
                gravity = Gravity.CENTER // 設定文字置中
                setTextColor(Color.DKGRAY) // 設定文字顏色為深灰色
                setTag("LABEL_FOR_${newView.id}") // Link to component / 設定 Tag 以便與組件關聯

                val labelParams = ConstraintLayout.LayoutParams(
                    wPx, // Match component width / 寬度與組件相同
                    ConstraintLayout.LayoutParams.WRAP_CONTENT // 高度自適應
                )
                layoutParams = labelParams // 設定布局參數
                this.x = newView.x // Align with component / X 軸與組件對齊
                this.y = newView.y + hPx + 4 // Position below component / Y 軸位於組件下方並留間距
            }
            editorCanvas.addView(labelView) // 將標籤添加到畫布
        }
    }

    // region UI Creation logic

    // 根據標籤類型創建組件視圖
    private fun createComponentView(tag: String): View {
        val context = this
        // Create a container
        val container = FrameLayout(context)

        // REVERT: Revert to standard border for all (including LED)
        container.setBackgroundResource(R.drawable.component_border)

        container.apply {
            val padding = (4 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }

        // 根據類型創建物件內的實際 View
        val view = when (tag) {
            "BUTTON" -> Button(context).apply {
                text = "BTN"
                setOnClickListener {
                    // v40: Restore Topic Logic for Button
                    if (!isEditMode && project != null) {
                         // Format: name/id/type/index/cmd
                         // Refactored to use helper:
                         val index = (parent as? View)?.id ?: id
                         val topic = getComponentTopic("button", index, isCommand = true)
                         val payload = "1" // Default payload

                         MqttRepository.mqttClient?.let { client ->
                             if (client.isConnected) {
                                 try {
                                     val message = org.eclipse.paho.client.mqttv3.MqttMessage(payload.toByteArray())
                                     client.publish(topic, message)
                                     MqttRepository.addLog("TX [$topic]: $payload", java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date()))
                                 } catch (e: Exception) {
                                     Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                 }
                             }
                         }
                    }
                }
            }
            "TEXT" -> TextView(context).apply { text = "Text"; gravity = Gravity.CENTER }
            "IMAGE" -> ImageView(context).apply {
                setImageResource(android.R.drawable.ic_menu_gallery)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            "SLIDER" -> com.google.android.material.slider.Slider(context).apply {
                valueFrom = 0f
                valueTo = 100f
                value = 50f
                stepSize = 1f

                // v48: Publish only on release (Stop Tracking)
                addOnSliderTouchListener(object : com.google.android.material.slider.Slider.OnSliderTouchListener {
                    override fun onStartTrackingTouch(slider: com.google.android.material.slider.Slider) {
                        // Optional: Disable updates from MQTT while dragging to prevent jumping
                    }

                    override fun onStopTrackingTouch(slider: com.google.android.material.slider.Slider) {
                         if (!isEditMode && project != null) {
                            val index = (parent as? View)?.id ?: id
                            // Refactored to use helper:
                            val topic = getComponentTopic("slider", index, isCommand = true)
                            val payload = slider.value.toInt().toString()

                            MqttRepository.mqttClient?.let { client ->
                                 if (client.isConnected) {
                                     try {
                                         val message = org.eclipse.paho.client.mqttv3.MqttMessage(payload.toByteArray())
                                         message.isRetained = true
                                         client.publish(topic, message)
                                     } catch (e: Exception) {}
                                 }
                             }
                         }
                    }
                })
            }
            "LED" -> View(context).apply {
                // Use GradientDrawable for reliable Oval shape
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(Color.RED)
                    setStroke(2, Color.DKGRAY)
                }
                // LED is a follower/subscriber component.
                // Color update logic handled in MqttRepository observer (setupUI/refreshUI) if we had mapped it.
                // For now, it stays static Red until mapped.
            }
            "THERMOMETER" -> ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progress = 75
                progressTintList = android.content.res.ColorStateList.valueOf(Color.GREEN)
            }
            "IMAGE" -> ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageResource(android.R.drawable.ic_menu_gallery) // Standard Placeholder
                setImageResource(R.drawable.ic_webview) // Placeholder
                // background = android.graphics.drawable.ColorDrawable(Color.LTGRAY)
            }
            "CAMERA" -> Button(context).apply {
                text = "CAM"
                setOnClickListener {
                    if (!isEditMode) {
                        // v80: Trigger Image Picker
                        selectedCameraComponentId = (parent as? View)?.id ?: id
                        openGallery()
                    }
                }
            }
            else -> TextView(context).apply { text = tag }
        }


        // 確保內部 View 填滿容器
        val params = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        view.layoutParams = params

        container.addView(view) // 將內部 View 添加到容器
        container.tag = tag // 設定容器的 Tag 為組件類型

        // v91: Image Clear Overlay Button
        if (tag == "IMAGE") {
             val clearBtn = ImageButton(context).apply {
                 setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                 background = null // Default / Transparent
                 // setColorFilter(Color.DKGRAY) // Optional: If icon is dark
                 setPadding(4, 4, 4, 4)

                 // Size 32dp
                 val density = resources.displayMetrics.density
                 val size = (32 * density).toInt()
                 val flParams = FrameLayout.LayoutParams(size, size)
                 flParams.gravity = Gravity.TOP or Gravity.END
                 flParams.setMargins(8, 8, 8, 8)
                 layoutParams = flParams

                 this.tag = "CLEAR_BTN" // Identify for mode switching

                 setOnClickListener {
                     if (!isEditMode) {
                         // Reset Image
                         (view as? ImageView)?.let { iv ->
                             iv.setImageResource(android.R.drawable.ic_menu_gallery)
                             iv.background = null // Transparent
                             Toast.makeText(context, "Image Cleared", Toast.LENGTH_SHORT).show()
                         }
                     }
                 }
             }
             container.addView(clearBtn)
        }

        return container // 返回容器（作為組件）
    }

    // 讓 View 變為可拖曳 (Rewrite for Touch & Live Snap)
    private fun makeDraggable(view: View) {
        // Click for Properties
        view.setOnClickListener {
            if (!isEditMode) return@setOnClickListener
            selectedView?.setBackgroundResource(R.drawable.component_border)
            selectedView = view
            // view.setBackgroundResource(R.drawable.component_border_selected)
            if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_COLLAPSED) {
                populateProperties(view)
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            } else {
                 populateProperties(view)
            }
        }

        // Touch for Dragging (Live Snap & Bounds)
        view.setOnTouchListener(object : View.OnTouchListener {
            var dX = 0f
            var dY = 0f
            var startRawX = 0f
            var startRawY = 0f
            var isDragging = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                 if (!isEditMode) return false

                 when (event.action) {
                     MotionEvent.ACTION_DOWN -> {
                         dX = v.x - event.rawX
                         dY = v.y - event.rawY
                         startRawX = event.rawX
                         startRawY = event.rawY
                         isDragging = false // Wait for move threshold?

                         // Show Drop Zone
                         dropDeleteZone.visibility = View.VISIBLE
                         dropDeleteZone.animate().scaleX(1f).scaleY(1f).start()

                         // Shadow/Highlight?
                         v.alpha = 0.8f
                     }
                     MotionEvent.ACTION_MOVE -> {
                         // Threshold check
                         if (kotlin.math.hypot(event.rawX - startRawX, event.rawY - startRawY) > 10) {
                             isDragging = true
                         }
                         if (!isDragging) return true

                         // 1. Calculate Raw New Position
                         var newX = event.rawX + dX
                         var newY = event.rawY + dY

                         // 2. Boundary Constraint (Strict)
                         val maxX = (editorCanvas.width - v.width).toFloat()
                         val maxY = (editorCanvas.height - v.height).toFloat()
                         newX = newX.coerceIn(0f, maxX)
                         newY = newY.coerceIn(0f, maxY)

                         // 3. Live Snap
                         val snapPt = calculateSnap(newX, newY, v.width, v.height, v)
                         if (snapPt != null) {
                             newX = snapPt.x.toFloat()
                             newY = snapPt.y.toFloat()
                         }

                         // Apply Position
                         v.x = newX
                         v.y = newY

                         // Move Label
                         val labelView = findLabelView(v)
                         if (labelView != null) {
                             labelView.x = newX
                             labelView.y = newY + v.height + 4
                         }

                         // 4. Guide Logic
                         checkAlignment(newX + v.width/2f, newY + v.height/2f, v)

                         // 5. Check Delete Zone Overlap
                         val locations = IntArray(2)
                         dropDeleteZone.getLocationOnScreen(locations)
                         val zoneRect = android.graphics.Rect(locations[0], locations[1], locations[0]+dropDeleteZone.width, locations[1]+dropDeleteZone.height)

                         val vRawX = event.rawX
                         val vRawY = event.rawY
                         val pointerRect = android.graphics.Rect(vRawX.toInt(), vRawY.toInt(), vRawX.toInt()+1, vRawY.toInt()+1)

                         if (android.graphics.Rect.intersects(zoneRect, pointerRect)) {
                              dropDeleteZone.animate().scaleX(1.3f).scaleY(1.3f).setDuration(100).start()
                              v.alpha = 0.3f // Fade component to show it will be deleted
                         } else {
                              dropDeleteZone.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                              v.alpha = 0.8f
                         }
                     }
                     MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                         dropDeleteZone.visibility = View.GONE
                         guideOverlay.clear()
                         v.alpha = 1.0f

                         // Delete Check
                         val locations = IntArray(2)
                         dropDeleteZone.getLocationOnScreen(locations)
                         val zoneRect = android.graphics.Rect(locations[0], locations[1], locations[0]+dropDeleteZone.width, locations[1]+dropDeleteZone.height)
                         val vRawX = event.rawX
                         val vRawY = event.rawY

                         // Basic hit test on pointer
                         if (vRawX >= zoneRect.left && vRawX <= zoneRect.right && vRawY >= zoneRect.top && vRawY <= zoneRect.bottom) {
                              // Delete
                              editorCanvas.removeView(v)
                              val label = findLabelView(v)
                              if (label != null) editorCanvas.removeView(label)
                              componentIndices.remove(v.id)
                              Toast.makeText(this@ProjectViewActivity, "Deleted", Toast.LENGTH_SHORT).show()
                         } else {
                             // Save State?
                             saveProjectState()
                         }

                         if (!isDragging) {
                             v.performClick() // Handle Click check
                         }
                     }
                 }
                 return true
            }
        })
    }

    // endregion

    // region Alignment Logic

    // 計算吸附位置 (Component-to-Component)
    private fun calculateSnap(rawX: Float, rawY: Float, w: Int, h: Int, currentView: View): Point? {
        val density = resources.displayMetrics.density
        val threshold = 16f * density // 16dp snap threshold

        var snapX = rawX
        var snapY = rawY
        var hasSnapX = false
        var hasSnapY = false

        val myLeft = rawX
        val myRight = rawX + w
        val myTop = rawY
        val myBottom = rawY + h
        val myCenterX = rawX + w / 2f
        val myCenterY = rawY + h / 2f

        var minDiffX = threshold
        var minDiffY = threshold

        for (i in 0 until editorCanvas.childCount) {
            val target = editorCanvas.getChildAt(i)
            if (target == currentView) continue
            // Skip labels if possible, though labels are usually non-interactive
             val tag = target.tag as? String ?: continue
             if (tag.startsWith("LABEL_FOR_")) continue

            val tLeft = target.x
            val tRight = target.x + target.width
            val tTop = target.y
            val tBottom = target.y + target.height
            val tCenterX = target.x + target.width / 2f
            val tCenterY = target.y + target.height / 2f

            // --- X Axis Alignment ---
            // Left to Left
            if (kotlin.math.abs(myLeft - tLeft) < minDiffX) {
                snapX = tLeft
                minDiffX = kotlin.math.abs(myLeft - tLeft)
                hasSnapX = true
            }
            // Left to Right
            if (kotlin.math.abs(myLeft - tRight) < minDiffX) {
                snapX = tRight
                minDiffX = kotlin.math.abs(myLeft - tRight)
                hasSnapX = true
            }
             // Right to Left
            if (kotlin.math.abs(myRight - tLeft) < minDiffX) {
                snapX = tLeft - w
                minDiffX = kotlin.math.abs(myRight - tLeft)
                hasSnapX = true
            }
            // Right to Right
            if (kotlin.math.abs(myRight - tRight) < minDiffX) {
                snapX = tRight - w
                minDiffX = kotlin.math.abs(myRight - tRight)
                hasSnapX = true
            }
             // Center to Center
            if (kotlin.math.abs(myCenterX - tCenterX) < minDiffX) {
                snapX = tCenterX - w / 2f
                minDiffX = kotlin.math.abs(myCenterX - tCenterX)
                hasSnapX = true
            }

            // --- Y Axis Alignment ---
            // Top to Top
            if (kotlin.math.abs(myTop - tTop) < minDiffY) {
                snapY = tTop
                minDiffY = kotlin.math.abs(myTop - tTop)
                hasSnapY = true
            }
             // Top to Bottom
            if (kotlin.math.abs(myTop - tBottom) < minDiffY) {
                snapY = tBottom
                minDiffY = kotlin.math.abs(myTop - tBottom)
                hasSnapY = true
            }
             // Bottom to Top
            if (kotlin.math.abs(myBottom - tTop) < minDiffY) {
                snapY = tTop - h
                minDiffY = kotlin.math.abs(myBottom - tTop)
                hasSnapY = true
            }
             // Bottom to Bottom
            if (kotlin.math.abs(myBottom - tBottom) < minDiffY) {
                snapY = tBottom - h
                minDiffY = kotlin.math.abs(myBottom - tBottom)
                hasSnapY = true
            }
             // Center to Center
            if (kotlin.math.abs(myCenterY - tCenterY) < minDiffY) {
                snapY = tCenterY - h / 2f
                minDiffY = kotlin.math.abs(myCenterY - tCenterY)
                hasSnapY = true
            }
        }

        if (hasSnapX || hasSnapY) {
            return Point(if(hasSnapX) snapX.toInt() else rawX.toInt(), if(hasSnapY) snapY.toInt() else rawY.toInt())
        }

        return null // No snap
    }

    // 檢查對齊並繪製輔助線 (Component Integration)
    // Note: Arguments x, y are "Center" coordinates in current call usage.
    // BUT we need Top/Left for component logic. The caller calls checkAlignment(newX + v.width/2f, newY + v.height/2f, v)
    // So x,y are indeed centers.
    // We should use v.x and v.y ideally if they are updated, OR calculate from center passed in.
    // However, the caller sets v.x and v.y BEFORE calling checkAlignment!
    // See line 1271: v.x = newX; v.y = newY
    // So we can trust `currentView.x`.
    private fun checkAlignment(centerX: Float, centerY: Float, currentView: View) {
        guideOverlay.clear()


        val density = resources.displayMetrics.density
        val threshold = 2f * density // Drawing tolerance (smaller than snap to show exact match)

        val myLeft = currentView.x
        val myRight = currentView.x + currentView.width
        val myTop = currentView.y
        val myBottom = currentView.y + currentView.height
        val myCenterX = currentView.x + currentView.width / 2f
        val myCenterY = currentView.y + currentView.height / 2f

        for (i in 0 until editorCanvas.childCount) {
            val target = editorCanvas.getChildAt(i)
            if (target == currentView) continue
             val tag = target.tag as? String ?: continue
             if (tag.startsWith("LABEL_FOR_")) continue

            val tLeft = target.x
            val tRight = target.x + target.width
            val tTop = target.y
            val tBottom = target.y + target.height
            val tCenterX = target.x + target.width / 2f
            val tCenterY = target.y + target.height / 2f

            // X Matches
            if (kotlin.math.abs(myLeft - tLeft) < threshold || kotlin.math.abs(myLeft - tRight) < threshold ||
                kotlin.math.abs(myRight - tLeft) < threshold || kotlin.math.abs(myRight - tRight) < threshold) {
                // Draw vertical line at the match X
                // Which X? Use current view's matched edge
                val matchX = if (kotlin.math.abs(myLeft - tLeft) < threshold) tLeft else if (kotlin.math.abs(myLeft - tRight) < threshold) tRight else if (kotlin.math.abs(myRight - tLeft) < threshold) tLeft else tRight

                // Draw line covering vertical span
                val minY = kotlin.math.min(myTop, tTop)
                val maxY = kotlin.math.max(myBottom, tBottom)
                guideOverlay.addLine(matchX, minY - 100, matchX, maxY + 100)
            }

            if (kotlin.math.abs(myCenterX - tCenterX) < threshold) {
                 val minY = kotlin.math.min(myTop, tTop)
                 val maxY = kotlin.math.max(myBottom, tBottom)
                 guideOverlay.addLine(tCenterX, minY - 100, tCenterX, maxY + 100)
            }

            // Y Matches
             if (kotlin.math.abs(myTop - tTop) < threshold || kotlin.math.abs(myTop - tBottom) < threshold ||
                kotlin.math.abs(myBottom - tTop) < threshold || kotlin.math.abs(myBottom - tBottom) < threshold) {

                val matchY = if (kotlin.math.abs(myTop - tTop) < threshold) tTop else if (kotlin.math.abs(myTop - tBottom) < threshold) tBottom else if (kotlin.math.abs(myBottom - tTop) < threshold) tTop else tBottom

                val minX = kotlin.math.min(myLeft, tLeft)
                val maxX = kotlin.math.max(myRight, tRight)
                guideOverlay.addLine(minX - 100, matchY, maxX + 100, matchY)
            }

            if (kotlin.math.abs(myCenterY - tCenterY) < threshold) {
                 val minX = kotlin.math.min(myLeft, tLeft)
                 val maxX = kotlin.math.max(myRight, tRight)
                 guideOverlay.addLine(minX - 100, tCenterY, maxX + 100, tCenterY)
            }
        }
    }

    // --- Persistence Logic ---

    private fun saveProjectState() {
        val currentProject = project ?: return
        currentProject.components.clear()

        android.util.Log.d("Persistence", "Saving Project: ${currentProject.name}")

        for (i in 0 until editorCanvas.childCount) {
             val view = editorCanvas.getChildAt(i)
             val tag = view.tag as? String ?: continue
             if (tag.startsWith("LABEL_FOR_")) continue

             val labelView = findLabelView(view)
             val labelText = labelView?.text?.toString() ?: ""

             // v80: Retrieve existing data or create new
             val oldData = componentDataMap[view.id]

             val compData = com.example.mqttpanelcraft.model.ComponentData(
                 id = view.id,
                 type = tag,
                 x = view.x,
                 y = view.y,
                 width = view.width,
                 height = view.height,
                 label = labelText,
                 // Preserve advanced props
                 topicConfig = oldData?.topicConfig ?: "",
                 props = oldData?.props ?: mutableMapOf()
             )
             currentProject.components.add(compData)
        }

        com.example.mqttpanelcraft.data.ProjectRepository.updateProject(currentProject)
        android.util.Log.d("Persistence", "Saved ${currentProject.components.size} components to Repo")
        // Toast.makeText(this, "Project Saved (${currentProject.components.size})", Toast.LENGTH_SHORT).show()
    }

    private fun restoreProjectState() {
        try {
            val currentProject = project ?: return
            android.util.Log.d("Persistence", "Restoring Project: ${currentProject.id}, Components: ${currentProject.components.size}")

            // Safety check for EditorCanvas (in case not initialized)
            if (findViewById<android.view.View>(R.id.editorCanvas) == null) {
                android.util.Log.e("Persistence", "EditorCanvas Not Found!")
                return
            }

            if (currentProject.components.isEmpty()) return

            val toRemove = mutableListOf<View>()
            for (i in 0 until editorCanvas.childCount) {
                val v = editorCanvas.getChildAt(i)
                if (v.tag is String) toRemove.add(v)
            }
            toRemove.forEach { editorCanvas.removeView(it) }

            componentIndices.clear()
            componentDataMap.clear()

            for (comp in currentProject.components) {
                // Defensive: If type is missing
                if (comp.type.isNullOrEmpty()) continue

                val newView = createComponentView(comp.type)

                // vFix: Handle ID collision or invalid ID
                if (comp.id == View.NO_ID || findViewById<View>(comp.id) != null) {
                     newView.id = View.generateViewId()
                } else {
                     newView.id = comp.id
                }

                // Restore Indices logic
                if (newView.id != View.NO_ID) {
                    val idx = comp.label.filter { it.isDigit() }.toIntOrNull() ?: 1
                    componentIndices[newView.id] = idx
                }

                val params = FrameLayout.LayoutParams(comp.width, comp.height)
                newView.layoutParams = params
                newView.x = comp.x
                newView.y = comp.y

                editorCanvas.addView(newView)

                makeDraggable(newView)

                // Update comp ID to match View ID if changed
                comp.id = newView.id
                componentDataMap[newView.id] = comp

                // Recreate Label
                val labelView = TextView(this).apply {
                    id = View.generateViewId()
                    text = comp.label
                    textSize = 10f
                    gravity = Gravity.CENTER
                    setTextColor(Color.DKGRAY)
                    setTag("LABEL_FOR_${newView.id}")

                    val labelParams = FrameLayout.LayoutParams(
                        comp.width,
                        ConstraintLayout.LayoutParams.WRAP_CONTENT
                    )
                    layoutParams = labelParams
                    this.x = newView.x
                    this.y = newView.y + comp.height + 4
                }
                editorCanvas.addView(labelView)
            }
        } catch (e: Exception) {
            android.util.Log.e("Persistence", "Restore Failed", e)
            android.widget.Toast.makeText(this, "Error restoring components: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }



    override fun onResume() {
        super.onResume()
        // v95: Register Listener (Zero-Drop)
        MqttRepository.registerListener(this)

        refreshUIFromCache() // Merged from v31

        // Reload project to check for changes
        if (project != null) {
            val fresh = com.example.mqttpanelcraft.data.ProjectRepository.getProjectById(project!!.id)
            if (fresh != null) {
                // If critical params changed, we imply a change in environment
                val oldBroker = project!!.broker
                val oldPort = project!!.port
                project = fresh // Update local reference

                // Refresh Toolbar Title (in case name changed)
                findViewById<android.widget.TextView>(R.id.tvToolbarTitle).text = project!!.name

                performFullSync() // Merged from v35

                // Connection Check
                val currentUri = MqttRepository.mqttClient?.serverURI ?: ""
                val targetUri = "tcp://${project!!.broker}:${project!!.port}"
                val isConnected = MqttRepository.mqttClient?.isConnected == true

                // Reconnect if disconnected OR params changed
                if (!isConnected || project!!.broker != oldBroker || project!!.port != oldPort) {
                    val intent = android.content.Intent(this, com.example.mqttpanelcraft.service.MqttService::class.java).apply {
                        action = "CONNECT"
                        putExtra("BROKER", project!!.broker)
                        putExtra("PORT", project!!.port)
                        putExtra("USER", project!!.username)
                        putExtra("PASSWORD", project!!.password)
                        putExtra("CLIENT_ID", project!!.clientId)
                    }
                    startService(intent)
                }
            }
        }

        // v98: Restart Idle Ad if running
        if (!isEditMode) {
             idleAdController.start()
        }
    }

    override fun onPause() {
        super.onPause()
        // v95: Unregister Listener
        MqttRepository.unregisterListener(this)
        // v98: Stop Idle Ad Controller to prevent showing ads when navigating to Setup
        idleAdController.stop()
        saveProjectState()
    }

    private fun checkOverlap(v1: View, v2: View): Boolean {
        val r1 = android.graphics.Rect()
        v1.getHitRect(r1)
        val r2 = android.graphics.Rect()
        v2.getHitRect(r2)
        return android.graphics.Rect.intersects(r1, r2)
    }
    private fun duplicateComponent(original: View) {
        if (project == null) return
        val container = original as? FrameLayout ?: return
        val type = container.tag as? String ?: return

        // 1. Create View (Raw)
        val newView = createComponentView(type)

        // 2. Assign ID and Index
        newView.id = View.generateViewId()
        val index = getNextIndex(type)
        componentIndices[newView.id] = index

        // 3. Apply Params (Offset)
        val params = original.layoutParams as? ConstraintLayout.LayoutParams ?: return
        val newParams = ConstraintLayout.LayoutParams(params)
        newParams.width = params.width
        newParams.height = params.height
        newView.x = original.x + 40f
        newView.y = original.y + 40f
        newView.layoutParams = newParams

        makeDraggable(newView)
        editorCanvas.addView(newView)

        // 4. Create Label
        val newLabel = TextView(this)
        newLabel.id = View.generateViewId()
        val typeName = when (type) {
             "THERMOMETER" -> "Thermometer"
             "BUTTON" -> "Button"
             "SLIDER" -> "Slider"
             "TEXT" -> "Text"
             "IMAGE" -> "Image"
             "LED" -> "LED"
             else -> type
        }
        newLabel.text = "$typeName $index"
        newLabel.tag = "LABEL_FOR_${newView.id}"
        newLabel.setTextColor(Color.WHITE)
        newLabel.textSize = 10f // Consistent size
        newLabel.gravity = Gravity.CENTER

        val lParams = ConstraintLayout.LayoutParams(newParams.width, ConstraintLayout.LayoutParams.WRAP_CONTENT)
        newLabel.x = newView.x
        newLabel.y = newView.y + newView.height + 4
        newLabel.layoutParams = lParams

        editorCanvas.addView(newLabel)

        Toast.makeText(this, "Duplicated", Toast.LENGTH_SHORT).show()
    }

    // 填充屬性面板資料
    private fun populateProperties(view: View) {
        val params = view.layoutParams
        val density = resources.displayMetrics.density
        etPropWidth.setText((params.width / density).toInt().toString()) // 顯示寬度
        etPropHeight.setText((params.height / density).toInt().toString()) // 顯示高度

        // Find the label TextView and display its text
        val labelView = findLabelView(view)
        if (labelView != null) {
            etPropName.setText(labelView.text)
        } else {
            etPropName.setText("")
        }

        if (project != null) {
            val container = view as? FrameLayout
            val type = container?.tag as? String ?: "UNKNOWN"
            val id = container?.id ?: view.id

            val isInput = (type == "BUTTON" || type == "SLIDER")
            val topicUrl = getComponentTopic(type, id, isInput)

            val prefix = if (isInput) "TX" else "RX"
            val tvTopic = findViewById<TextView>(R.id.tvPropTopic)
            if (tvTopic != null) {
                tvTopic.text = "Topic: $prefix: $topicUrl"
            }

            // v80: Load Advanced Props
            val compData = componentDataMap[view.id]
            val etPropTopicConfig = findViewById<EditText>(R.id.etPropTopicConfig)
            etPropTopicConfig.setText(compData?.topicConfig ?: "")

            // Camera Specific
            val containerCompression = findViewById<LinearLayout>(R.id.containerCompression)
            val sliderCompression = findViewById<com.google.android.material.slider.Slider>(R.id.sliderCompression)

            // Camera Specific
            if (type == "CAMERA") {
                containerCompression.visibility = View.VISIBLE
                val level = compData?.props?.get("compression")?.toFloatOrNull() ?: 3.0f
                sliderCompression.value = level
            } else {
                containerCompression.visibility = View.GONE
            }

        }

        // Setup Clone Button Only (Delete Removed)
        val btnClone = findViewById<Button>(R.id.btnClone)

        btnClone?.setOnClickListener {
            duplicateComponent(view)
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }

        // Refinement 3: Auto-Pan if component is low (Improved Buffer)
        view.post {
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            val viewY = location[1]
            val viewH = view.height

            val displayMetrics = resources.displayMetrics
            val screenHeight = displayMetrics.heightPixels
            val sheetHeight = (550 * displayMetrics.density).toInt() // v72: Increased Buffer (+150dp)

            val viewBottom = viewY + viewH
            val safeBottom = screenHeight - sheetHeight - 50 // 50px buffer

            if (viewBottom > safeBottom) {
                val scrollAmount = viewBottom - safeBottom
                editorCanvas.animate().translationY(-scrollAmount.toFloat()).setDuration(300).start()
            }
        }
    }

     // Reset Pan on Collapse
     private fun resetCanvasPan() {
         editorCanvas.animate().translationY(0f).setDuration(300).start()
     }

     // Helper function to find the label TextView in the component hierarchy
     // 輔助函式：在畫布上查找與組件關聯的標籤 TextView
     private fun findLabelView(view: View): TextView? {
         // Labels are now separate views on the canvas with tag "LABEL_FOR_{componentId}"
         // 標籤現在是畫布上的獨立 View，其 Tag 格式為 "LABEL_FOR_{componentId}"
         val targetTag = "LABEL_FOR_${view.id}"
         for (i in 0 until editorCanvas.childCount) {
             val child = editorCanvas.getChildAt(i)
             if (child is TextView && child.tag == targetTag) {
                 return child // 找到並返回
             }
         }
         return null // 未找到
     }

    // v31: Helper to refresh UI from cached states
    private fun refreshUIFromCache() {
        if (projectId == null) return

        // Loop through all components on canvas
        // This is a simplified approach. Ideally we map components to topics.
        // For now, we rely on the fact that MqttRepository holds the latest state.
        // But since components are dynamically created, we might need to query the repository
        // using the topic derived from the component tag/id.

        // Current implementation of MqttRepository.cachedStates is Topic -> Payload
        // We can iterate over components and check if their Topic has a cached value.

        // However, extracting Topic from View is tricky unless we stored it.
        // In this prototype, we constructed topics dynamically in setupConsole sending.
        // The components themselves (Button, Slider) don't store their topic reference in current code
        // (except if we added it to tag, but tag is used for TYPE).

        // If we assumed a convention like "{project}/{id}/{type}/{index}/state"
        // we could reconstruct it.

        // For v31, we will just ensure that IF we receive a message while paused (background),
        // MqttRepository updates the cache. When we resume, if we relied on LiveData,
        // it *should* receive the latest value if it's sticky.
        // MqttRepository._latestMessage is MutableLiveData. LiveData only emits *latest* value to new observers.
        // But for *multiple* topics, LiveData only holds the very last single message.
        // So MqttRepository.cachedStates is the source of truth.

        // Since we don't have a map of View -> Topic in this activity (yet),
        // we can't easily iterate views to update them from cache without more metadata.
        // For now, this function is a placeholder or can trigger a re-subscription/check.

        // In a real app, each component View would have its Topic stored in `tag` or a map.
        // We will leave this empty/placeholder for now as per v31 scope,
        // effectively relying on new messages arriving or retained messages handling.

        // Updated v31: If we want to support "Background Resume", we might want to
        // ask the Service to re-publish or re-deliver states?
        // Or simply wait for Retained messages which come on Subscribe.
    }

    // v35: Perform Full Sync (Subscribe Active, Unsubscribe Inactive)
    private fun performFullSync() {
        val currentProject = project ?: return
        val currentId = currentProject.id

        // 1. Subscribe to Current Project
        val baseTopic = "${currentProject.name.lowercase()}/$currentId/#"
        val intentSub = Intent(this, com.example.mqttpanelcraft.service.MqttService::class.java)
        intentSub.action = "SUBSCRIBE"
        intentSub.putExtra("TOPIC", baseTopic)
        startService(intentSub)

        // 2. Unsubscribe from Inactive Projects
        // Run in background to avoid UI thread block if list is huge (unlikely here but good practice)
        CoroutineScope(Dispatchers.IO).launch {
            // Give a small buffer for the subscribe to go out first
            delay(500)

            val allProjects = ProjectRepository.getAllProjects()
            for (p in allProjects) {
                if (p.id != currentId) {
                    val pTopic = "${p.name.lowercase()}/${p.id}/#"
                    val intentUnsub = Intent(this@ProjectViewActivity, com.example.mqttpanelcraft.service.MqttService::class.java)
                    intentUnsub.action = "UNSUBSCRIBE" // New Action
                    intentUnsub.putExtra("TOPIC", pTopic)
                    startService(intentUnsub)
                }
            }
            }
        }

    // v95: Listener Implementation
    override fun onMessageReceived(topic: String, payload: String) {
        runOnUiThread {
            updateComponentFromMqtt(topic, payload)
        }
    }


}

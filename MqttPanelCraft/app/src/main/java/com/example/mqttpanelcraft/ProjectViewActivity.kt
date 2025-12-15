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

// 定義主要的 Activity 類別，繼承自 AppCompatActivity 以獲得向後相容性
class ProjectViewActivity : AppCompatActivity() {

    // 宣告延遲初始化的 UI 元件變數，這些變數稍後會在 onCreate 中綁定
    private lateinit var drawerLayout: DrawerLayout // 側邊抽屜佈局
    private lateinit var editorCanvas: ConstraintLayout // 編輯區畫布，組件將被放置於此
    private lateinit var guideOverlay: AlignmentOverlayView // 用於顯示對齊輔助線的自定義 View
    private lateinit var fabMode: FloatingActionButton // 切換編輯/運行模式的浮動按鈕
    private lateinit var logAdapter: LogAdapter // 用於顯示 MQTT 日誌的適配器
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<FrameLayout> // 控制底部面板行為（展開/收起）的物件
    
    // 底部面板的容器
    private lateinit var containerLogs: LinearLayout // 包含日誌顯示區域的容器（運行模式用）
    private lateinit var containerProperties: ScrollView // 包含屬性編輯區域的容器（編輯模式用）

    // 屬性輸入欄位
    private lateinit var etPropName: TextInputEditText // 輸入組件名稱的編輯框
    private lateinit var etPropWidth: TextInputEditText // 輸入組件寬度的編輯框
    private lateinit var etPropHeight: TextInputEditText // 輸入組件高度的編輯框
    private lateinit var etPropColor: TextInputEditText // 輸入組件顏色的編輯框
    private lateinit var btnSaveProps: Button // 儲存屬性變更的按鈕
    
    // 控制台輸入欄位（用於手動發送 MQTT 訊息）
    private lateinit var etTopic: EditText // 輸入 MQTT 主題的編輯框
    private lateinit var etPayload: EditText // 輸入 MQTT 訊息內容的編輯框
    private lateinit var btnSend: Button // 發送 MQTT 訊息的按鈕

    private var isEditMode = false // 標記當前是否處於編輯模式，預設為 false (運行模式)
    private var projectId: String? = null // 儲存當前專案的 ID
    private var project: com.example.mqttpanelcraft.model.Project? = null // 儲存當前專案的資料模型
    
    private var selectedView: View? = null // 儲存當前被選中（點擊）的組件 View
    private val snapThreshold = 16f // 定義磁吸對齊的閾值（單位：dp）

    // Activity 創建時的生命週期方法
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState) // 呼叫父類別的 onCreate
        try {
            setContentView(R.layout.activity_project_view) // 設定此 Activity 的佈局檔案

            // 恢復之前的狀態（例如螢幕旋轉後）
            if (savedInstanceState != null) {
                isEditMode = savedInstanceState.getBoolean("IS_EDIT_MODE", false) // 讀取是否為編輯模式
            }

            setupUI() // 初始化 UI 元件與基本事件監聽
            setupConsole() // 初始化 MQTT 控制台相關功能
            setupSidebarInteraction() // 初始化側邊欄拖曳與搜尋功能
            setupPropertiesPanel() // 初始化屬性編輯面板
            
            // 載入專案資料
            projectId = intent.getStringExtra("PROJECT_ID") // 從 Intent 中獲取傳遞過來的專案 ID
            if (projectId != null) {
                loadProjectDetails(projectId!!) // 如果 ID 存在，載入專案詳細資訊
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
            
            // 確保 UI 反映當前的模式（編輯或運行）
            updateModeUI()
            
        } catch (e: Exception) {
            CrashLogger.logError(this, "Project View Init Failed", e) // 記錄錯誤到 CrashLogger
            finish() // 發生錯誤時結束 Activity
        }
    }
    
    // 儲存 Activity 狀態的生命週期方法（例如被系統回收或旋轉螢幕前）
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState) // 呼叫父類別方法
        outState.putBoolean("IS_EDIT_MODE", isEditMode) // 儲存當前模式
        if (::drawerLayout.isInitialized) {
            outState.putBoolean("IS_DRAWER_OPEN", drawerLayout.isDrawerOpen(GravityCompat.START)) // 儲存抽屜開啟狀態
        }
    }
    
    // Activity 可見時的生命週期方法
    override fun onStart() {
        super.onStart() // 呼叫父類別方法
        setupWindowInsets() // 設定視窗邊襯區（處理狀態列、導航列遮擋問題）

        setupDrawerListener() // 設定側邊抽屜的滑動監聽器
    }
    
    // 設定視窗邊襯區的方法，確保內容不會被系統 UI 遮擋
    private fun setupWindowInsets() { // 設定視窗邊襯區
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView.findViewById(android.R.id.content)) { view, insets -> // 為內容視圖設定邊襯區監聽器
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()) // 獲取系統列的尺寸
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom) // 為內容視圖設定內邊距
            WindowInsetsCompat.CONSUMED // 表示邊襯區已被處理
        }
    }

    // 載入專案詳細資訊並設定標題
    private fun loadProjectDetails(id: String) {
        // ProjectRepository is a singleton object
        project = ProjectRepository.getProjectById(id)
        if (project != null) {
            supportActionBar?.title = project!!.name
        } else {
            Toast.makeText(this, "Project not found", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
    
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
        
        // 點擊底部面板標題欄切換展開/收起
        findViewById<View>(R.id.bottomSheetHeader).setOnClickListener {
            if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_COLLAPSED) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            } else {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            }
        }

        // Set FAB Mode Click Listener
        fabMode.setOnClickListener {
            toggleMode()
        }

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar) // 獲取 Toolbar 物件
        setSupportActionBar(toolbar) // 設定此 Toolbar 為 Activity 的 ActionBar
    
        // 添加漢堡選單圖標（導航圖標）
        toolbar.setNavigationIcon(R.drawable.ic_menu) // 設定導航圖標資源
        toolbar.setNavigationOnClickListener {  // 設定導航（漢堡）按鈕的點擊監聽器
            // 允許在底部面板展開時打開抽屜（如果需要的話先收起底下）
            if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) { // 如果底部面板已展開
                 bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED // 將其收起
            }
            
            // 切換側邊抽屜的開啟/關閉狀態
            if (drawerLayout.isDrawerOpen(androidx.core.view.GravityCompat.START)) { // 如果抽屜已開啟
                drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START) // 關閉抽屜
            } else {
                drawerLayout.openDrawer(androidx.core.view.GravityCompat.START) // 開啟抽屜
            }
        }
        
        // 設定按鈕（自定義 View，位於 Toolbar 上）
        val btnSettings = findViewById<ImageView>(R.id.btnSettings) // 獲取設定按鈕
        btnSettings.setOnClickListener { // 設定設定按鈕的點擊監聽器
             if (projectId != null) { // 確保專案 ID 存在
                try {
                     // 使用反射方式啟動 SetupActivity（因為模組/包名結構可能變動）
                     val intent = android.content.Intent(this, Class.forName("com.example.mqttpanelcraft.SetupActivity"))
                     intent.putExtra("PROJECT_ID", projectId) // 傳遞專案 ID
                     startActivity(intent) // 啟動設定頁面
                     finish() // 結束當前頁面，避免返回時狀態混亂（或根據需求保留）
                } catch (e: Exception) {
                    android.widget.Toast.makeText(this, "Setup Activity not found: ${e.message}", android.widget.Toast.LENGTH_SHORT).show() // 顯示錯誤訊息
                }
            }
        }
        
        // 運行模式側邊欄動作（例如開關網格、深色模式）
        try {
            val backgroundGrid = findViewById<View>(R.id.backgroundGrid) // 獲取背景網格 View
            
            // 顯示網格開關（編輯模式）
            val switchGridToggle = findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchGridToggle)
            switchGridToggle?.setOnCheckedChangeListener { _, isChecked ->
                backgroundGrid?.visibility = if (isChecked) View.VISIBLE else View.GONE
            }

            // 顯示網格開關（運行模式）
            val switchGridToggleRunMode = findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchGridToggleRunMode) // 獲取運行模式的網格開關
            
            // 設定運行模式網格開關的監聽器
            switchGridToggleRunMode?.setOnCheckedChangeListener { _, isChecked ->
                backgroundGrid?.visibility = if (isChecked) View.VISIBLE else View.GONE // 根據開關狀態顯示或隱藏網格
            }
            
            // 深色模式開關
            val switchDarkMode = findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchDarkMode) // 獲取深色模式開關
            
            // 設定初始狀態（讀取當前系統深色模式設定）
            val currentNightMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK // 獲取當前夜間模式掩碼
            switchDarkMode?.isChecked = (currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES) // 如果是夜間模式，開關設為開啟
 
            // 設定深色模式開關的監聽器
            switchDarkMode?.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES) // 切換到深色模式
                } else {
                    androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO) // 切換到淺色模式
                }
                // 不要關閉抽屜，讓 onSaveInstanceState 處理狀態持久化（Activity 會重建）
            }
        } catch (e: Exception) {
            // Log or ignore errors in dynamic UI setup
        }
        // 垃圾桶拖曳監聽器（刪除組件功能）
        val binTrash = findViewById<ImageView>(R.id.binTrash) // 獲取垃圾桶 ImageView
        binTrash.setOnDragListener { v, event -> // 設定拖曳監聽器
            when (event.action) { // 根據拖曳事件的動作類型進行處理
                DragEvent.ACTION_DRAG_STARTED -> true // 拖曳開始，返回 true 表示接收拖曳事件
                DragEvent.ACTION_DRAG_ENTERED -> { // 當拖曳的組件進入垃圾桶區域時
                    (v as? ImageView)?.setColorFilter(Color.RED) // 將垃圾桶圖標變更為紅色，表示高亮
                    true // 返回 true 表示已處理此事件
                }
                DragEvent.ACTION_DRAG_EXITED -> { // 當拖曳的組件離開垃圾桶區域時
                    (v as? ImageView)?.clearColorFilter() // 清除垃圾桶圖標的顏色濾鏡
                    (v as? ImageView)?.setColorFilter(Color.WHITE) // 將垃圾桶圖標恢復為白色
                    true // 返回 true 表示已處理此事件
                }
                DragEvent.ACTION_DROP -> { // 當拖曳的組件被放置在垃圾桶上時（執行刪除動作）
                     (v as? ImageView)?.clearColorFilter() // 清除垃圾桶圖標的顏色濾鏡
                     (v as? ImageView)?.setColorFilter(Color.WHITE) // 將垃圾桶圖標恢復為白色
                     
                     val clipData = event.clipData // 獲取拖曳事件中包含的資料
                     if (clipData != null && clipData.itemCount > 0) { // 如果拖曳資料不為空且包含項目
                         val idStr = clipData.getItemAt(0).text.toString() // 獲取拖曳資料中的第一個項目，並將其轉換為字串（預期為組件ID）
                         // 檢查是否為有效 ID（View ID 通常為整數，這裡以字串形式傳遞）
                         try {
                             val viewId = idStr.toInt() // 嘗試將字串 ID 轉換為整數
                             val component = editorCanvas.findViewById<View>(viewId) // 在編輯畫布上根據 ID 查找對應的組件
                             if (component != null) { // 如果找到了該組件
                                  // 顯示確認刪除的對話框
                                  AlertDialog.Builder(this) // 創建一個 AlertDialog.Builder 實例
                                      .setTitle("Delete Component") // 設定對話框標題
                                      .setMessage("Are you sure you want to delete this component?") // 設定對話框訊息
                                      .setPositiveButton("Delete") { _, _ -> // 設定「刪除」按鈕及其點擊事件
                                          // 首先查找並移除組件的標籤 (Label)
                                          val labelView = findLabelView(component) // 查找與該組件關聯的標籤 View
                                          if (labelView != null) { // 如果找到了標籤 View
                                              editorCanvas.removeView(labelView) // 從編輯畫布中移除標籤 View
                                          }
                                          // 移除組件本身
                                          editorCanvas.removeView(component) // 從編輯畫布中移除組件 View
                                          guideOverlay.clear() // 清除對齊輔助線
                                      }
                                      .setNegativeButton("Cancel", null) // 設定「取消」按鈕（不執行任何操作）
                                      .show() // 顯示對話框
                             }
                         } catch (e: NumberFormatException) { // 捕獲 NumberFormatException，表示字串無法轉換為整數
                             // 如果不是有效 ID（例如，可能是從側邊欄拖出的新組件，而不是畫布上的現有組件），則忽略此拖曳事件
                         }
                     }
                     true // Drop event handled
                }
                else -> false
            }
            true
        }
    }


    // 初始化屬性編輯面板
    private fun setupPropertiesPanel() {
        etPropName = findViewById(R.id.etPropName) // 獲取名稱輸入框
        etPropWidth = findViewById(R.id.etPropWidth) // 獲取寬度輸入框
        etPropHeight = findViewById(R.id.etPropHeight) // 獲取高度輸入框
        etPropColor = findViewById(R.id.etPropColor) // 獲取顏色輸入框
        btnSaveProps = findViewById(R.id.btnSaveProps) // 獲取儲存按鈕
        
        etPropColor.setOnClickListener { showGradientColorPicker() } // 設定顏色輸入框的點擊事件（顯示顏色選擇器）
        etPropColor.focusable = View.FOCUSABLE_AUTO // 自動處理焦點
        etPropColor.isFocusableInTouchMode = false // 禁止觸控模式下取得焦點（強制使用點擊事件）
        
        btnSaveProps.setOnClickListener { // 設定儲存按鈕的點擊事件
            selectedView?.let { view -> // 確保有選中的組件 View
                try {
                    val w = etPropWidth.text.toString().toIntOrNull() // 嘗試將寬度文字轉為整數
                    val h = etPropHeight.text.toString().toIntOrNull() // 嘗試將高度文字轉為整數
                    if (w != null && h != null) { // 如果寬高都有效
                        val params = view.layoutParams as ConstraintLayout.LayoutParams // 獲取佈局參數
                        val density = resources.displayMetrics.density // 獲取螢幕密度
                        params.width = (w * density).toInt() // 設定寬度（dp 轉 px）
                        params.height = (h * density).toInt() // 設定高度（dp 轉 px）
                        view.layoutParams = params // 應用新的佈局參數
                    }
                    
                    val colorStr = etPropColor.text.toString() // 獲取顏色文字
                    if (colorStr.isNotEmpty()) {
                        try {
                             view.setBackgroundColor(Color.parseColor(colorStr)) // 嘗試解析並設定背景顏色
                        } catch (e: Exception) {} // 解析失敗則忽略
                    }
                    
                    // 更新關聯的標籤文字
                    val labelView = findLabelView(view) // 查找標籤 View
                    if (labelView != null) {
                        labelView.text = etPropName.text.toString() // 更新標籤文字
                    }
                    
                    Toast.makeText(this, "Updated", Toast.LENGTH_SHORT).show() // 顯示更新成功訊息
                } catch (e: Exception) {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show() // 顯示錯誤訊息
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
             val topic = etTopic.text.toString() // 獲取主題
            val payload = etPayload.text.toString() // 獲取訊息內容
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
                    Toast.makeText(this, "Not Connected.", Toast.LENGTH_SHORT).show() // 未連線提示
                }
            }
        }
    }
    
    // 檢查 MQTT 連線狀態並記錄初始日誌
    private fun checkMqttConnection() {
        val client = MqttRepository.mqttClient
        if (client == null || !client.isConnected) {
            MqttRepository.addLog("System: Editor opened without active MQTT connection.", "")
        } else {
            MqttRepository.addLog("System: Connected to broker.", "")
        }
    }

    // 切換編輯/運行模式
    private fun toggleMode() {
        isEditMode = !isEditMode // 反轉模式旗標
        updateModeUI() // 更新 UI 顯示
    }

    // 根據目前的 isEditMode 更新 UI 介面
    private fun updateModeUI() {
        val sidebarEditMode = findViewById<View>(R.id.sidebarEditMode) // 獲取編輯模式側邊欄
        val sidebarRunMode = findViewById<View>(R.id.sidebarRunMode) // 獲取運行模式側邊欄
        val backgroundGrid = findViewById<View>(R.id.backgroundGrid) // 獲取背景網格

        if (isEditMode) { // 如果是編輯模式
             fabMode.setImageResource(android.R.drawable.ic_media_play) // 按鈕圖示改為「播放」
             editorCanvas.background = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.bg_grid_pattern) // 顯示網格背景
             
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
             switchGridToggle?.isChecked = (backgroundGrid?.visibility == View.VISIBLE)
             
             Toast.makeText(this, "Edit Mode", Toast.LENGTH_SHORT).show()
        } else { // 如果是運行模式
             fabMode.setImageResource(android.R.drawable.ic_menu_edit) // 按鈕圖示改為「編輯」
             editorCanvas.background = null // 移除背景網格
             guideOverlay.clear() // 清除對齊線
             guideOverlay.visibility = View.GONE // 隱藏對齊線圖層
             
             // 運行模式下，解鎖側邊欄以便使用者使用匯出/匯入功能
             drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.START)
             
             sidebarEditMode?.visibility = View.GONE // 隱藏組件側邊欄
             sidebarRunMode?.visibility = View.VISIBLE // 顯示運行側邊欄
             
             containerLogs.visibility = View.VISIBLE // 顯示日誌面板
             containerProperties.visibility = View.GONE // 隱藏屬性面板
             
             // 同步運行模式的網格開關狀態
             val switchGridToggleRunMode = findViewById<SwitchMaterial>(R.id.switchGridToggleRunMode)
             switchGridToggleRunMode?.isChecked = (backgroundGrid?.visibility == View.VISIBLE)
             
             Toast.makeText(this, "Run Mode", Toast.LENGTH_SHORT).show()
        }
        
        // Disable/Enable interaction with components based on mode
        setComponentsInteractive(!isEditMode)
    }
    
    // Helper to enable/disable interaction for components
    private fun setComponentsInteractive(enable: Boolean) {
        for (i in 0 until editorCanvas.childCount) {
             val child = editorCanvas.getChildAt(i)
             // Only target component containers (FrameLayouts with our specific tag or structure)
             // Checking if it has the component_border background is one way, or checking tags.
             // Our components are FrameLayouts.
             if (child is FrameLayout && child.childCount > 0) {
                 val innerView = child.getChildAt(0)
                 if (innerView is Button || innerView is com.google.android.material.slider.Slider) {
                     innerView.isEnabled = enable
                     innerView.isClickable = enable
                     innerView.isFocusable = enable
                 }
                 // For custom views or others, isEnabled often propagates or handles visual state
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



        // 設定組件搜尋欄
        val etSearchComponents = findViewById<EditText>(R.id.etSearchComponents) // 獲取搜尋輸入框

        // 設定觸控監聽器，偵測清除按鈕（右側圖示）的點擊
        etSearchComponents?.setOnTouchListener { v, event -> // 為搜尋輸入框設定觸控監聽器
            if (event.action == MotionEvent.ACTION_UP) { // 當手指抬起時
                // 檢查點擊位置是否在右側圖示（drawableEnd）範圍內
                if (event.rawX >= (etSearchComponents.right - etSearchComponents.compoundDrawables[2].bounds.width())) { // 判斷點擊是否在清除圖示區域
                    etSearchComponents.text.clear() // 清空搜尋文字
                    v.performClick() // 觸發標準點擊事件（為了無障礙功能）
                    return@setOnTouchListener true // 已處理事件
                }
            }
            false // 其他位置點擊不處理
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
            newView.x = x - (wPx / 2) // 設定 X 座標（居中放下點）
            newView.y = y - (hPx / 2) // 設定 Y 座標（居中放下點）
            
            // v16: Unique ID Fix
            newView.id = View.generateViewId() // 生成唯一的 View ID
            
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
                
                // Fix: Map tags to human readable names
                text = when (tag) {
                    "THERMOMETER" -> "Level Indicator"
                    "BUTTON" -> "Button"
                    "SLIDER" -> "Slider"
                    "TEXT" -> "Text"
                    "IMAGE" -> "Image"
                    else -> tag
                }
                
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
            "BUTTON" -> Button(context).apply { text = "BTN" } // 按鈕
            "TEXT" -> TextView(context).apply { text = "Text"; gravity = Gravity.CENTER } // 文字
            "IMAGE" -> ImageView(context).apply { 
                setImageResource(android.R.drawable.ic_menu_gallery) // 圖片
                scaleType = ImageView.ScaleType.FIT_CENTER // 縮放模式
            }
            "SLIDER" -> com.google.android.material.slider.Slider(context).apply { 
                valueFrom = 0f
                valueTo = 100f
                value = 50f
                stepSize = 1f
            } // 滑塊
            "LED" -> View(context).apply { // LED 指示燈
                // Use GradientDrawable for reliable Oval shape
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(Color.RED)
                    setStroke(2, Color.DKGRAY) // Optional: Add a subtle border to the LED itself
                }
            }
            "THERMOMETER" -> ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply { 
                max = 100
                progress = 75
                progressTintList = android.content.res.ColorStateList.valueOf(Color.GREEN)
            } // 電量/水平指示 (Level Indicator)
            else -> TextView(context).apply { text = tag } // 默認文字
        }
        
        // 確保內部 View 填滿容器
        val params = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        view.layoutParams = params
        
        container.addView(view) // 將內部 View 添加到容器
        container.tag = tag // 設定容器的 Tag 為組件類型
        
        return container // 返回容器（作為組件）
    }

    // 讓 View 變為可拖曳，並設定點擊監聽器
    private fun makeDraggable(view: View) {
        view.setOnClickListener {
            if (!isEditMode) return@setOnClickListener // 僅在編輯模式下響應
            
            // 處理選取變更
            selectedView?.setBackgroundResource(R.drawable.component_border) // 還原之前選取組件的背景
            selectedView = view // 更新選取組件
            // 這裡可以更改選取視圖的背景以顯示選取狀態（例如變色邊框），目前暫時保持原樣
            // view.setBackgroundResource(R.drawable.component_border_selected) 
            
            // 彈出屬性面板
            if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_COLLAPSED) {
                populateProperties(view) // 填充屬性資料
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED // 展開底部面板
            } else {
                 populateProperties(view) // 如果已經展開，刷新資料
            }
        }
        
        view.setOnLongClickListener {
            if (!isEditMode) return@setOnLongClickListener false // 僅在編輯模式下響應長按
             val data = ClipData.newPlainText("id", view.id.toString()) // 傳遞 View ID
             val shadow = View.DragShadowBuilder(view) // 創建拖曳陰影
             view.startDragAndDrop(data, shadow, view, 0) // 開始拖曳
             view.visibility = View.INVISIBLE // 隱藏原 View
             
             // Also hide the label during drag
             // 在拖曳期間也隱藏關聯的標籤
             val labelView = findLabelView(view)
             labelView?.visibility = View.INVISIBLE
             
             true // 已處理長按事件
        }
    }

    // 檢查對齊線並繪製輔助線
    private fun checkAlignment(x: Float, y: Float, currentView: View) {
        guideOverlay.clear() // 清除之前的線
        val threshold = snapThreshold * resources.displayMetrics.density // 計算閾值
        
        for (i in 0 until editorCanvas.childCount) { // 遍歷所有元件
            val other = editorCanvas.getChildAt(i)
            if (other == currentView || other == guideOverlay) continue // 跳過自己和 Overlay
            
            val otherCx = other.x + other.width/2 // 計算其他元件中心 X
            val otherCy = other.y + other.height/2 // 計算其他元件中心 Y
            
            // 檢查 X 軸對齊
            if (kotlin.math.abs(x - otherCx) < threshold) {
                guideOverlay.addLine(otherCx, 0f, otherCx, editorCanvas.height.toFloat()) // 繪製垂直線
            }
            // 檢查 Y 軸對齊
            if (kotlin.math.abs(y - otherCy) < threshold) {
                guideOverlay.addLine(0f, otherCy, editorCanvas.width.toFloat(), otherCy) // 繪製水平線
            }
        }
    }

    // 計算吸附位置
    private fun calculateSnap(rawX: Float, rawY: Float, w: Int, h: Int, currentView: View): Point? {
        var bestX = rawX - w/2 // 預設為原始位置（中心點修正後）
        var bestY = rawY - h/2
        var snapped = false
        val threshold = snapThreshold * resources.displayMetrics.density
        
        for (i in 0 until editorCanvas.childCount) {
            val other = editorCanvas.getChildAt(i)
            if (other == currentView || other == guideOverlay) continue
            
            val otherCx = other.x + other.width/2
            val otherCy = other.y + other.height/2
            
            // 檢查是否在大致位置附近，如果是則吸附
            if (kotlin.math.abs(rawX - otherCx) < threshold) {
                bestX = otherCx - w/2
                snapped = true
            }
            if (kotlin.math.abs(rawY - otherCy) < threshold) {
                bestY = otherCy - h/2
                snapped = true
            }
        }
        return if (snapped) Point(bestX.toInt(), bestY.toInt()) else null // 返回吸附後的點或 null
    }
    
     // 填充屬性面板資料
     private fun populateProperties(view: View) {
         val params = view.layoutParams
        val density = resources.displayMetrics.density
        etPropWidth.setText((params.width / density).toInt().toString()) // 顯示寬度
        etPropHeight.setText((params.height / density).toInt().toString()) // 顯示高度
        
        // Find the label TextView and display its text
        // 查找關聯的標籤 TextView 並顯示其文字
        val labelView = findLabelView(view)
        if (labelView != null) {
            etPropName.setText(labelView.text)
        } else {
            etPropName.setText("") // 如果沒有標籤，清空名稱
        }
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
}

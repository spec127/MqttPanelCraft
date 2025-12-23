package com.example.mqttpanelcraft

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mqttpanelcraft.adapter.ProjectAdapter
import com.example.mqttpanelcraft.data.ProjectRepository
import com.example.mqttpanelcraft.databinding.ActivityDashboardBinding
import com.example.mqttpanelcraft.model.Project

import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import com.example.mqttpanelcraft.utils.CrashLogger
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import android.widget.AutoCompleteTextView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.launch
import java.util.Locale
import kotlinx.coroutines.*

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var projectAdapter: ProjectAdapter
    private var isGuest = false

    // v85: Sorting State
    // 0: Custom, 1: Name, 2: Date, 3: Last Opened
    private var currentSortMode = 3 // Default: Last Opened (User feedback implies preference for simply finding projects)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            binding = ActivityDashboardBinding.inflate(layoutInflater)
            setContentView(binding.root)

            isGuest = intent.getBooleanExtra("IS_GUEST", false)

            setupToolbar()
            setupDrawer()
            setupRecyclerView()
            setupFab()

            // Fix Status Bar Overlap
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.drawerLayout) { view, insets ->
                val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                // Apply padding to the content container inside Drawer (CoordinatorLayout is usually first child)
                val content = binding.drawerLayout.getChildAt(0)
                content.setPadding(0, bars.top, 0, 0)
                androidx.core.view.WindowInsetsCompat.CONSUMED
            }

            // Initialize Data
            ProjectRepository.initialize(this)

            // Initialize Ads
            com.example.mqttpanelcraft.utils.AdManager.initialize(this)
            com.example.mqttpanelcraft.utils.AdManager.loadBannerAd(this, binding.bannerAdContainer)

        } catch (e: Exception) {
            CrashLogger.logError(this, "Dashboard Init Failed", e)
        }
    }
    
    override fun onResume() {
        super.onResume()
        loadProjects()
        startConnectionCheck()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        // Enable hamburger icon click
        binding.toolbar.setNavigationOnClickListener {
             binding.drawerLayout.openDrawer(GravityCompat.START)
        }
    }

    private fun setupDrawer() {
        binding.navigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_settings -> showSettingsBottomSheet()
                R.id.nav_about -> Toast.makeText(this, "About MqttPanelCraft v1.0", Toast.LENGTH_SHORT).show()
                // Language/Dark Mode removed from here as per v3 plan
            }
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }

    private fun showSettingsBottomSheet() {
        val bottomSheetDialog = BottomSheetDialog(this)
        bottomSheetDialog.setContentView(R.layout.bottom_sheet_settings)
        
        val switchDarkMode = bottomSheetDialog.findViewById<SwitchMaterial>(R.id.switchDarkMode)
        val dropdownLanguage = bottomSheetDialog.findViewById<AutoCompleteTextView>(R.id.dropdownLanguage)
        
        // Setup Dark Mode Switch
        val currentNightMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        switchDarkMode?.isChecked = currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
        switchDarkMode?.setOnCheckedChangeListener { _, isChecked ->
            val mode = if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            AppCompatDelegate.setDefaultNightMode(mode)
            // No need to restart immediately, delegate handles it or wait for recreation
        }

        // Setup Language Dropdown
        val languages = listOf("English", "繁體中文")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, languages)
        dropdownLanguage?.setAdapter(adapter)
        
        // Set current selection
        val currentLang = if (resources.configuration.locales[0].language == "zh") "繁體中文" else "English"
        dropdownLanguage?.setText(currentLang, false)
        
        // Setup Sort RadioGroup
        val radioGroupSort = bottomSheetDialog.findViewById<android.widget.RadioGroup>(R.id.radioGroupSort)

        // Initialize radio state (check based on simple logic or leave unchecked? Let's check "Name" by default if user asks, but strictly we don't know state.
        // Actually, user just wants to SORT.
        // We can leave checks alone or manage currentSortMode as "Last Action" indicator if we want.
        // For simplicity:
        when (currentSortMode) {
            1 -> radioGroupSort?.check(R.id.rbSortNameAsc) // Default to Asc?
            2 -> radioGroupSort?.check(R.id.rbSortDateNew)
            3 -> radioGroupSort?.check(R.id.rbSortLastOpened)
        }

        radioGroupSort?.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbSortNameAsc -> {
                    ProjectRepository.sortProjects(compareBy { it.name })
                    currentSortMode = 1
                }
                R.id.rbSortNameDesc -> {
                    ProjectRepository.sortProjects(compareByDescending { it.name })
                    currentSortMode = 1
                }
                R.id.rbSortDateNew -> {
                    ProjectRepository.sortProjects(compareByDescending { it.createdAt })
                    currentSortMode = 2
                }
                R.id.rbSortDateOld -> {
                    ProjectRepository.sortProjects(compareBy { it.createdAt })
                    currentSortMode = 2
                }
                R.id.rbSortLastOpened -> {
                    ProjectRepository.sortProjects(compareByDescending { it.lastOpenedAt })
                    currentSortMode = 3
                }
            }
            loadProjects() // Reload list (now sorted in Repo)
        }

        dropdownLanguage?.setOnItemClickListener { _, _, position, _ ->
            val selected = languages[position]
            if (selected != currentLang) {
                val localeCode = if (selected == "繁體中文") "zh" else "en"
                val regionCode = if (selected == "繁體中文") "TW" else "US"
                setLocale(localeCode, regionCode)
                bottomSheetDialog.dismiss()
            }
        }
        
        bottomSheetDialog.show()
    }

    private fun setLocale(languageCode: String, countryCode: String) {
        val locale = Locale(languageCode, countryCode)
        Locale.setDefault(locale)
        val config = resources.configuration
        config.setLocale(locale)
        createConfigurationContext(config)
        resources.updateConfiguration(config, resources.displayMetrics)

        // Restart Activity
        finish()
        startActivity(intent)
    }

    private fun setupRecyclerView() {
        projectAdapter = ProjectAdapter(emptyList(), 
            onProjectClick = { project ->
                // Update Last Opened
                project.lastOpenedAt = System.currentTimeMillis()
                ProjectRepository.updateProject(project) // Save timestamp

                // On Item Click -> Open Project View or WebView
                Toast.makeText(this, "Opening ${project.name}...", Toast.LENGTH_SHORT).show()
                val targetActivity = if (project.type == com.example.mqttpanelcraft.model.ProjectType.WEBVIEW) {
                    WebViewActivity::class.java
                } else {
                    ProjectViewActivity::class.java
                }

                val intent = Intent(this, targetActivity)
                intent.putExtra("PROJECT_ID", project.id)
                startActivity(intent)
            },
            onMenuClick = { project, action ->
                if (action == "EDIT") {
                    val intent = Intent(this, SetupActivity::class.java)
                    intent.putExtra("PROJECT_ID", project.id)
                    startActivity(intent)
                } else if (action == "DELETE") {
                    AlertDialog.Builder(this)
                        .setTitle("Delete Project")
                        .setMessage("Are you sure you want to delete '${project.name}'?")
                        .setPositiveButton("Delete") { _, _ ->
                            try {
                                ProjectRepository.deleteProject(this@DashboardActivity, project.id)
                                loadProjects() // Refresh list
                                Toast.makeText(this, "Project deleted", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                CrashLogger.logError(this, "Delete Failed", e)
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
        )
        binding.rvProjects.apply {
            layoutManager = LinearLayoutManager(this@DashboardActivity)
            adapter = projectAdapter
        }

        // v85: Drag & Drop (Project Reordering) - Restored
        val itemTouchHelper = androidx.recyclerview.widget.ItemTouchHelper(object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
            androidx.recyclerview.widget.ItemTouchHelper.UP or androidx.recyclerview.widget.ItemTouchHelper.DOWN,
            0
        ) {
            override fun onMove(recyclerView: androidx.recyclerview.widget.RecyclerView, viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder, target: androidx.recyclerview.widget.RecyclerView.ViewHolder): Boolean {
                val fromPos = viewHolder.adapterPosition
                val toPos = target.adapterPosition

                try {
                     ProjectRepository.swapProjects(fromPos, toPos)
                     projectAdapter.notifyItemMoved(fromPos, toPos)
                     return true
                } catch (e: Exception) { return false }
            }

            override fun onSwiped(viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder, direction: Int) {}
        })
        itemTouchHelper.attachToRecyclerView(binding.rvProjects)
    }

    private fun setupFab() {
        binding.fabAddProject.setOnClickListener {
            val intent = Intent(this, SetupActivity::class.java)
            startActivity(intent)
        }
    }

    private fun loadProjects() {
        try {
            val projects = ProjectRepository.getAllProjects()

            // v85: Sorting is now persistent in Repository (Action-based)
            // No need to sort here dynamically.

            // v38: Don't just set data, allow connection check to update it.
            // But we need initial data.
            projectAdapter.updateData(projects)

            if (projects.isEmpty()) {
                binding.emptyState.visibility = View.VISIBLE
                binding.rvProjects.visibility = View.GONE
            } else {
                binding.emptyState.visibility = View.GONE
                binding.rvProjects.visibility = View.VISIBLE
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error loading projects: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // v38: Dashboard Connectivity Check (Request 4)
    private var connectionJob: kotlinx.coroutines.Job? = null
    private val dashboardScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)

    override fun onPause() {
        super.onPause()
        stopConnectionCheck()
    }

    private fun startConnectionCheck() {
        stopConnectionCheck()
        connectionJob = dashboardScope.launch {
            while (isActive) {
                checkAllProjectsConnection()
                kotlinx.coroutines.delay(10000) // Check every 10s
            }
        }
    }

    private fun stopConnectionCheck() {
        connectionJob?.cancel()
        connectionJob = null
    }

    private suspend fun checkAllProjectsConnection() {
        val currentProjects = ProjectRepository.getAllProjects() // Get fresh list
        if (currentProjects.isEmpty()) return

        val updatedList = currentProjects.map { project ->
             val isOnline = checkBrokerConnectivity(project.broker, project.port)
             project.copy(isConnected = isOnline)
        }

        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
             projectAdapter.updateData(updatedList)
        }
    }

    private fun checkBrokerConnectivity(broker: String, port: Int): Boolean {
        return try {
            java.net.Socket().use { socket ->
                socket.connect(java.net.InetSocketAddress(broker, port), 2000) // 2s timeout
                true
            }
        } catch (e: Exception) {
            false
        }
    }
}

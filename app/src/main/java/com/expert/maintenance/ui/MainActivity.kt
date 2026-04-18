package com.expert.maintenance.ui

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.expert.maintenance.R
import com.expert.maintenance.adapters.InterventionAdapter
import com.expert.maintenance.data.AppDatabase
import com.expert.maintenance.data.SyncManager
import com.expert.maintenance.data.local.dao.InterventionDao
import com.expert.maintenance.data.local.entity.Intervention
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.google.android.material.textview.MaterialTextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import com.expert.maintenance.ui.details.InterventionDetailsActivity

/**
 * Main Activity - Displays interventions for the selected day
 * Features:
 * - Date navigation (previous/next day)
 * - Interventions list with completion checkbox
 * - Navigation drawer with menu options
 * - Manual synchronization trigger
 */
class MainActivity : AppCompatActivity() {

    // UI Components
    private lateinit var toolbar: MaterialToolbar
    private lateinit var navigationView: NavigationView
    private lateinit var drawerToggle: ActionBarDrawerToggle
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: InterventionAdapter
    private lateinit var tvCurrentDate: MaterialTextView
    private lateinit var tvInterventionsCount: MaterialTextView
    private lateinit var layoutEmptyState: View
    private lateinit var fabSync: FloatingActionButton

    // Navigation buttons
    private lateinit var btnPreviousDay: View
    private lateinit var btnNextDay: View

    // Data
    private lateinit var interventionDao: InterventionDao
    private var currentDate: Calendar = Calendar.getInstance()
    private var employeeId: Int = 0
    private val dateFormat = SimpleDateFormat("dd MMMM yyyy", Locale.FRENCH)
    private val databaseDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.FRENCH)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize database
        interventionDao = AppDatabase.getDatabase(this).interventionDao()

        // Get employee ID from preferences
        val prefs = getSharedPreferences("expert_maintenance_prefs", MODE_PRIVATE)
        employeeId = prefs.getInt("current_employee_id", 0)

        if (employeeId == 0) {
            // No employee logged in, redirect to login
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // Initialize UI
        initViews()
        setupToolbar()
        setupNavigationDrawer()
        setupRecyclerView()
        setupDateNavigation()
        setupFab()
        updateDateDisplay()
        loadInterventions()
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        navigationView = findViewById(R.id.nav_view)
        recyclerView = findViewById(R.id.recycler_interventions)
        tvCurrentDate = findViewById(R.id.tv_current_date)
        tvInterventionsCount = findViewById(R.id.tv_interventions_count)
        layoutEmptyState = findViewById(R.id.layout_empty_state)
        fabSync = findViewById(R.id.fab_sync)
        btnPreviousDay = findViewById(R.id.btn_previous_day)
        btnNextDay = findViewById(R.id.btn_next_day)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
    }

    private fun setupNavigationDrawer() {
        drawerToggle = ActionBarDrawerToggle(
            this,
            findViewById(R.id.drawer_layout),
            toolbar,
            R.string.nav_interventions,
            R.string.nav_deconnexion
        )
        findViewById<androidx.drawerlayout.widget.DrawerLayout>(R.id.drawer_layout).addDrawerListener(drawerToggle)
        drawerToggle.syncState()

        // Handle navigation menu item clicks
        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_interventions -> {
                    // Already on this screen
                    Toast.makeText(this, "Interventions", Toast.LENGTH_SHORT).show()
                }
                R.id.nav_interventions_to_assign -> {
                    Toast.makeText(this, "Interventions à assigner", Toast.LENGTH_SHORT).show()
                    // TODO: Navigate to assignments screen
                }
                R.id.nav_messages -> {
                    Toast.makeText(this, "Messages", Toast.LENGTH_SHORT).show()
                    // TODO: Navigate to messages screen
                }
                R.id.nav_client -> {
                    Toast.makeText(this, "Client", Toast.LENGTH_SHORT).show()
                    // TODO: Navigate to clients screen
                }
                R.id.nav_adresses -> {
                    Toast.makeText(this, "Adresses", Toast.LENGTH_SHORT).show()
                    // TODO: Navigate to addresses screen
                }
                R.id.nav_parametres -> {
                    Toast.makeText(this, "Paramètres", Toast.LENGTH_SHORT).show()
                    // TODO: Create SettingsActivity
                    // startActivity(Intent(this, SettingsActivity::class.java))
                }
                R.id.nav_a_propos -> {
                    Toast.makeText(this, "À propos", Toast.LENGTH_SHORT).show()
                    // TODO: Show about dialog
                }
                R.id.nav_deconnexion -> {
                    showLogoutConfirmation()
                }
            }
            findViewById<androidx.drawerlayout.widget.DrawerLayout>(R.id.drawer_layout).closeDrawers()
            true
        }
    }

    private fun setupRecyclerView() {
        adapter = InterventionAdapter(
            onInterventionClick = { intervention ->
                openInterventionDetails(intervention)
            },
            onCompletionToggle = { intervention, isChecked ->
                confirmCompletionToggle(intervention, isChecked)
            }
        )

        // Click handling is done through the adapter's onInterventionClick callback
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupDateNavigation() {
        btnPreviousDay.setOnClickListener {
            currentDate.add(Calendar.DAY_OF_YEAR, -1)
            updateDateDisplay()
            loadInterventions()
        }

        btnNextDay.setOnClickListener {
            currentDate.add(Calendar.DAY_OF_YEAR, 1)
            updateDateDisplay()
            loadInterventions()
        }

        // Long press to go to today
        tvCurrentDate.setOnLongClickListener {
            currentDate = Calendar.getInstance()
            updateDateDisplay()
            loadInterventions()
            true
        }

        // Click on date to open date picker for quick navigation
        tvCurrentDate.setOnClickListener {
            showDatePicker()
        }
    }

    private fun showDatePicker() {
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                currentDate.set(Calendar.YEAR, year)
                currentDate.set(Calendar.MONTH, month)
                currentDate.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                updateDateDisplay()
                loadInterventions()
                Toast.makeText(this, "Date sélectionnée: $dayOfMonth/${month + 1}/$year", Toast.LENGTH_SHORT).show()
            },
            currentDate.get(Calendar.YEAR),
            currentDate.get(Calendar.MONTH),
            currentDate.get(Calendar.DAY_OF_MONTH)
        ).apply {
            // Optionally limit to past dates only
            // datePicker.maxDate = System.currentTimeMillis()
        }.show()
    }

    private fun setupFab() {
        fabSync.setOnClickListener {
            performSync()
        }
    }

    private fun updateDateDisplay() {
        val displayedDate = dateFormat.format(currentDate.time).uppercase(Locale.FRENCH)
        tvCurrentDate.text = displayedDate

        // Check if it's today
        val today = Calendar.getInstance()
        val isToday = currentDate.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) &&
                      currentDate.get(Calendar.YEAR) == today.get(Calendar.YEAR)

        tvCurrentDate.alpha = if (isToday) 1.0f else 0.7f
    }

    private fun loadInterventions() {
        val dateStr = databaseDateFormat.format(currentDate.time)

        lifecycleScope.launchWhenCreated {
            // Use getInterventionsByDate to show all interventions (employee filter not working yet)
            interventionDao.getInterventionsByDate(dateStr)
                .collect { interventions ->
                    updateUI(interventions)
                }
        }
    }

    private fun updateUI(interventions: List<Intervention>) {
        if (interventions.isEmpty()) {
            recyclerView.visibility = View.GONE
            layoutEmptyState.visibility = View.VISIBLE
            tvInterventionsCount.text = getString(R.string.no_interventions_today)
        } else {
            recyclerView.visibility = View.VISIBLE
            layoutEmptyState.visibility = View.GONE

            val count = interventions.size
            val completed = interventions.count { it.terminee }
            val remaining = count - completed

            tvInterventionsCount.text = if (completed == 0) {
                "$count ${getString(R.string.interventions_to_do)}"
            } else if (remaining == 0) {
                getString(R.string.interventions_all_completed)
            } else {
                "${getString(R.string.interventions_summary)}: $completed/$count"
            }

            adapter.submitList(interventions)
        }
    }

    private fun openInterventionDetails(intervention: Intervention) {
        val intent = Intent(this, InterventionDetailsActivity::class.java).apply {
            putExtra("intervention_id", intervention.id)
        }
        startActivity(intent)
    }

    private fun confirmCompletionToggle(intervention: Intervention, isChecked: Boolean) {
        if (!isChecked && intervention.terminee) {
            // User is trying to uncheck a completed intervention
            AlertDialog.Builder(this)
                .setTitle(R.string.confirm_uncheck_title)
                .setMessage(R.string.confirm_uncheck_message)
                .setPositiveButton(R.string.btn_yes) { _, _ ->
                    updateInterventionCompletion(intervention, false)
                }
                .setNegativeButton(R.string.btn_no, null)
                .show()
        } else {
            updateInterventionCompletion(intervention, isChecked)
        }
    }

    private fun updateInterventionCompletion(intervention: Intervention, isCompleted: Boolean) {
        lifecycleScope.launchWhenCreated {
            val completionDate = if (isCompleted) {
                databaseDateFormat.format(java.util.Date())
            } else {
                "0000-00-00"
            }

            val updatedIntervention = intervention.copy(
                terminee = isCompleted,
                dateterminaison = completionDate,
                valsync = intervention.valsync + 1
            )

            interventionDao.update(updatedIntervention)

            // Upload to server
            // SyncManager.uploadInterventionUpdate(updatedIntervention)

            Toast.makeText(
                this@MainActivity,
                if (isCompleted) getString(R.string.intervention_completed) else getString(R.string.intervention_pending),
                Toast.LENGTH_SHORT
            ).show()

            loadInterventions()
        }
    }

    private fun performSync() {
        Toast.makeText(this, R.string.sync_start, Toast.LENGTH_SHORT).show()

        // Use SyncManager to perform synchronization
        lifecycleScope.launchWhenCreated {
            val syncManager = com.expert.maintenance.data.SyncManager(
                this@MainActivity,
                AppDatabase.getDatabase(this@MainActivity).employeeDao(),
                AppDatabase.getDatabase(this@MainActivity).clientDao(),
                AppDatabase.getDatabase(this@MainActivity).siteDao(),
                AppDatabase.getDatabase(this@MainActivity).interventionDao(),
                AppDatabase.getDatabase(this@MainActivity).taskDao(),
                AppDatabase.getDatabase(this@MainActivity).priorityDao(),
                AppDatabase.getDatabase(this@MainActivity).imageDao(),
                AppDatabase.getDatabase(this@MainActivity).employeeInterventionDao()
            )

            val result = syncManager.performFullSync()

            if (result.success) {
                Toast.makeText(this@MainActivity, R.string.sync_success, Toast.LENGTH_SHORT).show()
                loadInterventions()
            } else {
                Toast.makeText(
                    this@MainActivity,
                    "${getString(R.string.sync_error)}: ${result.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showLogoutConfirmation() {
        AlertDialog.Builder(this)
            .setTitle(R.string.nav_deconnexion)
            .setMessage("Voulez-vous vraiment vous déconnecter ?")
            .setPositiveButton(R.string.btn_yes) { _, _ ->
                logout()
            }
            .setNegativeButton(R.string.btn_no, null)
            .show()
    }

    private fun logout() {
        // Clear preferences
        val prefs = getSharedPreferences("expert_maintenance_prefs", MODE_PRIVATE)
        prefs.edit().clear().apply()

        // Redirect to login
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }


}

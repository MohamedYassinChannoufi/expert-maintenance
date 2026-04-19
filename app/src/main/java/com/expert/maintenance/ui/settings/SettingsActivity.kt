package com.expert.maintenance.ui.settings

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.expert.maintenance.R
import com.expert.maintenance.data.AppDatabase
import com.expert.maintenance.data.SyncManager
import com.expert.maintenance.ui.about.AboutActivity
import com.expert.maintenance.ui.LoginActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.progressindicator.CircularProgressIndicator
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * SettingsActivity - Application settings and data management
 * Features:
 * - Clear local database
 * - Reset sync timestamp
 * - Logout
 * - Display app version
 * - Navigate to About screen
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var database: AppDatabase
    private lateinit var syncManager: SyncManager
    private val dateFormat: SimpleDateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRENCH)

    // UI Elements
    private lateinit var toolbar: MaterialToolbar
    private lateinit var tvUserInfo: TextView
    private lateinit var tvVersion: TextView
    private lateinit var btnClearData: Button
    private lateinit var btnResetSync: Button
    private lateinit var btnLogout: Button
    private lateinit var btnAbout: Button
    private lateinit var progressBar: CircularProgressIndicator

    companion object {
        private const val PREFS_NAME = "expert_maintenance_prefs"
        private const val KEY_EMPLOYEE_ID = "current_employee_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Initialize database
        database = AppDatabase.getDatabase(this)
        syncManager = SyncManager(
            this,
            database.employeeDao(),
            database.clientDao(),
            database.siteDao(),
            database.interventionDao(),
            database.taskDao(),
            database.priorityDao(),
            database.imageDao(),
            database.employeeInterventionDao()
        )

        // Initialize UI elements
        initViews()

        // Setup
        setupToolbar()
        displayUserInfo()
        displayVersionInfo()
        setupButtons()
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        tvUserInfo = findViewById(R.id.tv_user_info)
        tvVersion = findViewById(R.id.tv_version)
        btnClearData = findViewById(R.id.btn_clear_data)
        btnResetSync = findViewById(R.id.btn_reset_sync)
        btnLogout = findViewById(R.id.btn_logout)
        btnAbout = findViewById(R.id.btn_about)
        progressBar = findViewById(R.id.progress_bar)
    }

    private fun setupToolbar() {
        toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun displayUserInfo() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val employeeId = prefs.getInt(KEY_EMPLOYEE_ID, 0)

        if (employeeId > 0) {
            lifecycleScope.launch {
                try {
                    val employee = database.employeeDao().getEmployeeById(employeeId)
                    if (employee != null) {
                        tvUserInfo.text = "Connecté en tant que :\n${employee.prenom} ${employee.nom}\n${employee.email}"
                    } else {
                        tvUserInfo.text = "Connecté (ID: $employeeId)"
                    }
                } catch (e: Exception) {
                    tvUserInfo.text = "Connecté (ID: $employeeId)"
                }
            }
        } else {
            tvUserInfo.text = "Non connecté"
        }
    }

    private fun displayVersionInfo() {
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val versionName = packageInfo.versionName ?: "1.0.0"
            tvVersion.text = versionName
        } catch (e: Exception) {
            tvVersion.text = "1.0.0"
        }
    }

    private fun setupButtons() {
        // Clear local data
        btnClearData.setOnClickListener {
            showClearDataConfirmation()
        }

        // Reset sync timestamp
        btnResetSync.setOnClickListener {
            showResetSyncConfirmation()
        }

        // Logout
        btnLogout.setOnClickListener {
            showLogoutConfirmation()
        }

        // About app
        btnAbout.setOnClickListener {
            val intent = Intent(this, AboutActivity::class.java)
            startActivity(intent)
        }
    }

    private fun showClearDataConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Effacer les données locales")
            .setMessage("⚠️ Attention !\n\nCette action va supprimer :\n• Toutes les interventions locales\n• Les clients et sites\n• Les photos capturées\n• Les tâches\n\nLes données seront retéléchargées depuis le serveur lors de la prochaine synchronisation.\n\nVoulez-vous vraiment continuer ?")
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton("Effacer") { _, _ ->
                clearLocalData()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun clearLocalData() {
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                // Clear all tables except employees (keep logged in user)
                database.interventionDao().deleteAll()
                database.clientDao().deleteAll()
                database.siteDao().deleteAll()
                database.taskDao().deleteAll()
                database.priorityDao().deleteAll()
                database.imageDao().deleteAll()

                // Try to clear contract dao if it exists
                try {
                    database.contractDao().deleteAll()
                } catch (e: Exception) {
                    // Contract dao might not exist, ignore
                }

                database.employeeInterventionDao().deleteAll()

                // Reset sync timestamp
                syncManager.resetSyncTimestamp()

                progressBar.visibility = View.GONE

                Toast.makeText(
                    this@SettingsActivity,
                    "✅ Données locales effacées avec succès",
                    Toast.LENGTH_LONG
                ).show()

            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                Toast.makeText(
                    this@SettingsActivity,
                    "❌ Erreur: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showResetSyncConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Réinitialiser la synchronisation")
            .setMessage("Cette action va réinitialiser le timestamp de synchronisation.\n\nToutes les données du serveur seront retéléchargées lors de la prochaine synchronisation.\n\nVoulez-vous vraiment continuer ?")
            .setIcon(R.drawable.ic_sync)
            .setPositiveButton("Réinitialiser") { _, _ ->
                resetSyncTimestamp()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun resetSyncTimestamp() {
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                syncManager.resetSyncTimestamp()

                progressBar.visibility = View.GONE

                Toast.makeText(
                    this@SettingsActivity,
                    "✅ Synchronisation réinitialisée",
                    Toast.LENGTH_SHORT
                ).show()

            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                Toast.makeText(
                    this@SettingsActivity,
                    "❌ Erreur: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showLogoutConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Se déconnecter")
            .setMessage("Voulez-vous vraiment vous déconnecter ?\n\nVous devrez saisir vos identifiants pour vous reconnecter.")
            .setIcon(R.drawable.ic_logout)
            .setPositiveButton("Se déconnecter") { _, _ ->
                logout()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun logout() {
        // Clear preferences
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit().clear().apply()

        // Clear employee data
        syncManager.clearEmployeeData()

        // Redirect to login
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()

        Toast.makeText(
            this,
            "👋 Déconnecté avec succès",
            Toast.LENGTH_SHORT
        ).show()
    }
}

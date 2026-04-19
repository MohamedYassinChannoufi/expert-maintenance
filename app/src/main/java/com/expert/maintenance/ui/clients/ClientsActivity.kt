package com.expert.maintenance.ui.clients

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.expert.maintenance.R
import com.expert.maintenance.data.AppDatabase
import com.expert.maintenance.data.local.entity.Client
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * ClientsActivity - Displays the list of clients from the local database
 * with search and sync functionality.
 */
class ClientsActivity : AppCompatActivity() {

    private lateinit var database: AppDatabase
    private lateinit var adapter: ClientAdapter
    private val activityScope = CoroutineScope(Dispatchers.Main + Job())

    // UI Components
    private lateinit var toolbar: Toolbar
    private lateinit var recyclerClients: RecyclerView
    private lateinit var tvClientCount: TextView
    private lateinit var etSearch: EditText
    private lateinit var btnRefresh: ImageButton
    private lateinit var btnSyncClients: MaterialButton
    private lateinit var layoutEmptyState: LinearLayout
    private lateinit var progressBar: CircularProgressIndicator

    private var allClients = emptyList<Client>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_clients)

        // Initialize database
        database = AppDatabase.getDatabase(this)

        // Initialize views
        initViews()

        // Setup toolbar
        setupToolbar()

        // Setup RecyclerView
        setupRecyclerView()

        // Setup listeners
        setupListeners()

        // Load clients from database
        loadClients()
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        recyclerClients = findViewById(R.id.recycler_clients)
        tvClientCount = findViewById(R.id.tv_client_count)
        etSearch = findViewById(R.id.et_search)
        btnRefresh = findViewById(R.id.btn_refresh)
        btnSyncClients = findViewById(R.id.btn_sync_clients)
        layoutEmptyState = findViewById(R.id.layout_empty_state)
        progressBar = findViewById(R.id.progress_bar)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupRecyclerView() {
        adapter = ClientAdapter { client ->
            // Handle client click - can navigate to client details
            Toast.makeText(this, "Client: ${client.nom}", Toast.LENGTH_SHORT).show()
        }

        recyclerClients.apply {
            layoutManager = LinearLayoutManager(this@ClientsActivity)
            adapter = this@ClientsActivity.adapter
        }
    }

    private fun setupListeners() {
        // Refresh button
        btnRefresh.setOnClickListener {
            loadClients()
        }

        // Sync button
        btnSyncClients.setOnClickListener {
            syncClients()
        }

        // Search
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterClients(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun loadClients() {
        showLoading(true)

        activityScope.launch {
            try {
                database.clientDao().getAllClients().collectLatest { clients ->
                    allClients = clients
                    filterClients(etSearch.text.toString())
                    updateClientCount(clients.size)
                    showLoading(false)
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@ClientsActivity,
                    "Erreur de chargement: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
                showLoading(false)
            }
        }
    }

    private fun filterClients(query: String) {
        val filteredClients = if (query.isBlank()) {
            allClients
        } else {
            allClients.filter { client ->
                client.nom.contains(query, ignoreCase = true) ||
                client.adresse?.contains(query, ignoreCase = true) == true ||
                client.tel?.contains(query, ignoreCase = true) == true ||
                client.email?.contains(query, ignoreCase = true) == true
            }
        }

        adapter.submitList(filteredClients)
        updateEmptyState(filteredClients.isEmpty())
    }

    private fun updateClientCount(count: Int) {
        tvClientCount.text = "$count client(s)"
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        if (isEmpty) {
            recyclerClients.visibility = View.GONE
            layoutEmptyState.visibility = View.VISIBLE
        } else {
            recyclerClients.visibility = View.VISIBLE
            layoutEmptyState.visibility = View.GONE
        }
    }

    private fun showLoading(show: Boolean) {
        if (show) {
            progressBar.visibility = View.VISIBLE
            recyclerClients.visibility = View.GONE
            layoutEmptyState.visibility = View.GONE
        } else {
            progressBar.visibility = View.GONE
            if (recyclerClients.adapter?.itemCount ?: 0 > 0) {
                recyclerClients.visibility = View.VISIBLE
            } else {
                layoutEmptyState.visibility = View.VISIBLE
            }
        }
    }

    private fun syncClients() {
        Toast.makeText(this, "Synchronisation des clients...", Toast.LENGTH_SHORT).show()
        // In a full implementation, this would trigger a sync with the backend
        // For now, just reload from database
        loadClients()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up coroutine scope
        Job().cancel()
    }
}

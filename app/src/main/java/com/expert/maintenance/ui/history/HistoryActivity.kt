package com.expert.maintenance.ui.history

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.expert.maintenance.R
import com.expert.maintenance.data.AppDatabase
import com.expert.maintenance.data.local.entity.Intervention
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class HistoryActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: HistoryAdapter
    private lateinit var tvEmptyState: TextView
    private lateinit var database: AppDatabase

    private var siteId: Int = 0
    private var siteName: String = ""

    companion object {
        const val EXTRA_SITE_ID = "site_id"
        const val EXTRA_SITE_NAME = "site_name"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        siteId = intent.getIntExtra(EXTRA_SITE_ID, 0)
        siteName = intent.getStringExtra(EXTRA_SITE_NAME) ?: "Site"

        if (siteId == 0) {
            finish()
            return
        }

        database = AppDatabase.getDatabase(this)

        setupToolbar()
        setupRecyclerView()
        loadHistory()
    }

    private fun setupToolbar() {
        toolbar = findViewById(R.id.toolbar)
        toolbar.title = "Historique - $siteName"
        toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.recycler_history)
        tvEmptyState = findViewById(R.id.tv_empty_state)

        adapter = HistoryAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun loadHistory() {
        lifecycleScope.launch {
            try {
                val history = database.interventionDao()
                    .getInterventionsBySite(siteId)
                    .first()

                if (history.isEmpty()) {
                    recyclerView.visibility = View.GONE
                    tvEmptyState.visibility = View.VISIBLE
                } else {
                    recyclerView.visibility = View.VISIBLE
                    tvEmptyState.visibility = View.GONE
                    adapter.submitList(history)
                }
            } catch (e: Exception) {
                tvEmptyState.text = "Erreur: ${e.message}"
                tvEmptyState.visibility = View.VISIBLE
            }
        }
    }
}

/**
 * RecyclerView Adapter for history list
 */
class HistoryAdapter : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    private val interventions = mutableListOf<Intervention>()
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.FRENCH)

    fun submitList(list: List<Intervention>) {
        interventions.clear()
        interventions.addAll(list.sortedByDescending { it.datedebut })
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): HistoryViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val intervention = interventions[position]

        val dateStr = try {
            dateFormat.format(SimpleDateFormat("yyyy-MM-dd", Locale.FRENCH).parse(intervention.datedebut))
        } catch (e: Exception) {
            intervention.datedebut
        }

        val status = if (intervention.terminee) "✅ Terminée" else "⏳ En attente"

        holder.bind(intervention.titre, "$dateStr - $status")
    }

    override fun getItemCount(): Int = interventions.size

    class HistoryViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        private val text1: TextView = itemView.findViewById(android.R.id.text1)
        private val text2: TextView = itemView.findViewById(android.R.id.text2)

        fun bind(title: String, subtitle: String) {
            text1.text = title
            text2.text = subtitle
        }
    }
}

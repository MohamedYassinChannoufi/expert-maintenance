package com.expert.maintenance.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.expert.maintenance.R
import com.expert.maintenance.data.local.entity.Intervention
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * RecyclerView Adapter for displaying interventions list
 * Handles item binding, click events, and completion status toggles
 */
class InterventionAdapter(
    private val onInterventionClick: (Intervention) -> Unit,
    private val onCompletionToggle: (Intervention, Boolean) -> Unit
) : ListAdapter<Intervention, InterventionAdapter.InterventionViewHolder>(InterventionDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InterventionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_intervention, parent, false)
        return InterventionViewHolder(view)
    }

    override fun onBindViewHolder(holder: InterventionViewHolder, position: Int) {
        val intervention = getItem(position)
        holder.bind(intervention, onInterventionClick, onCompletionToggle)
    }

    /**
     * ViewHolder for intervention items
     */
    class InterventionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardIntervention: MaterialCardView = itemView.findViewById(R.id.card_intervention)
        private val textTitle: TextView = itemView.findViewById(R.id.text_intervention_title)
        private val textClientName: TextView = itemView.findViewById(R.id.text_client_name)
        private val textAddress: TextView = itemView.findViewById(R.id.text_client_address)
        private val textTimeRange: TextView = itemView.findViewById(R.id.text_time_range)
        private val checkboxCompleted: CheckBox = itemView.findViewById(R.id.checkbox_completed)
        private val viewPriorityIndicator: View = itemView.findViewById(R.id.view_priority_indicator)

        private var currentIntervention: Intervention? = null
        private var clickListener: ((Intervention) -> Unit)? = null
        private var toggleListener: ((Intervention, Boolean) -> Unit)? = null

        init {
            // Set click listener on the entire card
            cardIntervention.setOnClickListener {
                currentIntervention?.let { intervention ->
                    clickListener?.invoke(intervention)
                }
            }

            // Set checkbox change listener
            checkboxCompleted.setOnCheckedChangeListener { buttonView, isChecked ->
                // Only trigger if the change was user-initiated (not programmatic)
                if (buttonView.isPressed) {
                    currentIntervention?.let { intervention ->
                        toggleListener?.invoke(intervention, isChecked)
                    }
                }
            }
        }

        fun bind(
            intervention: Intervention,
            onInterventionClick: (Intervention) -> Unit,
            onCompletionToggle: (Intervention, Boolean) -> Unit
        ) {
            currentIntervention = intervention
            clickListener = onInterventionClick
            toggleListener = onCompletionToggle

            // Bind data to views
            textTitle.text = intervention.titre
            textClientName.text = itemView.context.getString(R.string.client_placeholder)
            textAddress.text = itemView.context.getString(R.string.address_placeholder)

            // Format time range
            val timeRange = String.format(
                "%s - %s",
                intervention.heuredebutplan.substring(0, 5),
                intervention.heurefinplan.substring(0, 5)
            )
            textTimeRange.text = timeRange

            // Set completion status
            checkboxCompleted.isChecked = intervention.terminee

            // Set priority indicator color
            setPriorityIndicator(intervention.prioriteId)

            // Set card background based on completion status
            if (intervention.terminee) {
                cardIntervention.strokeColor = ContextCompat.getColor(itemView.context, R.color.success)
                cardIntervention.strokeWidth = 2
            } else {
                cardIntervention.strokeColor = ContextCompat.getColor(itemView.context, R.color.transparent)
                cardIntervention.strokeWidth = 0
            }
        }

        private fun setPriorityIndicator(priorityId: Int) {
            val color = when (priorityId) {
                1 -> R.color.priority_normal      // Normale - Green
                2 -> R.color.priority_urgent      // Urgente - Orange
                3 -> R.color.priority_critical    // Critique - Red
                else -> R.color.priority_normal
            }
            viewPriorityIndicator.setBackgroundColor(ContextCompat.getColor(itemView.context, color))
            viewPriorityIndicator.visibility = View.VISIBLE
        }
    }

    /**
     * DiffUtil callback for efficient list updates
     */
    class InterventionDiffCallback : DiffUtil.ItemCallback<Intervention>() {
        override fun areItemsTheSame(oldItem: Intervention, newItem: Intervention): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Intervention, newItem: Intervention): Boolean {
            return oldItem == newItem
        }

        override fun getChangePayload(oldItem: Intervention, newItem: Intervention): Any? {
            // Return specific changes for partial updates
            return if (oldItem.copy(terminee = true) == newItem.copy(terminee = true)) {
                // Only completion status changed
                "COMPLETION_STATUS"
            } else {
                null
            }
        }
    }
}

package com.expert.maintenance.ui.clients

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.expert.maintenance.R
import com.expert.maintenance.data.local.entity.Client
import com.google.android.material.card.MaterialCardView

/**
 * ClientAdapter - RecyclerView adapter for displaying client list
 */
class ClientAdapter(
    private val onItemClick: (Client) -> Unit
) : ListAdapter<Client, ClientAdapter.ClientViewHolder>(ClientDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClientViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_client, parent, false)
        return ClientViewHolder(view, onItemClick)
    }

    override fun onBindViewHolder(holder: ClientViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ClientViewHolder(
        itemView: View,
        private val onItemClick: (Client) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val cardView: MaterialCardView = itemView as MaterialCardView
        private val tvClientName: TextView = itemView.findViewById(R.id.tv_client_name)
        private val tvClientAddress: TextView = itemView.findViewById(R.id.tv_client_address)
        private val tvClientPhone: TextView = itemView.findViewById(R.id.tv_client_phone)
        private val tvClientEmail: TextView = itemView.findViewById(R.id.tv_client_email)

        init {
            cardView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val client = getItem(position)
                    onItemClick(client)
                }
            }
        }

        fun bind(client: Client) {
            tvClientName.text = client.nom
            tvClientAddress.text = client.adresse
            tvClientPhone.text = client.tel
            tvClientEmail.text = client.email
        }
    }

    /**
     * DiffUtil callback for efficient list updates
     */
    class ClientDiffCallback : DiffUtil.ItemCallback<Client>() {
        override fun areItemsTheSame(oldItem: Client, newItem: Client): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Client, newItem: Client): Boolean {
            return oldItem == newItem
        }
    }
}

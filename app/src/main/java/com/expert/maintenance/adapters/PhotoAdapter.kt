package com.expert.maintenance.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.expert.maintenance.R
import com.expert.maintenance.data.local.entity.Image

class PhotoAdapter : RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder>() {

    private val photos = mutableListOf<Image>()
    private var onPhotoClick: ((Image) -> Unit)? = null

    fun submitList(list: List<Image>) {
        photos.clear()
        photos.addAll(list)
        notifyDataSetChanged()
    }

    fun setOnPhotoClickListener(listener: (Image) -> Unit) {
        onPhotoClick = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_photo, parent, false)
        return PhotoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val photo = photos[position]
        holder.bind(photo)
        holder.itemView.setOnClickListener {
            onPhotoClick?.invoke(photo)
        }
    }

    override fun getItemCount(): Int = photos.size

    class PhotoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.ivPhoto)

        fun bind(photo: Image) {
            val byteArray = photo.img
            if (byteArray != null) {
                Glide.with(itemView.context)
                    .load(byteArray)
                    .placeholder(R.drawable.ic_image)
                    .error(R.drawable.ic_image)
                    .centerCrop()
                    .into(imageView)
            } else {
                imageView.setImageResource(R.drawable.ic_image)
            }
        }
    }
}

package com.kstream.app.ui.year

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.kstream.app.databinding.ItemMovieBinding
import com.kstream.core.domain.models.MovieItem

class MovieAdapter(private val onClick: (MovieItem) -> Unit) : PagingDataAdapter<MovieItem, MovieAdapter.ViewHolder>(DiffCallback) {

    class ViewHolder(private val binding: ItemMovieBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: MovieItem, onClick: (MovieItem) -> Unit) {
            binding.movieTitle.text = item.title
            binding.posterImage.load(item.posterUrl) {
                crossfade(true)
                // placeholder(R.drawable.placeholder)
            }
            binding.root.setOnClickListener { onClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(ItemMovieBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        getItem(position)?.let { holder.bind(it, onClick) }
    }

    object DiffCallback : DiffUtil.ItemCallback<MovieItem>() {
        override fun areItemsTheSame(oldItem: MovieItem, newItem: MovieItem) = oldItem.path == newItem.path
        override fun areContentsTheSame(oldItem: MovieItem, newItem: MovieItem) = oldItem == newItem
    }
}

package com.kstream.app.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kstream.app.databinding.ItemYearBinding
import com.kstream.core.domain.models.YearItem

class YearAdapter(private val onClick: (YearItem) -> Unit) : ListAdapter<YearItem, YearAdapter.ViewHolder>(DiffCallback) {

    class ViewHolder(private val binding: ItemYearBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: YearItem, onClick: (YearItem) -> Unit) {
            binding.yearText.text = item.year
            binding.root.setOnClickListener { onClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(ItemYearBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onClick)
    }

    object DiffCallback : DiffUtil.ItemCallback<YearItem>() {
        override fun areItemsTheSame(oldItem: YearItem, newItem: YearItem) = oldItem.year == newItem.year
        override fun areContentsTheSame(oldItem: YearItem, newItem: YearItem) = oldItem == newItem
    }
}

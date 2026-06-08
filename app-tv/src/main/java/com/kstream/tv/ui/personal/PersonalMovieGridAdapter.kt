package com.kstream.tv.ui.personal

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.kstream.tv.R

class PersonalMovieGridAdapter(
    private val onClick: (PersonalMovieItem) -> Unit,
    private val onLongClick: (PersonalMovieItem) -> Unit
) : ListAdapter<PersonalMovieItem, PersonalMovieGridAdapter.MovieViewHolder>(Diff) {

    var selectMode: Boolean = false
    var selectedIds: Set<String> = emptySet()

    /**
     * Apply a new selection set without rebinding the whole grid. Calls
     * notifyItemChanged only for items whose selected state actually flipped,
     * which preserves focus on the currently navigated tile (a full
     * notifyDataSetChanged() destroys the focused view and bounces focus to
     * the next focusable, e.g. the Select button).
     */
    fun applySelection(newSelectMode: Boolean, newSelectedIds: Set<String>) {
        val modeChanged = selectMode != newSelectMode
        val oldIds = selectedIds
        selectMode = newSelectMode
        selectedIds = newSelectedIds
        if (modeChanged) {
            notifyItemRangeChanged(0, itemCount)
            return
        }
        if (oldIds == newSelectedIds) return
        val changedIds = (oldIds - newSelectedIds) + (newSelectedIds - oldIds)
        if (changedIds.isEmpty()) return
        for (i in 0 until itemCount) {
            if (getItem(i).id in changedIds) notifyItemChanged(i, PAYLOAD_SELECTION)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MovieViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_personal_movie, parent, false)
        return MovieViewHolder(view, onClick, onLongClick)
    }

    private companion object {
        const val PAYLOAD_SELECTION = "selection"
    }

    override fun onBindViewHolder(holder: MovieViewHolder, position: Int) {
        holder.bind(getItem(position), selectedIds.contains(getItem(position).id))
    }

    override fun onBindViewHolder(holder: MovieViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty() && payloads.all { it == PAYLOAD_SELECTION }) {
            holder.bindSelection(selectedIds.contains(getItem(position).id))
            return
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    override fun onViewRecycled(holder: MovieViewHolder) {
        holder.clear()
        super.onViewRecycled(holder)
    }

    class MovieViewHolder(
        itemView: View,
        private val onClick: (PersonalMovieItem) -> Unit,
        private val onLongClick: (PersonalMovieItem) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val posterFrame: View = itemView.findViewById(R.id.poster_frame)
        private val poster: ImageView = itemView.findViewById(R.id.poster_image)
        private val title: TextView = itemView.findViewById(R.id.movie_title)
        private val badge: TextView = itemView.findViewById(R.id.movie_badge)
        private val check: TextView = itemView.findViewById(R.id.check_overlay)
        private var boundItem: PersonalMovieItem? = null

        init {
            itemView.setOnClickListener { boundItem?.let(onClick) }
            itemView.setOnLongClickListener {
                boundItem?.let(onLongClick)
                true
            }
            itemView.setOnFocusChangeListener { _, hasFocus ->
                posterFrame.isSelected = hasFocus
                itemView.animate()
                    .scaleX(if (hasFocus) 1.06f else 1f)
                    .scaleY(if (hasFocus) 1.06f else 1f)
                    .setDuration(120L)
                    .start()
            }
        }

        fun bind(item: PersonalMovieItem, selected: Boolean) {
            boundItem = item
            title.text = item.title
            badge.text = item.badge
            badge.isVisible = item.badge.isNotBlank()
            posterFrame.isActivated = selected
            check.isVisible = selected
            Glide.with(poster)
                .load(item.posterUrl.takeIf { it.isNotBlank() })
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .placeholder(R.drawable.card_poster_bg)
                .error(R.drawable.card_poster_bg)
                .into(poster)
        }

        /** Update only the selection visuals without reloading the poster image. */
        fun bindSelection(selected: Boolean) {
            posterFrame.isActivated = selected
            check.isVisible = selected
        }

        fun clear() {
            Glide.with(poster).clear(poster)
            itemView.animate().cancel()
            itemView.scaleX = 1f
            itemView.scaleY = 1f
            boundItem = null
        }
    }

    private object Diff : DiffUtil.ItemCallback<PersonalMovieItem>() {
        override fun areItemsTheSame(oldItem: PersonalMovieItem, newItem: PersonalMovieItem): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: PersonalMovieItem, newItem: PersonalMovieItem): Boolean =
            oldItem == newItem
    }
}

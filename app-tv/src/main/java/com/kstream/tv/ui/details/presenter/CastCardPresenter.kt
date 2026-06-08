package com.kstream.tv.ui.details.presenter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.leanback.widget.Presenter
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.kstream.core.enrichment.model.EnrichedCast
import com.kstream.tv.R

class CastCardPresenter(
    private val onCastClick: ((EnrichedCast) -> Unit)? = null,
) : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.card_cast, parent, false)
        view.isFocusable = true
        view.isFocusableInTouchMode = true
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val cast = item as? EnrichedCast ?: return
        val v = viewHolder.view
        val photo = v.findViewById<ImageView>(R.id.cast_photo)
        val initials = v.findViewById<TextView>(R.id.cast_initials)
        val name = v.findViewById<TextView>(R.id.cast_name)
        val character = v.findViewById<TextView>(R.id.cast_character)
        name.text = cast.name
        if (cast.character.isNullOrBlank()) {
            character.visibility = View.GONE
        } else {
            character.visibility = View.VISIBLE
            character.text = cast.character
        }
        if (!cast.photoUrl.isNullOrBlank()) {
            initials.visibility = View.GONE
            photo.visibility = View.VISIBLE
            Glide.with(v).load(cast.photoUrl)
                .placeholder(R.drawable.cast_placeholder)
                .error(R.drawable.cast_placeholder)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .centerCrop()
                .into(photo)
        } else {
            // No portrait — render the cast member's initials inside the
            // circular bg to keep the row visually consistent.
            photo.visibility = View.GONE
            initials.visibility = View.VISIBLE
            initials.text = buildInitials(cast.name)
        }
        v.setOnClickListener { onCastClick?.invoke(cast) }
    }

    private fun buildInitials(name: String): String {
        val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (parts.isEmpty()) return "?"
        val first = parts.first().first().uppercaseChar()
        val last = if (parts.size > 1) parts.last().first().uppercaseChar() else null
        return if (last != null) "$first$last" else "$first"
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val photo = viewHolder.view.findViewById<ImageView>(R.id.cast_photo)
        photo?.let { Glide.with(it).clear(it) }
    }
}

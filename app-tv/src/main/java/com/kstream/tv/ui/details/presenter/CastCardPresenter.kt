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

class CastCardPresenter : Presenter() {

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
            Glide.with(v).load(cast.photoUrl)
                .placeholder(R.drawable.cast_placeholder)
                .error(R.drawable.cast_placeholder)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .centerCrop()
                .into(photo)
        } else {
            photo.setImageResource(R.drawable.cast_placeholder)
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val photo = viewHolder.view.findViewById<ImageView>(R.id.cast_photo)
        photo?.let { Glide.with(it).clear(it) }
    }
}

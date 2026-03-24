package com.fuelfinder.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fuelfinder.databinding.ItemSuggestionBinding

class SuggestionAdapter(
    private val onClick: (LocationSuggestion) -> Unit
) : ListAdapter<LocationSuggestion, SuggestionAdapter.VH>(Diff()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemSuggestionBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) =
        holder.bind(getItem(position))

    inner class VH(private val b: ItemSuggestionBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(s: LocationSuggestion) {
            b.tvShortName.text = s.shortName
            b.tvFullName.text  = s.displayName
            b.root.setOnClickListener { onClick(s) }
        }
    }

    class Diff : DiffUtil.ItemCallback<LocationSuggestion>() {
        override fun areItemsTheSame(o: LocationSuggestion, n: LocationSuggestion) = o.lat == n.lat && o.lon == n.lon
        override fun areContentsTheSame(o: LocationSuggestion, n: LocationSuggestion) = o == n
    }
}

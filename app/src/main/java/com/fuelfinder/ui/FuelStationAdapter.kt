package com.fuelfinder.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fuelfinder.R
import com.fuelfinder.data.model.FuelStation
import com.fuelfinder.databinding.ItemStationBinding

class FuelStationAdapter(
    private val onClick: (FuelStation) -> Unit
) : ListAdapter<FuelStation, FuelStationAdapter.VH>(Diff()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemStationBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) =
        holder.bind(getItem(position), position + 1)

    inner class VH(private val b: ItemStationBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(s: FuelStation, rank: Int) {
            val ctx = b.root.context
            b.tvName.text = s.name
            b.tvAddress.text = if (s.address.isNotEmpty()) s.address else "Along your route"
            b.tvPrice.text = "₹${String.format("%.2f", s.price)}/L"
            b.tvDistance.text = "${s.distanceFromRoute} km off route"
            b.tvRank.text = "#$rank"
            b.tvBrand.text = s.brand

            if (s.isBestOption) {
                b.layoutBest.visibility = View.VISIBLE
                b.cardRoot.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.best_card_bg))
                b.viewAccent.setBackgroundColor(ContextCompat.getColor(ctx, R.color.best_option_green))
                b.tvPrice.setTextColor(ContextCompat.getColor(ctx, R.color.best_option_green))
            } else {
                b.layoutBest.visibility = View.GONE
                b.cardRoot.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.card_bg))
                b.tvPrice.setTextColor(ContextCompat.getColor(ctx, R.color.price_color))
                b.viewAccent.setBackgroundColor(ContextCompat.getColor(ctx,
                    when (s.brand) {
                        "Indian Oil" -> R.color.brand_iocl
                        "BPCL" -> R.color.brand_bpcl
                        "HPCL" -> R.color.brand_hpcl
                        "Shell" -> R.color.brand_shell
                        "Reliance" -> R.color.brand_reliance
                        "Nayara" -> R.color.brand_nayara
                        else -> R.color.primary
                    }
                ))
            }
            b.root.setOnClickListener { onClick(s) }
        }
    }

    class Diff : DiffUtil.ItemCallback<FuelStation>() {
        override fun areItemsTheSame(o: FuelStation, n: FuelStation) = o.id == n.id
        override fun areContentsTheSame(o: FuelStation, n: FuelStation) = o == n
    }
}

package com.example.iot.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import com.example.iot.R

class IndicatorAdapter(
    private var count: Int = 0
) : RecyclerView.Adapter<IndicatorAdapter.ViewHolder>() {

    private var selectedPosition = 0

    fun updateCount(newCount: Int) {
        count = newCount
        notifyDataSetChanged()
    }

    fun setSelected(position: Int) {
        val prev = selectedPosition
        selectedPosition = position
        notifyItemChanged(prev)
        notifyItemChanged(selectedPosition)
    }

    override fun getItemCount(): Int = count

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_indicator, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(position == selectedPosition)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val selectedView: View = itemView.findViewById(R.id.view_indicator_selected)
        private val normalView: View = itemView.findViewById(R.id.view_indicator_normal)

        fun bind(isSelected: Boolean) {
            selectedView.visibility = if (isSelected) View.VISIBLE else View.GONE
            normalView.visibility = if (isSelected) View.GONE else View.VISIBLE
        }
    }
}
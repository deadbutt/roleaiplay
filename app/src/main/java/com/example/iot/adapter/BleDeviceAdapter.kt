package com.example.iot.adapter

import android.bluetooth.le.ScanResult
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.example.iot.R

class BleDeviceAdapter(
    private var devices: List<ScanResult> = emptyList()
) : RecyclerView.Adapter<BleDeviceAdapter.ViewHolder>() {

    private var selectedPosition = -1
    var onItemClick: ((Int) -> Unit)? = null

    fun getSelectedDevice(): ScanResult? {
        return if (selectedPosition in devices.indices) devices[selectedPosition] else null
    }

    fun updateDevices(newDevices: List<ScanResult>) {
        devices = newDevices
        selectedPosition = -1
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = devices.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ble_device, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(devices[position], position == selectedPosition)
        holder.itemView.setOnClickListener {
            val prev = selectedPosition
            selectedPosition = holder.adapterPosition
            notifyItemChanged(prev)
            notifyItemChanged(selectedPosition)
            onItemClick?.invoke(selectedPosition)
        }
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: CardView = itemView.findViewById(R.id.card_ble_device)
        private val tvName: TextView = itemView.findViewById(R.id.tv_device_name)
        private val tvMac: TextView = itemView.findViewById(R.id.tv_device_mac)
        private val ivSelected: ImageView = itemView.findViewById(R.id.iv_selected)

        fun bind(result: ScanResult, isSelected: Boolean) {
            val device = result.device
            tvName.text = device.name ?: "Unknown Device"
            tvMac.text = "MAC: ${device.address}"
            ivSelected.visibility = if (isSelected) View.VISIBLE else View.INVISIBLE

            if (isSelected) {
                cardView.setCardBackgroundColor(itemView.context.getColor(android.R.color.holo_green_dark))
            } else {
                cardView.setCardBackgroundColor(itemView.context.getColor(android.R.color.white))
            }
        }
    }
}
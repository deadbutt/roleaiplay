package com.example.iot.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.example.iot.R
import com.example.iot.model.BoundDevice

class DeviceCardAdapter(
    private var devices: List<BoundDevice> = emptyList(),
    private val onItemClick: (BoundDevice) -> Unit,
    private val onAddClick: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_DEVICE = 0
        private const val TYPE_ADD = 1
    }

    fun updateDevices(newDevices: List<BoundDevice>) {
        devices = newDevices
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = devices.size + 1

    override fun getItemViewType(position: Int): Int {
        return if (position < devices.size) TYPE_DEVICE else TYPE_ADD
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_DEVICE) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_device_card, parent, false)
            DeviceViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_add_device, parent, false)
            AddViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is DeviceViewHolder -> holder.bind(devices[position])
            is AddViewHolder -> holder.bind()
        }
    }

    inner class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardClick: View = itemView.findViewById(R.id.rv_device_card_click)
        private val tvName: TextView = itemView.findViewById(R.id.tv_device_name)
        private val tvStatus: TextView = itemView.findViewById(R.id.tv_device_status)

        fun bind(device: BoundDevice) {
            tvName.text = device.deviceName
            tvStatus.text = if (device.isOnline) "在线" else "离线"
            tvStatus.setTextColor(
                if (device.isOnline)
                    itemView.context.getColor(android.R.color.holo_green_dark)
                else
                    itemView.context.getColor(android.R.color.darker_gray)
            )
            cardClick.setOnClickListener { onItemClick(device) }
        }
    }

    inner class AddViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: CardView = itemView.findViewById(R.id.card_add_device)

        fun bind() {
            cardView.setOnClickListener { onAddClick() }
        }
    }
}
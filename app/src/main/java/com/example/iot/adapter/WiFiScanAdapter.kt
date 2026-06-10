package com.example.iot.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.iot.R
import com.example.iot.ble.proto.WiFiScanResult

class WiFiScanAdapter(
    private var results: List<WiFiScanResult> = emptyList(),
    private val onItemClick: (WiFiScanResult) -> Unit
) : RecyclerView.Adapter<WiFiScanAdapter.WiFiViewHolder>() {

    fun updateResults(newResults: List<WiFiScanResult>) {
        results = newResults
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = results.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WiFiViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_wifi_scan_result, parent, false)
        return WiFiViewHolder(view)
    }

    override fun onBindViewHolder(holder: WiFiViewHolder, position: Int) {
        holder.bind(results[position])
    }

    inner class WiFiViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvWifiName: TextView = itemView.findViewById(R.id.tv_wifi_name)
        private val tvWifiAuth: TextView = itemView.findViewById(R.id.tv_wifi_auth)
        private val tvWifiChannel: TextView = itemView.findViewById(R.id.tv_wifi_channel)
        private val tvWifiRssi: TextView = itemView.findViewById(R.id.tv_wifi_rssi)

        fun bind(result: WiFiScanResult) {
            tvWifiName.text = result.ssid.ifEmpty { "隐藏网络" }
            tvWifiAuth.text = result.getAuthModeDescription()
            tvWifiChannel.text = "信道 ${result.channel}"
            tvWifiRssi.text = "${result.rssi} dBm"

            // 根据信号强度设置颜色
            val rssiColor = when {
                result.rssi >= -50 -> itemView.context.getColor(android.R.color.holo_green_dark)
                result.rssi >= -60 -> itemView.context.getColor(android.R.color.holo_orange_dark)
                else -> itemView.context.getColor(android.R.color.holo_red_dark)
            }
            tvWifiRssi.setTextColor(rssiColor)

            itemView.setOnClickListener { onItemClick(result) }
        }
    }
}
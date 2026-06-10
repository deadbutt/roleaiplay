package com.example.iot

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.iot.adapter.IndicatorAdapter
import com.example.iot.model.BoundDevice
import com.example.iot.util.DeviceStorage

class DeviceListFragment : Fragment() {

    private lateinit var vpDeviceList: ViewPager2
    private lateinit var rvIndicator: RecyclerView
    private lateinit var llEmpty: LinearLayout
    private lateinit var ivAddDevice: ImageView
    private lateinit var indicatorAdapter: IndicatorAdapter
    private lateinit var devicePagerAdapter: DevicePagerAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.activity_device_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)
        loadDevices()
    }

    private fun initViews(view: View) {
        vpDeviceList = view.findViewById(R.id.vp_device_list)
        rvIndicator = view.findViewById(R.id.rv_indicator)
        llEmpty = view.findViewById(R.id.ll_empty)
        ivAddDevice = view.findViewById(R.id.iv_add_device)

        indicatorAdapter = IndicatorAdapter()
        rvIndicator.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        rvIndicator.adapter = indicatorAdapter

        devicePagerAdapter = DevicePagerAdapter { device ->
            val intent = Intent(requireContext(), HomeActivity::class.java).apply {
                putExtra("device_id", device.deviceId)
                putExtra("device_name", device.deviceName)
            }
            startActivity(intent)
        }
        vpDeviceList.adapter = devicePagerAdapter

        vpDeviceList.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                indicatorAdapter.setSelected(position)
            }
        })

        ivAddDevice.setOnClickListener {
            startActivity(Intent(requireContext(), BleSearchActivity::class.java))
        }
    }

    private fun loadDevices() {
        val devices = DeviceStorage.getDevices(requireContext())
        if (devices.isEmpty()) {
            vpDeviceList.visibility = View.GONE
            rvIndicator.visibility = View.GONE
            llEmpty.visibility = View.VISIBLE
        } else {
            vpDeviceList.visibility = View.VISIBLE
            rvIndicator.visibility = View.VISIBLE
            llEmpty.visibility = View.GONE
            devicePagerAdapter.updateDevices(devices)
            indicatorAdapter.updateCount(devices.size)
        }
    }

    override fun onResume() {
        super.onResume()
        loadDevices()
    }

    inner class DevicePagerAdapter(
        private val onItemClick: (BoundDevice) -> Unit
    ) : RecyclerView.Adapter<DevicePagerAdapter.DeviceViewHolder>() {

        private var devices: List<BoundDevice> = emptyList()

        fun updateDevices(newDevices: List<BoundDevice>) {
            devices = newDevices
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = devices.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_device_card, parent, false)
            return DeviceViewHolder(view)
        }

        override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
            holder.bind(devices[position])
        }

        inner class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvName: TextView = itemView.findViewById(R.id.tv_device_name)
            private val tvStatus: TextView = itemView.findViewById(R.id.tv_device_status)
            private val cardClick: View = itemView.findViewById(R.id.rv_device_card_click)

            fun bind(device: BoundDevice) {
                tvName.text = device.deviceName
                tvStatus.text = if (device.isOnline) "在线" else "离线"
                cardClick.setOnClickListener { onItemClick(device) }
                
                // 长按删除设备
                cardClick.setOnLongClickListener {
                    showDeleteDialog(device)
                    true
                }
            }
            
            private fun showDeleteDialog(device: BoundDevice) {
                AlertDialog.Builder(requireContext())
                    .setTitle("删除设备")
                    .setMessage("确定删除设备 ${device.deviceName}？")
                    .setPositiveButton("删除") { _, _ ->
                        DeviceStorage.removeDevice(requireContext(), device.deviceId)
                        Toast.makeText(requireContext(), "设备已删除", Toast.LENGTH_SHORT).show()
                        loadDevices()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }
    }
}
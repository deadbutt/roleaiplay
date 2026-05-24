package com.example.iot

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import org.json.JSONObject

class DeviceHomeFragment : Fragment() {

    private val handler = Handler(Looper.getMainLooper())
    private var isDestroyed = false
    
    private val DEVICE_ID = "test01"
    
    private lateinit var tvBattery: TextView
    private lateinit var tvTemp: TextView
    private lateinit var tvHumidity: TextView
    private lateinit var tvVoc: TextView
    private lateinit var tvCo2: TextView
    private lateinit var tvHcho: TextView
    private lateinit var tvWater: TextView
    private lateinit var tvMotor: TextView
    private lateinit var tvLight: TextView
    private lateinit var tvIrStatus: TextView
    
    private lateinit var wsManager: WebSocketManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_device_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initViews(view)
        initWebSocket()
    }
    
    private fun initViews(view: View) {
        tvBattery = view.findViewById(R.id.tv_battery_value)
        tvTemp = view.findViewById(R.id.tv_temp_value)
        tvHumidity = view.findViewById(R.id.tv_humidity_value)
        tvVoc = view.findViewById(R.id.tv_voc_value)
        tvCo2 = view.findViewById(R.id.tv_co2_value)
        tvHcho = view.findViewById(R.id.tv_hcho_value)
        tvWater = view.findViewById(R.id.tv_water_value)
        tvMotor = view.findViewById(R.id.tv_motor_value)
        tvLight = view.findViewById(R.id.tv_light_value)
        tvIrStatus = view.findViewById(R.id.tv_ir_status)
    }
    
    private fun initWebSocket() {
        wsManager = WebSocketManager.getInstance()
        
        wsManager.setOnMessageListener { message ->
            if (!isDestroyed) {
                parseAndUpdateData(message)
            }
        }
        
        wsManager.setOnConnectionListener { connected ->
            if (connected) {
                android.util.Log.d("DeviceHome", "WebSocket 已连接")
            } else {
                android.util.Log.d("DeviceHome", "WebSocket 断开")
            }
        }
        
        wsManager.connect()
    }
    
    private fun parseAndUpdateData(jsonStr: String) {
        try {
            val json = JSONObject(jsonStr)
            
            val temp = json.optDouble("temperature", 0.0)
            val hum = json.optDouble("humidity", 0.0)
            val battery = json.optInt("battery", 85)
            val light = json.optString("light", "70%")
            val water = json.optInt("water", 60)
            val motor = json.optInt("motor", 1400)
            val voc = json.optInt("voc", 75)
            val co2 = json.optInt("co2", 36)
            val hcho = json.optInt("hcho", 87)
            
            handler.post {
                tvBattery?.text = "$battery%"
                tvTemp?.text = "${String.format("%.1f", temp)}°C"
                tvHumidity?.text = "${String.format("%.1f", hum)}%"
                tvLight?.text = light
                tvWater?.text = "$water%"
                tvMotor?.text = "$motor RPM"
                tvVoc?.text = "VOC"
                tvCo2?.text = "CO2"
                tvHcho?.text = "HCHO"
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun sendCommand(cmd: String) {
        wsManager.sendCommand(DEVICE_ID, cmd)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        isDestroyed = true
        handler.removeCallbacksAndMessages(null)
    }
    
    override fun onResume() {
        super.onResume()
        if (!wsManager.isWSConnected()) {
            wsManager.connect()
        }
    }
}

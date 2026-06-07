package com.example.iot

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
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

    private lateinit var progressVoc: ProgressBar
    private lateinit var progressCo2: ProgressBar
    private lateinit var progressHcho: ProgressBar
    private lateinit var viewBatteryFill: View

    private lateinit var switchHumidifier: SwitchCompat
    private lateinit var seekBarLight: SeekBar

    private lateinit var wsManager: WebSocketManager

    private var isUserChangingLight = false
    private var lastLightValue = 70

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

        progressVoc = view.findViewById(R.id.progress_voc)
        progressCo2 = view.findViewById(R.id.progress_co2)
        progressHcho = view.findViewById(R.id.progress_hcho)
        viewBatteryFill = view.findViewById(R.id.view_battery_fill)

        switchHumidifier = view.findViewById(R.id.switch_humidifier)
        seekBarLight = view.findViewById(R.id.seekbar_light)

        initControls()
        updateBatteryUI(85)
    }

    private fun updateBatteryUI(batteryPercent: Int) {
        val percent = batteryPercent.coerceIn(0, 100)
        tvBattery?.text = "$percent%"

        viewBatteryFill?.post {
            val clipContainer = viewBatteryFill.parent as? FrameLayout
            val containerHeight = clipContainer?.height ?: 82
            if (containerHeight > 0) {
                val fillHeight = (containerHeight * percent / 100.0).toInt()
                viewBatteryFill?.layoutParams?.height = fillHeight.coerceIn(0, containerHeight)
                viewBatteryFill?.requestLayout()
            }
        }
    }

    private fun initControls() {
        switchHumidifier.setOnCheckedChangeListener { _, isChecked ->
            val cmd = if (isChecked) "water_pump:on" else "water_pump:off"
            sendCommand(cmd)
        }

        seekBarLight.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    isUserChangingLight = true
                    lastLightValue = progress
                    tvLight?.text = "$progress%"
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserChangingLight = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isUserChangingLight = false
                sendCommand("light:$lastLightValue")
            }
        })
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
            val battery = json.optInt("battery", 0)
            val lightStr = json.optString("light", "0%")
            val water = json.optInt("water", 0)
            val motor = json.optInt("motor", 0)
            val voc = json.optInt("voc", 0)
            val co2 = json.optInt("co2", 0)
            val hcho = json.optInt("hcho", 0)
            val effect = json.optInt("effect", 0)

            val lightValue = lightStr.replace("%", "").toIntOrNull() ?: 0

            handler.post {
                tvBattery?.text = "$battery%"
                tvTemp?.text = "${String.format("%.1f", temp)}°C"
                tvHumidity?.text = "${String.format("%.1f", hum)}%"
                if (!isUserChangingLight) {
                    tvLight?.text = lightStr
                    seekBarLight?.progress = lightValue
                }
                tvWater?.text = "$water%"
                tvMotor?.text = "$motor RPM"
                tvVoc?.text = "VOC: $voc"
                tvCo2?.text = "CO₂: $co2"
                tvHcho?.text = "HCHO: $hcho"

                progressVoc?.progress = voc.coerceIn(0, 100)
                progressCo2?.progress = co2.coerceIn(0, 100)
                progressHcho?.progress = hcho.coerceIn(0, 100)

                updateBatteryUI(battery)
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

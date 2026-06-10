package com.example.iot

import android.content.Intent
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import com.example.iot.ble.BleProvisioningManager
import com.example.iot.ble.proto.WiFiScanResult as BleWiFiScanResult
import com.example.iot.model.BoundDevice
import com.example.iot.util.DeviceStorage

class WifiConfigActivity : AppCompatActivity() {

    private lateinit var etSsid: EditText
    private lateinit var etPassword: EditText
    private lateinit var ivTogglePassword: ImageView
    private lateinit var tvDeviceInfo: TextView
    private lateinit var btnStart: AppCompatButton
    private lateinit var llProgress: LinearLayout
    private lateinit var tvProgressStatus: TextView
    private lateinit var tvProgressDetail: TextView

    private var deviceName: String = ""
    private var deviceAddress: String = ""
    private var isPasswordVisible = false
    private lateinit var provisioningManager: BleProvisioningManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wifi_config)

        deviceName = intent.getStringExtra("device_name") ?: ""
        deviceAddress = intent.getStringExtra("device_address") ?: ""

        provisioningManager = BleProvisioningManager.getInstance(this)
        provisioningManager.setCallback(object : BleProvisioningManager.ProvisioningCallback {
            override fun onStatusUpdate(status: String, detail: String) {
                runOnUiThread {
                    showProgress(status, detail)
                }
            }

            override fun onSuccess(deviceId: String, ipAddress: String?) {
                runOnUiThread {
                    val device = BoundDevice(
                        deviceId = deviceAddress.replace(":", ""),
                        deviceName = deviceName,
                        macAddress = deviceAddress
                    )
                    DeviceStorage.saveDevice(this@WifiConfigActivity, device)
                    Toast.makeText(this@WifiConfigActivity, "设备添加成功", Toast.LENGTH_SHORT).show()

                    val intent = Intent(this@WifiConfigActivity, MainHomeActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(intent)
                    finish()
                }
            }

            override fun onError(error: String) {
                runOnUiThread {
                    hideProgress()
                    btnStart.isEnabled = true
                    Toast.makeText(this@WifiConfigActivity, error, Toast.LENGTH_LONG).show()
                }
            }

            override fun onWifiScanReady(results: List<BleWiFiScanResult>) {
                // 手动输入模式，不使用 WiFi 扫描结果
            }
        })

        initViews()
    }

    private fun initViews() {
        etSsid = findViewById(R.id.et_ssid)
        etPassword = findViewById(R.id.et_password)
        ivTogglePassword = findViewById(R.id.iv_toggle_password)
        tvDeviceInfo = findViewById(R.id.tv_device_info)
        btnStart = findViewById(R.id.btn_start_provision)
        llProgress = findViewById(R.id.ll_progress)
        tvProgressStatus = findViewById(R.id.tv_progress_status)
        tvProgressDetail = findViewById(R.id.tv_progress_detail)

        tvDeviceInfo.text = "设备: $deviceName"

        findViewById<View>(R.id.iv_back).setOnClickListener { finish() }

        ivTogglePassword.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            if (isPasswordVisible) {
                etPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
                ivTogglePassword.alpha = 1.0f
            } else {
                etPassword.transformationMethod = PasswordTransformationMethod.getInstance()
                ivTogglePassword.alpha = 0.6f
            }
            etPassword.setSelection(etPassword.text.length)
        }

        // 隐藏扫描相关 UI
        findViewById<View>(R.id.iv_scan_wifi)?.visibility = View.GONE
        findViewById<View>(R.id.btn_scan_wifi)?.visibility = View.GONE
        findViewById<View>(R.id.view_spacer)?.visibility = View.GONE

        btnStart.setOnClickListener { sendWiFiCredentials() }
    }

    private fun sendWiFiCredentials() {
        val ssid = etSsid.text.toString().trim()
        val password = etPassword.text.toString().trim()

        if (ssid.isEmpty()) {
            Toast.makeText(this, "请输入 WiFi 名称", Toast.LENGTH_SHORT).show()
            return
        }

        showProgress("正在配网...", "正在发送 WiFi 凭证")
        btnStart.isEnabled = false
        provisioningManager.sendSelectedWifi(ssid, password)
    }

    private fun showProgress(status: String, detail: String) {
        llProgress.visibility = View.VISIBLE
        tvProgressStatus.text = status
        tvProgressDetail.text = detail
    }

    private fun hideProgress() {
        llProgress.visibility = View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        // 仅在配网未成功时释放资源
        if (provisioningManager.currentStateValue != BleProvisioningManager.State.SUCCESS) {
            BleProvisioningManager.releaseInstance()
        }
    }
}
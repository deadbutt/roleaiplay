package com.example.iot

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.iot.adapter.BleDeviceAdapter
import com.example.iot.ble.BleProvisioningManager
import com.example.iot.ble.proto.WiFiScanResult

class BleSearchActivity : AppCompatActivity() {

    private lateinit var llSearching: LinearLayout
    private lateinit var llEmpty: LinearLayout
    private lateinit var llDeviceList: LinearLayout
    private lateinit var llConnecting: LinearLayout
    private lateinit var rvBleDevices: RecyclerView
    private lateinit var adapter: BleDeviceAdapter
    private lateinit var tvConnectingStatus: TextView

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }

    private val scanResults = mutableListOf<ScanResult>()
    private val handler = Handler(Looper.getMainLooper())
    private var isScanning = false

    private lateinit var provisioningManager: BleProvisioningManager
    private var selectedDeviceName: String = ""
    private var selectedDeviceAddress: String = ""

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let {
                // 添加调试日志
                Log.d("BleSearch", "Found device: ${it.device.name} (${it.device.address})")

                // 手动过滤：只显示名称以 "PROV_" 开头的设备
                val deviceName = it.device.name ?: ""
                if (deviceName.startsWith("PROV_", ignoreCase = true)) {
                    if (!scanResults.any { r -> r.device.address == it.device.address }) {
                        scanResults.add(it)
                        adapter.updateDevices(scanResults)
                        showDeviceList()
                        Log.d("BleSearch", "Added PROV device: $deviceName")
                    }
                }
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { result ->
                Log.d("BleSearch", "Batch result: ${result.device.name} (${result.device.address})")

                val deviceName = result.device.name ?: ""
                if (deviceName.startsWith("PROV_", ignoreCase = true)) {
                    if (!scanResults.any { it.device.address == result.device.address }) {
                        scanResults.add(result)
                        Log.d("BleSearch", "Added PROV device in batch: $deviceName")
                    }
                }
            }
            adapter.updateDevices(scanResults)
            if (scanResults.isNotEmpty()) {
                showDeviceList()
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("BleSearch", "Scan failed with error: $errorCode")
            showEmpty()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ble_search)

        initViews()
        initProvisioningManager()
        checkPermissionsAndStart()
    }

    private fun initViews() {
        llSearching = findViewById(R.id.ll_searching)
        llEmpty = findViewById(R.id.ll_empty)
        llDeviceList = findViewById(R.id.ll_device_list)
        llConnecting = findViewById(R.id.ll_connecting)
        rvBleDevices = findViewById(R.id.rv_ble_devices)
        tvConnectingStatus = findViewById(R.id.tv_connecting_status)

        adapter = BleDeviceAdapter()
        rvBleDevices.layoutManager = LinearLayoutManager(this)
        rvBleDevices.adapter = adapter

        findViewById<View>(R.id.iv_back).setOnClickListener { finish() }
        findViewById<View>(R.id.btn_retry).setOnClickListener { startScan() }
        findViewById<View>(R.id.btn_connect).setOnClickListener { onConnectClick() }
    }

    private fun initProvisioningManager() {
        provisioningManager = BleProvisioningManager.getInstance(this)
        provisioningManager.setCallback(object : BleProvisioningManager.ProvisioningCallback {
            override fun onStatusUpdate(status: String, detail: String) {
                runOnUiThread {
                    tvConnectingStatus.text = "$status\n$detail"
                }
            }

            override fun onSuccess(deviceId: String, ipAddress: String?) {
                // 配网成功（在 WifiConfigActivity 发送凭证后触发，这里不会走到）
            }

            override fun onError(error: String) {
                runOnUiThread {
                    Toast.makeText(this@BleSearchActivity, "配网失败: $error", Toast.LENGTH_LONG).show()
                    showDeviceList()
                }
            }

            override fun onWifiScanReady(results: List<WiFiScanResult>) {
                runOnUiThread {
                    // 握手+扫描完成，跳转 WifiConfigActivity 展示 WiFi 列表
                    val intent = Intent(this@BleSearchActivity, WifiConfigActivity::class.java).apply {
                        putExtra("device_name", selectedDeviceName)
                        putExtra("device_address", selectedDeviceAddress)
                    }
                    startActivity(intent)
                    finish()
                }
            }
        })
    }

    private fun checkPermissionsAndStart() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), PERMISSION_REQUEST_CODE)
        } else {
            startScan()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startScan()
            } else {
                Toast.makeText(this, "需要蓝牙权限才能搜索设备", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun startScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            Toast.makeText(this, "请开启蓝牙", Toast.LENGTH_SHORT).show()
            return
        }

        scanResults.clear()
        adapter.updateDevices(emptyList())
        showSearching()

        val scanner = bluetoothAdapter!!.bluetoothLeScanner ?: run {
            showEmpty()
            return
        }

        // 不使用过滤器，扫描所有设备（在回调中手动过滤）
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        isScanning = true
        Log.d("BleSearch", "Starting BLE scan without filter")
        scanner.startScan(null, settings, scanCallback)

        // 10 秒超时
        handler.postDelayed({
            if (isScanning) {
                stopScan()
                if (scanResults.isEmpty()) {
                    showEmpty()
                    Log.d("BleSearch", "Scan timeout, no PROV devices found")
                }
            }
        }, SCAN_TIMEOUT)
    }

    private fun stopScan() {
        if (!isScanning) return
        isScanning = false
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun onConnectClick() {
        val selected = adapter.getSelectedDevice()
        if (selected == null) {
            Toast.makeText(this, "请先选择一个设备", Toast.LENGTH_SHORT).show()
            return
        }

        stopScan()
        showConnecting()

        selectedDeviceName = selected.device.name ?: "Unknown"
        selectedDeviceAddress = selected.device.address

        // 开始 BLE 连接 + Security 1 握手 + WiFi 扫描
        tvConnectingStatus.text = "正在连接设备...\n建立 BLE 连接"
        provisioningManager.startProvisioning(selectedDeviceAddress)
    }

    private fun showSearching() {
        llSearching.visibility = View.VISIBLE
        llEmpty.visibility = View.GONE
        llDeviceList.visibility = View.GONE
        llConnecting.visibility = View.GONE
    }

    private fun showEmpty() {
        llSearching.visibility = View.GONE
        llEmpty.visibility = View.VISIBLE
        llDeviceList.visibility = View.GONE
        llConnecting.visibility = View.GONE
    }

    private fun showDeviceList() {
        llSearching.visibility = View.GONE
        llEmpty.visibility = View.GONE
        llDeviceList.visibility = View.VISIBLE
        llConnecting.visibility = View.GONE
    }

    private fun showConnecting() {
        llSearching.visibility = View.GONE
        llEmpty.visibility = View.GONE
        llDeviceList.visibility = View.GONE
        llConnecting.visibility = View.VISIBLE
    }

    override fun onDestroy() {
        super.onDestroy()
        stopScan()
        handler.removeCallbacksAndMessages(null)
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
        private const val SCAN_TIMEOUT = 10000L
    }
}
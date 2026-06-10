package com.example.iot.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.iot.ble.proto.*
import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * BLE 配网管理器 - 完整异步实现
 * 
 * 状态机流程：
 * 1. 连接设备 → 发现服务
 * 2. Security 1 握手 (Command0 → Response0 → Command1 → Response1)
 * 3. 发送 WiFi 凭证 (CmdSetConfig → RespSetConfig)
 * 4. 轮询连接状态 (CmdGetStatus → RespGetStatus)
 */
class BleProvisioningManager(private val context: Context) {

    companion object {
        private const val TAG = "BleProvisioning"

        // UUIDs from actual device (from BLE scan logs)
        val SERVICE_UUID = UUID.fromString("021a9004-0382-4aea-bff4-6b3f1c5adfb4")

        // 兼容旧设备：保留硬编码 UUID 作为 fallback
        val SESSION_UUID = UUID.fromString("021aff4f-0382-4aea-bff4-6b3f1c5adfb4")  // Session (安全握手)
        val SCAN_UUID = UUID.fromString("021aff50-0382-4aea-bff4-6b3f1c5adfb4")     // Scan (WiFi扫描)
        val CONFIG_UUID = UUID.fromString("021aff51-0382-4aea-bff4-6b3f1c5adfb4")   // Config (WiFi配置)
        val VERSION_UUID = UUID.fromString("021aff52-0382-4aea-bff4-6b3f1c5adfb4")  // Version (协议版本)

        // BLE Notify Descriptor UUID
        val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        // Characteristic User Description Descriptor UUID (0x2901)
        val USER_DESC_UUID = UUID.fromString("00002901-0000-1000-8000-00805f9b34fb")

        private const val POP = "abcd1234"
        private const val STATUS_POLL_INTERVAL = 2000L  // 2 seconds
        private const val STATUS_POLL_MAX_ATTEMPTS = 30  // 60 seconds total

        // 单例，跨 Activity 共享加密会话
        @Volatile
        private var instance: BleProvisioningManager? = null

        fun getInstance(context: Context): BleProvisioningManager {
            return instance ?: synchronized(this) {
                instance ?: BleProvisioningManager(context.applicationContext).also { instance = it }
            }
        }

        fun releaseInstance() {
            instance?.disconnect()
            instance = null
        }
    }

    // 保存最近一次 WiFi 扫描结果，供 WifiConfigActivity 读取
    var lastScanResults: List<WiFiScanResult> = emptyList()
        private set

    // 状态机状态
    enum class State {
        IDLE,
        CONNECTING,
        DISCOVERING_SERVICES,
        HANDSHAKE_STEP1_SEND_PUBKEY,
        HANDSHAKE_STEP1_WAIT_RESPONSE,
        HANDSHAKE_STEP2_SEND_PROOF,
        HANDSHAKE_STEP2_WAIT_RESPONSE,
        SCANNING_WIFI,
        SEND_WIFI_CONFIG,
        WAIT_WIFI_CONFIG_RESPONSE,
        POLL_STATUS,
        SUCCESS,
        FAILED
    }

    private var currentState = State.IDLE
    val currentStateValue: State get() = currentState
    private val lazySodium = LazySodiumAndroid(SodiumAndroid())
    private var bluetoothGatt: BluetoothGatt? = null
    private var sharedKey: ByteArray? = null
    private var clientPrivateKey: ByteArray? = null
    private var clientPublicKey: ByteArray? = null
    private var devicePublicKey: ByteArray? = null
    private var deviceSalt: ByteArray? = null
    private var sessionCipher: Cipher? = null  // 保持 AES-CTR 计数器状态
    private var currentDeviceAddress: String? = null

    private val handler = Handler(Looper.getMainLooper())
    private val descriptorReadQueue = mutableListOf<BluetoothGattDescriptor>()
    private val descriptorNameMap = mutableMapOf<UUID, String>()
    private var currentService: BluetoothGattService? = null
    private var statusPollAttempts = 0
    private var targetSsid: String = ""
    private var targetPassword: String = ""

    // GATT Characteristics
    private var sessionCharacteristic: BluetoothGattCharacteristic? = null
    private var configCharacteristic: BluetoothGattCharacteristic? = null
    private var scanCharacteristic: BluetoothGattCharacteristic? = null
    private var versionCharacteristic: BluetoothGattCharacteristic? = null

    // 等待响应的 latch
    private var responseLatch: CountDownLatch? = null
    private var responseData: ByteArray? = null
    private var isReadingScanResults = false

    interface ProvisioningCallback {
        fun onStatusUpdate(status: String, detail: String)
        fun onWifiScanReady(results: List<WiFiScanResult>)
        fun onSuccess(deviceId: String, ipAddress: String?)
        fun onError(error: String)
    }

    interface WiFiScanCallback {
        fun onScanStarted()
        fun onScanResults(results: List<WiFiScanResult>)
        fun onScanError(error: String)
    }

    private var callback: ProvisioningCallback? = null
    private var wifiScanCallback: WiFiScanCallback? = null

    fun setCallback(cb: ProvisioningCallback) {
        this.callback = cb
    }

    fun setWiFiScanCallback(cb: WiFiScanCallback) {
        this.wifiScanCallback = cb
    }

    /**
     * 发送用户选择的 WiFi 凭证（配网流程中扫描完成后调用）
     */
    fun sendSelectedWifi(ssid: String, password: String) {
        targetSsid = ssid
        targetPassword = password
        Log.d(TAG, "sendSelectedWifi called - SSID: \"$ssid\", Password: \"${if (password.isNotEmpty()) "***" else ""}\"")
        sendWiFiConfig()
    }

    /**
     * 启动 WiFi 扫描
     * 需要先连接设备并完成 Security 1 握手
     */
    fun startWiFiScan(deviceAddress: String) {
        if (currentState != State.IDLE) {
            wifiScanCallback?.onScanError("配网正在进行中")
            return
        }

        val device = BluetoothAdapter.getDefaultAdapter()?.getRemoteDevice(deviceAddress)
        if (device == null) {
            wifiScanCallback?.onScanError("无法获取蓝牙设备")
            return
        }

        currentState = State.CONNECTING
        wifiScanCallback?.onScanStarted()

        bluetoothGatt = device.connectGatt(context, false, wifiScanGattCallback)
    }

    fun startProvisioning(deviceAddress: String) {
        if (currentState != State.IDLE) {
            callback?.onError("配网正在进行中")
            return
        }

        currentDeviceAddress = deviceAddress

        val device = BluetoothAdapter.getDefaultAdapter()?.getRemoteDevice(deviceAddress)
        if (device == null) {
            callback?.onError("无法获取蓝牙设备")
            return
        }

        currentState = State.CONNECTING
        callback?.onStatusUpdate("正在连接设备...", "建立 BLE 连接")

        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    /**
     * 通过 Descriptor 0x2901 (Characteristic User Description) 动态匹配特征
     */
    private fun resolveCharacteristicsByDescriptor(service: BluetoothGattService) {
        // 先清空
        sessionCharacteristic = null
        configCharacteristic = null
        scanCharacteristic = null
        versionCharacteristic = null

        for (char in service.characteristics) {
            val desc = char.getDescriptor(USER_DESC_UUID)
            if (desc != null) {
                val name = desc.value?.toString(Charsets.UTF_8)?.trim() ?: ""
                Log.d(TAG, "Characteristic ${char.uuid} has user desc: '$name'")
                when (name) {
                    "prov-session" -> sessionCharacteristic = char
                    "prov-scan"    -> scanCharacteristic = char
                    "prov-config"  -> configCharacteristic = char
                    "proto-ver"    -> versionCharacteristic = char
                }
            }
        }

        // fallback: 如果通过描述符没找到，再用硬编码 UUID
        if (sessionCharacteristic == null) {
            sessionCharacteristic = service.getCharacteristic(SESSION_UUID)
            if (sessionCharacteristic != null) Log.w(TAG, "Fallback to hardcoded SESSION_UUID")
        }
        if (scanCharacteristic == null) {
            scanCharacteristic = service.getCharacteristic(SCAN_UUID)
            if (scanCharacteristic != null) Log.w(TAG, "Fallback to hardcoded SCAN_UUID")
        }
        if (configCharacteristic == null) {
            configCharacteristic = service.getCharacteristic(CONFIG_UUID)
            if (configCharacteristic != null) Log.w(TAG, "Fallback to hardcoded CONFIG_UUID")
        }
        if (versionCharacteristic == null) {
            versionCharacteristic = service.getCharacteristic(VERSION_UUID)
            if (versionCharacteristic != null) Log.w(TAG, "Fallback to hardcoded VERSION_UUID")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Connection state change failed: $status")
                handleError("蓝牙连接失败 (status: $status)")
                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "Connected to GATT server")
                    callback?.onStatusUpdate("已连接", "正在协商MTU...")
                    // 先请求更大的 MTU，避免后续写入数据过长导致 Status=133
                    gatt.requestMtu(512)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Disconnected from GATT server")
                    if (currentState != State.SUCCESS) {
                        handleError("蓝牙连接已断开")
                    }
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.d(TAG, "MTU changed to $mtu, status=$status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                currentState = State.DISCOVERING_SERVICES
                callback?.onStatusUpdate("已连接", "正在发现服务...")
                gatt.discoverServices()
            } else {
                Log.w(TAG, "MTU request failed ($status), proceeding with default MTU")
                currentState = State.DISCOVERING_SERVICES
                callback?.onStatusUpdate("已连接", "正在发现服务...")
                gatt.discoverServices()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                handleError("服务发现失败")
                return
            }

            // 打印所有服务和特征（方便调试UUID）
            Log.d(TAG, "=== All discovered services ===")
            gatt.services.forEach { svc ->
                Log.d(TAG, "Service UUID: ${svc.uuid}")
                svc.characteristics.forEach { char ->
                    Log.d(TAG, "  Char UUID: ${char.uuid}")
                    char.descriptors.forEach { desc ->
                        Log.d(TAG, "    Desc UUID: ${desc.uuid}, value=${desc.value?.let { String(it) } ?: "null"}")
                    }
                }
            }

            var service = gatt.getService(SERVICE_UUID)
            if (service == null) {
                // fallback: 遍历所有服务，找包含 prov-session 描述符特征的服务
                for (svc in gatt.services) {
                    for (char in svc.characteristics) {
                        val desc = char.getDescriptor(USER_DESC_UUID)
                        if (desc != null) {
                            val name = desc.value?.toString(Charsets.UTF_8)?.trim() ?: ""
                            if (name == "prov-session") {
                                Log.w(TAG, "Service UUID mismatch! Expected: $SERVICE_UUID, actual: ${svc.uuid}")
                                service = svc
                                break
                            }
                        }
                    }
                    if (service != null) break
                }
            }

            if (service == null) {
                val allServiceUuids = gatt.services.joinToString(", ") { it.uuid.toString() }
                Log.e(TAG, "All service UUIDs: $allServiceUuids")
                handleError("未找到配网服务。期望: $SERVICE_UUID, 实际: $allServiceUuids")
                return
            }

            // 通过异步读取 Descriptor 0x2901 来动态匹配特征
            currentService = service
            descriptorNameMap.clear()
            descriptorReadQueue.clear()
            for (char in service.characteristics) {
                val desc = char.getDescriptor(USER_DESC_UUID)
                if (desc != null) {
                    descriptorReadQueue.add(desc)
                }
            }
            Log.d(TAG, "Starting to read ${descriptorReadQueue.size} descriptors...")
            readNextDescriptor(gatt)
        }

        private fun readNextDescriptor(gatt: BluetoothGatt) {
            if (descriptorReadQueue.isEmpty()) {
                onAllDescriptorsRead(gatt)
                return
            }
            val desc = descriptorReadQueue.removeAt(0)
            val success = gatt.readDescriptor(desc)
            Log.d(TAG, "readDescriptor ${desc.characteristic.uuid} -> success=$success")
        }

        private fun onAllDescriptorsRead(gatt: BluetoothGatt) {
            Log.d(TAG, "All descriptors read: $descriptorNameMap")

            // 根据 Descriptor 0x2901 的值匹配特征
            for (char in currentService!!.characteristics) {
                val name = descriptorNameMap[char.uuid] ?: ""
                when (name) {
                    "prov-session" -> sessionCharacteristic = char
                    "prov-scan"    -> scanCharacteristic = char
                    "prov-config"  -> configCharacteristic = char
                    "proto-ver"    -> versionCharacteristic = char
                }
            }

            // fallback: 如果 descriptor 匹配失败，用硬编码 UUID
            if (sessionCharacteristic == null) {
                Log.w(TAG, "Fallback to hardcoded SESSION_UUID")
                sessionCharacteristic = currentService!!.getCharacteristic(SESSION_UUID)
            }
            if (scanCharacteristic == null) {
                Log.w(TAG, "Fallback to hardcoded SCAN_UUID")
                scanCharacteristic = currentService!!.getCharacteristic(SCAN_UUID)
            }
            if (configCharacteristic == null) {
                Log.w(TAG, "Fallback to hardcoded CONFIG_UUID")
                configCharacteristic = currentService!!.getCharacteristic(CONFIG_UUID)
            }
            if (versionCharacteristic == null) {
                Log.w(TAG, "Fallback to hardcoded VERSION_UUID")
                versionCharacteristic = currentService!!.getCharacteristic(VERSION_UUID)
            }

            if (sessionCharacteristic == null || configCharacteristic == null) {
                handleError("未找到必要的 GATT 特征 (session=$sessionCharacteristic, config=$configCharacteristic)")
                return
            }

            Log.d(TAG, "Session=${sessionCharacteristic!!.uuid}, Config=${configCharacteristic!!.uuid}")
            Log.d(TAG, "Session props=${sessionCharacteristic?.properties}, Config props=${configCharacteristic?.properties}")

            // 按照文档流程：先启用Notify/Indicate，然后尝试Security 1握手
            Log.d(TAG, "Enabling notifications...")
            enableNotifications(gatt, configCharacteristic!!)
            if (scanCharacteristic != null) {
                enableNotifications(gatt, scanCharacteristic!!)
            }

            // 无论是否支持notify，都先尝试Security 1握手
            // 如果设备不支持notify，响应会通过readCharacteristic读取
            currentState = State.DISCOVERING_SERVICES
            bluetoothGatt = gatt
            handler.post {
                startSecurity1Handshake(gatt)
            }
        }

        override fun onDescriptorRead(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val name = descriptor.value?.toString(Charsets.UTF_8)?.trim() ?: ""
                Log.d(TAG, "Descriptor read: ${descriptor.characteristic.uuid} -> '$name'")
                descriptorNameMap[descriptor.characteristic.uuid] = name
            } else {
                Log.w(TAG, "Descriptor read failed for ${descriptor.characteristic.uuid}: $status")
            }
            readNextDescriptor(gatt)
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Write failed for ${characteristic.uuid}: $status")
                handleError("写入失败")
                return
            }
            Log.d(TAG, "Write success for ${characteristic.uuid}")

            // 触发响应等待
            responseLatch?.countDown()
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Read failed for ${characteristic.uuid}: $status")
                if (isReadingScanResults) {
                    isReadingScanResults = false
                    wifiScanCallback?.onScanError("读取WiFi扫描结果失败")
                } else {
                    handleError("读取失败")
                }
                return
            }
            
            Log.d(TAG, "Read success for ${characteristic.uuid}, data length: ${characteristic.value?.size ?: 0}")

            // 扫描结果读取
            if (isReadingScanResults) {
                isReadingScanResults = false
                characteristic.value?.let { handleWiFiScanResult(it) }
                return
            }

            responseData = characteristic.value
            responseLatch?.countDown()
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            Log.d(TAG, "Notify received for ${characteristic.uuid}, data length: ${characteristic.value?.size ?: 0}")
            
            // Notify 数据处理
            when (characteristic.uuid) {
                CONFIG_UUID -> {
                    handleConfigNotify(characteristic.value)
                }
                SCAN_UUID -> {
                    handleWiFiScanNotify(characteristic.value)
                }
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Descriptor write failed: $status")
                handleError("启用通知失败")
                return
            }
            Log.d(TAG, "Descriptor write success: notifications enabled")

            // 通知启用完成，计数器减1
            notifyEnableLatch?.countDown()
        }
    }

    private fun enableNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic): Boolean {
        val descriptor = characteristic.getDescriptor(CCCD_UUID) ?: return false
        return when {
            characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 -> {
                gatt.setCharacteristicNotification(characteristic, true)
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)
                true
            }
            characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0 -> {
                gatt.setCharacteristicNotification(characteristic, true)
                descriptor.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                gatt.writeDescriptor(descriptor)
                true
            }
            else -> false
        }
    }

    // 等待通知启用完成的方法
    private var notifyEnableLatch = CountDownLatch(0)
    private var notifyEnableCount = 0

    private fun enableNotificationsAndWait(
        gatt: BluetoothGatt,
        sessionChar: BluetoothGattCharacteristic,
        scanChar: BluetoothGattCharacteristic,
        onComplete: () -> Unit
    ) {
        notifyEnableCount = 0
        notifyEnableLatch = CountDownLatch(2)  // 需要启用2个特征的通知

        // 启用 sessionCharacteristic 的通知
        if (sessionChar.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
            val descriptor = sessionChar.getDescriptor(CCCD_UUID)
            if (descriptor != null) {
                Log.d(TAG, "Enabling notification for session characteristic")
                gatt.setCharacteristicNotification(sessionChar, true)
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)
            } else {
                Log.e(TAG, "CCCD descriptor not found for session characteristic")
                notifyEnableLatch.countDown()
            }
        } else {
            Log.d(TAG, "Session characteristic does not support NOTIFY")
            notifyEnableLatch.countDown()
        }

        // 启用 scanCharacteristic 的通知
        if (scanChar.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
            val descriptor = scanChar.getDescriptor(CCCD_UUID)
            if (descriptor != null) {
                Log.d(TAG, "Enabling notification for scan characteristic")
                gatt.setCharacteristicNotification(scanChar, true)
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)
            } else {
                Log.e(TAG, "CCCD descriptor not found for scan characteristic")
                notifyEnableLatch.countDown()
            }
        } else {
            Log.d(TAG, "Scan characteristic does not support NOTIFY")
            notifyEnableLatch.countDown()
        }

        // 等待通知启用完成
        Thread {
            try {
                notifyEnableLatch.await(5000, TimeUnit.MILLISECONDS)
                Log.d(TAG, "All notifications enabled")
                handler.post { onComplete() }
            } catch (e: Exception) {
                Log.e(TAG, "Timeout waiting for notifications to enable")
                handler.post { onComplete() }
            }
        }.start()
    }

    private fun startSecurity1Handshake(gatt: BluetoothGatt) {
        currentState = State.HANDSHAKE_STEP1_SEND_PUBKEY
        callback?.onStatusUpdate("安全握手...", "正在生成密钥")

        try {
            // Step 1: Generate Curve25519 keypair
            val keypair = lazySodium.cryptoKxKeypair()
            clientPublicKey = keypair.publicKey.asBytes
            clientPrivateKey = keypair.secretKey.asBytes

            Log.d(TAG, "Client public key generated: ${clientPublicKey!!.size} bytes")

            // Step 2: Build and send SessionData with Command0
            val sessionData = SessionData()
            sessionData.secVer = 1  // SecScheme1
            
            val sec1 = Sec1Payload()
            sec1.msg = 0  // Session_Command0
            val sc0 = SessionCmd0()
            sc0.clientPubkey = clientPublicKey!!
            sec1.sc0 = sc0
            
            sessionData.sec1 = sec1

            val encodedData = sessionData.encode()
            Log.d(TAG, "Sending Command0, data length: ${encodedData.size}")

            writeAndWaitForResponse(gatt, sessionCharacteristic!!, encodedData, 5000) { response ->
                handleHandshakeResponse0(response)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Handshake step 1 failed", e)
            handleError("密钥生成失败: ${e.message}")
        }
    }

    private fun handleHandshakeResponse0(response: ByteArray?) {
        if (response == null || response.isEmpty()) {
            handleError("握手响应为空")
            return
        }

        currentState = State.HANDSHAKE_STEP1_WAIT_RESPONSE
        Log.d(TAG, "Received Response0, data length: ${response.size}")

        try {
            val sessionData = SessionData.decode(response)
            val sr0 = sessionData.sec1?.sr0

            if (sr0 == null) {
                handleError("Response0 解析失败")
                return
            }

            if (sr0.status != 0) {
                handleError("握手失败: status=${sr0.status}")
                return
            }

            devicePublicKey = sr0.devicePubkey
            deviceSalt = sr0.deviceSalt

            Log.d(TAG, "Device public key: ${devicePublicKey!!.size} bytes")
            Log.d(TAG, "Device salt: ${deviceSalt!!.size} bytes")

            // Step 3: Compute shared key
            sharedKey = computeSharedKey(clientPrivateKey!!, devicePublicKey!!)
            Log.d(TAG, "Shared key computed: ${sharedKey!!.size} bytes")

            // Step 3.5: XOR shared key with SHA256(pop) → final key (ESP-IDF security1.c algorithm)
            val popBytes = POP.toByteArray()
            val sha256Pop = MessageDigest.getInstance("SHA-256").digest(popBytes)
            val finalKey = ByteArray(32)
            for (i in 0 until 32) {
                finalKey[i] = (sharedKey!![i].toInt() xor sha256Pop[i].toInt()).toByte()
            }
            sharedKey = finalKey
            Log.d(TAG, "Final key (XOR SHA256(PoP)) computed: ${sharedKey!!.size} bytes")

            // Step 4: client_verify_data = AES-CTR(final_key, device_random, device_pubkey) → 32 bytes
            // 创建会话 Cipher 并保持 CTR 状态连续
            sessionCipher = Cipher.getInstance("AES/CTR/NoPadding")
            sessionCipher!!.init(Cipher.ENCRYPT_MODE,
                SecretKeySpec(sharedKey!!, "AES"),
                IvParameterSpec(deviceSalt!!))
            val proof = sessionCipher!!.update(devicePublicKey!!)
            Log.d(TAG, "Client verify data generated: ${proof.size} bytes")

            // Step 5: Build and send Command1
            currentState = State.HANDSHAKE_STEP2_SEND_PROOF
            callback?.onStatusUpdate("安全握手...", "验证配网密码")

            val sessionData2 = SessionData()
            sessionData2.secVer = 1
            
            val sec1_2 = Sec1Payload()
            sec1_2.msg = 2  // Session_Command1
            
            val sc1 = SessionCmd1()
            sc1.clientProof = proof
            sec1_2.sc1 = sc1
            
            sessionData2.sec1 = sec1_2

            val encodedData2 = sessionData2.encode()
            Log.d(TAG, "Sending Command1, data length: ${encodedData2.size}")

            writeAndWaitForResponse(bluetoothGatt!!, sessionCharacteristic!!, encodedData2, 5000) { response2 ->
                handleHandshakeResponse1(response2)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Response0 handling failed", e)
            handleError("握手响应处理失败: ${e.message}")
        }
    }

    private fun handleHandshakeResponse1(response: ByteArray?) {
        if (response == null || response.isEmpty()) {
            handleError("PoP 验证响应为空")
            return
        }

        currentState = State.HANDSHAKE_STEP2_WAIT_RESPONSE
        Log.d(TAG, "Received Response1, data length: ${response.size}")

        try {
            val sessionData = SessionData.decode(response)
            val sr1 = sessionData.sec1?.sr1

            if (sr1 == null) {
                handleError("Response1 解析失败")
                return
            }

            Log.d(TAG, "Response1 status: ${sr1.status}")
            Log.d(TAG, "Response1 deviceProof length: ${sr1.deviceProof.size} bytes")

            if (sr1.status != 0) {
                handleError("配网密码验证失败 (PoP 错误)")
                return
            }

            // Verify device_verify_data: AES-CTR-decrypt with same cipher → should equal client_pubkey
            val expected = sessionCipher!!.update(sr1.deviceProof)
            Log.d(TAG, "Decrypted device verify: ${expected.size} bytes")
            if (!expected.contentEquals(clientPublicKey!!)) {
                handleError("设备验证失败: 公钥不匹配")
                return
            }

            Log.d(TAG, "Security 1 handshake completed successfully")

            // 握手成功，跳转到 WiFi 配置界面让用户手动输入 SSID 和密码
            callback?.onStatusUpdate("已连接", "设备握手成功")
            callback?.onWifiScanReady(emptyList())

        } catch (e: Exception) {
            Log.e(TAG, "Response1 handling failed", e)
            handleError("PoP 验证处理失败: ${e.message}")
        }
    }

    private fun sendWiFiConfig(useSecurity: Boolean = true) {
        currentState = State.SEND_WIFI_CONFIG
        callback?.onStatusUpdate("配网中...", "正在发送 WiFi 凭证")

        try {
            val configPayload = WiFiConfigPayload()
            configPayload.msg = 2  // TypeCmdSetConfig
            
            val cmdSetConfig = CmdSetConfig()
            cmdSetConfig.ssid = targetSsid
            cmdSetConfig.passphrase = targetPassword
            cmdSetConfig.bssid = ByteArray(6)  // All zeros
            
            configPayload.cmdSetConfig = cmdSetConfig

            val encodedPayload = configPayload.encode()
            Log.d(TAG, "WiFi config payload: ${encodedPayload.size} bytes")
            Log.d(TAG, "Sending WiFi credentials - SSID: \"$targetSsid\", Password: \"${targetPassword}\"")

            val payloadToSend = if (useSecurity) {
                val encryptedPayload = encryptPayload(encodedPayload)
                Log.d(TAG, "Encrypted WiFi config: ${encryptedPayload.size} bytes")
                encryptedPayload
            } else {
                Log.d(TAG, "Sending WiFi config without encryption (Security 0)")
                encodedPayload
            }

            writeAndWaitForNotify(bluetoothGatt!!, configCharacteristic!!, payloadToSend, 5000) { response ->
                handleWiFiConfigResponse(response)
            }

        } catch (e: Exception) {
            Log.e(TAG, "WiFi config send failed", e)
            handleError("发送 WiFi 凭证失败: ${e.message}")
        }
    }

    private fun handleWiFiConfigResponse(response: ByteArray?) {
        if (response == null || response.isEmpty()) {
            // 没有响应，可能设备已开始连接，直接进入状态轮询
            Log.d(TAG, "No WiFi config response, starting status poll")
            startStatusPoll()
            return
        }

        currentState = State.WAIT_WIFI_CONFIG_RESPONSE
        Log.d(TAG, "Received WiFi config response, data length: ${response.size}")

        try {
            val decrypted = decryptPayload(response)
            Log.d(TAG, "Decrypted WiFi config response: ${decrypted.size} bytes, hex=${decrypted.joinToString("") { "%02X".format(it) }}")

            // 解析 RespSetConfig (status field)
            try {
                ProtoDecoder.forEachField(decrypted) { fieldNumber, wireType, fieldData ->
                    if (fieldNumber == 1 && wireType == 0) { // status
                        val (status, _) = ProtoDecoder.decodeVarint(fieldData, 0)
                        Log.d(TAG, "RespSetConfig status: $status")
                    }
                    true
                }
            } catch (e: Exception) {
                Log.w(TAG, "RespSetConfig parse failed (non-critical): ${e.message}")
            }
            startStatusPoll()

        } catch (e: Exception) {
            Log.e(TAG, "WiFi config response handling failed", e)
            // 即使解析失败，也尝试轮询状态
            startStatusPoll()
        }
    }

    private fun startStatusPoll() {
        currentState = State.POLL_STATUS
        statusPollAttempts = 0
        callback?.onStatusUpdate("配网中...", "等待设备连接 WiFi")
        pollStatus()
    }

    private fun pollStatus() {
        if (statusPollAttempts >= STATUS_POLL_MAX_ATTEMPTS) {
            handleError("配网超时 (60秒)")
            return
        }

        if (currentState != State.POLL_STATUS) {
            return
        }

        statusPollAttempts++
        Log.d(TAG, "Polling status, attempt $statusPollAttempts/$STATUS_POLL_MAX_ATTEMPTS")

        try {
            val configPayload = WiFiConfigPayload()
            configPayload.msg = 0  // TypeCmdGetStatus

            val encodedPayload = configPayload.encode()
            val encryptedPayload = encryptPayload(encodedPayload)

            writeAndWaitForNotify(bluetoothGatt!!, configCharacteristic!!, encryptedPayload, 3000) { response ->
                handleStatusResponse(response)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Status poll failed", e)
            // 继续轮询
            handler.postDelayed({ pollStatus() }, STATUS_POLL_INTERVAL)
        }
    }

    private fun handleStatusResponse(response: ByteArray?) {
        if (response == null || response.isEmpty()) {
            // 继续轮询
            handler.postDelayed({ pollStatus() }, STATUS_POLL_INTERVAL)
            return
        }

        Log.d(TAG, "Received status response, data length: ${response.size}")

        try {
            val decrypted = decryptPayload(response)
            Log.d(TAG, "Decrypted status response: ${decrypted.size} bytes, hex=${decrypted.joinToString("") { "%02X".format(it) }}")

            val configPayload = WiFiConfigPayload.decode(decrypted)
            val respGetStatus = configPayload.respGetStatus

            if (respGetStatus == null) {
                handler.postDelayed({ pollStatus() }, STATUS_POLL_INTERVAL)
                return
            }

            val staState = respGetStatus.staState
            val failReason = respGetStatus.failReason
            val ipAddress = respGetStatus.ip4Addr

            Log.d(TAG, "Status: staState=$staState, failReason=$failReason, ip=$ipAddress")

            when (staState) {
                3 -> { // Connected
                    currentState = State.SUCCESS
                    callback?.onStatusUpdate("配网成功", "设备已连接到 WiFi")
                    callback?.onSuccess(bluetoothGatt?.device?.address ?: "", ipAddress)
                    disconnect()
                }
                0 -> { // Disconnected with error
                    when (failReason) {
                        1 -> handleError("WiFi 密码错误")
                        2 -> handleError("未找到 WiFi 网络")
                        else -> handleError("配网失败 (原因: $failReason)")
                    }
                }
                1 -> { // Scanning
                    callback?.onStatusUpdate("配网中...", "设备正在扫描 WiFi (${statusPollAttempts}/${STATUS_POLL_MAX_ATTEMPTS})")
                    handler.postDelayed({ pollStatus() }, STATUS_POLL_INTERVAL)
                }
                2 -> { // Connecting
                    callback?.onStatusUpdate("配网中...", "设备正在连接 WiFi (${statusPollAttempts}/${STATUS_POLL_MAX_ATTEMPTS})")
                    handler.postDelayed({ pollStatus() }, STATUS_POLL_INTERVAL)
                }
                else -> {
                    handler.postDelayed({ pollStatus() }, STATUS_POLL_INTERVAL)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Status response parsing failed", e)
            handler.postDelayed({ pollStatus() }, STATUS_POLL_INTERVAL)
        }
    }

    private fun handleConfigNotify(data: ByteArray?) {
        if (currentState == State.POLL_STATUS) {
            handleStatusResponse(data)
        } else if (currentState == State.WAIT_WIFI_CONFIG_RESPONSE) {
            handleWiFiConfigResponse(data)
        }
    }

    // 加密辅助方法 - 每消息随机 IV 模式
    private fun encryptPayload(data: ByteArray): ByteArray {
        val key = sharedKey ?: throw IllegalStateException("Session key not initialized")
        val iv = ByteArray(16)
        SecureRandom.getInstanceStrong().nextBytes(iv)
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        val encrypted = cipher.doFinal(data)
        return iv + encrypted  // [16字节随机IV] + [密文]
    }

    private fun decryptPayload(data: ByteArray): ByteArray {
        if (data.size < 16) {
            Log.w(TAG, "decryptPayload: data too short (${data.size}B), missing IV")
            return data
        }
        val key = sharedKey ?: throw IllegalStateException("Session key not initialized")
        val iv = data.copyOfRange(0, 16)
        val encrypted = data.copyOfRange(16, data.size)
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(encrypted)
    }

    private fun encryptWithAesCtr(key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        val keySpec = SecretKeySpec(key, "AES")
        val ivSpec = IvParameterSpec(iv)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        return cipher.doFinal(data)
    }

    private fun computeSharedKey(privateKey: ByteArray, publicKey: ByteArray): ByteArray {
        val sharedSecret = ByteArray(32)
        lazySodium.cryptoScalarMult(sharedSecret, privateKey, publicKey)
        return sharedSecret
    }

    // 异步写入并等待响应
    private fun writeAndWaitForResponse(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        data: ByteArray,
        timeoutMs: Long,
        onResponse: (ByteArray?) -> Unit
    ) {
        responseLatch = CountDownLatch(1)
        responseData = null

        // 检查特征属性
        val properties = characteristic.properties
        Log.d(TAG, "Characteristic properties: $properties")
        Log.d(TAG, "  PROPERTY_WRITE: ${properties and BluetoothGattCharacteristic.PROPERTY_WRITE}")
        Log.d(TAG, "  PROPERTY_WRITE_NO_RESPONSE: ${properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE}")

        // 根据特征属性选择写入类型
        val writeType = if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) {
            Log.d(TAG, "Using WRITE_TYPE_NO_RESPONSE")
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        } else {
            Log.d(TAG, "Using WRITE_TYPE_DEFAULT")
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }

        characteristic.value = data
        characteristic.writeType = writeType
        val writeSuccess = gatt.writeCharacteristic(characteristic)

        if (!writeSuccess) {
            Log.e(TAG, "Write characteristic failed immediately")
            onResponse(null)
            return
        }

        Log.d(TAG, "Write characteristic started successfully")

        // 等待写入完成
        Thread {
            try {
                responseLatch?.await(timeoutMs, TimeUnit.MILLISECONDS)

                // 写入成功后，读取响应
                handler.postDelayed({
                    responseLatch = CountDownLatch(1)
                    responseData = null
                    gatt.readCharacteristic(characteristic)

                    Thread {
                        try {
                            responseLatch?.await(timeoutMs, TimeUnit.MILLISECONDS)
                            handler.post { onResponse(responseData) }
                        } catch (e: Exception) {
                            handler.post { onResponse(null) }
                        }
                    }.start()
                }, 200)

            } catch (e: Exception) {
                handler.post { onResponse(null) }
            }
        }.start()
    }

    // 异步写入并等待 Notify
    private fun writeAndWaitForNotify(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        data: ByteArray,
        timeoutMs: Long,
        onNotify: (ByteArray?) -> Unit
    ) {
        responseLatch = CountDownLatch(1)
        responseData = null

        // 检查特征属性
        val properties = characteristic.properties
        Log.d(TAG, "Characteristic properties for notify: $properties")

        // 根据特征属性选择写入类型
        val writeType = if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) {
            Log.d(TAG, "Using WRITE_TYPE_NO_RESPONSE for notify")
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        } else {
            Log.d(TAG, "Using WRITE_TYPE_DEFAULT for notify")
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }

        characteristic.value = data
        characteristic.writeType = writeType
        val writeSuccess = gatt.writeCharacteristic(characteristic)

        if (!writeSuccess) {
            Log.e(TAG, "Write characteristic failed immediately for notify")
            onNotify(null)
            return
        }

        Log.d(TAG, "Write characteristic started successfully for notify")

        // 等待 Notify
        Thread {
            try {
                // Notify 会通过 onCharacteristicChanged 回调
                // 这里等待一段时间，让 Notify 回调处理
                Thread.sleep(timeoutMs)

                // 如果没有收到 Notify，尝试读取
                if (responseData == null) {
                    handler.post {
                        responseLatch = CountDownLatch(1)
                        gatt.readCharacteristic(characteristic)

                        Thread {
                            try {
                                responseLatch?.await(timeoutMs, TimeUnit.MILLISECONDS)
                                handler.post { onNotify(responseData) }
                            } catch (e: Exception) {
                                handler.post { onNotify(null) }
                            }
                        }.start()
                    }
                } else {
                    handler.post { onNotify(responseData) }
                }

            } catch (e: Exception) {
                handler.post { onNotify(null) }
            }
        }.start()
    }

    private fun handleError(error: String) {
        currentState = State.FAILED
        Log.e(TAG, "Error: $error")
        callback?.onError(error)
        disconnect()
    }

    fun disconnect() {
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
            bluetoothGatt = null
            currentState = State.IDLE
        } catch (e: Exception) {
            Log.e(TAG, "Disconnect error", e)
        }
    }

    // WiFi 扫描专用的 GATT Callback
    private val wifiScanGattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "WiFi scan connection failed: $status")
                wifiScanCallback?.onScanError("蓝牙连接失败 (status: $status)")
                disconnect()
                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "Connected for WiFi scan")
                    currentState = State.DISCOVERING_SERVICES
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Disconnected after WiFi scan")
                    currentState = State.IDLE
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Service discovery failed with status: $status")
                wifiScanCallback?.onScanError("服务发现失败")
                disconnect()
                return
            }

            Log.d(TAG, "Services discovered successfully")

            // 打印所有服务的UUID（调试用）
            gatt.services.forEach { service ->
                Log.d(TAG, "Service UUID: ${service.uuid}")
                service.characteristics.forEach { char ->
                    Log.d(TAG, "  Characteristic UUID: ${char.uuid}")
                }
            }

            var service = gatt.getService(SERVICE_UUID)
            if (service == null) {
                // fallback: 遍历所有服务，找包含 prov-session 描述符特征的服务
                for (svc in gatt.services) {
                    for (char in svc.characteristics) {
                        val desc = char.getDescriptor(USER_DESC_UUID)
                        if (desc != null) {
                            val name = desc.value?.toString(Charsets.UTF_8)?.trim() ?: ""
                            if (name == "prov-session") {
                                Log.w(TAG, "Service UUID mismatch! Expected: $SERVICE_UUID, actual: ${svc.uuid}")
                                service = svc
                                break
                            }
                        }
                    }
                    if (service != null) break
                }
            }

            if (service == null) {
                val allServiceUuids = gatt.services.joinToString(", ") { it.uuid.toString() }
                Log.e(TAG, "All service UUIDs: $allServiceUuids")
                wifiScanCallback?.onScanError("未找到配网服务。期望: $SERVICE_UUID, 实际: $allServiceUuids")
                disconnect()
                return
            }

            Log.d(TAG, "Found provisioning service: ${service.uuid}")

            // 通过 Descriptor 0x2901 动态匹配特征
            resolveCharacteristicsByDescriptor(service)

            if (sessionCharacteristic == null || scanCharacteristic == null) {
                Log.e(TAG, "Characteristics not found! session=$sessionCharacteristic, scan=$scanCharacteristic")
                wifiScanCallback?.onScanError("未找到必要的 GATT 特征")
                disconnect()
                return
            }

            Log.d(TAG, "Found all characteristics by descriptor")

            // 请求更大的MTU（解决数据大小问题）
            Log.d(TAG, "Requesting MTU size: 512")
            gatt.requestMtu(512)
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.d(TAG, "MTU changed to: $mtu, status: $status")

            // MTU设置完成后，启用Notify
            enableNotificationsAndWait(gatt, sessionCharacteristic!!, scanCharacteristic!!) {
                // Notify启用完成后，开始握手
                Log.d(TAG, "Notifications enabled, starting handshake")
                startSecurity1HandshakeForScan(gatt)
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Write failed for ${characteristic.uuid}: $status")
                if (characteristic.uuid == SCAN_UUID) {
                    wifiScanCallback?.onScanError("WiFi扫描命令发送失败")
                    disconnect()
                }
                return
            }
            Log.d(TAG, "Write success for ${characteristic.uuid}")
            responseLatch?.countDown()
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            Log.d(TAG, "Notify received for ${characteristic.uuid}, data length: ${characteristic.value?.size ?: 0}")

            when (characteristic.uuid) {
                SCAN_UUID -> {
                    handleWiFiScanNotify(characteristic.value)
                }
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Descriptor write failed: $status")
                wifiScanCallback?.onScanError("启用通知失败")
                disconnect()
                return
            }
            Log.d(TAG, "Descriptor write success for WiFi scan")
        }
    }

    private fun startSecurity1HandshakeForScan(gatt: BluetoothGatt) {
        currentState = State.HANDSHAKE_STEP1_SEND_PUBKEY

        try {
            // Step 1: Generate Curve25519 keypair
            val keypair = lazySodium.cryptoKxKeypair()
            clientPublicKey = keypair.publicKey.asBytes
            clientPrivateKey = keypair.secretKey.asBytes

            Log.d(TAG, "Client public key generated for scan: ${clientPublicKey!!.size} bytes")

            // Step 2: Build and send SessionData with Command0
            val sessionData = SessionData()
            sessionData.secVer = 1  // SecScheme1

            val sec1 = Sec1Payload()
            sec1.msg = 0  // Session_Command0
            val sc0 = SessionCmd0()
            sc0.clientPubkey = clientPublicKey!!
            sec1.sc0 = sc0

            sessionData.sec1 = sec1

            val encodedData = sessionData.encode()
            Log.d(TAG, "Sending Command0 for scan, data length: ${encodedData.size}")
            Log.d(TAG, "Command0 data (hex): ${encodedData.joinToString(" ") { "%02X".format(it) }}")

            // 添加延迟，给设备端准备时间
            handler.postDelayed({
                writeAndWaitForResponse(gatt, sessionCharacteristic!!, encodedData, 5000) { response ->
                    handleHandshakeResponse0ForScan(response)
                }
            }, 1000)  // 延迟1秒

        } catch (e: Exception) {
            Log.e(TAG, "Handshake step 1 failed for scan", e)
            wifiScanCallback?.onScanError("密钥生成失败: ${e.message}")
            disconnect()
        }
    }

    private fun handleHandshakeResponse0ForScan(response: ByteArray?) {
        if (response == null || response.isEmpty()) {
            wifiScanCallback?.onScanError("握手响应为空")
            disconnect()
            return
        }

        currentState = State.HANDSHAKE_STEP1_WAIT_RESPONSE
        Log.d(TAG, "Received Response0 for scan, data length: ${response.size}")

        try {
            val sessionData = SessionData.decode(response)
            val sr0 = sessionData.sec1?.sr0

            if (sr0 == null) {
                wifiScanCallback?.onScanError("Response0 解析失败")
                disconnect()
                return
            }

            if (sr0.status != 0) {
                wifiScanCallback?.onScanError("握手失败: status=${sr0.status}")
                disconnect()
                return
            }

            devicePublicKey = sr0.devicePubkey
            deviceSalt = sr0.deviceSalt

            Log.d(TAG, "Device public key for scan: ${devicePublicKey!!.size} bytes")
            Log.d(TAG, "Device salt for scan: ${deviceSalt!!.size} bytes")

            // Step 3: Compute shared key
            sharedKey = computeSharedKey(clientPrivateKey!!, devicePublicKey!!)
            Log.d(TAG, "Shared key computed for scan: ${sharedKey!!.size} bytes")

            // XOR shared key with SHA256(pop) → final key
            val popBytes = POP.toByteArray()
            val sha256Pop = MessageDigest.getInstance("SHA-256").digest(popBytes)
            val finalKey = ByteArray(32)
            for (i in 0 until 32) {
                finalKey[i] = (sharedKey!![i].toInt() xor sha256Pop[i].toInt()).toByte()
            }
            sharedKey = finalKey
            Log.d(TAG, "Final key for scan (XOR SHA256(PoP)): ${sharedKey!!.size} bytes")

            // client_verify_data = AES-CTR(final_key, device_random, device_pubkey) → 32 bytes
            // 创建会话 Cipher 并保持 CTR 状态连续
            sessionCipher = Cipher.getInstance("AES/CTR/NoPadding")
            sessionCipher!!.init(Cipher.ENCRYPT_MODE,
                SecretKeySpec(sharedKey!!, "AES"),
                IvParameterSpec(deviceSalt!!))
            val proof = sessionCipher!!.update(devicePublicKey!!)
            Log.d(TAG, "Client verify data for scan: ${proof.size} bytes")

            // Step 5: Build and send Command1
            currentState = State.HANDSHAKE_STEP2_SEND_PROOF

            val sessionData2 = SessionData()
            sessionData2.secVer = 1

            val sec1_2 = Sec1Payload()
            sec1_2.msg = 2  // Session_Command1
            val sc1 = SessionCmd1()
            sc1.clientProof = proof
            sec1_2.sc1 = sc1

            sessionData2.sec1 = sec1_2

            val encodedData2 = sessionData2.encode()
            Log.d(TAG, "Sending Command1 for scan, data length: ${encodedData2.size}")

            writeAndWaitForResponse(bluetoothGatt!!, sessionCharacteristic!!, encodedData2, 5000) { response2 ->
                handleHandshakeResponse1ForScan(response2)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Response0 handling failed for scan", e)
            wifiScanCallback?.onScanError("握手响应处理失败: ${e.message}")
            disconnect()
        }
    }

    private fun handleHandshakeResponse1ForScan(response: ByteArray?) {
        if (response == null || response.isEmpty()) {
            wifiScanCallback?.onScanError("PoP 验证响应为空")
            disconnect()
            return
        }

        currentState = State.HANDSHAKE_STEP2_WAIT_RESPONSE
        Log.d(TAG, "Received Response1 for scan, data length: ${response.size}")

        try {
            val sessionData = SessionData.decode(response)
            val sr1 = sessionData.sec1?.sr1

            if (sr1 == null) {
                wifiScanCallback?.onScanError("Response1 解析失败")
                disconnect()
                return
            }

            if (sr1.status != 0) {
                wifiScanCallback?.onScanError("配网密码验证失败 (PoP 错误)")
                disconnect()
                return
            }

            // Verify device_verify_data with same cipher
            val expected = sessionCipher!!.update(sr1.deviceProof)
            Log.d(TAG, "Decrypted device verify for scan: ${expected.size} bytes")
            if (!expected.contentEquals(clientPublicKey!!)) {
                wifiScanCallback?.onScanError("设备验证失败: 公钥不匹配")
                disconnect()
                return
            }

            Log.d(TAG, "Security 1 handshake completed for scan")

            // 握手成功，发送 WiFi 扫描命令
            sendWiFiScanCommand()

        } catch (e: Exception) {
            Log.e(TAG, "Response1 handling failed for scan", e)
            wifiScanCallback?.onScanError("PoP 验证处理失败: ${e.message}")
            disconnect()
        }
    }

    private fun sendWiFiScanCommand() {
        currentState = State.SCANNING_WIFI

        try {
            val scanPayload = WiFiScanPayload()
            scanPayload.msg = 0  // TypeCmdScanStart

            val cmdScanStart = CmdScanStart()
            cmdScanStart.blocking = true
            cmdScanStart.passive = false  // 主动扫描（发送 probe request）
            cmdScanStart.groupChannels = 0
            cmdScanStart.periodMs = 120  // 按文档默认值

            scanPayload.cmdScanStart = cmdScanStart

            val encodedPayload = scanPayload.encode()
            Log.d(TAG, "WiFi scan payload: ${encodedPayload.size} bytes")

            val encryptedPayload = encryptPayload(encodedPayload)
            Log.d(TAG, "Encrypted WiFi scan: ${encryptedPayload.size} bytes")

            // 直接写入，扫描结果通过 Notify 返回（不走 Read）
            val char = scanCharacteristic!!
            val properties = char.properties
            val writeType = if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }

            char.value = encryptedPayload
            char.writeType = writeType
            val writeSuccess = bluetoothGatt!!.writeCharacteristic(char)

            if (!writeSuccess) {
                handleError("发送WiFi扫描命令失败")
                return
            }

            Log.d(TAG, "WiFi scan command sent, waiting for Notify...")

            // 超时兜底：15秒后如果还没收到 Notify，尝试 Read 读取结果
            handler.postDelayed({
                if (currentState == State.SCANNING_WIFI && lastScanResults.isEmpty()) {
                    Log.d(TAG, "No Notify received, falling back to Read")
                    readScanResults()
                }
            }, 15000)

        } catch (e: Exception) {
            Log.e(TAG, "WiFi scan command send failed", e)
            handleError("发送WiFi扫描命令失败: ${e.message}")
        }
    }

    private fun handleWiFiScanResult(data: ByteArray?) {
        if (data == null || data.isEmpty()) {
            Log.w(TAG, "Empty WiFi scan result (Read fallback)")
            if (callback != null) {
                callback?.onWifiScanReady(emptyList())
            }
            return
        }

        Log.d(TAG, "WiFi scan result received (Read), data length: ${data.size}")

        try {
            val decryptedData = decryptPayload(data)
            Log.d(TAG, "Decrypted WiFi scan result: ${decryptedData.size} bytes, hex=${decryptedData.joinToString(" ") { "%02X".format(it) }}")

            val scanPayload = WiFiScanPayload.decode(decryptedData)
            
            // 如果 msg=1 (RespScanStart), 等待设备扫描完再读结果
            if (scanPayload.msg == 1) {
                Log.d(TAG, "Received RespScanStart (Read), waiting for scan to complete...")
                handler.postDelayed({
                    readScanResults()
                }, 5000)
                return
            }

            val results = scanPayload.respScanResult?.scanResults ?: emptyList()

            lastScanResults = results

            Log.d(TAG, "WiFi scan found ${results.size} networks (Read)")
            for (result in results) {
                Log.d(TAG, "  ${result.ssid} (${result.getAuthModeDescription()}, rssi=${result.rssi})")
            }

            // 配网模式走 callback，扫描模式走 wifiScanCallback
            if (callback != null) {
                callback?.onWifiScanReady(results)
            } else {
                wifiScanCallback?.onScanResults(results)
            }

        } catch (e: Exception) {
            Log.e(TAG, "WiFi scan result parsing failed", e)
            wifiScanCallback?.onScanError("解析WiFi扫描结果失败: ${e.message}")
        }
    }

    private fun readScanResults() {
        Log.d(TAG, "Reading scan results from device...")
        isReadingScanResults = true
        try {
            bluetoothGatt?.readCharacteristic(scanCharacteristic)
        } catch (e: Exception) {
            isReadingScanResults = false
            Log.e(TAG, "Read scan results failed", e)
            wifiScanCallback?.onScanError("读取WiFi扫描结果失败: ${e.message}")
        }
    }

    private fun handleWiFiScanNotify(data: ByteArray?) {
        if (data == null || data.isEmpty()) {
            Log.d(TAG, "Empty WiFi scan notify")
            return
        }

        Log.d(TAG, "WiFi scan notify received, data length: ${data.size}")

        try {
            val decrypted = decryptPayload(data)
            Log.d(TAG, "Decrypted WiFi scan data: ${decrypted.size} bytes")

            val scanPayload = WiFiScanPayload.decode(decrypted)

            // RespScanStart (msg=1)，设备已开始扫描，等待真正的扫描结果
            if (scanPayload.msg == 1) {
                Log.d(TAG, "Received RespScanStart via Notify, waiting for scan results...")
                return
            }

            val respScanResult = scanPayload.respScanResult
            if (respScanResult != null) {
                lastScanResults = respScanResult.scanResults
                Log.d(TAG, "WiFi scan results via Notify: ${respScanResult.scanResults.size} networks")
                for (result in respScanResult.scanResults) {
                    Log.d(TAG, "  ${result.ssid} (${result.getAuthModeDescription()}, rssi=${result.rssi})")
                }

                // 配网模式走 callback，扫描模式走 wifiScanCallback
                if (callback != null) {
                    callback?.onWifiScanReady(respScanResult.scanResults)
                } else {
                    wifiScanCallback?.onScanResults(respScanResult.scanResults)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "WiFi scan notify parsing failed", e)
            wifiScanCallback?.onScanError("WiFi扫描结果解析失败: ${e.message}")
        }
    }
}
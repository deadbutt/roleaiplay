package com.example.iot.ble.proto

/**
 * WiFiScanPayload - WiFi 扫描消息
 * 字段编号完全匹配 ESP-IDF components/wifi_provisioning/proto/wifi_scan.proto
 * 
 * message WiFiScanPayload {
 *   WiFiScanMsgType msg = 1;
 *   oneof payload {
 *     CmdScanStart cmd_scan_start = 10;
 *     RespScanResult resp_scan_result = 14;
 *   }
 * }
 * 
 * WiFiScanMsgType: 0=CmdScanStart, 6=RespScanResult
 */
class WiFiScanPayload {
    var msg: Int = 0  // 0=CmdScanStart
    var cmdScanStart: CmdScanStart? = null

    fun encode(): ByteArray {
        val result = mutableListOf<Byte>()
        
        // msg (field 1, varint)
        result.addAll(ProtoEncoder.encodeVarintField(1, msg).toList())
        
        // 根据 msg 类型编码对应的消息
        when (msg) {
            0 -> { // CmdScanStart = field 10
                if (cmdScanStart != null) {
                    result.addAll(ProtoEncoder.encodeEmbeddedMessage(10, cmdScanStart!!.encode()).toList())
                }
            }
        }
        
        return result.toByteArray()
    }

    companion object {
        fun decode(data: ByteArray): WiFiScanPayload {
            val payload = WiFiScanPayload()
            
            ProtoDecoder.forEachField(data) { fieldNumber, wireType, fieldData ->
                when (fieldNumber) {
                    1 -> {
                        if (wireType == 0) {
                            val (value, _) = ProtoDecoder.decodeVarint(fieldData, 0)
                            payload.msg = value
                        }
                    }
                    14 -> { // RespScanResult = field 14
                        if (wireType == 2) {
                            // 解析 RespScanResult
                            payload.respScanResult = RespScanResult.decode(fieldData)
                        }
                    }
                }
                true
            }
            
            return payload
        }
    }
    
    var respScanResult: RespScanResult? = null
}

/**
 * CmdScanStart - 启动 WiFi 扫描
 * 
 * message CmdScanStart {
 *   bool blocking = 1;
 *   bool passive = 2;
 *   int32 group_channels = 3;
 *   int32 period_ms = 4;
 * }
 */
class CmdScanStart {
    var blocking: Boolean = true
    var passive: Boolean = false  // active_scan = false
    var groupChannels: Int = 0
    var periodMs: Int = 120

    fun encode(): ByteArray {
        val result = mutableListOf<Byte>()
        
        // blocking (field 1, bool/varint)
        result.addAll(ProtoEncoder.encodeVarintField(1, if (blocking) 1 else 0).toList())
        
        // passive (field 2, bool/varint)
        result.addAll(ProtoEncoder.encodeVarintField(2, if (passive) 1 else 0).toList())
        
        // group_channels (field 3, varint)
        result.addAll(ProtoEncoder.encodeVarintField(3, groupChannels).toList())
        
        // period_ms (field 4, varint)
        result.addAll(ProtoEncoder.encodeVarintField(4, periodMs).toList())
        
        return result.toByteArray()
    }
}

/**
 * RespScanResult - WiFi 扫描结果
 * 
 * message RespScanResult {
 *   repeated WiFiScanResult scan_results = 1;
 * }
 */
class RespScanResult {
    var scanResults: List<WiFiScanResult> = emptyList()

    companion object {
        fun decode(data: ByteArray): RespScanResult {
            val response = RespScanResult()
            val results = mutableListOf<WiFiScanResult>()
            
            ProtoDecoder.forEachField(data) { fieldNumber, wireType, fieldData ->
                when (fieldNumber) {
                    1 -> { // scan_results (repeated)
                        if (wireType == 2) {
                            results.add(WiFiScanResult.decode(fieldData))
                        }
                    }
                }
                true
            }
            
            response.scanResults = results
            return response
        }
    }
}

/**
 * WiFiScanResult - 单个 WiFi 扫描结果
 * 
 * message WiFiScanResult {
 *   string ssid = 1;
 *   bytes bssid = 2;
 *   int32 channel = 3;
 *   int32 rssi = 4;
 *   WifiAuthMode auth = 5;
 * }
 * 
 * WifiAuthMode: 0=Open, 1=WEP, 2=WPA_PSK, 3=WPA2_PSK, 4=WPA_WPA2_PSK, 5=WPA3_PSK
 */
class WiFiScanResult {
    var ssid: String = ""
    var bssid: ByteArray = ByteArray(0)
    var channel: Int = 0
    var rssi: Int = 0
    var auth: Int = 0  // WifiAuthMode

    companion object {
        fun decode(data: ByteArray): WiFiScanResult {
            val result = WiFiScanResult()
            
            ProtoDecoder.forEachField(data) { fieldNumber, wireType, fieldData ->
                when (fieldNumber) {
                    1 -> { // ssid (string)
                        if (wireType == 2) {
                            result.ssid = String(fieldData)
                        }
                    }
                    2 -> { // bssid (bytes)
                        if (wireType == 2) {
                            result.bssid = fieldData
                        }
                    }
                    3 -> { // channel (varint)
                        if (wireType == 0) {
                            val (value, _) = ProtoDecoder.decodeVarint(fieldData, 0)
                            result.channel = value
                        }
                    }
                    4 -> { // rssi (varint)
                        if (wireType == 0) {
                            val (value, _) = ProtoDecoder.decodeVarint(fieldData, 0)
                            result.rssi = value
                        }
                    }
                    5 -> { // auth (varint)
                        if (wireType == 0) {
                            val (value, _) = ProtoDecoder.decodeVarint(fieldData, 0)
                            result.auth = value
                        }
                    }
                }
                true
            }
            
            return result
        }
    }
    
    /**
     * 获取认证模式描述
     */
    fun getAuthModeDescription(): String {
        return when (auth) {
            0 -> "Open"
            1 -> "WEP"
            2 -> "WPA_PSK"
            3 -> "WPA2_PSK"
            4 -> "WPA_WPA2_PSK"
            5 -> "WPA3_PSK"
            else -> "Unknown"
        }
    }
    
    /**
     * 是否需要密码
     */
    fun requiresPassword(): Boolean {
        return auth != 0  // Open 不需要密码
    }
}
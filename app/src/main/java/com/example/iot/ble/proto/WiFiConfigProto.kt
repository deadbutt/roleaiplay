package com.example.iot.ble.proto

/**
 * WiFiConfigPayload - WiFi 配置消息
 * 字段编号完全匹配 ESP-IDF components/wifi_provisioning/proto/wifi_config.proto
 * 
 * message WiFiConfigPayload {
 *   WiFiConfigMsgType msg = 1;
 *   oneof payload {
 *     CmdGetStatus cmd_get_status = 10;
 *     RespGetStatus resp_get_status = 11;
 *     CmdSetConfig cmd_set_config = 12;
 *     RespSetConfig resp_set_config = 13;
 *   }
 * }
 */
class WiFiConfigPayload {
    var msg: Int = 0  // 2=TypeCmdSetConfig, 0=TypeCmdGetStatus
    var cmdSetConfig: CmdSetConfig? = null
    var respGetStatus: RespGetStatus? = null

    fun encode(): ByteArray {
        val result = mutableListOf<Byte>()
        
        // msg (field 1, varint)
        result.addAll(ProtoEncoder.encodeVarintField(1, msg).toList())
        
        // 根据 msg 类型编码对应的消息
        when (msg) {
            2 -> { // CmdSetConfig = field 12
                if (cmdSetConfig != null) {
                    result.addAll(ProtoEncoder.encodeEmbeddedMessage(12, cmdSetConfig!!.encode()).toList())
                }
            }
            0 -> { // CmdGetStatus (empty message, just need msg field)
                // CmdGetStatus is empty, no additional fields
            }
        }
        
        return result.toByteArray()
    }

    companion object {
        fun decode(data: ByteArray): WiFiConfigPayload {
            val payload = WiFiConfigPayload()
            
            ProtoDecoder.forEachField(data) { fieldNumber, wireType, fieldData ->
                when (fieldNumber) {
                    1 -> {
                        if (wireType == 0) {
                            val (value, _) = ProtoDecoder.decodeVarint(fieldData, 0)
                            payload.msg = value
                        }
                    }
                    11 -> { // RespGetStatus = field 11
                        if (wireType == 2) {
                            payload.respGetStatus = RespGetStatus.decode(fieldData)
                        }
                    }
                }
                true
            }
            
            return payload
        }
    }
}

/**
 * CmdSetConfig - 设置 WiFi 凭证
 * 
 * message CmdSetConfig {
 *   string ssid = 1;
 *   string passphrase = 2;
 *   bytes bssid = 3;
 * }
 */
class CmdSetConfig {
    var ssid: String = ""
    var passphrase: String = ""
    var bssid: ByteArray = ByteArray(6)  // 6 bytes, can be all zeros

    fun encode(): ByteArray {
        val result = mutableListOf<Byte>()
        
        // ssid (field 1, string)
        result.addAll(ProtoEncoder.encodeLengthDelimited(1, ssid.toByteArray()).toList())
        
        // passphrase (field 2, string)
        result.addAll(ProtoEncoder.encodeLengthDelimited(2, passphrase.toByteArray()).toList())
        
        // bssid (field 3, bytes)
        result.addAll(ProtoEncoder.encodeLengthDelimited(3, bssid).toList())
        
        return result.toByteArray()
    }
}

/**
 * RespGetStatus - WiFi 连接状态响应
 * 字段编号完全匹配 ESP-IDF proto
 * 
 * message RespGetStatus {
 *   Status status = 1;
 *   WifiStationState sta_state = 2;
 *   oneof state {
 *     WifiConnectFailedReason fail_reason = 10;
 *     WifiConnectedState connected = 11;
 *   }
 * }
 * 
 * WifiStationState: 0=Disconnected, 1=Scanning, 2=Connecting, 3=Connected
 * WifiConnectFailedReason: 0=NoError, 1=AuthError, 2=APNotFound
 */
class RespGetStatus {
    var status: Int = 0
    var staState: Int = 0  // 0=断开, 1=扫描中, 2=连接中, 3=已连接
    var failReason: Int = 0  // 0=无, 1=认证失败, 2=AP未找到
    var ip4Addr: String? = null

    companion object {
        fun decode(data: ByteArray): RespGetStatus {
            val response = RespGetStatus()
            
            ProtoDecoder.forEachField(data) { fieldNumber, wireType, fieldData ->
                when (fieldNumber) {
                    1 -> { // status
                        if (wireType == 0) {
                            val (value, _) = ProtoDecoder.decodeVarint(fieldData, 0)
                            response.status = value
                        }
                    }
                    2 -> { // sta_state
                        if (wireType == 0) {
                            val (value, _) = ProtoDecoder.decodeVarint(fieldData, 0)
                            response.staState = value
                        }
                    }
                    10 -> { // fail_reason
                        if (wireType == 0) {
                            val (value, _) = ProtoDecoder.decodeVarint(fieldData, 0)
                            response.failReason = value
                        }
                    }
                    11 -> { // connected (embedded message)
                        if (wireType == 2) {
                            // 解析 WiFiConnectedState
                            ProtoDecoder.forEachField(fieldData) { fn, wt, fd ->
                                if (fn == 1 && wt == 2) { // ip4_addr (field 1, string)
                                    response.ip4Addr = String(fd)
                                }
                                true
                            }
                        }
                    }
                }
                true
            }
            
            return response
        }
    }
}
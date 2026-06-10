package com.example.iot.ble.proto

/**
 * ESP-IDF 标准 session.proto 手动实现
 * 字段编号完全匹配 ESP-IDF components/wifi_provisioning/proto/session.proto
 */

// enum SecSchemeVersion { SecScheme0 = 0; SecScheme1 = 1; }
// SessionData: sec_ver = 2, sec0 = 10, sec1 = 11
class SessionData {
    var secVer: Int = 0
    var sec0: Sec0Payload? = null
    var sec1: Sec1Payload? = null

    fun encode(): ByteArray {
        val result = mutableListOf<Byte>()
        result.addAll(ProtoEncoder.encodeVarintField(2, secVer).toList())
        if (sec0 != null) {
            result.addAll(ProtoEncoder.encodeEmbeddedMessage(10, sec0!!.encode()).toList())
        }
        if (sec1 != null) {
            result.addAll(ProtoEncoder.encodeEmbeddedMessage(11, sec1!!.encode()).toList())
        }
        return result.toByteArray()
    }

    companion object {
        fun decode(data: ByteArray): SessionData {
            val sd = SessionData()
            ProtoDecoder.forEachField(data) { fn, wt, fd ->
                when (fn) {
                    2 -> { if (wt == 0) { val (v, _) = ProtoDecoder.decodeVarint(fd, 0); sd.secVer = v } }
                    10 -> { if (wt == 2) sd.sec0 = Sec0Payload.decode(fd) }
                    11 -> { if (wt == 2) sd.sec1 = Sec1Payload.decode(fd) }
                }
                true
            }
            return sd
        }
    }
}

// Sec0Payload: msg = 1, sc = 20, sr = 21
class Sec0Payload {
    var msg: Int = 0  // 0=S0_Session_Command, 1=S0_Session_Response
    var sc: S0SessionCmd? = null
    var sr: S0SessionResp? = null

    fun encode(): ByteArray {
        val result = mutableListOf<Byte>()
        result.addAll(ProtoEncoder.encodeVarintField(1, msg).toList())
        if (sc != null) {
            result.addAll(ProtoEncoder.encodeEmbeddedMessage(20, sc!!.encode()).toList())
        }
        if (sr != null) {
            result.addAll(ProtoEncoder.encodeEmbeddedMessage(21, sr!!.encode()).toList())
        }
        return result.toByteArray()
    }

    companion object {
        fun decode(data: ByteArray): Sec0Payload {
            val p = Sec0Payload()
            ProtoDecoder.forEachField(data) { fn, wt, fd ->
                when (fn) {
                    1 -> { if (wt == 0) { val (v, _) = ProtoDecoder.decodeVarint(fd, 0); p.msg = v } }
                    20 -> { if (wt == 2) p.sc = S0SessionCmd.decode(fd) }
                    21 -> { if (wt == 2) p.sr = S0SessionResp.decode(fd) }
                }
                true
            }
            return p
        }
    }
}

// S0SessionCmd: empty message
class S0SessionCmd {
    fun encode(): ByteArray = byteArrayOf()
    companion object {
        fun decode(data: ByteArray): S0SessionCmd = S0SessionCmd()
    }
}

// S0SessionResp: status = 1
class S0SessionResp {
    var status: Int = 0
    fun encode(): ByteArray = ProtoEncoder.encodeVarintField(1, status)
    companion object {
        fun decode(data: ByteArray): S0SessionResp {
            val r = S0SessionResp()
            ProtoDecoder.forEachField(data) { fn, wt, fd ->
                if (fn == 1 && wt == 0) { val (v, _) = ProtoDecoder.decodeVarint(fd, 0); r.status = v }
                true
            }
            return r
        }
    }
}

// Sec1Payload: msg = 1, oneof payload { sc0=20, sr0=21, sc1=22, sr1=23 }
// 字段编号完全匹配 ESP-IDF components/protocomm/proto/sec1.proto
class Sec1Payload {
    var msg: Int = 0  // Sec1MsgType: 0=Session_Command0, 1=Session_Response0, 2=Session_Command1, 3=Session_Response1
    var sc0: SessionCmd0? = null
    var sr0: SessionResp0? = null
    var sc1: SessionCmd1? = null
    var sr1: SessionResp1? = null

    fun encode(): ByteArray {
        val result = mutableListOf<Byte>()
        result.addAll(ProtoEncoder.encodeVarintField(1, msg).toList())
        if (sc0 != null) result.addAll(ProtoEncoder.encodeEmbeddedMessage(20, sc0!!.encode()).toList())
        if (sr0 != null) result.addAll(ProtoEncoder.encodeEmbeddedMessage(21, sr0!!.encode()).toList())
        if (sc1 != null) result.addAll(ProtoEncoder.encodeEmbeddedMessage(22, sc1!!.encode()).toList())
        if (sr1 != null) result.addAll(ProtoEncoder.encodeEmbeddedMessage(23, sr1!!.encode()).toList())
        return result.toByteArray()
    }

    companion object {
        fun decode(data: ByteArray): Sec1Payload {
            val p = Sec1Payload()
            ProtoDecoder.forEachField(data) { fn, wt, fd ->
                when (fn) {
                    1 -> { if (wt == 0) { val (v, _) = ProtoDecoder.decodeVarint(fd, 0); p.msg = v } }
                    20 -> { if (wt == 2) p.sc0 = SessionCmd0.decode(fd) }
                    21 -> { if (wt == 2) p.sr0 = SessionResp0.decode(fd) }
                    22 -> { if (wt == 2) p.sc1 = SessionCmd1.decode(fd) }
                    23 -> { if (wt == 2) p.sr1 = SessionResp1.decode(fd) }
                }
                true
            }
            return p
        }
    }
}

// SessionCmd0: client_pubkey = 1
class SessionCmd0 {
    var clientPubkey: ByteArray = ByteArray(0)
    fun encode(): ByteArray = ProtoEncoder.encodeLengthDelimited(1, clientPubkey)
    companion object {
        fun decode(data: ByteArray): SessionCmd0 {
            val c = SessionCmd0()
            ProtoDecoder.forEachField(data) { fn, wt, fd ->
                if (fn == 1 && wt == 2) c.clientPubkey = fd
                true
            }
            return c
        }
    }
}

// SessionResp0: status = 1, device_pubkey = 2, device_salt = 3
class SessionResp0 {
    var status: Int = 0
    var devicePubkey: ByteArray = ByteArray(0)
    var deviceSalt: ByteArray = ByteArray(0)
    fun encode(): ByteArray {
        val result = mutableListOf<Byte>()
        result.addAll(ProtoEncoder.encodeVarintField(1, status).toList())
        if (devicePubkey.isNotEmpty()) result.addAll(ProtoEncoder.encodeLengthDelimited(2, devicePubkey).toList())
        if (deviceSalt.isNotEmpty()) result.addAll(ProtoEncoder.encodeLengthDelimited(3, deviceSalt).toList())
        return result.toByteArray()
    }
    companion object {
        fun decode(data: ByteArray): SessionResp0 {
            val r = SessionResp0()
            ProtoDecoder.forEachField(data) { fn, wt, fd ->
                when (fn) {
                    1 -> { if (wt == 0) { val (v, _) = ProtoDecoder.decodeVarint(fd, 0); r.status = v } }
                    2 -> { if (wt == 2) r.devicePubkey = fd }
                    3 -> { if (wt == 2) r.deviceSalt = fd }
                }
                true
            }
            return r
        }
    }
}

// SessionCmd1: client_verify_data = 2
// 字段编号完全匹配 ESP-IDF sec1.proto
class SessionCmd1 {
    var clientProof: ByteArray = ByteArray(0)
    fun encode(): ByteArray = ProtoEncoder.encodeLengthDelimited(2, clientProof)
    companion object {
        fun decode(data: ByteArray): SessionCmd1 {
            val c = SessionCmd1()
            ProtoDecoder.forEachField(data) { fn, wt, fd ->
                if (fn == 2 && wt == 2) c.clientProof = fd
                true
            }
            return c
        }
    }
}

// SessionResp1: status = 1, device_verify_data = 3
// 字段编号完全匹配 ESP-IDF sec1.proto
class SessionResp1 {
    var status: Int = 0
    var deviceProof: ByteArray = ByteArray(0)
    fun encode(): ByteArray {
        val result = mutableListOf<Byte>()
        result.addAll(ProtoEncoder.encodeVarintField(1, status).toList())
        if (deviceProof.isNotEmpty()) result.addAll(ProtoEncoder.encodeLengthDelimited(3, deviceProof).toList())
        return result.toByteArray()
    }
    companion object {
        fun decode(data: ByteArray): SessionResp1 {
            val r = SessionResp1()
            ProtoDecoder.forEachField(data) { fn, wt, fd ->
                when (fn) {
                    1 -> { if (wt == 0) { val (v, _) = ProtoDecoder.decodeVarint(fd, 0); r.status = v } }
                    3 -> { if (wt == 2) r.deviceProof = fd }
                }
                true
            }
            return r
        }
    }
}

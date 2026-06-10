package com.example.iot.ble.proto

/**
 * Protobuf 编解码工具类
 * 根据 ESP-IDF protocomm 协议手动实现
 */
object ProtoEncoder {

    // Field types
    private const val VARINT = 0
    private const val FIXED64 = 1
    private const val LENGTH_DELIMITED = 2
    private const val FIXED32 = 5

    // 编码 varint
    fun encodeVarint(value: Int): ByteArray {
        if (value == 0) return byteArrayOf(0)
        
        val result = mutableListOf<Byte>()
        var v = value
        while (v != 0) {
            val byte = (v and 0x7F)
            v = v shr 7
            if (v != 0) {
                result.add((byte or 0x80).toByte())
            } else {
                result.add(byte.toByte())
            }
        }
        return result.toByteArray()
    }

    // 编码 field tag: (field_number << 3) | wire_type
    fun encodeTag(fieldNumber: Int, wireType: Int): ByteArray {
        return encodeVarint((fieldNumber shl 3) or wireType)
    }

    // 编码 length-delimited field (string, bytes, embedded message)
    fun encodeLengthDelimited(fieldNumber: Int, data: ByteArray): ByteArray {
        val tag = encodeTag(fieldNumber, LENGTH_DELIMITED)
        val length = encodeVarint(data.size)
        return tag + length + data
    }

    // 编码 varint field
    fun encodeVarintField(fieldNumber: Int, value: Int): ByteArray {
        val tag = encodeTag(fieldNumber, VARINT)
        return tag + encodeVarint(value)
    }

    // 编码嵌套消息
    fun encodeEmbeddedMessage(fieldNumber: Int, messageData: ByteArray): ByteArray {
        return encodeLengthDelimited(fieldNumber, messageData)
    }
}
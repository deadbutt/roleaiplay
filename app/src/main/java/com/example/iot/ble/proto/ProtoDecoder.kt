package com.example.iot.ble.proto

/**
 * Protobuf 解码工具类
 */
object ProtoDecoder {

    // 解码 varint
    fun decodeVarint(data: ByteArray, offset: Int): Pair<Int, Int> {
        var result = 0
        var shift = 0
        var index = offset
        
        while (index < data.size) {
            val byte = data[index].toInt() and 0xFF
            result = result or ((byte and 0x7F) shl shift)
            index++
            if ((byte and 0x80) == 0) break
            shift += 7
        }
        
        return Pair(result, index - offset)
    }

    // 解码 field tag
    fun decodeTag(data: ByteArray, offset: Int): Pair<Int, Int> {
        val (tagValue, bytesRead) = decodeVarint(data, offset)
        val fieldNumber = tagValue shr 3
        val wireType = tagValue and 0x07
        return Pair(fieldNumber, bytesRead)
    }

    // 解码 length-delimited field
    fun decodeLengthDelimited(data: ByteArray, offset: Int): Pair<ByteArray, Int> {
        val (length, lengthBytes) = decodeVarint(data, offset)
        val start = offset + lengthBytes
        val end = start + length
        if (end > data.size) {
            return Pair(ByteArray(0), lengthBytes + length)
        }
        return Pair(data.copyOfRange(start, end), lengthBytes + length)
    }

    // 遍历所有 field
    fun forEachField(data: ByteArray, callback: (fieldNumber: Int, wireType: Int, fieldData: ByteArray) -> Boolean) {
        var offset = 0
        while (offset < data.size) {
            val (fieldNumber, tagBytes) = decodeTag(data, offset)
            val wireType = (data[offset].toInt() and 0xFF) and 0x07
            offset += tagBytes
            
            when (wireType) {
                0 -> { // VARINT
                    val (value, varintBytes) = decodeVarint(data, offset)
                    if (!callback(fieldNumber, wireType, encodeVarintBytes(value))) break
                    offset += varintBytes
                }
                2 -> { // LENGTH_DELIMITED
                    val (fieldData, lengthBytes) = decodeLengthDelimited(data, offset)
                    if (!callback(fieldNumber, wireType, fieldData)) break
                    offset += lengthBytes
                }
                else -> {
                    // Skip unknown wire type
                    break
                }
            }
        }
    }

    private fun encodeVarintBytes(value: Int): ByteArray {
        return ProtoEncoder.encodeVarint(value)
    }

    // 查找特定 field number 的数据
    fun findField(data: ByteArray, fieldNumber: Int, wireType: Int): ByteArray? {
        var offset = 0
        while (offset < data.size) {
            val (fn, tagBytes) = decodeTag(data, offset)
            val wt = (data[offset].toInt() and 0xFF) and 0x07
            offset += tagBytes
            
            if (fn == fieldNumber && wt == wireType) {
                when (wt) {
                    0 -> {
                        val (value, varintBytes) = decodeVarint(data, offset)
                        return encodeVarintBytes(value)
                    }
                    2 -> {
                        val (fieldData, _) = decodeLengthDelimited(data, offset)
                        return fieldData
                    }
                }
            } else {
                // Skip this field
                when (wt) {
                    0 -> {
                        val (_, varintBytes) = decodeVarint(data, offset)
                        offset += varintBytes
                    }
                    2 -> {
                        val (_, lengthBytes) = decodeLengthDelimited(data, offset)
                        offset += lengthBytes
                    }
                }
            }
        }
        return null
    }
}
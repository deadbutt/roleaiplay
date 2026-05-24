package com.example.iot

import android.os.Handler
import android.os.Looper
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import com.example.iot.network.HttpClient

class WebSocketManager private constructor() {

    private var webSocket: WebSocket? = null
    private val client = HttpClient.client
    
    private var isConnected = false
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 5
    private val handler = Handler(Looper.getMainLooper())
    
    private var onMessageListener: ((String) -> Unit)? = null
    private var onConnectionListener: ((Boolean) -> Unit)? = null
    
    companion object {
        private const val WS_URL = "wss://47.118.22.220:8443/ws"
        
        @Volatile
        private var instance: WebSocketManager? = null
        
        fun getInstance(): WebSocketManager {
            return instance ?: synchronized(this) {
                instance ?: WebSocketManager().also { instance = it }
            }
        }
    }
    
    fun connect() {
        if (isConnected) return
        
        val request = Request.Builder()
            .url(WS_URL)
            .build()
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected = true
                reconnectAttempts = 0
                handler.post {
                    onConnectionListener?.invoke(true)
                }
                android.util.Log.d("WebSocket", "连接成功")
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                handler.post {
                    onMessageListener?.invoke(text)
                }
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected = false
                handler.post {
                    onConnectionListener?.invoke(false)
                }
                android.util.Log.e("WebSocket", "连接失败: ${t.message}")
                attemptReconnect()
            }
            
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isConnected = false
                handler.post {
                    onConnectionListener?.invoke(false)
                }
                android.util.Log.d("WebSocket", "连接关闭: $reason")
            }
        })
    }
    
    fun disconnect() {
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        isConnected = false
    }
    
    fun sendCommand(deviceId: String, cmd: String) {
        if (!isConnected) {
            connect()
            return
        }
        
        val json = JSONObject().apply {
            put("type", "cmd")
            put("device_id", deviceId)
            put("cmd", cmd)
        }
        
        webSocket?.send(json.toString())
    }
    
    fun sendPing() {
        if (!isConnected) return
        
        val json = JSONObject().apply {
            put("type", "ping")
        }
        
        webSocket?.send(json.toString())
    }
    
    fun setOnMessageListener(listener: (String) -> Unit) {
        onMessageListener = listener
    }
    
    fun setOnConnectionListener(listener: (Boolean) -> Unit) {
        onConnectionListener = listener
    }
    
    private fun attemptReconnect() {
        if (reconnectAttempts >= maxReconnectAttempts) {
            android.util.Log.e("WebSocket", "重连失败次数过多，停止重连")
            return
        }
        
        reconnectAttempts++
        val delay = (reconnectAttempts * 2000).toLong()
        
        handler.postDelayed({
            android.util.Log.d("WebSocket", "尝试重连 ($reconnectAttempts/$maxReconnectAttempts)")
            connect()
        }, delay)
    }
    
    fun isWSConnected(): Boolean = isConnected
}

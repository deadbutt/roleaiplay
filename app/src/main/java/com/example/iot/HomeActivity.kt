package com.example.iot

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import org.json.JSONObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import com.example.iot.network.HttpClient
import java.util.concurrent.TimeUnit

class HomeActivity : AppCompatActivity() {

    // 控件声明
    private lateinit var tvWelcome: TextView
    private lateinit var tvDeviceData: TextView
    private lateinit var tvDeviceStatus: TextView
    private lateinit var btnSendCmd: MaterialButton
    private lateinit var btnRefresh: Button
    private lateinit var etUserInput: TextInputEditText
    private lateinit var btnSendText: Button
    private lateinit var chatHistoryContainer: LinearLayout
    private lateinit var chatHistoryScroll: ScrollView

    // 后端接口地址
    private val BASE_URL = "https://bolank.asia/api/"
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val handler = Handler(Looper.getMainLooper())
    private var isDestroyed = false

    // 轮询间隔（3秒）
    private val POLLING_INTERVAL = 3000L

    // 设备ID（固定）
    private val DEVICE_ID = "test01"

    // 用户名（用于后端 user_id）
    private var username: String = ""

    // 登录状态管理
    private val PREFS_NAME = "login_prefs"
    private val KEY_USERNAME = "username"
    private val KEY_LOGIN_TIME = "login_time"
    private val SESSION_TIMEOUT = 7 * 24 * 60 * 60 * 1000L // 7天超时

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // 检查登录状态
        checkLoginStatus()

        // 绑定控件
        tvWelcome = findViewById(R.id.tv_welcome)
        tvDeviceData = findViewById(R.id.tv_device_data)
        tvDeviceStatus = findViewById(R.id.tv_device_status)
        btnSendCmd = findViewById(R.id.btn_send_cmd)
        btnRefresh = findViewById(R.id.btn_refresh)
        etUserInput = findViewById(R.id.et_user_input)
        btnSendText = findViewById(R.id.btn_send_text)
        chatHistoryContainer = findViewById(R.id.chat_history_container)
        chatHistoryScroll = findViewById(R.id.chat_history_scroll)

        // 获取登录用户名并显示
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        username = prefs.getString(KEY_USERNAME, "") ?: ""
        if (username.isEmpty()) {
            username = "未知用户"
        }
        tvWelcome.text = "欢迎，$username！"

        // 立即获取一次设备数据
        fetchDeviceData()

        // 开始轮询
        startPolling()

        // 刷新按钮
        btnRefresh.setOnClickListener { fetchDeviceData() }

        // 下发指令按钮
        btnSendCmd.setOnClickListener { sendDeviceCommand() }

        // 发送文本按钮
        btnSendText.setOnClickListener { sendUserText() }
    }

    private fun startPolling() {
        handler.post(object : Runnable {
            override fun run() {
                if (!isDestroyed) {
                    fetchDeviceData()
                    handler.postDelayed(this, POLLING_INTERVAL)
                }
            }
        })
    }

    // 获取设备数据
    private fun fetchDeviceData() {
        Thread {
            try {
                val client = HttpClient.client

                val request = Request.Builder()
                    .url("${BASE_URL}device/data")
                    .get()
                    .build()

                val response = client.newCall(request).execute()

                if (!isDestroyed && response.isSuccessful && response.body != null) {
                    val result = response.body!!.string()
                    val json = JSONObject(result)

                    if (json.has("data")) {
                        val dataObj = json.getJSONObject("data")

                        val online = dataObj.optBoolean("online", false)
                        val updateTime = dataObj.optString("update_time", "")
                        val deviceData = dataObj.optString("data", "暂无数据")

                        var displayData = deviceData
                        try {
                            val dataJson = JSONObject(deviceData)
                            val temp = dataJson.optDouble("temperature", 0.0)
                            val hum = dataJson.optDouble("humidity", 0.0)
                            val light = dataJson.optString("light", "")

                            displayData = buildString {
                                if (temp > 0) append("温度: ${String.format("%.1f", temp)}°C\n")
                                if (hum > 0) append("湿度: ${String.format("%.1f", hum)}%\n")
                                if (light.isNotEmpty()) append("灯光: $light\n")
                            }.ifEmpty { deviceData }
                        } catch (e: Exception) { }

                        val finalDisplayData = displayData
                        handler.post {
                            tvDeviceStatus.text = if (online) "🟢 设备在线" else "🔴 设备离线"
                            tvDeviceData.text = """
                                最新数据:
                                $finalDisplayData
                                更新时间: $updateTime
                            """.trimIndent()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    // 发送用户输入的文本到AI
    private fun sendUserText() {
        val text = etUserInput.text.toString().trim()
        if (text.isEmpty()) {
            Toast.makeText(this, "请输入内容", Toast.LENGTH_SHORT).show()
            return
        }

        // 清空输入框
        etUserInput.text?.clear()

        // 添加用户消息到聊天历史
        addChatMessage("👤 $text", isUser = true)

        // 显示一个临时的“AI正在输入...”消息（可选）
        val loadingIndex = addTemporaryAiMessage("🤖 AI正在输入...")

        Thread {
            try {
                val client = HttpClient.client

                val json = JSONObject().apply {
                    put("text", text)
                    put("user_id", username)
                    put("device_id", DEVICE_ID)
                }
                val body = json.toString().toRequestBody(JSON)

                val request = Request.Builder()
                    .url("${BASE_URL}voice")
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()

                if (!isDestroyed && response.isSuccessful && response.body != null) {
                    val result = response.body!!.string()
                    val resJson = JSONObject(result)

                    if (resJson.getInt("code") == 200) {
                        val emotion = resJson.getJSONObject("emotion")
                        val reply = resJson.getString("reply")
                        val detailedEmotion = emotion.getString("detailed_emotion")

                        handler.post {
                            // 移除临时“AI正在输入...”消息
                            if (loadingIndex != -1) {
                                chatHistoryContainer.removeViewAt(loadingIndex)
                            }
                            // 添加AI回复
                            addChatMessage("🤖 [$detailedEmotion] $reply", isUser = false)
                        }
                    } else {
                        handler.post {
                            if (loadingIndex != -1) {
                                chatHistoryContainer.removeViewAt(loadingIndex)
                            }
                            addChatMessage("❌ 分析失败，请重试", isUser = false)
                        }
                    }
                } else {
                    handler.post {
                        if (loadingIndex != -1) {
                            chatHistoryContainer.removeViewAt(loadingIndex)
                        }
                        addChatMessage("❌ 网络错误", isUser = false)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                handler.post {
                    if (loadingIndex != -1) {
                        chatHistoryContainer.removeViewAt(loadingIndex)
                    }
                    addChatMessage("❌ 网络错误", isUser = false)
                }
            }
        }.start()
    }

    // 添加临时AI消息（如“AI正在输入...”），返回该消息在LinearLayout中的索引
    private fun addTemporaryAiMessage(message: String): Int {
        val textView = createMessageTextView(message, isUser = false)
        chatHistoryContainer.addView(textView)
        scrollToBottom()
        return chatHistoryContainer.indexOfChild(textView)
    }

    // 创建消息TextView
    private fun createMessageTextView(message: String, isUser: Boolean): TextView {
        return TextView(this).apply {
            text = message
            textSize = 14f
            setPadding(24, 16, 24, 16)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 8, 0, 0)
            }
            // 设置背景色
            setBackgroundColor(
                if (isUser) android.graphics.Color.parseColor("#E0E0E0")
                else android.graphics.Color.parseColor("#FFFFFF")
            )
        }
    }

    // 添加消息到聊天历史
    private fun addChatMessage(message: String, isUser: Boolean) {
        val textView = createMessageTextView(message, isUser)
        chatHistoryContainer.addView(textView)
        scrollToBottom()
    }

    // 滚动到底部
    private fun scrollToBottom() {
        chatHistoryScroll.post {
            chatHistoryScroll.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }

    // 下发设备指令
    private fun sendDeviceCommand() {
        val cmd = "open_light"

        Toast.makeText(this, "指令发送中...", Toast.LENGTH_SHORT).show()

        Thread {
            try {
                val client = HttpClient.client

                val json = JSONObject().apply {
                    put("device_id", DEVICE_ID)
                    put("cmd", cmd)
                }
                val body = json.toString().toRequestBody(JSON)

                val request = Request.Builder()
                    .url("${BASE_URL}device/cmd")
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()

                if (!isDestroyed && response.isSuccessful) {
                    handler.post {
                        Toast.makeText(this@HomeActivity, "指令已发送", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        isDestroyed = true
        handler.removeCallbacksAndMessages(null)
    }

    // 检查登录状态
    private fun checkLoginStatus() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedUsername = prefs.getString(KEY_USERNAME, "")
        val loginTime = prefs.getLong(KEY_LOGIN_TIME, 0)
        val currentTime = System.currentTimeMillis()

        // 如果没有登录信息或超时，返回登录页
        if (savedUsername.isNullOrEmpty() || (currentTime - loginTime > SESSION_TIMEOUT)) {
            clearLoginInfo()
            val intent = Intent(this@HomeActivity, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    // 保存登录信息
    private fun saveLoginInfo(username: String) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(KEY_USERNAME, username)
            putLong(KEY_LOGIN_TIME, System.currentTimeMillis())
            apply()
        }
    }

    // 清除登录信息
    private fun clearLoginInfo() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }
}
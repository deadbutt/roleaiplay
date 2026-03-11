package com.example.iot

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import org.json.JSONObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class HomeActivity : AppCompatActivity() {

    // 控件声明
    private lateinit var tvWelcome: TextView
    private lateinit var tvDeviceData: TextView
    private lateinit var tvDeviceStatus: TextView
    private lateinit var btnSendCmd: MaterialButton
    private lateinit var btnRefresh: Button

    // 新增：语音交互控件
    private lateinit var etUserInput: TextInputEditText
    private lateinit var btnSendText: Button
    private lateinit var btnVoiceInput: Button
    private lateinit var tvAiReply: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var chatAdapter: ChatAdapter

    // 后端接口地址
    private val BASE_URL = "http://47.118.22.220:8081/api/"
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val handler = Handler(Looper.getMainLooper())
    private var isDestroyed = false

    // 轮询间隔（3秒）
    private val POLLING_INTERVAL = 3000L

    // 设备ID（固定）
    private val DEVICE_ID = "test01"

    // 聊天历史
    private val chatHistory = mutableListOf<ChatMessage>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // 绑定控件
        tvWelcome = findViewById(R.id.tv_welcome)
        tvDeviceData = findViewById(R.id.tv_device_data)
        tvDeviceStatus = findViewById(R.id.tv_device_status)
        btnSendCmd = findViewById(R.id.btn_send_cmd)
        btnRefresh = findViewById(R.id.btn_refresh)

        // 新增控件绑定
        etUserInput = findViewById(R.id.et_user_input)
        btnSendText = findViewById(R.id.btn_send_text)
        btnVoiceInput = findViewById(R.id.btn_voice_input)
        tvAiReply = findViewById(R.id.tv_ai_reply)
        recyclerView = findViewById(R.id.recycler_chat)

        // 获取登录用户名并显示
        val username = intent.getStringExtra("username") ?: "未知用户"
        tvWelcome.text = "欢迎，$username！"

        // 设置聊天列表
        setupChatList()

        // 立即获取一次设备数据
        fetchDeviceData()

        // 开始轮询
        startPolling()

        // 刷新按钮
        btnRefresh.setOnClickListener { fetchDeviceData() }

        // 下发指令按钮
        btnSendCmd.setOnClickListener { sendDeviceCommand() }

        // 新增：发送文本按钮
        btnSendText.setOnClickListener { sendUserText() }

        // 新增：语音输入按钮
        btnVoiceInput.setOnClickListener { startVoiceInput() }
    }

    private fun setupChatList() {
        recyclerView.layoutManager = LinearLayoutManager(this)
        chatAdapter = ChatAdapter(chatHistory)
        recyclerView.adapter = chatAdapter
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
                val client = OkHttpClient.Builder()
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.SECONDS)
                    .build()

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
        addChatMessage(ChatMessage(text, true))

        // 显示正在输入
        tvAiReply.text = "AI正在思考..."

        Thread {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .build()

                val json = JSONObject().apply {
                    put("text", text)
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
                            tvAiReply.text = "🤖 $reply"

                            // 添加AI回复到聊天历史
                            addChatMessage(ChatMessage("[$detailedEmotion] $reply", false))

                            Toast.makeText(this@HomeActivity,
                                "情感: $detailedEmotion", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        handler.post {
                            tvAiReply.text = "❌ 分析失败"
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                handler.post {
                    tvAiReply.text = "❌ 网络错误"
                }
            }
        }.start()
    }

    // 语音输入（需要权限）
    private fun startVoiceInput() {
        // 检查录音权限
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.RECORD_AUDIO), 101)
            return
        }

        // TODO: 调用语音识别（可以用百度/科大讯飞SDK）
        // 这里先用Toast提示
        Toast.makeText(this, "语音识别功能开发中...", Toast.LENGTH_SHORT).show()

        // 临时：模拟语音识别结果
        etUserInput.setText("我今天心情不错")
    }

    private fun addChatMessage(message: ChatMessage) {
        chatHistory.add(message)
        chatAdapter.notifyItemInserted(chatHistory.size - 1)
        recyclerView.smoothScrollToPosition(chatHistory.size - 1)
    }

    // 下发设备指令
    private fun sendDeviceCommand() {
        val cmd = "open_light"

        Toast.makeText(this, "指令发送中...", Toast.LENGTH_SHORT).show()

        Thread {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .build()

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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.isNotEmpty()
            && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startVoiceInput()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isDestroyed = true
        handler.removeCallbacksAndMessages(null)
    }
}

// 聊天消息数据类
data class ChatMessage(val text: String, val isUser: Boolean)

// 聊天适配器（完整实现）
class ChatAdapter(private val messages: List<ChatMessage>) :
    RecyclerView.Adapter<ChatAdapter.ViewHolder>() {

    // ViewHolder 类
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvMessage: TextView = itemView.findViewById(android.R.id.text1)
    }

    // 创建 ViewHolder
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        return ViewHolder(view)
    }

    // 绑定数据
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val msg = messages[position]
        holder.tvMessage.text = if (msg.isUser) "👤 ${msg.text}" else "🤖 ${msg.text}"

        // 设置背景颜色
        holder.tvMessage.setBackgroundColor(
            if (msg.isUser)
                android.graphics.Color.parseColor("#E0E0E0")  // 用户消息灰色
            else
                android.graphics.Color.parseColor("#FFFFFF")  // AI消息白色
        )

        // 设置内边距
        holder.tvMessage.setPadding(32, 16, 32, 16)
    }

    // 返回列表项数量
    override fun getItemCount(): Int = messages.size
}
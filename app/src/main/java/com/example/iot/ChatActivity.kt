package com.example.iot

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.iot.model.ChatSendResponse
import com.example.iot.network.ApiClient
import java.text.SimpleDateFormat
import java.util.*

class ChatActivity : AppCompatActivity() {

    private lateinit var apiClient: ApiClient
    private val handler = Handler(Looper.getMainLooper())

    // 聊天相关变量
    private var username: String = ""
    private var characterId: String = ""
    private var characterName: String = ""
    private var currentSessionId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        apiClient = ApiClient(this)

        try {
            // 获取登录用户名
            username = apiClient.getUsername()
            if (username.isEmpty()) {
                username = "未知用户"
            }

            // 获取传递的角色信息
            characterId = intent.getStringExtra("characterId") ?: ""
            characterName = intent.getStringExtra("characterName") ?: "AI助手"

            // 更新AI名称显示
            findViewById<TextView>(R.id.tv_ai_name)?.text = characterName

            // 设置当天日期
            updateDateDisplay()

            // 检查是否有会话ID
            currentSessionId = intent.getStringExtra("sessionId") ?: ""

            // 绑定控件
            val ivSend = findViewById<ImageView>(R.id.iv_send)
            val ivSettings = findViewById<ImageView>(R.id.iv_chat_settings)
            val ivMenu = findViewById<ImageView>(R.id.iv_chat_menu)
            val ivNewSession = findViewById<ImageView>(R.id.iv_new_session)
            val etInput = findViewById<EditText>(R.id.cet_message_input)

            // 如果有会话ID，加载历史消息
            if (currentSessionId.isNotEmpty()) {
                loadSessionDetail(currentSessionId)
            }

            // 发送按钮点击事件
            ivSend?.setOnClickListener {
                val text = etInput?.text?.toString()?.trim() ?: ""
                if (text.isEmpty()) {
                    Toast.makeText(this, "请输入内容", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // 清空输入框
                etInput?.text?.clear()

                // 添加用户消息
                addUserMessage(text)

                // 显示加载状态
                val loadingIndex = addTemporaryAiMessage("AI正在输入...")

                // 如果是新对话，先创建会话
                if (currentSessionId.isEmpty() && characterId.isNotEmpty()) {
                    createNewSession(text) { sessionId ->
                        if (sessionId != null) {
                            currentSessionId = sessionId
                            sendMessageToAI(text, sessionId, loadingIndex)
                        } else {
                            // 创建会话失败，直接发送
                            sendMessageToAI(text, "", loadingIndex)
                        }
                    }
                } else {
                    // 使用现有会话发送消息
                    sendMessageToAI(text, currentSessionId, loadingIndex)
                }
            }

            // 设置按钮点击事件
            ivSettings?.setOnClickListener {
                val intent = Intent(this@ChatActivity, SettingsActivity::class.java)
                startActivity(intent)
            }

            // 菜单按钮 - 跳转到历史会话
            ivMenu?.setOnClickListener {
                val intent = Intent(this@ChatActivity, SessionsActivity::class.java)
                startActivity(intent)
            }

            // 新建对话按钮
            ivNewSession?.setOnClickListener {
                // 清空当前会话，重新开始
                currentSessionId = ""
                characterId = ""
                characterName = "AI助手"
                findViewById<TextView>(R.id.tv_ai_name)?.text = characterName
                
                // 清空聊天区域
                val chatContainer = findViewById<LinearLayout>(R.id.ll_welcome_message)
                chatContainer?.removeAllViews()
                
                // 重新添加欢迎消息
                addWelcomeMessage()
                
                etInput?.text?.clear()
                Toast.makeText(this, "已开启新对话", Toast.LENGTH_SHORT).show()
            }

            // 如果有提示词，自动发送
            val prompt = intent.getStringExtra("prompt")
            if (prompt != null && prompt.isNotEmpty()) {
                handler.postDelayed({
                    etInput?.setText(prompt)
                    ivSend?.performClick()
                }, 500)
            } else if (currentSessionId.isEmpty()) {
                // 没有提示词且不是历史会话，显示欢迎消息
                addWelcomeMessage()
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "界面初始化失败: ${e.message}", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    // 设置当天日期显示
    private fun updateDateDisplay() {
        val dateFormat = SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault())
        val currentDate = dateFormat.format(Date())
        findViewById<TextView>(R.id.tv_chat_date)?.text = currentDate
    }

    // 创建新会话
    private fun createNewSession(firstMessage: String, callback: (String?) -> Unit) {
        // 不传title，让后端默认设置为"新对话"
        apiClient.createSession("", characterId) { success, msg, session ->
            if (success && session != null) {
                callback(session.sessionId)
            } else {
                callback(null)
            }
        }
    }

    // 发送消息到AI
    private fun sendMessageToAI(text: String, sessionId: String, loadingIndex: Int) {
        apiClient.sendChatMessage(text, username, characterId, sessionId) { success, msg, response ->
            handler.post {
                // 移除临时消息
                if (loadingIndex != -1) {
                    findViewById<LinearLayout>(R.id.ll_welcome_message)?.removeViewAt(loadingIndex)
                }

                if (success && response != null) {
                    // 更新会话ID
                    currentSessionId = response.sessionId

                    // 添加AI回复
                    addAiMessage(response.reply)
                } else {
                    addAiMessage(msg)
                }
            }
        }
    }

    // 添加用户消息
    private fun addUserMessage(message: String) {
        val messageLayout = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 16, 0, 0)
            }
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.END
            setPadding(20, 0, 20, 0)
        }

        val bubbleLayout = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            background = resources.getDrawable(R.drawable.xml_ai_bubble, theme)
            setPadding(16, 16, 16, 16)
            orientation = LinearLayout.VERTICAL
        }

        val textView = TextView(this).apply {
            text = message
            textSize = 14f
            setTextColor(resources.getColor(R.color.text_black, theme))
        }

        bubbleLayout.addView(textView)
        
        messageLayout.addView(bubbleLayout)
        messageLayout.addView(createMessageActionBar(message, true))
        findViewById<LinearLayout>(R.id.ll_welcome_message)?.addView(messageLayout)
        scrollToBottom()
    }

    // 添加AI消息
    private fun addAiMessage(message: String) {
        val messageLayout = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 16, 0, 0)
            }
            orientation = LinearLayout.VERTICAL
            setPadding(20, 0, 20, 0)
        }

        // AI头像和名称
        val headerLayout = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 8)
            }
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val avatarLayout = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(24, 24)
            background = resources.getDrawable(R.drawable.xml_ai_avatar_bg, theme)
            gravity = android.view.Gravity.CENTER
        }

        val avatarIcon = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(16, 16)
            setImageResource(R.drawable.icon_ai_star)
        }

        val nameText = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(8, 0, 0, 0)
            }
            text = characterName
            textSize = 11f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.text_gray, theme))
        }

        avatarLayout.addView(avatarIcon)
        headerLayout.addView(avatarLayout)
        headerLayout.addView(nameText)

        // AI消息气泡
        val bubbleLayout = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            background = resources.getDrawable(R.drawable.xml_ai_bubble, theme)
            setPadding(16, 16, 16, 16)
            orientation = LinearLayout.VERTICAL
        }

        val messageText = TextView(this).apply {
            text = message
            textSize = 14f
            setTextColor(resources.getColor(R.color.text_black, theme))
        }

        bubbleLayout.addView(messageText)
        
        messageLayout.addView(headerLayout)
        messageLayout.addView(bubbleLayout)
        messageLayout.addView(createMessageActionBar(message, false))
        findViewById<LinearLayout>(R.id.ll_welcome_message)?.addView(messageLayout)
        scrollToBottom()
    }

    // 添加临时AI消息
    private fun addTemporaryAiMessage(message: String): Int {
        val messageLayout = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 16, 0, 0)
            }
            orientation = LinearLayout.VERTICAL
            setPadding(20, 0, 20, 0)
        }

        // AI头像和名称
        val headerLayout = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 8)
            }
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val avatarLayout = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(24, 24)
            background = resources.getDrawable(R.drawable.xml_ai_avatar_bg, theme)
            gravity = android.view.Gravity.CENTER
        }

        val avatarIcon = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(16, 16)
            setImageResource(R.drawable.icon_ai_star)
        }

        val nameText = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(8, 0, 0, 0)
            }
            text = characterName
            textSize = 11f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.text_gray, theme))
        }

        avatarLayout.addView(avatarIcon)
        headerLayout.addView(avatarLayout)
        headerLayout.addView(nameText)

        // AI消息气泡
        val bubbleLayout = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            background = resources.getDrawable(R.drawable.xml_ai_bubble, theme)
            setPadding(16, 16, 16, 16)
            orientation = LinearLayout.VERTICAL
        }

        val messageText = TextView(this).apply {
            text = message
            textSize = 14f
            setTextColor(resources.getColor(R.color.text_gray, theme))
        }

        bubbleLayout.addView(messageText)
        messageLayout.addView(headerLayout)
        messageLayout.addView(bubbleLayout)
        findViewById<LinearLayout>(R.id.ll_welcome_message)?.addView(messageLayout)
        scrollToBottom()
        return findViewById<LinearLayout>(R.id.ll_welcome_message)?.indexOfChild(messageLayout) ?: -1
    }

    // 创建消息操作栏（复制、点赞、点踩）
    private fun createMessageActionBar(message: String, isUser: Boolean): View {
        val actionBar = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 6, 0, 0)
            }
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val iconSize = 28
        val iconPadding = 4

        // 复制按钮
        val copyBtn = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
            setImageResource(R.drawable.icon_copy)
            setPadding(iconPadding, iconPadding, iconPadding, iconPadding)
            setOnClickListener {
                copyToClipboard(message)
            }
        }

        // 分隔线
        val divider1 = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(1, 14).apply {
                setMargins(4, 0, 4, 0)
            }
            setBackgroundColor(resources.getColor(R.color.line_gray, theme))
        }

        // 点赞按钮
        val likeBtn = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
            setImageResource(R.drawable.icon_like)
            setPadding(iconPadding, iconPadding, iconPadding, iconPadding)
            setOnClickListener {
                Toast.makeText(this@ChatActivity, "已点赞", Toast.LENGTH_SHORT).show()
            }
        }

        // 分隔线
        val divider2 = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(1, 14).apply {
                setMargins(4, 0, 4, 0)
            }
            setBackgroundColor(resources.getColor(R.color.line_gray, theme))
        }

        // 点踩按钮
        val dislikeBtn = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
            setImageResource(R.drawable.icon_dislike)
            setPadding(iconPadding, iconPadding, iconPadding, iconPadding)
            setOnClickListener {
                Toast.makeText(this@ChatActivity, "已点踩", Toast.LENGTH_SHORT).show()
            }
        }

        actionBar.addView(copyBtn)
        actionBar.addView(divider1)
        actionBar.addView(likeBtn)
        actionBar.addView(divider2)
        actionBar.addView(dislikeBtn)

        return actionBar
    }

    // 添加到剪贴板
    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("消息内容", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }

    // 滚动到底部
    private fun scrollToBottom() {
        findViewById<ScrollView>(R.id.scrollView)?.post {
            findViewById<ScrollView>(R.id.scrollView)?.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }

    // 加载会话详情
    private fun loadSessionDetail(sessionId: String) {
        apiClient.getSessionDetail(sessionId) { success, msg, sessionDetail ->
            runOnUiThread {
                if (success && sessionDetail != null) {
                    // 清空聊天区域
                    val chatContainer = findViewById<LinearLayout>(R.id.ll_welcome_message)
                    chatContainer?.removeAllViews()

                    // 加载历史消息，智能纠正后端返回的错误顺序
                    val messages = sessionDetail.messages
                    val correctedMessages = if (messages.size >= 2) {
                        // 检查第一条消息：如果是AI消息（isUser=false），说明顺序反了
                        val isFirstMessageWrong = !messages[0].isUser
                        if (isFirstMessageWrong) {
                            // 反转消息列表，纠正顺序
                            messages.reversed()
                        } else {
                            messages
                        }
                    } else {
                        messages
                    }

                    for (message in correctedMessages) {
                        if (message.isUser) {
                            addUserMessage(message.content)
                        } else {
                            addAiMessage(message.content)
                        }
                    }

                    // 如果有角色ID，更新角色名称
                    if (sessionDetail.characterId.isNotEmpty()) {
                        apiClient.getCharacterDetail(sessionDetail.characterId) { charSuccess, charMsg, character ->
                            runOnUiThread {
                                if (charSuccess && character != null) {
                                    characterName = character.name
                                    findViewById<TextView>(R.id.tv_ai_name)?.text = character.name
                                }
                            }
                        }
                    }
                } else {
                    // 加载失败，显示欢迎消息
                    addWelcomeMessage()
                }
            }
        }
    }

    // 添加欢迎消息
    private fun addWelcomeMessage() {
        val welcomeLayout = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 0)
        }

        // AI头像和名称
        val headerLayout = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 8)
            }
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val avatarLayout = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(24, 24)
            background = resources.getDrawable(R.drawable.xml_ai_avatar_bg, theme)
            gravity = android.view.Gravity.CENTER
        }

        val avatarIcon = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(16, 16)
            setImageResource(R.drawable.icon_ai_star)
        }

        val nameText = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(8, 0, 0, 0)
            }
            text = characterName
            textSize = 11f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.text_gray, theme))
        }

        avatarLayout.addView(avatarIcon)
        headerLayout.addView(avatarLayout)
        headerLayout.addView(nameText)

        // 欢迎消息气泡
        val bubbleLayout = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            background = resources.getDrawable(R.drawable.xml_ai_bubble, theme)
            setPadding(16, 16, 16, 16)
            orientation = LinearLayout.VERTICAL
        }

        val messageText = TextView(this).apply {
            text = "你好！我是你的AI助手。今天有什么可以帮你的吗？"
            textSize = 14f
            setTextColor(resources.getColor(R.color.text_black, theme))
        }

        bubbleLayout.addView(messageText)
        welcomeLayout.addView(headerLayout)
        welcomeLayout.addView(bubbleLayout)
        findViewById<LinearLayout>(R.id.ll_welcome_message)?.addView(welcomeLayout)
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
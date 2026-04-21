package com.example.iot

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.iot.model.Session
import com.example.iot.network.ApiClient

class SessionsActivity : AppCompatActivity() {

    private lateinit var apiClient: ApiClient

    // 搜索相关
    private lateinit var etSearch: EditText

    // 置顶会话
    private lateinit var tvPinnedTitle: TextView
    private lateinit var llPinnedContainer: LinearLayout
    private lateinit var llPinnedSession1: LinearLayout
    private lateinit var tvPinnedTitle1: TextView
    private lateinit var tvPinnedTime1: TextView
    private lateinit var tvPinnedPreview1: TextView
    private lateinit var llPinnedSession2: LinearLayout
    private lateinit var tvPinnedTitle2: TextView
    private lateinit var tvPinnedTime2: TextView
    private lateinit var tvPinnedPreview2: TextView

    // 今天会话
    private lateinit var tvTodayTitle: TextView
    private lateinit var llTodayContainer: LinearLayout
    private lateinit var llSession1: LinearLayout
    private lateinit var tvSessionTitle1: TextView
    private lateinit var tvSessionInfo1: TextView
    private lateinit var ivSessionMenu1: ImageView
    private lateinit var llSession2: LinearLayout
    private lateinit var tvSessionTitle2: TextView
    private lateinit var tvSessionInfo2: TextView
    private lateinit var ivSessionMenu2: ImageView

    // 昨天会话
    private lateinit var tvYesterdayTitle: TextView
    private lateinit var llYesterdayContainer: LinearLayout
    private lateinit var llSession3: LinearLayout
    private lateinit var tvSessionTitle3: TextView
    private lateinit var tvSessionInfo3: TextView
    private lateinit var llSession4: LinearLayout
    private lateinit var tvSessionTitle4: TextView
    private lateinit var tvSessionInfo4: TextView

    // 数据列表
    private var pinnedSessions: List<Session> = emptyList()
    private var todaySessions: List<Session> = emptyList()
    private var yesterdaySessions: List<Session> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sessions)

        apiClient = ApiClient(this)

        try {
            // 绑定控件
            bindViews()

            // 返回按钮
            findViewById<TextView>(R.id.tv_back_to_chat)?.setOnClickListener {
                finish()
            }

            // 删除所有按钮
            findViewById<ImageView>(R.id.iv_delete_all)?.setOnClickListener {
                showDeleteAllDialog()
            }

            // 搜索框
            etSearch.setOnEditorActionListener { _, _, _ ->
                val keyword = etSearch.text.toString().trim()
                if (keyword.isNotEmpty()) {
                    searchSessions(keyword)
                } else {
                    loadSessionList()
                }
                true
            }

            // 加载会话列表
            loadSessionList()

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "界面初始化失败", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    // 绑定所有控件
    private fun bindViews() {
        // 搜索框
        etSearch = findViewById(R.id.cet_search_session)

        // 置顶区域
        tvPinnedTitle = findViewById(R.id.tv_pinned_sessions)
        llPinnedContainer = findViewById(R.id.ll_pinned_sessions)
        llPinnedSession1 = findViewById(R.id.ll_pinned_session_1)
        tvPinnedTitle1 = findViewById(R.id.tv_pinned_title_1)
        tvPinnedTime1 = findViewById(R.id.tv_pinned_time_1)
        tvPinnedPreview1 = findViewById(R.id.tv_pinned_preview_1)
        llPinnedSession2 = findViewById(R.id.ll_pinned_session_2)
        tvPinnedTitle2 = findViewById(R.id.tv_pinned_title_2)
        tvPinnedTime2 = findViewById(R.id.tv_pinned_time_2)
        tvPinnedPreview2 = findViewById(R.id.tv_pinned_preview_2)

        // 今天区域
        tvTodayTitle = findViewById(R.id.tv_today_sessions)
        llTodayContainer = findViewById(R.id.ll_today_sessions)
        llSession1 = findViewById(R.id.ll_session_1)
        tvSessionTitle1 = findViewById(R.id.tv_session_title_1)
        tvSessionInfo1 = findViewById(R.id.tv_session_info_1)
        ivSessionMenu1 = findViewById(R.id.iv_session_menu_1)
        llSession2 = findViewById(R.id.ll_session_2)
        tvSessionTitle2 = findViewById(R.id.tv_session_title_2)
        tvSessionInfo2 = findViewById(R.id.tv_session_info_2)
        ivSessionMenu2 = findViewById(R.id.iv_session_menu_2)

        // 昨天区域
        tvYesterdayTitle = findViewById(R.id.tv_yesterday_sessions)
        llYesterdayContainer = findViewById(R.id.ll_yesterday_sessions)
        llSession3 = findViewById(R.id.ll_session_3)
        tvSessionTitle3 = findViewById(R.id.tv_session_title_3)
        tvSessionInfo3 = findViewById(R.id.tv_session_info_3)
        llSession4 = findViewById(R.id.ll_session_4)
        tvSessionTitle4 = findViewById(R.id.tv_session_title_4)
        tvSessionInfo4 = findViewById(R.id.tv_session_info_4)
    }

    // 加载会话列表
    private fun loadSessionList() {
        apiClient.getSessionList { success, msg, response ->
            runOnUiThread {
                if (success && response != null) {
                    pinnedSessions = response.pinned
                    todaySessions = response.today
                    yesterdaySessions = response.yesterday
                    updateSessionListUI()
                } else {
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 更新会话列表UI
    private fun updateSessionListUI() {
        // 更新置顶会话
        updatePinnedSessions()

        // 更新今天会话
        updateTodaySessions()

        // 更新昨天会话
        updateYesterdaySessions()
    }

    // 更新置顶会话UI
    private fun updatePinnedSessions() {
        if (pinnedSessions.isEmpty()) {
            tvPinnedTitle.visibility = View.GONE
            llPinnedContainer.visibility = View.GONE
            return
        }

        tvPinnedTitle.visibility = View.VISIBLE
        llPinnedContainer.visibility = View.VISIBLE

        // 置顶会话1
        if (pinnedSessions.isNotEmpty()) {
            val session = pinnedSessions[0]
            llPinnedSession1.visibility = View.VISIBLE
            tvPinnedTitle1.text = session.title
            tvPinnedTime1.text = formatTime(session.lastMessageTime)
            tvPinnedPreview1.text = session.preview
            llPinnedSession1.setOnClickListener {
                openSession(session.sessionId)
            }
        } else {
            llPinnedSession1.visibility = View.GONE
        }

        // 置顶会话2
        if (pinnedSessions.size > 1) {
            val session = pinnedSessions[1]
            llPinnedSession2.visibility = View.VISIBLE
            tvPinnedTitle2.text = session.title
            tvPinnedTime2.text = formatTime(session.lastMessageTime)
            tvPinnedPreview2.text = session.preview
            llPinnedSession2.setOnClickListener {
                openSession(session.sessionId)
            }
        } else {
            llPinnedSession2.visibility = View.GONE
        }
    }

    // 更新今天会话UI
    private fun updateTodaySessions() {
        if (todaySessions.isEmpty()) {
            tvTodayTitle.visibility = View.GONE
            llTodayContainer.visibility = View.GONE
            return
        }

        tvTodayTitle.visibility = View.VISIBLE
        llTodayContainer.visibility = View.VISIBLE

        // 会话1
        if (todaySessions.isNotEmpty()) {
            val session = todaySessions[0]
            llSession1.visibility = View.VISIBLE
            tvSessionTitle1.text = session.title
            tvSessionInfo1.text = "${session.characterName} · ${formatTime(session.lastMessageTime)}"
            ivSessionMenu1.visibility = View.VISIBLE
            llSession1.setOnClickListener {
                openSession(session.sessionId)
            }
            ivSessionMenu1.setOnClickListener {
                showSessionMenu(session.sessionId, session.title)
            }
        } else {
            llSession1.visibility = View.GONE
        }

        // 会话2
        if (todaySessions.size > 1) {
            val session = todaySessions[1]
            llSession2.visibility = View.VISIBLE
            tvSessionTitle2.text = session.title
            tvSessionInfo2.text = "${session.characterName} · ${formatTime(session.lastMessageTime)}"
            ivSessionMenu2.visibility = View.VISIBLE
            llSession2.setOnClickListener {
                openSession(session.sessionId)
            }
            ivSessionMenu2.setOnClickListener {
                showSessionMenu(session.sessionId, session.title)
            }
        } else {
            llSession2.visibility = View.GONE
        }
    }

    // 更新昨天会话UI
    private fun updateYesterdaySessions() {
        if (yesterdaySessions.isEmpty()) {
            tvYesterdayTitle.visibility = View.GONE
            llYesterdayContainer.visibility = View.GONE
            return
        }

        tvYesterdayTitle.visibility = View.VISIBLE
        llYesterdayContainer.visibility = View.VISIBLE

        // 会话3
        if (yesterdaySessions.isNotEmpty()) {
            val session = yesterdaySessions[0]
            llSession3.visibility = View.VISIBLE
            tvSessionTitle3.text = session.title
            tvSessionInfo3.text = "${session.characterName} · ${formatTime(session.lastMessageTime)}"
            llSession3.setOnClickListener {
                openSession(session.sessionId)
            }
        } else {
            llSession3.visibility = View.GONE
        }

        // 会话4
        if (yesterdaySessions.size > 1) {
            val session = yesterdaySessions[1]
            llSession4.visibility = View.VISIBLE
            tvSessionTitle4.text = session.title
            tvSessionInfo4.text = "${session.characterName} · ${formatTime(session.lastMessageTime)}"
            llSession4.setOnClickListener {
                openSession(session.sessionId)
            }
        } else {
            llSession4.visibility = View.GONE
        }
    }

    // 搜索会话
    private fun searchSessions(keyword: String) {
        apiClient.searchSessions(keyword) { success, msg, sessions ->
            runOnUiThread {
                if (success && sessions != null) {
                    // 将搜索结果合并显示
                    todaySessions = sessions
                    yesterdaySessions = emptyList()
                    pinnedSessions = emptyList()
                    updateSessionListUI()
                } else {
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 打开会话
    private fun openSession(sessionId: String) {
        val intent = Intent(this@SessionsActivity, ChatActivity::class.java)
        intent.putExtra("sessionId", sessionId)
        startActivity(intent)
        finish()
    }

    // 显示会话操作菜单
    private fun showSessionMenu(sessionId: String, title: String) {
        val options = arrayOf("置顶会话", "删除会话")
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> togglePinSession(sessionId, true)
                    1 -> deleteSession(sessionId)
                }
            }
            .show()
    }

    // 置顶/取消置顶会话
    private fun togglePinSession(sessionId: String, isPinned: Boolean) {
        apiClient.updateSession(sessionId, "", isPinned) { success, msg ->
            runOnUiThread {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                if (success) {
                    loadSessionList()
                }
            }
        }
    }

    // 删除单个会话
    private fun deleteSession(sessionId: String) {
        AlertDialog.Builder(this)
            .setTitle("删除会话")
            .setMessage("确定要删除这个会话吗？")
            .setPositiveButton("确定") { _, _ ->
                apiClient.deleteSession(sessionId) { success, msg ->
                    runOnUiThread {
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                        if (success) {
                            loadSessionList()
                        }
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // 显示删除所有会话对话框
    private fun showDeleteAllDialog() {
        AlertDialog.Builder(this)
            .setTitle("删除所有会话")
            .setMessage("确定要删除所有会话吗？此操作不可恢复。")
            .setPositiveButton("确定") { _, _ ->
                deleteAllSessions()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // 删除所有会话
    private fun deleteAllSessions() {
        apiClient.deleteAllSessions { success, msg ->
            runOnUiThread {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                if (success) {
                    loadSessionList()
                }
            }
        }
    }

    // 格式化时间显示
    private fun formatTime(timeStr: String): String {
        if (timeStr.isEmpty()) return ""
        // 简单截取，例如 "2026-04-20 12:00:00" -> "12:00"
        return try {
            if (timeStr.length > 16) {
                timeStr.substring(11, 16)
            } else {
                timeStr
            }
        } catch (e: Exception) {
            timeStr
        }
    }

    override fun onResume() {
        super.onResume()
        loadSessionList()
    }
}
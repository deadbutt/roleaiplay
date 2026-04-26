package com.example.iot

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.iot.adapter.SessionAdapter
import com.example.iot.adapter.SessionItem
import com.example.iot.model.Session
import com.example.iot.network.ApiClient

class SessionsActivity : AppCompatActivity() {

    private lateinit var apiClient: ApiClient
    private lateinit var etSearch: EditText
    private lateinit var rvSessions: RecyclerView
    private lateinit var adapter: SessionAdapter

    private var pinnedSessions: List<Session> = emptyList()
    private var todaySessions: List<Session> = emptyList()
    private var yesterdaySessions: List<Session> = emptyList()
    private var earlierSessions: List<Session> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sessions)

        apiClient = ApiClient(this)

        try {
            etSearch = findViewById(R.id.cet_search_session)
            rvSessions = findViewById(R.id.rv_sessions)

            adapter = SessionAdapter(
                context = this,
                onSessionDelete = { sessionId -> deleteSession(sessionId) },
                onSessionPin = { sessionId, isPinned -> togglePinSession(sessionId, isPinned) },
                onRefresh = { loadSessionList() }
            )
            rvSessions.layoutManager = LinearLayoutManager(this)
            rvSessions.adapter = adapter

            findViewById<TextView>(R.id.tv_back_to_chat)?.setOnClickListener {
                finish()
            }

            findViewById<ImageView>(R.id.iv_delete_all)?.setOnClickListener {
                showDeleteAllDialog()
            }

            etSearch.setOnEditorActionListener { _, _, _ ->
                val keyword = etSearch.text.toString().trim()
                if (keyword.isNotEmpty()) {
                    searchSessions(keyword)
                } else {
                    loadSessionList()
                }
                true
            }

            loadSessionList()

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "界面初始化失败", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

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

    private fun updateSessionListUI() {
        val items = mutableListOf<SessionItem>()

        if (pinnedSessions.isNotEmpty()) {
            items.add(SessionItem.Header("置顶工作流"))
            pinnedSessions.forEach { session ->
                items.add(SessionItem.PinnedSessionItem(session))
            }
        }

        if (todaySessions.isNotEmpty()) {
            items.add(SessionItem.Header("今天"))
            todaySessions.forEach { session ->
                items.add(SessionItem.NormalSessionItem(session))
            }
        }

        if (yesterdaySessions.isNotEmpty()) {
            items.add(SessionItem.Header("昨天"))
            yesterdaySessions.forEach { session ->
                items.add(SessionItem.NormalSessionItem(session))
            }
        }

        if (earlierSessions.isNotEmpty()) {
            items.add(SessionItem.Header("更早"))
            earlierSessions.forEach { session ->
                items.add(SessionItem.NormalSessionItem(session))
            }
        }

        adapter.setData(items)
    }

    private fun searchSessions(keyword: String) {
        apiClient.searchSessions(keyword) { success, msg, sessions ->
            runOnUiThread {
                if (success && sessions != null) {
                    todaySessions = sessions
                    yesterdaySessions = emptyList()
                    pinnedSessions = emptyList()
                    earlierSessions = emptyList()
                    updateSessionListUI()
                } else {
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun openSession(sessionId: String) {
        val intent = Intent(this@SessionsActivity, ChatActivity::class.java)
        intent.putExtra("sessionId", sessionId)
        startActivity(intent)
        finish()
    }

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

    override fun onResume() {
        super.onResume()
        loadSessionList()
    }
}

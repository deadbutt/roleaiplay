package com.example.iot

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.iot.network.ApiClient

class SettingsActivity : AppCompatActivity() {

    private lateinit var apiClient: ApiClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        apiClient = ApiClient(this)

        // 设置用户邮箱
        val tvUserEmail = findViewById<android.widget.TextView>(R.id.tv_user_email)
        val email = apiClient.getUsername()
        tvUserEmail?.text = if (email.isNotEmpty()) email else "未设置邮箱"

        try {
            // 返回按钮
            findViewById<android.widget.ImageView>(R.id.iv_settings_back)?.setOnClickListener {
                finish()
            }

            // 管理人物卡片点击事件
            findViewById<LinearLayout>(R.id.ll_character_cards)?.setOnClickListener {
                val intent = Intent(this@SettingsActivity, CharacterListActivity::class.java)
                startActivity(intent)
            }

            // 清空对话记录点击事件
            findViewById<LinearLayout>(R.id.ll_clear_history)?.setOnClickListener {
                showClearHistoryDialog()
            }

            // 退出账号点击事件
            findViewById<LinearLayout>(R.id.ll_logout)?.setOnClickListener {
                showLogoutDialog()
            }

        } catch (e: Exception) {
            e.printStackTrace()
            finish()
        }
    }

    // 显示清空对话记录对话框
    private fun showClearHistoryDialog() {
        AlertDialog.Builder(this)
            .setTitle("清空对话记录")
            .setMessage("确定要清空所有对话记录吗？此操作不可恢复。")
            .setPositiveButton("确定") { _, _ ->
                deleteAllSessions()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // 清空所有会话
    private fun deleteAllSessions() {
        apiClient.deleteAllSessions { success, msg ->
            runOnUiThread {
                if (success) {
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 显示退出账号对话框
    private fun showLogoutDialog() {
        AlertDialog.Builder(this)
            .setTitle("退出账号")
            .setMessage("确定要退出当前账号吗？")
            .setPositiveButton("确定") { _, _ ->
                performLogout()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // 执行退出登录
    private fun performLogout() {
        apiClient.logout { success, msg ->
            runOnUiThread {
                if (success) {
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                    // 跳转到登录界面
                    val intent = Intent(this@SettingsActivity, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                } else {
                    // 即使后端失败，也清除本地登录信息
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                    val intent = Intent(this@SettingsActivity, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
            }
        }
    }
}
package com.example.iot

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.iot.network.ApiClient
import java.io.File

class SettingsFragment : Fragment() {

    private lateinit var apiClient: ApiClient
    private lateinit var tvNickname: TextView
    private lateinit var tvEmail: TextView
    private lateinit var tvCacheSize: TextView
    private lateinit var ivAvatar: ImageView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        apiClient = ApiClient(requireContext())

        // 绑定控件
        tvNickname = view.findViewById<TextView>(R.id.tv_nickname)
        tvEmail = view.findViewById<TextView>(R.id.tv_email)
        tvCacheSize = view.findViewById<TextView>(R.id.tv_cache_size)
        ivAvatar = view.findViewById<ImageView>(R.id.iv_avatar)

        val llChangePassword = view.findViewById<LinearLayout>(R.id.ll_change_password)
        val llAbout = view.findViewById<LinearLayout>(R.id.ll_about)
        val llClearCache = view.findViewById<LinearLayout>(R.id.ll_clear_cache)
        val llLogout = view.findViewById<LinearLayout>(R.id.ll_logout)

        // 加载用户信息
        loadUserInfo()

        // 计算缓存大小
        calculateCacheSize()

        // 修改密码
        llChangePassword.setOnClickListener {
            val intent = Intent(requireContext(), PasswordVerificationActivity::class.java)
            startActivity(intent)
        }

        // 关于
        llAbout.setOnClickListener {
            showAboutDialog()
        }

        // 清除缓存
        llClearCache.setOnClickListener {
            showClearCacheDialog()
        }

        // 退出登录
        llLogout.setOnClickListener {
            showLogoutDialog()
        }
    }

    private fun loadUserInfo() {
        val email = apiClient.getUsername()
        tvNickname.text = if (email.isNotEmpty()) email else "用户"
        tvEmail.text = if (email.isNotEmpty()) email else "未设置邮箱"
    }

    private fun calculateCacheSize() {
        try {
            val cacheDir = requireContext().cacheDir
            val size = getFolderSize(cacheDir)
            val sizeMB = size / (1024 * 1024)
            tvCacheSize.text = "${sizeMB} MB"
        } catch (e: Exception) {
            tvCacheSize.text = "0 MB"
        }
    }

    private fun getFolderSize(folder: File): Long {
        var size: Long = 0
        val files = folder.listFiles()
        if (files != null) {
            for (file in files) {
                if (file.isDirectory) {
                    size += getFolderSize(file)
                } else {
                    size += file.length()
                }
            }
        }
        return size
    }

    private fun clearCache() {
        try {
            val cacheDir = requireContext().cacheDir
            deleteFolderContents(cacheDir)
            calculateCacheSize()
            Toast.makeText(requireContext(), "缓存已清除", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "清除失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteFolderContents(folder: File) {
        val files = folder.listFiles()
        if (files != null) {
            for (file in files) {
                if (file.isDirectory) {
                    deleteFolderContents(file)
                } else {
                    file.delete()
                }
            }
        }
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("关于")
            .setMessage("IoT 智能设备控制\n版本: 1.0.0\n\n一款智能设备管理与AI助手应用")
            .setPositiveButton("确定", null)
            .show()
    }

    private fun showClearCacheDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("清除缓存")
            .setMessage("确定要清除所有缓存数据吗？")
            .setPositiveButton("确定") { _, _ ->
                clearCache()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showLogoutDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("退出登录")
            .setMessage("确定要退出当前账号吗？")
            .setPositiveButton("确定") { _, _ ->
                performLogout()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun performLogout() {
        // 清除登录状态
        apiClient.clearLoginInfo()

        // 清除设备列表
        com.example.iot.util.DeviceStorage.clearDevices(requireContext())

        // 跳转到登录页面
        val intent = Intent(requireContext(), MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }
}
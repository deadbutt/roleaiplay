package com.example.iot

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.iot.network.ApiClient

class MainActivity : AppCompatActivity() {

    // 控件声明
    private lateinit var btnLogin: TextView
    private lateinit var btnRegister: TextView

    private lateinit var apiClient: ApiClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        apiClient = ApiClient(this)

        // 检查是否已登录（有Token）
        val token = apiClient.getToken()
        if (token.isNotEmpty()) {
            // 有Token，直接跳转到ChatActivity
            val intent = Intent(this@MainActivity, ChatActivity::class.java)
            startActivity(intent)
            finish()
            return
        }

        // 没有Token，显示入口选择界面
        setContentView(R.layout.activity_main)

        // 绑定控件
        btnLogin = findViewById(R.id.btn_login)
        btnRegister = findViewById(R.id.btn_register)

        // 登录按钮点击事件
        btnLogin.setOnClickListener {
            val intent = Intent(this@MainActivity, LoginActivity::class.java)
            startActivity(intent)
        }

        // 注册按钮点击事件
        btnRegister.setOnClickListener {
            val intent = Intent(this@MainActivity, RegisterActivity::class.java)
            startActivity(intent)
        }
    }
}
package com.example.iot

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import org.json.JSONObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class MainActivity : AppCompatActivity() {

    // 控件声明
    private lateinit var etUsername: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var tvTip: android.widget.TextView
    private lateinit var btnRegister: MaterialButton
    private lateinit var btnLogin: MaterialButton

    // 后端接口地址
    private val BASE_URL = "http://47.118.22.220:8081/api/"
    private val JSON = "application/json; charset=utf-8".toMediaType()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 绑定控件
        etUsername = findViewById(R.id.et_username)
        etPassword = findViewById(R.id.et_password)
        tvTip = findViewById(R.id.tv_tip)
        btnRegister = findViewById(R.id.btn_register)
        btnLogin = findViewById(R.id.btn_login)

        // 注册按钮点击事件
        btnRegister.setOnClickListener { registerUser() }

        // 登录按钮点击事件
        btnLogin.setOnClickListener { loginUser() }
    }

    // 注册用户
    private fun registerUser() {
        val username = etUsername.text.toString().trim()
        val password = etPassword.text.toString().trim()

        // 校验输入
        if (username.isEmpty() || password.isEmpty()) {
            tvTip.text = "用户名或密码不能为空"
            return
        }

        // 清空提示
        tvTip.text = ""

        // 子线程发起网络请求
        Thread {
            try {
                val client = OkHttpClient()
                // 构造JSON请求体
                val json = JSONObject().apply {
                    put("username", username)
                    put("password", password)
                }
                val body = json.toString().toRequestBody(JSON)

                // 发起POST请求
                val request = Request.Builder()
                    .url("${BASE_URL}register")
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful && response.body != null) {
                    val result = response.body!!.string()
                    val resJson = JSONObject(result)
                    val msg = resJson.getString("msg")

                    // 主线程更新UI
                    Handler(Looper.getMainLooper()).post {
                        tvTip.text = msg
                        if (msg.contains("成功")) {
                            Toast.makeText(this@MainActivity, "注册成功，可登录", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
                Handler(Looper.getMainLooper()).post {
                    tvTip.text = "注册失败：网络错误"
                }
            } catch (e: org.json.JSONException) {
                e.printStackTrace()
                Handler(Looper.getMainLooper()).post {
                    tvTip.text = "注册失败：数据解析错误"
                }
            }
        }.start()
    }

    // 登录用户
    private fun loginUser() {
        val username = etUsername.text.toString().trim()
        val password = etPassword.text.toString().trim()

        // 校验输入
        if (username.isEmpty() || password.isEmpty()) {
            tvTip.text = "用户名或密码不能为空"
            return
        }

        // 清空提示
        tvTip.text = ""

        // 子线程发起网络请求
        Thread {
            try {
                val client = OkHttpClient()
                // 构造JSON请求体
                val json = JSONObject().apply {
                    put("username", username)
                    put("password", password)
                }
                val body = json.toString().toRequestBody(JSON)

                // 发起POST请求
                val request = Request.Builder()
                    .url("${BASE_URL}login")
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful && response.body != null) {
                    val result = response.body!!.string()
                    val resJson = JSONObject(result)
                    val msg = resJson.getString("msg")

                    // 主线程更新UI
                    Handler(Looper.getMainLooper()).post {
                        tvTip.text = msg
                        if (msg.contains("成功")) {
                            // 登录成功，跳转到主界面
                            val intent = Intent(this@MainActivity, HomeActivity::class.java)
                            intent.putExtra("username", username)
                            startActivity(intent)
                            finish() // 关闭登录页
                        }
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
                Handler(Looper.getMainLooper()).post {
                    tvTip.text = "登录失败：网络错误"
                }
            } catch (e: org.json.JSONException) {
                e.printStackTrace()
                Handler(Looper.getMainLooper()).post {
                    tvTip.text = "登录失败：数据解析错误"
                }
            }
        }.start()
    }
}
package com.example.iot

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class RegisterPasswordActivity : AppCompatActivity() {

    // 控件声明
    private lateinit var etPassword: EditText
    private lateinit var etConfirmPassword: EditText
    private lateinit var ctvError: TextView
    private lateinit var ctvRegister: TextView
    private lateinit var ivPasswordToggle: ImageView
    private lateinit var ivPasswordConfirmToggle: ImageView
    
    // 用户信息
    private var userEmail: String = ""
    private var verificationCode: String = ""

    // 后端接口地址
    private val BASE_URL = "http://47.118.22.220:8091/api/"
    private val JSON = "application/json; charset=utf-8".toMediaType()

    // 登录状态管理
    private val PREFS_NAME = "login_prefs"
    private val KEY_USERNAME = "username"
    private val KEY_LOGIN_TIME = "login_time"

    // 密码可见性状态
    private var isPasswordVisible = false
    private var isPasswordConfirmVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register_password)

        // 获取传递的用户信息
        userEmail = intent.getStringExtra("email") ?: ""
        verificationCode = intent.getStringExtra("code") ?: ""

        // 绑定控件
        etPassword = findViewById(R.id.cet_register_password_1)
        etConfirmPassword = findViewById(R.id.cet_register_password_2)
        ctvError = findViewById(R.id.ctv_register_password_error)
        ctvRegister = findViewById(R.id.ctv_register_password_register)
        ivPasswordToggle = findViewById(R.id.iv_register_password_show_1)
        ivPasswordConfirmToggle = findViewById(R.id.iv_register_password_show_2)

        // 密码显示/隐藏切换
        ivPasswordToggle.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            togglePasswordVisibility(etPassword, ivPasswordToggle, isPasswordVisible)
        }

        ivPasswordConfirmToggle.setOnClickListener {
            isPasswordConfirmVisible = !isPasswordConfirmVisible
            togglePasswordVisibility(etConfirmPassword, ivPasswordConfirmToggle, isPasswordConfirmVisible)
        }

        // 密码输入监听
        etPassword.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                checkPasswordCompletion()
            }
        })

        etConfirmPassword.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                checkPasswordCompletion()
            }
        })

        // 注册按钮点击事件
        ctvRegister.setOnClickListener { registerUser() }
    }

    // 切换密码可见性
    private fun togglePasswordVisibility(editText: EditText, imageView: ImageView, isVisible: Boolean) {
        if (isVisible) {
            editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            // 使用现有的密码图标
            imageView.setImageResource(R.drawable.icon_register_password_1)
        } else {
            editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            // 使用现有的密码图标
            imageView.setImageResource(R.drawable.icon_register_password_1)
        }
        // 光标移动到末尾
        editText.setSelection(editText.text.length)
    }

    // 检查密码输入是否完成
    private fun checkPasswordCompletion() {
        val password = etPassword.text.toString().trim()
        val confirmPassword = etConfirmPassword.text.toString().trim()
        
        if (password.isNotEmpty() && confirmPassword.isNotEmpty()) {
            // 启用确认按钮
            ctvRegister.isEnabled = true
            ctvRegister.setTextColor(resources.getColor(R.color.white))
            ctvRegister.setBackgroundResource(R.drawable.xml_login_button_back)
        } else {
            // 禁用确认按钮
            ctvRegister.isEnabled = false
            ctvRegister.setTextColor(resources.getColor(R.color.gray_button_back))
            ctvRegister.setBackgroundResource(R.drawable.xml_login_button_gray_back)
        }
    }

    // 注册用户
    private fun registerUser() {
        val password = etPassword.text.toString().trim()
        val confirmPassword = etConfirmPassword.text.toString().trim()

        if (password.isEmpty() || confirmPassword.isEmpty()) {
            ctvError.text = "请填写完整信息"
            ctvError.visibility = android.view.View.VISIBLE
            return
        }

        if (password != confirmPassword) {
            ctvError.text = "两次输入的密码不一致"
            ctvError.visibility = android.view.View.VISIBLE
            return
        }

        if (password.length < 6) {
            ctvError.text = "密码长度不能少于6位"
            ctvError.visibility = android.view.View.VISIBLE
            return
        }

        ctvError.visibility = android.view.View.GONE

        Thread {
            try {
                val client = OkHttpClient()
                val json = JSONObject().apply {
                    put("email", userEmail)
                    put("password", password)
                    put("code", verificationCode)
                    put("username", userEmail) // 使用邮箱作为用户名
                }
                val body = json.toString().toRequestBody(JSON)

                val request = Request.Builder()
                    .url("${BASE_URL}register/code")
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful && response.body != null) {
                    val result = response.body!!.string()
                    val resJson = JSONObject(result)
                    val msg = resJson.getString("msg")

                    Handler(Looper.getMainLooper()).post {
                        if (msg.contains("成功")) {
                            Toast.makeText(this@RegisterPasswordActivity, "注册成功，正在登录...", Toast.LENGTH_SHORT).show()
                            // 自动登录
                            autoLogin(userEmail, password)
                        } else {
                            ctvError.text = msg
                            ctvError.visibility = android.view.View.VISIBLE
                        }
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
                Handler(Looper.getMainLooper()).post {
                    ctvError.text = "注册失败：网络错误"
                    ctvError.visibility = android.view.View.VISIBLE
                }
            } catch (e: org.json.JSONException) {
                e.printStackTrace()
                Handler(Looper.getMainLooper()).post {
                    ctvError.text = "注册失败：数据解析错误"
                    ctvError.visibility = android.view.View.VISIBLE
                }
            }
        }.start()
    }

    // 自动登录
    private fun autoLogin(username: String, password: String) {
        Thread {
            try {
                val client = OkHttpClient()
                val json = JSONObject().apply {
                    put("email", username)
                    put("password", password)
                }
                val body = json.toString().toRequestBody(JSON)

                val request = Request.Builder()
                    .url("${BASE_URL}login")
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful && response.body != null) {
                    val result = response.body!!.string()
                    val resJson = JSONObject(result)
                    val msg = resJson.getString("msg")

                    Handler(Looper.getMainLooper()).post {
                        if (msg.contains("成功")) {
                            // 保存登录信息
                            saveLoginInfo(username)
                            // 登录成功，跳转到主界面
                            val intent = Intent(this@RegisterPasswordActivity, HomeActivity::class.java)
                            intent.putExtra("username", username)
                            startActivity(intent)
                            finishAffinity() // 清除所有注册相关的Activity
                        } else {
                            ctvError.text = "自动登录失败：$msg"
                            ctvError.visibility = android.view.View.VISIBLE
                        }
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
                Handler(Looper.getMainLooper()).post {
                    ctvError.text = "自动登录失败：网络错误"
                    ctvError.visibility = android.view.View.VISIBLE
                }
            } catch (e: org.json.JSONException) {
                e.printStackTrace()
                Handler(Looper.getMainLooper()).post {
                    ctvError.text = "自动登录失败：数据解析错误"
                    ctvError.visibility = android.view.View.VISIBLE
                }
            }
        }.start()
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
}
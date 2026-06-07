package com.example.iot

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import java.io.IOException
import com.example.iot.network.ApiClient
import com.example.iot.network.HttpClient

class LoginActivity : AppCompatActivity() {

    // 控件声明
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var ivAgreement: ImageView
    private lateinit var ivPasswordShow: ImageView
    private lateinit var ctvSubmit: TextView
    private lateinit var ctvForget: TextView
    private lateinit var ctvCreate: TextView

    // 状态变量
    private var isAgreementChecked = false
    private var isPasswordVisible = false

    private val BASE_URL = "https://bolank.asia/api/"
    private val JSON = "application/json; charset=utf-8".toMediaType()

    // 登录状态管理
    private val PREFS_NAME = "login_prefs"
    private val KEY_USERNAME = "username"
    private val KEY_LOGIN_TIME = "login_time"

    // 使用ApiClient管理登录信息
    private lateinit var apiClient: ApiClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        apiClient = ApiClient(this)

        // 绑定控件
        etEmail = findViewById(R.id.cet_login_home_email)
        etPassword = findViewById(R.id.cet_login_password_1)
        ivAgreement = findViewById(R.id.iv_login_home_select)
        ivPasswordShow = findViewById(R.id.iv_login_password_show_1)
        ctvSubmit = findViewById(R.id.ctv_login_home_submit)
        ctvForget = findViewById(R.id.ctv_login_home_forget)
        ctvCreate = findViewById(R.id.ctv_login_home_create)

        // 协议勾选点击事件
        ivAgreement.setOnClickListener {
            isAgreementChecked = !isAgreementChecked
            updateAgreementUI()
        }

        // 密码显示切换事件
        ivPasswordShow.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            updatePasswordVisibility()
        }

        // 登录按钮点击事件
        ctvSubmit.setOnClickListener { loginUser() }

        // 找回密码按钮点击事件
        ctvForget.setOnClickListener {
            val intent = Intent(this@LoginActivity, ForgetPasswordActivity::class.java)
            startActivity(intent)
        }

        // 注册按钮点击事件
        ctvCreate.setOnClickListener {
            val intent = Intent(this@LoginActivity, RegisterActivity::class.java)
            startActivity(intent)
        }

        // 初始化UI状态
        updateAgreementUI()
        updatePasswordVisibility()
    }

    // 更新协议勾选UI
    private fun updateAgreementUI() {
        if (isAgreementChecked) {
            ivAgreement.setImageResource(R.drawable.icon_register_home_select)
            // 启用登录按钮
            ctvSubmit.isEnabled = true
            ctvSubmit.setTextColor(resources.getColor(R.color.white))
            ctvSubmit.setBackgroundResource(R.drawable.xml_login_button_back)
        } else {
            ivAgreement.setImageResource(R.drawable.icon_register_home_not)
            // 禁用登录按钮
            ctvSubmit.isEnabled = false
            ctvSubmit.setTextColor(resources.getColor(R.color.gray_button_back))
            ctvSubmit.setBackgroundResource(R.drawable.xml_login_button_gray_back)
        }
    }

    // 更新密码可见性
    private fun updatePasswordVisibility() {
        if (isPasswordVisible) {
            etPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
        } else {
            etPassword.transformationMethod = PasswordTransformationMethod.getInstance()
        }
        // 保持光标在末尾
        etPassword.setSelection(etPassword.text.length)
    }

    // 登录用户
    private fun loginUser() {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this@LoginActivity, "请输入邮箱和密码", Toast.LENGTH_SHORT).show()
            return
        }

        if (!isAgreementChecked) {
            Toast.makeText(this@LoginActivity, "请勾选协议", Toast.LENGTH_SHORT).show()
            return
        }

        Thread {
            try {
                val client = HttpClient.client
                val json = JSONObject().apply {
                    put("email", email)
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

                    val token = when {
                        resJson.has("token") -> resJson.getString("token")
                        resJson.has("data") && resJson.getJSONObject("data").has("token") ->
                            resJson.getJSONObject("data").getString("token")
                        else -> ""
                    }

                    Handler(Looper.getMainLooper()).post {
                        if (resJson.optInt("code") == 200 || msg.contains("成功")) {
                            apiClient.saveUsername(email)
                            if (token.isNotEmpty()) {
                                apiClient.saveToken(token)
                            } else {
                                apiClient.saveToken(email)
                            }

                            Toast.makeText(this@LoginActivity, "登录成功", Toast.LENGTH_SHORT).show()
                            val intent = Intent(this@LoginActivity, MainHomeActivity::class.java)
                            startActivity(intent)
                            finish()
                        } else {
                            Toast.makeText(this@LoginActivity, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(this@LoginActivity, "登录失败：${response.code}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(this@LoginActivity, "登录失败：网络错误", Toast.LENGTH_SHORT).show()
                }
            } catch (e: org.json.JSONException) {
                e.printStackTrace()
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(this@LoginActivity, "登录失败：数据解析错误", Toast.LENGTH_SHORT).show()
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
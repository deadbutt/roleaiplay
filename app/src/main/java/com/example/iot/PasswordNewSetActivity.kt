package com.example.iot

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
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import com.example.iot.network.HttpClient

class PasswordNewSetActivity : AppCompatActivity() {

    // 控件声明
    private lateinit var etPassword: EditText
    private lateinit var etConfirmPassword: EditText
    private lateinit var ctvError: TextView
    private lateinit var ctvSubmit: TextView
    private lateinit var ivPasswordToggle: ImageView
    private lateinit var ivPasswordConfirmToggle: ImageView
    
    // 用户信息
    private var userEmail: String = ""
    private var verificationCode: String = ""

    // 后端接口地址
    private val BASE_URL = "https://47.118.22.220:8443/api/"
    private val JSON = "application/json; charset=utf-8".toMediaType()

    // 密码可见性状态
    private var isPasswordVisible = false
    private var isPasswordConfirmVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_password_new_set)

        // 获取传递的用户信息
        userEmail = intent.getStringExtra("email") ?: ""
        verificationCode = intent.getStringExtra("code") ?: ""

        // 绑定控件
        etPassword = findViewById(R.id.cet_register_password_1)
        etConfirmPassword = findViewById(R.id.cet_register_password_2)
        ctvError = findViewById(R.id.ctv_register_password_error)
        ctvSubmit = findViewById(R.id.ctv_register_password_register)
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

        // 提交按钮点击事件
        ctvSubmit.setOnClickListener { resetPassword() }
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
            ctvSubmit.isEnabled = true
            ctvSubmit.setTextColor(resources.getColor(R.color.white))
            ctvSubmit.setBackgroundResource(R.drawable.xml_login_button_back)
        } else {
            // 禁用确认按钮
            ctvSubmit.isEnabled = false
            ctvSubmit.setTextColor(resources.getColor(R.color.gray_button_back))
            ctvSubmit.setBackgroundResource(R.drawable.xml_login_button_gray_back)
        }
    }

    // 重置密码
    private fun resetPassword() {
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

        ctvError.visibility = android.view.View.INVISIBLE

        Thread {
            try {
                val client = HttpClient.client
                val json = JSONObject().apply {
                    put("email", userEmail)
                    put("code", verificationCode)
                    put("new_password", password)
                }
                val body = json.toString().toRequestBody(JSON)

                val request = Request.Builder()
                    .url("${BASE_URL}reset/password")
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful && response.body != null) {
                    val result = response.body!!.string()
                    val resJson = JSONObject(result)
                    val msg = resJson.getString("msg")

                    Handler(Looper.getMainLooper()).post {
                        if (msg.contains("成功")) {
                            Toast.makeText(this@PasswordNewSetActivity, "密码重置成功", Toast.LENGTH_SHORT).show()
                            // 跳转到登录页面
                            val intent = Intent(this@PasswordNewSetActivity, LoginActivity::class.java)
                            startActivity(intent)
                            finishAffinity() // 清除所有找回密码相关的Activity
                        } else {
                            ctvError.text = msg
                            ctvError.visibility = android.view.View.VISIBLE
                        }
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
                Handler(Looper.getMainLooper()).post {
                    ctvError.text = "重置失败：网络错误"
                    ctvError.visibility = android.view.View.VISIBLE
                }
            } catch (e: org.json.JSONException) {
                e.printStackTrace()
                Handler(Looper.getMainLooper()).post {
                    ctvError.text = "重置失败：数据解析错误"
                    ctvError.visibility = android.view.View.VISIBLE
                }
            }
        }.start()
    }
}
package com.example.iot

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import com.example.iot.network.HttpClient

class ForgetPasswordActivity : AppCompatActivity() {

    // 控件声明
    private lateinit var etEmail: EditText
    private lateinit var ctvError: TextView
    private lateinit var ctvSubmit: TextView

    // 后端接口地址
    private val BASE_URL = "https://bolank.asia/api/"
    private val JSON = "application/json; charset=utf-8".toMediaType()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forget_password)

        // 绑定控件
        etEmail = findViewById(R.id.cet_forget_password_email)
        ctvError = findViewById(R.id.ctv_forget_password_error)
        ctvSubmit = findViewById(R.id.ctv_forget_password_submit)

        // 初始状态禁用提交按钮
        updateSubmitButtonState()

        // 监听邮箱输入变化
        etEmail.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                updateSubmitButtonState()
            }
        })

        // 提交按钮点击事件
        ctvSubmit.setOnClickListener { sendVerificationCode() }
    }

    // 发送验证码
    private fun sendVerificationCode() {
        val email = etEmail.text.toString().trim()

        if (email.isEmpty()) {
            ctvError.text = "请输入邮箱"
            ctvError.visibility = android.view.View.VISIBLE
            return
        }

        if (!isValidEmail(email)) {
            ctvError.text = "请输入有效的邮箱地址"
            ctvError.visibility = android.view.View.VISIBLE
            return
        }

        ctvError.visibility = android.view.View.INVISIBLE

        Thread {
            try {
                val client = HttpClient.client
                val json = JSONObject().apply {
                    put("email", email)
                    put("type", "reset_password")
                }
                val body = json.toString().toRequestBody(JSON)

                val request = Request.Builder()
                    .url("${BASE_URL}verify/send")
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful && response.body != null) {
                    val result = response.body!!.string()
                    val resJson = JSONObject(result)
                    val msg = resJson.getString("msg")

                    Handler(Looper.getMainLooper()).post {
                        if (msg.contains("已发送")) {
                            Toast.makeText(this@ForgetPasswordActivity, "验证码已发送", Toast.LENGTH_SHORT).show()
                            // 跳转到验证码页面
                            navigateToVerificationCode(email)
                        } else {
                            ctvError.text = msg
                            ctvError.visibility = android.view.View.VISIBLE
                        }
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
                Handler(Looper.getMainLooper()).post {
                    ctvError.text = "发送失败：网络错误"
                    ctvError.visibility = android.view.View.VISIBLE
                }
            } catch (e: org.json.JSONException) {
                e.printStackTrace()
                Handler(Looper.getMainLooper()).post {
                    ctvError.text = "发送失败：数据解析错误"
                    ctvError.visibility = android.view.View.VISIBLE
                }
            }
        }.start()
    }

    // 邮箱格式验证
    private fun isValidEmail(email: String): Boolean {
        val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$"
        return email.matches(emailRegex.toRegex())
    }

    // 跳转到验证码验证页面
    private fun navigateToVerificationCode(email: String) {
        val intent = Intent(this, PasswordVerificationActivity::class.java)
        intent.putExtra("email", email)
        startActivity(intent)
    }

    // 更新提交按钮状态
    private fun updateSubmitButtonState() {
        val email = etEmail.text.toString().trim()
        if (email.isNotEmpty() && isValidEmail(email)) {
            ctvSubmit.isEnabled = true
            ctvSubmit.setTextColor(resources.getColor(R.color.white))
            ctvSubmit.setBackgroundResource(R.drawable.xml_login_button_back)
        } else {
            ctvSubmit.isEnabled = false
            ctvSubmit.setTextColor(resources.getColor(R.color.gray_button_back))
            ctvSubmit.setBackgroundResource(R.drawable.xml_login_button_gray_back)
        }
    }
}
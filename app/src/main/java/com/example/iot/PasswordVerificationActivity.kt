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
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class PasswordVerificationActivity : AppCompatActivity() {

    // 控件声明
    private lateinit var etCode1: EditText
    private lateinit var etCode2: EditText
    private lateinit var etCode3: EditText
    private lateinit var etCode4: EditText
    private lateinit var etCode5: EditText
    private lateinit var etCode6: EditText
    private lateinit var ctvEmail: TextView
    private lateinit var ctvTime: TextView
    private lateinit var ctvSubmit: TextView
    
    // 邮箱信息
    private var userEmail: String = ""

    // 后端接口地址
    private val BASE_URL = "http://47.118.22.220:8091/api/"
    private val JSON = "application/json; charset=utf-8".toMediaType()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_password_verification)

        // 获取传递的邮箱信息
        userEmail = intent.getStringExtra("email") ?: ""

        // 绑定控件
        etCode1 = findViewById(R.id.code_1)
        etCode2 = findViewById(R.id.code_2)
        etCode3 = findViewById(R.id.code_3)
        etCode4 = findViewById(R.id.code_4)
        etCode5 = findViewById(R.id.code_5)
        etCode6 = findViewById(R.id.code_6)
        ctvEmail = findViewById(R.id.ctv_register_code_email)
        ctvTime = findViewById(R.id.ctv_register_code_time)
        ctvSubmit = findViewById(R.id.ctv_login_home_register)

        // 设置邮箱显示
        ctvEmail.text = userEmail

        // 提交按钮点击事件
        ctvSubmit.setOnClickListener { verifyCode() }

        // 设置验证码输入框的自动聚焦
        setupCodeInputs()
    }

    // 设置验证码输入框的自动聚焦
    private fun setupCodeInputs() {
        etCode1.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s?.length == 1) etCode2.requestFocus()
            }
            override fun afterTextChanged(s: android.text.Editable?) {
                checkCodeCompletion()
            }
        })
        etCode2.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s?.length == 1) etCode3.requestFocus()
            }
            override fun afterTextChanged(s: android.text.Editable?) {
                checkCodeCompletion()
            }
        })
        etCode3.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s?.length == 1) etCode4.requestFocus()
            }
            override fun afterTextChanged(s: android.text.Editable?) {
                checkCodeCompletion()
            }
        })
        etCode4.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s?.length == 1) etCode5.requestFocus()
            }
            override fun afterTextChanged(s: android.text.Editable?) {
                checkCodeCompletion()
            }
        })
        etCode5.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s?.length == 1) etCode6.requestFocus()
            }
            override fun afterTextChanged(s: android.text.Editable?) {
                checkCodeCompletion()
            }
        })
        etCode6.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                checkCodeCompletion()
            }
        })
    }

    // 检查验证码是否输入完成
    private fun checkCodeCompletion() {
        val code = getCode()
        if (code.length == 6) {
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

    // 获取输入的验证码
    private fun getCode(): String {
        return etCode1.text.toString() +
                etCode2.text.toString() +
                etCode3.text.toString() +
                etCode4.text.toString() +
                etCode5.text.toString() +
                etCode6.text.toString()
    }

    // 验证验证码
    private fun verifyCode() {
        val code = getCode()

        if (code.isEmpty()) {
            Toast.makeText(this@PasswordVerificationActivity, "请输入验证码", Toast.LENGTH_SHORT).show()
            return
        }

        if (code.length != 6) {
            Toast.makeText(this@PasswordVerificationActivity, "请输入6位验证码", Toast.LENGTH_SHORT).show()
            return
        }

        Thread {
            try {
                val client = OkHttpClient()
                val json = JSONObject().apply {
                    put("email", userEmail)
                    put("code", code)
                    put("type", "reset_password")
                }
                val body = json.toString().toRequestBody(JSON)

                val request = Request.Builder()
                    .url("${BASE_URL}verify/check")
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful && response.body != null) {
                    val result = response.body!!.string()
                    val resJson = JSONObject(result)
                    val msg = resJson.getString("msg")

                    Handler(Looper.getMainLooper()).post {
                        if (msg.contains("验证成功") || msg.contains("正确")) {
                            Toast.makeText(this@PasswordVerificationActivity, "验证码验证成功", Toast.LENGTH_SHORT).show()
                            // 跳转到设置新密码页面
                            navigateToPasswordReset(code)
                        } else {
                            Toast.makeText(this@PasswordVerificationActivity, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(this@PasswordVerificationActivity, "验证失败：网络错误", Toast.LENGTH_SHORT).show()
                }
            } catch (e: org.json.JSONException) {
                e.printStackTrace()
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(this@PasswordVerificationActivity, "验证失败：数据解析错误", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    // 跳转到设置新密码页面
    private fun navigateToPasswordReset(code: String) {
        val intent = Intent(this, PasswordNewSetActivity::class.java)
        intent.putExtra("email", userEmail)
        intent.putExtra("code", code)
        startActivity(intent)
    }
}
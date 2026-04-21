package com.example.iot

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class RegisterActivity : AppCompatActivity() {

    // 控件声明
    private lateinit var etEmail: EditText
    private lateinit var ivAgreement: ImageView
    private lateinit var ctvRegister: TextView
    private lateinit var ctvLogin: TextView
    private var isAgreementChecked = false

    // 后端接口地址
    private val BASE_URL = "http://47.118.22.220:8091/api/"
    private val JSON = "application/json; charset=utf-8".toMediaType()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        // 绑定控件
        etEmail = findViewById(R.id.cet_register_home_email)
        ivAgreement = findViewById(R.id.iv_register_home_select)
        ctvRegister = findViewById(R.id.ctv_login_home_register)
        ctvLogin = findViewById(R.id.ctv_register_home_login)

        // 协议勾选点击事件
        ivAgreement.setOnClickListener {
            isAgreementChecked = !isAgreementChecked
            // 切换图片
            if (isAgreementChecked) {
                ivAgreement.setImageResource(R.drawable.icon_register_home_select)
                // 启用注册按钮
                ctvRegister.isEnabled = true
                ctvRegister.setTextColor(resources.getColor(R.color.white))
                ctvRegister.setBackgroundResource(R.drawable.xml_login_button_back)
            } else {
                ivAgreement.setImageResource(R.drawable.icon_register_home_not)
                // 禁用注册按钮
                ctvRegister.isEnabled = false
                ctvRegister.setTextColor(resources.getColor(R.color.gray_button_back))
                ctvRegister.setBackgroundResource(R.drawable.xml_login_button_gray_back)
            }
        }

        // 注册按钮点击事件
        ctvRegister.setOnClickListener { sendVerificationCode() }

        // 登录按钮点击事件
        ctvLogin.setOnClickListener {
            val intent = Intent(this@RegisterActivity, LoginActivity::class.java)
            startActivity(intent)
        }
    }

    private var isSendingCode = false

    // 发送验证码
    private fun sendVerificationCode() {
        if (isSendingCode) {
            return
        }

        val email = etEmail.text.toString().trim()

        if (email.isEmpty()) {
            Toast.makeText(this@RegisterActivity, "请输入邮箱", Toast.LENGTH_SHORT).show()
            return
        }

        if (!isValidEmail(email)) {
            Toast.makeText(this@RegisterActivity, "请输入有效的邮箱地址", Toast.LENGTH_SHORT).show()
            return
        }

        if (!isAgreementChecked) {
            Toast.makeText(this@RegisterActivity, "请勾选协议", Toast.LENGTH_SHORT).show()
            return
        }

        isSendingCode = true
        ctvRegister.text = "发送中..."
        ctvRegister.isEnabled = false

        Thread {
            try {
                val client = OkHttpClient()
                val json = JSONObject().apply {
                    put("email", email)
                    put("type", "register")
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
                            Toast.makeText(this@RegisterActivity, "验证码已发送", Toast.LENGTH_SHORT).show()
                            // 跳转到验证码页面
                            navigateToCodeVerification(email)
                        } else {
                            Toast.makeText(this@RegisterActivity, msg, Toast.LENGTH_SHORT).show()
                            ctvRegister.text = "下一步"
                            ctvRegister.isEnabled = true
                        }
                        isSendingCode = false
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(this@RegisterActivity, "发送失败：网络错误", Toast.LENGTH_SHORT).show()
                    ctvRegister.text = "下一步"
                    ctvRegister.isEnabled = true
                    isSendingCode = false
                }
            } catch (e: org.json.JSONException) {
                e.printStackTrace()
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(this@RegisterActivity, "发送失败：数据解析错误", Toast.LENGTH_SHORT).show()
                    ctvRegister.text = "下一步"
                    ctvRegister.isEnabled = true
                    isSendingCode = false
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
    private fun navigateToCodeVerification(email: String) {
        val intent = Intent(this, RegisterCodeActivity::class.java)
        intent.putExtra("email", email)
        startActivity(intent)
    }


}
package com.example.iot

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.iot.network.ApiClient

class MainHomeActivity : AppCompatActivity() {

    private lateinit var navDevice: LinearLayout
    private lateinit var navAi: LinearLayout
    private lateinit var ivNavDevice: ImageView
    private lateinit var ivNavAi: ImageView
    private lateinit var tvNavDevice: TextView
    private lateinit var tvNavAi: TextView

    private lateinit var apiClient: ApiClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_home)

        apiClient = ApiClient(this)

        initViews()
        initListeners()

        // 默认显示设备首页
        showFragment(DeviceHomeFragment())
        updateNavState(true)
    }

    private fun initViews() {
        navDevice = findViewById(R.id.nav_device)
        navAi = findViewById(R.id.nav_ai)
        ivNavDevice = findViewById(R.id.iv_nav_device)
        ivNavAi = findViewById(R.id.iv_nav_ai)
        tvNavDevice = findViewById(R.id.tv_nav_device)
        tvNavAi = findViewById(R.id.tv_nav_ai)
    }

    private fun initListeners() {
        navDevice.setOnClickListener {
            showFragment(DeviceHomeFragment())
            updateNavState(true)
        }

        navAi.setOnClickListener {
            // 跳转到 ChatActivity
            val intent = Intent(this, ChatActivity::class.java)
            startActivity(intent)
        }
    }

    private fun showFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    private fun updateNavState(isDeviceSelected: Boolean) {
        if (isDeviceSelected) {
            ivNavDevice.alpha = 1.0f
            tvNavDevice.alpha = 1.0f
            ivNavAi.alpha = 0.5f
            tvNavAi.alpha = 0.5f
        } else {
            ivNavDevice.alpha = 0.5f
            tvNavDevice.alpha = 0.5f
            ivNavAi.alpha = 1.0f
            tvNavAi.alpha = 1.0f
        }
    }
}

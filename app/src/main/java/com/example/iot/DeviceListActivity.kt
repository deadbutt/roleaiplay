package com.example.iot

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class DeviceListActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 已废弃，使用 DeviceListFragment
        finish()
    }
}
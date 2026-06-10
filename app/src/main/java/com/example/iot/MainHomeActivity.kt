package com.example.iot

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2

class MainHomeActivity : AppCompatActivity() {

    private lateinit var vpMain: ViewPager2
    private lateinit var tabDevice: LinearLayout
    private lateinit var tabAi: LinearLayout
    private lateinit var tabSettings: LinearLayout

    private val tabs by lazy { listOf(tabDevice, tabAi, tabSettings) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_home)

        initViews()
        setupViewPager()
        setupTabs()

        // 处理从其他页面返回时指定 tab
        val tabIndex = intent.getIntExtra("tab_index", -1)
        if (tabIndex >= 0) {
            vpMain.currentItem = tabIndex
            updateTabStyles(tabIndex)
        }
    }

    override fun onResume() {
        super.onResume()
        // 从 ChatActivity 返回时，切回设备页
        val tabIndex = intent.getIntExtra("tab_index", -1)
        if (tabIndex >= 0) {
            switchToTab(tabIndex)
            intent.removeExtra("tab_index")
        } else {
            // 默认显示设备页
            updateTabStyles(0)
        }
    }

    private fun initViews() {
        vpMain = findViewById(R.id.vp_main)
        tabDevice = findViewById(R.id.tab_device)
        tabAi = findViewById(R.id.tab_ai)
        tabSettings = findViewById(R.id.tab_settings)
    }

    private fun setupViewPager() {
        vpMain.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = 2 // 只有设备和设置两个 tab

            override fun createFragment(position: Int): Fragment {
                return when (position) {
                    0 -> DeviceListFragment()
                    1 -> SettingsFragment()
                    else -> DeviceListFragment()
                }
            }
        }

        vpMain.isUserInputEnabled = false
    }

    private fun setupTabs() {
        tabDevice.setOnClickListener { 
            vpMain.currentItem = 0
            updateTabStyles(0)
        }
        tabAi.setOnClickListener { 
            // AI助手直接打开 ChatActivity，不切换 ViewPager
            updateTabStyles(1) // 显示选中样式
            val intent = Intent(this, ChatActivity::class.java).apply {
                putExtra("characterName", "AI助手")
            }
            startActivity(intent)
        }
        tabSettings.setOnClickListener { 
            vpMain.currentItem = 1
            updateTabStyles(2)
        }
    }

    fun switchToTab(index: Int) {
        if (index == 1) {
            // AI tab 不切换 ViewPager
            updateTabStyles(1)
        } else {
            vpMain.currentItem = if (index == 2) 1 else 0
            updateTabStyles(index)
        }
    }

    private fun updateTabStyles(selectedIndex: Int) {
        tabs.forEachIndexed { index, tab ->
            val isSelected = index == selectedIndex
            val bgRes = if (isSelected) R.drawable.xml_home_page_black else 0
            val textColor = if (isSelected) android.R.color.white else R.color.text_black_99
            val tintColor = if (isSelected) android.R.color.white else R.color.text_black_99

            tab.background = if (isSelected) ContextCompat.getDrawable(this, bgRes) else null

            val imageView = tab.getChildAt(0) as ImageView
            val textView = tab.getChildAt(1) as TextView

            imageView.setColorFilter(ContextCompat.getColor(this, tintColor))
            textView.setTextColor(ContextCompat.getColor(this, textColor))
        }
    }
}
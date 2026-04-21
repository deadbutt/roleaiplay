package com.example.iot

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.iot.network.ApiClient

class CharacterDetailActivity : AppCompatActivity() {

    private lateinit var apiClient: ApiClient
    private var characterId: String = ""

    private lateinit var ivAvatar: ImageView
    private lateinit var tvName: TextView
    private lateinit var tvDescription: TextView
    private lateinit var tvEnvironment: TextView
    private lateinit var btnStartConversation: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_character_detail)

        apiClient = ApiClient(this)

        try {
            // 绑定控件
            ivAvatar = findViewById(R.id.iv_character_avatar)
            tvName = findViewById(R.id.tv_character_name)
            tvDescription = findViewById(R.id.tv_character_description)
            tvEnvironment = findViewById(R.id.tv_environment_description)
            btnStartConversation = findViewById(R.id.btn_start_conversation)

            // 获取传递的角色ID
            characterId = intent.getStringExtra("characterId") ?: ""
            
            if (characterId.isEmpty()) {
                Toast.makeText(this, "角色ID不能为空", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

            // 加载角色详情
            loadCharacterDetail()

            // 开始对话按钮
            btnStartConversation.setOnClickListener {
                startConversation()
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "界面初始化失败", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    // 加载角色详情
    private fun loadCharacterDetail() {
        apiClient.getCharacterDetail(characterId) { success, msg, character ->
            runOnUiThread {
                if (success && character != null) {
                    tvName.text = character.name
                    tvDescription.text = character.description
                    tvEnvironment.text = character.scenario
                    
                    // 如果有头像，设置图片（暂时使用默认头像）
                    if (character.avatarUrl.isNotEmpty()) {
                        // 这里可以使用Glide等库加载网络图片
                    }
                } else {
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 开始对话
    private fun startConversation() {
        // 跳转到ChatActivity，传递角色ID和名称
        val intent = Intent(this@CharacterDetailActivity, ChatActivity::class.java)
        intent.putExtra("characterId", characterId)
        intent.putExtra("characterName", tvName.text.toString())
        intent.putExtra("prompt", tvDescription.text.toString())
        startActivity(intent)
        finish()
    }
}
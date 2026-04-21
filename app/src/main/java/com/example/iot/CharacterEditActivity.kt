package com.example.iot

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.iot.network.ApiClient

class CharacterEditActivity : AppCompatActivity() {

    private lateinit var apiClient: ApiClient
    private var characterId: String = ""

    private lateinit var ivEditAvatar: ImageView
    private lateinit var etName: EditText
    private lateinit var etDescription: EditText
    private lateinit var etEnvironment: EditText
    private lateinit var btnSave: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_character_edit)

        apiClient = ApiClient(this)

        try {
            // 绑定控件
            ivEditAvatar = findViewById(R.id.iv_edit_avatar)
            etName = findViewById(R.id.et_character_name)
            etDescription = findViewById(R.id.et_character_description)
            etEnvironment = findViewById(R.id.et_environment_description)
            btnSave = findViewById(R.id.btn_save_changes)

            // 获取传递的角色ID
            characterId = intent.getStringExtra("characterId") ?: ""

            // 返回按钮
            findViewById<ImageButton>(R.id.btn_back)?.setOnClickListener {
                finish()
            }

            // 如果是编辑模式，加载角色数据
            if (characterId.isNotEmpty()) {
                loadCharacterData()
            }

            // 保存按钮
            btnSave.setOnClickListener {
                saveCharacter()
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "界面初始化失败", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    // 加载角色数据
    private fun loadCharacterData() {
        apiClient.getCharacterDetail(characterId) { success, msg, character ->
            runOnUiThread {
                if (success && character != null) {
                    etName.setText(character.name)
                    etDescription.setText(character.description)
                    etEnvironment.setText(character.scenario)
                } else {
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 保存角色
    private fun saveCharacter() {
        val name = etName.text.toString().trim()
        val description = etDescription.text.toString().trim()
        val scenario = etEnvironment.text.toString().trim()

        if (name.isEmpty()) {
            Toast.makeText(this, "请输入角色名称", Toast.LENGTH_SHORT).show()
            return
        }

        if (description.isEmpty()) {
            Toast.makeText(this, "请输入角色描述", Toast.LENGTH_SHORT).show()
            return
        }

        btnSave.isEnabled = false
        btnSave.text = "保存中..."

        if (characterId.isNotEmpty()) {
            // 更新模式
            apiClient.updateCharacter(characterId, name, description, scenario, "") { success, msg ->
                runOnUiThread {
                    btnSave.isEnabled = true
                    btnSave.text = "保存修改"
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                    if (success) {
                        finish()
                    }
                }
            }
        } else {
            // 创建模式
            apiClient.createCharacter(name, description, scenario, "") { success, msg, character ->
                runOnUiThread {
                    btnSave.isEnabled = true
                    btnSave.text = "保存修改"
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                    if (success) {
                        finish()
                    }
                }
            }
        }
    }
}
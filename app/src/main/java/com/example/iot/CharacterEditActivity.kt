package com.example.iot

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.iot.network.ApiClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream

class CharacterEditActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_CODE_PICK_IMAGE = 1001
    }

    private lateinit var apiClient: ApiClient
    private var characterId: String = ""
    private var avatarUri: Uri? = null
    private var avatarUrl: String = ""

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

            // 点击头像选择图片
            ivEditAvatar.setOnClickListener {
                pickImageFromGallery()
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

    // 从相册选择图片
    private fun pickImageFromGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        startActivityForResult(intent, REQUEST_CODE_PICK_IMAGE)
    }

    // 处理选择图片结果
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_PICK_IMAGE && resultCode == Activity.RESULT_OK) {
            avatarUri = data?.data
            if (avatarUri != null) {
                // 显示选择的图片
                val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, avatarUri)
                ivEditAvatar.setImageBitmap(bitmap)
            }
        }
    }

    // 上传头像到服务器
    private fun uploadAvatar(callback: (String) -> Unit) {
        if (avatarUri == null) {
            callback(avatarUrl)
            return
        }

        try {
            // 将Uri转为File
            val inputStream = contentResolver.openInputStream(avatarUri!!)
            val tempFile = File.createTempFile("avatar_", ".jpg", cacheDir)
            val outputStream = FileOutputStream(tempFile)
            inputStream?.copyTo(outputStream)
            inputStream?.close()
            outputStream.close()

            // 创建Multipart请求
            val requestFile = tempFile.asRequestBody("image/*".toMediaType())
            val body = MultipartBody.Part.createFormData("file", tempFile.name, requestFile)

            apiClient.uploadAvatar(body) { success, msg, url ->
                if (success && url != null) {
                    avatarUrl = url
                    callback(url)
                } else {
                    Toast.makeText(this, "头像上传失败: $msg", Toast.LENGTH_SHORT).show()
                    callback("")
                }
                // 删除临时文件
                tempFile.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "头像上传失败: ${e.message}", Toast.LENGTH_SHORT).show()
            callback("")
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
                    avatarUrl = character.avatarUrl

                    // 如果有头像URL，加载显示
                    if (character.avatarUrl.isNotEmpty()) {
                        apiClient.loadImage(character.avatarUrl) { bitmap ->
                            runOnUiThread {
                                if (bitmap != null) {
                                    ivEditAvatar.setImageBitmap(bitmap)
                                }
                            }
                        }
                    }
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

        // 如果有新选择的头像，先上传头像
        if (avatarUri != null) {
            uploadAvatar { uploadedUrl ->
                saveCharacterToServer(name, description, scenario, uploadedUrl)
            }
        } else {
            saveCharacterToServer(name, description, scenario, avatarUrl)
        }
    }

    // 保存角色到服务器
    private fun saveCharacterToServer(name: String, description: String, scenario: String, avatarUrl: String) {
        if (characterId.isNotEmpty()) {
            // 更新模式
            apiClient.updateCharacter(characterId, name, description, scenario, avatarUrl) { success, msg ->
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
            apiClient.createCharacter(name, description, scenario, avatarUrl) { success, msg, character ->
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
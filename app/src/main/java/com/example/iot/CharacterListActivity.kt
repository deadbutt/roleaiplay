package com.example.iot

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.iot.model.Character
import com.example.iot.network.ApiClient

class CharacterListActivity : AppCompatActivity() {

    private lateinit var apiClient: ApiClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_character_list)

        apiClient = ApiClient(this)

        try {
            // 顶部头像点击事件 - 创建新角色
            findViewById<ImageView>(R.id.iv_user_profile)?.setOnClickListener {
                openEditCharacter(null)
            }

            // 编辑按钮点击事件
            findViewById<ImageButton>(R.id.btn_edit)?.setOnClickListener {
                openEditCharacter(null)
            }

            // 第一个卡片固定为"新建角色"
            setupNewCharacterCard()

            // 加载角色列表
            loadCharacterList()

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "界面初始化失败", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    // 设置新建角色卡片（第一个卡片）
    private fun setupNewCharacterCard() {
        val cardNew = findViewById<LinearLayout>(R.id.card_evelyn)
        val tvName = cardNew?.findViewById<TextView>(R.id.tv_card_name)
        val ivAvatar = cardNew?.findViewById<ImageView>(R.id.iv_card_avatar)

        tvName?.text = "新建角色"
        ivAvatar?.setImageResource(R.drawable.icon_add)

        cardNew?.setOnClickListener {
            openEditCharacter(null)
        }
    }

    // 加载角色列表
    private fun loadCharacterList() {
        apiClient.getCharacterList { success, msg, characterList ->
            runOnUiThread {
                if (success && characterList != null) {
                    // 卡片容器列表（排除第一个，因为第一个是新建角色）
                    val cards = listOf(
                        findViewById<LinearLayout>(R.id.card_marcus),
                        findViewById<LinearLayout>(R.id.card_sophia),
                        findViewById<LinearLayout>(R.id.card_julian)
                    )

                    // 填充角色卡片
                    cards.forEachIndexed { index, card ->
                        if (index < characterList.size) {
                            val character = characterList[index]
                            card?.visibility = View.VISIBLE

                            val tvName = card?.findViewById<TextView>(R.id.tv_card_name)
                            val ivAvatar = card?.findViewById<ImageView>(R.id.iv_card_avatar)

                            tvName?.text = character.name

                            // 加载头像
                            loadAvatar(character.avatarUrl, ivAvatar)

                            card?.setOnClickListener {
                                openDetailCharacter(character)
                            }
                        } else {
                            // 隐藏多余的卡片
                            card?.visibility = View.GONE
                        }
                    }
                } else {
                    // 如果没有角色数据，隐藏多余的卡片
                    findViewById<LinearLayout>(R.id.card_marcus)?.visibility = View.GONE
                    findViewById<LinearLayout>(R.id.card_sophia)?.visibility = View.GONE
                    findViewById<LinearLayout>(R.id.card_julian)?.visibility = View.GONE
                }
            }
        }
    }

    // 加载头像
    private fun loadAvatar(avatarUrl: String?, imageView: ImageView?) {
        if (avatarUrl.isNullOrEmpty()) {
            imageView?.setImageResource(R.drawable.ic_avatar_placeholder)
            return
        }

        apiClient.loadImage(avatarUrl) { bitmap ->
            runOnUiThread {
                if (bitmap != null) {
                    imageView?.setImageBitmap(bitmap)
                } else {
                    imageView?.setImageResource(R.drawable.ic_avatar_placeholder)
                }
            }
        }
    }

    // 打开角色编辑界面
    private fun openEditCharacter(character: Character?) {
        val intent = Intent(this@CharacterListActivity, CharacterEditActivity::class.java)
        if (character != null) {
            intent.putExtra("characterId", character.id)
        }
        startActivity(intent)
    }

    // 打开角色详情界面
    private fun openDetailCharacter(character: Character) {
        val intent = Intent(this@CharacterListActivity, CharacterDetailActivity::class.java)
        intent.putExtra("characterId", character.id)
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        loadCharacterList()
    }
}
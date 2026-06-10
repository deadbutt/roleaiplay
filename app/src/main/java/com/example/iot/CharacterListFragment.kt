package com.example.iot

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.iot.model.Character
import com.example.iot.network.ApiClient

class CharacterListFragment : Fragment() {

    private lateinit var apiClient: ApiClient

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.activity_character_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        apiClient = ApiClient(requireContext())

        try {
            view.findViewById<ImageView>(R.id.iv_user_profile)?.setOnClickListener {
                openEditCharacter(null)
            }

            view.findViewById<ImageButton>(R.id.btn_edit)?.setOnClickListener {
                openEditCharacter(null)
            }

            setupNewCharacterCard(view)
            loadCharacterList(view)

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "界面初始化失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupNewCharacterCard(view: View) {
        val cardNew = view.findViewById<LinearLayout>(R.id.card_evelyn)
        val tvName = cardNew?.findViewById<TextView>(R.id.tv_card_name)
        val ivAvatar = cardNew?.findViewById<ImageView>(R.id.iv_card_avatar)

        tvName?.text = "新建角色"
        ivAvatar?.setImageResource(R.drawable.icon_ai_star)

        cardNew?.setOnClickListener {
            openEditCharacter(null)
        }
    }

    private fun loadCharacterList(view: View) {
        apiClient.getCharacterList { success, msg, characterList ->
            requireActivity().runOnUiThread {
                if (success && characterList != null) {
                    val cards = listOf(
                        view.findViewById<LinearLayout>(R.id.card_marcus),
                        view.findViewById<LinearLayout>(R.id.card_sophia),
                        view.findViewById<LinearLayout>(R.id.card_julian)
                    )

                    cards.forEachIndexed { index, card ->
                        if (index < characterList.size) {
                            val character = characterList[index]
                            card?.visibility = View.VISIBLE

                            val tvName = card?.findViewById<TextView>(R.id.tv_card_name)
                            val ivAvatar = card?.findViewById<ImageView>(R.id.iv_card_avatar)

                            tvName?.text = character.name
                            ivAvatar?.setImageResource(R.drawable.icon_default_avatar)

                            card?.setOnClickListener {
                                openChat(character)
                            }
                        } else {
                            card?.visibility = View.GONE
                        }
                    }
                } else {
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun openEditCharacter(character: Character?) {
        val intent = Intent(requireContext(), CharacterEditActivity::class.java)
        if (character != null) {
            intent.putExtra("character_id", character.id)
            intent.putExtra("character_name", character.name)
            intent.putExtra("character_description", character.description)
        }
        startActivity(intent)
    }

    private fun openChat(character: Character) {
        val intent = Intent(requireContext(), ChatActivity::class.java)
        intent.putExtra("character_id", character.id)
        intent.putExtra("character_name", character.name)
        startActivity(intent)
    }
}
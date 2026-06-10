package com.example.iot

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

class AiChatFragment : Fragment() {

    private var hasNavigated = false
    private var isReturningFromChat = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_ai_chat, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // 避免重复跳转
        if (!hasNavigated) {
            hasNavigated = true
            navigateToChat()
        }
    }

    override fun onResume() {
        super.onResume()
        // 从 ChatActivity 返回时，自动切回设备页
        if (isReturningFromChat) {
            isReturningFromChat = false
            hasNavigated = false // 重置，允许下次点击再次跳转
            (activity as? MainHomeActivity)?.let { mainActivity ->
                mainActivity.switchToTab(0)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // 标记即将跳转到 ChatActivity
        isReturningFromChat = true
    }

    private fun navigateToChat() {
        val intent = Intent(requireContext(), ChatActivity::class.java).apply {
            putExtra("characterName", "AI助手")
        }
        startActivity(intent)
    }
}
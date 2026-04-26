package com.example.iot.adapter

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.example.iot.ChatActivity
import com.example.iot.R
import com.example.iot.model.Session

sealed class SessionItem {
    data class Header(val title: String) : SessionItem()
    data class PinnedSessionItem(val session: Session) : SessionItem()
    data class NormalSessionItem(val session: Session) : SessionItem()
}

class SessionAdapter(
    private val context: Context,
    private val onSessionDelete: (String) -> Unit,
    private val onSessionPin: (String, Boolean) -> Unit,
    private val onRefresh: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_PINNED = 1
        const val TYPE_NORMAL = 2
    }

    private var items: List<SessionItem> = emptyList()

    fun setData(newItems: List<SessionItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is SessionItem.Header -> TYPE_HEADER
            is SessionItem.PinnedSessionItem -> TYPE_PINNED
            is SessionItem.NormalSessionItem -> TYPE_NORMAL
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_HEADER -> {
                val view = LayoutInflater.from(context).inflate(R.layout.item_section_header, parent, false)
                HeaderViewHolder(view)
            }
            TYPE_PINNED -> {
                val view = LayoutInflater.from(context).inflate(R.layout.item_pinned_session, parent, false)
                PinnedSessionViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(context).inflate(R.layout.item_session, parent, false)
                NormalSessionViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is SessionItem.Header -> {
                (holder as HeaderViewHolder).bind(item)
            }
            is SessionItem.PinnedSessionItem -> {
                (holder as PinnedSessionViewHolder).bind(item.session, position)
            }
            is SessionItem.NormalSessionItem -> {
                (holder as NormalSessionViewHolder).bind(item.session, position)
            }
        }
    }

    override fun getItemCount(): Int = items.size

    inner class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvHeader: TextView = view.findViewById(R.id.tv_section_header)

        fun bind(item: SessionItem.Header) {
            tvHeader.text = item.title
        }
    }

    inner class PinnedSessionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val llItem: LinearLayout = view.findViewById(R.id.ll_pinned_item)
        private val tvTitle: TextView = view.findViewById(R.id.tv_pinned_title)
        private val tvTime: TextView = view.findViewById(R.id.tv_pinned_time)
        private val tvPreview: TextView = view.findViewById(R.id.tv_pinned_preview)

        fun bind(session: Session, position: Int) {
            tvTitle.text = session.title
            tvTime.text = formatTime(session.lastMessageTime)
            tvPreview.text = session.preview

            llItem.setOnClickListener {
                openSession(session.sessionId)
            }
        }
    }

    inner class NormalSessionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val llItem: LinearLayout = view.findViewById(R.id.ll_session_item)
        private val tvTitle: TextView = view.findViewById(R.id.tv_session_title)
        private val tvInfo: TextView = view.findViewById(R.id.tv_session_info)
        private val ivMenu: ImageView = view.findViewById(R.id.iv_session_menu)

        fun bind(session: Session, position: Int) {
            tvTitle.text = session.title
            tvInfo.text = "${session.characterName} · ${formatTime(session.lastMessageTime)}"
            ivMenu.visibility = View.VISIBLE

            llItem.setOnClickListener {
                openSession(session.sessionId)
            }

            ivMenu.setOnClickListener {
                showSessionMenu(session.sessionId, session.title)
            }
        }
    }

    private fun openSession(sessionId: String) {
        val intent = Intent(context, ChatActivity::class.java)
        intent.putExtra("sessionId", sessionId)
        context.startActivity(intent)
        if (context is android.app.Activity) {
            (context as android.app.Activity).finish()
        }
    }

    private fun showSessionMenu(sessionId: String, title: String) {
        val options = arrayOf("置顶会话", "删除会话")
        AlertDialog.Builder(context)
            .setTitle(title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> onSessionPin(sessionId, true)
                    1 -> onSessionDelete(sessionId)
                }
            }
            .show()
    }

    private fun formatTime(timeStr: String): String {
        if (timeStr.isEmpty()) return ""
        return try {
            if (timeStr.length > 16) {
                timeStr.substring(11, 16)
            } else {
                timeStr
            }
        } catch (e: Exception) {
            timeStr
        }
    }
}

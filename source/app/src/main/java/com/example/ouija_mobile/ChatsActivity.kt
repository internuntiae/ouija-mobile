package com.example.ouija_mobile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView

class ChatsActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var apiClient: ApiClient
    private lateinit var adapter: ChatAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chats)

        sessionManager = SessionManager(this)
        apiClient = ApiClient(sessionManager)

        val recycler = findViewById<RecyclerView>(R.id.recyclerChats)
        val emptyView = findViewById<TextView>(R.id.tvEmpty)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)

        adapter = ChatAdapter { chat ->
            startActivity(Intent(this, ChatActivity::class.java).apply {
                putExtra("CHAT_ID", chat.id)
                putExtra("CHAT_NAME", getChatDisplayName(chat))
            })
        }

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        bottomNav.selectedItemId = R.id.nav_chats
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_chats -> true
                R.id.nav_profile -> {
                    startActivity(Intent(this, ProfileActivity::class.java))
                    false
                }
                else -> false
            }
        }

        loadChats(emptyView)
    }

    override fun onResume() {
        super.onResume()
        val emptyView = findViewById<TextView>(R.id.tvEmpty)
        loadChats(emptyView)
    }

    private fun loadChats(emptyView: TextView) {
        val userId = sessionManager.getUserId() ?: return
        apiClient.getChats(userId,
            onSuccess = { chats ->
                runOnUiThread {
                    adapter.setChats(chats)
                    emptyView.visibility = if (chats.isEmpty()) View.VISIBLE else View.GONE
                }
            },
            onError = { error ->
                runOnUiThread {
                    Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun getChatDisplayName(chat: Chat): String {
        if (!chat.name.isNullOrEmpty()) return chat.name
        val myId = sessionManager.getUserId()
        val otherUser = chat.users.firstOrNull { it.userId != myId }
        return otherUser?.userId ?: "Chat"
    }
}

class ChatAdapter(private val onClick: (Chat) -> Unit) :
    RecyclerView.Adapter<ChatAdapter.ViewHolder>() {

    private val chats = mutableListOf<Chat>()

    fun setChats(newChats: List<Chat>) {
        chats.clear()
        chats.addAll(newChats)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(chats[position], onClick)
    }

    override fun getItemCount() = chats.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvInitials = view.findViewById<TextView>(R.id.tvInitials)
        private val tvName = view.findViewById<TextView>(R.id.tvChatName)
        private val tvType = view.findViewById<TextView>(R.id.tvChatType)

        fun bind(chat: Chat, onClick: (Chat) -> Unit) {
            val displayName = if (!chat.name.isNullOrEmpty()) chat.name
            else "Prywatna rozmowa"
            tvName.text = displayName
            tvType.text = if (chat.type == "GROUP") "Grupa" else "Prywatna"
            tvInitials.text = displayName.take(2).uppercase()
            itemView.setOnClickListener { onClick(chat) }
        }
    }
}

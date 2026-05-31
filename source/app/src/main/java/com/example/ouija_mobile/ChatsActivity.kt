package com.example.ouija_mobile

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.io.InputStream
import java.net.URL
import java.util.concurrent.Executors

class ChatsActivity : BaseActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var apiClient: ApiClient
    private lateinit var adapter: ChatAdapter
    private var allChats: List<Chat> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chats)

        sessionManager = SessionManager(this)
        apiClient = ApiClient(sessionManager)

        val recycler = findViewById<RecyclerView>(R.id.recyclerChats)
        val emptyView = findViewById<TextView>(R.id.tvEmpty)
        val searchView = findViewById<EditText>(R.id.etSearch)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)

        adapter = ChatAdapter(sessionManager.getUserId() ?: "") { chat ->
            val displayName = getChatDisplayName(chat)
            val myId = sessionManager.getUserId()
            val otherUser = chat.users.firstOrNull { it.userId != myId }?.user
            // Serialize users for MessageAdapter: id -> nickname,avatarUrl
            val usersJson = chat.users.joinToString("|") { cu ->
                val u = cu.user ?: return@joinToString ""
                "${cu.userId}::${u.nickname}::${u.avatarUrl ?: ""}"
            }
            startActivity(Intent(this, ChatActivity::class.java).apply {
                putExtra("CHAT_ID", chat.id)
                putExtra("CHAT_NAME", displayName)
                putExtra("CHAT_USERS", usersJson)
                if (otherUser?.avatarUrl != null) {
                    putExtra("CHAT_AVATAR_URL", otherUser.avatarUrl)
                }
            })
        }

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        // Live search filter
        searchView.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""
                val filtered = if (query.isEmpty()) allChats
                else allChats.filter { chat ->
                    getChatDisplayName(chat).contains(query, ignoreCase = true)
                }
                adapter.setChats(filtered)
                emptyView.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
            }
        })

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
                allChats = chats
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
        val otherChatUser = chat.users.firstOrNull { it.userId != myId }
        // Use the nested user's nickname when available
        return otherChatUser?.user?.nickname ?: otherChatUser?.userId ?: "Chat"
    }
}

class ChatAdapter(
    private val myUserId: String,
    private val onClick: (Chat) -> Unit
) : RecyclerView.Adapter<ChatAdapter.ViewHolder>() {

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
        holder.bind(chats[position], myUserId, onClick)
    }

    override fun getItemCount() = chats.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvInitials = view.findViewById<TextView>(R.id.tvInitials)
        private val ivAvatar   = view.findViewById<ImageView>(R.id.ivAvatar)
        private val tvName = view.findViewById<TextView>(R.id.tvChatName)
        private val tvType = view.findViewById<TextView>(R.id.tvChatType)

        fun bind(chat: Chat, myUserId: String, onClick: (Chat) -> Unit) {
            val displayName = if (!chat.name.isNullOrEmpty()) {
                chat.name
            } else {
                val other = chat.users.firstOrNull { it.userId != myUserId }
                other?.user?.nickname ?: "Prywatna rozmowa"
            }
            tvName.text = displayName
            tvType.text = if (chat.type == "GROUP") "Grupa" else "Prywatna"
            tvInitials.text = displayName.take(2).uppercase()
            tvInitials.visibility = View.VISIBLE
            ivAvatar.visibility = View.GONE

            // Try to load avatar for private chats
            val otherUser = chat.users.firstOrNull { it.userId != myUserId }?.user
            val avatarUrl = otherUser?.avatarUrl
            if (avatarUrl != null) {
                loadAvatarIntoViews(avatarUrl, ivAvatar, tvInitials)
            }

            itemView.setOnClickListener { onClick(chat) }
        }

        private fun loadAvatarIntoViews(url: String, imageView: ImageView, tvInitials: TextView) {
            val executor = Executors.newSingleThreadExecutor()
            executor.execute {
                try {
                    val inputStream: InputStream = URL(url).openStream()
                    val bitmap: Bitmap = BitmapFactory.decodeStream(inputStream)
                    imageView.post {
                        imageView.setImageBitmap(bitmap)
                        imageView.visibility = View.VISIBLE
                        tvInitials.visibility = View.GONE
                    }
                } catch (_: Exception) {
                    // Keep initials
                }
            }
        }
    }
}

package com.example.ouija_mobile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ChatActivity : BaseActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var apiClient: ApiClient
    private lateinit var wsClient: WebSocketClient
    private lateinit var adapter: MessageAdapter
    private lateinit var recycler: RecyclerView

    private var chatId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        sessionManager = SessionManager(this)
        apiClient = ApiClient(sessionManager)
        wsClient = WebSocketClient(sessionManager)

        chatId = intent.getStringExtra("CHAT_ID") ?: ""
        val chatName = intent.getStringExtra("CHAT_NAME") ?: "Chat"

        // Custom top bar
        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val tvChatName = findViewById<TextView>(R.id.tvChatName)
        val tvChatInitials = findViewById<TextView>(R.id.tvChatInitials)

        tvChatName.text = chatName
        tvChatInitials.text = chatName.take(2).uppercase()
        btnBack.setOnClickListener { finish() }

        recycler = findViewById(R.id.recyclerMessages)
        val etMessage = findViewById<EditText>(R.id.etMessage)
        val btnSend = findViewById<ImageButton>(R.id.btnSend)

        val myId = sessionManager.getUserId() ?: ""
        adapter = MessageAdapter(myId)
        recycler.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        recycler.adapter = adapter

        apiClient.getMessages(chatId,
            onSuccess = { messages ->
                runOnUiThread {
                    adapter.setMessages(messages.reversed())
                    recycler.scrollToPosition(adapter.itemCount - 1)
                }
            },
            onError = { error ->
                runOnUiThread { Toast.makeText(this, error, Toast.LENGTH_SHORT).show() }
            }
        )

        wsClient.onMessageReceived = { message ->
            runOnUiThread {
                adapter.addMessage(message)
                recycler.scrollToPosition(adapter.itemCount - 1)
            }
        }

        wsClient.onMessageUpdated = { message ->
            runOnUiThread { adapter.updateMessage(message) }
        }

        wsClient.onMessageDeleted = { messageId ->
            runOnUiThread { adapter.removeMessage(messageId) }
        }

        wsClient.connect(chatId)

        btnSend.setOnClickListener {
            val text = etMessage.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener
            etMessage.setText("")

            apiClient.sendMessage(chatId, text,
                onSuccess = { message ->
                    runOnUiThread {
                        adapter.addMessage(message)
                        recycler.scrollToPosition(adapter.itemCount - 1)
                    }
                },
                onError = { error ->
                    runOnUiThread { Toast.makeText(this, error, Toast.LENGTH_SHORT).show() }
                }
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        wsClient.disconnect()
    }
}

class MessageAdapter(private val myUserId: String) :
    RecyclerView.Adapter<MessageAdapter.ViewHolder>() {

    private val messages = mutableListOf<Message>()

    fun setMessages(newMessages: List<Message>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    fun addMessage(message: Message) {
        if (messages.none { it.id == message.id }) {
            messages.add(message)
            notifyItemInserted(messages.size - 1)
        }
    }

    fun updateMessage(message: Message) {
        val idx = messages.indexOfFirst { it.id == message.id }
        if (idx != -1) {
            messages[idx] = message
            notifyItemChanged(idx)
        }
    }

    fun removeMessage(messageId: String) {
        val idx = messages.indexOfFirst { it.id == messageId }
        if (idx != -1) {
            messages.removeAt(idx)
            notifyItemRemoved(idx)
        }
    }

    override fun getItemViewType(position: Int) =
        if (messages[position].senderId == myUserId) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layout = if (viewType == VIEW_TYPE_SENT) R.layout.item_message_sent
        else R.layout.item_message_received
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(messages[position])
    }

    override fun getItemCount() = messages.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvContent = view.findViewById<TextView>(R.id.tvContent)
        private val tvTime = view.findViewById<TextView>(R.id.tvTime)

        fun bind(message: Message) {
            tvContent.text = message.content ?: ""
            tvTime.text = if (message.sentAt.length >= 16) message.sentAt.substring(11, 16) else message.sentAt
        }
    }

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
    }
}

package com.example.ouija_mobile

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URL
import java.util.concurrent.Executors

class ChatActivity : BaseActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var apiClient: ApiClient
    private lateinit var wsClient: WebSocketClient
    private lateinit var adapter: MessageAdapter
    private lateinit var recycler: RecyclerView

    private var chatId: String = ""

    // Pending files to attach before sending
    private val pendingUris = mutableListOf<Uri>()
    private lateinit var scrollPendingFiles: HorizontalScrollView
    private lateinit var llPendingFiles: LinearLayout

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            val clipData = result.data?.clipData
            if (clipData != null) {
                for (i in 0 until clipData.itemCount) {
                    addPendingUri(clipData.getItemAt(i).uri)
                }
            } else if (uri != null) {
                addPendingUri(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        sessionManager = SessionManager(this)
        apiClient = ApiClient(sessionManager)
        wsClient = WebSocketClient(sessionManager)

        chatId = intent.getStringExtra("CHAT_ID") ?: ""
        val chatName = intent.getStringExtra("CHAT_NAME") ?: "Chat"

        val btnBack        = findViewById<ImageButton>(R.id.btnBack)
        val tvChatName     = findViewById<TextView>(R.id.tvChatName)
        val tvChatInitials = findViewById<TextView>(R.id.tvChatInitials)
        val ivChatAvatar   = findViewById<ImageView>(R.id.ivChatAvatar)
        scrollPendingFiles = findViewById(R.id.scrollPendingFiles)
        llPendingFiles     = findViewById(R.id.llPendingFiles)

        tvChatName.text     = chatName
        tvChatInitials.text = chatName.take(2).uppercase()
        btnBack.setOnClickListener { finish() }

        // Try to load avatar for private chats (passed via intent)
        val avatarUrl = intent.getStringExtra("CHAT_AVATAR_URL")
        if (avatarUrl != null) {
            loadAvatarIntoHeader(avatarUrl, ivChatAvatar, tvChatInitials)
        }

        recycler = findViewById(R.id.recyclerMessages)
        val etMessage = findViewById<EditText>(R.id.etMessage)
        val btnSend   = findViewById<ImageButton>(R.id.btnSend)
        val btnAttach = findViewById<ImageButton>(R.id.btnAttach)

        val myId = sessionManager.getUserId() ?: ""
        adapter = MessageAdapter(myId, this)
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

        // ── Attach button ─────────────────────────────────────────
        btnAttach.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            filePickerLauncher.launch(Intent.createChooser(intent, "Wybierz pliki"))
        }

        // ── Send button ───────────────────────────────────────────
        btnSend.setOnClickListener {
            val text = etMessage.text.toString().trim()
            if (text.isEmpty() && pendingUris.isEmpty()) return@setOnClickListener
            etMessage.setText("")

            if (pendingUris.isEmpty()) {
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
            } else {
                val urisCopy = pendingUris.toList()
                clearPendingFiles()

                apiClient.uploadFiles(
                    context = this,
                    uris = urisCopy,
                    onSuccess = { mediaFiles ->
                        val attachments = mediaFiles.map { mf ->
                            AttachmentInput(
                                url = mf.url,
                                type = mimeToAttachmentType(mf.mimeType),
                                name = mf.filename
                            )
                        }
                        apiClient.sendMessageWithAttachments(
                            chatId = chatId,
                            content = text.ifEmpty { null },
                            attachments = attachments,
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
                    },
                    onError = { error ->
                        runOnUiThread { Toast.makeText(this, "Błąd uploadu: $error", Toast.LENGTH_LONG).show() }
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        wsClient.disconnect()
    }

    // ── Pending files ──────────────────────────────────────────────────────────

    private fun addPendingUri(uri: Uri) {
        pendingUris.add(uri)
        refreshPendingBar()
    }

    private fun clearPendingFiles() {
        pendingUris.clear()
        runOnUiThread { refreshPendingBar() }
    }

    private fun refreshPendingBar() {
        if (pendingUris.isEmpty()) {
            scrollPendingFiles.visibility = View.GONE
            llPendingFiles.removeAllViews()
            return
        }
        scrollPendingFiles.visibility = View.VISIBLE
        llPendingFiles.removeAllViews()
        for ((index, uri) in pendingUris.withIndex()) {
            val chip = TextView(this).apply {
                val name = uri.lastPathSegment?.substringAfterLast("/") ?: "plik"
                text = "📎 $name  ✕"
                textSize = 12f
                setPadding(16, 8, 16, 8)
                setBackgroundResource(android.R.color.darker_gray)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(0, 0, 8, 8)
                layoutParams = params
                val idx = index
                setOnClickListener {
                    pendingUris.removeAt(idx)
                    refreshPendingBar()
                }
            }
            llPendingFiles.addView(chip)
        }
    }

    private fun mimeToAttachmentType(mimeType: String): String = when {
        mimeType.startsWith("image/") -> "IMAGE"
        mimeType.startsWith("video/") -> "VIDEO"
        mimeType.startsWith("audio/") -> "AUDIO"
        else -> "FILE"
    }

    private fun loadAvatarIntoHeader(url: String, imageView: ImageView, tvInitials: TextView) {
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
            } catch (_: Exception) {}
        }
    }
}

// ── MessageAdapter ─────────────────────────────────────────────────────────────

class MessageAdapter(private val myUserId: String, private val context: Context) :
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
        return ViewHolder(view, context)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(messages[position])
    }

    override fun getItemCount() = messages.size

    class ViewHolder(view: View, private val context: Context) : RecyclerView.ViewHolder(view) {
        private val tvContent        = view.findViewById<TextView>(R.id.tvContent)
        private val tvTime           = view.findViewById<TextView>(R.id.tvTime)
        private val flImageAttachment = view.findViewById<FrameLayout>(R.id.flImageAttachment)
        private val ivAttachment     = view.findViewById<ImageView>(R.id.ivAttachment)
        private val btnDownloadImage = view.findViewById<TextView>(R.id.btnDownloadImage)
        private val llAttachmentFile = view.findViewById<LinearLayout>(R.id.llAttachmentFile)
        private val tvAttachmentFile = view.findViewById<TextView>(R.id.tvAttachmentFile)
        private val btnDownload      = view.findViewById<TextView>(R.id.btnDownload)

        fun bind(message: Message) {
            tvContent.text = message.content ?: ""
            tvContent.visibility = if (message.content.isNullOrEmpty()) View.GONE else View.VISIBLE
            tvTime.text = if (message.sentAt.length >= 16) message.sentAt.substring(11, 16) else message.sentAt

            val firstAttachment = message.attachments.firstOrNull()
            if (firstAttachment != null) {
                when (firstAttachment.type) {
                    "IMAGE" -> {
                        flImageAttachment.visibility = View.VISIBLE
                        llAttachmentFile.visibility = View.GONE
                        loadImageIntoView(ivAttachment, firstAttachment.url)
                        btnDownloadImage.setOnClickListener {
                            downloadFile(context, firstAttachment.url, firstAttachment.name ?: "image.jpg")
                        }
                    }
                    else -> {
                        flImageAttachment.visibility = View.GONE
                        llAttachmentFile.visibility = View.VISIBLE
                        val icon = when (firstAttachment.type) {
                            "VIDEO" -> "🎥"
                            "AUDIO" -> "🎵"
                            else    -> "📎"
                        }
                        tvAttachmentFile.text = "$icon ${firstAttachment.name ?: "plik"}"
                        btnDownload.setOnClickListener {
                            downloadFile(context, firstAttachment.url, firstAttachment.name ?: "plik")
                        }
                    }
                }
            } else {
                flImageAttachment.visibility = View.GONE
                llAttachmentFile.visibility = View.GONE
            }
        }

        private fun loadImageIntoView(imageView: ImageView, url: String) {
            val executor = Executors.newSingleThreadExecutor()
            executor.execute {
                try {
                    val inputStream: InputStream = URL(url).openStream()
                    val bitmap: Bitmap = BitmapFactory.decodeStream(inputStream)
                    imageView.post { imageView.setImageBitmap(bitmap) }
                } catch (_: Exception) {
                    imageView.post { flImageAttachment?.visibility = View.GONE }
                }
            }
        }

        private fun downloadFile(context: Context, url: String, filename: String) {
            Toast.makeText(context, "Pobieranie: $filename…", Toast.LENGTH_SHORT).show()
            val executor = Executors.newSingleThreadExecutor()
            executor.execute {
                try {
                    val inputStream: InputStream = URL(url).openStream()
                    val bytes = inputStream.readBytes()

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val mimeType = when {
                            filename.endsWith(".jpg", ignoreCase = true) || filename.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
                            filename.endsWith(".png", ignoreCase = true) -> "image/png"
                            filename.endsWith(".gif", ignoreCase = true) -> "image/gif"
                            filename.endsWith(".mp4", ignoreCase = true) -> "video/mp4"
                            filename.endsWith(".mp3", ignoreCase = true) -> "audio/mpeg"
                            filename.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
                            else -> "application/octet-stream"
                        }
                        val isImage = mimeType.startsWith("image/")
                        val isVideo = mimeType.startsWith("video/")
                        val isAudio = mimeType.startsWith("audio/")

                        val collection = when {
                            isImage -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                            isVideo -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                            isAudio -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                            else    -> MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                        }
                        val relativeDir = when {
                            isImage -> Environment.DIRECTORY_PICTURES
                            isVideo -> Environment.DIRECTORY_MOVIES
                            isAudio -> Environment.DIRECTORY_MUSIC
                            else    -> Environment.DIRECTORY_DOWNLOADS
                        }
                        val values = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                            put(MediaStore.MediaColumns.RELATIVE_PATH, "$relativeDir/Ouija")
                        }
                        val uri = context.contentResolver.insert(collection, values)
                        uri?.let {
                            context.contentResolver.openOutputStream(it)?.use { os -> os.write(bytes) }
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Ouija")
                        dir.mkdirs()
                        val file = File(dir, filename)
                        FileOutputStream(file).use { it.write(bytes) }
                    }

                    (context as? android.app.Activity)?.runOnUiThread {
                        Toast.makeText(context, "Zapisano: $filename", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    (context as? android.app.Activity)?.runOnUiThread {
                        Toast.makeText(context, "Błąd pobierania: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
    }
}

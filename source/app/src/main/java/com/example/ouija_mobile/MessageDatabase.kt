package com.example.ouija_mobile

import android.content.Context
import androidx.room.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// ── Type Converters ────────────────────────────────────────────────────────────
// Room nie umie zapisać List<Attachment> — konwertujemy do/z JSON
class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromAttachmentList(value: List<Attachment>): String =
        gson.toJson(value)

    @TypeConverter
    fun toAttachmentList(value: String): List<Attachment> {
        val type = object : TypeToken<List<Attachment>>() {}.type
        return gson.fromJson(value, type) ?: emptyList()
    }
}

// ── Entity ─────────────────────────────────────────────────────────────────────
// Mapuje model Message na tabelę SQLite
@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    val senderId: String,
    val content: String?,
    val sentAt: String,
    val editedAt: String?,
    val attachmentsJson: String  // JSON z List<Attachment>
) {
    // Konwersja do modelu domenowego
    fun toMessage(gson: Gson): Message {
        val type = object : TypeToken<List<Attachment>>() {}.type
        val attachments: List<Attachment> = gson.fromJson(attachmentsJson, type) ?: emptyList()
        return Message(
            id = id,
            chatId = chatId,
            senderId = senderId,
            content = content,
            sentAt = sentAt,
            editedAt = editedAt,
            attachments = attachments
        )
    }

    companion object {
        // Konwersja z modelu domenowego
        fun fromMessage(message: Message, gson: Gson) = MessageEntity(
            id = message.id,
            chatId = message.chatId,
            senderId = message.senderId,
            content = message.content,
            sentAt = message.sentAt,
            editedAt = message.editedAt,
            attachmentsJson = gson.toJson(message.attachments)
        )
    }
}

// ── DAO ────────────────────────────────────────────────────────────────────────
@Dao
interface MessageDao {

    // Pobierz wszystkie wiadomości danego chatu, posortowane chronologicznie
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY sentAt ASC")
    fun getMessagesForChat(chatId: String): List<MessageEntity>

    // Wstaw lub zaktualizuj wiadomość (np. edycja)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrReplace(message: MessageEntity)

    // Wstaw lub zaktualizuj wiele wiadomości naraz
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrReplaceAll(messages: List<MessageEntity>)

    // Usuń konkretną wiadomość (np. po usunięciu przez WebSocket)
    @Query("DELETE FROM messages WHERE id = :messageId")
    fun deleteById(messageId: String)

    // Wyczyść cache konkretnego chatu (opcjonalne, np. przy refresh)
    @Query("DELETE FROM messages WHERE chatId = :chatId")
    fun clearChat(chatId: String)

    // Liczba wiadomości w cache dla danego chatu (do logowania/debugowania)
    @Query("SELECT COUNT(*) FROM messages WHERE chatId = :chatId")
    fun countForChat(chatId: String): Int
}

// ── Database ───────────────────────────────────────────────────────────────────
@Database(entities = [MessageEntity::class], version = 1, exportSchema = false)
abstract class MessageDatabase : RoomDatabase() {

    abstract fun messageDao(): MessageDao

    companion object {
        @Volatile
        private var INSTANCE: MessageDatabase? = null

        fun getInstance(context: Context): MessageDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MessageDatabase::class.java,
                    "ouija_messages.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}

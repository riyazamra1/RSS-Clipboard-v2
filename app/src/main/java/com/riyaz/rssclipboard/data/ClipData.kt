package com.riyaz.rssclipboard.data

import android.content.Context
import android.util.Patterns
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

enum class ClipCategory(val displayName: String) {
    TEXT("Text"),
    URL("Website"),
    EMAIL("Email"),
    PHONE("Mobile"),
    SENSITIVE("Password")
}

@Entity(
    tableName = "clipboard_entries",
    indices = [Index(value = ["createdAt"]), Index(value = ["isPinned"])]
)
data class ClipEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val content: String,
    val category: ClipCategory,
    val customName: String? = null,
    val sourceApp: String = "External App",
    val isPinned: Boolean = false,
    val isSensitive: Boolean = category == ClipCategory.SENSITIVE,
    val isManualSave: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

class ClipConverters {
    @TypeConverter
    fun fromCategory(category: ClipCategory): String = category.name
    @TypeConverter
    fun toCategory(value: String): ClipCategory =
        runCatching { ClipCategory.valueOf(value) }.getOrDefault(ClipCategory.TEXT)
}

object ContentDetector {
    // Raw Kotlin string keeps regex backslashes from being parsed as Kotlin escapes.
    private val phoneRegex = Regex("""^\+?[0-9()\s-]{7,20}$""")
    private val sensitiveKeywords = listOf("password", "secret", "api_key", "bearer", "otp", "token")

    fun detectCategory(rawText: String, flaggedSensitive: Boolean = false): ClipCategory {
        val text = rawText.trim()
        if (flaggedSensitive || sensitiveKeywords.any { text.lowercase().contains(it) }) return ClipCategory.SENSITIVE
        if (Patterns.EMAIL_ADDRESS.matcher(text).matches()) return ClipCategory.EMAIL
        if (Patterns.WEB_URL.matcher(text).matches() || text.startsWith("http://", true) || text.startsWith("https://", true)) return ClipCategory.URL
        if (phoneRegex.matches(text) && text.count { it.isDigit() } in 7..15) return ClipCategory.PHONE
        return ClipCategory.TEXT
    }
}

@Dao
interface ClipDao {
    @Query("SELECT * FROM clipboard_entries ORDER BY isPinned DESC, createdAt DESC")
    fun observeAllClips(): Flow<List<ClipEntity>>

    @Query("SELECT * FROM clipboard_entries WHERE content = :content LIMIT 1")
    suspend fun findByContent(content: String): ClipEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ClipEntity): Long

    @Update
    suspend fun update(entry: ClipEntity)

    @Delete
    suspend fun delete(entry: ClipEntity)

    @Query("DELETE FROM clipboard_entries WHERE isPinned = 0 AND isManualSave = 0 AND createdAt < :cutoffTime")
    suspend fun deleteExpiredUnpinned(cutoffTime: Long)

    @Query("DELETE FROM clipboard_entries WHERE isPinned = 0")
    suspend fun deleteAllUnpinned()
}

@Database(entities = [ClipEntity::class], version = 5, exportSchema = false)
@TypeConverters(ClipConverters::class)
abstract class ClipDatabase : RoomDatabase() {
    abstract fun clipDao(): ClipDao

    companion object {
        @Volatile private var INSTANCE: ClipDatabase? = null
        fun getInstance(context: Context): ClipDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context.applicationContext, ClipDatabase::class.java, "rss_clipboard_hybrid_v5.db")
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

package com.nobi.nobistream2.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** A Telegram group or channel the user added on Home. */
@Entity(tableName = "saved_groups")
data class SavedGroupEntity(
    @PrimaryKey val chatId: Long,
    val username: String,
    val title: String,
    val photoLocalPath: String?,
    val videoCount: Int,
    val lastUpdatedEpochSec: Long
)

/** Resume position (and display data) for one video message. */
@Entity(tableName = "video_positions", primaryKeys = ["chatId", "messageId"])
data class VideoPositionEntity(
    val chatId: Long,
    val messageId: Long,
    val title: String,
    val caption: String,
    val qualityLabel: String,
    val durationSec: Int,
    val sizeBytes: Long,
    val dateEpochSec: Long,
    val thumbFileId: Int?,
    val thumbLocalPath: String?,
    val fileId: Int,
    val remoteFileId: String,
    val positionSec: Long,
    val watched: Boolean,
    val updatedAtEpochSec: Long
)

@Dao
interface GroupDao {
    @Query("SELECT * FROM saved_groups ORDER BY title ASC")
    fun observeGroups(): Flow<List<SavedGroupEntity>>

    @Query("SELECT * FROM saved_groups WHERE chatId = :chatId LIMIT 1")
    suspend fun getGroup(chatId: Long): SavedGroupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(group: SavedGroupEntity)

    @Query("UPDATE saved_groups SET videoCount = :count, lastUpdatedEpochSec = :updatedAt WHERE chatId = :chatId")
    suspend fun updateCount(chatId: Long, count: Int, updatedAt: Long)

    @Query("DELETE FROM saved_groups WHERE chatId = :chatId")
    suspend fun delete(chatId: Long)
}

@Dao
interface PositionDao {
    @Query("SELECT * FROM video_positions WHERE positionSec > 0 AND watched = 0 ORDER BY updatedAtEpochSec DESC LIMIT 10")
    fun observeContinueWatching(): Flow<List<VideoPositionEntity>>

    /**
     * v4: the same list WITHOUT the 10-item cap, for the full
     * Continue Watching ("See all") screen. Same query otherwise —
     * no schema change, no migration.
     */
    @Query("SELECT * FROM video_positions WHERE positionSec > 0 AND watched = 0 ORDER BY updatedAtEpochSec DESC")
    fun observeContinueWatchingAll(): Flow<List<VideoPositionEntity>>

    /** The single most recently watched (unfinished) video, for the Home hero card and auto-redirect. */
    @Query("SELECT * FROM video_positions WHERE positionSec > 0 AND watched = 0 ORDER BY updatedAtEpochSec DESC LIMIT 1")
    suspend fun getMostRecent(): VideoPositionEntity?

    @Query("SELECT * FROM video_positions WHERE chatId = :chatId AND messageId = :messageId LIMIT 1")
    suspend fun getPosition(chatId: Long, messageId: Long): VideoPositionEntity?

    @Query("SELECT * FROM video_positions WHERE chatId = :chatId")
    suspend fun getPositionsForChat(chatId: Long): List<VideoPositionEntity>

    /**
     * v4-fb2: remember a downloaded thumbnail's local path, so the
     * next bind loads it from disk with no TDLib call. No schema
     * change — the column already exists, it was just never written.
     */
    @Query("UPDATE video_positions SET thumbLocalPath = :path WHERE chatId = :chatId AND messageId = :messageId")
    suspend fun updateThumbPath(chatId: Long, messageId: Long, path: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(position: VideoPositionEntity)
}

/** One video the user saved to Watch Later (snapshot, like a resume row). */
@Entity(tableName = "watch_later", primaryKeys = ["chatId", "messageId"])
data class WatchLaterEntity(
    val chatId: Long,
    val messageId: Long,
    val title: String,
    val caption: String,
    val qualityLabel: String,
    val durationSec: Int,
    val sizeBytes: Long,
    val dateEpochSec: Long,
    val thumbFileId: Int?,
    val thumbLocalPath: String?,
    val fileId: Int,
    val remoteFileId: String,
    val savedAtEpochSec: Long
)

/** Download state for one video (real TDLib state, never invented). */
@Entity(tableName = "downloads", primaryKeys = ["chatId", "messageId"])
data class DownloadEntity(
    val chatId: Long,
    val messageId: Long,
    val title: String,
    val qualityLabel: String,
    val durationSec: Int,
    val sizeBytes: Long,
    val dateEpochSec: Long,
    val thumbLocalPath: String?,
    val fileId: Int,
    val remoteFileId: String,
    /** QUEUED / DOWNLOADING / PAUSED / COMPLETED / FAILED */
    val status: String,
    val downloadedBytes: Long,
    val localPath: String?,
    val updatedAtEpochSec: Long
)

@Dao
interface WatchLaterDao {
    @Query("SELECT * FROM watch_later ORDER BY savedAtEpochSec DESC")
    fun observeAll(): Flow<List<WatchLaterEntity>>

    @Query("SELECT COUNT(*) FROM watch_later WHERE chatId = :chatId AND messageId = :messageId")
    suspend fun count(chatId: Long, messageId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WatchLaterEntity)

    @Query("DELETE FROM watch_later WHERE chatId = :chatId AND messageId = :messageId")
    suspend fun delete(chatId: Long, messageId: Long)
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY updatedAtEpochSec DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE chatId = :chatId AND messageId = :messageId LIMIT 1")
    suspend fun get(chatId: Long, messageId: Long): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DownloadEntity)

    @Query("DELETE FROM downloads WHERE chatId = :chatId AND messageId = :messageId")
    suspend fun delete(chatId: Long, messageId: Long)

    @Query("DELETE FROM downloads WHERE status = 'COMPLETED'")
    suspend fun deleteCompleted()
}

@Database(
    entities = [SavedGroupEntity::class, VideoPositionEntity::class, WatchLaterEntity::class, DownloadEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun groupDao(): GroupDao
    abstract fun positionDao(): PositionDao
    abstract fun watchLaterDao(): WatchLaterDao
    abstract fun downloadDao(): DownloadDao
}

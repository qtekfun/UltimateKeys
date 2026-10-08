// SPDX-FileCopyrightText: 2026 UltimateKeys contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatekeys.clipboard

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(tableName = "clips", indices = [Index(value = ["text"], unique = true)])
internal data class ClipEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val pinned: Boolean,
    val copiedAt: Long
)

@Dao
internal interface ClipDao {
    @Query("SELECT * FROM clips ORDER BY pinned DESC, copiedAt DESC, id DESC")
    fun observe(): Flow<List<ClipEntity>>

    @Query("UPDATE clips SET copiedAt = :now WHERE text = :text")
    suspend fun touch(text: String, now: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: ClipEntity): Long

    @Query("UPDATE clips SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("DELETE FROM clips WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM clips WHERE pinned = 0")
    suspend fun deleteUnpinned()

    @Query("DELETE FROM clips WHERE pinned = 0 AND copiedAt < :cutoff")
    suspend fun deleteUnpinnedCopiedBefore(cutoff: Long)

    @Query(
        "DELETE FROM clips WHERE pinned = 0 AND id NOT IN " +
            "(SELECT id FROM clips WHERE pinned = 0 ORDER BY copiedAt DESC, id DESC LIMIT :max)"
    )
    suspend fun trimUnpinnedTo(max: Int)
}

@Database(entities = [ClipEntity::class], version = 1, exportSchema = true)
internal abstract class ClipDatabase : RoomDatabase() {
    abstract fun dao(): ClipDao
}

/** The Room-backed history. One instance per process; see [clipStore]. */
internal class RoomClipStore(private val dao: ClipDao) : ClipStore {
    override fun observe(): Flow<List<ClipItem>> = dao.observe().map { rows ->
        rows.map { ClipItem(it.id, it.text, it.pinned, it.copiedAt) }
    }

    override suspend fun record(text: String, now: Long) {
        if (dao.touch(text, now) == 0) {
            dao.insert(ClipEntity(text = text, pinned = false, copiedAt = now))
        }
    }

    override suspend fun setPinned(id: Long, pinned: Boolean) = dao.setPinned(id, pinned)

    override suspend fun delete(id: Long) = dao.delete(id)

    override suspend fun deleteUnpinned() = dao.deleteUnpinned()

    override suspend fun deleteUnpinnedCopiedBefore(cutoff: Long) =
        dao.deleteUnpinnedCopiedBefore(cutoff)

    override suspend fun trimUnpinnedTo(max: Int) = dao.trimUnpinnedTo(max)
}

/** A Room-backed store. Pass a null [name] for an in-memory database (tests). */
fun clipStore(context: Context, name: String? = "clipboard.db"): ClipStore {
    val app = context.applicationContext
    val builder = if (name == null) {
        Room.inMemoryDatabaseBuilder(app, ClipDatabase::class.java)
    } else {
        Room.databaseBuilder(app, ClipDatabase::class.java, name)
    }
    return RoomClipStore(builder.build().dao())
}

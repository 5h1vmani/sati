package com.padakanti.sati.log

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.Upsert
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

/** How a practice is counted. One measure per practice. */
enum class Measure { Minutes, Count, Done }

/**
 * A practice the user keeps, such as Kriya Yoga. [createdDay] and every other
 * day field is a local date as `LocalDate.toEpochDay()`.
 */
@Entity(tableName = "practices")
data class Practice(
    @PrimaryKey val id: String,
    val name: String,
    val measure: Measure,
    val target: Int?,
    val createdDay: Long,
    val archived: Boolean,
    /** Order on Today. Lower comes first. */
    val position: Int,
)

/**
 * One stretch of days when a practice was archived. [toDay] is the day it came
 * back, or null while it is still archived. The analytics skip these days.
 */
@Entity(
    tableName = "archive_periods",
    foreignKeys = [ForeignKey(Practice::class, ["id"], ["practiceId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("practiceId")],
)
data class ArchivePeriod(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val practiceId: String,
    val fromDay: Long,
    val toDay: Long?,
)

/**
 * One logged practice. [day] is fixed when the entry is written and never
 * worked out again, so travel does not move it. [createdAt] is epoch
 * milliseconds, for sorting only.
 */
@Entity(
    tableName = "entries",
    foreignKeys = [ForeignKey(Practice::class, ["id"], ["practiceId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("practiceId", "day"), Index("day")],
)
data class Entry(
    @PrimaryKey val id: String,
    val practiceId: String,
    val day: Long,
    val value: Int,
    val focus: Int?,
    val note: String?,
    val createdAt: Long,
)

@Dao
interface LogDao {
    @Query("SELECT * FROM practices ORDER BY position, name")
    fun practices(): Flow<List<Practice>>

    @Query("SELECT * FROM practices ORDER BY position, name")
    suspend fun allPractices(): List<Practice>

    @Query("SELECT * FROM entries WHERE day BETWEEN :from AND :to ORDER BY day DESC, createdAt DESC")
    fun entriesBetween(from: Long, to: Long): Flow<List<Entry>>

    @Query("SELECT * FROM entries WHERE practiceId = :practiceId ORDER BY day DESC, createdAt DESC")
    fun entriesOf(practiceId: String): Flow<List<Entry>>

    @Query("SELECT * FROM entries ORDER BY day, createdAt")
    suspend fun allEntries(): List<Entry>

    @Query("SELECT COUNT(*) FROM entries WHERE practiceId = :practiceId AND day = :day")
    suspend fun entryCountOn(practiceId: String, day: Long): Int

    @Query("SELECT COUNT(*) FROM entries")
    fun entryTotal(): Flow<Int>

    @Query("SELECT * FROM practices WHERE id = :id")
    suspend fun practiceById(id: String): Practice?

    @Query("SELECT * FROM entries WHERE practiceId = :practiceId ORDER BY day DESC, createdAt DESC LIMIT 1")
    suspend fun lastEntryOf(practiceId: String): Entry?

    @Query("SELECT * FROM archive_periods ORDER BY fromDay")
    suspend fun allArchivePeriods(): List<ArchivePeriod>

    @Query("SELECT COUNT(*) FROM entries WHERE practiceId = :practiceId")
    suspend fun entryCount(practiceId: String): Int

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM practices")
    suspend fun nextPosition(): Int

    @Upsert
    suspend fun upsertPractice(practice: Practice)

    @Query("DELETE FROM practices WHERE id = :id")
    suspend fun deletePractice(id: String)

    @Upsert
    suspend fun upsertEntry(entry: Entry)

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun deleteEntry(id: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEntriesIgnoringIds(entries: List<Entry>)

    @Insert
    suspend fun insertArchivePeriod(period: ArchivePeriod)

    @Update
    suspend fun updateArchivePeriod(period: ArchivePeriod)

    @Query("SELECT * FROM archive_periods WHERE practiceId = :practiceId AND toDay IS NULL LIMIT 1")
    suspend fun openArchivePeriod(practiceId: String): ArchivePeriod?
}

@Database(entities = [Practice::class, ArchivePeriod::class, Entry::class], version = 1, exportSchema = true)
abstract class LogDb : RoomDatabase() {
    abstract fun dao(): LogDao

    companion object {
        @Volatile private var instance: LogDb? = null

        /**
         * The log lives in normal storage, which opens only after the first
         * unlock. Code that may run before it, such as a boot receiver, must
         * not call this.
         */
        fun get(context: Context): LogDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, LogDb::class.java, "log.db")
                .build().also { instance = it }
        }
    }
}

/** Runs [block] in one database transaction. */
suspend fun <T> LogDb.inTransaction(block: suspend () -> T): T = withTransaction { block() }

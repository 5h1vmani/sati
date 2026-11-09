package com.padakanti.sati.log

import android.content.Context
import androidx.room.ColumnInfo
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
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/**
 * How a practice is counted. One measure per practice. Room stores the name,
 * and the screen keeps the ordinal, so a new measure goes at the end.
 * A Duration is counted in seconds. It was called Minutes before database version 3.
 */
enum class Measure { Duration, Count, Done, Percent, Scale }

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
    /** Whether the entry form asks for a focus score. Entries that already have a focus keep it when this is off. */
    @ColumnInfo(defaultValue = "1") val askFocus: Boolean = true,
    /** The practice is due every this many days, from 1 to 7. */
    @ColumnInfo(defaultValue = "1") val everyDays: Int = 1,
    /** The first due day. The schedule counts from it and never moves, even when a due day is missed. */
    @ColumnInfo(defaultValue = "0") val scheduleStart: Long = 0,
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

    @Query("SELECT * FROM entries WHERE practiceId = :practiceId AND value > 0 ORDER BY day DESC, createdAt DESC LIMIT 1")
    suspend fun lastEntryOf(practiceId: String): Entry?

    @Query("SELECT * FROM archive_periods ORDER BY fromDay")
    suspend fun allArchivePeriods(): List<ArchivePeriod>

    @Query("SELECT * FROM archive_periods ORDER BY fromDay")
    fun archivePeriods(): Flow<List<ArchivePeriod>>

    /** The first entry day of each practice. The analytics use it to find when a practice started. */
    @Query("SELECT practiceId, MIN(day) AS day FROM entries GROUP BY practiceId")
    fun firstEntries(): Flow<List<FirstEntry>>

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

/**
 * Version 2 adds the focus switch. Old practices ask for focus, except done
 * practices, which had no focus row before. Version 1 data must survive, so
 * the migration only adds a column and sets it.
 */
const val ADD_ASK_FOCUS_SQL = "ALTER TABLE practices ADD COLUMN askFocus INTEGER NOT NULL DEFAULT 1"
const val ASK_FOCUS_OFF_FOR_DONE_SQL = "UPDATE practices SET askFocus = 0 WHERE measure = 'Done'"

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(ADD_ASK_FOCUS_SQL)
        db.execSQL(ASK_FOCUS_OFF_FOR_DONE_SQL)
    }
}

/**
 * Version 3 counts Duration in seconds and adds the repeat schedule. The steps
 * run in this order: the old Minutes rows are still found by name while the
 * values change, then the name changes, then the new columns appear. An old
 * practice is due every day from the day it was created. Version 2 allowed
 * up to 99,999 minutes and a focus on a Percent 0, so values are capped at
 * the new maximum and a value of 0, which now means not done, loses its focus.
 */
const val MINUTES_TO_SECONDS_ENTRIES_SQL =
    "UPDATE entries SET value = MIN(value * 60, 86400) WHERE practiceId IN (SELECT id FROM practices WHERE measure = 'Minutes')"
const val MINUTES_TO_SECONDS_TARGETS_SQL = "UPDATE practices SET target = MIN(target * 60, 86400) WHERE measure = 'Minutes' AND target IS NOT NULL"
const val RENAME_MINUTES_SQL = "UPDATE practices SET measure = 'Duration' WHERE measure = 'Minutes'"
const val ADD_EVERY_DAYS_SQL = "ALTER TABLE practices ADD COLUMN everyDays INTEGER NOT NULL DEFAULT 1"
const val ADD_SCHEDULE_START_SQL = "ALTER TABLE practices ADD COLUMN scheduleStart INTEGER NOT NULL DEFAULT 0"
const val SCHEDULE_START_FROM_CREATED_SQL = "UPDATE practices SET scheduleStart = createdDay"
const val NOT_DONE_HAS_NO_FOCUS_SQL = "UPDATE entries SET focus = NULL WHERE value = 0"

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(MINUTES_TO_SECONDS_ENTRIES_SQL)
        db.execSQL(MINUTES_TO_SECONDS_TARGETS_SQL)
        db.execSQL(RENAME_MINUTES_SQL)
        db.execSQL(ADD_EVERY_DAYS_SQL)
        db.execSQL(ADD_SCHEDULE_START_SQL)
        db.execSQL(SCHEDULE_START_FROM_CREATED_SQL)
        db.execSQL(NOT_DONE_HAS_NO_FOCUS_SQL)
    }
}

@Database(entities = [Practice::class, ArchivePeriod::class, Entry::class], version = 3, exportSchema = true)
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build().also { instance = it }
        }
    }
}

/** Runs [block] in one database transaction. */
suspend fun <T> LogDb.inTransaction(block: suspend () -> T): T = withTransaction { block() }

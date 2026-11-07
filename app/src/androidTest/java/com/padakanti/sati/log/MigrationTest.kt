package com.padakanti.sati.log

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks every database migration on a real SQLite, against the schema files
 * in app/schemas. A broken migration deletes the user's log, and nothing
 * brings it back. Each new migration gets a test here before its PR merges.
 *
 * Run only on an emulator, through tools/emulator_check.sh. On a phone with
 * Sati installed, the test run would replace the app and its data.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), LogDb::class.java)

    @Test
    fun migrate1To2() {
        helper.createDatabase(DB, 1).use { seedVersion1(it) }
        val db = helper.runMigrationsAndValidate(DB, 2, true, MIGRATION_1_2)

        // Every practice asks for focus, except Done practices, which had no focus before.
        assertEquals(
            mapOf(MAHAMANTRA to 1, LONG to 1, SURYA to 1, POOJA to 0, PERCENT to 1, MOOD to 1),
            db.intColumn("SELECT id, askFocus FROM practices"),
        )
        // Nothing else changes.
        assertEquals(VERSION_1_PRACTICES, db.rows("SELECT id, name, measure, target, createdDay, archived, position FROM practices ORDER BY position"))
        assertEquals(VERSION_1_ENTRIES, db.rows(ENTRY_ROWS))
        assertEquals(ARCHIVE_ROWS, db.rows("SELECT practiceId, fromDay, toDay FROM archive_periods"))
        db.close()
    }

    @Test
    fun migrate2To3() {
        helper.createDatabase(DB, 1).use { seedVersion1(it) }
        helper.runMigrationsAndValidate(DB, 2, true, MIGRATION_1_2).use { db ->
            // A user switched focus off for one practice in version 2. The migration must keep it.
            db.execSQL("UPDATE practices SET askFocus = 0 WHERE id = '$SURYA'")
        }
        val db = helper.runMigrationsAndValidate(DB, 3, true, MIGRATION_2_3)
        checkVersion3(db)
        assertEquals(0, db.intColumn("SELECT id, askFocus FROM practices")[SURYA])
        db.close()
    }

    /** A phone that skipped 1.2 goes from version 1 to 3, then the app reads the log with its own code. */
    @Test
    fun migrate1To3AndRead() {
        helper.createDatabase(DB, 1).use { seedVersion1(it) }
        val db = helper.runMigrationsAndValidate(DB, 3, true, MIGRATION_1_2, MIGRATION_2_3)
        checkVersion3(db)
        db.close()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val room = Room.databaseBuilder(context, LogDb::class.java, DB)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()
        try {
            runBlocking {
                val dao = room.dao()
                val practices = dao.allPractices()
                assertEquals(listOf(MAHAMANTRA, LONG, SURYA, POOJA, PERCENT, MOOD), practices.map { it.id })
                val mahamantra = practices.first { it.id == MAHAMANTRA }
                assertEquals(Measure.Duration, mahamantra.measure)
                assertEquals(21 * 60, mahamantra.target)
                assertEquals(1, mahamantra.everyDays)
                assertEquals(DAY, mahamantra.scheduleStart)
                assertEquals(true, practices.first { it.id == MOOD }.archived)
                assertEquals(VERSION_1_ENTRIES.size, dao.allEntries().size)
                assertEquals(1, dao.allArchivePeriods().size)
            }
        } finally {
            room.close()
        }
    }

    /** The state after version 3, whichever path led there. */
    private fun checkVersion3(db: SupportSQLiteDatabase) {
        // Minutes became Duration in seconds, capped at one day. The other measures keep their names.
        assertEquals(
            mapOf(MAHAMANTRA to "Duration", LONG to "Duration", SURYA to "Count", POOJA to "Done", PERCENT to "Percent", MOOD to "Scale"),
            db.rows("SELECT id, measure FROM practices").associate { it[0] to it[1] },
        )
        assertEquals(
            mapOf(MAHAMANTRA to 21 * 60, LONG to 86400, SURYA to 3, PERCENT to 80),
            db.intColumn("SELECT id, target FROM practices WHERE target IS NOT NULL"),
        )
        assertNull(db.rows("SELECT target FROM practices WHERE id = '$POOJA'").single()[0])
        // The Done practice still has focus off from version 2.
        assertEquals(0, db.intColumn("SELECT id, askFocus FROM practices")[POOJA])
        // Every old practice is due every day, from the day it was created.
        assertEquals(setOf("1"), db.rows("SELECT everyDays FROM practices").map { it[0] }.toSet())
        assertEquals(
            mapOf(MAHAMANTRA to DAY, LONG to DAY, SURYA to DAY + 1, POOJA to DAY + 2, PERCENT to DAY + 3, MOOD to DAY + 4).mapValues { it.value.toInt() },
            db.intColumn("SELECT id, scheduleStart FROM practices"),
        )
        // Minute values became seconds and were capped. A value of 0 is not done and loses its focus.
        assertEquals(
            listOf(
                listOf("e1", MAHAMANTRA, "$DAY", "1500", "7", "morning", "1"),
                listOf("e2", MAHAMANTRA, "${DAY + 1}", "0", null, null, "2"),
                listOf("e3", LONG, "$DAY", "86400", "9", null, "3"),
                listOf("e4", SURYA, "$DAY", "3", "8", null, "4"),
                listOf("e5", POOJA, "$DAY", "1", null, "with family", "5"),
                listOf("e6", PERCENT, "$DAY", "0", null, null, "6"),
                listOf("e7", MOOD, "$DAY", "6", null, null, "7"),
            ),
            db.rows(ENTRY_ROWS),
        )
        assertEquals(ARCHIVE_ROWS, db.rows("SELECT practiceId, fromDay, toDay FROM archive_periods"))
    }

    /** Realistic version 1 data: one practice of each measure, an archived one, and edge values. */
    private fun seedVersion1(db: SupportSQLiteDatabase) {
        for (row in VERSION_1_PRACTICES) {
            db.execSQL(
                "INSERT INTO practices (id, name, measure, target, createdDay, archived, position) VALUES (?, ?, ?, ?, ?, ?, ?)",
                row.toTypedArray(),
            )
        }
        for (row in VERSION_1_ENTRIES) {
            db.execSQL(
                "INSERT INTO entries (id, practiceId, day, value, focus, note, createdAt) VALUES (?, ?, ?, ?, ?, ?, ?)",
                row.toTypedArray(),
            )
        }
        db.execSQL("INSERT INTO archive_periods (practiceId, fromDay, toDay) VALUES (?, ?, NULL)", arrayOf<Any>(MOOD, DAY + 10))
    }

    private companion object {
        const val DB = "migration-test.db"
        const val DAY = 20_390L
        const val MAHAMANTRA = "p-mahamantra"
        const val LONG = "p-long"
        const val SURYA = "p-surya"
        const val POOJA = "p-pooja"
        const val PERCENT = "p-percent"
        const val MOOD = "p-mood"

        const val ENTRY_ROWS = "SELECT id, practiceId, day, value, focus, note, createdAt FROM entries ORDER BY id"

        val VERSION_1_PRACTICES = listOf(
            listOf(MAHAMANTRA, "Mahamantra", "Minutes", "21", "$DAY", "0", "0"),
            // 2,000 minutes is more than a day. Version 2 allowed up to 99,999.
            listOf(LONG, "Long retreat", "Minutes", "2000", "$DAY", "0", "1"),
            listOf(SURYA, "Surya Kriya", "Count", "3", "${DAY + 1}", "0", "2"),
            listOf(POOJA, "Guru Pooja", "Done", null, "${DAY + 2}", "0", "3"),
            listOf(PERCENT, "Attention", "Percent", "80", "${DAY + 3}", "0", "4"),
            listOf(MOOD, "Mood", "Scale", null, "${DAY + 4}", "1", "5"),
        )

        val VERSION_1_ENTRIES = listOf(
            listOf("e1", MAHAMANTRA, "$DAY", "25", "7", "morning", "1"),
            // 0 means not done. Its focus goes away in version 3.
            listOf("e2", MAHAMANTRA, "${DAY + 1}", "0", "5", null, "2"),
            listOf("e3", LONG, "$DAY", "99999", "9", null, "3"),
            listOf("e4", SURYA, "$DAY", "3", "8", null, "4"),
            listOf("e5", POOJA, "$DAY", "1", null, "with family", "5"),
            listOf("e6", PERCENT, "$DAY", "0", "4", null, "6"),
            listOf("e7", MOOD, "$DAY", "6", null, null, "7"),
        )

        val ARCHIVE_ROWS = listOf(listOf(MOOD, "${DAY + 10}", null))
    }
}

/** Every row as strings, with null for SQL NULL, so rows compare with plain equality. */
private fun SupportSQLiteDatabase.rows(sql: String): List<List<String?>> = query(sql).use { c ->
    buildList {
        while (c.moveToNext()) add((0 until c.columnCount).map { if (c.isNull(it)) null else c.getString(it) })
    }
}

/** A two-column query of id and a whole number, as a map. */
private fun SupportSQLiteDatabase.intColumn(sql: String): Map<String, Int> =
    rows(sql).associate { it[0]!! to it[1]!!.toInt() }

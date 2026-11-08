package com.homerentmanager.data

import android.app.Application
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.homerentmanager.data.local.AppDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class MigrationTest {
    private lateinit var context: Application
    private val dbName = "migration-1-2-test.db"
    private lateinit var helper: SupportSQLiteOpenHelper

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        if (::helper.isInitialized) helper.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun `migration 1 to 2 adds nullable notes and preserves bill rows`() {
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(1) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL(
                                "CREATE TABLE bills (" +
                                    "id TEXT NOT NULL PRIMARY KEY, " +
                                    "bill_number TEXT NOT NULL, " +
                                    "total_paise INTEGER NOT NULL, " +
                                    "paid_paise INTEGER NOT NULL)",
                            )
                            db.execSQL(
                                "INSERT INTO bills (id, bill_number, total_paise, paid_paise) " +
                                    "VALUES ('bill-1', 'INV-1', 5000, 1000)",
                            )
                        }

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) = Unit
                    },
                )
                .build(),
        )

        val db = helper.writableDatabase
        assertEquals(1, db.version)
        AppDatabase.MIGRATION_1_2.migrate(db)

        val columns = mutableMapOf<String, Boolean>()
        db.query("PRAGMA table_info(bills)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            val notNullIndex = cursor.getColumnIndexOrThrow("notnull")
            while (cursor.moveToNext()) {
                columns[cursor.getString(nameIndex)] = cursor.getInt(notNullIndex) == 1
            }
        }
        assertTrue(columns.containsKey("notes"))
        assertEquals(false, columns["notes"])

        db.query("SELECT bill_number, total_paise, paid_paise, notes FROM bills").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("INV-1", cursor.getString(0))
            assertEquals(5000L, cursor.getLong(1))
            assertEquals(1000L, cursor.getLong(2))
            assertTrue(cursor.isNull(3))
        }
    }
}

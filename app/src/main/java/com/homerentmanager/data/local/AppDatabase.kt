package com.homerentmanager.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.homerentmanager.data.dao.BillDao
import com.homerentmanager.data.dao.MeterReadingDao
import com.homerentmanager.data.dao.PaymentDao
import com.homerentmanager.data.dao.RoomDao
import com.homerentmanager.data.dao.SettingsDao
import com.homerentmanager.data.dao.TenancyDao
import com.homerentmanager.data.dao.TenantDao
import com.homerentmanager.data.entity.BillEntity
import com.homerentmanager.data.entity.MeterReadingEntity
import com.homerentmanager.data.entity.PaymentEntity
import com.homerentmanager.data.entity.RoomEntity
import com.homerentmanager.data.entity.SettingsEntity
import com.homerentmanager.data.entity.TenancyEntity
import com.homerentmanager.data.entity.TenantEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

@Database(
    entities = [
        RoomEntity::class,
        TenantEntity::class,
        TenancyEntity::class,
        MeterReadingEntity::class,
        BillEntity::class,
        PaymentEntity::class,
        SettingsEntity::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun roomDao(): RoomDao
    abstract fun tenantDao(): TenantDao
    abstract fun tenancyDao(): TenancyDao
    abstract fun meterReadingDao(): MeterReadingDao
    abstract fun billDao(): BillDao
    abstract fun paymentDao(): PaymentDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        const val NAME = "home_rent_manager.db"
        const val VERSION = 2

        /**
         * v1 -> v2 adds an optional note field to bills. Existing rows are preserved.
         * All other v1 data is untouched. New installs create v2 directly.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE bills ADD COLUMN notes TEXT")
            }
        }

        val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)
    }
}

/**
 * Seeds exactly seven rooms and one settings row on first creation. Uses INSERT OR IGNORE
 * so a partially seeded database can never grow beyond seven default rooms.
 */
class SeedCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        val now = System.currentTimeMillis()
        val roomInsert = db.compileStatement(
            """
            INSERT OR IGNORE INTO rooms
                (id, room_number, floor, default_rent_paise, default_electricity_rate,
                 notes, is_active, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        )
        for (index in 1..7) {
            roomInsert.clearBindings()
            roomInsert.bindString(1, UUID.randomUUID().toString())
            roomInsert.bindString(2, index.toString())
            roomInsert.bindString(3, if (index <= 3) "Ground floor" else "First floor")
            roomInsert.bindLong(4, 800_000L) // Rs. 8,000.00
            roomInsert.bindDouble(5, 8.0)
            roomInsert.bindString(6, "")
            roomInsert.bindLong(7, 1)
            roomInsert.bindLong(8, now)
            roomInsert.executeInsert()
        }

        val settingsInsert = db.compileStatement(
            """
            INSERT OR IGNORE INTO settings
                (id, owner_name, owner_mobile, property_name, property_address,
                 default_electricity_rate, bill_due_day, invoice_prefix, receipt_prefix,
                 backup_reminder_days, last_backup_at, last_backup_status,
                 last_backup_file_name, last_backup_size_bytes, updated_at)
            VALUES (1, '', '', 'Home Rentals', '', 8.0, 5, 'INV', 'RCP', 7, NULL,
                    'No manual backup yet', '', 0, ?)
            """.trimIndent(),
        )
        settingsInsert.bindLong(1, now)
        settingsInsert.executeInsert()
    }
}

/**
 * Owns the single Room instance. Repositories always obtain the database from here so a
 * restore can continue to work without holding stale DAO references.
 */
class DatabaseProvider(context: Context) {
    private val appContext = context.applicationContext

    val database: AppDatabase by lazy {
        Room.databaseBuilder(appContext, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .addCallback(SeedCallback())
            .build()
    }

    /** Flushes WAL contents into the main database file so a file copy is self-contained. */
    suspend fun checkpoint() = withContext(Dispatchers.IO) {
        database.openHelper.writableDatabase
            .query("PRAGMA wal_checkpoint(TRUNCATE)")
            .use { it.moveToFirst() }
    }

    suspend fun close() = withContext(Dispatchers.IO) {
        if (database.isOpen) database.close()
    }

    fun databaseFile() = appContext.getDatabasePath(AppDatabase.NAME)
}

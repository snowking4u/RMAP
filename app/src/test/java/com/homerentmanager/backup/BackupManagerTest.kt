package com.homerentmanager.backup

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.homerentmanager.data.local.AppDatabase
import com.homerentmanager.data.local.DatabaseProvider
import com.homerentmanager.data.repository.BillingRepository
import com.homerentmanager.data.repository.RoomRepository
import com.homerentmanager.data.repository.SettingsRepository
import com.homerentmanager.data.repository.TenancyRepository
import com.homerentmanager.data.repository.TenantRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class BackupManagerTest {
    private lateinit var context: Application
    private lateinit var provider: DatabaseProvider
    private lateinit var settings: SettingsRepository
    private lateinit var tenants: TenantRepository
    private lateinit var manager: BackupManager
    private lateinit var backupFile: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(AppDatabase.NAME)
        provider = DatabaseProvider(context)
        settings = SettingsRepository(provider)
        tenants = TenantRepository(provider)
        manager = BackupManager(
            context = context,
            provider = provider,
            settingsRepository = settings,
            billingRepository = BillingRepository(provider),
            roomRepository = RoomRepository(provider),
            tenancyRepository = TenancyRepository(provider),
        )
        backupFile = File(context.cacheDir, "test-backup.hrmbackup")
        backupFile.delete()
    }

    @After
    fun tearDown() {
        backupFile.delete()
        runBlocking { provider.close() }
        context.deleteDatabase(AppDatabase.NAME)
    }

    @Test
    fun `manual backup validates and restores a complete snapshot`() = runBlocking {
        val uri = Uri.fromFile(backupFile)
        val outcome = manager.createBackup(uri, "correct-password".toCharArray(), "test")
        assertTrue(backupFile.exists())
        assertTrue(outcome.sizeBytes > 0)
        assertTrue("backup should be encrypted", !backupFile.readBytes().decodeToString().contains("SQLite"))

        try {
            manager.validateBackup(uri, "wrong-password".toCharArray())
            fail("A wrong password must fail validation")
        } catch (_: Exception) {
        }

        val validated = manager.validateBackup(uri, "correct-password".toCharArray())
        assertEquals(7, validated.stats.rooms)
        assertEquals(0, validated.stats.tenants)

        tenants.createTenant(
            TenantRepository.TenantDraft(
                name = "Temporary Tenant",
                mobile = "9876543210",
                alternateMobile = "",
                address = "",
                idType = com.homerentmanager.data.entity.IdType.NONE,
                idNumber = "",
                notes = "",
            ),
            null,
        )
        assertEquals(1, tenants.observeAll().first().size)

        val result = manager.restoreBackup(validated)
        assertEquals(0, tenants.observeAll().first().size)
        assertEquals(7, provider.database.roomDao().count())
        assertTrue(File(context.filesDir, "restore_safety/${result.safetyCopyName}").exists())
    }

    @Test
    fun `tampered backup file is rejected before restore`() = runBlocking {
        val uri = Uri.fromFile(backupFile)
        manager.createBackup(uri, "correct-password".toCharArray(), "test")
        val bytes = backupFile.readBytes()
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 0x01).toByte()
        backupFile.writeBytes(bytes)

        try {
            manager.validateBackup(uri, "correct-password".toCharArray())
            fail("A tampered backup must not validate")
        } catch (_: Exception) {
        }
    }
}

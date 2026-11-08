package com.homerentmanager

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.homerentmanager.backup.BackupManager
import com.homerentmanager.data.local.DatabaseProvider
import com.homerentmanager.data.repository.BillingRepository
import com.homerentmanager.data.repository.ElectricityRepository
import com.homerentmanager.data.repository.RoomRepository
import com.homerentmanager.data.repository.SettingsRepository
import com.homerentmanager.data.repository.TenancyRepository
import com.homerentmanager.data.repository.TenantRepository
import com.homerentmanager.pdf.PdfService
import com.homerentmanager.work.BackupReminderWorker
import java.util.concurrent.TimeUnit

/**
 * Manual dependency graph. Keeping it explicit makes the data ownership obvious and avoids
 * hidden service locators. Only one Room database instance exists per process.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val databaseProvider = DatabaseProvider(appContext)
    val roomRepository = RoomRepository(databaseProvider)
    val tenantRepository = TenantRepository(databaseProvider)
    val tenancyRepository = TenancyRepository(databaseProvider)
    val electricityRepository = ElectricityRepository(databaseProvider)
    val billingRepository = BillingRepository(databaseProvider)
    val settingsRepository = SettingsRepository(databaseProvider)
    val backupManager = BackupManager(
        context = appContext,
        provider = databaseProvider,
        settingsRepository = settingsRepository,
        billingRepository = billingRepository,
        roomRepository = roomRepository,
        tenancyRepository = tenancyRepository,
    )
    val pdfService = PdfService(appContext)

    fun scheduleBackgroundWork() {
        val request = PeriodicWorkRequestBuilder<BackupReminderWorker>(
            24,
            TimeUnit.HOURS,
            2,
            TimeUnit.HOURS,
        )
            .setInitialDelay(1, TimeUnit.HOURS)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.MINUTES)
            .build()
        runCatching {
            WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
                BackupReminderWorker.UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}

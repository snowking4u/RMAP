package com.homerentmanager.work

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.homerentmanager.HrmApplication
import com.homerentmanager.MainActivity
import com.homerentmanager.R
import com.homerentmanager.util.formatDisplayDateTime
import java.time.Instant

/**
 * Daily, best-effort reminder. This is NOT Android Auto Backup and never uploads data.
 * It only checks the last manual backup age and the estimated Auto Backup quota, then posts
 * a local notification. Android itself remains in control of cloud backup scheduling.
 */
class BackupReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? HrmApplication ?: return Result.success()
        val container = app.container
        val needsBackup = runCatching { container.backupManager.needsBackupReminder() }.getOrDefault(false)
        val autoInfo = runCatching { container.backupManager.autoBackupInfo() }.getOrNull()
        val overQuota = autoInfo?.overQuota == true
        if (!needsBackup && !overQuota) return Result.success()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }

        val settings = runCatching { container.settingsRepository.get() }.getOrNull()
        val title: String
        val body: String
        if (overQuota) {
            title = "Auto Backup quota warning"
            body = "Your estimated app backup is over the 25 MB Android Auto Backup quota. " +
                "Open Backup & Restore for details."
        } else {
            title = "Time to back up your rent records"
            val last = settings?.lastBackupAt
            body = if (last == null) {
                "No manual backup has been created yet. Open Backup & Restore to save an encrypted archive."
            } else {
                "Last manual backup: ${last.formatDisplayDateTime()}. Keep an off-device copy in Google Drive."
            }
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_BACKUP, true)
        }
        val pending = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, HrmApplication.BACKUP_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching {
            NotificationManagerCompat.from(applicationContext)
                .notify(NOTIFICATION_ID, notification)
        }
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "backup-reminder-daily"
        private const val NOTIFICATION_ID = 4101
    }
}

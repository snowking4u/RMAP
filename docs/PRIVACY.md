# Privacy

## Summary

Home Rent Manager is offline-first. Rental data is stored in a local SQLite database on the device. The app declares **no internet permission**, contains no analytics, advertising, crash reporting, login, or third-party data SDK, and has no server component.

## Data stored on the device

- Property, room, tenant, tenancy, electricity, bill, payment, and settings records.
- Tenant identity details if the user enters them (for example Aadhaar/PAN type and number). These are optional free-text fields; the app does not verify or upload them.
- Last manual backup time, file name, and size.
- App-private pre-restore safety copies and generated PDFs in the app cache.

## Data leaving the device

Only when the user explicitly acts:

- **Manual backup** – the encrypted `.hrmbackup` file is written to the URI chosen in the system file picker. If the user chooses Google Drive, Google's own app/account handles the upload under Google's terms. The file contents are encrypted with the user's password; the file name and size are visible to the storage provider.
- **PDF/CSV share or save** – the selected document is passed to the destination app or saved to the chosen folder. These files are **not encrypted**.
- **Android Auto Backup** – if enabled on the device, Android may upload the app database to the user's Google account. This is performed and encrypted in transit/at rest by Android/Google, subject to the 25 MB quota. The app cannot inspect, trigger, or query this process.
- **Print** – the PDF is handed to the Android print framework and the chosen print service.

No rental data is sent to the developer.

## Permissions

| Permission | Purpose |
| --- | --- |
| `POST_NOTIFICATIONS` (Android 13+) | Local WorkManager backup reminders. Optional. |

The app requests no storage, location, contacts, camera, microphone, SMS, or phone permission, and it does not declare `INTERNET`. File access goes through the Storage Access Framework, where the user grants access to a specific document/folder.

WorkManager merges a few local scheduling permissions into the manifest: `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `ACCESS_NETWORK_STATE`, and `FOREGROUND_SERVICE`. They are used by the periodic reminder scheduler. The app has no code that opens a network connection.

## Security measures

- Manual backups use AES-256-GCM with PBKDF2-HMAC-SHA256 (210,000 iterations, random salt and IV).
- The password is never stored; only a derived key exists briefly in memory.
- Backup files are authenticated; tampering or a wrong password fails before restore.
- Restore validates integrity, checksum, manifest counts, and schema, then imports in a transaction.
- Room foreign keys and unique indexes protect data consistency.
- Android Auto Backup is scoped to the database and settings; caches and temporary files are excluded.

## User responsibilities

- Keep the backup password safe; it cannot be recovered.
- Store at least one backup off the device.
- Treat CSV/PDF exports as sensitive financial documents.
- Protect the unlocked device with a screen lock and full-disk encryption (Android default on modern versions).
- Review Google account backup settings if Auto Backup is not desired.

## Data deletion

Uninstalling the app removes its local database, cache, and app-private safety copies. It does not delete manual backups the user saved to Google Drive or other folders, nor PDF/CSV files exported elsewhere. Android Auto Backup copies are managed by the user's Google account and can be removed through the account's backup settings. In-app, ending a tenancy never deletes history; there is no bulk "erase all data" action. Use Android's app-data clearing/uninstall or a validated restore to replace data.

# Backup and restore

Home Rent Manager has two independent backup paths:

1. **Manual backup** – user-initiated, portable, password-encrypted `.hrmbackup` file. This is
   the reliable recovery path.
2. **Android Auto Backup** – OS-managed, best-effort, subject to Google's 25 MB quota and
   Android scheduling.

The app never uploads data itself and declares no INTERNET permission. Manual backups are
written through the Storage Access Framework to a location the user chooses, for example
Google Drive, a local folder, or an SD card.

> **Manual backups are encrypted.** The file contains a ZIP archive with a manifest and a
> full SQLite snapshot, then the entire ZIP is encrypted with AES-256-GCM under a password
> the owner chooses. There is no recovery password stored by the app. If the password is
> lost, the backup cannot be opened.

## Encryption format

Manual backups use the versioned `HRMBK1` envelope:

```
"HRMBK1" | 4-byte big-endian header length | UTF-8 JSON header | AES-GCM ciphertext
```

- **Password:** minimum 8 characters, never stored.
- **Key derivation:** PBKDF2-HMAC-SHA256, 210,000 iterations, random 16-byte salt.
- **Encryption:** AES-256-GCM, random 12-byte IV, 128-bit authentication tag.
- **Header:** non-sensitive metadata only: format version, database schema version,
  timestamp, app version, salt, IV, iteration count, cipher, and KDF names.
- **Plaintext:** a ZIP containing `manifest.json` and `database.db`.

The manifest records the app version, schema version, timestamp, SHA-256 of the database,
database byte size, and row counts for rooms, tenants, tenancies, readings, bills, and
payments. Row counts are not fixed to seven rooms; any number of rooms is supported.

## Manual backup

### Creating a backup

1. Open **More → Backup & Restore**.
2. Choose **Create backup**.
3. Enter a recovery password of at least 8 characters and confirm it.
4. Select a destination in the system file picker (Google Drive, Downloads, SD card, etc.).
5. Wait for the success message. The last backup time, file name, and size are recorded in
   settings.

The app checkpoints SQLite WAL state and creates a consistent snapshot (preferring
`VACUUM INTO`, falling back to a checkpointed file copy on older SQLite versions). It
inspects the snapshot (`PRAGMA integrity_check`, schema version, table presence, row counts),
builds the ZIP, encrypts it, and writes the exact bytes to the selected URI.

The app does not use Google Drive's hidden `appDataFolder`, does not overwrite the live
database, and does not automatically create or upload backups.

### Validation before restore

`validateBackup` performs all of these checks before enabling the Restore button:

- the envelope size is bounded to the supported maximum;
- the file starts with the `HRMBK1` magic and has a valid header length;
- the header and manifest format versions are supported;
- the database schema version exactly matches the installed database version;
- the iteration count and salt/IV sizes are sane;
- AES-GCM authentication succeeds (a wrong password or any modification fails here);
- the decrypted ZIP is well formed and stays within expansion limits;
- the manifest is present, readable, and parseable;
- the database entry is present and non-empty;
- the database byte size matches the manifest;
- the database SHA-256 matches the manifest;
- SQLite `PRAGMA integrity_check` returns `ok`;
- all required tables exist;
- row counts match the manifest;
- the schema version inside the database matches the manifest.

A failed validation never touches live data.

### Transactional restore

After validation, `restoreBackup` re-checks the snapshot checksum and schema. It then:

1. checkpoints the live database;
2. copies the live database to app-private `files/restore_safety/pre-restore-*.db` (keeps the
   three newest);
3. attaches the validated snapshot as `backup_db`;
4. begins one transaction, defers foreign keys, deletes all rows from live tables, and copies
   every table from the snapshot;
5. commits, detaches, and verifies integrity of the live database.

If any step fails, the transaction rolls back and the message says the live data is
unchanged. The live database file is never overwritten by the imported file, so a failure
cannot leave a half-written database. A successful restore discards the temporary validated
copy.

Restore replaces all current app data, including settings. Export any CSV or PDF you still
need first.

### Restoring a backup

1. Open **More → Backup & Restore**.
2. Choose **Restore** and select a `.hrmbackup` file.
3. Enter the recovery password. Validation runs automatically and a summary is shown.
4. Review the summary and tap **Restore now**.
5. Confirm the restore and check the dashboard afterwards.

Use **Export CSV** before restoring if you need a human-readable copy of the current data.
CSV exports are plain text and are not encrypted.

## Android Auto Backup

### Configuration

`AndroidManifest.xml`:

```xml
android:allowBackup="true"
android:fullBackupContent="@xml/backup_rules"
android:dataExtractionRules="@xml/data_extraction_rules"
```

`res/xml/backup_rules.xml` (Android 11 and lower) and
`res/xml/data_extraction_rules.xml` (Android 12+) include:

- `database/home_rent_manager.db`
- `database/home_rent_manager.db-wal`
- `database/home_rent_manager.db-shm`
- `sharedpref/` (forward compatibility; current settings live in the database)

and exclude:

- `cache/` (generated PDFs, temporary backup/validation files)
- `file/backup_tmp/`, `file/restore_safety/`, `file/pdfs/` (defensive exclusions)
- `no_backup/`

The database is small (well under 25 MB for normal use). The app estimates database +
shared-prefs size and displays it against the 25 MB quota. At 20 MB it warns; above 25 MB it
reports that Auto Backup will fail.

### What Android controls

The app cannot start an Auto Backup, choose its timing, query the last system backup time, or
force a restore. Android typically runs backups about once per day when the device is idle,
charging, and on Wi-Fi, subject to the user's Google account and device settings. The OS may
skip backups for battery, network, policy, or quota reasons.

Restore happens during app reinstall or device setup, and only when the OS/account decides to
restore. It is not guaranteed. Uninstalling the app removes local data; Auto Backup may bring
it back on reinstall under the same account, but this is not a substitute for a manual backup.

Device-to-device transfer uses the `device-transfer` rules and can copy the database when the
user migrates phones with a supported Android flow.

The app schedules a local WorkManager reminder every 24 hours (initial delay 1 hour) to
prompt a manual backup. It does not perform a backup. Reminder interval is configurable in
Settings; notifications require the Android 13+ permission.

### Verifying Auto Backup configuration

On a debuggable build with a connected device:

```bash
adb shell bmgr enabled
adb shell bmgr backupnow com.homerentmanager.debug
adb shell bmgr list transports
```

`backupnow` is a best-effort request; the transport/account still decides. On Android 12+, use
the system **Settings → System → Backup** screen to confirm the account is active. Auto
Backup is not available on all emulators/images.

## Backup limitations

- A forgotten recovery password cannot be recovered by the app.
- Android Auto Backup timing, transport, quota enforcement, and restore are controlled by
  Android/Google, not this app.
- The 25 MB quota is shared by the app's entire Auto Backup payload. Large CSV/PDF collections
  kept in backed-up directories could push it over, so generated files live in cache and are
  excluded.
- The app cannot show the last successful Auto Backup time.
- Backups are tied to an exact database schema version. A backup from a newer app cannot be
  restored by an older app; install the matching version.
- Restore is all-or-nothing and replaces current data.
- Internal pre-restore safety copies are app-private, limited to three, and excluded from Auto
  Backup. They are for immediate recovery, not long-term storage.
- CSV exports and PDFs are not encrypted; protect them like any other financial document.
- A backup stored only on the same phone is not protection against loss, theft, or factory
  reset. Keep at least one copy on Google Drive or another device.

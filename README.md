# Home Rent Manager

A complete, offline-first rental manager for a single property with **exactly seven rooms**. It is a native Android app built with Kotlin, Jetpack Compose, Material 3, Room/SQLite, MVVM, Coroutines/Flow, WorkManager, Android Auto Backup, the Storage Access Framework, and the Android PDF APIs.

There is no website, backend, login, subscription, analytics, cloud database, or INTERNET permission. All rental data lives on the device. Manual encrypted backups are written to a file location the user chooses, such as Google Drive through the system file picker.

- **Package / application ID:** `com.homerentmanager`
- **Version:** 1.0.0 (versionCode 1)
- **Database schema:** v2
- **Minimum Android version:** 8.0 (API 26)
- **Target / compile SDK:** 35
- **Language / UI:** Kotlin 2.0.21, Jetpack Compose, Material 3

## Features

- **Dashboard** – current-month billed/collected/outstanding amounts, occupancy, recent bills and payments, backup reminder.
- **Rooms** – seven seeded rooms, editable number/floor/rent/electricity rate/notes, occupancy and balance summary. Rooms cannot be added or deleted, so the property always stays at seven.
- **Tenants** – tenant directory, contact details, ID details, current and past tenancies, outstanding balance.
- **Tenancy history** – every tenancy is retained. Ending a tenancy frees the room without deleting the tenant, bills, payments, or meter readings.
- **Electricity** – meter readings with previous/current readings, calculated units and charges, and generation of the linked bill.
- **Billing** – one bill per room per month enforced by a unique database index; rent, electricity, additional charges, discount, totals, and notes.
- **Payments** – partial and full payments, UPI/cash/bank/cheque/other methods, receipt numbers, automatic paid/partial/unpaid status, overpayment rejection.
- **PDF** – professional A4 invoices and receipts with save-to-storage, share, and print actions.
- **Reports** – monthly collection, outstanding, room-wise and tenant-wise summaries, CSV export.
- **Backup & restore** – encrypted, versioned manual backups validated before a transactional restore; automatic Android backup configuration; 25 MB quota visibility.
- **Settings** – owner/property details, default electricity rate, due day, invoice/receipt prefixes, backup reminder interval.

## Quick start

```bash
# Requirements: JDK 17, Android SDK platform 35 and build-tools 35.0.0
# Point local.properties at your SDK (it is gitignored):
echo "sdk.dir=$HOME/Android/Sdk" > local.properties

./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Install it on a device or emulator with:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On first launch the database is created with seven rooms (numbers 1–7) and one settings row. Open **More → Settings** to enter the owner and property details, then edit each room's rent and electricity rate.

## Documentation

- [Setup and first use](docs/SETUP.md)
- [Database design](docs/DATABASE.md)
- [Backup and restore](docs/BACKUP.md)
- [Build and release](docs/BUILD.md)
- [Testing](docs/TESTING.md)
- [Privacy](docs/PRIVACY.md)

## Project layout

```
app/src/main/java/com/homerentmanager/
  data/            Room entities, DAOs, converters, database, repositories
  domain/          Billing calculator and domain exceptions
  backup/          Encrypted backup format, crypto, validation, restore, CSV
  pdf/             Invoice/receipt renderer and print adapter
  ui/              Compose theme, navigation, screens, components, ViewModels
  util/            Money/date formatting, validators, CSV, number-to-words
  work/            WorkManager backup reminder
app/src/test/      JVM and Robolectric tests
app/schemas/       Exported Room schema (committed for migration review)
docs/              Operational and technical documentation
```

Architecture is a manual dependency graph (`AppContainer`) with a single Room database owner (`DatabaseProvider`). ViewModels expose immutable UI state from `Flow`s; repositories own transactions; no Android or Compose types leak into the billing calculator.

## Data safety

- Amounts are stored as integer **paise** (`Long`); floating point is used only for meter readings and electricity rates, with explicit rounding at the billing boundary.
- Every table uses UUID primary keys, foreign keys, and indexes.
- A bill is unique per room and month at the database level, not only in the UI.
- Payments are inserted and the bill's paid amount/status is updated in one transaction. Overpayment is rejected.
- Manual backups are AES-256-GCM encrypted with a key derived from the user's password (PBKDF2-HMAC-SHA256, 210,000 iterations). The password is never stored.
- Restore validates the archive, checksum, manifest, row counts, schema version, and SQLite integrity, keeps an internal safety copy, and imports in a single transaction. A failure leaves live data unchanged.

## Limitations

- One property with exactly seven rooms by design.
- No multi-device sync; move data with a manual backup or Android device transfer.
- Android Auto Backup decides when to run (typically daily while charging and on Wi-Fi). The app cannot trigger or query it, and Google's 25 MB quota applies. Manual backups are the reliable recovery path.
- Manual backup passwords cannot be recovered. A lost password means the backup cannot be opened.
- Restoring replaces all current app data. The app keeps up to three internal pre-restore safety copies, but they are app-private and excluded from Auto Backup.
- CSV exports are plain text and are not encrypted.

## Status

This repository contains the complete app source, Room schema export, unit/Robolectric test suites, and Gradle build configuration. See [BUILD.md](docs/BUILD.md) for producing a signed release APK.

## License

Private project. All rights reserved. Do not redistribute without permission.

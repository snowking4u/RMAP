# Home Rent Manager

A complete, offline-first rental manager for a single property. It is a native Android app
built with Kotlin, Jetpack Compose, Material 3, Room/SQLite, MVVM, Coroutines/Flow,
WorkManager, Android Auto Backup, the Storage Access Framework, and the Android PDF APIs.

There is no website, backend, login, subscription, analytics, cloud database, or INTERNET
permission. All rental data lives on the device. Manual backups are password-encrypted
`HRMBK1` files written through the system file picker, for example to Google Drive or a
local folder.

- **Package / application ID:** `com.homerentmanager`
- **Version:** 1.0.0 (versionCode 1)
- **Database schema:** v2
- **Minimum Android version:** 8.0 (API 26)
- **Target / compile SDK:** 35
- **Language / UI:** Kotlin 2.0.21, Jetpack Compose, Material 3

---

## Owner manual

### First launch

1. Install and open the app. The database is created with seven starter rooms (numbers 1–7)
   and one settings row.
2. Open **More → Settings** and enter the owner name, property name, address, and mobile
   number.
3. Set the default electricity rate, bill due day, invoice prefix, and receipt prefix.
4. Open **Rooms** and adjust each starter room's rent, floor, electricity rate, and notes.
   You can add more rooms or archive unused ones at any time.

The seven starter rooms are a convenience, not a limit. The app works with any number of
rooms.

### Rooms

- Tap **Add Room** to create a room. Room numbers must be unique.
- Tap the pencil icon on a room to edit its number, floor, default rent, electricity rate,
  and notes.
- Tap the archive icon to retire a room. Archiving is refused while the room has an active
  tenancy; end the tenancy first.
- Tap the restore icon on an archived room to make it available again.
- Archived rooms keep all of their bills, payments, meter readings, and tenancy history.
  They are excluded from new tenant assignment and the electricity room picker.
- Rooms are never hard-deleted, so historical records always keep a valid room.

### Tenants and tenancies

- Open **Tenants** to register a tenant and optionally assign a vacant room immediately.
- A room can have only one active tenancy at a time, and a tenant can have only one active
  tenancy at a time.
- Ending a tenancy frees the room without deleting the tenant, the tenancy, the bills, the
  payments, or the meter readings.
- Starting a tenancy for a new tenant in the same room preserves the previous tenant's
  history.

### Meter readings and bills

- Open **Electricity** to record a reading manually, or generate it together with a bill.
- The app automatically uses the latest successful reading for that physical room as the
  **Previous Reading**. You only enter the **Current Reading**.
- If the room has no earlier reading, the previous reading starts at `0`.
- The current reading must be greater than or equal to the previous reading. The form checks
  this before submitting, and the database transaction checks it again.
- Units are `current − previous`, rounded to two decimals. Electricity is
  `units × rate`, rounded half-up to paise.
- Open **Billing** and tap **Generate bills** to bill one or more active tenancies for the
  selected month. The dialog shows each room's previous reading so you do not need to look
  it up.
- A bill is unique per room per month. Duplicate submissions are skipped or rejected without
  changing data.
- Bill generation and its linked meter reading are written in one transaction. If any part
  fails, the bill is rolled back.
- Historical bills store the electricity units, rate, and amount that applied on the bill
  date. Changing a room's current rate never changes an old bill or an old receipt.

### Payments and receipts

- Open a bill and tap **Record Payment** to add a full or partial payment.
- Supported methods are cash, UPI, bank transfer, cheque, and other, with an optional
  reference and notes.
- Overpayment, zero/negative payments, and payments on a fully paid bill are rejected.
- A payment updates the bill's paid amount and status (`UNPAID`, `PARTIAL`, `PAID`) in the
  same transaction.
- **Download Receipt** produces a PDF built from the stored bill and the payment history at
  the time of that payment. It shows:
  - rent, previous reading, current reading, units, rate, electricity charge;
  - additional charges, discount, original bill total;
  - previous payments, this payment, cumulative paid, outstanding balance;
  - payment date, method, reference, receipt number, room, tenant, and billing month.
- Receipts never recalculate historical electricity using the room's current rate.

### Reports and exports

- **Reports** shows monthly billed, collected, outstanding, room-wise and method-wise
  summaries, plus aging buckets.
- CSV exports are plain text. They are useful for spreadsheets but are **not encrypted**.
  Handle them like any other financial document.

### Backup and restore

- Open **More → Backup & Restore**.
- **Create backup** asks for a recovery password of at least 8 characters and a destination
  in the system file picker.
- The app writes an encrypted `.hrmbackup` file. The file contains a validated SQLite
  snapshot and a manifest, compressed as a ZIP and then encrypted with AES-256-GCM.
- **Restore** asks for the same password. The app decrypts, validates the envelope, checksum,
  schema version, row counts, and SQLite integrity, then imports the snapshot in one
  transaction. A failed validation or restore leaves live data unchanged.
- The app keeps up to three app-private pre-restore safety copies.
- There is no recovery password stored in the app. **If you forget the password, the backup
  cannot be opened.**
- Android Auto Backup is also enabled as a best-effort OS feature. It is controlled by
  Android/Google and is not a substitute for a manual encrypted backup.

### Settings

Settings include owner and property details, default electricity rate, bill due day,
invoice/receipt prefixes, backup reminder interval, and the last manual backup metadata.
Changing settings does not rewrite historical bills or receipts.

---

## Developer documentation

### Architecture

The app uses a manual dependency graph (`AppContainer`) and a single Room database owner
(`DatabaseProvider`). ViewModels expose immutable UI state from `Flow`s. Repositories own
transactions. The billing calculator is pure Kotlin and has no Android or Compose
dependencies.

```
UI (Compose) → ViewModel (StateFlow) → Repository (transactions) → Room DAO → SQLite
```

Navigation is a single `NavHost` with five top-level destinations: Home, Rooms, Tenants,
Billing, and More. Bottom-tab taps that target the already-selected tab are ignored, and
detail navigation uses `launchSingleTop` to prevent duplicate destinations from stacking.

### Project layout

```
app/src/main/java/com/homerentmanager/
  data/            Room entities, DAOs, converters, database, repositories
  domain/          Billing calculator, receipt snapshot builder, domain exceptions
  backup/          Encrypted HRMBK1 format, validation, restore, CSV
  pdf/             Invoice/receipt renderer and print adapter
  ui/              Compose theme, navigation, screens, components, ViewModels
  util/            Money/date formatting, validators, CSV, number-to-words
  work/            WorkManager backup reminder
app/src/test/      JVM and Robolectric tests
app/schemas/       Exported Room schema (committed for migration review)
docs/              Operational and technical documentation
```

### Data model and invariants

- Money is stored as integer **paise** (`Long`); floating point is used only for meter
  readings and electricity rates, with explicit rounding at billing boundaries.
- Every table uses UUID primary keys, foreign keys, and indexes.
- `bills` has a unique `(room_id, billing_month)` index and stores an immutable snapshot of
  rent, electricity units, rate, amount, charges, discount, and total.
- `meter_readings` is append-only. The latest reading for a physical room becomes the next
  bill's previous reading.
- `payments` is append-only with a unique receipt number. The bill's `paid_paise` and status
  are updated in the same transaction as the payment insert.
- Rooms are archived (`is_active = 0`), never deleted. Foreign keys use `RESTRICT`, so
  historical rows cannot lose their room.
- Schema v1 → v2 adds a nullable `bills.notes` column and preserves all existing rows.

### Build

Requirements: JDK 17, Android SDK platform 35, and build-tools 35.0.0.

```bash
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

Release signing is configured in `app/build.gradle.kts` when `keystore.properties` exists.
That file and all keystores are gitignored and must never be committed. See
[docs/BUILD.md](docs/BUILD.md) for the full release procedure.

### Tests

The suite uses JUnit, Robolectric, Truth, and `kotlinx-coroutines-test`. Database, migration,
and backup tests use a real SQLite database through Robolectric.

```bash
./gradlew :app:testDebugUnitTest
```

Reports:

- `app/build/reports/tests/testDebugUnitTest/index.html`
- `app/build/test-results/testDebugUnitTest/*.xml`

Robolectric's bundled native SQLite runtime has no Linux/aarch64 build. On an aarch64 host,
run the test worker with an x86_64 JDK (for example through `qemu-x86_64`), or run the suite
on an x86_64 machine/CI runner. The pure-JVM tests (billing, formatting, receipt, backup
format) run on any architecture. See [docs/TESTING.md](docs/TESTING.md) for details.

### Data safety

- A bill is unique per room and month at the database level, not only in the UI.
- Bill generation, meter-reading creation, payment insertion, tenancy changes, and restore
  are transactional.
- Manual backups use PBKDF2-HMAC-SHA256 with 210,000 iterations, a random 16-byte salt,
  AES-256-GCM with a random 12-byte IV, and a 128-bit authentication tag.
- Validation rejects a wrong password, tampering, a foreign file, a truncated file, a
  mismatched checksum, an incompatible schema version, invalid row counts, or a corrupt
  SQLite snapshot before any live data is touched.
- CSV exports and PDFs are plain files and are not encrypted.

### Limitations

- One property per installation. The number of rooms is not limited.
- No multi-device sync; move data with an encrypted manual backup or Android device transfer.
- Android Auto Backup timing, quota, and restore are controlled by Android/Google, not this
  app.
- A forgotten backup password cannot be recovered.
- Restoring replaces all current app data. Pre-restore safety copies are app-private and
  limited to three.
- The app cannot show the last successful Android Auto Backup time.

---

## Documentation

- [Setup and first use](docs/SETUP.md)
- [Database design](docs/DATABASE.md)
- [Backup and restore](docs/BACKUP.md)
- [Build and release](docs/BUILD.md)
- [Testing](docs/TESTING.md)
- [Privacy](docs/PRIVACY.md)

## License

Private project. All rights reserved. Do not redistribute without permission.

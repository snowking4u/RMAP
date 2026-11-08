# Testing

## Automated tests

Run all JVM/Robolectric tests:

```bash
./gradlew :app:testDebugUnitTest
```

Reports:

- `app/build/reports/tests/testDebugUnitTest/index.html`
- `app/build/test-results/testDebugUnitTest/*.xml` for CI parsing

The suite runs on the JVM with Robolectric for database, migration, and backup integration tests. No device or emulator is required.

### Coverage

| Suite | What it verifies |
| --- | --- |
| `BillingCalculatorTest` | Units calculation and rounding, invalid descending readings, paise electricity amount with `HALF_UP`, bill totals, discount limits, paid/partial/unpaid status, overpayment rejection. |
| `FormattingAndValidationTest` | Money parsing/formatting with Indian grouping, month helpers, mobile validation/normalization, CSV escaping (commas, quotes, newlines, spreadsheet formula injection), enum labels, amount-in-words for lakhs and paise. |
| `BackupFormatTest` | AES-GCM round trip, wrong-password rejection, short-password rejection, tampered-ciphertext authentication failure, envelope/manifest JSON round trip, foreign/truncated file rejection. |
| `BackupManagerTest` | Full manual backup creation, encrypted-at-rest check, wrong password rejection, tamper rejection, validation, transactional restore, internal safety copy, live-data replacement. |
| `DatabaseBehaviorTest` | Exactly seven seeded rooms and settings row; tenant replacement preserves old tenancy/bill/payments; duplicate monthly bill rejected without side effects; partial and full payments update status and never exceed the total. |
| `MigrationTest` | Schema 1 → 2 adds a nullable `bills.notes` column and preserves existing bill rows. |
| `BackupConfigurationTest` | Manifest requests Auto Backup; `backup_rules.xml` and `data_extraction_rules.xml` include the database and exclude cache/temp directories. |

### Database tests

Robolectric opens a real SQLite database per test, runs the seeding callback, and uses the production repositories and transactions. Each test deletes the database before and after itself.

### Backup tests

`BackupManagerTest` uses `file://` URIs supported by Robolectric's content resolver. On a device, the same code path runs through SAF `content://` URIs. The test verifies:

1. backup file is created and does not contain plaintext `SQLite`;
2. a wrong password fails;
3. a tampered byte fails authentication;
4. validation reports seven rooms;
5. a tenant added after backup is removed by restore;
6. seven rooms remain and an internal pre-restore safety copy exists.

The Android Auto Backup transport itself cannot be exercised from unit tests. Its configuration is tested; scheduling/upload/restore must be checked manually with `bmgr` as described in [BACKUP.md](BACKUP.md).

## Manual test plan

Use a debug build on a real device when possible.

### First run and seeding

- [ ] Clear app data or install fresh.
- [ ] App opens with seven rooms numbered 1–7 and a settings row.
- [ ] Room numbers, rent, floor, and electricity rate are editable.
- [ ] No add/delete room action exists.

### Tenant replacement and history

- [ ] Register tenant A and start a tenancy in Room 1.
- [ ] Generate a January bill for Room 1 and record a partial payment.
- [ ] End tenant A's tenancy with a leave date on/after the join date.
- [ ] Register tenant B and start a tenancy in Room 1.
- [ ] Generate a February bill for Room 1.
- [ ] January bill still shows tenant A, the old tenancy, the original rent, and the partial payment.
- [ ] Tenancy history shows A (ended) and B (active).
- [ ] Trying to start a second active tenancy for Room 1 or tenant B is rejected.

### Electricity and billing

- [ ] First reading for a room starts from 0 and calculates units/amount.
- [ ] Next reading uses the previous reading.
- [ ] Current reading lower than previous is rejected.
- [ ] Electricity amount matches units × rate rounded to paise.
- [ ] Bill total = rent + electricity + additional − discount.
- [ ] Discount greater than gross charges is rejected.
- [ ] The same room/month cannot be billed twice (try single and bulk generation).
- [ ] Due date uses the configured day and is clamped for short months.

### Payments

- [ ] Partial payment sets status `PARTIAL` and increases paid amount.
- [ ] Remaining payment sets status `PAID`.
- [ ] Overpayment and zero/negative payment are rejected.
- [ ] Payment appears in the bill, payments list, dashboard, and reports.
- [ ] Receipt PDF shows the correct receipt number, amount, method, and balance.

### PDF

- [ ] Invoice PDF opens and contains owner/property, tenant, room, month, rent, electricity, charges, discount, total, paid, and balance.
- [ ] Save writes to the selected SAF location.
- [ ] Share opens the Android share sheet with a readable PDF.
- [ ] Print opens the system print preview with A4 media.
- [ ] Receipt PDF is generated for each payment.

### Backup and restore

- [ ] Create a manual backup to Google Drive or a local folder.
- [ ] Confirm the file is present and the app records time/name/size.
- [ ] Try restoring with a wrong password; validation fails and data is unchanged.
- [ ] Modify the backup file; validation fails.
- [ ] Validate with the correct password; summary shows room/tenant/bill counts.
- [ ] Add a test tenant, restore the earlier backup, confirm the test tenant is gone and counts match.
- [ ] Confirm an internal safety copy exists under app-private `files/restore_safety/` (debug build via `run-as`).
- [ ] Export all three CSV reports and open them in a spreadsheet.

### Auto Backup

- [ ] `adb shell bmgr enabled` reports enabled.
- [ ] `adb shell bmgr backupnow com.homerentmanager.debug` requests a backup and logs success/failure.
- [ ] Backup screen shows enabled/disabled status, estimated size, and 25 MB quota warning.
- [ ] Settings → System → Backup shows the account and the app's backed-up data size where the OS exposes it.
- [ ] Reinstall on the same device/account and check whether Android restores the database. Record the result; this is OS-controlled and not guaranteed.
- [ ] Confirm generated PDFs and temporary files are not part of the app's backup size.

### Uninstall/reinstall recovery

- [ ] Create a manual encrypted backup first.
- [ ] Uninstall, reinstall, and check whether Auto Backup restored data (same account/device).
- [ ] If not restored, use the manual backup to recover.
- [ ] Confirm the app never claims a system restore is guaranteed.

### Regression checks

- [ ] Rotate the device on each major screen; state survives configuration changes.
- [ ] Enable large font/display size; key actions remain reachable.
- [ ] Deny notification permission; app still works, reminders do not appear.
- [ ] Use an offline device; every feature works except opening Drive in the system picker.
- [ ] Check amounts display with ₹ and Indian digit grouping.
- [ ] Check no permission prompt for storage appears (SAF is used).

## CI suggestion

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon
```

Cache `~/.gradle` and the Android SDK between runs. Keep test reports as artifacts. A release pipeline should additionally run `apksigner verify` and archive the signed APK outside the repository.

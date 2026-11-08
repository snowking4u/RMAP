# Setup and first use

## Device requirements

- Android 8.0 (API 26) or newer.
- About 100 MB of free space for the app and generated PDFs.
- No internet connection is required. The app declares no INTERNET permission; WorkManager contributes local scheduling permissions such as wake-lock and boot-completed.

## Developer requirements

- JDK 17 (`JAVA_HOME` must point to it).
- Android SDK platform 35 and build-tools 35.0.0.
- Android platform-tools for `adb`.
- Gradle 8.9 through the included wrapper.

Create `local.properties` in the repository root (it is gitignored):

```properties
sdk.dir=/absolute/path/to/Android/Sdk
```

Install the SDK components if needed:

```bash
sdkmanager "platforms;android-35" "build-tools;35.0.0" "platform-tools"
```

## Build and run

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or open the project in Android Studio and run the `app` configuration on a device/emulator running API 26+.

The debug build uses application ID `com.homerentmanager.debug`, so it can be installed next to a release build without sharing data.

## First launch

1. The database is created and exactly seven rooms are seeded with numbers 1–7, ground/first floor labels, a default rent of ₹8,000, and a default electricity rate of ₹8.00/unit.
2. Open **More → Settings** and fill in owner name, owner mobile, property name, property address, default electricity rate, bill due day, invoice prefix, and receipt prefix.
3. Open **Rooms**, select each room, and adjust the room number/floor, monthly rent, electricity rate, and notes. Rooms cannot be added or removed.
4. Add tenants from **Tenants**. A tenant can be registered without a room; starting a tenancy assigns an available room, monthly rent, security deposit, and joining date.
5. Open **Electricity** to record the first meter reading. The first reading uses a previous value of 0 until a reading exists for the room. Enter the current reading to generate units and the electricity amount.
6. Open **Billing**, choose the month, and generate bills. A bill can also be generated directly from the electricity form. Duplicate bills for the same room and month are rejected.
7. Record payments from the bill detail screen. Partial payments are allowed; total payments can never exceed the bill total.
8. Generate the invoice PDF from the bill screen and use **Save**, **Share**, or **Print**.

## Suggested monthly workflow

1. Record the current electricity reading for each occupied room.
2. Generate the month's bills (individually or in bulk).
3. Review due dates and amounts.
4. Record payments as they arrive. The dashboard and reports update automatically.
5. Generate/share invoices and receipts.
6. Create an encrypted manual backup and upload/save it to Google Drive or another location.
7. Confirm the backup appears in the backup screen's last-backup summary.

## Tenancy changes

- **End a tenancy** from the tenant or room screen. The leave date must not be before the join date. The tenancy is marked `ENDED`; the room becomes vacant; every historical bill, payment, and reading is preserved.
- **Replace a tenant** by ending the old tenancy, then registering the new tenant and starting a tenancy for the same room. Old bills still show the old tenant and old tenancy.
- A tenant can have only one active tenancy at a time, and a room can have only one active tenancy.

## Notifications

The app asks for the Android 13+ notification permission on first launch. It is used only for local backup reminders scheduled by WorkManager. Denying it does not affect rental features. Reminders are informational; the app never creates a backup without the user selecting a destination and entering a password.

## File access

The app uses the Storage Access Framework. It has no broad storage permission and does not read unrelated files. Backup, restore, CSV, and PDF save operations open the system document picker so the user explicitly chooses the destination and grants access.

## Uninstall/reinstall

Uninstalling removes the app's local database. Android Auto Backup may restore it automatically after reinstall when:

- the same Google account and device (or a supported device-transfer flow) is used,
- the device has backup enabled and the app has been backed up,
- the OS chooses to restore.

This is controlled by Android and cannot be forced by the app. For a guaranteed recovery, keep a recent manual encrypted backup outside the device. See [BACKUP.md](BACKUP.md).

## Troubleshooting

| Symptom | Check |
| --- | --- |
| `SDK location not found` | `local.properties` has a valid `sdk.dir`. |
| Build fails with missing `aapt2` on ARM64 Linux | See the ARM64 note in [BUILD.md](BUILD.md). |
| Bills cannot be generated | The room is occupied, the month is `yyyy-MM`, and no bill exists for that room/month. |
| Payment rejected as overpayment | The amount exceeds the remaining balance. |
| Restore rejects a backup | It was created by a newer schema, the password is wrong, or the file is damaged. |
| No Auto Backup after reinstall | Android scheduling, account backup settings, and the 25 MB quota all apply; use a manual backup. |

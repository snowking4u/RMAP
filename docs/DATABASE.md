# Database design

Room 2.6.1 / SQLite, database name `home_rent_manager.db`, schema version **2**. The exported schema lives in `app/schemas/com.homerentmanager.data.local.AppDatabase/2.json` and is committed so schema changes are reviewable.

## Conventions

| Concern | Representation |
| --- | --- |
| Primary keys | UUID, stored as canonical 36-character text |
| Money | Integer **paise** (`Long`); ₹1 = 100 paise |
| Dates | Epoch day (`Long`) via `LocalDate` |
| Timestamps | Epoch milliseconds (`Long`) via `Instant` |
| Enums | Stored by name (`ACTIVE`, `PAID`, …) |
| Booleans | SQLite integer 0/1 |
| Electricity units/rates | `Double`, rounded to 2 decimals at calculation boundaries |

Money never uses `Float`/`Double`. Electricity is inherently fractional (units, rate per unit), so it is calculated with `BigDecimal` and converted once to paise with `HALF_UP` rounding.

## Tables

### `rooms`

One row per rental room. Exactly seven rows are seeded on first creation with `INSERT OR IGNORE`, so a partially seeded database cannot exceed seven defaults.

| Column | Type | Notes |
| --- | --- | --- |
| `id` | TEXT PK | UUID |
| `room_number` | TEXT | Unique index |
| `floor` | TEXT | Free text |
| `default_rent_paise` | INTEGER | Defaults to ₹8,000 |
| `default_electricity_rate` | REAL | Default 8.00 |
| `notes` | TEXT | |
| `is_active` | INTEGER | |
| `created_at` | INTEGER | |

Rooms are editable but not creatable/deletable in the UI. Foreign keys use `RESTRICT`, so historical rows cannot lose their room.

### `tenants`

Tenant contact and identity details. Indexed by name, mobile, and active flag. `is_active` reflects whether the tenant currently has an active tenancy; it is not used to delete history.

### `tenancies`

The immutable bridge between a tenant, a room, and the rent that applied. Ending a tenancy sets `leave_date` and `status = ENDED`; the row is never deleted or overwritten.

Foreign keys: `room_id → rooms.id`, `tenant_id → tenants.id`, both `RESTRICT`.
Indexes: `room_id`, `tenant_id`, `(room_id, status)`, `(tenant_id, status)`.

Business rules enforced in `TenantRepository` inside transactions:

- a room can have only one `ACTIVE` tenancy at a time;
- a tenant can have only one `ACTIVE` tenancy at a time;
- `leave_date >= join_date`;
- ending a tenancy does not touch bills, payments, or readings.

### `meter_readings`

Append-only electricity history. Each reading stores previous/current values, units, rate, amount, date, and links to the room, tenancy, and (after billing) the bill. `bill_id` uses `ON DELETE SET NULL`.

Indexes: `room_id`, `tenancy_id`, `bill_id`, `(room_id, reading_date)`.

The next reading's `previous_reading` is the latest reading for the room. The first reading starts from 0.

### `bills`

One monthly bill per occupied room.

| Column | Notes |
| --- | --- |
| `bill_number` | Unique index; generated from invoice prefix, month, room, tenancy |
| `room_id`, `tenant_id`, `tenancy_id` | Snapshots of who was billed at generation time |
| `billing_month` | `yyyy-MM`, indexed |
| `bill_date`, `due_date` | Due day comes from settings, clamped to month length |
| `rent_paise` | Rent from the tenancy snapshot |
| `electricity_units`, `electricity_rate`, `electricity_amount_paise` | Reading-derived |
| `additional_charges_paise`, `discount_paise` | Non-negative; discount cannot exceed gross |
| `total_paise` | Rent + electricity + additional − discount |
| `paid_paise` | Maintained transactionally by payment operations |
| `status` | `UNPAID` / `PARTIAL` / `PAID` |
| `notes` | Added in schema v2, nullable |
| `created_at`, `updated_at` | |

**Duplicate prevention:** unique index on `(room_id, billing_month)`, plus an explicit check inside the insert transaction. A `SQLiteConstraintException` is translated to a domain conflict. Two simultaneous submissions cannot create two bills.

**History preservation:** tenant name/mobile changes do not alter bills. Ending a tenancy and assigning a new tenant keeps old bills pointing at the old tenant/tenancy and room.

### `payments`

Append-only financial records linked to a bill and tenant with `RESTRICT` deletes.

Indexes: `bill_id`, `tenant_id`, `paid_at`, unique `receipt_number`.

`BillingRepository.recordPayment` runs in one transaction:

1. load the bill;
2. recompute and validate the outstanding balance;
3. insert the payment;
4. increment `paid_paise`;
5. recompute `status`;
6. update `updated_at`.

Overpayment, zero/negative amounts, and payments on a fully paid bill are rejected before any write. The database stores the resulting paid total, so reports never need to sum all payments to know a bill's status.

### `settings`

Single row with `id = 1`: owner/property details, default electricity rate, bill due day, invoice/receipt prefixes, backup reminder days, and last manual backup metadata. The backup password is **never** stored.

## Transactions

Room's `withTransaction` is used for:

- tenant creation + optional first tenancy;
- starting/ending tenancies and updating tenant active flag;
- bill generation + linked meter reading;
- payment insert + bill paid/status update;
- restore of all tables.

`DatabaseProvider` owns the single database instance. Repositories obtain DAOs through it on each access, so restore operations can continue without stale references.

## Migration

Schema v1 → v2:

```sql
ALTER TABLE bills ADD COLUMN notes TEXT;
```

`MIGRATION_1_2` is registered with Room and adds a nullable column, preserving every existing row. Fresh installs create v2 directly. Backups record their schema version; restore refuses a backup whose schema does not exactly match the installed database version.

Adding a future schema version requires:

1. incrementing `AppDatabase.VERSION`;
2. adding a `Migration(n, n+1)` to `ALL_MIGRATIONS`;
3. exporting the new schema JSON;
4. adding a migration test;
5. updating the backup compatibility policy if old backups must be importable.

## Seeding

`SeedCallback.onCreate` inserts seven rooms and one settings row. It only runs when the database file is first created. It does not run on every open and never overwrites user edits. Restore deletes and re-imports all rows, including the seven rooms, from the validated snapshot.

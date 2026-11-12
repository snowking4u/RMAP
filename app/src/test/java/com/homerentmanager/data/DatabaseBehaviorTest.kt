package com.homerentmanager.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.homerentmanager.data.entity.BillStatus
import com.homerentmanager.data.entity.PaymentMethod
import com.homerentmanager.data.entity.TenancyStatus
import com.homerentmanager.data.local.AppDatabase
import com.homerentmanager.data.local.DatabaseProvider
import com.homerentmanager.data.repository.BillingRepository
import com.homerentmanager.data.repository.ElectricityRepository
import com.homerentmanager.data.repository.RoomRepository
import com.homerentmanager.data.repository.TenantRepository
import com.homerentmanager.domain.ConflictException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class DatabaseBehaviorTest {
    private lateinit var context: Application
    private lateinit var provider: DatabaseProvider
    private lateinit var tenants: TenantRepository
    private lateinit var billing: BillingRepository
    private lateinit var rooms: RoomRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(AppDatabase.NAME)
        provider = DatabaseProvider(context)
        tenants = TenantRepository(provider)
        billing = BillingRepository(provider)
        rooms = RoomRepository(provider)
    }

    @After
    fun tearDown() {
        if (::provider.isInitialized) {
            runBlocking { provider.close() }
        }
        if (::context.isInitialized) {
            context.deleteDatabase(AppDatabase.NAME)
        }
    }

    @Test
    fun `fresh database seeds seven starter rooms and settings`() = runBlocking {
        val rooms = provider.database.roomDao().getAll()
        assertEquals(7, rooms.size)
        assertEquals((1..7).map(Int::toString).toSet(), rooms.map { it.roomNumber }.toSet())
        assertNotNull(provider.database.settingsDao().get())
    }

    @Test
    fun `replacement tenant preserves the previous tenancy and bill`() = runBlocking {
        val room = provider.database.roomDao().getAll().first()
        val tenantA = tenants.createTenant(
            draft("Alice", "9876543210"),
            TenantRepository.NewTenancy(
                roomId = room.id,
                monthlyRentPaise = 800_000,
                securityDepositPaise = 100_000,
                joinDate = LocalDate.of(2026, 1, 1),
            ),
        )
        val tenancyA = provider.database.tenancyDao().getActiveForRoom(room.id)!!
        val billA = billing.generateBill(
            BillingRepository.BillRequest(
                tenancyId = tenancyA.id,
                billingMonth = "2026-01",
                currentReading = 120.0,
                ratePerUnit = 8.0,
            ),
        )
        billing.recordPayment(
            billId = billA,
            amountPaise = 50_000,
            paidAt = LocalDate.of(2026, 1, 20),
            method = PaymentMethod.CASH,
            reference = "",
            notes = "",
        )

        tenants.endTenancy(tenancyA.id, LocalDate.of(2026, 1, 31))
        val tenantB = tenants.createTenant(
            draft("Bob", "9123456780"),
            TenantRepository.NewTenancy(
                roomId = room.id,
                monthlyRentPaise = 900_000,
                securityDepositPaise = 120_000,
                joinDate = LocalDate.of(2026, 2, 1),
            ),
        )
        val tenancyB = provider.database.tenancyDao().getActiveForRoom(room.id)!!
        assertEquals(tenantB, tenancyB.tenantId)
        val billB = billing.generateBill(
            BillingRepository.BillRequest(
                tenancyId = tenancyB.id,
                billingMonth = "2026-02",
                currentReading = 150.0,
                ratePerUnit = 8.0,
            ),
        )

        val old = billing.observeById(billA).first()!!
        assertEquals("Alice", old.tenant.name)
        assertEquals(tenantA, old.bill.tenantId)
        assertEquals(tenancyA.id, old.bill.tenancyId)
        assertEquals(50_000L, old.bill.paidPaise)
        assertEquals(BillStatus.PARTIAL, old.bill.status)
        assertEquals(TenancyStatus.ENDED, provider.database.tenancyDao().getById(tenancyA.id)!!.status)

        val newBill = billing.observeById(billB).first()!!
        assertEquals("Bob", newBill.tenant.name)
        assertEquals(2, billing.observeForRoom(room.id).first().size)
        assertEquals(2, provider.database.billDao().count())
    }

    @Test
    fun `duplicate monthly bill is rejected and does not change data`() = runBlocking {
        val room = provider.database.roomDao().getAll().first()
        tenants.createTenant(
            draft("Carol", "9000000001"),
            TenantRepository.NewTenancy(
                roomId = room.id,
                monthlyRentPaise = 750_000,
                securityDepositPaise = 0,
                joinDate = LocalDate.of(2026, 3, 1),
            ),
        )
        val tenancy = provider.database.tenancyDao().getActiveForRoom(room.id)!!
        billing.generateBill(
            BillingRepository.BillRequest(tenancy.id, "2026-03", 100.0, 8.0),
        )
        val before = provider.database.billDao().count()
        try {
            billing.generateBill(
                BillingRepository.BillRequest(tenancy.id, "2026-03", 120.0, 8.0),
            )
            fail("Expected the duplicate month to be rejected")
        } catch (_: ConflictException) {
        }
        assertEquals(before, provider.database.billDao().count())
        assertEquals(1, provider.database.meterReadingDao().count())
    }

    @Test
    fun `partial and full payments update the bill but never exceed the total`() = runBlocking {
        val room = provider.database.roomDao().getAll().first()
        tenants.createTenant(
            draft("Dinesh", "9000000002"),
            TenantRepository.NewTenancy(
                roomId = room.id,
                monthlyRentPaise = 800_000,
                securityDepositPaise = 0,
                joinDate = LocalDate.of(2026, 4, 1),
            ),
        )
        val tenancy = provider.database.tenancyDao().getActiveForRoom(room.id)!!
        val billId = billing.generateBill(
            BillingRepository.BillRequest(tenancy.id, "2026-04", 10.0, 8.0),
        )
        val total = billing.getBill(billId)!!.totalPaise
        billing.recordPayment(billId, 30_000, LocalDate.of(2026, 4, 5), PaymentMethod.UPI, "UPI1", "")
        assertEquals(BillStatus.PARTIAL, billing.getBill(billId)!!.status)
        assertEquals(30_000L, billing.getBill(billId)!!.paidPaise)

        try {
            billing.recordPayment(
                billId,
                total,
                LocalDate.of(2026, 4, 6),
                PaymentMethod.CASH,
                "",
                "",
            )
            fail("Overpayment must be rejected")
        } catch (_: IllegalArgumentException) {
        }

        billing.recordPayment(
            billId,
            total - 30_000,
            LocalDate.of(2026, 4, 6),
            PaymentMethod.CASH,
            "",
            "",
        )
        val paid = billing.getBill(billId)!!
        assertEquals(BillStatus.PAID, paid.status)
        assertEquals(total, paid.paidPaise)
        assertEquals(2, provider.database.paymentDao().count())
    }

    @Test
    fun `new rooms can be added, archived and restored without deleting history`() = runBlocking {
        val roomId = rooms.addRoom("8", "Second floor", 900_000, 9.0, "")
        val added = provider.database.roomDao().getById(roomId)!!
        assertEquals("8", added.roomNumber)
        assertTrue(added.isActive)

        try {
            rooms.addRoom("8", "", 900_000, 9.0, "")
            fail("Duplicate room numbers must be rejected")
        } catch (_: ConflictException) {
        }

        val tenantId = tenants.createTenant(
            draft("Esha", "9000000003"),
            TenantRepository.NewTenancy(
                roomId = roomId,
                monthlyRentPaise = 900_000,
                securityDepositPaise = 0,
                joinDate = LocalDate.of(2026, 5, 1),
            ),
        )
        val tenancy = provider.database.tenancyDao().getActiveForRoom(roomId)!!
        try {
            rooms.setRoomActive(roomId, false)
            fail("An occupied room cannot be archived")
        } catch (_: ConflictException) {
        }

        tenants.endTenancy(tenancy.id, LocalDate.of(2026, 5, 31))
        rooms.setRoomActive(roomId, false)
        assertFalse(provider.database.roomDao().getById(roomId)!!.isActive)
        try {
            tenants.startTenancy(
                tenantId,
                TenantRepository.NewTenancy(
                    roomId = roomId,
                    monthlyRentPaise = 900_000,
                    securityDepositPaise = 0,
                    joinDate = LocalDate.of(2026, 6, 1),
                ),
            )
            fail("An archived room cannot receive a new tenancy")
        } catch (_: ConflictException) {
        }

        rooms.setRoomActive(roomId, true)
        assertTrue(provider.database.roomDao().getById(roomId)!!.isActive)
    }

    @Test
    fun `unused rooms can be deleted but rooms with history cannot`() = runBlocking {
        val unusedId = rooms.addRoom("14", "", 800_000, 8.0, "")
        rooms.deleteRoom(unusedId)
        assertNull(provider.database.roomDao().getById(unusedId))

        val linkedId = rooms.addRoom("15", "", 800_000, 8.0, "")
        tenants.createTenant(
            draft("Omar", "9000000013"),
            TenantRepository.NewTenancy(
                roomId = linkedId,
                monthlyRentPaise = 800_000,
                securityDepositPaise = 0,
                joinDate = LocalDate.of(2026, 7, 1),
            ),
        )

        try {
            rooms.deleteRoom(linkedId)
            fail("A room with linked history must not be deleted")
        } catch (_: ConflictException) {
        }

        assertNotNull(provider.database.roomDao().getById(linkedId))
    }

    @Test
    fun `next bill uses the latest room reading as previous automatically`() = runBlocking {
        val room = provider.database.roomDao().getAll().first()
        tenants.createTenant(
            draft("Farah", "9000000004"),
            TenantRepository.NewTenancy(
                roomId = room.id,
                monthlyRentPaise = 800_000,
                securityDepositPaise = 0,
                joinDate = LocalDate.of(2026, 6, 1),
            ),
        )
        val tenancy = provider.database.tenancyDao().getActiveForRoom(room.id)!!
        billing.generateBill(
            BillingRepository.BillRequest(tenancy.id, "2026-06", 120.0, 8.0),
        )
        val secondBill = billing.generateBill(
            BillingRepository.BillRequest(tenancy.id, "2026-07", 150.0, 8.0),
        )

        val second = billing.getBill(secondBill)!!
        assertEquals(30.0, second.electricityUnits, 0.0001)
        assertEquals(24_000L, second.electricityAmountPaise)
        val reading = billing.getReadingForBill(secondBill)!!
        assertEquals(120.0, reading.previousReading, 0.0001)
        assertEquals(150.0, reading.currentReading, 0.0001)
        assertEquals(30.0, reading.units, 0.0001)
        assertEquals(2, provider.database.billDao().count())
    }

    @Test
    fun `reading lower than the previous reading is rejected without side effects`() = runBlocking {
        val room = provider.database.roomDao().getAll().first()
        tenants.createTenant(
            draft("Gita", "9000000005"),
            TenantRepository.NewTenancy(
                roomId = room.id,
                monthlyRentPaise = 800_000,
                securityDepositPaise = 0,
                joinDate = LocalDate.of(2026, 8, 1),
            ),
        )
        val tenancy = provider.database.tenancyDao().getActiveForRoom(room.id)!!
        billing.generateBill(
            BillingRepository.BillRequest(tenancy.id, "2026-08", 100.0, 8.0),
        )
        val billsBefore = provider.database.billDao().count()
        val readingsBefore = provider.database.meterReadingDao().count()

        try {
            billing.generateBill(
                BillingRepository.BillRequest(tenancy.id, "2026-09", 80.0, 8.0),
            )
            fail("A reading below the previous reading must be rejected")
        } catch (_: IllegalArgumentException) {
        }

        assertEquals(billsBefore, provider.database.billDao().count())
        assertEquals(readingsBefore, provider.database.meterReadingDao().count())
    }

    @Test
    fun `latest reading per room never mixes rooms`() = runBlocking {
        val roomA = provider.database.roomDao().getAll()[0]
        val roomB = provider.database.roomDao().getAll()[1]
        tenants.createTenant(
            draft("Hari", "9000000006"),
            TenantRepository.NewTenancy(roomA.id, 800_000, 0, LocalDate.of(2026, 10, 1)),
        )
        tenants.createTenant(
            draft("Ira", "9000000007"),
            TenantRepository.NewTenancy(roomB.id, 800_000, 0, LocalDate.of(2026, 10, 1)),
        )
        val tenancyA = provider.database.tenancyDao().getActiveForRoom(roomA.id)!!
        val tenancyB = provider.database.tenancyDao().getActiveForRoom(roomB.id)!!
        billing.generateBill(BillingRepository.BillRequest(tenancyA.id, "2026-10", 100.0, 8.0))
        billing.generateBill(BillingRepository.BillRequest(tenancyB.id, "2026-10", 50.0, 8.0))
        billing.generateBill(BillingRepository.BillRequest(tenancyA.id, "2026-11", 120.0, 8.0))

        val latest = ElectricityRepository(provider).observeLatestPerRoom().first()
            .associateBy { it.roomId }
        assertEquals(120.0, latest.getValue(roomA.id).currentReading, 0.0001)
        assertEquals(50.0, latest.getValue(roomB.id).currentReading, 0.0001)
    }

    @Test
    fun `bill keeps its original linked reading for later invoices`() = runBlocking {
        val room = provider.database.roomDao().getAll().first()
        tenants.createTenant(
            draft("Jaya", "9000000008"),
            TenantRepository.NewTenancy(room.id, 800_000, 0, LocalDate.of(2026, 1, 1)),
        )
        val tenancy = provider.database.tenancyDao().getActiveForRoom(room.id)!!
        val january = billing.generateBill(
            BillingRepository.BillRequest(tenancy.id, "2026-01", 1300.0, 8.0),
        )
        val januaryReading = billing.getReadingForBill(january)!!

        billing.generateBill(BillingRepository.BillRequest(tenancy.id, "2026-02", 1350.0, 9.0))

        val historical = billing.getReadingForBill(january)!!
        assertEquals(januaryReading.id, historical.id)
        assertEquals(0.0, historical.previousReading, 0.0001)
        assertEquals(1300.0, historical.currentReading, 0.0001)
        assertEquals(1300.0, historical.units, 0.0001)
        assertEquals(8.0, historical.ratePerUnit, 0.0001)
        assertEquals(1_040_000L, historical.amountPaise)
        val oldBill = billing.getBill(january)!!
        assertEquals(8.0, oldBill.electricityRate, 0.0001)
        assertEquals(1_040_000L, oldBill.electricityAmountPaise)
    }

    @Test
    fun `deleting the latest bill restores its previous reading for regeneration`() = runBlocking {
        val room = provider.database.roomDao().getAll().first()
        tenants.createTenant(
            draft("Kiran", "9000000009"),
            TenantRepository.NewTenancy(room.id, 800_000, 0, LocalDate.of(2026, 1, 1)),
        )
        val tenancy = provider.database.tenancyDao().getActiveForRoom(room.id)!!
        billing.generateBill(
            BillingRepository.BillRequest(tenancy.id, "2026-01", 100.0, 8.0),
        )
        val billId = billing.generateBill(
            BillingRepository.BillRequest(tenancy.id, "2026-02", 115.0, 8.0),
        )

        billing.deleteBill(billId)

        assertNull(billing.getBill(billId))
        assertNull(billing.getReadingForBill(billId))
        assertEquals(1, provider.database.meterReadingDao().count())
        assertEquals(
            100.0,
            ElectricityRepository(provider).latestForRoom(room.id)!!.currentReading,
            0.0001,
        )

        val regeneratedId = billing.generateBill(
            BillingRepository.BillRequest(tenancy.id, "2026-02", 115.0, 8.0),
        )
        val regeneratedReading = billing.getReadingForBill(regeneratedId)!!
        assertEquals(100.0, regeneratedReading.previousReading, 0.0001)
        assertEquals(115.0, regeneratedReading.currentReading, 0.0001)
        assertEquals(15.0, regeneratedReading.units, 0.0001)
    }

    @Test
    fun `bill deletion is blocked when newer bills and readings exist`() = runBlocking {
        val room = provider.database.roomDao().getAll().first()
        tenants.createTenant(
            draft("Nisha", "9000000012"),
            TenantRepository.NewTenancy(room.id, 800_000, 0, LocalDate.of(2026, 1, 1)),
        )
        val tenancy = provider.database.tenancyDao().getActiveForRoom(room.id)!!
        val firstId = billing.generateBill(
            BillingRepository.BillRequest(tenancy.id, "2026-01", 100.0, 8.0),
        )
        val secondId = billing.generateBill(
            BillingRepository.BillRequest(tenancy.id, "2026-02", 115.0, 8.0),
        )

        try {
            billing.deleteBill(firstId)
            fail("An older bill with newer history must not be deleted")
        } catch (_: ConflictException) {
        }

        assertNotNull(billing.getBill(firstId))
        assertNotNull(billing.getBill(secondId))
        assertEquals(2, provider.database.meterReadingDao().count())
    }

    @Test
    fun `bill with payment records cannot be deleted or regenerated`() = runBlocking {
        val room = provider.database.roomDao().getAll().first()
        tenants.createTenant(
            draft("Lata", "9000000010"),
            TenantRepository.NewTenancy(room.id, 800_000, 0, LocalDate.of(2026, 1, 1)),
        )
        val tenancy = provider.database.tenancyDao().getActiveForRoom(room.id)!!
        val billId = billing.generateBill(
            BillingRepository.BillRequest(tenancy.id, "2026-01", 1300.0, 8.0),
        )
        billing.recordPayment(
            billId,
            10_000,
            LocalDate.of(2026, 1, 10),
            PaymentMethod.CASH,
            "",
            "",
        )

        try {
            billing.deleteBill(billId)
            fail("A bill with payments must not be deleted")
        } catch (_: ConflictException) {
        }
        try {
            billing.regenerateBill(billId)
            fail("A bill with payments must not be regenerated")
        } catch (_: ConflictException) {
        }

        assertNotNull(billing.getBill(billId))
        assertEquals(1, provider.database.paymentDao().count())
        assertEquals(10_000L, billing.getBill(billId)!!.paidPaise)
    }

    @Test
    fun `regenerating a bill reuses its reading without advancing the meter`() = runBlocking {
        val room = provider.database.roomDao().getAll().first()
        tenants.createTenant(
            draft("Mohan", "9000000011"),
            TenantRepository.NewTenancy(room.id, 800_000, 0, LocalDate.of(2026, 1, 1)),
        )
        val tenancy = provider.database.tenancyDao().getActiveForRoom(room.id)!!
        val originalId = billing.generateBill(
            BillingRepository.BillRequest(tenancy.id, "2026-01", 1300.0, 8.0),
        )
        val original = billing.getBill(originalId)!!
        val originalReading = billing.getReadingForBill(originalId)!!
        val readingsBefore = provider.database.meterReadingDao().count()

        val regeneratedId = billing.regenerateBill(originalId)

        assertNotEquals(originalId, regeneratedId)
        assertNull(billing.getBill(originalId))
        val regenerated = billing.getBill(regeneratedId)!!
        assertEquals(original.billingMonth, regenerated.billingMonth)
        assertEquals(original.totalPaise, regenerated.totalPaise)
        assertEquals(original.electricityUnits, regenerated.electricityUnits, 0.0001)
        assertEquals(original.electricityRate, regenerated.electricityRate, 0.0001)
        assertEquals(original.electricityAmountPaise, regenerated.electricityAmountPaise)
        assertEquals(readingsBefore, provider.database.meterReadingDao().count())
        val reused = billing.getReadingForBill(regeneratedId)!!
        assertEquals(originalReading.id, reused.id)
        assertEquals(originalReading.previousReading, reused.previousReading, 0.0001)
        assertEquals(originalReading.currentReading, reused.currentReading, 0.0001)

        val nextId = billing.generateBill(
            BillingRepository.BillRequest(tenancy.id, "2026-02", 1350.0, 8.0),
        )
        val nextReading = billing.getReadingForBill(nextId)!!
        assertEquals(1300.0, nextReading.previousReading, 0.0001)
        assertEquals(50.0, nextReading.units, 0.0001)
        assertEquals(readingsBefore + 1, provider.database.meterReadingDao().count())
        assertEquals(1, billing.observeForMonth("2026-01").first().size)
    }

    private fun draft(name: String, mobile: String) = TenantRepository.TenantDraft(
        name = name,
        mobile = mobile,
        alternateMobile = "",
        address = "",
        idType = com.homerentmanager.data.entity.IdType.NONE,
        idNumber = "",
        notes = "",
    )
}

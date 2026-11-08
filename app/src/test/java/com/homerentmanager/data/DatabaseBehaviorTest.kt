package com.homerentmanager.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.homerentmanager.data.entity.BillStatus
import com.homerentmanager.data.entity.PaymentMethod
import com.homerentmanager.data.entity.TenancyStatus
import com.homerentmanager.data.local.AppDatabase
import com.homerentmanager.data.local.DatabaseProvider
import com.homerentmanager.data.repository.BillingRepository
import com.homerentmanager.data.repository.TenantRepository
import com.homerentmanager.domain.ConflictException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(AppDatabase.NAME)
        provider = DatabaseProvider(context)
        tenants = TenantRepository(provider)
        billing = BillingRepository(provider)
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
    fun `fresh database seeds exactly seven rooms and settings`() = runBlocking {
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

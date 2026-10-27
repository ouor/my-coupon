package com.ouor.mycoupon.domain

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ouor.mycoupon.data.AppDatabase
import com.ouor.mycoupon.data.Coupon
import com.ouor.mycoupon.data.CouponRepository
import com.ouor.mycoupon.data.CouponStatus
import com.ouor.mycoupon.data.CouponType
import com.ouor.mycoupon.data.ImageStore
import com.ouor.mycoupon.data.UsageKind
import com.ouor.mycoupon.data.UsedReason
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Amount-voucher state machine on a real (in-memory) Room DB (code review A-05) and the S5 partial update (A-04). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CouponActionsTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
        .allowMainThreadQueries().build()
    private val repo = CouponRepository(db, ImageStore(ApplicationProvider.getApplicationContext()), emptySet())
    private val actions = CouponActions(repo)

    @After fun close() = db.close()

    private fun amount(face: Long = 10_000) = runBlocking {
        repo.insertAll(listOf(Coupon(type = CouponType.AMOUNT, faceValue = face, balance = face, code = "A1", imagePath = "x", thumbPath = "x", imageHash = "h"))).single()
    }
    private fun get(id: Long) = runBlocking { repo.get(id)!! }
    private fun logs(id: Long) = runBlocking { repo.usageOf(id) }

    @Test
    fun useThenUndo() = runBlocking {
        val id = amount()
        actions.markUsed(id)
        assertEquals(CouponStatus.USED, get(id).status)
        assertEquals(0L, get(id).balance)
        actions.undoUse(id)
        assertEquals(CouponStatus.ACTIVE, get(id).status)
        assertEquals(10_000L, get(id).balance)
        assertTrue(logs(id).isEmpty())
    }

    @Test
    fun twoPartialUsesUndoneOneByOne() = runBlocking {
        val id = amount()
        actions.recordRemaining(id, 7_000)
        actions.recordSpent(id, 2_000)
        assertEquals(5_000L, get(id).balance)
        actions.undoLastBalance(id)
        assertEquals(7_000L, get(id).balance)
        actions.undoLastBalance(id)
        assertEquals(10_000L, get(id).balance)
        assertTrue(logs(id).isEmpty())
    }

    @Test
    fun spentToZeroIsUsedAndUndoRestores() = runBlocking {
        val id = amount()
        actions.recordRemaining(id, 4_000)
        actions.recordSpent(id, 4_000)
        assertEquals(CouponStatus.USED, get(id).status)
        actions.undoUse(id)
        assertEquals(CouponStatus.ACTIVE, get(id).status)
        assertEquals(4_000L, get(id).balance)
        assertEquals(1, logs(id).size)
    }

    @Test
    fun givenAwayThenUndo() = runBlocking {
        val id = amount()
        actions.markUsed(id, UsedReason.GIVEN, givenTo = "  김  ")
        assertEquals(UsedReason.GIVEN, get(id).usedReason)
        assertEquals("김", get(id).givenTo) // C2-25
        assertTrue(CouponRepository.matches(get(id), "김"))
        actions.undoUse(id)
        assertEquals(CouponStatus.ACTIVE, get(id).status)
        assertEquals(null, get(id).usedReason)
        assertEquals(null, get(id).givenTo)
        actions.markUsed(id, UsedReason.GIVEN, givenTo = " ") // C2-25 AC2: blank is fine
        assertEquals(null, get(id).givenTo)
    }

    @Test(expected = UsageInputException::class)
    fun overspendRefused() = runBlocking { actions.recordSpent(amount(), 20_000) }

    /** A-04: a use recorded while S5 was open survives the save; a typed balance leaves an ADJUST record that undo reverts. */
    @Test
    fun editKeepsConcurrentChangesAndRecordsBalance() = runBlocking {
        val id = amount()
        val opened = get(id)
        actions.recordRemaining(id, 8_000) // e.g. from the widget while the editor is open
        actions.edit(id, opened, opened.copy(brand = "스타벅스", expiryDate = LocalDate.of(2027, 1, 31)))
        get(id).let { assertEquals("스타벅스", it.brand); assertEquals(8_000L, it.balance) }

        val again = get(id)
        actions.edit(id, again, again.copy(balance = 6_500))
        assertEquals(6_500L, get(id).balance)
        assertEquals(UsageKind.ADJUST, logs(id).first().kind)
        assertEquals(1_500L, logs(id).first().spent)
        actions.undoLastBalance(id)
        assertEquals(8_000L, get(id).balance)
    }

    @Test
    fun editFaceValueCarriesUntouchedBalance() = runBlocking {
        val id = amount(10_000)
        val opened = get(id)
        actions.edit(id, opened, opened.copy(faceValue = 30_000))
        assertEquals(30_000L, get(id).balance)
        assertTrue(logs(id).isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun editRefusesBalanceAboveFace() = runBlocking {
        val id = amount()
        val opened = get(id)
        actions.edit(id, opened, opened.copy(balance = 50_000))
    }
}

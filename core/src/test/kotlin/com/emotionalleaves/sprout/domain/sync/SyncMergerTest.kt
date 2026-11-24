package com.emotionalleaves.sprout.domain.sync

import com.emotionalleaves.sprout.Fixtures
import com.emotionalleaves.sprout.data.simplefin.AccountSetParser
import com.emotionalleaves.sprout.data.simplefin.ProtocolMode
import com.emotionalleaves.sprout.data.simplefin.SfTransaction
import com.emotionalleaves.sprout.domain.categorize.CategoryResolver
import com.emotionalleaves.sprout.domain.categorize.CategorySource
import com.emotionalleaves.sprout.domain.categorize.DefaultCategories
import com.emotionalleaves.sprout.domain.categorize.RuleType
import com.emotionalleaves.sprout.domain.categorize.UserRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Simulates the database as a map so merges can be applied and repeated. */
class SyncMergerTest {

    private val ids = DefaultCategories.all.mapIndexed { i, c -> c.key to (i + 1).toLong() }.toMap()
    private val eatingOut = ids.getValue(DefaultCategories.EATING_OUT)
    private val gifts = ids.getValue(DefaultCategories.GIFTS)
    private val window = SyncWindow(1_763_000_000, 1_764_000_000)

    private class Db { val rows = linkedMapOf<String, StoredTx>() }

    private fun merger(rules: List<UserRule> = emptyList()) = SyncMerger(CategoryResolver(rules, ids), setOf(eatingOut))

    private fun syncFixture(db: Db, fixture: String, syncTime: Long, m: SyncMerger = merger(), cleanup: Boolean = true): MergeResult {
        val set = AccountSetParser.parse(Fixtures.simplefin(fixture), ProtocolMode.V2)
        var total = MergeResult(emptyList(), emptyList(), 0, 0)
        for (acct in set.accounts) {
            val key = Ids.accountKey(acct.connId, acct.id)
            total = apply(db, m.mergeAccount(key, acct.transactions, existingFor(db, key, acct.transactions), window, syncTime, cleanup), total)
        }
        return total
    }

    private fun existingFor(db: Db, key: String, incoming: List<SfTransaction>): List<StoredTx> {
        val wanted = incoming.map { Ids.transactionId(key, it.id) }.toSet()
        return db.rows.values.filter { it.id in wanted || (it.pending && it.accountKey == key) }
    }

    private fun apply(db: Db, r: MergeResult, acc: MergeResult): MergeResult {
        r.deleteIds.forEach { db.rows.remove(it) }
        r.upserts.forEach { db.rows[it.id] = it }
        return MergeResult(acc.upserts + r.upserts, acc.deleteIds + r.deleteIds, acc.newCount + r.newCount, acc.updatedCount + r.updatedCount)
    }

    @Test fun sameFixtureTwiceInsertsZeroDuplicates() {
        val db = Db()
        val first = syncFixture(db, "v2_basic.json", 1_763_900_000)
        assertEquals(6, first.newCount)
        assertEquals(6, db.rows.size)
        val second = syncFixture(db, "v2_basic.json", 1_763_900_500)
        assertEquals(0, second.newCount)
        assertEquals(0, second.updatedCount)
        assertEquals(6, db.rows.size)
    }

    @Test fun transactionIdsUseAccountKeyAndRemoteId() {
        val db = Db()
        syncFixture(db, "v2_multi_connection.json", 1_763_900_000)
        assertEquals(setOf("sf:CON-A:1:T1", "sf:CON-B:1:T1"), db.rows.keys)
    }

    @Test fun pendingToPostedKeepsUserCategoryNoteAndAlert() {
        val db = Db()
        syncFixture(db, "pending_then_posted_1.json", 1_763_750_000)
        val pendingCasey = db.rows.getValue("sf:C:CHK:P-2")
        db.rows[pendingCasey.id] = pendingCasey.copy(categoryId = gifts, categorySource = CategorySource.USER, note = "road trip", alertedAt = 99, excludedFromBudget = true)

        syncFixture(db, "pending_then_posted_2.json", 1_763_900_000)

        assertFalse(db.rows.containsKey("sf:C:CHK:P-2"))
        val posted = db.rows.getValue("sf:C:CHK:F-2")
        assertEquals(gifts, posted.categoryId)
        assertEquals(CategorySource.USER, posted.categorySource)
        assertEquals("road trip", posted.note)
        assertEquals(99L, posted.alertedAt)
        assertTrue(posted.excludedFromBudget)
        assertFalse(posted.pending)
    }

    @Test fun eatingOutTipWithin25PercentMatchesPending() {
        val db = Db()
        syncFixture(db, "pending_then_posted_1.json", 1_763_750_000)
        db.rows["sf:C:CHK:P-1"] = db.rows.getValue("sf:C:CHK:P-1").copy(note = "lunch with Sam")
        syncFixture(db, "pending_then_posted_2.json", 1_763_900_000)
        assertFalse(db.rows.containsKey("sf:C:CHK:P-1"))
        assertEquals("lunch with Sam", db.rows.getValue("sf:C:CHK:F-1").note)
    }

    @Test fun pendingMissingFromLatestResponseIsDeleted() {
        val db = Db()
        syncFixture(db, "pending_then_posted_1.json", 1_763_750_000)
        assertTrue(db.rows.containsKey("sf:C:CHK:P-3"))
        syncFixture(db, "pending_then_posted_2.json", 1_763_900_000)
        assertFalse(db.rows.containsKey("sf:C:CHK:P-3"))
        assertEquals(setOf("sf:C:CHK:F-1", "sf:C:CHK:F-2"), db.rows.keys)
    }

    @Test fun pendingNotDeletedWhenAccountHadErrors() {
        val db = Db()
        syncFixture(db, "pending_then_posted_1.json", 1_763_750_000)
        syncFixture(db, "pending_then_posted_2.json", 1_763_900_000, cleanup = false)
        assertTrue(db.rows.containsKey("sf:C:CHK:P-3"))
    }

    @Test fun pendingOutsideWindowIsKept() {
        val m = merger()
        val old = stored("sf:C:CHK:OLD", pending = true, effectiveAt = window.startDate - 100)
        val r = m.mergeAccount("sf:C:CHK", emptyList(), listOf(old), window, 1_763_900_000, true)
        assertTrue(r.deleteIds.isEmpty())
    }

    @Test fun nonTipAmountMismatchDoesNotMatch() {
        val m = merger()
        val pending = stored("sf:C:CHK:P", pending = true, amount = -6000, merchant = "CASEYS LINCOLN", effectiveAt = 1_763_700_000)
        val posted = SfTransaction("F", 1_763_800_000, -6600, "CASEYS #3021 LINCOLN NE", null, false)
        val r = m.mergeAccount("sf:C:CHK", listOf(posted), listOf(pending), window, 1_763_900_000, true)
        // Not matched for carry-over, but it is still deleted as a vanished pending row.
        assertEquals(CategorySource.BUILT_IN, r.upserts.single().categorySource)
        assertEquals(listOf("sf:C:CHK:P"), r.deleteIds)
    }

    @Test fun postedMoreThanSevenDaysAfterPendingDoesNotCarryOver() {
        val m = merger()
        val pending = stored("sf:C:CHK:P", pending = true, amount = -6000, merchant = "CASEYS LINCOLN",
            effectiveAt = 1_763_000_100, source = CategorySource.USER, category = gifts)
        val posted = SfTransaction("F", 1_763_000_100 + 8 * 86_400, -6000, "CASEYS #3021 LINCOLN NE", null, false)
        val r = m.mergeAccount("sf:C:CHK", listOf(posted), listOf(pending), window, 1_763_900_000, true)
        assertEquals(CategorySource.BUILT_IN, r.upserts.single().categorySource)
    }

    @Test fun resyncNeverOverwritesUserCategoryOrNote() {
        val db = Db()
        syncFixture(db, "v2_basic.json", 1_763_900_000)
        val id = "sf:CON-111:ACT-CHK-1:TX-1003"
        db.rows[id] = db.rows.getValue(id).copy(categoryId = gifts, categorySource = CategorySource.USER, note = "keep me")
        syncFixture(db, "v2_basic.json", 1_763_950_000)
        assertEquals(gifts, db.rows.getValue(id).categoryId)
        assertEquals("keep me", db.rows.getValue(id).note)
    }

    @Test fun resyncAppliesNewMerchantRuleToBuiltInButNotUser() {
        val db = Db()
        syncFixture(db, "v2_basic.json", 1_763_900_000)
        val netflix = "sf:CON-111:ACT-CHK-1:TX-1003"
        val rule = UserRule(1, RuleType.EXACT, "NETFLIX", gifts)
        syncFixture(db, "v2_basic.json", 1_763_950_000, merger(listOf(rule)))
        assertEquals(gifts, db.rows.getValue(netflix).categoryId)
        assertEquals(CategorySource.MERCHANT_RULE, db.rows.getValue(netflix).categorySource)
    }

    @Test fun changedAmountCountsAsUpdate() {
        val m = merger()
        val old = stored("sf:C:CHK:T", amount = -1000, merchant = "NETFLIX", effectiveAt = 1_763_500_000)
        val tx = SfTransaction("T", 1_763_500_000, -1100, "NETFLIX.COM", null, false)
        val r = m.mergeAccount("sf:C:CHK", listOf(tx), listOf(old), window, 1_763_900_000, true)
        assertEquals(1, r.updatedCount)
        assertEquals(-1100L, r.upserts.single().amountMinor)
    }

    @Test fun pendingWithoutTransactedAtUsesSyncTimeAndDoesNotDrift() {
        val m = merger()
        val tx = SfTransaction("P", 0, -500, "SOMEWHERE", null, true)
        val first = m.mergeAccount("sf:C:A", listOf(tx), emptyList(), window, 1_763_800_000, true).upserts.single()
        assertEquals(1_763_800_000L, first.effectiveAt)
        val second = m.mergeAccount("sf:C:A", listOf(tx), listOf(first), window, 1_763_900_000, true)
        assertTrue(second.upserts.isEmpty())
    }

    private fun stored(
        id: String, pending: Boolean = false, amount: Long = -1000, merchant: String = "X",
        effectiveAt: Long = 1_763_500_000, source: CategorySource = CategorySource.NONE, category: Long = 1,
    ) = StoredTx(
        id = id, accountKey = id.substringBeforeLast(':'), remoteId = id.substringAfterLast(':'), amountMinor = amount,
        description = merchant, merchantKey = merchant, postedAt = if (pending) 0 else effectiveAt, transactedAt = null,
        pending = pending, effectiveAt = effectiveAt, categoryId = category, categorySource = source, note = null,
        alertedAt = null, createdAt = 0,
    )
}

package com.emotionalleaves.sprout.data

import com.emotionalleaves.sprout.data.goals.GoalRepository
import com.emotionalleaves.sprout.data.db.SyncTrigger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class GoalRepositoryTest {
    private lateinit var env: TestEnv
    private lateinit var goals: GoalRepository

    @Before fun setUp() {
        env = TestEnv()
        goals = GoalRepository(env.db, env.clock) { ZoneId.of("America/Chicago") }
    }

    @After fun tearDown() = env.close()

    @Test fun manualGoalTracksAddedMoney() = runBlocking {
        val id = goals.save(null, "Art supplies fund", 50_000, LocalDate.of(2027, 3, 31), null, 10_000)
        goals.addMoney(id, 5_000)
        val v = goals.observe().first().single()
        assertEquals(15_000L, v.progress.savedMinor)
        assertEquals(30, v.progress.percent)
        goals.addMoney(id, -100_000)
        assertEquals(0L, goals.observe().first().single().progress.savedMinor)
    }

    @Test fun linkedGoalFollowsAccountBalance() = runBlocking {
        env.secure.saveAccessUrl(env.accessUrl)
        env.server.enqueue(MockResponse().setBody(env.fixture("v2_basic.json")))
        env.repo().sync(SyncTrigger.MANUAL)
        goals.save(null, "Emergency fund", 300_000, null, "sf:CON-111:ACT-CHK-1", 0)
        val v = goals.observe().first().single()
        assertEquals(152_345L, v.progress.savedMinor)
        assertEquals("Everyday Checking", v.account!!.name)
    }

    @Test fun deleteRemoves() = runBlocking {
        val id = goals.save(null, "Trip", 10_000, null, null, 0)
        val undo = goals.delete(id)
        assertTrue(goals.observe().first().isEmpty())
        undo()
        val back = goals.observe().first().single()
        assertEquals(id, back.goal.id); assertEquals("Trip", back.goal.name); assertEquals(10_000L, back.goal.targetMinor)
    }
}

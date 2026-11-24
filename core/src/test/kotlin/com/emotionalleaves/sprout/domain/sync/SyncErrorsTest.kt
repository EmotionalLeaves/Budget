package com.emotionalleaves.sprout.domain.sync

import com.emotionalleaves.sprout.Fixtures
import com.emotionalleaves.sprout.data.simplefin.AccountSetParser
import com.emotionalleaves.sprout.data.simplefin.ProtocolMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncErrorsTest {
    private fun plan(name: String, mode: ProtocolMode = ProtocolMode.V2) =
        SyncErrors.plan(AccountSetParser.parse(Fixtures.simplefin(name), mode))

    @Test fun cleanResponseIsSuccess() = assertEquals(SyncOutcome.SUCCESS, plan("v2_basic.json").outcome)

    @Test fun partialErrorsArePartialWithAccountError() {
        val p = plan("v2_partial_errors.json")
        assertEquals(SyncOutcome.PARTIAL, p.outcome)
        assertNotNull(p.accountError("CON-1", "ACT-SAV"))
        assertNull(p.accountError("CON-1", "ACT-CHK"))
        assertEquals("gen.", p.generalErrors.single().code)
        assertEquals("act.missingdata,gen.", p.codes)
    }

    @Test fun conAuthMarksOnlyThatConnection() {
        val p = plan("v2_con_auth.json")
        assertEquals(setOf("CON-A"), p.connectionErrors.keys)
        assertEquals(SyncOutcome.PARTIAL, p.outcome)
    }

    @Test fun legacyErrorsAreGeneral() {
        val p = plan("v1_legacy_org_and_errors.json", ProtocolMode.V1)
        assertEquals(1, p.generalErrors.size)
        assertTrue(p.connectionErrors.isEmpty())
    }

    @Test fun errorsWithNoAccountsFail() {
        val set = AccountSetParser.parse("""{"errlist":[{"code":"gen.auth","msg":"no"}],"connections":[],"accounts":[]}""", ProtocolMode.V2)
        assertEquals(SyncOutcome.FAILED, SyncErrors.plan(set).outcome)
    }

    @Test fun emptyIsSuccess() = assertEquals(SyncOutcome.SUCCESS, plan("empty_accounts.json").outcome)
}
